package org.yamcs.xtce;

import java.util.Objects;

/**
 * A lexical reference to a member of the aggregate currently being processed.
 * <p>
 * Unlike a {@link ParameterInstanceRef}, this reference does not identify a parameter in the MDB. It is resolved at
 * processing time, starting with the innermost aggregate and continuing outwards through enclosing aggregates.
 */
public class AggregateMemberInstanceRef extends ParameterOrArgumentRef {
    private static final long serialVersionUID = 1L;

    private final String memberName;

    public AggregateMemberInstanceRef(String memberName, boolean useCalibratedValue) {
        this.memberName = Objects.requireNonNull(memberName);
        this.useCalibratedValue = useCalibratedValue;
    }

    @Override
    public String getName() {
        return memberName;
    }

    @Override
    public DataType getDataType() {
        return null;
    }

    @Override
    public String toString() {
        return memberName;
    }
}
