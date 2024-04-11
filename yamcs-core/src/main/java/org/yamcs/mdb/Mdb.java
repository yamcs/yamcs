package org.yamcs.mdb;

import static org.yamcs.xtce.NameDescription.PATH_SEPARATOR;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SystemParameter;
import org.yamcs.xtce.XtceDb;

/**
 * Wraps an {@link Mdb} object.
 * <p>
 * Offers persistence capabilities for selected subtrees.
 */
public class Mdb extends XtceDb {

    private static final long serialVersionUID = 1L;
    final transient Map<String, SpaceSystemWriter> subsystemWriters;

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
}
