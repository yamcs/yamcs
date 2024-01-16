package org.yamcs.activities;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.security.User;
import org.yamcs.yarch.Bucket;

public class CommandStackExecution extends ActivityExecution {

    private Processor processor;
    private Bucket bucket;
    private String stackName;
    private User user;

    public CommandStackExecution(
            ActivityService activityService,
            CommandStackExecutor executor,
            Activity activity,
            Processor processor,
            Bucket bucket,
            String stackName,
            User user) {
        super(activityService, executor, activity);
        this.processor = processor;
        this.bucket = bucket;
        this.stackName = stackName;
        this.user = user;
    }

    @Override
    public Void run() throws Exception {
        var yamcs = YamcsServer.getServer();
        var cmdManager = processor.getCommandingManager();
        var mdb = MdbFactory.getInstance(processor.getInstance());
        var origin = InetAddress.getLocalHost().getHostName();
        var seq = 0;

        var bytes = bucket.getObject(stackName);
        var json = new String(bytes, StandardCharsets.UTF_8);
        var stack = CommandStack.fromJson(json, mdb);

        for (var stackedCommand : stack.getCommands()) {
            logActivityInfo("Running command " + stackedCommand);

            var args = new LinkedHashMap<String, Object>();
            for (var arg : stackedCommand.getAssignments().entrySet()) {
                args.put(arg.getKey().getName(), arg.getValue());
            }

            var preparedCommand = cmdManager.buildCommand(
                    stackedCommand.getMetaCommand(), args, origin, seq++, user);
            if (stackedCommand.getComment() != null) {
                preparedCommand.setComment(stackedCommand.getComment());
            }

            for (var entry : stackedCommand.getExtra().entrySet()) {
                var commandOption = yamcs.getCommandOption(entry.getKey());
                if (commandOption == null) {
                    throw new IllegalArgumentException("Unknown command option '" + entry.getKey() + "'");
                }
                preparedCommand.addAttribute(CommandHistoryAttribute.newBuilder()
                        .setName(entry.getKey())
                        .setValue(commandOption.coerceValue(entry.getValue()))
                        .build());
            }

            cmdManager.sendCommand(user, preparedCommand);

            int waitTime = stackedCommand.getWaitTime();
            if (waitTime == -1) {
                waitTime = stack.getWaitTime();
            }
            if (waitTime > 0) {
                logActivityInfo("Waiting for " + waitTime + " ms");
                Thread.sleep(waitTime);
            }
        }

        return null;
    }

    @Override
    public void stop() throws Exception {
        // NOP
    }
}
