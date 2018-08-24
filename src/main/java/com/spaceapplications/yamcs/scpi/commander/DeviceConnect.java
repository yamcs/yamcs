package com.spaceapplications.yamcs.scpi.commander;

public class DeviceConnect extends Command {
    public DeviceConnect(String cmd, String description, HasContext context) {
        super(cmd, description, context);
    }

    @Override
    String handleExecute(String deviceId) {
        String prompt = "device:" + deviceId + Command.DEFAULT_PROMPT;
        setPrompt(prompt);
        Command parent = this;
        Command contextCmd = new Command("", "", context) {
            @Override
            String handleExecute(String cmd) {
                if (Commander.isCtrlD(cmd)) {
                    setPrompt(Command.DEFAULT_PROMPT);
                    parent.setPrompt(Command.DEFAULT_PROMPT);
                    context.clearContextCmd();
                    return "\ndisconnect from " + deviceId;
                }
                return deviceId + "(" + cmd + ")";
            }
        };
        contextCmd.setPrompt(prompt);
        context.setContextCmd(contextCmd);
        return "connect to: " + deviceId;
    }

}