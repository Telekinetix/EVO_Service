package models;

public class EPOSMessage {
  public Integer id;
  public Integer saleId;
  public Integer transId;
  public String type;
  public String currency;
  public String value;

  public EPOSMessage(Integer id, String type, String currency, String value, Integer saleId, Integer transId) {
    this.id = id;
    this.type = type;
    this.currency = currency;
    this.value = value;
    this.saleId = saleId;
    this.transId = transId;
  }
}
