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

  private EcrStatus status;
  private EcrTerminalStatus terminalStatus;

  private String lastTransactionNumber;
  private String lastTransactionTime;
  private String lastTransactionDate;

  private DataOutputStream eposOutput;

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
    synchronized (this.eposOutput) {
      this.eposOutput = out;
    }
  }

  public void sendCallbackMessage(CallbackMessage msg) {
    String json = Main.gson.toJson(msg) + (char) 3;
    if (this.eposOutput == null) {
      ErrorHandler.error(ErrorType.eposConnectionError, "EPOS connection not set up.");
    }
    synchronized (this.eposOutput) {
      try {
        this.eposOutput.write(json.getBytes());
      } catch (IOException e) {
        // Logs error locally if the socket dies.
        ErrorHandler.error(ErrorType.eposConnectionError, e, "Socket died while sending message to EPOS");
      }
    }
  }

  public EPOSMessage waitForCallbackResponse() {
    while (true) {
      synchronized (this.eposResponse) {
        if (eposResponse == null) continue;
        return eposResponse;
      }
    }
  }

  public void setCallbackResponse(EPOSMessage msg) {
    while (true) {
      synchronized (this.eposResponse) {
        if (eposResponse != null) continue;
        this.eposResponse = msg;
        return;
      }
    }
  }

  public void clearCallbackResponse() {
    synchronized (this.eposResponse) {
      this.eposResponse = null;
    }
  }

  public boolean connectToTerminal(String terminalIp, int terminalPort, int timeout) {
    status = terminalComm.setTcpIpLink(terminalIp, terminalPort, timeout);
    if (EcrStatus.ECR_OK != status) {
      System.out.println("Tcp connection error.");
      return false;
    }
    return true;
  }

  public boolean cancelTransaction() {
    EcrStatus status = terminalComm.cancelTransaction();
    System.out.println("Attempting to cancel transaction. Status: " + status.name());
    return status == EcrStatus.ECR_OK;
  }

  public EcrStatus getTerminalStatus() {
    return terminalComm.getTerminalStatus();
  }

  public EcrTerminalStatus getTerminalState() {
    return terminalComm.readTerminalStatus();
  }


  public boolean executeSaleTransaction(String amount, boolean anotherTry) {
    if (!getStatus()) {
      System.out.println("Sale transaction. Connection error.");
      return false;
    }

    switch (terminalStatus) {
      case STATUS_READY_FOR_NEW_TRAN:
        if (anotherTry) {
          if (!emergencyProcedureForTransaction(anotherTry)) {
            break;
          }
          return true;
        }
        break;
      case STATUS_RECON_NEEDED:
        System.out.println("Sale transaction. Reconciliation needed.");
        return false;
      case STATUS_BATCH_COMPLETED:
        System.out.println("Sale transaction. Read batch operation needed.");
        return false;
      case STATUS_BUSY:
        System.out.println("Sale transaction. Terminal busy.");
        return false;
      case STATUS_APP_ERROR:
        System.out.println("Sale transaction. Problem with terminal.");
        return false;
      default:
        return emergencyProcedureForTransaction(anotherTry);
    }

    status = terminalComm.setTransactionType(EcrTransactionType.TRANS_SALE);
    if (EcrStatus.ECR_OK != status) {
      System.out.print("Set transaction type. Unexpected error.\n");
      return false;
    }

    status = terminalComm.setTransactionAmount(amount);
    if (EcrStatus.ECR_OK != status) {
      System.out.print("Set transaction amount. Unexpected error.\n");
      return false;
    }

    status = terminalComm.startTransaction();
    if (EcrStatus.ECR_OK != status) {
      return emergencyProcedureForTransaction(true);
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();
    if (null != result) {
      saveTransactionData();
      if (printoutHandler.isMerchantPrintoutNecessary()) {
        printoutHandler.generateMerchantPrintout();
      }
      printoutHandler.generateCustomerPrintout();
    }

    try {
      return checkTransactionResult(result);
    } catch (NullPointerException e) {
      return emergencyProcedureForTransaction(true);
    }
  }

  public void forceConnectionTestToAuthorizationHost() {
    if (!getStatus()) {
      System.out.println("Force connection test to authorization host. Connection error.");
      return;
    }

    if (!checkTerminalStatus(EcrTerminalStatus.STATUS_READY_FOR_NEW_TRAN)) {
      System.out.println("Force connection test to authorization host. Unexpected error.");
      return;
    }

    status = terminalComm.setTransactionType(EcrTransactionType.TRANS_TEST_CONNECTION);
    if (EcrStatus.ECR_OK != status) {
      System.out.println("Force connection test to authorization host. Set transaction type error.");
      return;
    }

    status = terminalComm.startTransaction();
    if (EcrStatus.ECR_OK != status) {
      System.out.println("Force connection test to authorization host. Start transaction error.");
      return;
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();
    if (null == result) {
      System.out.println("Error: No information about connection test result");
      return;
    }

    switch (result) {
      case RESULT_TRANS_ACCEPTED:
        System.out.println("Connection test succeed.");
        break;
      case RESULT_TRANS_REFUSED:
        System.out.println("Connection test failed.");
        break;
      case RESULT_NO_CONNECTION:
        System.out.println("Connection test failed - no connection.");
        break;
      case RESULT_TRANS_INTERRUPTED_BY_USER:
        System.out.println("Operation interrupted by user.");
        break;
      default:
        System.out.println("Unknown operation result.");
        break;
    }
  }

  public void forceConnectionToTMS() {
    if (!getStatus()) {
      System.out.println("Force connection test to TMS. Connection error.");
      return;
    }

    if (!checkTerminalStatus(EcrTerminalStatus.STATUS_READY_FOR_NEW_TRAN)) {
      System.out.println("Force connection test to TMS. Unexpected error.");
      return;
    }

    status = terminalComm.setTransactionType(EcrTransactionType.TRANS_TMS);
    if (EcrStatus.ECR_OK != status) {
      System.out.println("Force connection test to TMS. Set transaction type error.");
      return;
    }
    status = terminalComm.startTransaction();
  }

  public void forceReconciliation() {
    if (!getStatus()) {
      System.out.print("Force reconciliation. Connection error.\n");
      return;
    }

    if (!checkTerminalStatus(EcrTerminalStatus.STATUS_READY_FOR_NEW_TRAN)
        && !checkTerminalStatus(EcrTerminalStatus.STATUS_RECON_NEEDED)) {
      System.out.print("Force reconciliation. Unexpected error.\n");
      return;
    }

    status = terminalComm.setTransactionType(EcrTransactionType.TRANS_RECONCILE);
    if (EcrStatus.ECR_OK != status) {
      System.out.println("Setting transaction to reconciliation error.");
      return;
    }

    status = terminalComm.startTransaction();
    if (EcrStatus.ECR_OK != status) {
      System.out.println("Starting reconciliation transaction error.");
      return;
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();
    if (null == result) {
      System.out.println("Transaction result null error.");
      return;
    }

    switch (result) {
      case RESULT_TRANS_ACCEPTED:
        System.out.println("Reconciliation succeed.");
        break;
      case RESULT_NO_CONNECTION:
        System.out.println("Reconciliation failed - no connection.");
        break;
      default:
        System.out.println("Reconciliation failed.");
        break;
    }
  }

  public void getTerminalInfo() {
    status = terminalComm.readTerminalInformationData();
    if (EcrStatus.ECR_OK != status) {
      System.out.print("Get terminal info. Connection error.\n");
      return;
    }

    String tid = terminalComm.readTerminalId();
    System.out.print("Terminal info (tid): ");
    System.out.println(tid);
  }


//
//    switch (terminalStatus) {
//      case STATUS_READY_FOR_NEW_TRAN:
//        System.out.print("Termianl ready for new operation.\n");
//        break;
//      case STATUS_RECON_NEEDED:
//        System.out.print("Terminal demand to perform reconcillation.\n");
//        break;
//      case STATUS_BATCH_COMPLETED:
//        System.out.print("Terminal demand to perform batch read operation.\n");
//        break;
//      case STATUS_BUSY:
//        System.out.print("Terminal busy.\n");
//        break;
//      case STATUS_APP_ERROR:
//        System.out.print("Terminal error.\n");
//        break;
//      default:
//        System.out.print("Last operation is not completed.\n");
//        break;
//    }
 // }

  public void handleBatch() {
    while (true) {
      if (!getStatus()) {
        System.out.println("Handle batch - connection error.");
        return;
      }

      if (!checkTerminalStatus(EcrTerminalStatus.STATUS_BATCH_COMPLETED)) {
        System.out.println("End of batches.");
        return;
      }

      printoutHandler.generateReportFromBatch();
    }
  }

  public EcrStatus initTerminalSettings(Config config) {
    terminalComm.setProtocol(EcrCommProtocol.PROTOCOL_ESERVICE);

    status = terminalComm.setCashRegisterId(config.tid.toString());
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

  private boolean checkTerminalStatus(EcrTerminalStatus expectedTerminalStatus) {
    if (expectedTerminalStatus != terminalStatus) {
      System.out.println("Info: terminal status = " + terminalStatus
          + " expected terminal status = " + expectedTerminalStatus);
      return false;
    }
    return true;
  }

  private boolean checkTransactionResult(EcrTransactionResult result) {
    if (null == result) {
      throw new NullPointerException("Transaction result is null.");
    }

    switch (result) {
      case RESULT_NO_CONNECTION:
        System.out.println("Transaction result: no connection.");
        return false;
      case RESULT_TRANS_ACCEPTED:
        System.out.println("Transaction result: accepted.");
        return true;
      case RESULT_TRANS_INTERRUPTED_BY_USER:
        System.out.println("Transaction result: interrupted by user.");
        return false;
      case RESULT_TRANS_REFUSED:
        System.out.println("Transaction result: refused.");
        return false;
      default:
        System.out.println("Transaction unknown result.");
        return false;
    }
  }

  private boolean compareTransactions() {
    status = terminalComm.getLastTransactionData();
    if (EcrStatus.ECR_OK != status) {
      System.out.println("Compare transactions - failed to get last transaction data.");
      return false;
    }
    String transactionNumber = readTransactionNumber();
    String transactionDate = terminalComm.readTransactionDate();
    String transactionTime = terminalComm.readTransactionTime();

    if (transactionNumber.equals(lastTransactionNumber) &&
        transactionDate.equals(lastTransactionDate) &&
        transactionTime.equals(lastTransactionTime)) {
      return true;
    }
    return false;
  }

  private boolean emergencyProcedureForTransaction(boolean anotherTry) {
    terminalStatus = EcrTerminalStatus.STATUS_UNKNOWN;

    for (int i = 0; i < 3; i++) {
      if (!getStatus()) {
        continue;
      }

      EcrTransactionResult result;
      switch (terminalStatus) {
        case STATUS_READY_FOR_NEW_TRAN:
          if (compareTransactions()) {
            if (!anotherTry) {
              System.out.println("Transaction failed.");
            }
            return false;
          }
          saveTransactionData();
          if (printoutHandler.isMerchantPrintoutNecessary()) {
            printoutHandler.generateMerchantPrintout();
          }
          printoutHandler.generateCustomerPrintout();
          result = terminalComm.readTransactionResult();
          if (EcrTransactionResult.RESULT_TRANS_ACCEPTED != result) {
            System.out.println("Transaction failed.");
            return false;
          }
          System.out.println("Transaction for previous receipt"
              + "has been accepted.\nIf previous receipt has not been"
              + "paid by card, make reversal of previous transaction.");
          return true;
        case STATUS_BUSY:
        case STATUS_BATCH_COMPLETED:
        case STATUS_APP_ERROR:
        case STATUS_RECON_NEEDED:
        case STATUS_UNKNOWN:
          System.out.println("Transaction status is unknown.\n"
              + " If transaction has been approved by terminal,"
              + " confirm payment manually.");
          return false;

        default:
          status = terminalComm.continueTransaction();
          if (EcrStatus.ECR_OK != status) {
            return false;
          }

          result = terminalComm.readTransactionResult();
          if (null == result) {
            continue;
          }

          saveTransactionData();
          if (printoutHandler.isMerchantPrintoutNecessary()) {
            printoutHandler.generateMerchantPrintout();
          }
          printoutHandler.generateCustomerPrintout();

          if (anotherTry) {
            EcrTransactionType type = terminalComm.readTransactionType();
            if (EcrTransactionResult.RESULT_TRANS_ACCEPTED == result &&
                EcrTransactionType.TRANS_SALE == type) {
              System.out.println("Transaction for previous receipt has been accepted.\n"
                  + " If previous receipt has not been paid by card,"
                  + " make reversal of previous transaction.");
              return true;
            }
            System.out.println("Transaction failed.");
            return false;
          }

          System.out.println("Last operation was in progress. Sale transaction failed.");
          return false;
      }
    }
    return false;
  }

  private boolean getStatus() {
    status = terminalComm.getTerminalStatus();
    if (EcrStatus.ECR_OK != status) {
      System.out.println("Get Status operation failed.");
      return false;
    }

    terminalStatus = terminalComm.readTerminalStatus();
    if (null == terminalStatus) {
      System.out.println("Get Status operation failed. Unexpected Error");
      return false;
    }

    return true;
  }

  private String readTransactionNumber() {
    Tag tagRawData = terminalComm.readTag(TlvTag.TAG_TRANSACTION_NUMBER);
    try {
      return new String(tagRawData.getData(), "Cp1250");
    } catch (NullPointerException exception) {
      return "";
    } catch (UnsupportedEncodingException exception) {
      return "";
    }
  }

  private void saveTransactionData() {
    lastTransactionNumber = readTransactionNumber();
    lastTransactionDate = terminalComm.readTransactionDate();
    lastTransactionTime = terminalComm.readTransactionTime();
  }
}