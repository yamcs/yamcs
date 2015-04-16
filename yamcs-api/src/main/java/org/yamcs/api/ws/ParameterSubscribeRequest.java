package org.yamcs.api.ws;

import java.util.HashSet;
import java.util.Set;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;

import com.google.protobuf.Message;

public class ParameterSubscribeRequest extends WebSocketRequest {

    private Set<NamedObjectId> ids = new HashSet<>();

    public ParameterSubscribeRequest(NamedObjectList idList) {
        super("parameter", "subscribe");
        ids.addAll(idList.getListList());
    }

    @Override
    public Message getRequestData() {
        return NamedObjectList.newBuilder().addAllList(ids).build();
    }

    Set<NamedObjectId> getRequestedIds() {
        return ids;
    }
}
