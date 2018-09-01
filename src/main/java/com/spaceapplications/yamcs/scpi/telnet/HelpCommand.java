package com.spaceapplications.yamcs.scpi.telnet;

import java.util.List;
import java.util.stream.Collectors;

public class HelpCommand extends Command {

    private List<Command> commands;

    public HelpCommand(String cmd, String description, Commander commander, List<Command> commands) {
        super(cmd, description, commander);
        this.commands = commands;
    }

    @Override
    String handleExecute(String args) {
        return "Available commands:\n"
                + commands.stream()
                        .map((Command c) -> String.format("%-10s %s", c.cmd(), c.description()))
                        .collect(Collectors.joining("\n"));
    }
}
