package com.spaceapplications.yamcs.scpi.commander;

import java.util.Optional;

public abstract class Command {
  public static final String DEFAULT_PROMPT = "$ ";
  public static String COL_FORMAT = "%-20s %s";

  private String prompt = DEFAULT_PROMPT;
  private String cmd;
  private String description;
  protected HasContext context;

  public interface HasContext {
    public Optional<Command> contextCmd();
    public void setContextCmd(Command context);
    public void clearContextCmd();
  }
  // private BiFunction<Command, String, String> exec;

  // public static Command of(String cmd, String description, BiFunction<Command,
  // String, String> exec) {
  // Command c = new Command();
  // c.cmd = cmd;
  // c.description = description;
  // c.exec = exec;
  // return c;
  // }

  public Command(String cmd, String description, HasContext context) {
    this.cmd = cmd;
    this.description = description;
    this.context = context;
  }

  public String cmd() {
    return cmd;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String prompt() {
    return prompt;
  }

  public String description() {
    return description;
  }

  public String execute(String cmd) {
    String args = cmd.replaceFirst(this.cmd + " ", "").replaceAll("\r", "").replaceAll("\n", "");
    return handleExecute(args);
  }

  abstract String handleExecute(String args);
}