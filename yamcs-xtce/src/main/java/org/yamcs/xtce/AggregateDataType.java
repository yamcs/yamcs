package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.util.AggregateMemberNames;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class AggregateDataType extends NameDescription implements DataType {
    private static final long serialVersionUID = 1L;

    List<Member> memberList = new ArrayList<>();
    transient AggregateMemberNames memberNames;

    public AggregateDataType(Builder<?> builder) {
        super(builder);
        this.memberList = builder.memberList;
    }

    protected AggregateDataType(AggregateDataType t) {
        super(t);
        this.memberList = t.memberList;
        this.memberNames = t.memberNames;
    }

    @Override
    public String getTypeAsString() {
        return "aggregate";
    }

    /**
     * Returns a member on the given name. If no such member is present return null
     * 
     * @param name
     *            the name of the member to be returned
     * @return the member with the given name
     */
    public Member getMember(String name) {
        for (Member m : memberList) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    public List<Member> getMemberList() {
        return memberList;
    }

    @Override
    public Type getValueType() {
        return Value.Type.AGGREGATE;
    }

    /**
     * Returns a member in a hierarchical aggregate. It is equivalent with a chained call of {@link #getMember(String)}:
     * 
     * <pre>
     * getMember(path[0]).getMember(path[1])...getMember(path[n])
     * </pre>
     * 
     * assuming that all the elements on the path exist.
     * 
     * 
     * @param path
     *            - the path to be traversed. Its length has to be at least 1 - otherwise an
     *            {@link IllegalArgumentException} will be thrown.
     * @return the member obtained by traversing the path or null if not such member exist.
     */
    public Member getMember(String[] path) {
        if (path.length == 0) {
            throw new IllegalArgumentException("path cannot be empty");
        }
        DataType ptype = this;
        Member m = null;
        for (int i = 0; i < path.length; i++) {

            if (ptype instanceof AggregateDataType) {
                m = ((AggregateDataType) ptype).getMember(path[i]);
                if (m == null) {
                    return null;
                } else {
                    ptype = m.getType();
                }
            } else {
                return null;
            }
        }
        return m;
    }

    /**
     * 
     * @return the (unique) object encoding the member names
     * 
     */
    public AggregateMemberNames getMemberNames() {
        if (memberNames == null) {
            String[] n = memberList.stream().map(m -> m.getName()).toArray(String[]::new);
            memberNames = AggregateMemberNames.get(n);
        }
        return memberNames;
    }

    public int numMembers() {
        return memberList.size();
    }

    public Member getMember(int idx) {
        return memberList.get(idx);
    }

    /**
     * Parse the initial value as a JSON string.
     * <p>
     * This allows to specify only partially the values, the rest are copied from the member initial value or the type
     * definition (an exception is thrown if there is any member for which the value cannot be determined).
     * 
     * @return a map containing the values for all members.
     * @throws IllegalArgumentException
     *             if the string cannot be parsed or if values cannot be determined for all members
     */
    @Override
    public Map<String, Object> convertType(Object value) {
        if (value instanceof String) {
            // Parse as JSON
            try {
                JsonElement je = JsonParser.parseString((String) value);
                if (je instanceof JsonObject) {
                    return fromJson((JsonObject) je);
                } else {
                    throw new IllegalArgumentException("Expected JSON object but found " + je.getClass());
                }
            } catch (JsonParseException jpe) {
                throw new IllegalArgumentException(jpe.toString());
            }
        } else if (value instanceof Map) {
            return fromMap((Map<String, Object>) value);
        } else {
            throw new IllegalArgumentException("Cannot convert value of type '" + value.getClass() + "'");
        }
    }

    private Map<String, Object> fromJson(JsonObject jobj) {
        // Copy, so that we can remove
        JsonObject input = jobj.deepCopy();
        Map<String, Object> r = new HashMap<>();
        for (Member memb : memberList) {
            if (input.has(memb.getName())) {
                JsonElement jsel = input.remove(memb.getName());
                String v;
                if (jsel.isJsonPrimitive() && jsel.getAsJsonPrimitive().isString()) {
                    v = jsel.getAsString();
                } else {
                    v = jsel.toString();
                }
                r.put(memb.getName(), memb.getType().convertType(v));
            } else {
                Object v = memb.getInitialValue();
                if (v == null) {
                    v = memb.getType().getInitialValue();
                }
                if (v == null) {
                    throw new IllegalArgumentException("No value could be determined for member '"
                            + memb.getName() + "' (its corresponding type does not have an initial value)");
                }
                r.put(memb.getName(), v);
            }
        }
        if (input.size() > 0) {
            throw new IllegalArgumentException("Unknown members "
                    + input.entrySet().stream().map(e -> e.getKey()).collect(Collectors.toList()));
        }
        return r;
    }

    private Map<String, Object> fromMap(Map<String, Object> map) {
        // Provided map may be immutable. So make a copy where we can remove.
        Map<String, Object> input = new HashMap<>(map);
        Map<String, Object> r = new HashMap<>(input.size());
        for (Member memb : memberList) {
            if (input.containsKey(memb.getName())) {
                Object el = input.remove(memb.getName());
                r.put(memb.getName(), memb.getType().convertType(el));
            } else {
                Object v = memb.getInitialValue();
                if (v == null) {
                    v = memb.getType().getInitialValue();
                }
                if (v == null) {
                    throw new IllegalArgumentException("No value could be determined for member '"
                            + memb.getName() + "' (its corresponding type does not have an initial value)");
                }
                r.put(memb.getName(), v);
            }
        }
        if (input.size() > 0) {
            throw new IllegalArgumentException("Unknown members: " + input.keySet());
        }
        return r;
    }

    @Override
    public Map<String, Object> parseStringForRawValue(String stringValue) {
        // parse it as json
        try {
            JsonElement je = JsonParser.parseString(stringValue);
            if (je instanceof JsonObject) {
                return fromJsonRaw((JsonObject) je);
            } else {
                throw new IllegalArgumentException("Expected JSON object but found " + je.getClass());
            }
        } catch (JsonParseException jpe) {
            throw new IllegalArgumentException(jpe.toString());
        }
    }

    private Map<String, Object> fromJsonRaw(JsonObject jobj) {
        // Copy, so that we can remove
        JsonObject input = jobj.deepCopy();
        Map<String, Object> r = new HashMap<>();
        for (Member memb : memberList) {
            if (input.has(memb.getName())) {
                JsonElement jsel = input.remove(memb.getName());
                String v;
                if (jsel.isJsonPrimitive() && jsel.getAsJsonPrimitive().isString()) {
                    v = jsel.getAsString();
                } else {
                    v = jsel.toString();
                }
                r.put(memb.getName(), memb.getType().parseStringForRawValue(v));
            } else {
                throw new IllegalArgumentException("No value for member '" + memb.getName() + "'");
            }
        }
        if (input.size() > 0) {
            throw new IllegalArgumentException("Unknown members "
                    + input.entrySet().stream().map(e -> e.getKey()).collect(Collectors.joining(",", "[", "]")));
        }
        return r;
    }

    @Override
    public Map<String, Object> getInitialValue() {
        Map<String, Object> r = new HashMap<>();
        for (Member memb : memberList) {
            Object v = memb.getInitialValue();
            if (v == null) {
                DataType dt = memb.getType();
                if (dt != null) {
                    v = dt.getInitialValue();
                }
            }
            if (v == null) {
                return null;
            }
            r.put(memb.getName(), v);
        }
        return r;
    }

    @Override
    public String toString(Object v) {
        Gson gson = new Gson();
        return gson.toJson(getMapStr(v));
    }

    // convert the map m to another one with the leafs converted to strings or primitive types
    // also verifies that the m is valid for this type (all members present and no extra member)
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapStr(Object v) {

        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Can only convert maps; got: " + v);
        }
        // Copy, so that we can remove
        Map<String, Object> m = new HashMap<>((Map<String, Object>) v);

        Map<String, Object> r = new HashMap<>();

        for (Member memb : memberList) {
            Object v1 = m.remove(memb.getName());
            if (v1 == null) {
                if (memb.getInitialValue() == null) {
                    throw new IllegalArgumentException("no value provided for member '" + memb.getName() + "'");
                }
            } else {
                DataType dt = memb.getType();
                if (dt instanceof AggregateDataType) {
                    r.put(memb.getName(), ((AggregateDataType) dt).getMapStr(v1));
                } else {
                    dt.toString(v1);
                    r.put(memb.getName(), v1);
                }
            }
        }
        if (!m.isEmpty()) {
            throw new IllegalArgumentException("Unknown members " + m.keySet());
        }
        return r;
    }

    public abstract static class Builder<T extends Builder<T>> extends NameDescription.Builder<T>
            implements DataType.Builder<T> {
        List<Member> memberList = new ArrayList<>();

        public Builder() {
        }

        public Builder(AggregateDataType dataType) {
            super(dataType);
            this.memberList = dataType.memberList;
        }

        @Override
        public T setInitialValue(String initialValue) {
            throw new UnsupportedOperationException(
                    "Cannot set initial value; please send individual initial values for the members");

        }

        public T addMember(Member member) {
            memberList.add(member);
            return self();
        }

        public T addMembers(List<Member> memberList) {
            this.memberList.addAll(memberList);
            return self();
        }

        public List<Member> getMemberList() {
            return memberList;
        }
    }

}
