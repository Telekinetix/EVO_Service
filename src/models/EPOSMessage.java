package models;

public class EPOSMessage {
  public Integer id;
  public Integer saleId;
  public Integer transId;
  public String type;
  public String currency;
  public String value;
  public String evoTransId;

  public EPOSMessage(Integer id, String type, String currency, String value, Integer saleId, Integer transId, String evoTransId) {
    this.id = id;
    this.type = type;
    this.currency = currency;
    this.value = value;
    this.saleId = saleId;
    this.transId = transId;
    this.evoTransId = evoTransId;
  }
}
