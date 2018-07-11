package org.yamcs.yarch;

import java.io.IOException;
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
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectPropertiesOrBuilder;

public class FileSystemBucket implements Bucket {

    private Path root;
    private Mimetypes mimetypes;
    private boolean includeHidden = false;

    public FileSystemBucket(Path root) throws IOException {
        this.root = root;
        mimetypes = Mimetypes.getInstance();
    }

    @Override
    public String getName() {
        return root.getFileName().toString();
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
                if ((prefix == null || objectName.startsWith(prefix))) {
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
                if (includeHidden || !Files.isHidden(dir)) {
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

        // Current implementation ignores specified contentType, instead deriving
        // MIME type from the filename extension.

        Path path = root.resolve(objectName);
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
        if (newSize > FileSystemBucketDatabase.MAX_BUCKET_SIZE) {
            throw new IOException("Maximum bucket size " + FileSystemBucketDatabase.MAX_BUCKET_SIZE + " exceeded");
        }

        int newCount = count.get() + 1;
        if (newCount > FileSystemBucketDatabase.MAX_NUM_OBJECTS_PER_BUCKET) {
            throw new IOException(
                    "Maximum number of objects in the bucket " + newCount + " exceeded");
        }

        if (!Files.exists(path.getParent())) { // Check to avoid error when the parent is a symlink
            Files.createDirectories(path.getParent());
        }
        Files.write(path, objectData);
    }

    @Override
    public byte[] getObject(String objectName) throws IOException {
        Path path = root.resolve(objectName);
        return Files.readAllBytes(path);
    }

    @Override
    public void deleteObject(String objectName) throws IOException {
        Path path = root.resolve(objectName);
        Files.delete(path);
    }

    @Override
    public ObjectProperties findObject(String objectName) throws IOException {
        Path path = root.resolve(objectName);
        if (Files.exists(path)) {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return toObjectProperties(objectName, path, attrs);
        } else {
            return null;
        }
    }

    private ObjectProperties toObjectProperties(String objectName, Path file, BasicFileAttributes attrs) {
        ObjectProperties.Builder props = ObjectProperties.newBuilder();
        props.setName(objectName);
        props.setContentType(mimetypes.getMimetype(file));
        props.setCreated(attrs.creationTime().toMillis());
        props.setSize(attrs.size());
        return props.build();
    }
}
