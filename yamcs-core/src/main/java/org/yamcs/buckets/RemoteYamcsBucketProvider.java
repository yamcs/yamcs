package org.yamcs.buckets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.client.BasicAuthCredentials;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.storage.StorageClient;

public class RemoteYamcsBucketProvider implements BucketProvider {

    private StorageClient storageClient;

    @Override
    public BucketLocation getLocation() {
        return RemoteYamcsBucket.LOCATION;
    }

    @Override
    public Spec getSpec() {
        var bucketSpec = new Spec();
        bucketSpec.addOption("name", OptionType.STRING).withRequired(true);
        bucketSpec.addOption("localName", OptionType.STRING);

        var spec = new Spec();
        spec.addOption("yamcsUrl", OptionType.STRING).withRequired(true);
        spec.addOption("username", OptionType.STRING);
        spec.addOption("password", OptionType.STRING).withSecret(true);
        spec.addOption("verifyTls", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("buckets", OptionType.LIST)
                .withRequired(true)
                .withElementType(OptionType.MAP)
                .withSpec(bucketSpec);
        spec.requireTogether("username", "password");
        return spec;
    }

    @Override
    public List<Bucket> loadBuckets(YConfiguration config) throws IOException {
        var client = YamcsClient.newBuilder(config.getString("yamcsUrl"))
                .withVerifyTls(config.getBoolean("verifyTls"));
        if (config.containsKey("username")) {
            var username = config.getString("username");
            var password = config.getString("password").toCharArray();
            client.withCredentials(new BasicAuthCredentials(username, password));
        }

        storageClient = client.build().createStorageClient();

        var buckets = new ArrayList<Bucket>();
        for (var bucketConfig : config.getConfigList("buckets")) {
            var remoteBucketName = bucketConfig.getString("name");

            var localBucketName = remoteBucketName;
            if (bucketConfig.containsKey("localName")) {
                localBucketName = bucketConfig.getString("localName");
            }

            var bucketClient = storageClient.getBucket(remoteBucketName);
            buckets.add(new RemoteYamcsBucket(localBucketName, bucketClient));
        }
        return buckets;
    }
}
