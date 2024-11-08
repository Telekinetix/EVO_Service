import ecrlib.api.EcrPaymentTerminal;
import ecrlib.api.EcrPrintoutLine;
import ecrlib.api.ExtendedPrintoutHandler;
import ecrlib.api.SimplePrintoutHandler;
import ecrlib.api.enums.AuthorizationMethod;
import ecrlib.api.enums.EcrStatus;
import ecrlib.api.enums.EcrTransactionResult;
import ecrlib.api.enums.PrintoutResult;

public class DevicePrintoutHandler {

  final int LINE_LENGTH = 40;

  private final EcrPaymentTerminal terminalComm;

  public DevicePrintoutHandler(EcrPaymentTerminal terminal) {
      terminalComm = terminal;
  }

  public String generateCustomerPrintout() {
    return generatePrintout(terminalComm.getTransactionCustomerPrintoutHandler());
  }

  public String generateMerchantPrintout() {
    return generatePrintout(terminalComm.getTransactionMerchantPrintoutHandler());
  }

  public String generatePrintout(SimplePrintoutHandler printoutHandler) {
    printoutHandler.setNormalLineLength(LINE_LENGTH);
    printoutHandler.setSmallLineLength(LINE_LENGTH);
    printoutHandler.setBigLineLength(LINE_LENGTH);

    PrintoutResult result = printoutHandler.preparePrintout();
    if (result != PrintoutResult.PRINTOUT_OK) {
      System.out.println("Printout result error.");
      return "error";
    }

    System.out.println("Printout:");
    StringBuilder out = new StringBuilder();
    int lines = printoutHandler.getNumberOfLines();
    for (int i=0; i<lines; i++) {
      EcrPrintoutLine line = printoutHandler.getNextLine();
      out.append(line.getLineNumber()).append(" ").append(line.getText()).append("\n");
    }
    System.out.println(out);
    return out.toString();
  }

  public String generateReportFromBatch() {
    EcrStatus status;

    ExtendedPrintoutHandler printoutHandler = terminalComm.getClosingDayPrintoutHandler();

    printoutHandler.setNormalLineLength(LINE_LENGTH);
    printoutHandler.setSmallLineLength(LINE_LENGTH);
    printoutHandler.setBigLineLength(LINE_LENGTH);

    PrintoutResult result = printoutHandler.startPrintout();
    if (PrintoutResult.PRINTOUT_OK != result) {
        System.out.println("Printout report start error.");
        return "start error";
    }

    int iterator = 1;
    while (true) {
      status = terminalComm.setTransactionId(iterator++);
      if (status != EcrStatus.ECR_OK) {
        System.out.println("Set transaction id error.");
        return "id error";
      }

      status = terminalComm.getSingleTransactionFromBatch();
      if (status == EcrStatus.ECR_OK) {
        result = printoutHandler.addPrintoutEntry();
      } else if (EcrStatus.ECR_NO_TERMINAL_DATA == status) {
        System.out.println("End of data");
        break;
      } else {
        System.out.println("Reading trans data error.");
        return "trans data error";
      }
    }

    status = terminalComm.getBatchSummary();
    if (status != EcrStatus.ECR_OK) {
      System.out.println("Get batch summary error.");
      return "batch summary error";
    }

    result = printoutHandler.finishPrintout();
    if (result != PrintoutResult.PRINTOUT_OK) {
      System.out.println("Summary printout result error.");
      return "printout result error";
    }

    StringBuilder out = new StringBuilder();
    System.out.println("Printout:");
    int lines = printoutHandler.getNumberOfLines();
    for (int i=0; i<lines; i++) {
      EcrPrintoutLine line = printoutHandler.getNextLine();
      out.append(line.getLineNumber()).append(" ").append(line.getText()).append("\n");
    }
    System.out.println(out);
    return out.toString();
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