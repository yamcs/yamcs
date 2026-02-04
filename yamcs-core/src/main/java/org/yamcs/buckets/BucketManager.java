package org.yamcs.buckets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.yamcs.ConfigScope;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.buckets.Bucket.AccessRuleType;
import org.yamcs.logging.Log;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.RdbBucket;

/**
 * Provides access to buckets of various types.
 */
public class BucketManager {

    private static final Log log = new Log(BucketManager.class);

    private final Map<String, Bucket> buckets = new HashMap<>();

    private BucketDatabase bucketDatabase; // Local DB buckets
    private final Map<String, BucketProvider> providers = new HashMap<>(); // External buckets

    public BucketManager() {
        var yamcs = YamcsServer.getServer();

        var accessRuleSpec = new Spec();
        accessRuleSpec.addOption("role", OptionType.STRING).withRequired(true);
        Arrays.stream(AccessRuleType.values()).forEach(type ->
                accessRuleSpec.addOption(type.getConfigName(), OptionType.LIST_OR_ELEMENT)
                        .withElementType(OptionType.STRING)
                        .withDefault(Collections.emptyList())
        );

        var bucketSpec = new Spec();
        bucketSpec.addOption("name", OptionType.STRING).withRequired(true);
        bucketSpec.addOption("path", OptionType.STRING);
        bucketSpec.addOption("maxSize", OptionType.INTEGER);
        bucketSpec.addOption("maxObjects", OptionType.INTEGER);
        bucketSpec.addOption("accessRules", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.MAP).withSpec(accessRuleSpec);

        yamcs.addConfigurationList(ConfigScope.YAMCS, "buckets", bucketSpec);

        // Discover known providers
        for (var provider : ServiceLoader.load(BucketProvider.class)) {
            providers.put(provider.getLocation().name(), provider);
        }

        var providerSpec = new Spec();
        providerSpec.addOption("type", OptionType.STRING)
                .withRequired(true)
                .withChoices(providers.keySet());

        for (var provider : providers.values()) {
            var condition = providerSpec.when("type", provider.getLocation().name());
            condition.mergeSpec(provider.getSpec());
        }

        yamcs.addConfigurationList(ConfigScope.YAMCS, "bucketProviders", providerSpec);
    }

    public void loadBuckets() throws IOException {
        var yarchDatabase = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        bucketDatabase = YarchDatabase.getDefaultStorageEngine().getBucketDatabase(yarchDatabase);

        for (var rdbBucket : bucketDatabase.listBuckets()) {
            buckets.put(rdbBucket.getName(), rdbBucket);
        }

        var yconf = YamcsServer.getServer().getConfig();
        if (yconf != null) { // Can be null in unit tests
            if (yconf.containsKey("buckets")) {
                var bucketsConfigs = yconf.getConfigList("buckets");
                for (var config : bucketsConfigs) {
                    loadBucket(config);
                }
            }

            if (yconf.containsKey("bucketProviders")) {
                for (var providerConfig : yconf.getConfigList("bucketProviders")) {
                    var provider = providers.get(providerConfig.getString("type"));
                    var extraBuckets = provider.loadBuckets(providerConfig);
                    synchronized (buckets) {
                        for (var extraBucket : extraBuckets) {
                            var bucket = buckets.get(extraBucket.getName());
                            if (bucket == null) {
                                log.info("Adding {} bucket '{}'", extraBucket.getLocation(), extraBucket.getName());
                                buckets.put(extraBucket.getName(), extraBucket);
                            } else {
                                throw new IllegalArgumentException(
                                        "Bucket " + extraBucket.getName() + " already exists");
                            }
                        }
                    }
                }
            }
        }
    }

    private void loadBucket(YConfiguration config) throws IOException {
        String name = config.getString("name");
        Bucket bucket;
        if (config.containsKey("path")) {
            var path = Paths.get(config.getString("path"));
            bucket = createFileSystemBucket(name, path);
        } else {
            bucket = getBucket(name);
            if (bucket == null) {
                log.info("Creating bucket {}", name);
                bucket = createBucket(name);
            }
        }
        if (config.containsKey("maxSize")) {
            long maxSize = config.getLong("maxSize");
            bucket.setMaxSize(maxSize);
        }
        if (config.containsKey("maxObjects")) {
            int maxObjects = config.getInt("maxObjects");
            bucket.setMaxObjects(maxObjects);
        }

        if (config.containsKey("accessRules")) {
            Map<AccessRuleType, Map<String, List<String>>> accessRules = Arrays.stream(AccessRuleType.values())
                    .collect(Collectors.toMap(type -> type, type -> new HashMap<>()));

            config.getConfigList("accessRules").forEach(rule -> {
                String role = rule.getString("role");
                Arrays.stream(AccessRuleType.values()).forEach(type ->
                        accessRules.get(type).put(role, rule.getList(type.getConfigName())));
            });

            bucket.setAccessRules(accessRules);
        }
    }

    public Bucket getBucket(String bucketName) throws IOException {
        synchronized (buckets) {
            return buckets.get(bucketName);
        }
    }

    public Bucket createBucket(String bucketName) throws IOException {
        synchronized (buckets) {
            var bucket = buckets.get(bucketName);
            if (bucket == null) {
                bucket = bucketDatabase.createBucket(bucketName);
                buckets.put(bucketName, bucket);
            } else {
                throw new IllegalArgumentException("Bucket " + bucketName + " already exists");
            }
            return bucket;
        }
    }

    /**
     * Adds a bucket that maps to the file system. This is a transient operation that has to be done on each server
     * restart.
     * 
     * @param bucketName
     *            the name of the bucket
     * @param location
     *            path to the bucket contents. The directory is created if it does not yet exist.
     * @return the created bucket
     * @throws IOException
     *             on I/O issues
     */
    public FileSystemBucket createFileSystemBucket(String bucketName, Path location) throws IOException {
        synchronized (buckets) {
            var bucket = buckets.get(bucketName);
            if (bucket == null || bucket instanceof RdbBucket) { // Shadow existing RDB bucket
                if (!Files.exists(location)) { // Mandatory check, to allow for symlinks
                    Files.createDirectories(location);
                }
                var fsBucket = new FileSystemBucket(bucketName, location);
                buckets.put(bucketName, fsBucket);
                return fsBucket;
            } else {
                throw new IllegalArgumentException("Bucket " + bucketName + " already exists");
            }
        }
    }

    public List<Bucket> listBuckets() throws IOException {
        synchronized (buckets) {
            return new ArrayList<>(buckets.values());
        }
    }

    public void deleteBucket(String bucketName) throws IOException {
        var bucket = getBucket(bucketName);
        if (bucket instanceof RdbBucket) {
            bucketDatabase.deleteBucket(bucketName);
        } else {
            throw new UnsupportedOperationException("Only local RocksDB buckets can be deleted");
        }
    }

    /**
     * Configure this class for use in standalone unit tests
     */
    public static void setMockup() {
        var bucketManager = new BucketManager();
        try {
            bucketManager.loadBuckets();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        YamcsServer.getServer().setBucketManager(bucketManager);
    }
}
