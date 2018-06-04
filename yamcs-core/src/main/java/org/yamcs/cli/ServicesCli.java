package org.yamcs.cli;

import java.util.List;

import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Rest.EditServiceRequest;
import org.yamcs.protobuf.Rest.ListServiceInfoResponse;
import org.yamcs.protobuf.YamcsManagement.ServiceInfo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import io.netty.handler.codec.http.HttpMethod;

@Parameters(commandDescription = "Service operations")
public class ServicesCli extends Command {

    public ServicesCli(Command parent) {
        super("services", parent);
        addSubCommand(new ServicesList());
        addSubCommand(new ServicesEnable());
        addSubCommand(new ServicesDisable());
        setYcpRequired(true, true);
    }

    @Parameters(commandDescription = "List existing tables")
    class ServicesList extends Command {

        public ServicesList() {
            super("list", ServicesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            RestClient restClient = new RestClient(ycp);

            byte[] resp = restClient.doRequest("/services/" + ycp.getInstance(), HttpMethod.GET).get();
            ListServiceInfoResponse response = ListServiceInfoResponse.parseFrom(resp);
            String tpl = "%-50s %-10s";
            console.println(String.format(tpl, "NAME", "STATUS"));
            for (ServiceInfo service : response.getServiceList()) {
                console.println(String.format(tpl, service.getName(), service.getState()));
            }
        }
    }

    @Parameters(commandDescription = "Enable a service")
    class ServicesEnable extends Command {

        @Parameter(description = "service", required = true)
        List<String> services;

        public ServicesEnable() {
            super("enable", ServicesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            RestClient restClient = new RestClient(ycp);

            EditServiceRequest.Builder requestb = EditServiceRequest.newBuilder();
            requestb.setState("running");

            for (String service : services) {
                String url = "/services/" + ycp.getInstance() + "/" + service;
                restClient.doRequest(url, HttpMethod.PATCH, requestb.build().toByteArray()).get();
            }
        }
    }

    @Parameters(commandDescription = "Disable a service")
    class ServicesDisable extends Command {

        @Parameter(description = "service", required = true)
        List<String> services;

        public ServicesDisable() {
            super("disable", ServicesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            RestClient restClient = new RestClient(ycp);

            EditServiceRequest.Builder requestb = EditServiceRequest.newBuilder();
            requestb.setState("stopped");

            for (String service : services) {
                String url = "/services/" + ycp.getInstance() + "/" + service;
                restClient.doRequest(url, HttpMethod.PATCH, requestb.build().toByteArray()).get();
            }
        }
    }
}
