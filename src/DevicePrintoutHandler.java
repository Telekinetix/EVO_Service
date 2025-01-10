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

  public JsonArray getTransactionsFromBatch() throws Exception {
    EcrStatus status;
    JsonArray transList = new JsonArray();

    int iterator = 1;
    while (true) {
      status = terminalComm.setTransactionId(iterator++);
      if (status != EcrStatus.ECR_OK) {
        throw new Exception(status.name() + " - setTransactionId");
      }

      status = terminalComm.getSingleTransactionFromBatch();
      if (status == EcrStatus.ECR_OK) {
        JsonObject valueObject = new JsonObject();
        valueObject.addProperty("cardType", new String(terminalComm.readTag(TlvTag.TAG_APP_PREFERRED_NAME).getData(), "Cp1250"));
        valueObject.addProperty("transactionNumber", new String(terminalComm.readTag(TlvTag.TAG_TRANSACTION_NUMBER).getData(), "Cp1250"));
        valueObject.addProperty("pan", new String(terminalComm.readTag(TlvTag.TAG_MASKED_PAN).getData(), "Cp1250"));
        valueObject.addProperty("currencyCode", terminalComm.readTransactionCurrencyLabel());
        valueObject.addProperty("amount", terminalComm.readTransactionAmount());
        valueObject.addProperty("date", terminalComm.readTransactionDate());
        valueObject.addProperty("time", terminalComm.readTransactionTime());
        valueObject.addProperty("type", new String(terminalComm.readTag(TlvTag.TAG_TRANSACTION_TYPE).getData(), "Cp1250"));
        valueObject.addProperty("authorisationType", new String(terminalComm.readTag(TlvTag.TAG_AUTHORIZATION_TYPE).getData(), "Cp1250"));
        transList.add(valueObject);
      } else if (status == EcrStatus.ECR_NO_TERMINAL_DATA) {
        break;
      } else {
        throw new Exception(status.name() + " - getSingleTransactionFromBatch");
      }
    }

    if (transList.isEmpty()) {
      return transList;
    }
    status = terminalComm.getBatchSummary();
    if (status != EcrStatus.ECR_OK) {
      throw new Exception(status.name() + " - getBatchSummary");
    }

    return transList;
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