package org.yamcs.parameter;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.protobuf.Yamcs.Value.Type;

public class AggregateValue extends Value {
    Map<String, Value> members = new HashMap<>();

    public void setValue(String name, Value value) {
        members.put(name, value);
    }

    /**
     * Returns the value of the member with the given name or null if the parameter has no such member.
     * 
     * @param name
     *            - the name of the aggregate member whos value has to be returned
     * 
     * @return he value of the member with the given name or null if the parameter has no such member
     */
    public Value getMemberValue(String name) {
        return members.get(name);
    }

    @Override
    public Type getType() {
        return Type.AGGREGATE;
    }

    public int numMembers() {
        return members.size();
    }

    public String toString() {
        return members.toString();
    }
    
    public Set<Entry<String, Value>> getMemeberValues() {
        return members.entrySet();
    }

}
