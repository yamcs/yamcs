package org.yamcs.parameter;

import org.yamcs.parameter.ParameterValue;
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

    public ParameterValueWithId(ParameterValue pv, NamedObjectId id) {
        this.pv = pv;
        this.id = id;
    }

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
    
    public org.yamcs.protobuf.Pvalue.ParameterValue toGbpParameterValue() {
	return pv.toGpb(id);
    }

    @Override
    public String toString() {
	return "id:"+id+", pv:"+pv;
    }
}
