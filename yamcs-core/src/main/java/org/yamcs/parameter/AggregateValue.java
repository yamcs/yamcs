package org.yamcs.parameter;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.util.AggregateMemberNames;

public class AggregateValue extends Value {
    AggregateMemberNames names;
    Value[] values;

    /**
     * Create a new aggregate value with the member names.
     * Make sure that the memberNames are interned string (see {@link String#intern()},
     * for example as returned by {@link AggregateDataType#getMemberNames()}
     * 
     * 
     * @param memberNames
     */
    public AggregateValue(AggregateMemberNames memberNames) {
        this.names = memberNames;
        this.values = new Value[memberNames.size()];
    }

    private int idx(String name) {
        int idx = names.indexOf(name);
        if (idx == -1) {
            throw new IllegalArgumentException("No member named '" + name + "'");
        }
        return idx;
    }

    public void setMemberValue(String name, Value value) {
        setMemberValue(idx(name), value);
    }

    /**
     * Returns the value of the member with the given name
     * 
     * @param name
     *            the name of the aggregate member whos value has to be returned
     * 
     * @return the value of the member with the given name
     * @throws IllegalArgumentException
     *             if there is no member with that name
     */
    public Value getMemberValue(String name) {
        return values[idx(name)];
    }

    public void setMemberValue(int idx, Value value) {
        if (value == null) {
            throw new NullPointerException();
        }
        values[idx] = value;
    }

    /**
     * Get the index of the member with the given name or -1 if there is no such member
     * 
     * @param name
     * @return
     */
    public int getMemberIndex(String name) {
        return names.indexOf(name);
    }
    @Override
    public Type getType() {
        return Type.AGGREGATE;
    }

    public int numMembers() {
        return values.length;
    }

    public String getMemberName(int idx) {
        return names.get(idx);
    }

    public Value getMemberValue(int i) {
        return values[i];
    }

    public AggregateMemberNames getMemberNames() {
        return names;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < values.length; i++) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(names.get(i)).append(" : ").append(values[i]);
        }
        return sb.toString();
    }

}
