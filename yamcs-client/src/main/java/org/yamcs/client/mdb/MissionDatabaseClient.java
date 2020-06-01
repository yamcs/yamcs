package org.yamcs.client.mdb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.AbstractPage;
import org.yamcs.client.Page;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.ListCommandsRequest;
import org.yamcs.protobuf.Mdb.ListCommandsResponse;
import org.yamcs.protobuf.Mdb.ListContainersRequest;
import org.yamcs.protobuf.Mdb.ListContainersResponse;
import org.yamcs.protobuf.Mdb.ListParametersRequest;
import org.yamcs.protobuf.Mdb.ListParametersResponse;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.MdbApiClient;

public class MissionDatabaseClient {

    private String instance;

    private MdbApiClient mdbService;

    public MissionDatabaseClient(MethodHandler handler, String instance) {
        this.instance = instance;
        mdbService = new MdbApiClient(handler);
    }

    public String getInstance() {
        return instance;
    }

    public CompletableFuture<Page<ParameterInfo>> listParameters() {
        ListParametersRequest request = ListParametersRequest.newBuilder()
                .setInstance(instance)
                .setDetails(true)
                .build();
        return new ParameterPage(request).future();
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<SystemPage<ParameterInfo>> listParametersForSystem(String system) {
        ListParametersRequest request = ListParametersRequest.newBuilder()
                .setInstance(instance)
                .setDetails(true)
                .build();
        return (CompletableFuture<SystemPage<ParameterInfo>>) (Object) new ParameterPage(request).future();
    }

    public CompletableFuture<Page<ContainerInfo>> listContainers() {
        ListContainersRequest request = ListContainersRequest.newBuilder()
                .setInstance(instance)
                .build();
        return new ContainerPage(request).future();
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<SystemPage<ContainerInfo>> listContainersForSystem(String system) {
        ListContainersRequest request = ListContainersRequest.newBuilder()
                .setInstance(instance)
                .build();
        return (CompletableFuture<SystemPage<ContainerInfo>>) (Object) new ContainerPage(request).future();
    }

    public CompletableFuture<Page<CommandInfo>> listCommands() {
        ListCommandsRequest request = ListCommandsRequest.newBuilder()
                .setInstance(instance)
                .build();
        return new CommandPage(request).future();
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<SystemPage<CommandInfo>> listCommandsForSystem(String system) {
        ListCommandsRequest request = ListCommandsRequest.newBuilder()
                .setInstance(instance)
                .build();
        return (CompletableFuture<SystemPage<CommandInfo>>) (Object) new CommandPage(request).future();
    }

    private class ParameterPage extends AbstractPage<ListParametersRequest, ListParametersResponse, ParameterInfo>
            implements SystemPage<ParameterInfo> {

        public ParameterPage(ListParametersRequest request) {
            super(request, "parameters");
        }

        @Override
        protected void fetch(ListParametersRequest request, Observer<ListParametersResponse> observer) {
            mdbService.listParameters(null, request, observer);
        }

        @Override
        public List<String> getSubsystems() {
            return new ArrayList<>(getResponse().getSpaceSystemsList());
        }
    }

    private class ContainerPage extends AbstractPage<ListContainersRequest, ListContainersResponse, ContainerInfo>
            implements SystemPage<ContainerInfo> {

        public ContainerPage(ListContainersRequest request) {
            super(request, "containers");
        }

        @Override
        protected void fetch(ListContainersRequest request, Observer<ListContainersResponse> observer) {
            mdbService.listContainers(null, request, observer);
        }

        @Override
        public List<String> getSubsystems() {
            return new ArrayList<>(getResponse().getSpaceSystemsList());
        }
    }

    private class CommandPage extends AbstractPage<ListCommandsRequest, ListCommandsResponse, CommandInfo>
            implements SystemPage<CommandInfo> {

        public CommandPage(ListCommandsRequest request) {
            super(request, "commands");
        }

        @Override
        protected void fetch(ListCommandsRequest request, Observer<ListCommandsResponse> observer) {
            mdbService.listCommands(null, request, observer);
        }

        @Override
        public List<String> getSubsystems() {
            return new ArrayList<>(getResponse().getSpaceSystemsList());
        }
    }
}
