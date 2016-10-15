package org.yamcs.xtceproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.DatabaseLoadException;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.NameReference;
import org.yamcs.xtce.NameReference.Type;
import org.yamcs.xtce.NonStandardData;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SpaceSystemLoader;
import org.yamcs.xtce.SpreadsheetLoader;
import org.yamcs.xtce.SystemParameterDb;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtce.XtceLoader;


public class XtceDbFactory {
    static Logger log = LoggerFactory.getLogger(XtceDbFactory.class);


    /**
     * map instance names and config names to databases
     */
    static transient Map<String, XtceDb> instance2Db = new HashMap<>();
    static transient Map<String, Map<String, XtceDb>> instance2DbConfigs = new HashMap<>();
    static LoaderTree loaderTree;


    /**
     * Creates a new instance of the database in memory.
     * configSection is the top heading under which this appears in the mdb.yaml
     * @throws ConfigurationException 
     */
    @SuppressWarnings("unchecked")
    public static synchronized XtceDb createInstance(String configSection) throws ConfigurationException {
        YConfiguration c = null;
        c = YConfiguration.getConfiguration("mdb");

        if(configSection==null) {
            configSection=c.getFirstEntry();
        }

        XtceDb db=null;
        //
        // create MDB/spreadsheet/XTCE loaders according to configuration
        // settings
        //
        loaderTree= new LoaderTree(new RootSpaceSystemLoader());

        List<Object> list=c.getList(configSection);
        for(Object o: list) {
            if(o instanceof Map) {
                loaderTree.addChild(getLoaderTree(c, (Map<String, Object>) o));
            } else {
                throw new ConfigurationException("Expected type Map instead of "+o.getClass());
            }
        }

        boolean serializedLoaded = false;
        boolean loadSerialized = true;
        String filename = loaderTree.getConfigName()+".xtce";

        if (new File(getFullName(filename) + ".serialized").exists()) {
            try {
                RandomAccessFile raf = new RandomAccessFile(getFullName(filename) + ".consistency_date", "r");
                if(loaderTree.neesUpdate(raf)) {
                    loadSerialized = false;
                }
            } catch (IOException e) {
                if (new File(getFullName(filename) + ".serialized").exists()) {
                    log.warn("can't check the consistency date of the serialized database", e);
                }
                loadSerialized = false;
            }
        } else {
            loadSerialized = false;
        }

        if (loadSerialized) {
            try {
                db = loadSerializedInstance(getFullName(filename.toString()) + ".serialized");
                serializedLoaded = true;
            } catch (Exception e) {
                log.info("Cannot load serialized database", e);
                db = null;
            }
        }

        if (db == null) {
            //Construct a Space System with one branch from the config file and the other one /yamcs for system variables
            SpaceSystem rootSs = loaderTree.load();
            SystemParameterDb sysDb = new SystemParameterDb();

            rootSs.addSpaceSystem(sysDb.getYamcsSpaceSystem());
            //rootSs.setHeader(xss.getHeader());

            int n;
            while((n=resolveReferences(rootSs, rootSs, sysDb))>0 ){};
            StringBuffer sb=new StringBuffer();
            collectUnresolvedReferences(rootSs, sb);
            if(n==0) throw new ConfigurationException("Cannot resolve (circular?) references: "+ sb.toString());
            setQualifiedNames(rootSs, "");
            db = new XtceDb(rootSs);

            db.setSystemParameterDb(sysDb);

            //set the root sequence container as the first root sequence container found in the sub-systems. 
            for(SpaceSystem ss: rootSs.getSubSystems()) {
                SequenceContainer seqc = ss.getRootSequenceContainer();
                if(seqc!=null){
                    db.setRootSequenceContainer(seqc);
                }
            }

            db.buildIndexMaps();
        }
        // log.info("Loaded database with "+instance.sid2TcPacketMap.size()+" TC, "+instance.sid2SequenceContainertMap.size()+" TM containers, "+instance.sid2ParameterMap.size()+" TM parameters and "+instance.upcOpsname2PpMap.size()+" processed parameters");

        if ((!serializedLoaded)) {
            try {
                saveSerializedInstance(db, filename.toString());
                log.info("Serialized database saved locally");
            } catch (Exception e) {
                log.warn("Cannot save serialized MDB", e);
            }
        }
        return db;
    }

    /*collects a description for all unresolved references into the StringBuffer to raise an error*/
    private static void collectUnresolvedReferences(SpaceSystem ss, StringBuffer sb) {
        List<NameReference> refs = ss.getUnresolvedReferences();
        if(refs!=null) {
            for(NameReference nr: ss.getUnresolvedReferences()) {
                sb.append("system").append(ss.getName()).append(" ").append(nr.toString()).append("\n");
            }
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            collectUnresolvedReferences(ss1, sb);
        }
    }

    /**
     * resolves references in ss by going recursively to all sub-space systems (in the first call ss=rootSs)
     * 
     * @param ss
     * @param sysDb 
     * @return the number of references resolved or -1 if there was no reference to be resolved
     */
    private static int resolveReferences(SpaceSystem rootSs, SpaceSystem ss, SystemParameterDb sysDb) throws ConfigurationException {
        List<NameReference> refs = ss.getUnresolvedReferences();

        //This can happen when we deserialise the SpaceSystem since the unresolved references is a transient list.
        if(refs==null) refs = Collections.emptyList();

        int n = (refs.size()==0)?-1:0;

        Iterator<NameReference> it = refs.iterator();
        while (it.hasNext()) {
            NameReference nr=it.next();

            //Special case for system parameters: they are created on the fly
            NameDescription nd;
            if(nr.getType()==Type.PARAMETER && nr.getReference().startsWith(SystemParameterDb.YAMCS_SPACESYSTEM_NAME)) {
                nd = sysDb.getSystemParameter(nr.getReference(), true);
            } else {
                nd = findReference(rootSs, nr, ss);
            }
            if(nd==null) throw new ConfigurationException("Cannot resolve reference SpaceSystem: "+ss.getName()+" "+nr);
            if(nr.resolved(nd)) {
                n++;
                it.remove();
            }
        }
        for(SpaceSystem ss1:ss.getSubSystems()) {
            int m = resolveReferences(rootSs, ss1, sysDb);
            if(n==-1) {
                n = m;
            } else if(m>0) {
                n+=m;
            }
        }
        return n;
    }




    /**
     * find the reference nr mentioned in the space system ss by looking either in root (if absolute reference) or in the parent hierarchy if relative reference
     * 
     * @param rootSs
     * @param nr
     * @param ss
     * @return
     */
    static NameDescription findReference(SpaceSystem rootSs, NameReference nr, SpaceSystem ss) {
        String ref=nr.getReference();
        boolean absolute=false;
        SpaceSystem startSs=null;

        if(ref.startsWith("/")) {
            absolute=true;
            startSs=rootSs;
        } else if(ref.startsWith("./")|| ref.startsWith("..")) {
            absolute=true;
            startSs=ss;
        }

        if(absolute) {
            return findReference(startSs, nr);
        } else {
            //go up until the root
            NameDescription nd=null;
            startSs=ss;
            while(true) {
                nd=findReference(startSs, nr);
                if(nd!=null) break;
                if(startSs==rootSs) break;
                startSs=startSs.getParent();
            } 
            return nd;   
        }

    }
    /**
     * find reference starting at startSs and looking through the SpaceSystem path
     * @param startSs
     * @param nr
     * @return
     */
    private static NameDescription findReference(SpaceSystem startSs, NameReference nr) {
        String[] path=nr.getReference().split("/");
        SpaceSystem ss=startSs;

        for(int i=0; i<path.length-1; i++) {
            if(".".equals(path[i]) || "".equals(path[i])) {
                continue;
            } else if("..".equals(path[i])) {
                ss=ss.getParent();
                if(ss==null) break; //this can only happen if the root has no parent (normally it's its own parent)
                continue;
            } 

            if(i==path.length-1) break;

            ss = ss.getSubsystem(path[i]);

            if(ss==null) break;
        }
        if(ss==null) return null;

        String name=path[path.length-1];
        switch(nr.getType()) {
        case PARAMETER:
            return ss.getParameter(name);
        case PARAMETER_TYPE:
            return (NameDescription) ss.getParameterType(name);
        case SEQUENCE_CONTAINTER:
            return ss.getSequenceContainer(name);
        case META_COMMAND:
            return ss.getMetaCommand(name);
        }
        //shouldn't arrive here
        return null;
    }


    @SuppressWarnings({ "unchecked", "static-access" })
    private static LoaderTree getLoaderTree(YConfiguration c, Map<String,Object> m) throws ConfigurationException {
        String type=YConfiguration.getString(m, "type");
        Object args=null;
        if(m.containsKey("args")) {
            args=m.get("args");
        } else if(m.containsKey("spec")) {
            args=m.get("spec");
        }

        SpaceSystemLoader l;
        LoaderTree ltree;

        if (type.equals("xtce")) {
            l= new XtceLoader((String)args);
        } else if (type.equals("sheet")) {
            if(args==null) throw new ConfigurationException("No argument specified for loading the XTCE spreadhseet in mdb.yaml section: "+m);
            l=new SpreadsheetLoader((String)args);
        } else {
            // custom class
            try {
                YObjectLoader<SpaceSystemLoader> objloader=new YObjectLoader<SpaceSystemLoader>();
                l = objloader.loadObject(type, args);
            } catch (Exception e) {
                log.warn(e.toString());
                throw new ConfigurationException("Invalid database loader class: " + type, e);
            }
        }

        ltree=new LoaderTree(l);

        if(m.containsKey("subLoaders")) {
            List<Object> list=c.getList(m, "subLoaders");
            for(Object o: list) {
                if(o instanceof Map) {
                    ltree.addChild(getLoaderTree(c, (Map<String, Object>) o));
                } else {
                    throw new ConfigurationException("Expected type Map instead of "+o.getClass());
                }
            }
        }

        return ltree;
    }

    /**
     * Propagates qualified name to enclosing objects including subsystems. Also
     * registers aliases under each subsystem.
     */
    private static void setQualifiedNames(SpaceSystem ss, String parentqname) {
        String ssqname;
        if(String.valueOf(NameDescription.PATH_SEPARATOR).equals(parentqname)) { //parent is root
            ssqname = NameDescription.PATH_SEPARATOR+ss.getName();
        } else {
            ssqname = parentqname+NameDescription.PATH_SEPARATOR+ss.getName();
        }

        ss.setQualifiedName(ssqname);

        if (!"".equals(parentqname)) {
            ss.addAlias(parentqname, ss.getName());
        }
        for(Parameter p: ss.getParameters()) {
            p.setQualifiedName(ss.getQualifiedName()+NameDescription.PATH_SEPARATOR + p.getName());
        }
        for(ParameterType pt: ss.getParameterTypes()) {
            NameDescription nd=(NameDescription)pt;
            nd.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + nd.getName());
        }

        for(SequenceContainer c: ss.getSequenceContainers()) {
            c.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + c.getName());
        }

        for(MetaCommand c: ss.getMetaCommands()) {
            c.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + c.getName());
        }

        for(Algorithm a: ss.getAlgorithms()) {
            a.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + a.getName());
        }

        for(NonStandardData<?> nonStandardData: ss.getNonStandardData()) {
            nonStandardData.setSpaceSystemQualifiedName(ss.getQualifiedName());
        }

        for(SpaceSystem ss1:ss.getSubSystems()) {
            setQualifiedNames(ss1, ss.getQualifiedName());
        }
    }

    private static XtceDb loadSerializedInstance(String filename) throws IOException, ClassNotFoundException {
        ObjectInputStream in = null;
        log.debug("Loading serialized XTCE DB from: " + filename);
        in = new ObjectInputStream(new FileInputStream(filename));
        XtceDb db = (XtceDb) in.readObject();
        in.close();
        log.info("Loaded XTCE DB from {} with {} containers, {} parameters and {} commands",
                filename, db.getSequenceContainers().size(), db.getParameterNames().size(), db.getMetaCommands().size());
        return db;
    }

    private static String getFullName(String filename) throws ConfigurationException {
        YConfiguration c = YConfiguration.getConfiguration("mdb");
        return new File(c.getGlobalProperty("cacheDirectory"), filename).getAbsolutePath();
    }

    private static void saveSerializedInstance(XtceDb db, String filename) throws IOException, ConfigurationException {
        OutputStream os = null;
        ObjectOutputStream out = null;

        os = new FileOutputStream(getFullName(filename) + ".serialized");

        out = new ObjectOutputStream(os);
        out.writeObject(db);
        out.close();
        FileWriter fw = new FileWriter(getFullName(filename) + ".consistency_date");
        loaderTree.writeConsistencyDate(fw);
        fw.close();
    }

    /**
     * retrieves the XtceDb for the corresponding yamcsInstance.
     * if yamcsInstance is null, then the first one in the mdb.yaml config file is loaded
     * @param yamcsInstance
     * @return
     * @throws ConfigurationException
     * @throws DatabaseLoadException 
     */
    public static synchronized XtceDb getInstance(String yamcsInstance) throws ConfigurationException {
        XtceDb db = instance2Db.get(yamcsInstance);
        if(db==null) {
            YConfiguration c=YConfiguration.getConfiguration("yamcs."+yamcsInstance);
            db=getInstanceByConfig(yamcsInstance, c.getString("mdb"));
            instance2Db.put(yamcsInstance, db);
        }
        return db;
    }

    public static synchronized XtceDb getInstanceByConfig(String yamcsInstance, String config) throws ConfigurationException {
        Map<String, XtceDb> dbConfigs = instance2DbConfigs.get(yamcsInstance);
        if (dbConfigs == null) {
            dbConfigs = new HashMap<>();
            instance2DbConfigs.put(yamcsInstance, dbConfigs);
        }

        XtceDb db = dbConfigs.get(config);
        if(db==null) {
            db = createInstance(config);
            dbConfigs.put(config, db);
        }
        return db;
    }

    /**
     * forgets any singleton
     */
    public synchronized static void reset() {
        instance2Db.clear();
        instance2DbConfigs.clear();
    }

    public static void main(String argv[]) throws Exception {
        if(argv.length!=1) {
            System.out.println("Usage: print-mdb config-name");
            System.exit(1);
        }
        YConfiguration.setup();
        XtceDb xtcedb = createInstance(argv[0]);
        xtcedb.print(System.out);
    }


    static class LoaderTree {
        SpaceSystemLoader root;
        List<LoaderTree> children;  

        LoaderTree(SpaceSystemLoader root) {
            this.root=root;
        }


        void addChild(LoaderTree c) {
            if(children==null) children=new ArrayList<LoaderTree>();
            children.add(c);
        }

        /**
         * 
         * @return a concatenation of all configs
         * @throws ConfigurationException 
         */
        String getConfigName() throws ConfigurationException {
            if(children==null) {
                return root.getConfigName();
            } else {
                StringBuilder sb=new StringBuilder();
                sb.append(root.getConfigName());
                for(LoaderTree c:children) {
                    sb.append("_").append(c.getConfigName());
                }
                return sb.toString();
            }
        }

        /**checks the date in the file and returns true if any of the root or children needs to be updated
         * @throws ConfigurationException 
         * @throws IOException */
        public boolean neesUpdate(RandomAccessFile raf) throws IOException, ConfigurationException {
            raf.seek(0);
            if(root.needsUpdate(raf)) {
                return true;
            }
            if(children!=null) {
                for(LoaderTree lt:children) {
                    if(lt.neesUpdate(raf)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public SpaceSystem load() throws ConfigurationException {
            try {
                SpaceSystem rss=root.load();
                if(children!=null) {
                    for(LoaderTree lt:children) {
                        SpaceSystem ss=lt.load();
                        rss.addSpaceSystem(ss);
                        ss.setParent(rss);
                    }
                }
                return rss;
            } catch (ConfigurationException e) {
                throw e;
            }
        }


        public void writeConsistencyDate(FileWriter fw) throws IOException {
            root.writeConsistencyDate(fw);
            if(children!=null) {
                for(LoaderTree lt:children) {
                    lt.writeConsistencyDate(fw);
                }
            }
        }
    }

    //fake loader for the root (empty) space system
    static class RootSpaceSystemLoader implements SpaceSystemLoader {
        @Override
        public boolean needsUpdate(RandomAccessFile consistencyDateFile)   throws IOException, ConfigurationException {
            return false;
        }

        @Override
        public String getConfigName() throws ConfigurationException {
            return "";
        }

        @Override
        public void writeConsistencyDate(FileWriter consistencyDateFile)  throws IOException {
        }

        @Override
        public SpaceSystem load() throws ConfigurationException,  DatabaseLoadException {
            SpaceSystem rootSs = new SpaceSystem("");
            rootSs.setParent(rootSs);
            return rootSs;
        }

    }    
}
