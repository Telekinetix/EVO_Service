import ecrlib.api.EcrCallbackDataInput;
import ecrlib.api.EcrCallbacks;
import ecrlib.api.enums.EcrTerminalStatus;
import models.EPOSMessage;

import java.io.DataOutputStream;

public class DeviceCallbacks implements EcrCallbacks {
  private final DevicePrintoutHandler printoutHandler;
  private final DeviceHandler deviceHandler;

  public DeviceCallbacks(DevicePrintoutHandler printoutHandler, DeviceHandler deviceHandler) {
    this.printoutHandler = printoutHandler;
    this.deviceHandler = deviceHandler;
  }

  @Override
  public void handleDevLog(String s) {

  }

  @Override
  public void handleCommLog(String s) {

  }

  @Override
  public void handleBusLog(String s) {
  }

  @Override
  public void handleStatusChange(EcrTerminalStatus ecrTerminalStatus) {
    this.deviceHandler.sendEPOSMessage("AAAAA");
  }

  @Override
  public boolean askForSignature(String s) {
    printoutHandler.generateMerchantPrintout();
    this.deviceHandler.sendEPOSMessage("BBB");
    EPOSMessage response = this.deviceHandler.waitForEPOSResponse();
    // TODO: get response from cashier
    this.deviceHandler.clearEPOSResponse();
    return true;
  }

  @Override
  public boolean askForCopy(String s) {
    return false;
  }

  @Override
  public int askForCurrency(String[] strings) {
    return 0;
  }

  @Override
  public int askForSelection(String[] strings, String s) {
    return 0;
  }

  @Override
  public boolean waitForCard(String s) {
    return false;
  }

  @Override
  public void waitForCardRemoval(String s) {

  }

  @Override
  public boolean waitForPin(String s) {
    return false;
  }

  @Override
  public void showOkScreen(String s) {

  }

  @Override
  public boolean showYesNoScreen(String s) {
    return false;
  }

  @Override
  public void showPromptScreen(String s) {

  }

  @Override
  public String getCashbackAmount(String s, int i, int i1) {
    return null;
  }

  @Override
  public String getAuthorizationCode(String s, int i, int i1) {
    return null;
  }

  @Override
  public String getUserData(String s, int i, int i1, EcrCallbackDataInput ecrCallbackDataInput) {
    return null;
  }

  @Override
  public String getAmount(String s, int i, int i1) {
    return null;
  }
}
