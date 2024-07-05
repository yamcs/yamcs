package org.yamcs.mdb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.NonStandardData;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SystemParameter;
import org.yamcs.xtce.util.ArgumentReference;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.ReferenceFinder;
import org.yamcs.xtce.util.ReferenceFinder.FoundReference;

import static org.yamcs.xtce.NameDescription.PATH_SEPARATOR;

public class MdbFactory {

    private static Path cacheDirectory; // This is used in client tools to overwrite

    static Log log = new Log(MdbFactory.class);

    /**
     * map instance names and config names to databases
     */
    static transient Map<String, Mdb> instance2Db = new HashMap<>();
    static transient Map<String, Map<String, Mdb>> instance2DbConfigs = new HashMap<>();

    /**
     * Creates a new instance of the database in memory. configSection is the top heading under which this appears in
     * the mdb.yaml
     *
     * @throws DatabaseLoadException
     */
    public static synchronized Mdb createInstanceByConfig(String configSection) throws DatabaseLoadException {
        return createInstanceByConfig(configSection, true);
    }

    public static synchronized Mdb createInstanceByConfig(String configSection, boolean attemptToLoadSerialized)
            throws ConfigurationException, DatabaseLoadException {
        YConfiguration c = YConfiguration.getConfiguration("mdb");

        if (configSection == null) {
            configSection = c.getFirstEntry();
        }
        List<YConfiguration> list = c.getConfigList(configSection);
        return createInstance(list, attemptToLoadSerialized, true);
    }

    /**
     * Load a XTCE database from a description.
     * 
     * 
     * @param treeConfig
     *            this should be a list of maps as it would come out of the mdb.yaml definition.
     * @param attemptToLoadSerialized
     *            if true, it will attempt to load a serialized version from the disk instead of creating a new object
     *            by loading all elements from the tree definition.
     * @param saveSerialized
     *            if the result should be saved as a serialized file. If the database has been loaded from a serialized
     *            file, this option will have no effect.
     * @return a newly created XTCE database object.
     * @throws ConfigurationException
     */
    public static synchronized Mdb createInstance(List<YConfiguration> treeConfig, boolean attemptToLoadSerialized,
            boolean saveSerialized) throws ConfigurationException, DatabaseLoadException {
        LoaderTree loaderTree = new LoaderTree(new RootSpaceSystemLoader());

        for (YConfiguration o : treeConfig) {
            loaderTree.addChild(getLoaderTree(o));
        }

        boolean loadSerialized = attemptToLoadSerialized;
        boolean serializedLoaded = false;
        String filename = sha1(loaderTree.getConfigName() + ".xtce");
        File serializedFile = resolveSerializedFile(filename);
        File consistencyFile = resolveConsistencyFile(filename);

        if (loadSerialized) {
            if (serializedFile.exists()) {
                try (RandomAccessFile raf = new RandomAccessFile(consistencyFile, "r")) {
                    if (loaderTree.needsUpdate(raf)) {
                        loadSerialized = false;
                    }
                } catch (IOException e) {
                    if (serializedFile.exists()) {
                        log.warn("can't check the consistency date of the serialized database", e);
                    }
                    loadSerialized = false;
                }
            } else {
                loadSerialized = false;
            }
        }
        Mdb mdb = null;
        if (loadSerialized) {
            try {
                mdb = loadSerializedInstance(serializedFile);
                serializedLoaded = true;
            } catch (InvalidClassException e) {
                log.debug("Cannot load serialized database: " + e.getMessage());
                mdb = null;
            } catch (Exception e) {
                log.warn("Cannot load serialized database", e);
                mdb = null;
            }
        }

        if (mdb == null) {
            // Construct a Space System with one branch from the config file and the other one /yamcs for system
            // variables
            LoadResult lr = loaderTree.load();

            if (lr.ssList.size() != 1) {
                throw new IllegalStateException("root loader has to load exactly one subsystem");
            }
            SpaceSystem rootSs = lr.ssList.get(0);
            SpaceSystem yamcsSs = new SpaceSystem(Mdb.YAMCS_SPACESYSTEM_NAME.substring(1));
            yamcsSs.setQualifiedName(Mdb.YAMCS_SPACESYSTEM_NAME);

            rootSs.addSpaceSystem(yamcsSs);
            ReferenceFinder refFinder = new ReferenceFinder(s -> log.warn(s));
            int n;
            while ((n = resolveReferences(rootSs, rootSs, refFinder)) > 0) {
            }

            if (n == 0) {
                StringBuilder sb = new StringBuilder();
                collectUnresolvedReferences(rootSs, sb);
                throw new DatabaseLoadException("Cannot resolve (circular?) references: " + sb.toString());
            }
            setQualifiedNames(rootSs, "");

            mdb = new Mdb(rootSs, lr.writers);

            addTmPartitions(rootSs);

            // set the root sequence container as the first root sequence container found in the sub-systems.
            for (SpaceSystem ss : rootSs.getSubSystems()) {
                SequenceContainer seqc = ss.getRootSequenceContainer();
                if (seqc != null) {
                    mdb.setRootSequenceContainer(seqc);
                    break;
                }
            }

            mdb.buildIndexMaps();
        }

        if (saveSerialized && (!serializedLoaded)) {
            try {
                saveSerializedInstance(loaderTree, mdb, serializedFile, consistencyFile);
                log.info("Serialized database saved locally");
            } catch (Exception e) {
                log.warn("Cannot save serialized MDB", e);
            }
        }

        return mdb;
    }

    /* collects a description for all unresolved references into the StringBuffer to raise an error */
    private static void collectUnresolvedReferences(SpaceSystem ss, StringBuilder sb) {
        List<NameReference> refs = ss.getUnresolvedReferences();
        if (refs != null) {
            for (NameReference nr : ss.getUnresolvedReferences()) {
                sb.append("system: ").append(ss.getName()).append(" ").append(nr.toString()).append("\n");
            }
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
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
    private static int resolveReferences(SpaceSystem rootSs, SpaceSystem ss, ReferenceFinder refFinder)
            throws DatabaseLoadException {
        List<NameReference> refs = ss.getUnresolvedReferences();

        if (refs == null) { // this can happen if the spacesystem has been unserialized since the reference name is
                            // transient
            // do not return here, we need to check the subsystems below
            refs = Collections.emptyList();
        }

        int n = refs.isEmpty() ? -1 : 0;

        Iterator<NameReference> it = refs.iterator();
        while (it.hasNext()) {
            NameReference nr = it.next();
            boolean resolved;
            if (nr.getType() == Type.ARGUMENT) {
                resolved = resolveArgumentReference((ArgumentReference) nr);
            } else {
                FoundReference foundReference = refFinder.findReference(rootSs, nr, ss);
                if (foundReference == null && nr.getType() == Type.PARAMETER
                        && nr.getReference().startsWith(Mdb.YAMCS_SPACESYSTEM_NAME)) {
                    // Special case for system parameters: they are created on the fly
                    SystemParameter sp = createSystemParameter(rootSs, nr);
                    foundReference = new FoundReference(sp);
                }
                if (foundReference == null) { // look for aliases up the hierarchy
                    foundReference = refFinder.findAliasReference(rootSs, nr, ss);
                }

                if (foundReference != null && foundReference.isComplete()) {
                    foundReference.resolved(nr);
                    resolved = true;
                } else {
                    resolved = false;
                }
            }
            if (resolved) {
                n++;
                it.remove();
            }
        }
        for (SpaceSystem ss1 : ss.getSubSystems()) {
            int m = resolveReferences(rootSs, ss1, refFinder);
            if (n == -1) {
                n = m;
            } else if (m > 0) {
                n += m;
            }
        }
        return n;
    }

    // argument references are references used in verifiers or transmission constraints.
    // they refer to arguments that are from a parent command which was not loaded (defined in a different file) at the
    // time when the command was created.
    private static boolean resolveArgumentReference(ArgumentReference nr) {
        Argument arg = null;
        MetaCommand cmd = nr.getMetaCommand();
        while (arg == null && cmd != null) {
            arg = cmd.getArgument(nr.getArgName());
            cmd = cmd.getBaseMetaCommand();
        }
        if (arg == null || arg.getArgumentType() == null) {
            return false;
        }

        if (nr.getPath() != null) {
            if (!ReferenceFinder.verifyPath(arg.getArgumentType(), nr.getPath())) {
                throw new DatabaseLoadException("Invalid aggregate member '" + AggregateUtil.toString(nr.getPath())
                        + " for argument '" + arg.getName());
            }
        }
        nr.resolved(arg, nr.getPath());
        return true;
    }

    static private void addTmPartitions(SpaceSystem spaceSystem) {
        Set<SequenceContainer> scset = new HashSet<>();
        collectAutoPartition(spaceSystem, scset);

        for (SequenceContainer sc : scset) {
            sc.useAsArchivePartition(true);
        }
    }

    static void collectAutoPartition(SpaceSystem spaceSystem, Set<SequenceContainer> scset) {
        for (SequenceContainer sc : spaceSystem.getSequenceContainers()) {
            if (!sc.isAutoPartition()) {
                continue;
            }
            if (sc.getBaseContainer() == null) {
                // do not set the flag on root containers because:
                // 1. they will be used anyway as archive partitions if no child matches
                // 2. if they appear as container entries, we do not want to use them
                continue;
            }

            boolean part = true;
            SequenceContainer sc1 = sc;
            while (sc1 != null) {
                if (sc1.useAsArchivePartition()) {
                    part = false;
                    break;
                }
                sc1 = sc1.getBaseContainer();
            }
            if (part) {
                scset.add(sc);
            }
        }

        for (SpaceSystem ss : spaceSystem.getSubSystems()) {
            collectAutoPartition(ss, scset);
        }

    }

    static SystemParameter createSystemParameter(SpaceSystem rootSs, NameReference nr) {
        String fqname = nr.getReference();
        SystemParameter sp = SystemParameter.getForFullyQualifiedName(fqname);

        String ssname = sp.getSubsystemName();
        String[] a = ssname.split("/");
        SpaceSystem ss1 = rootSs;
        for (String name : a) {
            if (name.isEmpty()) {
                continue;
            }
            SpaceSystem ss2 = ss1.getSubsystem(name);
            if (ss2 == null) {
                ss2 = new SpaceSystem(name);
                ss1.addSpaceSystem(ss2);
            }
            ss1 = ss2;
        }
        ss1.addParameter(sp);

        return sp;
    }

    private static LoaderTree getLoaderTree(YConfiguration c)
            throws ConfigurationException, DatabaseLoadException {
        String type = c.getString("type");
        Object args = null;
        if (c.containsKey("args")) {
            args = c.getConfig("args");
        } else if (c.containsKey("spec")) {
            args = c.get("spec");
        }

        SpaceSystemLoader l;
        LoaderTree ltree;

        if ("xtce".equals(type)) {
            type = XtceLoader.class.getName();
        } else if ("sheet".equals(type)) {
            type = SpreadsheetLoader.class.getName();
        } else if ("emptyNode".equalsIgnoreCase(type)) {
            type = EmptyNodeLoader.class.getName();
        }
        try {
            if (args == null) {
                l = YObjectLoader.loadObject(type);
            } else {
                l = YObjectLoader.loadObject(type, args);
            }
        } catch (DatabaseLoadException | ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            log.warn(e.toString());
            throw new DatabaseLoadException("Cannot load xtce database: " + e.getMessage(), e);
        }

        ltree = new LoaderTree(l);
        var writable = c.getBoolean("writable", false);
        if (writable && !l.isWritable()) {
            throw new ConfigurationException("The " + type + " MDB does not support writing");
        }
        ltree.writable = writable;

        if (c.containsKey("subLoaders")) {
            List<YConfiguration> list = c.getConfigList("subLoaders");
            for (YConfiguration c1 : list) {
                ltree.addChild(getLoaderTree(c1));
            }
        }

        return ltree;
    }

    /**
     * Propagates qualified name to enclosing objects including subsystems. Also registers aliases under each subsystem.
     */
    private static void setQualifiedNames(SpaceSystem ss, String parentqname) {
        String ssqname;
        if (String.valueOf(NameDescription.PATH_SEPARATOR).equals(parentqname)) { // parent is root
            ssqname = NameDescription.PATH_SEPARATOR + ss.getName();
        } else {
            ssqname = parentqname + NameDescription.PATH_SEPARATOR + ss.getName();
        }

        ss.setQualifiedName(ssqname);

        for (Parameter p : ss.getParameters()) {
            p.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + p.getName());
        }
        for (ParameterType pt : ss.getParameterTypes()) {
            NameDescription nd = (NameDescription) pt;
            nd.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + nd.getName());
        }

        for (ArgumentType at : ss.getArgumentTypes()) {
            NameDescription nd = (NameDescription) at;
            nd.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + nd.getName());
        }

        for (SequenceContainer c : ss.getSequenceContainers()) {
            c.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + c.getName());
        }

        for (MetaCommand c : ss.getMetaCommands()) {
            c.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + c.getName());
            for (CommandVerifier cv : c.getCommandVerifiers()) {
                Algorithm a = cv.getAlgorithm();
                if (a != null) {
                    a.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + a.getName());
                }
            }
        }

        for (Algorithm a : ss.getAlgorithms()) {
            a.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + a.getName());
        }

        for (CommandContainer cc : ss.getCommandContainers()) {
            cc.setQualifiedName(ss.getQualifiedName() + NameDescription.PATH_SEPARATOR + cc.getName());
        }

        for (NonStandardData<?> nonStandardData : ss.getNonStandardData()) {
            nonStandardData.setSpaceSystemQualifiedName(ss.getQualifiedName());
        }

        for (SpaceSystem ss1 : ss.getSubSystems()) {
            setQualifiedNames(ss1, ss.getQualifiedName());
        }
    }

    private static Mdb loadSerializedInstance(File serializedFile) throws IOException, ClassNotFoundException {
        log.debug("Loading serialized XTCE DB from: {}", serializedFile);

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(serializedFile))) {
            Mdb mdb = (Mdb) in.readObject();
            log.info("Loaded XTCE DB from {} with {} containers, {} parameters and {} commands",
                    serializedFile, mdb.getSequenceContainers().size(), mdb.getParameterNames().size(),
                    mdb.getMetaCommands().size());
            return mdb;
        }
    }

    private static File resolveSerializedFile(String filename) {
        Path cacheDir = cacheDirectory != null ? cacheDirectory : YamcsServer.getServer().getCacheDirectory();
        if (cacheDir == null) { // During unit tests
            cacheDir = Path.of("cache").toAbsolutePath();
        }
        return cacheDir.resolve(filename + ".serialized").toFile();
    }

    private static File resolveConsistencyFile(String filename) {
        Path cacheDir = cacheDirectory != null ? cacheDirectory : YamcsServer.getServer().getCacheDirectory();
        if (cacheDir == null) { // During unit tests
            cacheDir = Path.of("cache").toAbsolutePath();
        }
        return cacheDir.resolve(filename + ".consistency_date").toFile();
    }

    private static void saveSerializedInstance(LoaderTree loaderTree, Mdb mdb, File serializedFile,
            File consistencyFile) throws IOException {
        serializedFile.getParentFile().mkdirs();
        try (OutputStream os = new FileOutputStream(serializedFile);
                ObjectOutputStream out = new ObjectOutputStream(os);
                FileWriter fw = new FileWriter(consistencyFile)) {
            out.writeObject(mdb);
            loaderTree.writeConsistencyDate(fw);
        }
    }

    /**
     * retrieves the MDB for the corresponding yamcsInstance. if yamcsInstance is null, then the first one in the
     * mdb.yaml config file is loaded
     * 
     * @param yamcsInstance
     * @return
     * @throws ConfigurationException
     * @throws DatabaseLoadException
     */
    public static synchronized Mdb getInstance(String yamcsInstance) throws ConfigurationException {
        Mdb mdb = instance2Db.get(yamcsInstance);
        if (mdb == null) {
            YConfiguration instanceConfig = YConfiguration.getConfiguration("yamcs." + yamcsInstance);
            if (instanceConfig.containsKey("mdbSpec")) {
                mdb = getInstanceByConfig(yamcsInstance, instanceConfig.getString("mdbSpec"));
                instance2Db.put(yamcsInstance, mdb);
            } else if (instanceConfig.isList("mdb")) {
                mdb = createInstance(instanceConfig.getConfigList("mdb"), true, true);
                instance2Db.put(yamcsInstance, mdb);
            }
        }
        return mdb;
    }

    public static synchronized Mdb getInstanceByConfig(String yamcsInstance, String config) {
        Map<String, Mdb> dbConfigs = instance2DbConfigs.computeIfAbsent(yamcsInstance, k -> new HashMap<>());

        return dbConfigs.computeIfAbsent(config, k -> createInstanceByConfig(config));
    }

    /**
     * 
     * Removes the MDB corresponding to yamcsInstance from memory
     */
    public static synchronized void remove(String yamcsInstance) {
        log.info("Removing the MDB for instance {}", yamcsInstance);
        instance2DbConfigs.remove(yamcsInstance);
        instance2Db.remove(yamcsInstance);
    }

    /**
     * forgets any singleton
     */
    public synchronized static void reset() {
        instance2Db.clear();
        instance2DbConfigs.clear();
    }

    /**
     * Sets up this class (creation of cache directory).
     * <p>
     * This method is intended for client tools.
     */
    public static void setupTool(Path cacheDirectory) {
        MdbFactory.cacheDirectory = cacheDirectory;
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            log.error("Cannot create directory", e);
        }
    }

    private static String sha1(String input) throws ConfigurationException {
        try {
            MessageDigest msgDigest = MessageDigest.getInstance("SHA-1");
            msgDigest.update(input.getBytes("UTF-8"), 0, input.length());
            return StringConverter.arrayToHexString(msgDigest.digest());
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new ConfigurationException("Cannot compute SHA-1 of a string", e);
        }
    }

    static class LoaderTree {
        SpaceSystemLoader root;
        List<LoaderTree> children;
        SpaceSystemWriter writer = null;
        boolean writable = false;

        LoaderTree(SpaceSystemLoader root) {
            this.root = root;
        }

        void addChild(LoaderTree c) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(c);
        }

        /**
         *
         * @return a concatenation of all configs
         * @throws ConfigurationException
         */
        String getConfigName() throws ConfigurationException {
            if (children == null) {
                return root.getConfigName();
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(root.getConfigName());
                for (LoaderTree c : children) {
                    sb.append("_").append(c.getConfigName());
                }
                return sb.toString();
            }
        }

        /**
         * checks the date in the file and returns true if any of the root or children needs to be updated
         * 
         * @throws ConfigurationException
         * @throws IOException
         */
        public boolean needsUpdate(RandomAccessFile raf) throws IOException, ConfigurationException {
            raf.seek(0);
            if (root.needsUpdate(raf)) {
                return true;
            }
            if (children != null) {
                for (LoaderTree lt : children) {
                    if (lt.needsUpdate(raf)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public LoadResult load() throws ConfigurationException {
            LoadResult r = new LoadResult();
            r.ssList = root.loadList();

            if (writable) {
                var w = root.getWriter();
                for (var ss : r.ssList) {
                    r.writers.put(ss.getName(), w);
                }
            }

            if (children != null) {
                if (r.ssList.size() != 1) {
                    throw new ConfigurationException("Cannot load multiple space systems and have sub loaders");
                }
                SpaceSystem rss = r.ssList.get(0);

                for (LoaderTree lt : children) {
                    LoadResult rc = lt.load();
                    for (SpaceSystem ss : rc.ssList) {
                        rss.addSpaceSystem(ss);
                        ss.setParent(rss);
                    }
                    for (var entry : rc.writers.entrySet()) {
                        r.writers.put(rss.getName() + PATH_SEPARATOR + entry.getKey(), entry.getValue());
                    }

                }
            }
            return r;
        }

        public void writeConsistencyDate(FileWriter fw) throws IOException {
            root.writeConsistencyDate(fw);
            if (children != null) {
                for (LoaderTree lt : children) {
                    lt.writeConsistencyDate(fw);
                }
            }
        }
    }

    // fake loader for the root (empty) space system
    static class RootSpaceSystemLoader implements SpaceSystemLoader {
        @Override
        public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
            return false;
        }

        @Override
        public String getConfigName() throws ConfigurationException {
            return "";
        }

        @Override
        public void writeConsistencyDate(FileWriter consistencyDateFile) throws IOException {
            // no consistency needed since we are always "up2date"
        }

        @Override
        public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
            SpaceSystem rootSs = new SpaceSystem("");
            rootSs.setParent(rootSs);
            return rootSs;
        }
    }

    static class LoadResult {
        List<SpaceSystem> ssList;
        Map<String, SpaceSystemWriter> writers = new HashMap<>();
    }

}
