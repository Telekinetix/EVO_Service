package models;

public class CallbackMessage {
  public String type;
  public String prompt;
  public Integer minLength;
  public Integer maxLength;
  public String[] values;
  public String value;

  public CallbackMessage(String type) {
    this.type = type;
  }
  public CallbackMessage(String type, String prompt, Integer minLength, Integer maxLength, String[] values, String value) {
    this.type = type;
    this.prompt = prompt;
    this.minLength = minLength;
    this.maxLength = maxLength;
    this.values = values;
    this.value = value;
  }
}
