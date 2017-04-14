package org.yamcs.yarch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.yarch.management.JMXService;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;
import org.yaml.snakeyaml.Yaml;



/**
 * Synchronization policy: to avoid problems with stream disappearing when clients connect to them, all
 * the creation/closing/subscription to streams/tables shall be done while acquiring a lock on the YarchDatabase
 * object. This is done in the StreamSqlStatement.java
 * 
 * Delivery of tuples does not require locking, this means subscription can change while delivering 
 *  (for that a concurrent list is used in Stream.java)
 * 
 * @author nm
 *
 */
public class YarchDatabase {

    Map<String,TableDefinition> tables;
    transient Map<String,AbstractStream> streams;
    static Logger log=LoggerFactory.getLogger(YarchDatabase.class.getName());
    static YConfiguration config;
    private static String home;

    private Map<String, StorageEngine> storageEngines=new HashMap<>();
    public static final String RDB_ENGINE_NAME="rocksdb";

    private static final String DEFAULT_STORAGE_ENGINE = RDB_ENGINE_NAME;
    private final String defaultStorageEngineName;

    static {
        config = YConfiguration.getConfiguration("yamcs");
        home = config.getString("dataDir");
    } 

    final JMXService jmxService;


    static Map<String,YarchDatabase> databases = new HashMap<>();
    private String dbname;

    private YarchDatabase(String dbname, boolean ignoreVersionIncompatibility) throws YarchException {
        this.dbname = dbname;
        jmxService = JMXService.getInstance();
        tables = new HashMap<String,TableDefinition>();
        streams = new HashMap<String,AbstractStream>();


        List<String> se;
        if(config.containsKey("storageEngines")) {
            se = config.getList("storageEngines");
        } else {
            se = Arrays.asList(RDB_ENGINE_NAME);
        }
        if(config.containsKey("defaultStorageEngine")) {
            defaultStorageEngineName = config.getString("defaultStorageEngine");
            if(!RDB_ENGINE_NAME.equalsIgnoreCase(defaultStorageEngineName))  {
                throw new ConfigurationException("Unknown storage engine: "+defaultStorageEngineName);
            }
        } else {
            defaultStorageEngineName = DEFAULT_STORAGE_ENGINE;
        }

        if(se!=null) {
            for(String s:se) {
                if(RDB_ENGINE_NAME.equalsIgnoreCase(s)) {
                    storageEngines.put(RDB_ENGINE_NAME, new RdbStorageEngine(this, ignoreVersionIncompatibility));
                }
            }
        }
        loadTables();
    }

    public static boolean instanceExistsOnDisk(String yamcsInstance) {
        File dir=new File(getHome()+"/"+yamcsInstance);
        return dir.exists() && dir.isDirectory();
    }
    
    static public YarchDatabase getInstance(String yamcsInstance) {
        return getInstance(yamcsInstance, false);
    }
    /**
     * 
     * @param yamcsInstance
     * @param ignoreVersionIncompatibility - if set to true, the created StorageEngines will load old data (as far as possible). Used only when upgrading from old data formats to new ones.
     * 
     * @return
     */
    static synchronized public YarchDatabase getInstance(String yamcsInstance, boolean ignoreVersionIncompatibility) {
        YarchDatabase instance = databases.get(yamcsInstance);
        if(instance==null) {
            try {
                instance = new YarchDatabase(yamcsInstance, ignoreVersionIncompatibility);
            } catch (YarchException e) {
                throw new RuntimeException("Cannot create database '"+yamcsInstance+"'", e);
            }
            databases.put(yamcsInstance, instance);
        }
        return instance;
    }

    static public boolean hasInstance(String dbname) {
        return databases.containsKey(dbname);
    }

    /**
     * 
     * @return the instance name
     */
    public String getName() {
        return dbname;
    }

    public String getYamcsInstance() {
        return dbname;
    }
    
    public String getDefaultStorageEngineName() {
        return defaultStorageEngineName;
    }

    public StorageEngine getDefaultStorageEngine() {
        return storageEngines.get(defaultStorageEngineName);
    }


    /**
     * loads all the .def files from the disk. The ascii def file is structed as follows
     * col1 type1, col2 type2, col3 type3     <- definition of the columns
     * col1, col2                             <- definition of the primary key
     * @throws YarchException 
     */
    void loadTables() throws YarchException {
        File dir=new File(getRoot());
        if(dir.exists() ) {
            File[] dirFiles=dir.listFiles();
            if(dirFiles==null) {
                return; //no tables found
            }
            for(File f:dirFiles) {
                String fn=f.getName();
                if(fn.endsWith(".def")) {
                    try {
                        TableDefinition tblDef = deserializeTableDefinition(f);
                        StorageEngine storageEngine = getStorageEngine(tblDef);
                        if(storageEngine==null) {
                            throw new YarchException("Do not have a storage engine '"+tblDef.getStorageEngineName()+"'. Check storageEngines key in yamcs.yaml");
                        }

                        getStorageEngine(tblDef).loadTable(tblDef);
                        if(jmxService!=null) {
                            jmxService.registerTable(dbname, tblDef);
                        }
                        tables.put(tblDef.getName(), tblDef);
                        log.debug("loaded table definition {} from {}", tblDef.getName(), f);
                    } catch (IOException e) {
                        log.warn("Got exception when reading the table definition from {}: ", f, e);
                        throw new YarchException("Got exception when reading the table definition from "+f+": ", e);
                    } catch (ClassNotFoundException e) {
                        log.warn("Got exception when reading the table definition from {}: ", f, e);
                        throw new YarchException("Got exception when reading the table definition from "+f+": ", e);
                    } 
                }
            }
        } else {
            log.info("Creating directory for db {}: {}", dbname, dir.getAbsolutePath());
            if(!dir.mkdirs()) {
                YamcsServer.getCrashHandler(dbname).handleCrash("Archive", "Cannot create directory: "+dir);
                log.error("Cannot create directory: {}", dir);
            }
        }
    }

    TableDefinition deserializeTableDefinition(File f) throws IOException, ClassNotFoundException {
        if(f.length()==0) {
            throw new IOException("Cannot load table definition from empty file "+f);
        }
        String fn=f.getName();
        String tblName=fn.substring(0,fn.length()-4);
        Yaml yaml = new Yaml(new TableDefinitionConstructor());
        FileInputStream fis=new FileInputStream(f);
        Object o = yaml.load(fis);
        if(!(o instanceof TableDefinition)) {
            fis.close();
            throw new IOException("Cannot load table definition from "+f+": object is "+o.getClass().getName()+"; should be "+TableDefinition.class.getName());
        }
        TableDefinition tblDef = (TableDefinition) o;
        fis.close();
       
        
        tblDef.setName(tblName);
        tblDef.setDb(this);
        if(!tblDef.hasCustomDataDir()){
            tblDef.setDataDir(getRoot());
        }
        
        if(tblDef.getFormatVersion()!=TableDefinition.CURRENT_FORMAT_VERSION) {
            //temporary upgrade to version 2 from version 1 - should be removed in a future version 
            if(tblDef.getFormatVersion()==1) {
                log.info("Converting {} from format version 1 to format version 2", tblDef.getName());
                if("pp".equals(tblDef.getName())) {
                    changeParaValueType(tblDef);
                }
                tblDef.setFormatVersion(2);
                serializeTableDefinition(tblDef);
                return deserializeTableDefinition(f);
            }
        }
        
        log.debug("loaded table definition {}  from {}", tblName, fn);
        return tblDef;
    }

    static void changeParaValueType(TableDefinition tblDef) {
        TupleDefinition valueDef = tblDef.getValueDefinition();
        List<ColumnDefinition> l= valueDef.getColumnDefinitions();
        for(int i=0; i<l.size(); i++) {
            ColumnDefinition cd = l.get(i);
            if("PROTOBUF(org.yamcs.protobuf.Pvalue$ParameterValue)".equals(cd.getType().name())) {
                ColumnDefinition cd1 = new ColumnDefinition(cd.getName(), DataType.PARAMETER_VALUE);
                l.set(i,  cd1);
            }
        }
    }
    
    /**
     * serializes to disk to the rootDir/name.def
     * @param algorithmDef
     */
    void serializeTableDefinition(TableDefinition td) {
        String fn=getRoot()+"/"+td.getName()+".def";
        try (FileOutputStream fos = new FileOutputStream(fn)) {
            Yaml yaml = new Yaml(new TableDefinitionRepresenter());
           
            Writer w = new BufferedWriter(new OutputStreamWriter(fos));
            yaml.dump(td, w);
            w.flush();
            fos.getFD().sync();
            w.close();
        } catch (IOException e) {
            YamcsServer.getCrashHandler(dbname).handleCrash("Archive", "Cannot write table definition to "+fn+" :"+e);
            log.error("Got exception when writing table definition to {} ",fn, e);
        }
    }

    /**
     *  add a table to the dictionary
     *  throws exception if a table or a stream with the same name already exist
     *  
     * @param def - table definition
     * @throws YarchException - thrown in case a table or a stream with the same name already exists or if there was an error in creating the table 
     *  
     */
    public void createTable(TableDefinition def) throws YarchException {
        if(tables.containsKey(def.getName())) {
            throw new YarchException("A table named '"+def.getName()+"' already exists");
        }
        if(streams.containsKey(def.getName())) {
            throw new YarchException("A stream named '"+def.getName()+"' already exists");
        }

        if(!def.hasCustomDataDir()) {
            def.setDataDir(getRoot());
        }
        StorageEngine se = storageEngines.get(def.getStorageEngineName());
        if(se==null) {
            throw new YarchException("Invalid storage engine '"+def.getStorageEngineName()+"' specified. Valid names are: "+storageEngines.keySet());
        }
        se.createTable(def);

        tables.put(def.getName(),def);
        def.setDb(this);
        serializeTableDefinition(def);
        if(jmxService!=null) {
            jmxService.registerTable(dbname, def);
        }
    }



    /**
     * Adds a stream to the dictionary making it "official"
     * @param stream
     * @throws YarchException
     */
    public void addStream(AbstractStream stream) throws YarchException {
        if(tables.containsKey(stream.getName())) {
            throw new YarchException("A table named '"+stream.getName()+"' already exists");
        }
        if(streams.containsKey(stream.getName())) {
            throw new YarchException("A stream named '"+stream.getName()+"' already exists");
        }
        streams.put(stream.getName(), stream);
        if(jmxService!=null) {
            jmxService.registerStream(dbname, stream);
        }
    }

    public TableDefinition getTable(String name) {
        return tables.get(name);
    }

    public boolean streamOrTableExists(String name) {
        if(streams.containsKey(name)) {
            return true;
        }
        if(tables.containsKey(name)) {
            return true;
        }
        return false;
    }

    public AbstractStream getStream(String name) {
        return streams.get(name);
    }



    public void dropTable(String tblName) throws YarchException {
        log.info("dropping table {}", tblName);
        TableDefinition tbl=tables.remove(tblName);
        if(tbl==null) {
            throw new YarchException("There is no table named '"+tblName+"'");
        }
        if(jmxService!=null) {
            jmxService.unregisterTable(dbname, tblName);
        }
        getStorageEngine(tbl).dropTable(tbl);
        File f=new File(getRoot()+"/"+tblName+".def");
        if(!f.delete()) {
            throw new YarchException("Cannot remove "+f);
        }
    }


    public synchronized void removeStream(String name) {
        Stream s=streams.remove(name);
        if((s!=null) &&  (jmxService!=null)) {
            jmxService.unregisterStream(dbname, name);
        }
    }

    public StorageEngine getStorageEngine(TableDefinition tbldef) {
        return storageEngines.get(tbldef.getStorageEngineName());
    }


    public Collection<AbstractStream> getStreams() {
        return streams.values();
    }

    public Collection<TableDefinition> getTableDefinitions() {
        return tables.values();
    }

    /**
     * Returns the root directory for this database instance.
     *  It is usually home/instance_name.
     * @return the roor directory for this database instance
     */
    public String getRoot() {
        return getHome()+"/"+dbname;
    }

    public static void setHome(String home) {
        YarchDatabase.home = home;
    }

    public static String getHome() {
        return home;
    }

    /**to be used for testing
     * @param dbName database name to be removed 
     **/
    public static void removeInstance(String dbName) {
        databases.remove(dbName);
    }

    public StreamSqlResult execute(String query) throws StreamSqlException, ParseException {
        ExecutionContext context = new ExecutionContext(dbname);
        StreamSqlParser parser = new StreamSqlParser(new java.io.StringReader(query));
        try {
            StreamSqlStatement s =  parser.OneStatement();
            return s.execute(context);
        } catch (TokenMgrError e) {
            throw new ParseException(e.getMessage());
        }
    }
}
