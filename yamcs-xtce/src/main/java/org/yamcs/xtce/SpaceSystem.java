package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

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
  
    private static final long serialVersionUID = 3L;
    private SequenceContainer rootSequenceContainer;
    
    
    public SpaceSystem(String name) {
        super(name);
    }

    Header header;
    private HashMap<String, SequenceContainer> containers=new HashMap<String, SequenceContainer>();
    private HashMap<String, Parameter> parameters = new HashMap<String, Parameter>(); 
    private HashMap<String, ParameterType> parameterTypes=new HashMap<String, ParameterType>();
    private HashMap<String, MetaCommand> commands=new HashMap<String, MetaCommand>();
    
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
            throw new IllegalArgumentException("there is already a parameter with name "+parameter.getName());
        parameters.put(parameter.getName(), parameter);
    }

    public void addParameterType(ParameterType parameterType) {
        String ptn=((NameDescription)parameterType).getName();
        if(parameterTypes.containsKey(ptn)) 
            throw new IllegalArgumentException("there is already a parameter type with name "+ptn);
        parameterTypes.put(ptn, parameterType);
        
    }
    
    
    public void addMetaCommand(MetaCommand command) {
        if(commands.containsKey(command.getName()))
            throw new IllegalArgumentException("there is already a command with name "+command.getName());
        commands.put(command.getName(), command);
    }
    
    public ParameterType getParameterType(String typeName) {
        return parameterTypes.get(typeName);
    }

    public SequenceContainer getSequenceContainer(String refName) {
        return containers.get(refName);
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

  
}
