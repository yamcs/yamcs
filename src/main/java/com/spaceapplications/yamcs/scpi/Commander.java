package com.spaceapplications.yamcs.scpi;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Commander {
  private static String COL_FORMAT = "%-20s %s";
  private Optional<Command> context = Optional.empty();

  private static class Command {
    private static final String DEFAULT_PROMPT = "$ ";
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
      String args = cmd.replaceFirst(this.cmd, "").trim();
      return exec.apply(this, args);
    }
  }

  private List<Command> commands = new ArrayList<>();

  public Commander(Config config) {
    commands.add(Command.of("device list", "List available devices to manage.", (command, na) -> {
      String header = String.format("Available devices:\n" + COL_FORMAT + "\n", "ID", "DESCRIPTION");
      String devList = config.devices.entrySet().stream()
          .map(set -> String.format(COL_FORMAT, set.getKey(), set.getValue().description))
          .collect(Collectors.joining("\n"));
      return header + devList;
    }));

    commands.add(Command.of("device inspect", "Print device configuration details.", (command, deviceId) -> {
      return Optional.ofNullable(config.devices).map(devices -> devices.get(deviceId)).map(Config::dump)
          .orElse(MessageFormat.format("device \"{0}\" not found", deviceId));
    }));

    commands.add(Command.of("device connect", "Connect and interact with a given device.", (command, deviceId) -> {
      String prompt = "device:" + deviceId + Command.DEFAULT_PROMPT;
      command.setPrompt(prompt);
      Command contextCmd = Command.of("", "", (na, cmd) -> cmd.isEmpty() ? "" : deviceId + "(" + cmd + ")");
      contextCmd.setPrompt("device:" + deviceId + Command.DEFAULT_PROMPT);
      context = Optional.of(contextCmd);
      return "connect to: " + deviceId;
    }));

    commands.add(Command.of("help", "Prints this description.", (command, na) -> {
      return "Available commands:\n" + commands.stream().map(c -> String.format(COL_FORMAT, c.cmd(), c.description()))
          .collect(Collectors.joining("\n"));
    }));

  }

  public String confirm() {
    return "Connected. Run help for more info." + "\r\n$ ";
  }

  public String execute(String cmd) {
    return context.map(command -> exec(command, cmd)).orElse(execMatching(cmd));
  }

  private String execMatching(String cmd) {
    return commands.stream().filter(command -> cmd.startsWith(command.cmd())).findFirst()
        .map(command -> exec(command, cmd))
        .orElse(cmd.isEmpty() ? Command.DEFAULT_PROMPT : cmd + ": command not found\n" + Command.DEFAULT_PROMPT);
  }

  private String exec(Command command, String cmd) {
    return cmd.isEmpty() ? command.prompt() : command.execute(cmd) + "\n" + command.prompt();
  }
}