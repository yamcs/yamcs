package org.yamcs.yarch;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;

/**
 * {@link BucketDatabase} implementation that maps objects to files on a file system.
 */
public class FileSystemBucketDatabase implements BucketDatabase {

    static final long MAX_BUCKET_SIZE = 100L * 1024 * 1024; // 100MB
    static final int MAX_NUM_OBJECTS_PER_BUCKET = 1000;

    private Path root;

    // Manually registered buckets (located outside of root)
    private Map<String, FileSystemBucket> additionalBuckets = new HashMap<>();

    public FileSystemBucketDatabase(String yamcsInstance) throws IOException {
        this(yamcsInstance, Collections.emptyMap());
    }

    public FileSystemBucketDatabase(String yamcsInstance, Map<String, Object> args) throws IOException {
        if (args.containsKey("dataDir")) {
            root = Paths.get(YConfiguration.getString(args, "dataDir"));
        } else {
            YConfiguration yconf = YamcsServer.getServer().getConfig();
            String dataDir = yconf.getString("dataDir");
            root = Paths.get(dataDir).resolve(yamcsInstance).resolve("fsbuckets");
        }
        Files.createDirectories(root);
    }

    @Override
    public FileSystemBucket createBucket(String bucketName) throws IOException {
        synchronized (additionalBuckets) {
            if (additionalBuckets.containsKey(bucketName)) {
                throw new IllegalArgumentException("Bucket already exists");
            }
        }

        Path path = root.resolve(bucketName);
        Files.createDirectory(path);
        return new FileSystemBucket(bucketName, path);
    }

    /**
     * Manually register a bucket based on a location on the file system.
     * <p>
     * It is not necessary to register buckets that exist in the standard location (directly under under dataDir).
     * <p>
     * Remark that bucket registration is transient.
     * 
     * @param bucketName
     *            the name of the bucket.
     * 
     * @param location
     *            the path to the bucket. This location should already exist.
     */
    public FileSystemBucket registerBucket(String bucketName, Path location) throws IOException {
        if (!location.toFile().exists()) {
            throw new FileNotFoundException("Directory '" + location + "' not found");
        } else if (!location.toFile().isDirectory()) {
            throw new IOException("Not a directory '" + location + "'");
        }

        synchronized (additionalBuckets) {
            if (additionalBuckets.containsKey(bucketName)) {
                throw new IllegalArgumentException("Bucket already exists");
            }

            FileSystemBucket bucket = new FileSystemBucket(bucketName, location);
            additionalBuckets.put(bucketName, bucket);
            return bucket;
        }
    }

    @Override
    public FileSystemBucket getBucket(String bucketName) throws IOException {
        synchronized (additionalBuckets) {
            FileSystemBucket bucket = additionalBuckets.get(bucketName);
            if (bucket != null) {
                return bucket;
            }
        }

        Path path = root.resolve(bucketName);
        if (Files.isDirectory(path)) {
            return new FileSystemBucket(bucketName, path);
        }
        return null;
    }

    @Override
    public List<BucketProperties> listBuckets() throws IOException {
        List<BucketProperties> props = new ArrayList<>();

        synchronized (additionalBuckets) {
            for (Entry<String, FileSystemBucket> entry : additionalBuckets.entrySet()) {
                Path file = entry.getValue().getBucketRoot();
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                props.add(toBucketProperties(entry.getKey(), file, attrs));
            }

            try (java.util.stream.Stream<Path> stream = Files.list(root)) {
                List<Path> files = stream.collect(Collectors.toList());
                for (Path file : files) {
                    String bucketName = file.getFileName().toString();
                    if (!additionalBuckets.containsKey(bucketName)) {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        if (attrs.isDirectory() && !Files.isHidden(file)) {
                            props.add(toBucketProperties(bucketName, file, attrs));
                        }
                    }
                }
            }
        }

        return props;
    }

    private BucketProperties toBucketProperties(String bucketName, Path location, BasicFileAttributes attrs)
            throws IOException {
        BucketProperties.Builder b = BucketProperties.newBuilder();
        b.setName(bucketName);
        b.setCreated(attrs.creationTime().toMillis());
        b.setMaxNumObjects(MAX_NUM_OBJECTS_PER_BUCKET);
        b.setMaxSize(MAX_BUCKET_SIZE);
        // Commented out because it slows listings down
        // b.setSize(calculateSize(location));
        return b.build();
    }

    @Override
    public void deleteBucket(String bucketName) throws IOException {
        synchronized (additionalBuckets) {
            FileSystemBucket bucket = additionalBuckets.get(bucketName);
            if (bucket != null) {
                Files.delete(bucket.getBucketRoot());
                additionalBuckets.remove(bucketName);
                return;
            }
        }

        Path path = root.resolve(bucketName);
        Files.delete(path);
    }

    public Path getRootPath() {
        return root;
    }

    private static long calculateSize(Path dir) throws IOException {
        AtomicLong size = new AtomicLong(0);
        Set<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
        Files.walkFileTree(dir, opts, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                size.addAndGet(attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        return size.get();
    }
}
