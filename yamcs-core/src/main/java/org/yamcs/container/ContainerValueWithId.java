package org.yamcs.container;

import java.util.List;

import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Cvalue.ContainerValue;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

/**
 * Same as ParameterValueWithId, for Container
 * 
 * @author dho
 *
 */
public class ContainerValueWithId {
	private List<ParameterValue> paramVals;
	private NamedObjectId id;

	public void setId(NamedObjectId id) {
		this.id = id;
	}

	public NamedObjectId getId() {
		return id;
	}

	public List<ParameterValue> getParamVals() {
		return paramVals;
	}

	public void setParamVals(List<ParameterValue> paramVals) {
		this.paramVals = paramVals;
	}

	public org.yamcs.protobuf.Cvalue.ContainerValue toGbpParameterData() {
		ContainerValue.Builder cvalue = ContainerValue.newBuilder();
		cvalue.setId(id);
		for (ParameterValue pv: paramVals) {
			NamedObjectId paramId = NamedObjectId.newBuilder().setName(pv.def.getName()).build();
			org.yamcs.protobuf.Pvalue.ParameterValue pval = pv.toGpb(paramId);
			cvalue.addParameter(pval);
		}
		
		//pdata.setGroup(id.get)
		
		return cvalue.build();
	}

	@Override
	public String toString() {
		return "id:" + id + ", pvals:" + paramVals;
	}
}
