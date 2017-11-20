package org.yamcs.yarch;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.YConfiguration;

/**
 * Handles all tables/streams/indexes for a Yamcs server
 * 
 */
public class YarchDatabase {
    YarchDatabase instance;
    //note that this home variable is currently changed in the org.yamcs.cli.CheckConfig
    // to avoid errors when running check config in parallel with a running yamcs server
    private static String home;
    static YConfiguration config;
    
    static {
        config = YConfiguration.getConfiguration("yamcs");
        home = config.getString("dataDir");
    } 
    static Map<String,YarchDatabaseInstance> databases = new HashMap<>();
    /**
     * 
     * @param yamcsInstance
     * @param ignoreVersionIncompatibility - if set to true, the created StorageEngines will load old data (as far as possible). Used only when upgrading from old data formats to new ones.
     * 
     */
    public static synchronized YarchDatabaseInstance getInstance(String yamcsInstance, boolean ignoreVersionIncompatibility) {
        YarchDatabaseInstance instance = databases.get(yamcsInstance);
        if(instance==null) {
            try {
                instance = new YarchDatabaseInstance(yamcsInstance, ignoreVersionIncompatibility);
            } catch (YarchException e) {
                throw new RuntimeException("Cannot create database '"+yamcsInstance+"'", e);
            }
            databases.put(yamcsInstance, instance);
        }
        return instance;
    }
    static public YarchDatabaseInstance getInstance(String yamcsInstance) {
        return getInstance(yamcsInstance, false);
    }
   

    static public boolean hasInstance(String dbname) {
        return databases.containsKey(dbname);
    }

    public static boolean instanceExistsOnDisk(String yamcsInstance) {
        File dir=new File(getHome()+"/"+yamcsInstance);
        return dir.exists() && dir.isDirectory();
    }
    

    /**to be used for testing
     * @param dbName database name to be removed 
     **/
    public static void removeInstance(String dbName) {
        databases.remove(dbName);
    }
    
    public static void setHome(String home) {
        YarchDatabase.home = home;
    }

    public static String getHome() {
        return home;
    }
}
