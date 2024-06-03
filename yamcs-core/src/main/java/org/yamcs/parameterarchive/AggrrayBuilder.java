package org.yamcs.parameterarchive;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.IntArray;
import org.yamcs.xtce.PathElement;
import org.yamcs.xtce.util.AggregateMemberNames;

import org.yamcs.protobuf.Yamcs.Value.Type;

/**
 * builds aggregate or array values out of members extracted from the parameter archive.
 *
 */
public class AggrrayBuilder {

    Map<Integer, BasicValueBuilder> builders = new HashMap<>();

    ValueBuilder rootBuilder;
    String fqn;

    AggrrayBuilder(ParameterId... pids) {
        for (ParameterId pid : pids) {
            addParameterId(pid);
        }
    }

    private void addParameterId(ParameterId pid) {
        PathElement[] path = AggregateUtil.parseReference(pid.getParamFqn());

        AggregateValueBuilder builder = processRoot(pid, path[0], path.length == 1);

        if (builder == null) {
            assert (path.length == 1);
            // special case: parameter is an array of basic elements
            // they are already processed in the processRoot
            return;
        }

        PathElement pe0 = path[0];
        if (pe0.getIndex() == null) {
            builder = (AggregateValueBuilder) rootBuilder;
        } else {
            builder = createOrVerifyArrayElement((ArrayValueBuilder) rootBuilder, IntArray.wrap(pe0.getIndex()));
        }

        for (int i = 1; i < path.length - 1; i++) {
            builder = addAndCheckAggrPathElement(builder, path[i]);
        }

        // last one is special: of basic type or array of basic type
        BasicValueBuilder bvb = getBasicValueBuilder(pid);

        PathElement pe = path[path.length - 1];
        IntArray idx = pe.getIndex() == null ? null : IntArray.wrap(pe.getIndex());
        if (idx == null) {
            builder.addMember(pe.getName(), bvb);
        } else {
            ArrayValueBuilder arrb = createOrVerifyArrayMember(builder, pe.getName());
            arrb.addElement(idx, bvb);
        }
    }

    private BasicValueBuilder getBasicValueBuilder(ParameterId pid) {
        BasicValueBuilder bvb = new BasicValueBuilder();
        builders.put(pid.getPid(), bvb);
        return bvb;
    }

    // creates the necessary root structure for path[0] and returns
    // the aggregate builder where the sub-elements have to be added
    // if the root is an array, returns null
    private AggregateValueBuilder processRoot(ParameterId pid, PathElement pe0, boolean basicArray) {
        // verify the qualified name
        if (fqn == null) {
            fqn = pe0.getName();
        } else {
            if (!fqn.equals(pe0.getName())) {
                throw new ParameterArchiveException("Invalid parameter id found for aggregate or array: fqn is '"
                        + pe0.getName() + " while expecting from the first parameter '" + fqn + "'");
            }
        }

        IntArray idx = pe0.getIndex() == null ? null : IntArray.wrap(pe0.getIndex());
        if (idx == null) { // root is an aggregate
            if (rootBuilder == null) {
                rootBuilder = new AggregateValueBuilder();
            } else {
                if (!(rootBuilder instanceof AggregateValueBuilder)) {
                    throw new ParameterArchiveException("parameter is not an aggregate");
                }
            }
            return (AggregateValueBuilder) rootBuilder;
        } else { // root is an array
            if (rootBuilder == null) {
                rootBuilder = new ArrayValueBuilder();
            } else {
                if (!(rootBuilder instanceof ArrayValueBuilder)) {
                    throw new ParameterArchiveException("parameter is not an array");
                }
            }
            if (basicArray) {// special case, elements of the array are basic
                BasicValueBuilder bvb = getBasicValueBuilder(pid);
                ((ArrayValueBuilder) rootBuilder).addElement(idx, bvb);

                return null;
            } else {
                return createOrVerifyArrayElement((ArrayValueBuilder) rootBuilder, idx);
            }
        }
    }

    private AggregateValueBuilder addAndCheckAggrPathElement(AggregateValueBuilder aggb, PathElement pe) {
        if (pe.getIndex() == null) {
            return createOrVerifyAggregateMember(aggb, pe.getName());
        } else {
            ArrayValueBuilder arrb = createOrVerifyArrayMember(aggb, pe.getName());
            return createOrVerifyArrayElement(arrb, IntArray.wrap(pe.getIndex()));
        }
    }

    // creates and returns the element idx verifying also that it is an aggregate
    private AggregateValueBuilder createOrVerifyArrayElement(ArrayValueBuilder arrb, IntArray idx) {
        ValueBuilder builder = arrb.getElement(idx);
        if (builder == null) {
            AggregateValueBuilder aggb = new AggregateValueBuilder();
            arrb.addElement(idx, aggb);
            return aggb;
        } else {
            if (builder instanceof AggregateValueBuilder) {
                return (AggregateValueBuilder) builder;
            } else {
                throw new ParameterArchiveException(
                        "Expected " + idx + " element index to be an aggregate but it is " + builder.getClass());
            }
        }
    }

    ArrayValueBuilder createOrVerifyArrayMember(AggregateValueBuilder aggb, String member) {
        ValueBuilder builder = aggb.getMember(member);
        if (builder == null) {
            ArrayValueBuilder arrb = new ArrayValueBuilder();
            aggb.addMember(member, arrb);
            return arrb;
        } else {
            if (builder instanceof ArrayValueBuilder) {
                return (ArrayValueBuilder) builder;
            } else {
                throw new ParameterArchiveException(
                        "Expected '" + member + "' to be an array but it is " + builder.getClass());
            }
        }
    }

    AggregateValueBuilder createOrVerifyAggregateMember(AggregateValueBuilder aggb, String member) {
        ValueBuilder builder = aggb.getMember(member);
        if (builder == null) {
            AggregateValueBuilder arrb = new AggregateValueBuilder();
            aggb.addMember(member, arrb);
            return arrb;
        } else {
            if (builder instanceof AggregateValueBuilder) {
                return (AggregateValueBuilder) builder;
            } else {
                throw new ParameterArchiveException(
                        "Expected '" + member + "' to be an aggregate but it is " + builder.getClass());
            }
        }
    }

    public void setValue(ParameterId pid, Value v) {
        BasicValueBuilder bvb = builders.get(pid.getPid());
        if (bvb == null) {
            throw new IllegalArgumentException("Unknown parameter " + pid);
        }
        bvb.setValue(v);
    }

    public Value build() {
        return rootBuilder.build();
    }

    public void clear() {
        rootBuilder.clear();
    }

    interface ValueBuilder {
        Value build();

        void clear();
    }

    class BasicValueBuilder implements ValueBuilder {
        Value v;

        @Override
        public Value build() {
            return v;
        }

        public void setValue(Value v) {
            this.v = v;
        }

        @Override
        public void clear() {
            this.v = null;
        }

    }

    class ArrayValueBuilder implements ValueBuilder {
        Map<IntArray, ValueBuilder> elements = new LinkedHashMap<>();
        int[] dim = null;
        int numDim = -1;

        public void addElement(IntArray idx, ValueBuilder b) {
            if (numDim == -1) {
                numDim = idx.size();
            } else if (numDim != idx.size()) {
                throw new ParameterArchiveException("Invalid number of dimensions for index '" + idx
                        + "'; expected " + numDim);
            }
            if (elements.containsKey(idx)) {
                throw new ParameterArchiveException("Duplicate member '" + idx + "'");
            }
            elements.put(idx, b);
        }

        public ValueBuilder getElement(IntArray idx) {
            return elements.get(idx);
        }

        @Override
        public ArrayValue build() {
            if (dim == null) {
                dim = new int[numDim];
            }

            Map<IntArray, Value> values = new HashMap<>();
            Type valueType = null;
            
            for (Map.Entry<IntArray, ValueBuilder> me : elements.entrySet()) {
                Value v = me.getValue().build();
                if (v != null) {
                    values.put(me.getKey(), v);
                    valueType = v.getType();
                }
            }
            
            for (int i = 0; i < numDim; i++) {
                int k = i;
                dim[i] = values.keySet().stream().mapToInt(a -> a.get(k)).max().getAsInt() + 1;
            }

            ArrayValue av = new ArrayValue(dim, valueType);
            for (var me : values.entrySet()) {
                av.setElementValue(me.getKey().array(), me.getValue());
            }
            return av;
        }

        @Override
        public void clear() {
            this.dim = null;
            for (ValueBuilder vb : elements.values()) {
                vb.clear();
            }
        }
    }

    class AggregateValueBuilder implements ValueBuilder {
        Map<String, ValueBuilder> members = new LinkedHashMap<>();
        AggregateMemberNames names;

        public void addMember(String name, ValueBuilder b) {
            if (members.containsKey(name)) {
                throw new ParameterArchiveException("Duplicate member '" + name + "'");
            }
            members.put(name, b);
        }

        public ValueBuilder getMember(String name) {
            return members.get(name);
        }

        @Override
        public AggregateValue build() {
            if (names == null) {
                names = AggregateMemberNames.get(members.keySet().toArray(new String[0]));
            }
            AggregateValue av = new AggregateValue(names);
            for (Map.Entry<String, ValueBuilder> me : members.entrySet()) {
                Value v = me.getValue().build();
                if (v == null) {
                    throw new ParameterArchiveException("No value for member '" + me.getKey() + "'");
                }
                av.setMemberValue(me.getKey(), me.getValue().build());
            }
            return av;
        }

        @Override
        public void clear() {
            for (ValueBuilder vb : members.values()) {
                vb.clear();
            }
        }
    }

}
