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
       if(idx==-1) {
           throw new IllegalArgumentException("No member named '"+name+"'");
       }
       return idx;
    }
    
    public void setValue(String name, Value value) {
        values[idx(name)] = value;
    }

    /**
     * Returns the value of the member with the given name or null if the parameter has no such member.
     * 
     * @param name
     *            - the name of the aggregate member whos value has to be returned
     * 
     * @return the value of the member with the given name
     */
    public Value getMemberValue(String name) {
        return values[idx(name)];
    }

    @Override
    public Type getType() {
        return Type.AGGREGATE;
    }

    public int numMembers() {
        return values.length;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(int i =0; i<values.length; i++) {
            if(first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(names.get(i)).append(" : ").append(values[i]);
        }
       return sb.toString();
    }

    public String getMemberName(int idx) {
        return names.get(idx);
    }

    public Value getMemberValue(int i) {
        return values[i];
    }

}
