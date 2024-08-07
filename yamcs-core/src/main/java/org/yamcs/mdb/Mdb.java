package org.yamcs.mdb;

import static org.yamcs.xtce.NameDescription.PATH_SEPARATOR;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AbsoluteTimeArgumentType;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanArgumentType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.SystemParameter;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.XtceDb;

/**
 * Wraps an {@link XtceDb} object.
 * <p>
 * Offers persistence capabilities for selected subtrees.
 */
public class Mdb extends XtceDb {
    private static final long serialVersionUID = 2L;
    final Map<String, SpaceSystemWriter> subsystemWriters;

    public Mdb(SpaceSystem spaceSystem, Map<String, SpaceSystemWriter> susbsystemWriters) {
        super(spaceSystem);
        susbsystemWriters.put(YAMCS_SPACESYSTEM_NAME, (fqn, mdb) -> {
            // writer for the /yamcs doesn't write to disk
        });
        this.subsystemWriters = susbsystemWriters;
    }

    public void addParameter(Parameter p, boolean addSpaceSystem, boolean addParameterType) throws IOException {
        addParameters(List.of(p), addSpaceSystem, addParameterType);
    }

    /**
     * Adds parameters to the MDB after which it persist the corresponding subtree to the file
     * <p>
     * If any of spacesystems of the parameters are not writable, an IllegalArgumentException will be thrown and no
     * parameter will be added
     * 
     * @throws IOException
     */
    public synchronized void addParameters(List<Parameter> parameters, boolean createSpaceSystems,
            boolean createParameterTypes) throws IOException {
        Map<String, SpaceSystemWriter> writers = new HashMap<>();

        // collect the writers (and verify that the spacesystem where this parameters belong are writable)
        for (var p : parameters) {
            WriterWithPath wwp = getWriter(p.getSubsystemName());
            writers.put(wwp.path, wwp.writer);
        }
        // add the parameters to the MDB
        super.doAddParameters(parameters, createSpaceSystems, createSpaceSystems);

        for (var entry : writers.entrySet()) {
            entry.getValue().write(entry.getKey(), this);
        }
    }

    /**
     * Get the MDB writer for this qualified name
     * <p>
     * throws IllegalArgumentException if there is no writer (i.e. if the spacesystem is not writable)
     */
    private WriterWithPath getWriter(String fqn) {
        String tmp = fqn;
        SpaceSystemWriter w = subsystemWriters.get(tmp);
        while (w == null) {
            int index = tmp.lastIndexOf(PATH_SEPARATOR);
            if (index == -1) {
                throw new IllegalArgumentException("'" + fqn + "' is not a writable SpaceSystem");
            }
            tmp = tmp.substring(0, index);
            w = subsystemWriters.get(tmp);
        }

        return new WriterWithPath(tmp, w);
    }

    static class WriterWithPath {
        final String path;
        final SpaceSystemWriter writer;

        public WriterWithPath(String path, SpaceSystemWriter writer) {
            this.path = path;
            this.writer = writer;
        }

    }

    public void addParameterType(ParameterType ptype, boolean createSpaceSystem) throws IOException {
        addParameterTypes(List.of(ptype), createSpaceSystem);
    }

    public void addParameterTypes(List<ParameterType> ptypeList, boolean createSpaceSystem) throws IOException {
        // collect the writers (and verify that the spacesystem where this parameter types belong are writable)
        Map<String, SpaceSystemWriter> writers = new HashMap<>();
        for (var ptype : ptypeList) {
            WriterWithPath wwp = getWriter(NameDescription.getSubsystemName(ptype.getQualifiedName()));
            writers.put(wwp.path, wwp.writer);
        }
        // add the parameters to the MDB
        super.doAddParameterType(ptypeList, createSpaceSystem);

        for (var entry : writers.entrySet()) {
            entry.getValue().write(entry.getKey(), this);
        }

    }

    /**
     * Creates and returns a system parameter with the given qualified name. If the parameter already exists it is
     * returned.
     * 
     * 
     * @param parameterQualifiedNamed
     *            - the name of the parmaeter to be created. It must start with {@link #YAMCS_SPACESYSTEM_NAME}
     * @param ptype
     * @return the parameter created or already existing.
     * @throws IllegalArgumentException
     *             if the <code>parameterQualifiedNamed</code> does not start with {@link #YAMCS_SPACESYSTEM_NAME}
     */
    public SystemParameter createSystemParameter(String parameterQualifiedNamed, ParameterType ptype,
            String shortDescription) {

        if (!parameterQualifiedNamed.startsWith(YAMCS_SPACESYSTEM_NAME)) {
            throw new IllegalArgumentException(
                    "The parameter qualified name must start with " + YAMCS_SPACESYSTEM_NAME);
        }

        SystemParameter p = (SystemParameter) parameters.get(parameterQualifiedNamed);
        if (p == null) {
            p = SystemParameter.getForFullyQualifiedName(parameterQualifiedNamed);
            p.setParameterType(ptype);
            doAddParameter(p, true, true);
        } else {
            if (p.getParameterType() != ptype) {
                throw new IllegalArgumentException("A parameter with name " + parameterQualifiedNamed
                        + " already exists but has a different type: " + p.getParameterType()
                        + " The type in the request was: " + ptype);
            }
        }
        p.setShortDescription(shortDescription);
        return p;
    }

    /**
     * Adds a parameter type to the MDB. The type has to have the qualified name set and start with
     * {@link #YAMCS_SPACESYSTEM_NAME}.
     * <p>
     * If a type with the same name already exists, it is returned instead. No check is performed that the existing type
     * and the new type are the same or even compatible.
     * 
     * @param ptype
     * @return
     */
    public ParameterType addSystemParameterType(ParameterType ptype) {
        String fqn = ptype.getQualifiedName();
        if (fqn == null) {
            throw new IllegalArgumentException("The type does not have a qualified name");
        }

        if (!fqn.startsWith(YAMCS_SPACESYSTEM_NAME)) {
            throw new IllegalArgumentException(
                    "The qualified name of the type must start with " + YAMCS_SPACESYSTEM_NAME);
        }

        ParameterType ptype1 = parameterTypes.get(fqn);

        if (ptype1 == null) {
            doAddParameterType(Arrays.asList(ptype), true);
        } else {
            ptype = ptype1;
        }
        return ptype;
    }

    /**
     * Creates if it does not exist and returns a basic parameter type corresponding to the value.
     * <p>
     * The namespace has to be writable otherwise an IllegalArgumentException will be thrown
     */
    public ParameterType getOrCreateBasicParameterType(String namespace, Type type, UnitType unit) throws IOException {

        switch (type) {
        case BINARY:
            return getOrCreateParameterType(namespace, "binary", unit,
                    () -> new BinaryParameterType.Builder());
        case BOOLEAN:
            return getOrCreateParameterType(namespace, "boolean", unit,
                    () -> new BooleanParameterType.Builder());
        case STRING:
            return getOrCreateParameterType(namespace, "string", unit,
                    () -> new StringParameterType.Builder());
        case FLOAT:
            return getOrCreateParameterType(namespace, "float32", unit,
                    () -> new FloatParameterType.Builder().setSizeInBits(32));
        case DOUBLE:
            return getOrCreateParameterType(namespace, "float64", unit,
                    () -> new FloatParameterType.Builder().setSizeInBits(64));
        case SINT32:
            return getOrCreateParameterType(namespace, "sint32", unit,
                    () -> new IntegerParameterType.Builder().setSizeInBits(32).setSigned(true));
        case SINT64:
            return getOrCreateParameterType(namespace, "sint64", unit,
                    () -> new IntegerParameterType.Builder().setSizeInBits(64).setSigned(true));
        case UINT32:
            return getOrCreateParameterType(namespace, "uint32", unit,
                    () -> new IntegerParameterType.Builder().setSizeInBits(32).setSigned(false));
        case UINT64:
            return getOrCreateParameterType(namespace, "uint64", unit,
                    () -> new IntegerParameterType.Builder().setSizeInBits(64).setSigned(false));
        case TIMESTAMP:
            return getOrCreateParameterType(namespace, "time", unit, () -> new AbsoluteTimeParameterType.Builder());
        case ENUMERATED:
            return getOrCreateParameterType(namespace, "enum", unit, () -> new EnumeratedParameterType.Builder());
        default:
            throw new IllegalArgumentException(type + "is not a basic type");
        }
    }

    private ParameterType getOrCreateParameterType(String namespace, String name, UnitType unit,
            Supplier<ParameterType.Builder<?>> supplier) throws IOException {

        String units;
        if (unit != null) {
            units = unit.getUnit();
            if (!"1".equals(unit.getFactor())) {
                units = unit.getFactor() + "x" + units;
            }
            if (unit.getPower() != 1) {
                units = units + "^" + unit.getPower();
            }
            name = name + "_" + units.replaceAll("/", "_");
        }

        String fqn = namespace + NameDescription.PATH_SEPARATOR + name;
        ParameterType ptype = getParameterType(fqn);
        if (ptype != null) {
            return ptype;
        }
        ParameterType.Builder<?> typeb = supplier.get().setName(name);
        if (unit != null) {
            ((BaseDataType.Builder<?>) typeb).addUnit(unit);
        }

        ptype = typeb.build();
        ((NameDescription) ptype).setQualifiedName(fqn);

        var writer = getWriter(namespace);
        doAddParameterType(Arrays.asList(ptype), true);
        writer.writer.write(namespace, this);
        return ptype;
    }

    /**
     * Creates if it does not exist and returns a basic argument type corresponding to the value.
     * <p>
     * The namespace has to be writable otherwise an IllegalArgumentException will be thrown
     */
    public ArgumentType getOrCreateBasicArgumentType(String namespace, Type type, UnitType unit) throws IOException {

        switch (type) {
        case BINARY:
            return getOrCreateArgumentType(namespace, "binary", unit,
                    () -> new BinaryArgumentType.Builder());
        case BOOLEAN:
            return getOrCreateArgumentType(namespace, "boolean", unit,
                    () -> new BooleanArgumentType.Builder());
        case STRING:
            return getOrCreateArgumentType(namespace, "string", unit,
                    () -> new StringArgumentType.Builder());
        case FLOAT:
            return getOrCreateArgumentType(namespace, "float32", unit,
                    () -> new FloatArgumentType.Builder().setSizeInBits(32));
        case DOUBLE:
            return getOrCreateArgumentType(namespace, "float64", unit,
                    () -> new FloatArgumentType.Builder().setSizeInBits(64));
        case SINT32:
            return getOrCreateArgumentType(namespace, "sint32", unit,
                    () -> new IntegerArgumentType.Builder().setSizeInBits(32).setSigned(true));
        case SINT64:
            return getOrCreateArgumentType(namespace, "sint64", unit,
                    () -> new IntegerArgumentType.Builder().setSizeInBits(64).setSigned(true));
        case UINT32:
            return getOrCreateArgumentType(namespace, "uint32", unit,
                    () -> new IntegerArgumentType.Builder().setSizeInBits(32).setSigned(false));
        case UINT64:
            return getOrCreateArgumentType(namespace, "uint64", unit,
                    () -> new IntegerArgumentType.Builder().setSizeInBits(64).setSigned(false));
        case TIMESTAMP:
            return getOrCreateArgumentType(namespace, "time", unit, () -> new AbsoluteTimeArgumentType.Builder());
        case ENUMERATED:
            return getOrCreateArgumentType(namespace, "enum", unit, () -> new EnumeratedArgumentType.Builder());
        default:
            throw new IllegalArgumentException(type + "is not a basic type");
        }
    }

    private ArgumentType getOrCreateArgumentType(String namespace, String name, UnitType unit,
            Supplier<ArgumentType.Builder<?>> supplier) throws IOException {

        String units;
        if (unit != null) {
            units = unit.getUnit();
            if (!"1".equals(unit.getFactor())) {
                units = unit.getFactor() + "x" + units;
            }
            if (unit.getPower() != 1) {
                units = units + "^" + unit.getPower();
            }
            name = name + "_" + units.replaceAll("/", "_");
        }

        String fqn = namespace + NameDescription.PATH_SEPARATOR + name;
        ArgumentType atype = getArgumentType(fqn);
        if (atype != null) {
            return atype;
        }
        ArgumentType.Builder<?> typeb = supplier.get().setName(name);
        if (unit != null) {
            ((BaseDataType.Builder<?>) typeb).addUnit(unit);
        }

        atype = typeb.build();
        ((NameDescription) atype).setQualifiedName(fqn);

        var writer = getWriter(namespace);
        doAddArgumentType(Arrays.asList(atype), true);
        writer.writer.write(namespace, this);
        return atype;
    }
}
