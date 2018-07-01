package org.yamcs.parameter;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class AggregateValue extends Value {
    String[] names;
    Value[] values;
    
    public AggregateValue(String[] memberNames) {
        this.names = memberNames;
        this.values = new Value[memberNames.length];
    }

    private int idx(String name) {
        String internedName = name.intern();
        for(int i = 0; i<names.length ; i++) {
            if(names[i] == internedName) {
                return i;
            }
        }
        throw new IllegalArgumentException("No member named '"+name+"'");
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
            sb.append(names[i]).append(" : ").append(values[i]);
        }
       return sb.toString();
    }

    public String getMemberName(int idx) {
        return names[idx];
    }

    public Value getMemberValue(int i) {
        return values[i];
    }

}
