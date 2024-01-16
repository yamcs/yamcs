package org.yamcs.activities;

import java.net.InetAddress;
import java.util.Map;

import org.yamcs.Processor;
import org.yamcs.YamcsServer;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.security.User;

public class CommandExecution extends ActivityExecution {

    private Processor processor;
    private String commandName;
    private Map<String, Object> args;
    private Map<String, Object> extra;
    private User user;

    public CommandExecution(
            ActivityService activityService,
            CommandExecutor executor,
            Activity activity,
            Processor processor,
            String commandName,
            Map<String, Object> args,
            Map<String, Object> extra,
            User user) {
        super(activityService, executor, activity);
        this.processor = processor;
        this.commandName = commandName;
        this.args = args;
        this.extra = extra;
        this.user = user;
    }

    @Override
    public Void run() throws Exception {
        var cmdManager = processor.getCommandingManager();

        var mdb = MdbFactory.getInstance(processor.getInstance());
        var cmd = mdb.getMetaCommand(commandName);

        var origin = InetAddress.getLocalHost().getHostName();
        var preparedCommand = cmdManager.buildCommand(cmd, args, origin, 0, user);

        if (extra != null && !extra.isEmpty()) {
            extra.forEach((k, v) -> {
                var commandOption = YamcsServer.getServer().getCommandOption(k);
                if (commandOption == null) {
                    throw new IllegalArgumentException("Unknown command option '" + k + "'");
                }

                preparedCommand.addAttribute(CommandHistoryAttribute.newBuilder()
                        .setName(k)
                        .setValue(commandOption.coerceValue(v))
                        .build());
            });
        }

        cmdManager.sendCommand(user, preparedCommand);

        return null;
    }

    @Override
    public void stop() throws Exception {
        // NOP
    }
}
