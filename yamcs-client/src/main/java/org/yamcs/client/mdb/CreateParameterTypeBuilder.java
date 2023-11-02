package org.yamcs.client.mdb;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.Mdb.AlarmInfo;
import org.yamcs.protobuf.Mdb.ContextAlarmInfo;
import org.yamcs.protobuf.Mdb.CreateParameterTypeRequest;
import org.yamcs.protobuf.Mdb.EnumValue;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.MdbApiClient;

public class CreateParameterTypeBuilder {

    private MdbApiClient mdbService;
    private CreateParameterTypeRequest.Builder requestb;

    CreateParameterTypeBuilder(MissionDatabaseClient client, String parameter) {
        this(client.mdbService, client.instance, parameter);
    }

    private CreateParameterTypeBuilder(MdbApiClient mdbService, String instance, String parameter) {
        this.mdbService = mdbService;
        requestb = CreateParameterTypeRequest.newBuilder()
                .setInstance(instance)
                .setName(parameter);
    }

    public CreateParameterTypeBuilder withShortDescription(String shortDescription) {
        requestb.setShortDescription(shortDescription);
        return this;
    }

    public CreateParameterTypeBuilder withLongDescription(String longDescription) {
        requestb.setLongDescription(longDescription);
        return this;
    }

    public CreateParameterTypeBuilder withAlias(String namespace, String name) {
        requestb.putAliases(namespace, name);
        return this;
    }

    public CreateParameterTypeBuilder withEngType(String engType) {
        requestb.setEngType(engType);
        return this;
    }

    public CreateParameterTypeBuilder withUnit(String unit) {
        requestb.setUnit(unit);
        return this;
    }

    public CreateParameterTypeBuilder withSigned(boolean signed) {
        requestb.setSigned(signed);
        return this;
    }

    public CreateParameterTypeBuilder withDefaultAlarm(AlarmInfo alarmInfo) {
        requestb.setDefaultAlarm(alarmInfo);
        return this;
    }

    public CreateParameterTypeBuilder withContextAlarm(ContextAlarmInfo contextAlarmInfo) {
        requestb.addContextAlarms(contextAlarmInfo);
        return this;
    }

    public CreateParameterTypeBuilder withEnumerationValues(Map<Long, String> enumerationValues) {
        requestb.clearEnumerationValues();
        for (var entry : enumerationValues.entrySet()) {
            requestb.addEnumerationValues(EnumValue.newBuilder()
                    .setValue(entry.getKey())
                    .setLabel(entry.getValue()));
        }
        return this;
    }

    public CreateParameterTypeBuilder withEnumerationValues(List<EnumValue> enumerationValues) {
        requestb.clearEnumerationValues();
        requestb.addAllEnumerationValues(enumerationValues);
        return this;
    }

    public CreateParameterTypeBuilder withZeroStringValue(String zeroStringValue) {
        requestb.setZeroStringValue(zeroStringValue);
        return this;
    }

    public CreateParameterTypeBuilder withOneStringValue(String oneStringValue) {
        requestb.setOneStringValue(oneStringValue);
        return this;
    }

    public CompletableFuture<ParameterTypeInfo> create() {
        var f = new CompletableFuture<ParameterTypeInfo>();
        var request = requestb.build();
        mdbService.createParameterType(null, request, new ResponseObserver<>(f));
        return f;
    }
}
