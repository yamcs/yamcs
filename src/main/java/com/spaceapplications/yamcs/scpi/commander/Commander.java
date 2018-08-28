package com.spaceapplications.yamcs.scpi.commander;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.spaceapplications.yamcs.scpi.commander.Command.HasContext;

public class Commander implements HasContext {
  
  private Optional<Command> context = Optional.empty();
  private List<Command> commands = new ArrayList<>();

  @SuppressWarnings("serial")
  public class ExitException extends RuntimeException {
    ExitException(String msg) {
      super(msg);
    }
  }

  public void addAll(List<Command> commands) {
    this.commands.addAll(commands);
  }

  public String confirm() {
    return "Connected. Run help for more info. (ctrl+d to exit)\n" + Command.DEFAULT_PROMPT;
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
      return cmd.trim() + ": command not found\n" + Command.DEFAULT_PROMPT;
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

  @Override
  public Optional<Command> contextCmd() {
    return context;
  }

  @Override
  public void setContextCmd(Command context) {
    this.context = Optional.ofNullable(context);
  }

  @Override
  public void clearContextCmd() {
    this.context = Optional.empty();
  }
}