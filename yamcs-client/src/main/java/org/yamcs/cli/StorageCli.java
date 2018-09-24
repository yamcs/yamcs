package org.yamcs.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import org.yamcs.api.InstanceClient;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.protobuf.Rest.BucketInfo;
import org.yamcs.protobuf.Rest.CreateBucketRequest;
import org.yamcs.protobuf.Rest.ObjectInfo;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Manage object storage")
public class StorageCli extends Command {

    public StorageCli(Command parent) {
        super("storage", parent);
        addSubCommand(new StorageCat());
        addSubCommand(new StorageList());
        addSubCommand(new StorageMakeBuckets());
        addSubCommand(new StorageRemoveBuckets());
        setInstanceRequired(true);
    }

    @Parameters(commandDescription = "List buckets or objects")
    class StorageList extends Command {

        @Parameter(required = false, description = "bucket", arity = 1)
        List<String> main = new ArrayList<>();

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public StorageList() {
            super("list", StorageCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);

            if (global) {
                if (main.isEmpty()) {
                    yamcsClient.getBuckets().thenAccept(response -> {
                        for (BucketInfo bucket : response.getBucketList()) {
                            console.println(bucket.getName());
                        }
                    }).get();
                } else {
                    String bucket = main.get(0);
                    yamcsClient.getObjects(bucket).thenAccept(response -> {
                        for (ObjectInfo object : response.getObjectList()) {
                            console.println(object.getName());
                        }
                    }).get();
                }
            } else {
                InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                if (main.isEmpty()) {
                    instanceClient.getBuckets().thenAccept(response -> {
                        for (BucketInfo bucket : response.getBucketList()) {
                            console.println(bucket.getName());
                        }
                    }).get();
                } else {
                    String bucket = main.get(0);
                    instanceClient.getObjects(bucket).thenAccept(response -> {
                        for (ObjectInfo object : response.getObjectList()) {
                            console.println(object.getName());
                        }
                    }).get();
                }
            }
        }
    }

    @Parameters(commandDescription = "Concatenate object content to stdout")
    class StorageCat extends Command {

        @Parameter(required = true, description = "bucket object", arity = 2)
        List<String> main = new ArrayList<>();

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public StorageCat() {
            super("cat", StorageCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            String bucket = main.get(0);
            String object = main.get(1);

            if (global) {
                yamcsClient.getObject(bucket, object).thenAccept(response -> {
                    try {
                        System.out.write(response);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }).get();
            } else {
                InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                instanceClient.getObject(bucket, object).thenAccept(response -> {
                    try {
                        System.out.write(response);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }).get();
            }
        }
    }

    @Parameters(commandDescription = "Make buckets")
    class StorageMakeBuckets extends Command {

        @Parameter(required = true, description = "bucket...")
        List<String> main = new ArrayList<>();

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public StorageMakeBuckets() {
            super("mb", StorageCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            for (String bucket : main) {
                CreateBucketRequest req = CreateBucketRequest.newBuilder().setName(bucket).build();
                if (global) {
                    yamcsClient.createBucket(req).get();
                } else {
                    InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                    instanceClient.createBucket(req).get();
                }
            }
        }
    }

    @Parameters(commandDescription = "Remove buckets")
    class StorageRemoveBuckets extends Command {

        @Parameter(required = true, description = "bucket...")
        List<String> main = new ArrayList<>();

        @Parameter(names = { "-g", "--global" }, description = "Do not use instance-specific data")
        boolean global = false;

        public StorageRemoveBuckets() {
            super("rb", StorageCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            YamcsClient yamcsClient = new YamcsClient(ycp);
            for (String bucket : main) {
                if (global) {
                    yamcsClient.deleteBucket(bucket).get();
                } else {
                    InstanceClient instanceClient = yamcsClient.selectInstance(ycp.getInstance());
                    instanceClient.deleteBucket(bucket).get();
                }
            }
        }
    }
}
