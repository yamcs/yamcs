package org.yamcs.client.mdb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.HttpBody;
import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.Page;
import org.yamcs.client.base.AbstractPage;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.mdb.MissionDatabaseClient.ListOptions.LimitOption;
import org.yamcs.client.mdb.MissionDatabaseClient.ListOptions.ListOption;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.ExportJavaMissionDatabaseRequest;
import org.yamcs.protobuf.Mdb.GetCommandRequest;
import org.yamcs.protobuf.Mdb.GetContainerRequest;
import org.yamcs.protobuf.Mdb.GetParameterRequest;
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

    public CompletableFuture<ParameterInfo> getParameter(String name) {
        GetParameterRequest.Builder requestb = GetParameterRequest.newBuilder()
                .setInstance(instance)
                .setName(name);
        CompletableFuture<ParameterInfo> f = new CompletableFuture<>();
        mdbService.getParameter(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Page<ParameterInfo>> listParameters(ListOption... options) {
        ListParametersRequest.Builder requestb = ListParametersRequest.newBuilder()
                .setInstance(instance)
                .setDetails(true);
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new ParameterPage(requestb.build()).future();
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<SystemPage<ParameterInfo>> listParametersForSystem(String system) {
        ListParametersRequest request = ListParametersRequest.newBuilder()
                .setInstance(instance)
                .setDetails(true)
                .build();
        return (CompletableFuture<SystemPage<ParameterInfo>>) (Object) new ParameterPage(request).future();
    }

    public CompletableFuture<ContainerInfo> getContainer(String name) {
        GetContainerRequest.Builder requestb = GetContainerRequest.newBuilder()
                .setInstance(instance)
                .setName(name);
        CompletableFuture<ContainerInfo> f = new CompletableFuture<>();
        mdbService.getContainer(null, requestb.build(), new ResponseObserver<>(f));
        return f;
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

    public CompletableFuture<CommandInfo> getCommand(String name) {
        GetCommandRequest.Builder requestb = GetCommandRequest.newBuilder()
                .setInstance(instance)
                .setName(name);
        CompletableFuture<CommandInfo> f = new CompletableFuture<>();
        mdbService.getCommand(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<Page<CommandInfo>> listCommands(ListOption... options) {
        ListCommandsRequest.Builder requestb = ListCommandsRequest.newBuilder()
                .setInstance(instance)
                .setDetails(true);
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new CommandPage(requestb.build()).future();
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<SystemPage<CommandInfo>> listCommandsForSystem(String system) {
        ListCommandsRequest request = ListCommandsRequest.newBuilder()
                .setInstance(instance)
                .build();
        return (CompletableFuture<SystemPage<CommandInfo>>) (Object) new CommandPage(request).future();
    }

    public CompletableFuture<byte[]> getSerializedJavaDump() {
        ExportJavaMissionDatabaseRequest request = ExportJavaMissionDatabaseRequest.newBuilder()
                .setInstance(instance)
                .build();
        CompletableFuture<HttpBody> f = new CompletableFuture<>();
        mdbService.exportJavaMissionDatabase(null, request, new ResponseObserver<>(f));
        return f.thenApply(response -> response.getData().toByteArray());
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

    public static final class ListOptions {

        public static interface ListOption {
        }

        public static ListOption limit(int limit) {
            return new LimitOption(limit);
        }

        static final class LimitOption implements ListOption {
            final int limit;

            public LimitOption(int limit) {
                this.limit = limit;
            }
        }
    }
}
