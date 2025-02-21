import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ecrlib.api.enums.EcrStatus;
import ecrlib.api.enums.EcrTerminalStatus;
import models.Config;
import models.EPOSMessage;
import models.ErrorType;
import models.ResponseMessage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class Main {
  public static final Gson gson = new Gson();
  public static void main(String[] args) {
    Config config = new ConfigHandler().loadConfig();
    DeviceHandler deviceHandler = new DeviceHandler();

    if (!deviceHandler.connectToTerminal(config.terminalIp, config.terminalPort, config.terminalTimeout)) {
      ErrorHandler.error(ErrorType.deviceConnectionError, "Failed to connect to terminal.");
      return;
    }

    if (deviceHandler.initTerminalSettings(config) != EcrStatus.ECR_OK) {
      ErrorHandler.error(ErrorType.deviceGenericError, "Failed to setup terminal.");
      return;
    }

    ServerSocket serverSocket;
    try {
      serverSocket = new ServerSocket(config.serverPort);
    } catch (IOException e) {
      // Crash out of app - Likely failed to bind to port
      ErrorHandler.error(ErrorType.eposConnectionError, e, "Likely failed to bind to port");
      return;
    }

    while (true) {
      try (Socket socket = serverSocket.accept()) {
        new ConnectionHandler(socket, deviceHandler).run();
      }
      catch (IOException e) {
        // Logs error locally if the socket dies.
        ErrorHandler.error(ErrorType.eposConnectionError, e, "Socket died while connecting to EPOS");
      }
    }
  }

  static class ConnectionHandler {
    private final DeviceHandler deviceHandler;
    DataOutputStream out;
    DataInputStream in;

    public ConnectionHandler(Socket socket, DeviceHandler deviceHandler) {
      this.deviceHandler = deviceHandler;
      try {
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      } catch (IOException e) {
        // Logs error locally if the socket dies.
        ErrorHandler.error(ErrorType.eposConnectionError, e, "Socket died while connecting to EPOS");
      }
    }

    public void run() {
      while (true) {
        try {
          EPOSMessage msg = waitForMessage();
          if (msg == null || Objects.equals(msg.type, "closeConnection")) break; //When we receive this message from 4D, break from loop.

          new MessageHandler(out, msg, deviceHandler).start();
        } catch (IOException e) {
          // Logs error locally if the socket dies.
          ErrorHandler.error(ErrorType.eposConnectionError, e, "Socket died while receiving message from EPOS");
          return;
        }
      }
    }

    public EPOSMessage waitForMessage() throws IOException {
      byte[] messageByte = new byte[1024];
      StringBuilder dataString = new StringBuilder(1024);

      int lastChar = 0;
      do {
        int currentBytesRead = in.read(messageByte);
        if(currentBytesRead>0) {
          dataString.append(new String(messageByte, 0, currentBytesRead, StandardCharsets.UTF_8));
          lastChar = dataString.charAt(dataString.length() - 1);
        }
      } while ((lastChar != 3) && (lastChar != 0));

      if (dataString.length() == 0) return null;

      String outString = dataString.substring(0, dataString.length() - 1);
      return gson.fromJson(outString, EPOSMessage.class);
    }
  }

  private static class MessageHandler extends Thread {
    private final DeviceHandler deviceHandler;
    private final EPOSMessage msg;
    private final DataOutputStream out;

    public MessageHandler(DataOutputStream out, EPOSMessage msg, DeviceHandler deviceHandler) {
      this.out = out;
      this.msg = msg;
      this.deviceHandler = deviceHandler;
    }

    public void run() {
      deviceHandler.setupEPOSCallback(out);
      if (this.deviceHandler.getTerminalStatus() == null) {
        this.deviceHandler.postToEPOS(ErrorHandler.buildErrorObject(ErrorType.deviceConnectionError));
        return;
      }

      //Log a timestamp of when this transaction started
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
      String formattedDate = sdf.format(new Date());
      //Log the type of transaction we are processing, for reference when checking logs
      System.out.println(formattedDate + " - " + msg.type + " request from EPOS");
      String logJson = gson.toJson(msg);
      System.out.println(logJson);

      ResponseMessage responseMessage = new ResponseMessage("error");
      responseMessage.prompt = "Unknown action requested.";

      if (Objects.equals(msg.type, "Sale")) {
        responseMessage = this.deviceHandler.doSale(msg.value);
      }
      else if (Objects.equals(msg.type, "Refund")) {
        responseMessage = this.deviceHandler.doRefund(msg.value);
      }
      else if (Objects.equals(msg.type, "Reversal")) {
        responseMessage = this.deviceHandler.doReversal(msg.value, msg.evoTransId);
      }
      else if (Objects.equals(msg.type, "Status")) {
        responseMessage = this.deviceHandler.getTerminalState();
      }
      else if (Objects.equals(msg.type, "Batch")) {
        responseMessage = this.deviceHandler.handleBatch();
      }
      else if (Objects.equals(msg.type, "Reconcile")) {
        responseMessage = this.deviceHandler.forceReconciliation();
      }
      else if (Objects.equals(msg.type, "Continue")) {
        responseMessage = this.deviceHandler.continueTransaction();
      }
      else if (Objects.equals(msg.type, "Last")) {
        responseMessage = this.deviceHandler.getLastTransaction();
      }
      else if (Objects.equals(msg.type, "Test")) {
        responseMessage = this.deviceHandler.testConnection();
      }
      else if (Objects.equals(msg.type, "Update")) {
        responseMessage = this.deviceHandler.update();
      }
      else if (Objects.equals(msg.type, "Response")) {
        this.deviceHandler.setCallbackResponse(msg);
        return;
      }

      String json = gson.toJson(responseMessage) + (char) 4 + (char) 3;
      this.deviceHandler.postToEPOS(json.getBytes());
    }

  }
}


