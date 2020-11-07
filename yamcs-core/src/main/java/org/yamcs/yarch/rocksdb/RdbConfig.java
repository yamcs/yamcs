package org.yamcs.yarch.rocksdb;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.IndexType;
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
    public static final String KEY_RDB_CONFIG = "rdbConfig";
    public static final String KEY_TABLESPACE_CONFIG = "tablespaceConfig";
    public static final String KEY_OPTIONS = "options";
    public static final String KEY_TABLESPACE_NAME_PATTERN = "tablespaceNamePattern";
    public static final String KEY_TF_CONFIG = "tableFormatConfig";
    public static final int DEFAULT_MAX_OPEN_FILES = 1000;

    private List<TablespaceConfig> tblConfigList = new ArrayList<>();
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
        if (config.containsKey(KEY_RDB_CONFIG)) {
            YConfiguration rdbOptions = config.getConfig(KEY_RDB_CONFIG);
            if (rdbOptions.containsKey(KEY_TABLESPACE_CONFIG)) {
                List<YConfiguration> tableConfigs = rdbOptions.getConfigList(KEY_TABLESPACE_CONFIG);
                for (YConfiguration tableConfig : tableConfigs) {
                    TablespaceConfig tblConf = new TablespaceConfig(tableConfig);
                    tblConfigList.add(tblConf);
                }
            }
        }

        env = Env.getDefault();
        defaultColumnFamilyOptions = new ColumnFamilyOptions();

        BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
        tableFormatConfig.setBlockSize(256l * 1024);// 256KB
        tableFormatConfig.setBlockCacheSize(10l * 1024 * 1024);// 1MB
        tableFormatConfig.setFilter(new BloomFilter());
        // tableFormatConfig.setIndexType(IndexType.kTwoLevelIndexSearch);

        defaultOptions = new Options();
        defaultOptions.setWriteBufferSize(128l * 1024 * 1024);// 50MB
        defaultOptions.setEnv(env);
        defaultOptions.setCreateIfMissing(true);
        defaultOptions.setTableFormatConfig(tableFormatConfig);
        defaultOptions.useFixedLengthPrefixExtractor(4);

        defaultColumnFamilyOptions.setTableFormatConfig(tableFormatConfig);

        defaultDBOptions = new DBOptions();
        defaultDBOptions.setCreateIfMissing(true);

    }

    /**
     * default column family options if no table specific config has been configured.
     * 
     * @return default column family options
     */
    public ColumnFamilyOptions getDefaultColumnFamilyOptions() {
        return defaultColumnFamilyOptions;
    }

    /**
     * default options if no table specific config has been configured.
     * 
     * 
     * @return default options
     */
    public Options getDefaultOptions() {
        return defaultOptions;
    }

    /**
     * default db options if no table specific config has been configured.
     * 
     * no specific option set
     * 
     * @return default options
     */
    public DBOptions getDefaultDBOptions() {
        return defaultDBOptions;
    }

    /**
     * 
     * @param tablespaceName
     * @return the first table config that matches the tablespace name or null if no config matches
     * 
     */
    public TablespaceConfig getTablespaceConfig(String tablespaceName) {
        for (TablespaceConfig tc : tblConfigList) {
            if (tc.tablespaceNamePattern.matcher(tablespaceName).matches()) {
                return tc;
            }
        }
        return null;
    }

    public static class TablespaceConfig {
        Pattern tablespaceNamePattern;
        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions();
        // these options are used for the default column family when the database is open
        // for some strange reason we cannot use the cfOptions for that
        Options options = new Options();
        DBOptions dboptions = new DBOptions();

        long targetFileSizeBase;

        TablespaceConfig(YConfiguration tblspConfig) throws ConfigurationException {
            String s = tblspConfig.getString(KEY_TABLESPACE_NAME_PATTERN);
            try {
                tablespaceNamePattern = Pattern.compile(s);
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException("Cannot parse regexp " + e);
            }
            options.setCreateIfMissing(true);
            int maxOpenFiles = tblspConfig.getInt("maxOpenFiles", DEFAULT_MAX_OPEN_FILES);
            if (maxOpenFiles < 20) {
                throw new ConfigurationException(
                        "Exception when reading table configuration for '" + tablespaceNamePattern
                                + "': maxOpenFiles has to be at least 20");
            }
            options.setMaxOpenFiles(maxOpenFiles);
            dboptions.setMaxOpenFiles(maxOpenFiles);

            if (tblspConfig.containsKey("targetFileSizeBase")) {
                options.setTargetFileSizeBase(1024 * tblspConfig.getLong("targetFileSizeBase"));
            }
            if (tblspConfig.containsKey("targetFileSizeMultiplier")) {
                options.setTargetFileSizeMultiplier(tblspConfig.getInt("targetFileSizeMultiplier"));
            }
            if (tblspConfig.containsKey("maxBytesForLevelBase")) {
                options.setMaxBytesForLevelBase(1024 * tblspConfig.getLong("maxBytesForLevelBase"));
            }
            if (tblspConfig.containsKey("writeBufferSize")) {
                options.setWriteBufferSize(1024 * tblspConfig.getLong("writeBufferSize"));
            }
            if (tblspConfig.containsKey("maxBytesForLevelMultiplier")) {
                options.setMaxBytesForLevelMultiplier(tblspConfig.getInt("maxBytesForLevelMultiplier"));
            }
            if (tblspConfig.containsKey("maxWriteBufferNumber")) {
                options.setMaxWriteBufferNumber(tblspConfig.getInt("maxWriteBufferNumber"));
            }
            if (tblspConfig.containsKey("minWriteBufferNumberToMerge")) {
                options.setMinWriteBufferNumberToMerge(tblspConfig.getInt("minWriteBufferNumberToMerge"));
            }

            if (tblspConfig.containsKey(KEY_TF_CONFIG)) {
                YConfiguration tfc = tblspConfig.getConfig(KEY_TF_CONFIG);
                BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
                if (tfc.containsKey("blockSize")) {
                    tableFormatConfig.setBlockSize(1024L * tfc.getLong("blockSize"));
                }
                if (tfc.containsKey("blockCacheSize")) {
                    tableFormatConfig.setBlockCacheSize(1024L * tfc.getLong("blockCacheSize"));
                }
                if (tfc.containsKey("noBlockCache")) {
                    tableFormatConfig.setNoBlockCache(tfc.getBoolean("noBlockCache"));
                }

                boolean partitionedIndex = tfc.getBoolean("partitionedIndex", true);
                tableFormatConfig
                        .setIndexType(partitionedIndex ? IndexType.kTwoLevelIndexSearch : IndexType.kBinarySearch);

                options.setTableFormatConfig(tableFormatConfig);

            }
            options.useFixedLengthPrefixExtractor(4);
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
