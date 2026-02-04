package org.yamcs.parameter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.yamcs.protobuf.Yamcs.NamedObjectId;

/**
 * Used during multiple-parameter subscription. Captures information about two sorts of invalid parameters:
 * <ul>
 * <li>Unknown parameters (not present in MDB)
 * <li>Forbidden parameters (not accessible to requesting user)
 * </ul>
 */
@SuppressWarnings("serial")
public class InvalidParametersException extends Exception {

    private List<NamedObjectId> unknownParameters;
    private List<NamedObjectId> forbiddenParameters;

    public InvalidParametersException(List<NamedObjectId> unknownParameters, List<NamedObjectId> forbiddenParameters) {
        this.unknownParameters = unknownParameters;
        this.forbiddenParameters = forbiddenParameters;
    }

    public List<NamedObjectId> getParameters() {
        return Stream.of(unknownParameters, forbiddenParameters)
                .flatMap(List::stream)
                .toList();
    }

    public List<NamedObjectId> getUnknownParameters() {
        return unknownParameters;
    }

    public List<NamedObjectId> getForbiddenParameters() {
        return forbiddenParameters;
    }

    @Override
    public String getMessage() {
        return getParameters().stream()
                .map(NamedObjectId::toString)
                .collect(Collectors.joining(", "));
    }
}
