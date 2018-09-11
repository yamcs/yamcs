package org.yamcs.cli;

import org.yamcs.api.InstanceClient;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "List clients")
public class ClientsCli extends Command {

    public ClientsCli(Command parent) {
        super("clients", parent);
        addSubCommand(new ClientsList());
        setInstanceRequired(true);
    }

    @Parameters(commandDescription = "List connected clients")
    class ClientsList extends Command {

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public ClientsList() {
            super("list", ClientsCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            if (global) {
                yamcsClient.getClients().thenAccept(response -> {
                    TableStringBuilder builder = new TableStringBuilder("ID", "USER", "APPLICATION", "INSTANCE",
                            "PROCESSOR", "LOGIN");
                    for (ClientInfo client : response.getClientList()) {
                        builder.addLine(client.getId(), client.getUsername(), client.getApplicationName(),
                                client.getInstance(), client.getProcessorName(), client.getLoginTimeUTC());
                    }
                    console.println(builder.toString());
                }).get();
            } else {
                InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                instanceClient.getClients().thenAccept(response -> {
                    TableStringBuilder builder = new TableStringBuilder("ID", "USER", "APPLICATION", "PROCESSOR",
                            "LOGIN");
                    for (ClientInfo client : response.getClientList()) {
                        builder.addLine(client.getId(), client.getUsername(), client.getApplicationName(),
                                client.getProcessorName(), client.getLoginTimeUTC());
                    }
                    console.println(builder.toString());
                }).get();
            }
        }
    }
}
