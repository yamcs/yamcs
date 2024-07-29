package org.yamcs.client.mdb;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.yamcs.api.HttpBody;
import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.Page;
import org.yamcs.client.StreamReceiver;
import org.yamcs.client.base.AbstractPage;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.mdb.MissionDatabaseClient.ListOptions.DetailsOption;
import org.yamcs.client.mdb.MissionDatabaseClient.ListOptions.LimitOption;
import org.yamcs.client.mdb.MissionDatabaseClient.ListOptions.ListOption;
import org.yamcs.client.mdb.MissionDatabaseClient.ListOptions.QOption;
import org.yamcs.client.mdb.MissionDatabaseClient.ListOptions.SystemOption;
import org.yamcs.protobuf.Mdb.CommandInfo;
import org.yamcs.protobuf.Mdb.ContainerInfo;
import org.yamcs.protobuf.Mdb.DataSourceType;
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
import org.yamcs.protobuf.Mdb.MissionDatabaseItem;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Mdb.StreamMissionDatabaseRequest;
import org.yamcs.protobuf.MdbApiClient;

public class MissionDatabaseClient {

    String instance;
    MdbApiClient mdbService;

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

    public CreateParameterBuilder createParameter(String name, DataSourceType dataSource) {
        return new CreateParameterBuilder(this, name, dataSource);
    }

    public CreateParameterTypeBuilder createParameterType(String name) {
        return new CreateParameterTypeBuilder(this, name);
    }

    public CompletableFuture<Page<ParameterInfo>> listParameters(ListOption... options) {
        ListParametersRequest.Builder requestb = ListParametersRequest.newBuilder()
                .setInstance(instance)
                .setDetails(true);
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else if (option instanceof SystemOption) {
                requestb.setSystem(((SystemOption) option).system);
            } else if (option instanceof QOption) {
                requestb.setQ(((QOption) option).q);
            } else if (option instanceof DetailsOption) {
                requestb.setDetails(((DetailsOption) option).details);
            } else {
                throw new IllegalArgumentException("Unsupported option " + option.getClass());
            }
        }
        return new ParameterPage(requestb.build()).future();
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
                throw new IllegalArgumentException("Unsupported option " + option.getClass());
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

    public CompletableFuture<Void> streamMissionDatabaseItems(StreamReceiver<MissionDatabaseItem> consumer,
            StreamMissionDatabaseOptions options) {
        var request = StreamMissionDatabaseRequest.newBuilder()
                .setInstance(instance)
                .setIncludeSpaceSystems(options.isIncludeSpaceSystems())
                .setIncludeContainers(options.isIncludeContainers())
                .setIncludeParameters(options.isIncludeParameters())
                .setIncludeParameterTypes(options.isIncludeParameterTypes())
                .setIncludeCommands(options.isIncludeCommands())
                .setIncludeAlgorithms(options.isIncludeAlgorithms())
                .build();
        var f = new CompletableFuture<Void>();
        mdbService.streamMissionDatabase(null, request, new Observer<MissionDatabaseItem>() {

            @Override
            public void next(MissionDatabaseItem message) {
                consumer.accept(message);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                f.completeExceptionally(t);
            }

            @Override
            public void complete() {
                f.complete(null);
            }
        });
        return f;
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
            return getResponse().getSystemsList().stream()
                    .map(system -> system.getQualifiedName())
                    .collect(Collectors.toList());
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
            return getResponse().getSystemsList().stream()
                    .map(system -> system.getQualifiedName())
                    .collect(Collectors.toList());
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
            return getResponse().getSystemsList().stream()
                    .map(system -> system.getQualifiedName())
                    .collect(Collectors.toList());
        }
    }

    public static final class ListOptions {

        public static interface ListOption {
        }

        public static ListOption limit(int limit) {
            return new LimitOption(limit);
        }

        public static SystemOption system(String system) {
            return new SystemOption(system);
        }

        public static QOption q(String q) {
            return new QOption(q);
        }

        public static DetailsOption details(boolean details) {
            return new DetailsOption(details);
        }

        static final class LimitOption implements ListOption {
            final int limit;

            public LimitOption(int limit) {
                this.limit = limit;
            }
        }

        static final class SystemOption implements ListOption {
            final String system;

            public SystemOption(String system) {
                this.system = system;
            }
        }

        static final class QOption implements ListOption {
            final String q;

            public QOption(String q) {
                this.q = q;
            }
        }

        static final class DetailsOption implements ListOption {
            final boolean details;

            public DetailsOption(boolean details) {
                this.details = details;
            }
        }
    }
}
