package com.spaceapplications.yamcs.scpi.commander;

import java.util.function.BiFunction;

class Command {
    public static final String DEFAULT_PROMPT = "$ ";
    private String cmd;
    private String description;
    private BiFunction<Command, String, String> exec;
    private String prompt = DEFAULT_PROMPT;

    public static Command of(String cmd, String description, BiFunction<Command, String, String> exec) {
      Command c = new Command();
      c.cmd = cmd;
      c.description = description;
      c.exec = exec;
      return c;
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
      return exec.apply(this, args);
    }
  }