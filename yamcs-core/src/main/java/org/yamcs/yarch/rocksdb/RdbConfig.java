package org.yamcs.yarch.rocksdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.Cache;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.IndexType;
import org.rocksdb.LRUCache;
import org.rocksdb.YamcsParchiveMergeOperator;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.parameterarchive.ParameterArchive;

/**
 * reads the rdbConfig from the yamcs.yaml and provides RocksDB Options when creating and opening databases
 * 
 * singleton
 * 
 * @author nm
 *
 */
public class RdbConfig {
    public static final String KEY_RDB_CONFIG = "rdbConfig";
    public static final String KEY_TABLESPACE_CONFIG = "tablespaceConfig";
    public static final String KEY_CF_CONFIG = "columnFamilyConfig";
    public static final String KEY_OPTIONS = "options";
    public static final String KEY_TABLESPACE_NAME_PATTERN = "tablespaceNamePattern";
    public static final String KEY_CF_PATTERN = "columnFamilyPattern";
    public static final String KEY_TF_CONFIG = "tableFormatConfig";

    public static final int DEFAULT_MAX_OPEN_FILES = 10000;

    static final Map<String, CompressionType> COMP_TYPES = new HashMap<>();
    static {
        COMP_TYPES.put("none", CompressionType.DISABLE_COMPRESSION_OPTION);
        COMP_TYPES.put("bzlib2", CompressionType.BZLIB2_COMPRESSION);
        COMP_TYPES.put("lz4", CompressionType.LZ4_COMPRESSION);
        COMP_TYPES.put("lz4hc", CompressionType.LZ4HC_COMPRESSION);
        COMP_TYPES.put("snappy", CompressionType.SNAPPY_COMPRESSION);
        COMP_TYPES.put("xpress", CompressionType.XPRESS_COMPRESSION);
        COMP_TYPES.put("zlib", CompressionType.ZLIB_COMPRESSION);
        COMP_TYPES.put("zstd", CompressionType.ZSTD_COMPRESSION);
    }

    static final private RdbConfig INSTANTCE = new RdbConfig();

    final TablespaceConfig defaultTblConfig;
    private List<TablespaceConfig> tblConfigList = new ArrayList<>();

    /**
     * 
     * @return the singleton instance
     */
    public static RdbConfig getInstance() {
        return INSTANTCE;
    }

    private RdbConfig() {
        YConfiguration config = YConfiguration.getConfiguration("yamcs");
        if (config.containsKey(KEY_RDB_CONFIG)) {
            YConfiguration rdbOptions = config.getConfig(KEY_RDB_CONFIG);
            if (rdbOptions.containsKey(KEY_TABLESPACE_CONFIG)) {
                List<YConfiguration> tablespaceConfigs = rdbOptions.getConfigList(KEY_TABLESPACE_CONFIG);
                for (YConfiguration tableConfig : tablespaceConfigs) {
                    TablespaceConfig tblConf = new TablespaceConfig(tableConfig);
                    tblConfigList.add(tblConf);
                }
            }
        }

        defaultTblConfig = new TablespaceConfig();
    }

    /**
     * 
     * @param tablespaceName
     * @return the first tablespace config that matches the tablespace name or the default config if no config matches
     * 
     */
    public TablespaceConfig getTablespaceConfig(String tablespaceName) {
        for (TablespaceConfig tc : tblConfigList) {
            if (tc.tablespaceNamePattern.matcher(tablespaceName).matches()) {
                return tc;
            }
        }
        return defaultTblConfig;
    }

    public static class TablespaceConfig {
        Pattern tablespaceNamePattern;
        // these options are used for the default column family when the database is
        // open for some strange reason we cannot use the cfOptions for that

        DBOptions dboptions;

        ColumnFamilyOptions defaultCfOptions = new ColumnFamilyOptions();
        ColumnFamilyOptions metadataDbCfOptions = new ColumnFamilyOptions();
        ColumnFamilyOptions rtDataCfOptions = new ColumnFamilyOptions();
        ColumnFamilyOptions parchiveCfOptions = new ColumnFamilyOptions();

        List<CfConfig> cfConfigList = new ArrayList<>();
        BlockBasedTableConfig tableFormatConfig;

        long targetFileSizeBase;
        final LRUCache lruCache;

        /**
         * default tablespace config containing default
         */
        public TablespaceConfig() {
            dboptions = new DBOptions();
            dboptions.setCreateIfMissing(true);
            dboptions.setKeepLogFileNum(10);
            dboptions.setMaxOpenFiles(DEFAULT_MAX_OPEN_FILES);
            int halfNumProc = Runtime.getRuntime().availableProcessors() / 2;
            if (halfNumProc > 1) {
                dboptions.setIncreaseParallelism(halfNumProc);
            }

            metadataDbCfOptions.optimizeForSmallDb();
            metadataDbCfOptions.setCompressionType(CompressionType.LZ4_COMPRESSION);

            defaultCfOptions.useFixedLengthPrefixExtractor(4);
            defaultCfOptions.setCompressionType(CompressionType.LZ4_COMPRESSION);

            rtDataCfOptions.useFixedLengthPrefixExtractor(4);
            rtDataCfOptions.setCompressionType(CompressionType.LZ4_COMPRESSION);
            rtDataCfOptions.setBottommostCompressionType(CompressionType.ZSTD_COMPRESSION);
            rtDataCfOptions.setMaxWriteBufferNumber(4);
            rtDataCfOptions.setTargetFileSizeMultiplier(2);
            rtDataCfOptions.setLevel0SlowdownWritesTrigger(50);
            rtDataCfOptions.setLevel0StopWritesTrigger(100);

            parchiveCfOptions.useFixedLengthPrefixExtractor(4);
            parchiveCfOptions.setCompressionType(CompressionType.LZ4_COMPRESSION);
            parchiveCfOptions.setTargetFileSizeMultiplier(2);
            parchiveCfOptions.setMaxWriteBufferNumber(4);
            parchiveCfOptions.setLevel0FileNumCompactionTrigger(20);
            parchiveCfOptions.setLevel0SlowdownWritesTrigger(50);
            parchiveCfOptions.setLevel0StopWritesTrigger(100);
            parchiveCfOptions.setMergeOperator(new YamcsParchiveMergeOperator());

            tableFormatConfig = new BlockBasedTableConfig();
            tableFormatConfig.setBlockSize(256l * 1024);
            tableFormatConfig.setFormatVersion(5);
            tableFormatConfig.setFilterPolicy(new BloomFilter());
            lruCache = new LRUCache(64 * 1024 * 1024);
            tableFormatConfig.setBlockCache(lruCache);

            tableFormatConfig.setIndexType(IndexType.kTwoLevelIndexSearch);

            rtDataCfOptions.setTableFormatConfig(tableFormatConfig);
            parchiveCfOptions.setTableFormatConfig(tableFormatConfig);
            defaultCfOptions.setTableFormatConfig(tableFormatConfig);
            metadataDbCfOptions.setTableFormatConfig(tableFormatConfig);

            cfConfigList.add(new CfConfig(lruCache, Pattern.compile(ParameterArchive.CF_NAME), parchiveCfOptions));
            cfConfigList.add(new CfConfig(lruCache, Pattern.compile(XtceTmRecorder.CF_NAME), rtDataCfOptions));
            cfConfigList.add(new CfConfig(lruCache, Pattern.compile(Tablespace.CF_METADATA), metadataDbCfOptions));
            cfConfigList.add(new CfConfig(lruCache, Pattern.compile(YRDB.DEFAULT_CF), defaultCfOptions));

        }

        TablespaceConfig(YConfiguration tblspConfig) throws ConfigurationException {
            this();
            String s = tblspConfig.getString(KEY_TABLESPACE_NAME_PATTERN);
            try {
                tablespaceNamePattern = Pattern.compile(s);
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException("Cannot parse regexp " + e);
            }

            if (tblspConfig.containsKey("maxOpenFiles")) {
                dboptions.setMaxOpenFiles(tblspConfig.getInt("maxOpenFiles"));
            }

            if (tblspConfig.containsKey("maxBackgroundJobs")) {
                dboptions.setMaxBackgroundJobs(tblspConfig.getInt("maxBackgroundJobs"));
            }
            if (tblspConfig.containsKey("allowConcurrentMemtableWrite")) {
                dboptions.setAllowConcurrentMemtableWrite(tblspConfig.getBoolean("allowConcurrentMemtableWrite"));
            }

            if (tblspConfig.containsKey(KEY_CF_CONFIG)) {
                int count = 0;
                List<YConfiguration> cfConfigs = tblspConfig.getConfigList(KEY_CF_CONFIG);
                for (YConfiguration cfConfig : cfConfigs) {
                    CfConfig cfConf = new CfConfig(lruCache, cfConfig);
                    cfConfigList.add(count, cfConf); // make sure to add them before the three ones added in the default
                                                     // constructor
                    count++;
                }
            }
        }

        public ColumnFamilyOptions getColumnFamilyOptions(String cfName) {
            for (CfConfig cfc : cfConfigList) {
                if (cfc.cfNamePattern.matcher(cfName).matches()) {
                    return cfc.options;
                }
            }
            return defaultCfOptions;
        }

        public DBOptions getDBOptions() {
            return dboptions;
        }

        public Cache getTableCache() {
            return lruCache;
        }
    }

    static class CfConfig {
        Pattern cfNamePattern;
        ColumnFamilyOptions options;
        final LRUCache lruCache;

        public CfConfig(LRUCache lruCache, Pattern cfNamePattern, ColumnFamilyOptions options) {
            this.lruCache = lruCache;
            this.cfNamePattern = cfNamePattern;
            this.options = options;
        }

        public CfConfig(LRUCache lruCache, YConfiguration cfConfig) {
            this.lruCache = lruCache;
            String s = cfConfig.getString(KEY_CF_PATTERN);
            try {
                cfNamePattern = Pattern.compile(s);
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException("Cannot parse regexp " + e);
            }
            options = new ColumnFamilyOptions();
            options.useFixedLengthPrefixExtractor(4);

            if (cfConfig.containsKey("numLevels")) {
                options.setNumLevels(cfConfig.getInt("numLevels"));
            }
            if (cfConfig.containsKey("targetFileSizeBase")) {
                options.setTargetFileSizeBase(1024 * cfConfig.getLong("targetFileSizeBase"));
            }
            if (cfConfig.containsKey("targetFileSizeMultiplier")) {
                options.setTargetFileSizeMultiplier(cfConfig.getInt("targetFileSizeMultiplier"));
            }
            if (cfConfig.containsKey("maxBytesForLevelBase")) {
                options.setMaxBytesForLevelBase(1024 * cfConfig.getLong("maxBytesForLevelBase"));
            }
            if (cfConfig.containsKey("writeBufferSize")) {
                options.setWriteBufferSize(1024 * cfConfig.getLong("writeBufferSize"));
            }
            if (cfConfig.containsKey("maxBytesForLevelMultiplier")) {
                options.setMaxBytesForLevelMultiplier(cfConfig.getInt("maxBytesForLevelMultiplier"));
            }
            if (cfConfig.containsKey("maxWriteBufferNumber")) {
                options.setMaxWriteBufferNumber(cfConfig.getInt("maxWriteBufferNumber"));
            }

            if (cfConfig.containsKey("minWriteBufferNumberToMerge")) {
                options.setMinWriteBufferNumberToMerge(cfConfig.getInt("minWriteBufferNumberToMerge"));
            }
            if (cfConfig.containsKey("level0FileNumCompactionTrigger")) {
                options.setLevel0FileNumCompactionTrigger(cfConfig.getInt("level0FileNumCompactionTrigger"));
            }
            if (cfConfig.containsKey("level0SlowdownWritesTrigger")) {
                options.setLevel0SlowdownWritesTrigger(cfConfig.getInt("level0SlowdownWritesTrigger"));
            }
            if (cfConfig.containsKey("level0StopWritesTrigger")) {
                options.setLevel0StopWritesTrigger(cfConfig.getInt("level0StopWritesTrigger"));
            }
            if (cfConfig.containsKey("compressionType")) {
                options.setCompressionType(getCompressionType(cfConfig.getString("compressionType")));
            }

            if (cfConfig.containsKey("compressionPerLevel")) {
                List<String> comps = cfConfig.getList("compressionPerLevel");
                List<CompressionType> compl = comps.stream().map(comptype -> getCompressionType(comptype))
                        .collect(Collectors.toList());
                options.setCompressionPerLevel(compl);
            }

            if (cfConfig.containsKey("bottommostCompressionType")) {
                options.setBottommostCompressionType(
                        getCompressionType(cfConfig.getString("bottommostCompressionType")));
            }

            if (cfConfig.containsKey(KEY_TF_CONFIG)) {
                YConfiguration tfc = cfConfig.getConfig(KEY_TF_CONFIG);
                BlockBasedTableConfig tableFormatConfig = new BlockBasedTableConfig();
                if (tfc.containsKey("blockSize")) {
                    tableFormatConfig.setBlockSize(1024L * tfc.getLong("blockSize"));
                }

                if (tfc.containsKey("noBlockCache")) {
                    tableFormatConfig.setNoBlockCache(tfc.getBoolean("noBlockCache"));
                }

                boolean partitionedIndex = tfc.getBoolean("partitionedIndex", true);
                tableFormatConfig
                        .setIndexType(partitionedIndex ? IndexType.kTwoLevelIndexSearch : IndexType.kBinarySearch);
                options.setTableFormatConfig(tableFormatConfig);
            }
        }

    }

    static CompressionType getCompressionType(String compr) {
        CompressionType ct = COMP_TYPES.get(compr);
        if (ct == null) {
            throw new ConfigurationException(
                    "Unknown compression type '" + compr + "'. Allowed types: " + COMP_TYPES.keySet());
        }
        return ct;
    }
}
