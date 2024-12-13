package models;

import com.google.gson.JsonObject;

public class ResponseMessage {
  public String type;
  public String prompt;
  public Integer minLength;
  public Integer maxLength;
  public String[] values;
  public JsonObject value;
  public String status;

  public ResponseMessage(String type) {
    this.type = type;
  }
  public ResponseMessage(String type, String prompt, Integer minLength, Integer maxLength, String[] values, JsonObject value, String status) {
    this.type = type;
    this.prompt = prompt;
    this.minLength = minLength;
    this.maxLength = maxLength;
    this.values = values;
    this.value = value;
    this.status = status;
  }
}
