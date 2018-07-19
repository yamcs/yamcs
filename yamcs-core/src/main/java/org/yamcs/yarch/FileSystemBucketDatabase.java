package org.yamcs.yarch;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.yamcs.YConfiguration;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;

/**
 * {@link BucketDatabase} implementation that maps objects to files on a file system.
 */
public class FileSystemBucketDatabase implements BucketDatabase {

    static final long MAX_BUCKET_SIZE = 100L * 1024 * 1024; // 100MB
    static final int MAX_NUM_OBJECTS_PER_BUCKET = 1000;

    private Path root;

    public FileSystemBucketDatabase(String yamcsInstance) throws IOException {
        this(yamcsInstance, Collections.emptyMap());
    }

    public FileSystemBucketDatabase(String yamcsInstance, Map<String, Object> args) throws IOException {
        if (args.containsKey("dataDir")) {
            root = Paths.get(YConfiguration.getString(args, "dataDir"));
        } else {
            YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
            String dataDir = yconf.getString("dataDir");
            root = Paths.get(dataDir).resolve(yamcsInstance).resolve("fsbuckets");
        }
        Files.createDirectories(root);
    }

    @Override
    public Bucket createBucket(String bucketName) throws IOException {
        Path path = root.resolve(bucketName);
        Files.createDirectory(path);
        return new FileSystemBucket(path);
    }

    @Override
    public Bucket getBucket(String bucketName) throws IOException {
        Path path = root.resolve(bucketName);
        if (Files.isDirectory(path)) {
            return new FileSystemBucket(path);
        }
        return null;
    }

    @Override
    public List<BucketProperties> listBuckets() throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(root)) {
            List<Path> files = stream.collect(Collectors.toList());
            List<BucketProperties> props = new ArrayList<>();
            for (Path file : files) {
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                if (attrs.isDirectory() && !Files.isHidden(file)) {
                    BucketProperties.Builder b = BucketProperties.newBuilder();
                    b.setName(file.getFileName().toString());
                    b.setCreated(attrs.creationTime().toMillis());
                    b.setMaxNumObjects(MAX_NUM_OBJECTS_PER_BUCKET);
                    b.setMaxSize(MAX_BUCKET_SIZE);
                    b.setSize(calculateSize(file));
                    props.add(b.build());
                }
            }
            return props;
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws IOException {
        Path path = root.resolve(bucketName);
        Files.delete(path);
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
