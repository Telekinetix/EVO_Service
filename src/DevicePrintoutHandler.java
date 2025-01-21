import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ecrlib.api.EcrPaymentTerminal;
import ecrlib.api.EcrPrintoutLine;
import ecrlib.api.ExtendedPrintoutHandler;
import ecrlib.api.SimplePrintoutHandler;
import ecrlib.api.enums.*;
import ecrlib.api.tlv.Tag;

import java.io.UnsupportedEncodingException;
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
      throw new Exception(result.name());
    }

    JsonArray lineList = new JsonArray();

    int lines = printoutHandler.getNumberOfLines();
    for (int i=0; i<lines; i++) {
      EcrPrintoutLine line = printoutHandler.getNextLine();
      lineList.add(Main.gson.toJsonTree(line));
    }
    return lineList;
  }

  public JsonObject getTransactionsFromBatch() throws Exception {
    JsonObject res = new JsonObject();
    EcrStatus status;
    JsonArray transList = new JsonArray();
    ExtendedPrintoutHandler printoutHandler = terminalComm.getClosingDayPrintoutHandler();
    printoutHandler.setNormalLineLength(LINE_LENGTH);
    printoutHandler.setSmallLineLength(LINE_LENGTH);
    printoutHandler.setBigLineLength(LINE_LENGTH);

    PrintoutResult result = printoutHandler.startPrintout();

    if (result != PrintoutResult.PRINTOUT_OK) {
      throw new Exception(result.toString() + " - startPrintout");
    }

    int iterator = 1;
    while (true) {
      status = terminalComm.setTransactionId(iterator++);
      if (status != EcrStatus.ECR_OK) {
        throw new Exception(status.name() + " - setTransactionId");
      }

      status = terminalComm.getSingleTransactionFromBatch();
      if (status == EcrStatus.ECR_OK) {
        printoutHandler.addPrintoutEntry();
        JsonObject valueObject = new JsonObject();
        valueObject.addProperty("cardType", new String(terminalComm.readTag(TlvTag.TAG_APP_PREFERRED_NAME).getData(), "Cp1250"));
        valueObject.addProperty("transactionNumber", new String(terminalComm.readTag(TlvTag.TAG_TRANSACTION_NUMBER).getData(), "Cp1250"));
        valueObject.addProperty("pan", new String(terminalComm.readTag(TlvTag.TAG_MASKED_PAN).getData(), "Cp1250"));
        valueObject.addProperty("currencyCode", terminalComm.readTransactionCurrencyLabel());
        valueObject.addProperty("amount", terminalComm.readTransactionAmount());
        valueObject.addProperty("exchangeRate", terminalComm.readTransactionExchangeRate());
        valueObject.addProperty("amount", terminalComm.readTransactionAmount());
        valueObject.addProperty("date", terminalComm.readTransactionDate());
        valueObject.addProperty("time", terminalComm.readTransactionTime());
        String type = new String(terminalComm.readTag(TlvTag.TAG_TRANSACTION_TYPE).getData(), "Cp1250");
        valueObject.addProperty("type", type);
        if (type.equals("5")) {
          valueObject.addProperty("originalType", new String(terminalComm.readTag(TlvTag.TAG_ORIGINAL_TRANSACTION_TYPE).getData(), "Cp1250"));
        }
        valueObject.addProperty("authorisationType", new String(terminalComm.readTag(TlvTag.TAG_AUTHORIZATION_TYPE).getData(), "Cp1250"));
        transList.add(valueObject);
      } else if (status == EcrStatus.ECR_NO_TERMINAL_DATA) {
        break;
      } else {
        throw new Exception(status.name() + " - getSingleTransactionFromBatch");
      }
    }
    status = terminalComm.getBatchSummary();
    if (status != EcrStatus.ECR_OK) {
      throw new Exception(status.name() + " - getBatchSummary");
    }
    result = printoutHandler.finishPrintout();

    if (result != PrintoutResult.PRINTOUT_OK) {
      throw new Exception(result.toString() + " - finishPrintout");
    }

    JsonArray printoutLines = new JsonArray();

    int lines = printoutHandler.getNumberOfLines();
    for (int i=0; i<lines; i++) {
      EcrPrintoutLine line = printoutHandler.getNextLine();
      printoutLines.add(Main.gson.toJsonTree(line));
    }

    res.add("receipt", printoutLines);
    res.add("transactions", transList);
    return res;
  }

}