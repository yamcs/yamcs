package com.spaceapplications.yamcs.scpi.commander;

import java.util.List;
import java.util.stream.Collectors;

public class HelpCommand extends Command {
    private List<Command> commands;

    public HelpCommand(String cmd, String description, HasContext context, List<Command> commands) {
        super(cmd, description, context);
        this.commands = commands;
    }

    @Override
    String handleExecute(String args) {
        return "Available commands:\n" + commands.stream().map((Command c) -> String.format(COL_FORMAT, c.cmd(), c.description()))
                .collect(Collectors.joining("\n"));
    }
}