import ecrlib.api.enums.*;
import ecrlib.api.tlv.*;
import ecrlib.api.*;
import models.CallbackMessage;
import models.Config;
import models.ErrorType;
import models.EPOSMessage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.NullPointerException;
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

  public void sendCallbackMessage(CallbackMessage msg) {
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

  public EcrTerminalStatus getTerminalState() {
    return terminalComm.readTerminalStatus();
  }

  public String continueTransaction() {
    EcrStatus status = terminalComm.continueTransaction();
    if (EcrStatus.ECR_OK != status) {
      return "{\"status\": \"error\"}";
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();
    if (result != null) {
      String merchant = printoutHandler.generateMerchantPrintout();
      String customer = printoutHandler.generateCustomerPrintout();
      return "{\"merchant\":" + merchant + "\", \"customer\": \"" + customer + "\"}";
    }
    else {
      return "{\"status\": \"error\"}";
    }
  }

  public String doSale(String amount) {
    EcrStatus status = terminalComm.setTransactionType(EcrTransactionType.TRANS_SALE);
    if (status != EcrStatus.ECR_OK) {
      // TODO: log here -
      System.out.print("Set transaction type. Unexpected error.\n");
      return "error";
    }

    status = terminalComm.setTransactionAmount(amount);
    if (status != EcrStatus.ECR_OK) {
      // TODO: log here -
      System.out.print("Set transaction amount. Unexpected error.\n");
      return "error";
    }

    status = terminalComm.startTransaction();
    if (status != EcrStatus.ECR_OK) {
      // TODO: log here -
      System.out.print("failed to start transaction.\n");
      return "{\"status\": \"error\"}";

      // return emergencyProcedureForTransaction(true);
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();
    if (result != null) {
      String merchant = printoutHandler.generateMerchantPrintout();
      String customer = printoutHandler.generateCustomerPrintout();
      return "\"merchant\":" + merchant + "\", \"customer\": \"" + customer + "\"}";
    }
    else {
      return "{\"status\": \"error\"}";
    }
  }
//
//  public void forceConnectionTestToAuthorizationHost() {
//    if (!getStatus()) {
//      System.out.println("Force connection test to authorization host. Connection error.");
//      return;
//    }
//
//    if (!checkTerminalStatus(EcrTerminalStatus.STATUS_READY_FOR_NEW_TRAN)) {
//      System.out.println("Force connection test to authorization host. Unexpected error.");
//      return;
//    }
//
//    var status = terminalComm.setTransactionType(EcrTransactionType.TRANS_TEST_CONNECTION);
//    if (EcrStatus.ECR_OK != status) {
//      System.out.println("Force connection test to authorization host. Set transaction type error.");
//      return;
//    }
//
//    status = terminalComm.startTransaction();
//    if (EcrStatus.ECR_OK != status) {
//      System.out.println("Force connection test to authorization host. Start transaction error.");
//      return;
//    }
//
//    EcrTransactionResult result = terminalComm.readTransactionResult();
//    if (null == result) {
//      System.out.println("Error: No information about connection test result");
//      return;
//    }
//
//    switch (result) {
//      case RESULT_TRANS_ACCEPTED:
//        System.out.println("Connection test succeed.");
//        break;
//      case RESULT_TRANS_REFUSED:
//        System.out.println("Connection test failed.");
//        break;
//      case RESULT_NO_CONNECTION:
//        System.out.println("Connection test failed - no connection.");
//        break;
//      case RESULT_TRANS_INTERRUPTED_BY_USER:
//        System.out.println("Operation interrupted by user.");
//        break;
//      default:
//        System.out.println("Unknown operation result.");
//        break;
//    }
//  }
//
//  public void forceConnectionToTMS() {
//    if (!getStatus()) {
//      System.out.println("Force connection test to TMS. Connection error.");
//      return;
//    }
//
//    if (!checkTerminalStatus(EcrTerminalStatus.STATUS_READY_FOR_NEW_TRAN)) {
//      System.out.println("Force connection test to TMS. Unexpected error.");
//      return;
//    }
//
//    var status = terminalComm.setTransactionType(EcrTransactionType.TRANS_TMS);
//    if (EcrStatus.ECR_OK != status) {
//      System.out.println("Force connection test to TMS. Set transaction type error.");
//      return;
//    }
//    status = terminalComm.startTransaction();
//  }
//
//  public void forceReconciliation() {
//    if (!getStatus()) {
//      System.out.print("Force reconciliation. Connection error.\n");
//      return;
//    }
//
//    if (!checkTerminalStatus(EcrTerminalStatus.STATUS_READY_FOR_NEW_TRAN)
//        && !checkTerminalStatus(EcrTerminalStatus.STATUS_RECON_NEEDED)) {
//      System.out.print("Force reconciliation. Unexpected error.\n");
//      return;
//    }
//
//    var status = terminalComm.setTransactionType(EcrTransactionType.TRANS_RECONCILE);
//    if (EcrStatus.ECR_OK != status) {
//      System.out.println("Setting transaction to reconciliation error.");
//      return;
//    }
//
//    status = terminalComm.startTransaction();
//    if (EcrStatus.ECR_OK != status) {
//      System.out.println("Starting reconciliation transaction error.");
//      return;
//    }
//
//    EcrTransactionResult result = terminalComm.readTransactionResult();
//    if (null == result) {
//      System.out.println("Transaction result null error.");
//      return;
//    }
//
//    switch (result) {
//      case RESULT_TRANS_ACCEPTED:
//        System.out.println("Reconciliation succeed.");
//        break;
//      case RESULT_NO_CONNECTION:
//        System.out.println("Reconciliation failed - no connection.");
//        break;
//      default:
//        System.out.println("Reconciliation failed.");
//        break;
//    }
//  }

  public String handleBatch() {
    StringBuilder out = new StringBuilder();
    while (true) {
      if (getTerminalState() != EcrTerminalStatus.STATUS_BATCH_COMPLETED) {
        return out.toString();
      }
    out.append(printoutHandler.generateReportFromBatch());
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