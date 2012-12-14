package org.yamcs.usoctools;

import java.util.Collection;

import org.yamcs.protobuf.Yamcs.Event;


public interface PayloadModel {
	public String getMonParameterName(int parId);
	public String[] getEventPacketsOpsnames();
	public Collection<Event> decode(long recTime, byte[] ccsds);
    public String getPayloadName();
}
