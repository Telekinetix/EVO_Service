import ecrlib.api.EcrCallbackDataInput;
import ecrlib.api.EcrCallbacks;
import ecrlib.api.enums.EcrTerminalStatus;
import models.ResponseMessage;
import models.EPOSMessage;

import java.util.Objects;

public class DeviceCallbacks implements EcrCallbacks {
  private final DevicePrintoutHandler printoutHandler;
  private final DeviceHandler deviceHandler;

  public DeviceCallbacks(DevicePrintoutHandler printoutHandler, DeviceHandler deviceHandler) {
    this.printoutHandler = printoutHandler;
    this.deviceHandler = deviceHandler;
  }

  @Override
  public void handleDevLog(String s) {
    System.out.println(s);
  }

  @Override
  public void handleCommLog(String s) {
    System.out.println(s);

  }

  @Override
  public void handleBusLog(String s) {
    System.out.println(s);
  }

  @Override
  public void handleStatusChange(EcrTerminalStatus ecrTerminalStatus) {
    ResponseMessage msg = new ResponseMessage("handleStatusChange");
    msg.prompt = ecrTerminalStatus.name();
    this.deviceHandler.sendCallbackMessage(msg);
  }

  @Override
  public boolean askForSignature(String prompt) {
    String receiptToSign = printoutHandler.generateMerchantPrintout();
    ResponseMessage msg = new ResponseMessage("askForSignature");
    msg.value = receiptToSign;
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return Objects.equals(resp, "true");
  }

  @Override
  public boolean askForCopy(String prompt) {
    ResponseMessage msg = new ResponseMessage("askForCopy");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return Objects.equals(resp, "true");
  }

  @Override
  public int askForCurrency(String[] options) {
    ResponseMessage msg = new ResponseMessage("askForCurrency");
    msg.values = options;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return Integer.parseInt(resp);
  }

  @Override
  public int askForSelection(String[] options, String prompt) {
    ResponseMessage msg = new ResponseMessage("askForSelection");
    msg.prompt = prompt;
    msg.values = options;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return Integer.parseInt(resp);
  }

  @Override
  public boolean waitForCard(String prompt) {
    ResponseMessage msg = new ResponseMessage("waitForCard");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    // non-blocking wait for card screen
    return true;
  }

  @Override
  public void waitForCardRemoval(String prompt) {
    ResponseMessage msg = new ResponseMessage("waitForCardRemoval");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
  }

  @Override
  public boolean waitForPin(String prompt) {
    ResponseMessage msg = new ResponseMessage("waitForPin");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    // non-blocking wait for pin screen
    return true;
  }

  @Override
  public void showOkScreen(String prompt) {
    ResponseMessage msg = new ResponseMessage("showOkScreen");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
  }

  @Override
  public boolean showYesNoScreen(String prompt) {
    ResponseMessage msg = new ResponseMessage("showYesNoScreen");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return Objects.equals(resp, "true");
  }

  @Override
  public void showPromptScreen(String prompt) {
    ResponseMessage msg = new ResponseMessage("showPromptScreen");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
  }

  @Override
  public String getCashbackAmount(String prompt, int minLength, int maxLength) {
    // Not doing cashback
    return null;
  }

  @Override
  public String getAuthorizationCode(String prompt, int minLength, int maxLength) {
    ResponseMessage msg = new ResponseMessage("getAuthorizationCode");
    msg.prompt = prompt;
    msg.minLength = minLength;
    msg.maxLength = maxLength;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return resp;
  }

  @Override
  public String getUserData(String prompt, int minLength, int maxLength, EcrCallbackDataInput ecrCallbackDataInput) {
    boolean isDataCorrect = false;
    boolean anotherTry = false;
    ResponseMessage msg = new ResponseMessage("getUserData");
    msg.prompt = prompt;
    msg.minLength = minLength;
    msg.maxLength = maxLength;

    String resp = null;

    while (!isDataCorrect) {
      this.deviceHandler.sendCallbackMessage(msg);
      EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
      resp = response.value;
      this.deviceHandler.clearCallbackResponse();
      isDataCorrect = true;
      for (int i = 0; i < resp.length(); i++) {
        if (!ecrCallbackDataInput.isCharacterAllowed(resp.charAt(i))) {
          if (!anotherTry) {
            prompt = "Incorrect data typed - please try again.\n" + prompt;
            anotherTry = true;
          }
          isDataCorrect = false;
          break;
        }
      }
    }
    return resp;
  }

  @Override
  public String getAmount(String prompt, int minLength, int maxLength) {
    ResponseMessage msg = new ResponseMessage("getAmount");
    msg.prompt = prompt;
    msg.minLength = minLength;
    msg.maxLength = maxLength;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return resp;
  }
}
