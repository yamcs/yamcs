package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.ReferenceFinder;
import org.yamcs.xtce.util.ReferenceFinder.FoundReference;
import org.yamcs.xtce.xml.XtceAliasSet;

/**
 * SpaceSystem is a collection of SpaceSystem(s) including space assets, ground assets, multi-satellite systems and
 * sub-systems. A SpaceSystem is the root element for the set of data necessary to monitor and command an arbitrary
 * space device - this includes the binary decomposition the data streams going into and out of a device.
 *
 *
 * @author nm
 *
 */
public class SpaceSystem extends NameDescription {

    private static final long serialVersionUID = 6L;

    Header header;
    private Map<String, SequenceContainer> containers = new LinkedHashMap<>();
    private Map<String, Parameter> parameters = new LinkedHashMap<>();
    private HashMap<String, ParameterType> parameterTypes = new HashMap<>();
    private HashMap<String, Algorithm> algorithms = new HashMap<>();
    private HashMap<String, MetaCommand> commands = new HashMap<>();
    private HashMap<Class<?>, NonStandardData> nonStandardDatas = new HashMap<>();
    private Map<String, CommandContainer> cmdContainers = new LinkedHashMap<>();

    private Map<String, SpaceSystem> subsystems = new LinkedHashMap<>();
    static Logger log = LoggerFactory.getLogger(SpaceSystem.class.getName());
    private HashMap<String, ArgumentType> argumentTypes = new HashMap<>();

    transient List<NameReference> unresolvedReferences = new ArrayList<>();
    SpaceSystem parent;

    public SpaceSystem(String name) {
        super(name);
    }

    public void setHeader(Header h) {
        this.header = h;
    }

    /**
     * Register the container
     *
     * @param container
     *            Container to be registered
     */
    public void addSequenceContainer(SequenceContainer container) {
        if (containers.containsKey(container.getName())) {
            throw new IllegalArgumentException("duplicate container '" + container.getName() + "'");
        }
        containers.put(container.getName(), container);
    }

    public void addParameter(Parameter parameter) throws IllegalArgumentException {
        if (parameters.containsKey(parameter.getName())) {
            throw new IllegalArgumentException("duplicate parameter '" + parameter.getName() + "'");
        }
        parameters.put(parameter.getName(), parameter);
    }

    public void addParameterType(ParameterType parameterType) {
        String ptn = ((NameDescription) parameterType).getName();
        if (parameterTypes.containsKey(ptn)) {
            throw new IllegalArgumentException("duplicate parameter type '" + ptn + "'");
        }
        parameterTypes.put(ptn, parameterType);

    }

    public boolean removeParameterType(ParameterType ptype) {
        return parameterTypes.remove(ptype.getName(), ptype);
    }

    public void addArgumentType(ArgumentType argumentType) {
        String atn = ((NameDescription) argumentType).getName();
        if (argumentTypes.containsKey(atn)) {
            throw new IllegalArgumentException("duplicate argument type '" + atn + "'");
        }
        argumentTypes.put(atn, argumentType);
    }

    public void addAlgorithm(Algorithm algorithm) {
        if (algorithms.containsKey(algorithm.getName())) {
            throw new IllegalArgumentException("duplcate algorithm '" + algorithm.getName() + "'");
        }
        algorithms.put(algorithm.getName(), algorithm);
    }

    public void addMetaCommand(MetaCommand command) {
        if (commands.containsKey(command.getName())) {
            throw new IllegalArgumentException("duplicate command '" + command.getName() + "'");
        }
        commands.put(command.getName(), command);
    }

    public MetaCommand getMetaCommand(String refName) {
        return commands.get(refName);
    }

    public ParameterType getParameterType(String typeName) {
        return parameterTypes.get(typeName);
    }

    public ArgumentType getArgumentType(String typeName) {
        return argumentTypes.get(typeName);
    }

    public SequenceContainer getSequenceContainer(String refName) {
        return containers.get(refName);
    }

    public void addCommandContainer(CommandContainer cmdContainer) {
        if (cmdContainers.containsKey(cmdContainer.getName())) {
            throw new IllegalArgumentException("duplicate container '" + cmdContainer.getName() + "'");
        }
        cmdContainers.put(cmdContainer.getName(), cmdContainer);
    }

    public CommandContainer getCommandContainer(String name) {
        return cmdContainers.get(name);
    }

    public Algorithm getAlgorithm(String algoName) {
        return algorithms.get(algoName);
    }

    public Parameter getParameter(String refName) {
        return parameters.get(refName);
    }

    public void addSpaceSystem(SpaceSystem ss) throws IllegalArgumentException {
        if (subsystems.containsKey(ss.getName())) {
            throw new IllegalArgumentException("there is already a subsystem with name " + ss.getName());
        }
        subsystems.put(ss.getName(), ss);
        ss.setParent(this);
    }

    /**
     * tries to resolve the reference immediately in the current system; if it cannot, add it to the list of unresolved
     * references - the reference will be resolved when all parts are available
     */
    public void addUnresolvedReference(NameReference nr) {
        if (nr.isAbsolute()) {
            // do not resolve absolute references, they will be resolved when assembling the MDB
            unresolvedReferences.add(nr);
        } else {
            // try to resolve it immediately
            FoundReference foundRef = ReferenceFinder.findReference(this, nr);
            if (foundRef == null || !foundRef.isComplete()) {
                unresolvedReferences.add(nr);
            } else {
                foundRef.resolved(nr);
            }
        }
    }

    public Collection<SequenceContainer> getSequenceContainers() {
        return containers.values();
    }

    public Collection<CommandContainer> getCommandContainers() {
        return cmdContainers.values();
    }

    public int getSequenceContainerCount(boolean recurse) {
        if (!recurse) {
            return containers.size();
        }
        int total = containers.size();
        for (SpaceSystem sub : getSubSystems()) {
            total += sub.getSequenceContainerCount(recurse);
        }
        return total;
    }

    /**
     * Returns the direct sub parameters of this space system
     */
    public Collection<Parameter> getParameters() {
        return parameters.values();
    }

    /**
     * Returns the parameters defined in this space system, or under any of its sub space systems
     */
    public Collection<Parameter> getParameters(boolean recurse) {
        if (!recurse) {
            return getParameters();
        }
        List<Parameter> res = new ArrayList<>();
        res.addAll(parameters.values());
        for (SpaceSystem sub : getSubSystems()) {
            res.addAll(sub.getParameters(recurse));
        }
        return res;
    }

    public int getParameterCount(boolean recurse) {
        if (!recurse) {
            return parameters.size();
        }
        int total = parameters.size();
        for (SpaceSystem sub : getSubSystems()) {
            total += sub.getParameterCount(recurse);
        }
        return total;
    }

    public Collection<ParameterType> getParameterTypes() {
        return parameterTypes.values();
    }

    public int getParameterTypeCount(boolean recurse) {
        if (!recurse) {
            return parameterTypes.size();
        }
        int total = parameterTypes.size();
        for (SpaceSystem sub : getSubSystems()) {
            total += sub.getParameterTypeCount(recurse);
        }
        return total;
    }

    public Collection<ArgumentType> getArgumentTypes() {
        return argumentTypes.values();
    }

    public Collection<SpaceSystem> getSubSystems() {
        return subsystems.values();
    }

    public Collection<Algorithm> getAlgorithms() {
        return algorithms.values();
    }

    public int getAlgorithmCount(boolean recurse) {
        if (!recurse) {
            return algorithms.size();
        }
        int total = algorithms.size();
        for (SpaceSystem sub : getSubSystems()) {
            total += sub.getAlgorithmCount(recurse);
        }
        return total;
    }

    public Collection<MetaCommand> getMetaCommands() {
        return commands.values();
    }

    /**
     * remove parameter from SpaceSystem - only used during loading or from XtceDb XtceDb has several maps pointing to
     * these parameters.
     * 
     * @param p
     *            parameter to remove
     */
    public void removeParameter(Parameter p) {
        parameters.remove(p.getName());
    }

    public int getMetaCommandCount(boolean recurse) {
        if (!recurse) {
            return commands.size();
        }
        int total = commands.size();
        for (SpaceSystem sub : getSubSystems()) {
            total += sub.getMetaCommandCount(recurse);
        }
        return total;
    }

    public List<NameReference> getUnresolvedReferences() {
        return unresolvedReferences;
    }

    public void setParent(SpaceSystem parent) {
        this.parent = parent;
    }

    public SpaceSystem getParent() {
        return parent;
    }

    public SpaceSystem getSubsystem(String sname) {
        return subsystems.get(sname);
    }

    public SequenceContainer getRootSequenceContainer() {
        return containers.values().stream().filter(sc -> sc.getBaseContainer() == null).findFirst().orElse(null);
    }

    public Header getHeader() {
        return header;
    }

    /**
     * Add non-standard data to this SpaceSystem. This enables loading any kind of data from within custom
     * SpaceSystemLoaders and making it available through the XtceDb.
     * <p>
     * Non-standard data is distinguished from each other using the classname. Only one object is allowed for each
     * classname.
     */
    public void addNonStandardData(NonStandardData data) {
        if (nonStandardDatas.containsKey(data.getClass())) {
            throw new IllegalArgumentException("there is already non-standard data of type " + data.getClass());
        }
        nonStandardDatas.put(data.getClass(), data);
    }

    @SuppressWarnings("unchecked")
    public <T extends NonStandardData> T getNonStandardDataOfType(Class<T> clazz) {
        if (nonStandardDatas.containsKey(clazz)) {
            return (T) nonStandardDatas.get(clazz);
        } else {
            return null;
        }
    }

    public Collection<NonStandardData> getNonStandardData() {
        return nonStandardDatas.values();
    }

    @Override
    public String toString() {
        return "SpaceSystem[" + getName() + "]";
    }

    /**
     * Searches through all namespaces for a parameter with the given alias.
     * 
     * Returns a list with all matches.
     * 
     * This is an expensive operation as it iterates over all parameters
     * 
     * @param alias
     * @return a list of parameters matching the alias. If no alias matches the list will be empty.
     */
    public List<Parameter> getParameterByAlias(String alias) {
        return getObjectByAlias(alias, parameters.values());
    }

    public List<SequenceContainer> getSequenceContainerByAlias(String alias) {
        return getObjectByAlias(alias, containers.values());
    }

    public List<MetaCommand> getMetaCommandByAlias(String alias) {
        return getObjectByAlias(alias, commands.values());
    }

    private static <T extends NameDescription> List<T> getObjectByAlias(String alias, Collection<T> ndObjects) {
        List<T> l = new ArrayList<>(1);
        for (T nd : ndObjects) {

            XtceAliasSet aliasSet = nd.getAliasSet();
            if (aliasSet == null) {
                continue;
            }
            for (Map.Entry<String, String> m : nd.getAliasSet().getAliases().entrySet()) {
                if (m.getValue().equals(alias)) {
                    l.add(nd);
                }
            }
        }
        return l;
    }

}
