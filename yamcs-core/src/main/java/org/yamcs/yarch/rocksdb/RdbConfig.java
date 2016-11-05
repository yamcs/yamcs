package org.yamcs.yarch.rocksdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.Options;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * reads the rdbConfig from the yamcs.yaml and provides RocksDB Options when creating and opening databases 
 * 
 * singleton
 * 
 * @author nm
 *
 */
public class RdbConfig {
    static private RdbConfig instance = new RdbConfig();
    public static final String KEY_rdbConfig = "rdbConfig";
    public static final String KEY_tableConfig = "tableConfig";
    public static final String KEY_cfOptions = "columnFamilyOptions";
    public static final String KEY_tableNamePattern = "tableNamePattern";
    public static final String KEY_tfConfig = "tableFormatConfig";

    
    private List<TableConfig> tblConfigList = new ArrayList<TableConfig>();
    final Env env;
    final ColumnFamilyOptions defaultColumnFamilyOptions;
    final Options defaultOptions;
    final DBOptions defaultDBOptions;
    
    /**
     * 
     * @return the singleton instance
     */
    public static RdbConfig getInstance() {
        return instance;
    }
    
    @SuppressWarnings("unchecked")
    private RdbConfig() {
        YConfiguration config = YConfiguration.getConfiguration("yamcs");
        if(config.containsKey(KEY_rdbConfig)) {
            Map<String, Object> rdbOptions = config.getMap(KEY_rdbConfig);
            if(rdbOptions.containsKey(KEY_tableConfig)) {
                List<Object> tableConfigs = YConfiguration.getList(rdbOptions, KEY_tableConfig);
                for(Object o: tableConfigs) {
                    if(!(o instanceof Map)) {
                        throw new ConfigurationException("Error in rdbConfig -> tableConfig in yamcs.yaml: the entries of tableConfig have to be maps");
                    }
                    TableConfig tblConf = new TableConfig( (Map<String, Object>)o);
                    tblConfigList.add(tblConf);
                }
            }
        }
        
        env = Env.getDefault();
        defaultColumnFamilyOptions = new ColumnFamilyOptions().setWriteBufferSize(2*1024*1024);//2MB
        BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
        tableFormatConfig.setBlockSize(32*1024);//32KB
        tableFormatConfig.setNoBlockCache(true);
        defaultColumnFamilyOptions.setTableFormatConfig(tableFormatConfig);
        
        defaultOptions = new Options();
        defaultOptions.setEnv(env);
        defaultOptions.setCreateIfMissing(true);
        defaultOptions.setTableFormatConfig(tableFormatConfig);
        
        defaultDBOptions = new DBOptions().setCreateIfMissing(true);
    }
    
    /**
     * default column family options if no table specific config has been configured.
     *  
     * @return default column family options
     */
    ColumnFamilyOptions getDefaultColumnFamilyOptions() {
        return defaultColumnFamilyOptions;
    }
    /**
     * default options if no table specific config has been configured.
     *  
     *  at least the environment and the create if not open are set.
     *  
     *  
     * @return default options
     */
    Options getDefaultOptions() {
        return defaultOptions;
    }
    /**
     * default db options if no table specific config has been configured.
     *  
     * no specific option set
     * @return default options
     */
    DBOptions getDefaultDBOptions() {
        return defaultDBOptions;
    }
    /**
     *  
     * @param tableName
     * @return the first table config that matches the table name or null if no config matches
     * 
     */
    public TableConfig getTableConfig(String tableName) {
        for(TableConfig tc: tblConfigList) {
            if(tc.tableNamePattern.matcher(tableName).matches()) {
                return tc;
            }
        }
        return null;
    }
    
    public static class TableConfig {
        Pattern tableNamePattern;
        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions();
        //these options are used for the default column family when the database is open
        //for some strange reason we cannot use the cfOptions for that
        Options options = new Options();
        DBOptions dboptions = new DBOptions();
        
        long targetFileSizeBase;
        
        TableConfig(Map<String, Object> m) throws ConfigurationException {
            String s = YConfiguration.getString(m, KEY_tableNamePattern);
            try {
                tableNamePattern = Pattern.compile(s);
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException("Cannot parse regexp "+e);
            }
            options.setCreateIfMissing(true);
            if(m.containsKey("maxOpenFiles")) {
                int maxOpenFiles = YConfiguration.getInt(m, "maxOpenFiles");
                if(maxOpenFiles<20) throw new ConfigurationException("Exception when reading table configuration for '"+tableNamePattern+"': maxOpenFiles has to be at least 20");
                options.setMaxOpenFiles(maxOpenFiles);
                dboptions.setMaxOpenFiles(maxOpenFiles);
            }
            if(m.containsKey(KEY_cfOptions)) {
                Map<String, Object> cm = YConfiguration.getMap(m, KEY_cfOptions);
                if(cm.containsKey("targetFileSizeBase")) {
                    cfOptions.setTargetFileSizeBase(1024 * YConfiguration.getLong(cm, "targetFileSizeBase"));
                    options.setTargetFileSizeBase(1024 * YConfiguration.getLong(cm, "targetFileSizeBase"));
                }
                if(cm.containsKey("targetFileSizeMultiplier")) {
                    cfOptions.setTargetFileSizeMultiplier(YConfiguration.getInt(cm, "targetFileSizeMultiplier"));
                    options.setTargetFileSizeMultiplier(YConfiguration.getInt(cm, "targetFileSizeMultiplier"));
                }
                if(cm.containsKey("maxBytesForLevelBase")) {
                    cfOptions.setMaxBytesForLevelBase(1024 * YConfiguration.getLong(cm, "maxBytesForLevelBase"));
                    options.setMaxBytesForLevelBase(1024 * YConfiguration.getLong(cm, "maxBytesForLevelBase"));
                }
                if(cm.containsKey("writeBufferSize")) {
                    cfOptions.setWriteBufferSize(1024 * YConfiguration.getLong(cm, "writeBufferSize"));
                    options.setWriteBufferSize(1024 * YConfiguration.getLong(cm, "writeBufferSize"));
                }
                if(cm.containsKey("maxBytesForLevelMultiplier")) {
                    cfOptions.setMaxBytesForLevelMultiplier(YConfiguration.getInt(cm, "maxBytesForLevelMultiplier"));
                    options.setMaxBytesForLevelMultiplier(YConfiguration.getInt(cm, "maxBytesForLevelMultiplier"));
                }
                if(cm.containsKey("maxWriteBufferNumber")) {
                    cfOptions.setMaxWriteBufferNumber(YConfiguration.getInt(cm, "maxWriteBufferNumber"));
                    options.setMaxWriteBufferNumber(YConfiguration.getInt(cm, "maxWriteBufferNumber"));
                }
                if(cm.containsKey("minWriteBufferNumberToMerge")) {
                    cfOptions.setMinWriteBufferNumberToMerge(YConfiguration.getInt(cm, "minWriteBufferNumberToMerge"));
                    options.setMinWriteBufferNumberToMerge(YConfiguration.getInt(cm, "minWriteBufferNumberToMerge"));
                }
                
                if(m.containsKey(KEY_tfConfig)) {
                    Map<String, Object> tfc = YConfiguration.getMap(m, KEY_tfConfig);
                    BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
                    if(tfc.containsKey("blockSize")) {
                        tableFormatConfig.setBlockSize(1024L*YConfiguration.getLong(tfc, "blockSize"));
                    }
                    if(tfc.containsKey("blockCacheSize")) {
                        tableFormatConfig.setBlockCacheSize(1024L*YConfiguration.getLong(tfc, "blockCacheSize"));
                    }
                    if(tfc.containsKey("noBlockCache")) {
                        tableFormatConfig.setNoBlockCache(YConfiguration.getBoolean(tfc, "noBlockCache"));
                    }
                    options.setTableFormatConfig(tableFormatConfig);
                    cfOptions.setTableFormatConfig(tableFormatConfig);
                }
            }
        }
        
        public ColumnFamilyOptions getColumnFamilyOptions() {
            return cfOptions;
        }

        public Options getOptions() {
            return options;
        }
        public DBOptions getDBOptions() {
            return dboptions;
        }
    }
}
