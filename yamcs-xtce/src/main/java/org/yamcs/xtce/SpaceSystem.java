package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SpaceSystem is a collection of SpaceSystem(s) including space assets, ground assets, 
 * multi-satellite systems and sub-systems.  A SpaceSystem is the root element for the set of data 
 * necessary to monitor and command an arbitrary space device - this includes the binary decomposition the
 * data streams going into and out of a device.
 * 
 * 
 * @author nm
 *
 */
public class SpaceSystem extends NameDescription {
  
    private static final long serialVersionUID = 4L;
    private SequenceContainer rootSequenceContainer;
    
    
    public SpaceSystem(String name) {
        super(name);
    }

    Header header;
    private Map<String, SequenceContainer> containers=new LinkedHashMap<String, SequenceContainer>();
    private Map<String, Parameter> parameters = new LinkedHashMap<String, Parameter>(); 
    private HashMap<String, ParameterType> parameterTypes=new HashMap<String, ParameterType>();
    private HashMap<String, Algorithm> algorithms=new HashMap<String, Algorithm>();
    private HashMap<String, MetaCommand> commands=new HashMap<String, MetaCommand>();
    private HashMap<Class<?>, NonStandardData> nonStandardDatas=new HashMap<Class<?>, NonStandardData>();
    
    private HashMap<String, SpaceSystem> subsystems=new HashMap<String, SpaceSystem>();
    static Logger log = LoggerFactory.getLogger(SpaceSystem.class.getName());
    
    transient List<NameReference> unresolvedReferences=new ArrayList<NameReference>();
    SpaceSystem parent;
    
    public void setHeader(Header h) {
        this.header=h;
     }
    
    /**
     * Register the container
     * 
     * @param container  Container to be registered
     */
    public void addSequenceContainer(SequenceContainer container) {
        if(containers.containsKey(container.getName()))
            throw new IllegalArgumentException("there is already a container with name "+container.getName());
        containers.put(container.getName(), container);
    }
    
    public void addParameter(Parameter parameter) throws IllegalArgumentException{
        if(parameters.containsKey(parameter.getName())) 
            throw new IllegalArgumentException("there is already a parameter with name "+parameter.getName()+" in space system "+qualifiedName );
        parameters.put(parameter.getName(), parameter);
    }

    public void addParameterType(ParameterType parameterType) {
        String ptn=((NameDescription)parameterType).getName();
        if(parameterTypes.containsKey(ptn)) 
            throw new IllegalArgumentException("there is already a parameter type with name "+ptn);
        parameterTypes.put(ptn, parameterType);
        
    }

    public void addAlgorithm(Algorithm algorithm) {
        if(algorithms.containsKey(algorithm.getName()))
            throw new IllegalArgumentException("there is already an algorithm with name "+algorithm.getName());
        algorithms.put(algorithm.getName(), algorithm);
    }

    public void addMetaCommand(MetaCommand command) {
        if(commands.containsKey(command.getName()))
            throw new IllegalArgumentException("there is already a command with name "+command.getName());
        commands.put(command.getName(), command);
    }

    public MetaCommand getMetaCommand(String refName) {
        return commands.get(refName);
    }

    public ParameterType getParameterType(String typeName) {
        return parameterTypes.get(typeName);
    }

    public SequenceContainer getSequenceContainer(String refName) {
        return containers.get(refName);
    }

    public Algorithm getAlgorithm(String algoName) {
        return algorithms.get(algoName);
    }
    
    public Parameter getParameter(String refName) {
        return parameters.get(refName);
    }

    public void addSpaceSystem(SpaceSystem ss) throws IllegalArgumentException {
        if(subsystems.containsKey(ss.getName())) 
            throw new IllegalArgumentException("there is already a subsystem with name "+ss.getName());
        subsystems.put(ss.getName(), ss);
        ss.setParent(this);
    }

    public void addUnresolvedReference(NameReference nr) {
        unresolvedReferences.add(nr);
    }
    public Collection<SequenceContainer> getSequenceContainers() {
        return containers.values();
    }

    public Collection<Parameter> getParameters() {
        return parameters.values();
    }
    
    public Collection<ParameterType> getParameterTypes() {
        return parameterTypes.values();
    }
    
    public Collection<SpaceSystem> getSubSystems() {
        return subsystems.values();
    }
    
    public Collection<Algorithm> getAlgorithms() {
        return algorithms.values();
    }
    
    public Collection<MetaCommand> getMetaCommands() {
        return commands.values();
    }

    public List<NameReference> getUnresolvedReferences() {
        return unresolvedReferences;
    }

    public void setParent(SpaceSystem parent) {
        this.parent=parent;
    }
    
    public SpaceSystem getParent() {
        return parent;
    }

    public SpaceSystem getSubsystem(String sname) {
        return subsystems.get(sname);
    }

    public SequenceContainer getRootSequenceContainer() {
        return rootSequenceContainer;
    }
    
    public void setRootSequenceContainer(SequenceContainer sc) {
        this.rootSequenceContainer=sc;
    }

    public Header getHeader() {
        return header;
    }

    /**
     * Add non-standard data to this SpaceSystem. This enables loading any kind
     * of data from within custom SpaceSystemLoaders and making it available
     * through the XtceDb.
     * <p>
     * Non-standard data is distinguished from each other using the classname.
     * Only one object is allowed for each classname.
     */
    public void addNonStandardData(NonStandardData data) {
        if(nonStandardDatas.containsKey(data.getClass()))
            throw new IllegalArgumentException("there is already non-standard data of type "+data.getClass());
        nonStandardDatas.put(data.getClass(), data);
    }
    
    @SuppressWarnings("unchecked")
    public <T extends NonStandardData> T getNonStandardDataOfType(Class<T> clazz) {
        if(nonStandardDatas.containsKey(clazz))
            return (T) nonStandardDatas.get(clazz);
        else
            return null;
    }
    
    public Collection<NonStandardData> getNonStandardData() {
        return nonStandardDatas.values();
    }
    
    @Override
    public String toString() {
    	return "SpaceSystem["+getName()+"]";
    }

    
    
}
