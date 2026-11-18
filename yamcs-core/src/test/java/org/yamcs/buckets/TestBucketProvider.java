package org.yamcs.buckets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.utils.FileUtils;

/**
 * Bucket provider used in unit tests. It maps each configured bucket to a directory.
 */
public class TestBucketProvider implements BucketProvider {

    public static final BucketLocation LOCATION = new BucketLocation("test", "Test");

    private YConfiguration config;

    @Override
    public BucketLocation getLocation() {
        return LOCATION;
    }

    @Override
    public Spec getSpec() {
        var spec = new Spec();
        spec.addOption("dir", OptionType.STRING).withRequired(true);
        spec.addOption("buckets", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("canCreate", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("canDelete", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public List<Bucket> loadBuckets(YConfiguration config) throws IOException {
        this.config = config;
        var buckets = new ArrayList<Bucket>();
        for (String name : config.<String> getList("buckets")) {
            buckets.add(newBucket(name));
        }
        return buckets;
    }

    @Override
    public Bucket createBucket(String bucketName) throws IOException {
        if (config.getBoolean("canCreate", false)) {
            return newBucket(bucketName);
        } else {
            return BucketProvider.super.createBucket(bucketName);
        }
    }

    @Override
    public void deleteBucket(Bucket bucket) throws IOException {
        if (config.getBoolean("canDelete", false)) {
            FileUtils.deleteRecursively(Path.of(config.getString("dir")).resolve(bucket.getName()));
        } else {
            BucketProvider.super.deleteBucket(bucket);
        }
    }

    private Bucket newBucket(String name) throws IOException {
        var dir = Path.of(config.getString("dir")).resolve(name);
        Files.createDirectories(dir);
        return new FileSystemBucket(name, dir);
    }
}
