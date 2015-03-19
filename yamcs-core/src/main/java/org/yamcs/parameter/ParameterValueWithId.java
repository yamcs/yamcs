package org.yamcs.parameter;

import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;


/**
 * groups together a parameter value with a telemetry item id.
 * The reason for this is that the consumers subscribe to telemetry items based on NamedObjectId (and they want to receive the same back)
 *  while all the computation inside is done based on ParameterDefinition. There can be many NamedObjectId
 *  pointing to the same ParameterDefinition
 * @author nm
 *
 */
public class ParameterValueWithId {
	private ParameterValue pv;
	private NamedObjectId id;
	
	public void setId(NamedObjectId id) {
		this.id = id;
	}
	public NamedObjectId getId() {
		return id;
	}
	public void setParameterValue(ParameterValue pv) {
		this.pv = pv;
	}
	public ParameterValue getParameterValue() {
		return pv;
	}
	
	@Override
    public String toString() {
        return "id:"+id+", pv:"+pv;
    }
}
