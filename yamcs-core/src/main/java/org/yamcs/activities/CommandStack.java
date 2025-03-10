package org.yamcs.activities;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.mdb.Mdb;
import org.yamcs.xtce.MetaCommand;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class CommandStack {

    private List<Step> steps = new ArrayList<>();
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

    public void addStep(Step step) {
        steps.add(step);
    }

    public List<Step> getSteps() {
        return steps;
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

        if (stackObject.has("steps")) { // v2
            for (var stepEl : stackObject.getAsJsonArray("steps")) {
                var stepObject = stepEl.getAsJsonObject();
                var stepType = stepObject.get("type").getAsString();
                if (stepType.equals("command")) {
                    var command = parseCommand(mdb, stepObject);
                    stack.addStep(command);
                } else if (stepType.equals("verify")) {
                    var verify = parseVerify(mdb, stepObject);
                    stack.addStep(verify);
                }
            }
        } else if (stackObject.has("commands")) { // v1
            for (var commandEl : stackObject.getAsJsonArray("commands")) {
                var command = parseCommand(mdb, commandEl.getAsJsonObject());
                stack.addStep(command);
            }
        }

        return stack;
    }

    private static StackedVerify parseVerify(Mdb mdb, JsonObject verifyObject) throws CommandStackParseException {
        var stackedVerify = new StackedVerify();

        if (verifyObject.has("condition")) {
            var comparisonsArray = verifyObject.get("condition").getAsJsonArray();
            for (var comparisonEl : comparisonsArray) {
                var comparisonObject = comparisonEl.getAsJsonObject();

                var parameterName = comparisonObject.get("parameter").getAsString();
                var parameter = mdb.getParameter(parameterName);
                if (parameter == null) {
                    throw new CommandStackParseException(
                            "Parameter " + parameterName + " does not exist in MDB");
                }

                var operator = comparisonObject.get("operator").getAsString();

                Object value;
                var jsonValue = comparisonObject.get("value").getAsJsonPrimitive();
                if (jsonValue.isBoolean()) {
                    value = jsonValue.getAsBoolean();
                } else if (jsonValue.isNumber()) {
                    value = jsonValue.getAsNumber();
                } else if (jsonValue.isString()) {
                    value = jsonValue.getAsString();
                } else {
                    throw new CommandStackParseException("Unexpected comparand of class " + jsonValue.getClass());
                }

                stackedVerify.addComparison(parameter, operator, value);
            }
        }

        if (verifyObject.has("delay")) {
            stackedVerify.setDelay(verifyObject.get("delay").getAsLong());
        }
        if (verifyObject.has("timeout")) {
            stackedVerify.setTimeout(verifyObject.get("timeout").getAsLong());
        }

        return stackedVerify;
    }

    private static StackedCommand parseCommand(Mdb mdb, JsonObject commandObject) throws CommandStackParseException {
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
        if (commandObject.has("stream")) {
            var stream = commandObject.get("stream").getAsString();
            command.setStream(stream);
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
        if (commandObject.has("extraOptions")) {
            var extraArray = commandObject.get("extraOptions").getAsJsonArray();
            for (var extraEl : extraArray) {
                var extraObject = extraEl.getAsJsonObject();

                var extraId = extraObject.get("id").getAsString();

                var extraValue = extraObject.get("value");
                if (extraValue.isJsonNull()) {
                    command.setExtra(extraId, null);
                } else if (extraValue.isJsonPrimitive()) {
                    var primitive = extraValue.getAsJsonPrimitive();
                    if (primitive.isBoolean()) {
                        command.setExtra(extraId, primitive.getAsBoolean());
                    } else if (primitive.isNumber()) {
                        command.setExtra(extraId, primitive.getAsNumber());
                    } else if (primitive.isString()) {
                        command.setExtra(extraId, primitive.getAsString());
                    } else {
                        throw new CommandStackParseException("Unexpected value type for " + extraValue);
                    }
                } else {
                    throw new CommandStackParseException("Unexpected value type for " + extraValue);
                }
            }
        }

        return command;
    }
}
