import com.google.gson.Gson;
import ecrlib.api.enums.EcrStatus;
import ecrlib.api.enums.EcrTerminalStatus;
import models.CallbackMessage;
import models.Config;
import models.EPOSMessage;
import models.ErrorType;

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

      String response = "";

      if (Objects.equals(msg.type, "Sale")) {
        response = this.deviceHandler.doSale(msg.value);
      }
      else if (Objects.equals(msg.type, "Status")) {
        EcrTerminalStatus res = this.deviceHandler.getTerminalState();
        response = "{\"status\": \"" + res.name() + "\"}";

      }
      else if (Objects.equals(msg.type, "Batch")) {
        response = "{\"batchData\": \"" + this.deviceHandler.handleBatch() + "\"}";
      }
      else if (Objects.equals(msg.type, "Response")) {
        this.deviceHandler.setCallbackResponse(msg);
        return;
      }
      else if (Objects.equals(msg.type, "Continue")) {
        response = this.deviceHandler.continueTransaction();
      }

      String json = response + (char) 4;

      //If processing a cancel transaction from EPOS, log the ingenico response
      formattedDate = sdf.format(new Date());
      if (Objects.equals(msg.type, "Cancel")) {
        System.out.println(formattedDate + " - " + json);
      } else {
        System.out.println(formattedDate + " - " + msg.type + " completed. Sending response to EPOS");
      }

      this.deviceHandler.postToEPOS(json.getBytes());
    }

  }
}


