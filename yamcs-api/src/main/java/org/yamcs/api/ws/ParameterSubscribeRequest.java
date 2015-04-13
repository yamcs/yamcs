package org.yamcs.api.ws;

import java.util.HashSet;
import java.util.Set;

import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;

import com.google.protobuf.Message;


public class ParameterSubscribeRequest extends WebSocketRequest {

    private Set<NamedObjectId> ids = new HashSet<>();

    public ParameterSubscribeRequest(NamedObjectList idList) {
	ids.addAll(idList.getListList());
    }

    @Override
    public String getResource() {
	return "parameter";
    }

    @Override
    public String getOperation() {
	return "subscribe";
    }

    @Override
    public Message getRequestData() {
	NamedObjectList idList = NamedObjectList.newBuilder().addAllList(ids).build();
	return idList;
    }

    @Override
    public boolean canMergeWith(WebSocketRequest otherEvent) {
	return otherEvent instanceof ParameterSubscribeRequest;
    }

    @Override
    public WebSocketRequest mergeWith(WebSocketRequest otherEvent) {
	Set<NamedObjectId> otherIds = ((ParameterSubscribeRequest) otherEvent).ids;
	ids.addAll(otherIds);
	return this;
    }
    
    
    Set<NamedObjectId> getRequestedIds() {
	return ids;
    }
}