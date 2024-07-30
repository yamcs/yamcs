package org.yamcs.yarch;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.yamcs.utils.Mimetypes;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectPropertiesOrBuilder;

public class FileSystemBucket implements Bucket {

    private static final long DEFAULT_MAX_SIZE = 100L * 1024 * 1024; // 100MB
    private static final int DEFAULT_MAX_OBJECTS = 1000;
    private static final Mimetypes MIME = Mimetypes.getInstance();

    private String bucketName;
    private Path root;
    private boolean includeHidden = false;
    private long maxSize = DEFAULT_MAX_SIZE;
    private int maxObjects = DEFAULT_MAX_OBJECTS;

    public FileSystemBucket(String bucketName, Path root) throws IOException {
        this.bucketName = bucketName;
        this.root = root;
    }

    @Override
    public String getName() {
        return bucketName;
    }

    @Override
    public void setMaxSize(long maxSize) throws IOException {
        // Not stored anywhere, we expect it to be set upon startup. For
        // example, coming from configuration.
        this.maxSize = maxSize;
    }

    @Override
    public void setMaxObjects(int maxObjects) throws IOException {
        // Not stored anywhere, we expect it to be set upon startup. For
        // example, coming from configuration.
        this.maxObjects = maxObjects;
    }

    @Override
    public BucketProperties getProperties() throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(root, BasicFileAttributes.class);

        BucketProperties.Builder b = BucketProperties.newBuilder()
                .setName(bucketName)
                .setCreated(attrs.creationTime().toMillis())
                .setMaxNumObjects(maxObjects)
                .setMaxSize(maxSize);

        AtomicLong size = new AtomicLong(0);
        AtomicInteger objectCount = new AtomicInteger(0);
        Set<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
        Files.walkFileTree(root, opts, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                size.addAndGet(attrs.size());
                objectCount.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }
        });

        b.setSize(size.get());
        b.setNumObjects(objectCount.get());
        return b.build();
    }

    @Override
    public List<ObjectProperties> listObjects(String prefix, Predicate<ObjectPropertiesOrBuilder> p)
            throws IOException {
        List<ObjectProperties> objects = new ArrayList<>();
        Set<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
        Files.walkFileTree(root, opts, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String objectName = root.relativize(file).toString();
                if (prefix == null || objectName.startsWith(prefix)) {
                    if (includeHidden || !Files.isHidden(file)) {
                        ObjectProperties props = toObjectProperties(objectName, file, attrs);
                        if (p.test(props)) {
                            objects.add(props);
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String rel = root.relativize(dir).toString();
                if (!rel.isEmpty()) {
                    rel += "/";
                }

                if (includeHidden || !Files.isHidden(dir)) {
                    // By convention, empty folders are represented as objects with a terminating slash
                    if (!Files.isSameFile(root, dir) && (prefix == null || rel.startsWith(prefix))) {
                        try (DirectoryStream<Path> directory = Files.newDirectoryStream(dir)) {
                            if (!directory.iterator().hasNext()) {
                                ObjectProperties props = toObjectProperties(rel, dir, attrs);
                                if (p.test(props)) {
                                    objects.add(props);
                                }
                            }
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.SKIP_SUBTREE;
            }
        });

        Collections.sort(objects, (o1, o2) -> o1.getName().compareTo(o2.getName()));
        return objects;
    }

    @Override
    public void putObject(String objectName, String contentType, Map<String, String> metadata, byte[] objectData)
            throws IOException {
        // TODO: do something with metadata

        Path path = resolvePath(objectName);

        if (objectName.endsWith("/")) {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            } else if (!Files.isDirectory(path)) {
                throw new IOException("Object path is already in use");
            }
        } else {
            // Current implementation ignores specified contentType, instead deriving
            // MIME type from the filename extension.
            boolean fileExists = Files.isRegularFile(path);

            // Verify limits
            AtomicLong size = new AtomicLong(fileExists ? -Files.size(path) : 0);
            AtomicInteger count = new AtomicInteger(fileExists ? -1 : 0);
            Set<FileVisitOption> opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
            Files.walkFileTree(root, opts, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    size.addAndGet(attrs.size());
                    count.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });

            long newSize = size.get() + objectData.length;
            if (newSize > maxSize) {
                throw new IOException("Maximum bucket size " + maxSize + " exceeded");
            }

            int newCount = count.get() + 1;
            if (newCount > maxObjects) {
                throw new IOException(
                        "Maximum number of objects in the bucket " + newCount + " exceeded");
            }

            if (!Files.exists(path.getParent())) { // Check to avoid error when the parent is a symlink
                Files.createDirectories(path.getParent());
            }
            Files.write(path, objectData);
        }
    }

    @Override
    public byte[] getObject(String objectName) throws IOException {
        Path path = resolvePath(objectName);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        } else {
            return null;
        }
    }

    @Override
    public void deleteObject(String objectName) throws IOException {
        Path path = resolvePath(objectName);
        Files.delete(path);
    }

    @Override
    public ObjectProperties findObject(String objectName) throws IOException {
        Path path = resolvePath(objectName);
        if (Files.exists(path)) {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return toObjectProperties(objectName, path, attrs);
        } else {
            return null;
        }
    }

    private Path resolvePath(String objectName) throws IOException {
        Path path = root.resolve(objectName);

        // Prevent directory traversal
        if (!path.normalize().toAbsolutePath().startsWith(root.normalize().toAbsolutePath())) {
            throw new IOException("Directory traversal attempted: " + path);
        }

        return path;
    }

    public Path getBucketRoot() {
        return root;
    }

    private ObjectProperties toObjectProperties(String objectName, Path file, BasicFileAttributes attrs) {
        ObjectProperties.Builder props = ObjectProperties.newBuilder();
        props.setName(objectName);
        props.setContentType(MIME.getMimetype(file));
        // Not creation time. Objects are always replaced.
        props.setCreated(attrs.lastModifiedTime().toMillis());
        props.setSize(attrs.size());
        return props.build();
    }
}
