package models;
public class Config {
  public String terminalIp;
  public Integer terminalPort;
  public Integer terminalTimeout;
  public Integer serverPort;
  public Integer tid;
  Config(String terminalIp, Integer terminalPort, Integer terminalTimeout, Integer serverPort, Integer tid) {
    this.terminalIp = terminalIp;
    this.terminalPort = terminalPort;
    this.terminalTimeout = terminalTimeout;
    this.serverPort = serverPort;
    this.tid = tid;
  }
}
