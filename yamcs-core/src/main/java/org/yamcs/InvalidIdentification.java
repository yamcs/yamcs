package org.yamcs;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Yamcs.NamedObjectId;

public class InvalidIdentification extends Exception {
    private List<NamedObjectId> invalidParameters;

    public InvalidIdentification(List<NamedObjectId> paraList) {
        this.invalidParameters = paraList;
    }

    public InvalidIdentification() {
    }

    public InvalidIdentification(NamedObjectId paraId) {
        this.invalidParameters = new ArrayList<>(1);
        getInvalidParameters().add(paraId);
    }

    public List<NamedObjectId> getInvalidParameters() {
        return invalidParameters;
    }
}
