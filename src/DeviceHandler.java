import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ecrlib.api.enums.*;
import ecrlib.api.*;
import ecrlib.api.tlv.Tag;
import models.ResponseMessage;
import models.Config;
import models.ErrorType;
import models.EPOSMessage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public class DeviceHandler {

  private EcrPaymentTerminal terminalComm;
  private EcrCallbacks callbacks;
  private EcrCallbacksManager callbacksManager;
  private DevicePrintoutHandler printoutHandler;
  private EcrTerminalStatus terminalStatus;

  private DataOutputStream eposOutput;
  public final Object eposLock = new Object();
  public final Object callbackLock = new Object();
  private EPOSMessage eposResponse;

  static {
    try {
      System.loadLibrary("libecrjava");
    }
    catch (UnsatisfiedLinkError linkError) {
      System.loadLibrary("ecrjava");
    }
  }

  public DeviceHandler() {
    terminalComm = new EcrPaymentTerminal();
    printoutHandler = new DevicePrintoutHandler(terminalComm);
    callbacks = new DeviceCallbacks(printoutHandler, this);
    callbacksManager = new EcrCallbacksManager(callbacks);
    callbacksManager.register();

    PrintoutHandler.setupDictionaryFromFile("EN.LNG");
    PrintoutHandler.setUsingSignatureVerifiedLine(true);
  }

  public void setupEPOSCallback(DataOutputStream out) {
    synchronized (eposLock) {
      this.eposOutput = out;
    }
  }

  private ResponseMessage lastMsg;
  public void sendCallbackMessage(ResponseMessage msg) {
    if (lastMsg != null && Objects.equals(msg.type, lastMsg.type) && Objects.equals(msg.prompt, lastMsg.prompt)) {
      return;
    }

    lastMsg = msg;
    String json = Main.gson.toJson(msg) + (char) 3;
    System.out.println(json);
    if (this.eposOutput == null) {
      ErrorHandler.error(ErrorType.eposConnectionError, "EPOS connection not set up.");
    }
    else {
      postToEPOS(json.getBytes());
    }
  }

  public void postToEPOS(byte[] data) {
    try {
      synchronized (eposLock) {
        this.eposOutput.write(data);
      }
    } catch (IOException e) {
      ErrorHandler.error(ErrorType.eposConnectionError, e, "Socket died while sending message to EPOS"); // Logs error locally if the socket dies.
    }
  }

  public EPOSMessage waitForCallbackResponse() {
    while (true) {
      synchronized (callbackLock) {
        if (eposResponse == null) continue;
        return eposResponse;
      }
    }
  }

  public void setCallbackResponse(EPOSMessage msg) {
    while (true) {
      synchronized (callbackLock) {
        if (eposResponse != null) continue;
        this.eposResponse = msg;
        return;
      }
    }
  }

  public void clearCallbackResponse() {
    synchronized (callbackLock) {
      this.eposResponse = null;
    }
  }

  public boolean connectToTerminal(String terminalIp, int terminalPort, int timeout) {
    EcrStatus status = terminalComm.setTcpIpLink(terminalIp, terminalPort, timeout);
    if (status != EcrStatus.ECR_OK) {
      System.out.println("Tcp connection error.");
      return false;
    }
    return true;
  }

  public EcrStatus getTerminalStatus() {
    return terminalComm.getTerminalStatus();
  }

  public ResponseMessage getTerminalState() {
    EcrTerminalStatus status = terminalComm.readTerminalStatus();
    if (status == null) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when getting terminal status.";
      error.status = "TK_TERMINAL_CONNECTION_ERROR";
      return error;
    }
    ResponseMessage response = new ResponseMessage("success");
    response.status = status.name();
    return response;
  }

  public ResponseMessage testConnection() {
    EcrStatus status = terminalComm.setTransactionType(EcrTransactionType.TRANS_TEST_CONNECTION);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting transaction type on terminal.";
      error.status = status.name();
      return error;
    }

    status = terminalComm.startTransaction();
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when testing connection to payment authorisation host.";
      error.status = status.name();
      return error;
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();
    if (result == null) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when getting result of connection test to payment authorisation host.";
      error.status = "TK_TEST_CONNECTION_RESULT_ERROR";
      return error;
    }
    ResponseMessage response = new ResponseMessage("success");
    response.status = status.name();
    return response;
  }

  public ResponseMessage update() {
    EcrStatus status = terminalComm.setTransactionType(EcrTransactionType.TRANS_TMS);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when updating terminal from TMS.";
      error.status = status.name();
      return error;
    }

    terminalComm.startTransaction();
    return new ResponseMessage("success");
  }

  public ResponseMessage continueTransaction() {
    EcrStatus status = terminalComm.continueTransaction();
    if (EcrStatus.ECR_OK != status) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when continuing transaction.";
      error.status = status.name();
      return error;
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();
    if (result != null) {
      try {
        JsonArray merchant = printoutHandler.generateMerchantPrintout();
        JsonArray customer = printoutHandler.generateCustomerPrintout();
        ResponseMessage response = new ResponseMessage("success");
        JsonObject valueObject = new JsonObject();
        valueObject.add("merchant", merchant);
        valueObject.add("customer", customer);
        response.value = valueObject;
        return response;
      }
      catch (Exception e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when getting printout.";
        error.status = e.getMessage();
        return error;
      }
    }
    else {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when reading transaction result.";
      return error;
    }
  }

  public ResponseMessage doSale(String amount) {
    EcrStatus status = terminalComm.setTransactionType(EcrTransactionType.TRANS_SALE);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting transaction type.";
      error.status = status.name();
      return error;
    }

    status = terminalComm.setTransactionAmount(amount);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting transaction amount.";
      error.status = status.name();
      return error;
    }

    status = terminalComm.startTransaction();
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting starting transaction.";
      error.status = status.name();
      return error;
      // return emergencyProcedureForTransaction(true);
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();

    if (result != null) {
      try {
        JsonArray merchant = printoutHandler.generateMerchantPrintout();
        JsonArray customer = printoutHandler.generateCustomerPrintout();
        ResponseMessage response = new ResponseMessage("success");
        JsonObject valueObject = new JsonObject();
        valueObject.add("merchant", merchant);
        valueObject.add("customer", customer);
        Tag cardType = terminalComm.readTag(TlvTag.TAG_APP_PREFERRED_NAME);
        Tag transactionNumber = terminalComm.readTag(TlvTag.TAG_TRANSACTION_NUMBER);
        valueObject.addProperty("cardType", new String(cardType.getData(), "Cp1250"));
        valueObject.addProperty("transactionNumber", new String(transactionNumber.getData(), "Cp1250"));
        valueObject.addProperty("pan", new String(terminalComm.readTag(TlvTag.TAG_MASKED_PAN).getData(), "Cp1250"));
        valueObject.addProperty("currencyCode", terminalComm.readTransactionCurrencyLabel());
        response.status = result.name();

        response.value = valueObject;
        return response;
      }
      catch (NullPointerException e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when accessing tags.";
        error.status = e.getMessage();
        return error;
      }
      catch (UnsupportedEncodingException e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when decoding tags.";
        error.status = e.getMessage();
        return error;
      }
      catch (Exception e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when getting printout.";
        error.status = e.getMessage();

        return error;
      }
    }
    else {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when reading transaction result.";
      return error;
    }
  }

  public ResponseMessage doRefund(String amount) {
    EcrStatus status = terminalComm.setTransactionType(EcrTransactionType.TRANS_REFUND);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting transaction type.";
      error.status = status.name();
      return error;
    }

    status = terminalComm.setTransactionAmount(amount);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting transaction amount.";
      error.status = status.name();
      return error;
    }

    status = terminalComm.startTransaction();
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting starting transaction.";
      error.status = status.name();
      return error;
      // return emergencyProcedureForTransaction(true);
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();

    if (result != null) {
      try {
        JsonArray merchant = printoutHandler.generateMerchantPrintout();
        JsonArray customer = printoutHandler.generateCustomerPrintout();
        ResponseMessage response = new ResponseMessage("success");
        JsonObject valueObject = new JsonObject();
        valueObject.add("merchant", merchant);
        valueObject.add("customer", customer);
        Tag cardType = terminalComm.readTag(TlvTag.TAG_APP_PREFERRED_NAME);
        Tag transactionNumber = terminalComm.readTag(TlvTag.TAG_TRANSACTION_NUMBER);

        valueObject.addProperty("cardType", new String(cardType.getData(), "Cp1250"));
        valueObject.addProperty("transactionNumber", new String(transactionNumber.getData(), "Cp1250"));
        valueObject.addProperty("pan", new String(terminalComm.readTag(TlvTag.TAG_MASKED_PAN).getData(), "Cp1250"));
        valueObject.addProperty("currencyCode", terminalComm.readTransactionCurrencyLabel());
        response.status = result.name();

        response.value = valueObject;
        return response;
      }
      catch (NullPointerException e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when accessing tags.";
        error.status = e.getMessage();
        return error;
      }
      catch (UnsupportedEncodingException e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when decoding tags.";
        error.status = e.getMessage();
        return error;
      }
      catch (Exception e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when getting printout.";
        error.status = e.getMessage();

        return error;
      }
    }
    else {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when reading transaction result.";
      return error;
    }
  }

  public ResponseMessage doReversal(String amount, String transactionId) {
    EcrStatus status = terminalComm.setTransactionType(EcrTransactionType.TRANS_REVERSAL);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting transaction type.";
      error.status = status.name();
      return error;
    }

    status = terminalComm.setNumberOfTransactionToReverse(transactionId);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting transaction id.";
      error.status = status.name();
      return error;
    }

    status = terminalComm.setTransactionAmount(amount);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting transaction amount.";
      error.status = status.name();
      return error;
    }

    status = terminalComm.startTransaction();
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when setting starting transaction.";
      error.status = status.name();
      return error;
      // return emergencyProcedureForTransaction(true);
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();

    if (result != null) {
      try {
        JsonArray merchant = printoutHandler.generateMerchantPrintout();
        JsonArray customer = printoutHandler.generateCustomerPrintout();
        ResponseMessage response = new ResponseMessage("success");
        JsonObject valueObject = new JsonObject();
        valueObject.add("merchant", merchant);
        valueObject.add("customer", customer);
        Tag cardType = terminalComm.readTag(TlvTag.TAG_APP_PREFERRED_NAME);
        Tag transactionNumber = terminalComm.readTag(TlvTag.TAG_TRANSACTION_NUMBER);
        valueObject.addProperty("cardType", new String(cardType.getData(), "Cp1250"));
        valueObject.addProperty("transactionNumber", new String(transactionNumber.getData(), "Cp1250"));
        valueObject.addProperty("pan", new String(terminalComm.readTag(TlvTag.TAG_MASKED_PAN).getData(), "Cp1250"));
        valueObject.addProperty("currencyCode", terminalComm.readTransactionCurrencyLabel());
        response.status = result.name();

        response.value = valueObject;
        return response;
      }
      catch (NullPointerException e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when accessing tags.";
        error.status = e.getMessage();
        return error;
      }
      catch (UnsupportedEncodingException e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when decoding tags.";
        error.status = e.getMessage();
        return error;
      }
      catch (Exception e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when getting printout.";
        error.status = e.getMessage();

        return error;
      }
    }
    else {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when reading transaction result.";
      return error;
    }
  }

  public ResponseMessage forceReconciliation() {
    EcrStatus status = terminalComm.setResetReport(true);
    if (status != EcrStatus.ECR_OK)
    {
      ResponseMessage msg = new ResponseMessage("error");
      msg.prompt = "Reconciliation Transaction Error when resetting report.";
      msg.status = status.name();
      return msg;
    }
    
    status = terminalComm.setTransactionType(EcrTransactionType.TRANS_RECONCILE);
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage msg = new ResponseMessage("error");
      msg.prompt = "Reconciliation Transaction Error when starting.";
      msg.status = status.name();
      return msg;
    }

    status = terminalComm.startTransaction();
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when starting transaction.";
      error.status = status.name();
      return error;
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();
    if (result == null) {
      ResponseMessage msg = new ResponseMessage("error");
      msg.prompt = "Reconciliation null error.";
      return msg;
    }

    ResponseMessage msg;
    switch (result) {
      case RESULT_TRANS_ACCEPTED:
        msg = new ResponseMessage("success");
        return msg;
      case RESULT_NO_CONNECTION:
        msg = new ResponseMessage("error");
        msg.prompt = "Reconciliation failed - no connection.";
        msg.status = result.name();
        return msg;
      default:
        msg = new ResponseMessage("error");
        msg.prompt = "Reconciliation failed.";
        msg.status = result.name();
        return msg;
    }
  }
  public ResponseMessage handleBatch() {
    JsonArray reports = new JsonArray();
    JsonObject valueObject = new JsonObject();
    boolean shouldBreak = false;
    while (true) {
      if (terminalComm.readTerminalStatus() != EcrTerminalStatus.STATUS_BATCH_COMPLETED || shouldBreak) {
        ResponseMessage msg = new ResponseMessage("success");
        valueObject.add("reports", reports);
        msg.value = valueObject;
        return msg;
      }

      try {
        JsonArray arr = printoutHandler.getTransactionsFromBatch();
        if (arr.isEmpty()) {
          shouldBreak = true;
          continue;
        }
        reports.add(arr);
      }
      catch (NullPointerException e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when accessing tags.";
        error.status = e.getMessage();
        return error;
      }
      catch (UnsupportedEncodingException e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when decoding tags.";
        error.status = e.getMessage();
        return error;
      }
      catch (Exception e) {
        ResponseMessage error = new ResponseMessage("error");
        error.prompt = "Unexpected error when generating report from batch.";
        error.status = e.getMessage();
        return error;
      }
    }
  }

  public ResponseMessage getLastTransaction() {
    EcrStatus status = terminalComm.getLastTransactionData();
    if (status != EcrStatus.ECR_OK) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when getting last transaction.";
      error.status = status.name();
      return error;
    }

    try {
      JsonObject valueObject = new JsonObject();
      valueObject.addProperty("cardType", new String(terminalComm.readTag(TlvTag.TAG_APP_PREFERRED_NAME).getData(), "Cp1250"));
      valueObject.addProperty("transactionNumber", new String(terminalComm.readTag(TlvTag.TAG_TRANSACTION_NUMBER).getData(), "Cp1250"));
      valueObject.addProperty("pan", new String(terminalComm.readTag(TlvTag.TAG_MASKED_PAN).getData(), "Cp1250"));
      valueObject.addProperty("currencyCode", terminalComm.readTransactionCurrencyLabel());
      valueObject.addProperty("amount", terminalComm.readTransactionAmount());
      valueObject.addProperty("date", terminalComm.readTransactionDate());
      valueObject.addProperty("time", terminalComm.readTransactionTime());
      String type = new String(terminalComm.readTag(TlvTag.TAG_TRANSACTION_TYPE).getData(), "Cp1250");
      valueObject.addProperty("type", type);
      if (type.equals("5")) {
        valueObject.addProperty("originalType", new String(terminalComm.readTag(TlvTag.TAG_ORIGINAL_TRANSACTION_TYPE).getData(), "Cp1250"));
      }
      valueObject.addProperty("authorisationType", new String(terminalComm.readTag(TlvTag.TAG_AUTHORIZATION_TYPE).getData(), "Cp1250"));

      ResponseMessage msg = new ResponseMessage("success");
      msg.value = valueObject;
      return msg;
    }
    catch (NullPointerException e) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when accessing tags.";
      error.status = e.getMessage();
      return error;
    }
    catch (UnsupportedEncodingException e) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when decoding tags.";
      error.status = e.getMessage();
      return error;
    }
    catch (Exception e) {
      ResponseMessage error = new ResponseMessage("error");
      error.prompt = "Unexpected error when getting printout.";
      error.status = e.getMessage();
      return error;
    }
  }

  public EcrStatus initTerminalSettings(Config config) {
    terminalComm.setProtocol(EcrCommProtocol.PROTOCOL_ESERVICE);

    EcrStatus status = terminalComm.setCashRegisterId(config.tid.toString());
    if (status != EcrStatus.ECR_OK) {
      ErrorHandler.error(ErrorType.deviceGenericError, status,"Cash registration error.");
      return status;
    }

    status = terminalComm.setHandleTerminalRequests(EcrHandlingTerminalRequestsMode.REQUESTS_HANDLE_CHOSEN_BY_TERMINAL);
    if (status != EcrStatus.ECR_OK) {
      ErrorHandler.error(ErrorType.deviceGenericError, status,"Requests mode initialization error.");
      return status;
    }

    status = terminalComm.setTerminalIndex((byte)1);
    if (status != EcrStatus.ECR_OK) {
      ErrorHandler.error(ErrorType.deviceGenericError, status,"Failed to set terminal index.");
      return status;
    }
    return status;
  }
}