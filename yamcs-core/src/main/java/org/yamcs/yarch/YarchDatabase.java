package org.yamcs.yarch;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
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

    private static Map<String, StorageEngine> storageEngines = new HashMap<>();
    public static final String OLD_RDB_ENGINE_OLD_NAME = "rocksdb";
    public static final String OLD_RDB_ENGINE_NAME = "oldrocksdb";
    public static final String RDB_ENGINE_NAME = "rocksdb2";
    private static final String DEFAULT_STORAGE_ENGINE = RDB_ENGINE_NAME;
    private static final String defaultStorageEngineName;

    static {
        config = YConfiguration.getConfiguration("yamcs");
        if(config.containsKey("dataDir")) {
            home = config.getString("dataDir");
        }

        List<String> se;
        if (config.containsKey("storageEngines")) {
            se = config.getList("storageEngines");
        } else {
            se = Arrays.asList(RDB_ENGINE_NAME, OLD_RDB_ENGINE_OLD_NAME);
        }
        if (config.containsKey("defaultStorageEngine")) {
            defaultStorageEngineName = config.getString("defaultStorageEngine");
            if (!RDB_ENGINE_NAME.equalsIgnoreCase(defaultStorageEngineName)) {
                throw new ConfigurationException("Unknown storage engine: " + defaultStorageEngineName);
            }
        } else {
            defaultStorageEngineName = DEFAULT_STORAGE_ENGINE;
        }

        if (se != null) {
            for (String s : se) {
                if (RDB_ENGINE_NAME.equalsIgnoreCase(s)) {
                    storageEngines.put(RDB_ENGINE_NAME, RdbStorageEngine.getInstance());
                } else if (OLD_RDB_ENGINE_OLD_NAME.equalsIgnoreCase(s) || OLD_RDB_ENGINE_NAME.equalsIgnoreCase(s)) {
                    storageEngines.put(OLD_RDB_ENGINE_OLD_NAME,
                            org.yamcs.yarch.oldrocksdb.RdbStorageEngine.getInstance());
                    storageEngines.put(OLD_RDB_ENGINE_NAME, org.yamcs.yarch.oldrocksdb.RdbStorageEngine.getInstance());
                } else {
                    throw new ConfigurationException("Unknown storage engine '" + se + "'");
                }
            }
        }

    }
    static Map<String, YarchDatabaseInstance> databases = new HashMap<>();
    
    /**
     * 
     * @param yamcsInstance
     * 
     */
    public static synchronized YarchDatabaseInstance getInstance(String yamcsInstance) {
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

    static public boolean hasInstance(String dbname) {
        return databases.containsKey(dbname);
    }

    public static boolean instanceExistsOnDisk(String yamcsInstance) {
        File dir = new File(getHome() + "/" + yamcsInstance);
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

    public static StorageEngine getDefaultStorageEngine() {
        return storageEngines.get(defaultStorageEngineName);
    }

    public static StorageEngine getStorageEngine(String storageEngineName) {
        return storageEngines.get(storageEngineName);
    }

    public static Set<String> getStorageEngineNamesk() {
        return storageEngines.keySet();
    }

    public static String getDefaultStorageEngineName() {
        return defaultStorageEngineName;
    }
}
