package com.spaceapplications.yamcs.scpi.commander;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.spaceapplications.yamcs.scpi.Config;

public class Commander {
  private static String COL_FORMAT = "%-20s %s";
  private Optional<Command> context = Optional.empty();
  private List<Command> commands = new ArrayList<>();

  @SuppressWarnings("serial")
  public class ExitException extends RuntimeException {
    ExitException(String msg) {
      super(msg);
    }
  }

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
      Command contextCmd = Command.of("", "", (c, cmd) -> {
        if (isCtrlD(cmd)) {
          c.setPrompt(Command.DEFAULT_PROMPT);
          command.setPrompt(Command.DEFAULT_PROMPT);
          context = Optional.empty();
          return "\ndisconnect from " + deviceId;
        }
        return deviceId + "(" + cmd + ")";
      });
      contextCmd.setPrompt(prompt);
      context = Optional.of(contextCmd);
      return "connect to: " + deviceId;
    }));

    commands.add(Command.of("help", "Prints this description.", (command, na) -> {
      return "Available commands:\n" + commands.stream().map(c -> String.format(COL_FORMAT, c.cmd(), c.description()))
          .collect(Collectors.joining("\n"));
    }));
  }

  public String confirm() {
    return "Connected. Run help for more info.\n" + Command.DEFAULT_PROMPT;
  }

  public String execute(String cmd) {
    return context.map(command -> exec(command, cmd)).orElseGet(() -> execMatching(cmd));
  }

  private String handleUnknownCmd(String cmd) {
    if (isCtrlD(cmd)) {
      throw new ExitException("bye");
    } else if (isLineEnd(cmd))
      return Command.DEFAULT_PROMPT;
    else
      return cmd + ": command not found\n" + Command.DEFAULT_PROMPT;
  }

  private String execMatching(String cmd) {
    return commands.stream().filter(command -> cmd.startsWith(command.cmd())).findFirst()
        .map(command -> exec(command, cmd)).orElse(handleUnknownCmd(cmd));
  }

  private String exec(Command command, String cmd) {
    return isLineEnd(cmd) ? command.prompt() : command.execute(cmd) + "\n" + command.prompt();
  }

  public static boolean isLineEnd(String msg) {
    return "\n".equals(msg) || "\r\n".equals(msg);
  }

  public static boolean isCtrlD(String msg) {
    return "\4".equals(msg);
  }
}