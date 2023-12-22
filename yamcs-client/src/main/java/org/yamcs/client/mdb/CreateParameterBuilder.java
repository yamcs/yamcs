package org.yamcs.client.mdb;

import java.util.concurrent.CompletableFuture;

import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.Mdb.CreateParameterRequest;
import org.yamcs.protobuf.Mdb.DataSourceType;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.MdbApiClient;

public class CreateParameterBuilder {

    private MdbApiClient mdbService;
    private CreateParameterRequest.Builder requestb;

    CreateParameterBuilder(MissionDatabaseClient client, String parameter, DataSourceType dataSource) {
        this(client.mdbService, client.instance, parameter, dataSource);
    }

    private CreateParameterBuilder(MdbApiClient mdbService, String instance, String parameter,
            DataSourceType dataSource) {
        this.mdbService = mdbService;
        requestb = CreateParameterRequest.newBuilder()
                .setInstance(instance)
                .setName(parameter)
                .setDataSource(dataSource);
    }

    public CreateParameterBuilder withShortDescription(String shortDescription) {
        requestb.setShortDescription(shortDescription);
        return this;
    }

    public CreateParameterBuilder withLongDescription(String longDescription) {
        requestb.setLongDescription(longDescription);
        return this;
    }

    public CreateParameterBuilder withAlias(String namespace, String name) {
        requestb.putAliases(namespace, name);
        return this;
    }

    public CreateParameterBuilder withParameterType(String parameterType) {
        requestb.setParameterType(parameterType);
        return this;
    }

    public CompletableFuture<ParameterInfo> create() {
        var f = new CompletableFuture<ParameterInfo>();
        var request = requestb.build();
        mdbService.createParameter(null, request, new ResponseObserver<>(f));
        return f;
    }
}
