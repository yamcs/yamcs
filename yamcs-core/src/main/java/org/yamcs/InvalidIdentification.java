package org.yamcs;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.protobuf.Yamcs.NamedObjectId;

@SuppressWarnings("serial")
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

    @Override
    public String getMessage() {
        if (invalidParameters != null) {
            return invalidParameters.stream()
                    .map(NamedObjectId::toString)
                    .collect(Collectors.joining(", "));
        }
        return super.getMessage();
    }
}
