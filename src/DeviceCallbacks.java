import ecrlib.api.EcrCallbackDataInput;
import ecrlib.api.EcrCallbacks;
import ecrlib.api.enums.EcrTerminalStatus;
import models.CallbackMessage;
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
    CallbackMessage msg = new CallbackMessage("handleStatusChange");
    msg.prompt = ecrTerminalStatus.name();
    this.deviceHandler.sendCallbackMessage(msg);
  }

  @Override
  public boolean askForSignature(String s) {
    printoutHandler.generateMerchantPrintout();
    // TODO: get response from cashier
    return true;
  }

  @Override
  public boolean askForCopy(String prompt) {
    CallbackMessage msg = new CallbackMessage("askForCopy");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return Objects.equals(resp, "true");
  }

  @Override
  public int askForCurrency(String[] options) {
    CallbackMessage msg = new CallbackMessage("askForCurrency");
    msg.values = options;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return Integer.parseInt(resp);
  }

  @Override
  public int askForSelection(String[] options, String prompt) {
    CallbackMessage msg = new CallbackMessage("askForSelection");
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
    CallbackMessage msg = new CallbackMessage("waitForCard");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    // non-blocking wait for card screen
    return true;
  }

  @Override
  public void waitForCardRemoval(String prompt) {
    CallbackMessage msg = new CallbackMessage("waitForCardRemoval");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
  }

  @Override
  public boolean waitForPin(String prompt) {
    CallbackMessage msg = new CallbackMessage("waitForPin");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    // non-blocking wait for pin screen
    return true;
  }

  @Override
  public void showOkScreen(String prompt) {
    CallbackMessage msg = new CallbackMessage("showOkScreen");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
  }

  @Override
  public boolean showYesNoScreen(String prompt) {
    CallbackMessage msg = new CallbackMessage("showYesNoScreen");
    msg.prompt = prompt;
    this.deviceHandler.sendCallbackMessage(msg);
    EPOSMessage response = this.deviceHandler.waitForCallbackResponse();
    String resp = response.value;
    this.deviceHandler.clearCallbackResponse();
    return Objects.equals(resp, "true");
  }

  @Override
  public void showPromptScreen(String prompt) {
    CallbackMessage msg = new CallbackMessage("showPromptScreen");
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
    CallbackMessage msg = new CallbackMessage("getAuthorizationCode");
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
    CallbackMessage msg = new CallbackMessage("getUserData");
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
    CallbackMessage msg = new CallbackMessage("getAmount");
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
