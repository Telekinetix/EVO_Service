import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ecrlib.api.EcrPaymentTerminal;
import ecrlib.api.EcrPrintoutLine;
import ecrlib.api.ExtendedPrintoutHandler;
import ecrlib.api.SimplePrintoutHandler;
import ecrlib.api.enums.AuthorizationMethod;
import ecrlib.api.enums.EcrStatus;
import ecrlib.api.enums.EcrTransactionResult;
import ecrlib.api.enums.PrintoutResult;

import java.util.ArrayList;
import java.util.List;

public class DevicePrintoutHandler {

  final int LINE_LENGTH = 40;

  private final EcrPaymentTerminal terminalComm;

  public DevicePrintoutHandler(EcrPaymentTerminal terminal) {
      terminalComm = terminal;
  }

  public JsonArray generateCustomerPrintout() throws Exception {
    return generatePrintout(terminalComm.getTransactionCustomerPrintoutHandler());
  }

  public JsonArray generateMerchantPrintout() throws Exception {
    return generatePrintout(terminalComm.getTransactionMerchantPrintoutHandler());
  }

  public JsonArray generatePrintout(SimplePrintoutHandler printoutHandler) throws Exception {
    printoutHandler.setNormalLineLength(LINE_LENGTH);
    printoutHandler.setSmallLineLength(LINE_LENGTH);
    printoutHandler.setBigLineLength(LINE_LENGTH);


    PrintoutResult result = printoutHandler.preparePrintout();
    if (result != PrintoutResult.PRINTOUT_OK) {
      throw new Exception("Printout result error.");
    }

    JsonArray lineList = new JsonArray();

    int lines = printoutHandler.getNumberOfLines();
    for (int i=0; i<lines; i++) {
      EcrPrintoutLine line = printoutHandler.getNextLine();
      lineList.add(Main.gson.toJsonTree(line));
    }
    return lineList;
  }

  public JsonArray generateReportFromBatch() throws Exception {
    EcrStatus status;

    ExtendedPrintoutHandler printoutHandler = terminalComm.getClosingDayPrintoutHandler();

    printoutHandler.setNormalLineLength(LINE_LENGTH);
    printoutHandler.setSmallLineLength(LINE_LENGTH);
    printoutHandler.setBigLineLength(LINE_LENGTH);

    PrintoutResult result = printoutHandler.startPrintout();
    if (PrintoutResult.PRINTOUT_OK != result) {
        throw new Exception("Start Error");
    }

    int iterator = 1;
    while (true) {
      status = terminalComm.setTransactionId(iterator++);
      if (status != EcrStatus.ECR_OK) {
        throw new Exception("Set Transaction ID Error");
      }

      status = terminalComm.getSingleTransactionFromBatch();
      if (status == EcrStatus.ECR_OK) {
        result = printoutHandler.addPrintoutEntry();
      } else if (EcrStatus.ECR_NO_TERMINAL_DATA == status) {
        System.out.println("End of data");
        break;
      } else {
        throw new Exception("Transaction data reading error");
      }
    }

    status = terminalComm.getBatchSummary();
    if (status != EcrStatus.ECR_OK) {
      throw new Exception("Get batch summary error");
    }

    result = printoutHandler.finishPrintout();
    if (result != PrintoutResult.PRINTOUT_OK) {
      throw new Exception("Summary printout result error");
     }

    JsonArray lineList = new JsonArray();
    int lines = printoutHandler.getNumberOfLines();
    for (int i=0; i<lines; i++) {
      EcrPrintoutLine line = printoutHandler.getNextLine();
      lineList.add(line.getText());
    }
    return lineList;
  }

  public boolean isMerchantPrintoutNecessary() {
    AuthorizationMethod method = terminalComm.readAuthorizationMethod();

    if (method != AuthorizationMethod.AUTH_METHOD_SIGN && method != AuthorizationMethod.AUTH_METHOD_PIN_SIGN) {
      return true;
    }

    EcrTransactionResult result = terminalComm.readTransactionResult();

    if (result != EcrTransactionResult.RESULT_TRANS_ACCEPTED) {
      return true;
    }
    return false;
  }
}