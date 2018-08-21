package com.spaceapplications.yamcs.scpi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Commander {
  private static String PROMPT = "\r\n$ ";

  private String context = "";

  private static class Command {
    private String cmd;
    private String description;
    private Supplier<String> exec;

    public static Command of(String cmd, String description, Supplier<String> exec) {
      Command c = new Command();
      c.cmd = cmd;
      c.description = description;
      c.exec = exec;
      return c;
    }

    public String cmd() {
      return cmd;
    }

    public String description() {
      return description;
    }

    public String execute() {
      return exec.get();
    }
  }

  private List<Command> commands = new ArrayList<>();

  public Commander(Config config) {
    commands.add(Command.of("device list", "List available devices to manage.", () -> config.devices.toString()));
    commands.add(Command.of("help", "Prints this description.", () -> {
      return "Available commands:\n" + commands.stream().map(c -> String.format("%-20s %s", c.cmd(), c.description())).collect(Collectors.joining("\n"));
    }));
  }

  public String confirm() {
    return "Connected. Run help for more info." + PROMPT;
  }

  public String execute(String cmd) {
    String result = commands.stream().filter(c -> c.cmd.startsWith(cmd)).findFirst().map(c -> c.execute())
        .orElse(cmd + ": command not found");
    return context + result + PROMPT;
  }
}