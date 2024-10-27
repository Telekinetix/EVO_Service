import ecrlib.api.enums.EcrStatus;
import models.ErrorType;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ErrorHandler {
  static void error(ErrorType type, Exception e, String message) {
    logTimestamp();
    System.err.println("An error has occurred: " + type.name());
    System.err.println(message);
    e.printStackTrace();
  }

  static void error(ErrorType type, Exception e) {
    logTimestamp();
    System.err.println("An error has occurred: " + type.name());
    e.printStackTrace();
  }

  static void error(ErrorType type, String message) {
    logTimestamp();
    System.err.println("An error has occurred: " + type.name());
    System.err.println(message);
  }

  static void error(ErrorType type, EcrStatus status, String message) {
    logTimestamp();
    System.err.println("An error has occurred: " + type.name());
    System.err.println(status.name());
    System.err.println(message);
  }

  static void logTimestamp() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String formattedDate = sdf.format(new Date());
    System.err.println(formattedDate);
  }

  static byte[] buildErrorObject(ErrorType type) {
    return buildErrorObject(type, "");
  }

  static byte[] buildErrorObject(ErrorType type, String message) {
    return ("{\"err\": \"" + type.name() + "\""+ (!message.isEmpty() ? ", \"message\": \"" + message + "\"" : "")+ "}"
            + (char) 4).getBytes();
  }
}