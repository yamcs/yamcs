package org.yamcs.cli;

import org.yamcs.api.InstanceClient;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "List processors")
public class ProcessorsCli extends Command {

    public ProcessorsCli(Command parent) {
        super("processors", parent);
        addSubCommand(new ProcessorsList());
        setInstanceRequired(true);
    }

    @Parameters(commandDescription = "List processors")
    class ProcessorsList extends Command {

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public ProcessorsList() {
            super("list", ProcessorsCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            if (global) {
                yamcsClient.getProcessors().thenAccept(response -> {
                    TableStringBuilder builder = new TableStringBuilder("INSTANCE", "NAME", "TYPE", "OWNER",
                            "PERSISTENT", "TIME", "STATUS");
                    for (ProcessorInfo processor : response.getProcessorList()) {
                        builder.addLine(processor.getInstance(), processor.getName(), processor.getType(),
                                processor.getCreator(), processor.getPersistent(), processor.getTime(),
                                processor.getState());
                    }
                    console.println(builder.toString());
                }).get();
            } else {
                InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                instanceClient.getProcessors().thenAccept(response -> {
                    TableStringBuilder builder = new TableStringBuilder("NAME", "TYPE", "OWNER", "PERSISTENT",
                            "TIME", "STATUS");
                    for (ProcessorInfo processor : response.getProcessorList()) {
                        builder.addLine(processor.getName(), processor.getType(), processor.getCreator(),
                                processor.getPersistent(), processor.getTime(), processor.getState());
                    }
                    console.println(builder.toString());
                }).get();
            }
        }
    }
}
