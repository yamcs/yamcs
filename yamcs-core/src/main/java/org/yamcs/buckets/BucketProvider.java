package org.yamcs.buckets;

import java.io.IOException;
import java.util.List;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;

public interface BucketProvider {
    /**
     * Description of provider location
     */
    BucketLocation getLocation();

    /**
     * Returns the valid configuration options for this plugin.
     */
    Spec getSpec();

    /**
     * Called on Yamcs startup. Provider should create and return all buckets that match the provided configuration.
     * <p>
     * Implementations do not need to verify bucket uniqueness.
     */
    List<Bucket> loadBuckets(YConfiguration config) throws IOException;
}
