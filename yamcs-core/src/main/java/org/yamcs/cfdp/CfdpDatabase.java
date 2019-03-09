package org.yamcs.cfdp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.yamcs.yarch.YarchException;

/**
 * Handles all tables/streams/indexes for a Yamcs server
 * 
 */
public class CfdpDatabase {
    static Map<String, CfdpDatabaseInstance> databases = new HashMap<>();

    // TODO, how to uniquely identify entities
    public final static int mySourceId = 1;

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
