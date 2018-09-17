package org.yamcs.cli;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.api.InstanceClient;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.Rest.EditServiceRequest;
import org.yamcs.protobuf.YamcsManagement.ServiceInfo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Read and manipulate services")
public class ServicesCli extends Command {

    public ServicesCli(Command parent) {
        super("services", parent);
        addSubCommand(new ServicesList());
        addSubCommand(new ServicesEnable());
        addSubCommand(new ServicesDisable());
        addSubCommand(new ServicesDescribe());
        setInstanceRequired(true);
    }

    @Parameters(commandDescription = "List services")
    class ServicesList extends Command {

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public ServicesList() {
            super("list", ServicesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            if (global) {
                yamcsClient.getServices().thenAccept(response -> {
                    TableStringBuilder builder = new TableStringBuilder("NAME", "CLASS", "STATUS");
                    for (ServiceInfo service : response.getServiceList()) {
                        builder.addLine(service.getName(), service.getClassName(), service.getState());
                    }
                    console.println(builder.toString());
                }).get();
            } else {
                InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                instanceClient.getServices().thenAccept(response -> {
                    TableStringBuilder builder = new TableStringBuilder("NAME", "CLASS", "STATUS");
                    for (ServiceInfo service : response.getServiceList()) {
                        builder.addLine(service.getName(), service.getClassName(), service.getState());
                    }
                    console.println(builder.toString());
                }).get();
            }
        }
    }

    @Parameters(commandDescription = "Enable a service")
    class ServicesEnable extends Command {

        @Parameter(description = "service", required = true)
        List<String> services;

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public ServicesEnable() {
            super("enable", ServicesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            if (global) {
                for (String service : services) {
                    EditServiceRequest options = EditServiceRequest.newBuilder().setState("running").build();
                    yamcsClient.editService(service, options).get();
                }
            } else {
                InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                for (String service : services) {
                    EditServiceRequest options = EditServiceRequest.newBuilder().setState("running").build();
                    instanceClient.editService(service, options).get();
                }
            }
        }
    }

    @Parameters(commandDescription = "Disable a service")
    class ServicesDisable extends Command {

        @Parameter(description = "service", required = true)
        List<String> services;

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public ServicesDisable() {
            super("disable", ServicesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            if (global) {
                for (String service : services) {
                    EditServiceRequest options = EditServiceRequest.newBuilder().setState("stopped").build();
                    yamcsClient.editService(service, options).get();
                }
            } else {
                InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                for (String service : services) {
                    EditServiceRequest options = EditServiceRequest.newBuilder().setState("stopped").build();
                    instanceClient.editService(service, options).get();
                }
            }
        }
    }

    @Parameters(commandDescription = "Describe service")
    class ServicesDescribe extends Command {

        @Parameter(required = true, description = "instance")
        List<String> services = new ArrayList<>();

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public ServicesDescribe() {
            super("describe", ServicesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            if (global) {
                for (String service : services) {
                    yamcsClient.getService(service).thenAccept(response -> {
                        console.println(response.toString());
                    }).get();
                }
            } else {
                InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                for (String service : services) {
                    instanceClient.getService(service).thenAccept(response -> {
                        console.println(response.toString());
                    }).get();
                }
            }
        }
    }
}
