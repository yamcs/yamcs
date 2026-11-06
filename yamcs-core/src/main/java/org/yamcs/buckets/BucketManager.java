package org.yamcs.buckets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.yamcs.ConfigScope;
import org.yamcs.ConfigurationException;
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
    // Discovered providers, by type. Used for the configuration spec, and as template for creating a provider
    // instance for each configured entry.
    private final Map<String, BucketProvider> providers = new HashMap<>(); // External buckets

    // Provider instance that owns each provider bucket. Used for delegating the deletion of buckets.
    private final Map<String, BucketProvider> bucketOwners = new HashMap<>();

    // Provider instance to which the creation of new buckets is delegated. If null, new buckets are created in the
    // local database.
    private BucketProvider creationProvider;

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
        bucketSpec.addOption("accessRules", OptionType.LIST)
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
        providerSpec.addOption("createBuckets", OptionType.BOOLEAN).withDefault(false);

        for (var provider : providers.values()) {
            var condition = providerSpec.when("type", provider.getLocation().name());
            condition.mergeSpec(provider.getSpec());
        }

        yamcs.addConfigurationList(ConfigScope.YAMCS, "bucketProviders", providerSpec);
    }

    public void loadBuckets() throws IOException {
        loadBuckets(YamcsServer.getServer().getConfig());
    }

    void loadBuckets(YConfiguration yconf) throws IOException {
        var yarchDatabase = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);
        bucketDatabase = YarchDatabase.getDefaultStorageEngine().getBucketDatabase(yarchDatabase);

        for (var rdbBucket : bucketDatabase.listBuckets()) {
            buckets.put(rdbBucket.getName(), rdbBucket);
        }

        if (yconf != null) { // Can be null in unit tests
            // Load the providers first, so that the buckets configured below can refer to provider buckets,
            // or be created through a provider.
            if (yconf.containsKey("bucketProviders")) {
                for (var providerConfig : yconf.getConfigList("bucketProviders")) {
                    var provider = newProvider(providers.get(providerConfig.getString("type")));
                    if (providerConfig.getBoolean("createBuckets", false)) {
                        if (creationProvider != null) {
                            throw new ConfigurationException(
                                    "Only one bucket provider can have createBuckets enabled");
                        }
                        creationProvider = provider;
                    }
                    for (var extraBucket : provider.loadBuckets(providerConfig)) {
                        addBucket(extraBucket, provider);
                    }
                }
            }

            if (yconf.containsKey("buckets")) {
                var bucketsConfigs = yconf.getConfigList("buckets");
                for (var config : bucketsConfigs) {
                    loadBucket(config);
                }
            }
        }
    }

    private static BucketProvider newProvider(BucketProvider template) {
        try {
            return template.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ConfigurationException(
                    "Cannot instantiate bucket provider " + template.getClass().getName(), e);
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
            // Creates empty access rules HashMaps for all permissions types
            Map<AccessRuleType, Map<String, List<PathMatcher>>> accessRules = Arrays.stream(AccessRuleType.values())
                    .collect(Collectors.toMap(type -> type, type -> new HashMap<>()));

            // Adds rules to each rule type hashmap, with role as key and their list of glob patterns as values
            FileSystem fileSystem = FileSystems.getDefault();
            config.getConfigList("accessRules").forEach(rule -> {
                String role = rule.getString("role");
                Arrays.stream(AccessRuleType.values()).forEach(type ->
                        accessRules.get(type).put(
                                role,
                                rule.getList(type.getConfigName()).stream()
                                        .map(glob -> fileSystem.getPathMatcher("glob:" + glob))
                                        .toList()
                        ));
            });

            bucket.setAccessRules(accessRules);
        }
    }

    public Bucket getBucket(String bucketName) throws IOException {
        synchronized (buckets) {
            return buckets.get(bucketName);
        }
    }

    /**
     * Creates a new bucket.
     * <p>
     * The bucket is created in the local database, unless a bucket provider is configured with
     * {@code createBuckets: true}, in which case the creation is delegated to that provider.
     *
     * @throws IllegalArgumentException
     *             if a bucket with this name already exists
     * @throws UnsupportedOperationException
     *             if the bucket provider does not support creating buckets
     */
    public Bucket createBucket(String bucketName) throws IOException {
        synchronized (buckets) {
            var bucket = buckets.get(bucketName);
            if (bucket == null) {
                if (creationProvider == null) {
                    bucket = bucketDatabase.createBucket(bucketName);
                } else {
                    bucket = creationProvider.createBucket(bucketName);
                    bucketOwners.put(bucketName, creationProvider);
                }
                buckets.put(bucketName, bucket);
            } else {
                throw new IllegalArgumentException("Bucket " + bucketName + " already exists");
            }
            return bucket;
        }
    }

    /**
     * Adds an already existing bucket. This is a transient operation that has to be done on each server restart.
     * <p>
     * This method can be used at runtime, for example by a provider that discovers new buckets.
     * <p>
     * A bucket added with this method cannot be deleted through {@link #deleteBucket(String)}. Use
     * {@link #addBucket(Bucket, BucketProvider)} to have the deletion delegated to a provider.
     *
     * @throws IllegalArgumentException
     *             if a bucket with this name already exists
     */
    public void addBucket(Bucket bucket) {
        addBucket(bucket, null);
    }

    /**
     * Adds an already existing bucket, owned by a {@link BucketProvider}. This is a transient operation that has to
     * be done on each server restart.
     * <p>
     * This method can be used at runtime, for example by a provider that discovers new buckets.
     *
     * @param bucket
     *            the bucket to add
     * @param owner
     *            the provider owning the bucket, to which the deletion of this bucket is delegated. May be null.
     * @throws IllegalArgumentException
     *             if a bucket with this name already exists
     */
    public void addBucket(Bucket bucket, BucketProvider owner) {
        synchronized (buckets) {
            if (buckets.containsKey(bucket.getName())) {
                throw new IllegalArgumentException("Bucket " + bucket.getName() + " already exists");
            }
            log.info("Adding {} bucket '{}'", bucket.getLocation(), bucket.getName());
            buckets.put(bucket.getName(), bucket);
            if (owner != null) {
                bucketOwners.put(bucket.getName(), owner);
            }
        }
    }

    /**
     * Removes a bucket that was added with {@link #addBucket(Bucket)}, or that was created by a bucket provider. The
     * bucket content is not touched.
     * <p>
     * Components that have kept a reference to the bucket may continue to use it.
     *
     * @return the removed bucket, or null if there was no bucket with this name
     * @throws UnsupportedOperationException
     *             if the bucket is a local RocksDB bucket. Use {@link #deleteBucket(String)} for those.
     */
    public Bucket removeBucket(String bucketName) {
        synchronized (buckets) {
            var bucket = buckets.get(bucketName);
            if (bucket instanceof RdbBucket) {
                throw new UnsupportedOperationException("Local RocksDB buckets cannot be removed, only deleted");
            }
            if (bucket != null) {
                log.info("Removing {} bucket '{}'", bucket.getLocation(), bucketName);
                buckets.remove(bucketName);
                bucketOwners.remove(bucketName);
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

    /**
     * Deletes an empty bucket.
     * <p>
     * Local RocksDB buckets are deleted from the database. For buckets owned by a bucket provider, the deletion is
     * delegated to that provider.
     *
     * @throws UnsupportedOperationException
     *             if the bucket cannot be deleted. For example a file system bucket, or a provider bucket whose
     *             provider does not support deleting buckets.
     * @throws IllegalStateException
     *             if the bucket still contains objects
     */
    public void deleteBucket(String bucketName) throws IOException {
        synchronized (buckets) {
            var bucket = buckets.get(bucketName);
            var owner = bucketOwners.get(bucketName);
            if (bucket instanceof RdbBucket rdbBucket) {
                // Same lock as putObject/deleteObject, so no object can be added between check and delete
                synchronized (rdbBucket) {
                    if (rdbBucket.getProperties().numObjects() > 0) {
                        throw new IllegalStateException("Bucket '" + bucketName + "' is not empty");
                    }
                    bucketDatabase.deleteBucket(bucketName);
                }
            } else if (owner != null) {
                if (!isEmpty(bucket)) {
                    throw new IllegalStateException("Bucket '" + bucketName + "' is not empty");
                }
                owner.deleteBucket(bucket);
                bucketOwners.remove(bucketName);
            } else {
                throw new UnsupportedOperationException("Bucket " + bucketName + " cannot be deleted");
            }
            buckets.remove(bucketName);
        }
    }

    private static boolean isEmpty(Bucket bucket) throws IOException {
        try {
            // Not using the properties, because providers may not track the number of objects
            return bucket.listObjectsAsync().get().isEmpty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
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
