package org.yamcs;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Yamcs.NamedObjectId;

public class InvalidIdentification extends Exception {
	public List<NamedObjectId> invalidParameters;
	
	public InvalidIdentification(List<NamedObjectId> paraList) {
		this.invalidParameters=paraList;
	}

	public InvalidIdentification() {
	}

	public InvalidIdentification(NamedObjectId paraId) {
		invalidParameters=new ArrayList<NamedObjectId>(1);
		invalidParameters.add(paraId);
	}
}
