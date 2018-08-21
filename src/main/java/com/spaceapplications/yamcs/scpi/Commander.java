package com.spaceapplications.yamcs.scpi;

public class Commander {
  public static String CONFIRM = "confirm";

  private static String PROMPT = "\r\n$ ";

  private Config config;

  public Commander(Config config) {
    this.config = config;
  }

  public String execute(String cmd) {
    String msg = "error";
    if (CONFIRM.equals(cmd))
      msg = "Connected. Run help for more info.";
    else
      msg = cmd + ": command not found";
    return msg + PROMPT;
  }
}