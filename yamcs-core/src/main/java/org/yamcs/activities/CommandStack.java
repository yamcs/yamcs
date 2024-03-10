package org.yamcs.activities;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.mdb.Mdb;
import org.yamcs.xtce.MetaCommand;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class CommandStack {

    private List<StackedCommand> commands = new ArrayList<>();
    private String acknowledgment = CommandHistoryPublisher.AcknowledgeQueued_KEY;
    private int waitTime = 0;

    public void setAcknowledgment(String acknowledgment) {
        this.acknowledgment = acknowledgment;
    }

    public String getAcknowledgment() {
        return acknowledgment;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public void addCommand(StackedCommand command) {
        commands.add(command);
    }

    public List<StackedCommand> getCommands() {
        return commands;
    }

    public static CommandStack fromJson(String json, Mdb mdb) throws CommandStackParseException {
        var stack = new CommandStack();

        var gson = new Gson();
        var stackObject = gson.fromJson(json, JsonObject.class);

        if (stackObject.has("advancement")) {
            var advancementObject = stackObject.get("advancement").getAsJsonObject();
            if (advancementObject.has("acknowledgment")) {
                var acknowledgment = advancementObject.get("acknowledgment").getAsString();
                stack.setAcknowledgment(acknowledgment);
            }
            if (advancementObject.has("wait")) {
                var wait = advancementObject.get("wait").getAsInt();
                if (wait >= 0) {
                    stack.setWaitTime(wait);
                }
            }
        }

        if (stackObject.has("commands")) {
            for (var commandEl : stackObject.getAsJsonArray("commands")) {
                var commandObject = commandEl.getAsJsonObject();

                var name = commandObject.get("name").getAsString();

                MetaCommand mdbInfo;
                if (commandObject.has("namespace")) {
                    var namespace = commandObject.get("namespace").getAsString();
                    mdbInfo = mdb.getMetaCommand(namespace, name);
                    if (mdbInfo == null) {
                        throw new CommandStackParseException(
                                "Command " + name + " (" + namespace + ") does not exist in MDB");
                    }
                } else {
                    mdbInfo = mdb.getMetaCommand(name);
                    if (mdbInfo == null) {
                        throw new CommandStackParseException(
                                "Command " + name + " does not exist in MDB");
                    }
                }

                var command = new StackedCommand();
                command.setMetaCommand(mdbInfo);
                if (commandObject.has("comment")) {
                    var comment = commandObject.get("comment").getAsString();
                    command.setComment(comment);
                }
                if (commandObject.has("advancement")) {
                    var advancementObject = commandObject.get("advancement").getAsJsonObject();
                    if (advancementObject.has("acknowledgment")) {
                        var acknowledgment = advancementObject.get("acknowledgment").getAsString();
                        command.setAcknowledgment(acknowledgment);
                    }
                    if (advancementObject.has("wait")) {
                        var wait = advancementObject.get("wait").getAsInt();
                        if (wait >= 0) {
                            command.setWaitTime(wait);
                        }
                    }
                }
                if (commandObject.has("arguments")) {
                    var argumentsArray = commandObject.get("arguments").getAsJsonArray();
                    for (var argumentEl : argumentsArray) {
                        var argumentObject = argumentEl.getAsJsonObject();

                        var argName = argumentObject.get("name").getAsString();
                        var argValue = argumentObject.get("value");
                        var argInfo = mdbInfo.getEffectiveArgument(argName);
                        if (argInfo == null) {
                            throw new CommandStackParseException(
                                    "Argument " + argName + " does not exist in MDB for command " + name);
                        }
                        if (argValue.isJsonNull()) {
                            command.addAssignment(argInfo, null);
                        } else if (argValue.isJsonPrimitive()) {
                            command.addAssignment(argInfo, argValue.getAsString());
                        } else if (argValue.isJsonArray()) {
                            command.addAssignment(argInfo, argValue.getAsJsonArray().toString());
                        } else if (argValue.isJsonObject()) {
                            command.addAssignment(argInfo, argValue.getAsJsonObject().toString());
                        } else {
                            throw new CommandStackParseException("Unexpected value: " + argValue);
                        }
                    }
                }

                stack.addCommand(command);
            }
        }

        return stack;
    }
}
