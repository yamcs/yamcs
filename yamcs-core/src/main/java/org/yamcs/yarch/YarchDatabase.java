package org.yamcs.yarch;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;

/**
 * Handles all tables/streams/indexes for a Yamcs server
 * 
 */
public class YarchDatabase {
    YarchDatabase instance;
    // note that this home variable is currently changed in the
    // org.yamcs.cli.CheckConfig
    // to avoid errors when running check config in parallel with a running
    // yamcs server
    private static String home;
    static YConfiguration config;

    private static Map<String, StorageEngine> storageEngines = new LinkedHashMap<>();
    public static final String RDB_ENGINE_NAME = "rocksdb2";
    private static final String DEFAULT_STORAGE_ENGINE = RDB_ENGINE_NAME;
    private static final String defaultStorageEngineName;

    static {
        config = YConfiguration.getConfiguration("yamcs");
        if (config.containsKey("dataDir")) {
            Path dataDir = Path.of(config.getString("dataDir")).toAbsolutePath().normalize();
            home = dataDir.toString();
        }

        if (config.containsKey("storageEngines")) {
            List<Object> engineConfs = config.getList("storageEngines");
            for (int i = 0; i < engineConfs.size(); i++) {
                Object o = engineConfs.get(i);
                if (o instanceof String) {
                    String name = (String) o;
                    if (!RDB_ENGINE_NAME.equalsIgnoreCase(name)) {
                        throw new ConfigurationException("Unknown storage engine '" + name
                                + "'. Engines other than " + RDB_ENGINE_NAME
                                + " have to be configured with a name and a class");
                    }
                    addStorageEngine(RDB_ENGINE_NAME, RdbStorageEngine.getInstance());
                } else {
                    loadStorageEngine(config.getConfigListIdx("storageEngines", i));
                }
            }
        } else {
            addStorageEngine(RDB_ENGINE_NAME, RdbStorageEngine.getInstance());
        }

        if (config.containsKey("defaultStorageEngine")) {
            defaultStorageEngineName = config.getString("defaultStorageEngine");
            if (!storageEngines.containsKey(defaultStorageEngineName)) {
                throw new ConfigurationException("Unknown default storage engine '" + defaultStorageEngineName
                        + "'. Configured engines: " + storageEngines.keySet());
            }
        } else {
            defaultStorageEngineName = DEFAULT_STORAGE_ENGINE;
        }

        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            mbeanServer.registerMBean(new BackupControl(), new ObjectName("org.yamcs:name=Backup"));
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException
                | MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }
    static Map<String, YarchDatabaseInstance> databases = new HashMap<>();

    private static void loadStorageEngine(YConfiguration engineConf) {
        String name = engineConf.getString("name");
        String className = engineConf.getString("class");
        StorageEngine engine = YObjectLoader.loadObject(className);
        YConfiguration args = engineConf.getConfigOrEmpty("args");
        Spec spec = engine.getSpec();
        if (spec != null) {
            try {
                args = spec.validate(args);
            } catch (ValidationException e) {
                throw new ConfigurationException("Invalid configuration for the storage engine '" + name + "': "
                        + e.getMessage(), e);
            }
        }
        engine.init(name, args);
        addStorageEngine(name, engine);
    }

    /**
     * Registers a storage engine.
     * <p>
     * The engines are normally configured in the {@code storageEngines} section of yamcs.yaml; this method can be
     * used to register an engine programmatically (e.g. in tests). The registration has to be done before the
     * instances using the engine are loaded because the tables are loaded at instance creation.
     * 
     * @throws IllegalArgumentException
     *             if an engine with the same name is already registered
     */
    public static synchronized void addStorageEngine(String name, StorageEngine engine) {
        if (storageEngines.containsKey(name)) {
            throw new IllegalArgumentException("A storage engine named '" + name + "' is already registered");
        }
        storageEngines.put(name, engine);
    }

    /**
     * 
     * @param yamcsInstance
     * 
     */
    public static synchronized YarchDatabaseInstance getInstance(String yamcsInstance) {
        if (yamcsInstance == null) {
            throw new NullPointerException("yamcsInstance cannot be null");
        }

        YarchDatabaseInstance instance = databases.get(yamcsInstance);
        if (instance == null) {
            try {
                instance = new YarchDatabaseInstance(yamcsInstance);
            } catch (YarchException e) {
                throw new RuntimeException("Cannot create database '" + yamcsInstance + "'", e);
            }
            databases.put(yamcsInstance, instance);
        }
        return instance;
    }

    /**
     * Returns the names of the loaded databases.
     */
    public static Set<String> getDatabases() {
        return databases.keySet();
    }

    static public boolean hasInstance(String dbname) {
        return databases.containsKey(dbname);
    }

    public static boolean instanceExistsOnDisk(String yamcsInstance) {
        File dir = new File(getHome(), yamcsInstance);
        return dir.exists() && dir.isDirectory();
    }

    /**
     * to be used for testing
     * 
     * @param dbName
     *            database name to be removed
     **/
    public static void removeInstance(String dbName) {
        YarchDatabaseInstance ydb = databases.remove(dbName);
        if (ydb != null) {
            ydb.close();
        }
    }

    public static void setHome(String home) {
        YarchDatabase.home = home;
    }

    public static String getHome() {
        return home;
    }

    public static String getDataDir() {
        return home;
    }

    /**
     * Returns the default storage engine, used for the tables created without an explicit engine (unless the
     * instance configures another one) and providing the bucket and protobuf databases and the sequences.
     */
    public static StorageEngine getDefaultStorageEngine() {
        return storageEngines.get(defaultStorageEngineName);
    }

    public static StorageEngine getStorageEngine(String storageEngineName) {
        return storageEngines.get(storageEngineName);
    }

    public static Collection<StorageEngine> getStorageEngines() {
        return storageEngines.values();
    }

    public static Set<String> getStorageEngineNames() {
        return storageEngines.keySet();
    }

    public static String getDefaultStorageEngineName() {
        return defaultStorageEngineName;
    }

    /**
     * Shuts down all the storage engines. Called when Yamcs is stopped, after all the instances have been closed.
     */
    public static synchronized void shutdownStorageEngines() {
        for (StorageEngine engine : storageEngines.values()) {
            engine.shutdown();
        }
    }
}
