package org.yamcs.yarch;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handles all tables/streams/indexes for a Yamcs server
 * 
 */
public class CfdpDatabase {
    CfdpDatabase instance;

    static Map<String, CfdpDatabaseInstance> databases = new HashMap<>();

    /**
     * 
     * @param yamcsInstance
     * 
     */
    public static synchronized CfdpDatabaseInstance getInstance(String yamcsInstance) {
        CfdpDatabaseInstance instance = databases.get(yamcsInstance);
        if (instance == null) {
            try {
                instance = new CfdpDatabaseInstance(yamcsInstance);
            } catch (YarchException e) {
                throw new RuntimeException("Cannot create cfdp database '" + yamcsInstance + "'", e);
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

    static public boolean hasInstance(String yamcsInstance) {
        return databases.containsKey(yamcsInstance);
    }

    /**
     * to be used for testing
     * 
     * @param dbName
     *            database name to be removed
     **/
    public static void removeInstance(String dbName) {
        CfdpDatabaseInstance cdb = databases.remove(dbName);
    }

}
