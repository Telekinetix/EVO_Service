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

    private EcrPaymentTerminal terminalComm;
    private SimplePrintoutHandler simplePrintoutHandler;
    private ExtendedPrintoutHandler extendedPrintoutHandler;

    public DevicePrintoutHandler(EcrPaymentTerminal terminal) {
        terminalComm = terminal;
    }

    public void generateCustomerPrintout() {
        simplePrintoutHandler = terminalComm.getTransactionCustomerPrintoutHandler();
        generatePrintout();
    }

    public void generateMerchantPrintout() {
        simplePrintoutHandler = terminalComm.getTransactionMerchantPrintoutHandler();
        generatePrintout();
    }

    public void generatePrintout() {
        simplePrintoutHandler.setNormalLineLength(LINE_LENGTH);
        simplePrintoutHandler.setSmallLineLength(LINE_LENGTH);
        simplePrintoutHandler.setBigLineLength(LINE_LENGTH);

        PrintoutResult result = simplePrintoutHandler.preparePrintout();
        if (PrintoutResult.PRINTOUT_OK != result) {
            System.out.println("Printout result error.");
            return;
        }

        System.out.println("Printout:");
        int lines = simplePrintoutHandler.getNumberOfLines();
        for (int i=0; i<lines; i++) {
            EcrPrintoutLine line = simplePrintoutHandler.getNextLine();
            int lineNumber = line.getLineNumber();
            String text = line.getText();
            System.out.println(Integer.toString(lineNumber) + " " + text + "\n");
        }
    }

    public void generateReportFromBatch() {
        EcrStatus status;

        extendedPrintoutHandler = terminalComm.getClosingDayPrintoutHandler();

        extendedPrintoutHandler.setNormalLineLength(LINE_LENGTH);
        extendedPrintoutHandler.setSmallLineLength(LINE_LENGTH);
        extendedPrintoutHandler.setBigLineLength(LINE_LENGTH);

        PrintoutResult result = extendedPrintoutHandler.startPrintout();
        if (PrintoutResult.PRINTOUT_OK != result) {
            System.out.println("Printout report start error.");
            return;
        }

        int iterator = 1;
        while (true) {
            status = terminalComm.setTransactionId(iterator++);
            if (EcrStatus.ECR_OK != status) {
                System.out.println("Set transaction id error.");
                return;
            }

            status = terminalComm.getSingleTransactionFromBatch();
            if (EcrStatus.ECR_OK == status) {
                result = extendedPrintoutHandler.addPrintoutEntry();
            } else if (EcrStatus.ECR_NO_TERMINAL_DATA == status) {
                System.out.println("End of data");
                break;
            } else {
                System.out.println("Reading trans data error.");
                return;
            }
        }

        status = terminalComm.getBatchSummary();
        if (EcrStatus.ECR_OK != status) {
            System.out.println("Get batch summary error.");
            return;
        }

        result = extendedPrintoutHandler.finishPrintout();
        if (PrintoutResult.PRINTOUT_OK != result) {
            System.out.println("Summary printout result error.");
            return;
        }

        System.out.println("Printout:");
        int lines = extendedPrintoutHandler.getNumberOfLines();
        for (int i=0; i<lines; i++) {
            EcrPrintoutLine line = extendedPrintoutHandler.getNextLine();
            int lineNumber = line.getLineNumber();
            String text = line.getText();
            System.out.println(Integer.toString(lineNumber) + " " + text + "\n");
           }
    }

    public boolean isMerchantPrintoutNecessary() {
        AuthorizationMethod method = terminalComm.readAuthorizationMethod();

        if (AuthorizationMethod.AUTH_METHOD_SIGN != method &&
                    AuthorizationMethod.AUTH_METHOD_PIN_SIGN != method) {
            return true;
        }

        EcrTransactionResult result = terminalComm.readTransactionResult();

        if (EcrTransactionResult.RESULT_TRANS_ACCEPTED != result) {
            return true;
        }
        return false;
    }
}