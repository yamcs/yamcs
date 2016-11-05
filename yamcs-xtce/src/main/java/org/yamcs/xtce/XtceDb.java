package org.yamcs.xtce;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.xml.XtceAliasSet;

/**
 *XtceDB database
 * 
 * It contains a SpaceSystem as defined in the Xtce schema and has lots of hashes to help find things quickly
 * 
 * @author mache
 */
public class XtceDb implements Serializable {
    private static final long  serialVersionUID   = 54L;
    SpaceSystem rootSystem;

    //rwLock is used to guard the read/write of parameters and spaceSystems which are the only ones that can change dynamically as of now
    ReadWriteLock rwLock = new ReentrantReadWriteLock();

    SystemParameterDb sysParamDb;

    public XtceDb(SpaceSystem spaceSystem) {
        this.rootSystem=spaceSystem;
    }

    transient static Logger log = LoggerFactory.getLogger(XtceDb.class);

    //map from the fully qualified names to the objects
    private HashMap<String, SpaceSystem> spaceSystems = new HashMap<String, SpaceSystem>();
    private Map<String, SequenceContainer> sequenceContainers = new LinkedHashMap<String, SequenceContainer>();
    private Map<String, Parameter> parameters = new LinkedHashMap<String, Parameter>();
    private HashMap<String, Algorithm> algorithms = new HashMap<String, Algorithm>();
    private HashMap<String, MetaCommand> commands = new HashMap<String, MetaCommand>();

    @SuppressWarnings("rawtypes")
    private HashMap<Class<?>, NonStandardData> nonStandardDatas = new HashMap<Class<?>, NonStandardData>();

    //different namespaces
    private NamedDescriptionIndex<SpaceSystem> spaceSystemAliases = new NamedDescriptionIndex<SpaceSystem>();
    private NamedDescriptionIndex<Parameter> parameterAliases = new NamedDescriptionIndex<Parameter>();
    private NamedDescriptionIndex<SequenceContainer> sequenceContainerAliases =new NamedDescriptionIndex<SequenceContainer>();
    private NamedDescriptionIndex<Algorithm> algorithmAliases = new NamedDescriptionIndex<Algorithm>();
    private NamedDescriptionIndex<MetaCommand> commandAliases = new NamedDescriptionIndex<MetaCommand>();

    private Set<String> namespaces = new HashSet<>();


    //this is the sequence container where the xtce processors start processing
    //we should perhaps have possibility to specify different ones for different streams
    SequenceContainer rootSequenceContainer;
    /**
     * Maps the Parameter to a list of ParameterEntry such that we know from
     * which container we can extract this parameter
     */
    private HashMap<Parameter, ArrayList<ParameterEntry>> parameter2ParameterEntryMap;

    /**
     * maps the SequenceContainer to a list of other EntryContainers in case of
     * aggregation
     */
    private HashMap<SequenceContainer, ArrayList<ContainerEntry>> sequenceContainer2ContainerEntryMap;

    /**
     * maps the SequenceContainer to a list of containers inheriting this one
     */
    private HashMap<SequenceContainer, ArrayList<SequenceContainer>> sequenceContainer2InheritingContainerMap;


    public SequenceContainer getSequenceContainer(String qualifiedName) {
        return sequenceContainers.get(qualifiedName);
    }

    public SequenceContainer getSequenceContainer(String namespace, String name) {
        return sequenceContainerAliases.get(namespace, name);
    }

    public SequenceContainer getSequenceContainer(NamedObjectId id) {
        if(id.hasNamespace()) {
            return sequenceContainerAliases.get(id.getNamespace(), id.getName());
        } else {
            return sequenceContainerAliases.get(id.getName());
        }
    }

    public Parameter getParameter(String qualifiedName) {
        rwLock.readLock().lock();
        try {
            return parameters.get(qualifiedName);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Parameter getParameter(String namespace, String name) {
        rwLock.readLock().lock();
        try {
            return parameterAliases.get(namespace,name);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Parameter getParameter(NamedObjectId id) {
        rwLock.readLock().lock();
        try {
            if (id.hasNamespace()) {
                return parameterAliases.get(id.getNamespace(), id.getName());
            } else {
                return parameterAliases.get(id.getName());
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public SequenceContainer getRootSequenceContainer() {
        return rootSequenceContainer;
    }

    public void setRootSequenceContainer(SequenceContainer sc) {
        this.rootSequenceContainer=sc;
    }

    public Algorithm getAlgorithm(String qualifiedName) {
        return algorithmAliases.get(qualifiedName);
    }

    public Algorithm getAlgorithm(String namespace, String name) {
        return algorithmAliases.get(namespace, name);
    }

    public Algorithm getAlgorithm(NamedObjectId id) {
        if (id.hasNamespace()) {
            return algorithmAliases.get(id.getNamespace(), id.getName());
        } else {
            return algorithmAliases.get(id.getName());
        }
    }

    public Collection<Algorithm> getAlgorithms() {
        return algorithms.values();
    }

    public Collection<Parameter> getParameters() {
        rwLock.readLock().lock();
        try {
            return parameters.values();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean containsNamespace(String namespace) {
        return namespaces.contains(namespace);
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    public MetaCommand getMetaCommand(String qualifiedName) {
        return commandAliases.get(qualifiedName);
    }

    /**
     * Returns a command based on a name in a namespace
     * @param namespace
     * @param name
     * @return
     */
    public MetaCommand getMetaCommand(String namespace, String name) {
        return commandAliases.get(namespace, name);
    }

    public MetaCommand getMetaCommand(NamedObjectId id) {
        if (id.hasNamespace()) {
            return commandAliases.get(id.getNamespace(), id.getName());
        } else {
            return commandAliases.get(id.getName());
        }
    }

    /**
     * Returns the list of MetaCommmands in the XTCE database
     * @return
     */
    public Collection<MetaCommand> getMetaCommands() {
        return commands.values();
    }

    public SpaceSystem getRootSpaceSystem() {
        return rootSystem;
    }

    public SpaceSystem getSpaceSystem(String qualifiedName) {
        rwLock.readLock().lock();
        try {
            return spaceSystemAliases.get(qualifiedName);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public SpaceSystem getSpaceSystem(String namespace, String name) {
        rwLock.readLock().lock();
        try {
            return spaceSystemAliases.get(namespace, name);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public SpaceSystem getSpaceSystem(NamedObjectId id) {
        rwLock.readLock().lock();
        try {
            if (id.hasNamespace()) {
                return spaceSystemAliases.get(id.getNamespace(), id.getName());
            } else {
                return spaceSystemAliases.get(id.getName());
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Collection<SequenceContainer> getSequenceContainers() {
        return sequenceContainers.values();
    }

    /**
     * 
     * @return list of ParameterEntry corresponding to a given parameter.
     */
    public List<ParameterEntry> getParameterEntries(Parameter p) {
        return parameter2ParameterEntryMap.get(p);
    }

    /**
     * @return list of ContainerEntry corresponding to a given sequence
     *         container.
     */
    public List<ContainerEntry> getContainerEntries(SequenceContainer sc) {
        return sequenceContainer2ContainerEntryMap.get(sc);
    }

    public Collection<String> getParameterNames() {
        return parameters.keySet();
    }

    @SuppressWarnings("unchecked")
    public <T extends NonStandardData<T>> T getNonStandardDataOfType(Class<T> clazz) {
        if(nonStandardDatas.containsKey(clazz))
            return (T) nonStandardDatas.get(clazz);
        else
            return null;
    }

    @SuppressWarnings("rawtypes")
    public Collection<NonStandardData> getNonStandardData() {
        return nonStandardDatas.values();
    }

    /**
     * Called after the database has been populated to build the maps for
     * quickly finding things
     * 
     */
    public void buildIndexMaps() {
        buildSpaceSystemsMap(rootSystem) ;
        buildParameterMap(rootSystem);
        buildSequenceContainerMap(rootSystem);
        buildAlgorithmMap(rootSystem);
        buildMetaCommandMap(rootSystem);
        buildNonStandardDataMap(rootSystem);

        parameter2ParameterEntryMap = new HashMap<Parameter, ArrayList<ParameterEntry>>();
        sequenceContainer2ContainerEntryMap = new HashMap<SequenceContainer, ArrayList<ContainerEntry>>();
        sequenceContainer2InheritingContainerMap = new HashMap<SequenceContainer, ArrayList<SequenceContainer>>();
        for (SequenceContainer sc : sequenceContainers.values()) {
            for (SequenceEntry se : sc.getEntryList()) {
                if (se instanceof ParameterEntry) {
                    ParameterEntry pe = (ParameterEntry) se;
                    Parameter param = pe.getParameter();
                    ArrayList<ParameterEntry> al = parameter2ParameterEntryMap.get(param);
                    if (al == null) {
                        al = new ArrayList<ParameterEntry>();
                        parameter2ParameterEntryMap.put(param, al);
                    }
                    al.add(pe);
                } else if (se instanceof ContainerEntry) {
                    ContainerEntry ce = (ContainerEntry) se;
                    ArrayList<ContainerEntry> al = sequenceContainer2ContainerEntryMap
                            .get(ce.getRefContainer());
                    if (al == null) {
                        al = new ArrayList<ContainerEntry>();
                        sequenceContainer2ContainerEntryMap.put(ce.getRefContainer(), al);
                    }
                    al.add(ce);
                }
            }
            if (sc.baseContainer != null) {
                ArrayList<SequenceContainer> al_sc = sequenceContainer2InheritingContainerMap
                        .get(sc.baseContainer);
                if (al_sc == null) {
                    al_sc = new ArrayList<SequenceContainer>();
                    sequenceContainer2InheritingContainerMap.put(sc.baseContainer, al_sc);
                }
                al_sc.add(sc);
            }
        }


        //build aliases maps
        for (SpaceSystem ss : spaceSystems.values()) {
            spaceSystemAliases.add(ss);
            XtceAliasSet aliases=ss.getAliasSet();
            if(aliases!=null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for (SequenceContainer sc : sequenceContainers.values()) {
            sequenceContainerAliases.add(sc);
            XtceAliasSet aliases=sc.getAliasSet();
            if(aliases!=null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for(Parameter p:parameters.values()) {
            parameterAliases.add(p);
            XtceAliasSet aliases=p.getAliasSet();
            if(aliases!=null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for(Algorithm a:algorithms.values()) {
            algorithmAliases.add(a);
            XtceAliasSet aliases=a.getAliasSet();
            if(aliases!=null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }

        for(MetaCommand mc:commands.values()) {
            commandAliases.add(mc);
            XtceAliasSet aliases=mc.getAliasSet();
            if(aliases!=null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        }
    }

    private void buildSpaceSystemsMap(SpaceSystem ss) {
        spaceSystems.put(ss.getQualifiedName(), ss);
        for(SpaceSystem ss1:ss.getSubSystems()) {
            buildSpaceSystemsMap(ss1);
        }
    }

    private void buildParameterMap(SpaceSystem ss) {
        for(Parameter p:ss.getParameters()) {
            parameters.put(p.getQualifiedName(), p);
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            buildParameterMap(ss1);
        }
    }

    private void buildSequenceContainerMap(SpaceSystem ss) {
        for(SequenceContainer sc:ss.getSequenceContainers()) {
            sequenceContainers.put(sc.getQualifiedName(), sc);
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            buildSequenceContainerMap(ss1);
        }
    }

    private void buildAlgorithmMap(SpaceSystem ss) {
        for(Algorithm a:ss.getAlgorithms()) {
            algorithms.put(a.getQualifiedName(), a);
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            buildAlgorithmMap(ss1);
        }
    }

    private void buildMetaCommandMap(SpaceSystem ss) {
        for(MetaCommand mc:ss.getMetaCommands()) {
            commands.put(mc.getQualifiedName(), mc);
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            buildMetaCommandMap(ss1);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void buildNonStandardDataMap(SpaceSystem ss) {
        for(NonStandardData data:ss.getNonStandardData()) {
            if(nonStandardDatas.containsKey(data.getClass())) {
                NonStandardData mergeResult=nonStandardDatas.get(data.getClass()).mergeWithChild(data);
                nonStandardDatas.put(data.getClass(), mergeResult);
            } else {
                nonStandardDatas.put(data.getClass(), data);
            }
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            buildNonStandardDataMap(ss1);
        }
    }

    public List<SequenceContainer> getInheritingContainers(SequenceContainer container) {
        return sequenceContainer2InheritingContainerMap.get(container);
    }

    /**
     * Adds  a new parameter to the XTCE db.
     *
     * 
     * If the SpaceSystem where this parameter belongs does not exist, throws an IllegalArgumentException
     * If the SpaceSystem where this parameter belongs already contains an parameter by this name, throws and IllegalArgumentException
     * 
     * Note that this method is used to create parameters on the fly. 
     * The parameers are not saved anywhere and they will not be available when this object is created by the XtceDbFactory.
     * 
     * @param p
     */
    public void addParameter(Parameter p) {
        rwLock.writeLock().lock();
        try {
            String ssname = p.getSubsystemName();
            SpaceSystem ss = spaceSystems.get(ssname);
            if(ss==null) {
                throw new IllegalArgumentException("No SpaceSystem by name '"+ssname+"'");
            }
            ss.addParameter(p);
            parameters.put(p.getQualifiedName(), p);

            parameterAliases.add(p);
            XtceAliasSet aliases=p.getAliasSet();
            if(aliases!=null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Adds a new spacesystem to the XTCE db.
     * 
     * It throws an IllegalArgumentException in the following circumstances:
     *  - if a SpaceSystem with this name already exists
     *  - if system.getParent() does not return null 
     *  - if the parent SpaceSystem does not exist
     *  - if the space system is not empty
     *  
     * This method also sets the parent of the passed spacesystem to its parent object.
     * 
     * Note that this method is used to create SpaceSystems on the fly. 
     * The SpaceSystems are not saved anywhere and they will not be available when this object is created by the XtceDbFactory. 
     * @param system
     * 
     */
    public void addSpaceSystem(SpaceSystem system) {
        rwLock.writeLock().lock();
        try {
            if(system.getParent()!=null) throw new IllegalArgumentException("The parent of the space system has to be null (it will be set by this method");
            if(!system.getParameters().isEmpty() || !system.getSequenceContainers().isEmpty()|| !system.getAlgorithms().isEmpty()
                    || !system.getMetaCommands().isEmpty()) throw new IllegalArgumentException("The space system must be empty (no parameters, containers, commands, algorithms)");


            String parentName = system.getSubsystemName();
            SpaceSystem parent = spaceSystems.get(parentName);
            if(parent==null) throw new IllegalArgumentException("The parent subsystem '"+parentName+"' does not exist");


            parent.addSpaceSystem(system);
            system.setParent(parent);

            spaceSystems.put(system.getQualifiedName(), system);
            spaceSystemAliases.add(system);
            XtceAliasSet aliases = system.getAliasSet();
            if(aliases!=null) {
                aliases.getNamespaces().forEach(ns -> namespaces.add(ns));
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void print(SpaceSystem ss, PrintStream out) {
        if( ss.getHeader() != null ) {
            out.println("=========SpaceSystem "+ss.getQualifiedName()+" version: "+ss.getHeader().getVersion()+" date: "+ss.getHeader().getDate()+"=========");
        } else {
            out.println("=========SpaceSystem "+ss.getQualifiedName()+" (no header information)=========");
        }

        Comparator<NameDescription> comparator=new Comparator<NameDescription>() {
            @Override
            public int compare(NameDescription o1, NameDescription o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };

        SequenceContainer[] sca=ss.getSequenceContainers().toArray(new SequenceContainer[0]);
        Arrays.sort(sca, comparator);
        for (SequenceContainer sc : sca) {
            sc.print(out);
        }

        Algorithm[] aa=ss.getAlgorithms().toArray(new Algorithm[0]);
        Arrays.sort(aa, comparator);
        for (Algorithm a : aa) {
            a.print(out);
        }

        MetaCommand[] mca=ss.getMetaCommands().toArray(new MetaCommand[0]);
        Arrays.sort(mca, comparator);
        for (MetaCommand mc : mca) {
            mc.print(out);
        }

        //print the list of system variables if any (because those will not be part of the sequence containers)
        List<SystemParameter> systemVariables = new ArrayList<SystemParameter>();
        for(Parameter p: ss.getParameters()) {
            if(p instanceof SystemParameter) {
                systemVariables.add((SystemParameter)p);
            }
        }
        if(!systemVariables.isEmpty()) {
            out.println("System Parameters: ");
            SystemParameter[] sva=systemVariables.toArray(new SystemParameter[0]);
            Arrays.sort(sva, comparator);
            for (SystemParameter sv : sva) {
                out.println("\t"+sv.getName());
            }
        }

        SpaceSystem[] ssa=ss.getSubSystems().toArray(new SpaceSystem[0]);
        Arrays.sort(ssa, comparator);
        for(SpaceSystem ss1:ssa) {
            print(ss1, out);
        }
    }

    public void print(PrintStream out) {
        print(rootSystem, out);

        Set<Parameter> orphanedParameters = new HashSet<Parameter>();
        orphanedParameters.addAll(parameters.values());
        removeNonOrphaned(rootSystem, orphanedParameters);
        orphanedParameters.removeAll(parameter2ParameterEntryMap.keySet());


        if(!orphanedParameters.isEmpty()) {
            out.println("================ Orphaned parameters (not referenced in any container or algorithm):");
            for(Parameter p:orphanedParameters) {
                String namespaces = "";
                if(p.getAliasSet()!=null) namespaces = ", aliases: "+ p.getAliasSet();
                out.println(p.getQualifiedName()+", datasource: "+p.getDataSource()+ namespaces);
            }
        }
    }

    private void removeNonOrphaned(SpaceSystem ss, Set<Parameter> orphanedParameters) {
        for(Algorithm a:ss.getAlgorithms()) {
            for(InputParameter p:a.getInputSet()) {
                orphanedParameters.remove(p.getParameterInstance().getParameter());
            }
            for(OutputParameter p:a.getOutputSet()) {
                orphanedParameters.remove(p.getParameter());
            }
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            removeNonOrphaned(ss1, orphanedParameters);
        }        
    }

    public void setSystemParameterDb(SystemParameterDb sysParamDb) {
        this.sysParamDb = sysParamDb;
    }

    public SystemParameterDb getSystemParameterDb() {
        return sysParamDb;
    }


}