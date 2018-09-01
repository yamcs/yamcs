package com.spaceapplications.yamcs.scpi.telnet;

public abstract class Command {

    private String cmd;
    private String description;
    protected Commander commander;

    public Command(String cmd, String description, Commander commander) {
        this.cmd = cmd;
        this.description = description;
        this.commander = commander;
    }

    public String cmd() {
        return cmd;
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
