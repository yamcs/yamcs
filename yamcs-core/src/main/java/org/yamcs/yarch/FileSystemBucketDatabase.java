package org.yamcs.yarch;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yamcs.YConfiguration;

/**
 * {@link BucketDatabase} implementation that maps objects to files on a file system.
 */
public class FileSystemBucketDatabase implements BucketDatabase {

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
            String dataDir = YarchDatabase.getDataDir();
            root = Paths.get(dataDir).resolve(yamcsInstance).resolve("fsbuckets");
        }
    }

    @Override
    public FileSystemBucket createBucket(String bucketName) throws IOException {
        synchronized (additionalBuckets) {
            if (additionalBuckets.containsKey(bucketName)) {
                throw new IllegalArgumentException("Bucket already exists");
            }
        }

        Path path = root.resolve(bucketName);
        Files.createDirectories(path);
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
            throw new FileNotFoundException(
                    "Directory '" + location + "'(" + location.toAbsolutePath() + ") not found");
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
    public List<Bucket> listBuckets() throws IOException {
        List<Bucket> buckets = new ArrayList<>();

        synchronized (additionalBuckets) {
            buckets.addAll(additionalBuckets.values());

            if (Files.exists(root)) {
                try (java.util.stream.Stream<Path> stream = Files.list(root)) {
                    List<Path> files = stream.collect(Collectors.toList());
                    for (Path file : files) {
                        String bucketName = file.getFileName().toString();
                        if (!additionalBuckets.containsKey(bucketName)) {
                            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                            if (attrs.isDirectory() && !Files.isHidden(file)) {
                                Bucket bucket = new FileSystemBucket(bucketName, file);
                                buckets.add(bucket);
                            }
                        }
                    }
                }
            }
        }

        return buckets;
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
}
