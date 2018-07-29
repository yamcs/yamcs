package org.yamcs.cli;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Read Yamcs instances")
public class InstancesCli extends Command {

    public InstancesCli(Command parent) {
        super("instances", parent);
        addSubCommand(new InstancesDescribe());
        addSubCommand(new InstancesList());
    }

    @Parameters(commandDescription = "List instances")
    class InstancesList extends Command {

        public InstancesList() {
            super("list", InstancesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            yamcsClient.getInstances().thenAccept(response -> {
                TableStringBuilder builder = new TableStringBuilder("NAME", "STATE", "MISSION TIME");
                for (YamcsInstance instance : response.getInstanceList()) {
                    builder.addLine(instance.getName(), instance.getState(), instance.getMissionTime());
                }
                console.println(builder.toString());
            }).get();
        }
    }

    @Parameters(commandDescription = "Describe instance")
    class InstancesDescribe extends Command {

        @Parameter(required = true, description = "instance")
        List<String> instances = new ArrayList<>();

        public InstancesDescribe() {
            super("describe", InstancesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            for (String instance : instances) {
                yamcsClient.getInstance(instance).thenAccept(response -> {
                    console.println(response.toString());
                }).get();
            }
        }
    }
}
