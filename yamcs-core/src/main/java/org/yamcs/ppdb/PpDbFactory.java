package org.yamcs.ppdb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.xtce.PpDatabaseLoader;

public class PpDbFactory {
    static Logger log = LoggerFactory.getLogger(PpDbFactory.class.getName());
    
    /**
     * Loaders which load particular databases Contains only active loaders
     * (those which are configured to load data)
     */
    static List<PpDatabaseLoader> loaders;
    
    // Singleton instance for each config, also keep a mapping from the reference
    static transient Map<String,PpDefDb> config2Db = new HashMap<String,PpDefDb>();
    // maping from the yamcsInstance to the same dbs for fast access 
    static transient Map<String,PpDefDb> instance2Db = new HashMap<String,PpDefDb>();
    
    
    /**
     * Creates a new instance of the database in memory.
     * 
     * @throws ConfigurationException
     */
    @SuppressWarnings("unchecked")
    private static synchronized PpDefDb createInstance(String configSection) throws ConfigurationException {
        YConfiguration c = YConfiguration.getConfiguration("mdb");
        PpDefDb db=null;
        //
        // create MDB loaders according to configuration
        // settings
        //

        List<Map<String, Object>> loadersConfig = c.getList(configSection, "ppLoaders");
        loaders = new ArrayList<PpDatabaseLoader>();
        
        for (Map<String, Object> m : loadersConfig) {
            String type=c.getString(m, "type");
            String spec=c.getString(m, "spec");
            if (type.equals("flatfile")) {
                loaders.add(new FlatFilePpDbLoader(spec));
            } else if (type.equals("sheet")) {
            	loaders.add( new SpreadsheetPpDbLoader( spec ) );
            } else {
                // custom class
                try {
                    Class<PpDatabaseLoader> cl = (Class<PpDatabaseLoader>) Class.forName(type);
                    Constructor<PpDatabaseLoader> constr= cl.getConstructor(String.class);
                    loaders.add(constr.newInstance(spec));
                } catch (ClassNotFoundException e) {
                    log.warn("Could not find appropriate loader class", e);
                    throw new ConfigurationException("Invalid database loader class: " + type);
                } catch (Exception e1) {
                    log.warn("Cannot instantiate class " + type, e1);
                    throw new ConfigurationException("Cannot instantiate class " + type, e1);
                }
            }
        }


        //
        // check MDB/spreadsheet consistency dates
        //

        boolean serializedLoaded = false;
        boolean loadSerialized = true;
        StringBuilder filename = new StringBuilder();
        try {
            for (PpDatabaseLoader l : loaders) {
                filename.append(l.getConfigName() + ".");
            }
        } catch (ConfigurationException e) {
            log.error("Cannot load the database configuration: ", e);
            System.exit(-1);
        }
        filename.append("ppdb");
        try {
            RandomAccessFile raf = new RandomAccessFile(getFullName(filename.toString()) + ".consistency_date", "r");
            for (PpDatabaseLoader l : loaders) {
                if (l.needsUpdate(raf)) {
                    loadSerialized = false;
                    break;
                }
                raf.seek(0);
            }
        } catch (IOException e) {
            log.info("Could not read date for when the database was last serialized so database will be re-loaded.", e);
            loadSerialized = false;
        } catch (ConfigurationException e) {
            log.error("Cannot check the consistency date of the serialized database: ", e);
            System.exit(-1);
        }

        if (loadSerialized) {
            try {
               db=loadSerializedInstance(getFullName(filename.toString()) + ".serialized");
                serializedLoaded = true;
            } catch (Exception e) {
                log.info("Found a date for when the database was last serialized, but could not load the serialized database: ", e);
            }
        }

        if (db == null) {
            try {
                db = new PpDefDb();
                for (PpDatabaseLoader l : loaders) {
                    l.loadDatabase(db);
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Cannot load the database: ", e);
                System.exit(-1);// if we can not read the database we are out of
                                // the game
            }
        }
        // log.info("Loaded database with "+instance.sid2TcPacketMap.size()+" TC, "+instance.sid2SequenceContainertMap.size()+" TM containers, "+instance.sid2ParameterMap.size()+" TM parameters and "+instance.upcOpsname2PpMap.size()+" processed parameters");

        if ((!serializedLoaded)) {
            try {
                saveSerializedInstance(db, filename.toString());
                log.info("Serialized database saved locally");
            } catch (Exception e) {
                log.warn("Cannot save serialized MDB: ", e);
                e.printStackTrace();
            }
        }
        return db;
    }

    private static PpDefDb loadSerializedInstance(String filename) throws IOException, ClassNotFoundException {
        ObjectInputStream in = null;
        log.info("Attempting to load serialized pp database from file: " + filename);
        in = new ObjectInputStream(new FileInputStream(filename));
        PpDefDb db = (PpDefDb) in.readObject();
        in.close();
        log.info("Loaded ppdb database with " + db.size() +"PPs");
        return db;
    }

    private static String getFullName(String filename) throws ConfigurationException {
        YConfiguration c = YConfiguration.getConfiguration("mdb");
        return c.getGlobalProperty("cacheDirectory") + File.separator + filename;
    }

    private static void saveSerializedInstance(PpDefDb db, String filename) throws IOException, ConfigurationException {
        OutputStream os = null;
        ObjectOutputStream out = null;

        os = new FileOutputStream(getFullName(filename) + ".serialized");

        out = new ObjectOutputStream(os);
        out.writeObject(db);
        out.close();
        FileWriter fw = new FileWriter(getFullName(filename) + ".consistency_date");

        for (PpDatabaseLoader l : loaders) {
            l.writeConsistencyDate(fw);
        }
        fw.close();
    }

    static public synchronized PpDefDb getInstanceByConfig(String config) throws ConfigurationException {
        PpDefDb db=config2Db.get(config);
        if(db==null) {
            db=createInstance(config);
            config2Db.put(config, db);
        }
        return db;
    }
    
    static public synchronized PpDefDb getInstance(String yamcsInstance) throws ConfigurationException {
        PpDefDb db=instance2Db.get(yamcsInstance);
        if(db==null) {
            YConfiguration c=YConfiguration.getConfiguration("yamcs."+yamcsInstance);
            db=getInstanceByConfig(c.getString("mdb"));
            instance2Db.put(yamcsInstance, db);
        }
        return db;
    }
    
    public static void main(String argv[]) throws Exception {
        if(argv.length!=1) {
            System.out.println("Usage: print-mdb config-name (config-name is a section from mdb.yaml)");
            System.exit(1);
        }
        YConfiguration.setup();
        PpDbFactory.getInstanceByConfig(argv[0]).print(System.out);
    }
}
