package org.yamcs.tse.commander;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * Guarantees devices are used by only one thread at a time, and establishes/closes device connections as-needed.
 */
public class DeviceManager extends AbstractService {

    private List<Device> devices = new ArrayList<>();
    private Map<String, ListeningExecutorService> executorsById = new HashMap<>();

    public void add(Device device) {
        devices.add(device);
    }

    @Override
    protected void doStart() {
        for (Device device : devices) {
            executorsById.put(device.getId(), listeningDecorator(Executors.newSingleThreadExecutor()));
        }
        notifyStarted();
    }

    public ListenableFuture<String> queueCommand(Device device, String command) {
        ListeningExecutorService exec = executorsById.get(device.getId());
        return exec.submit(() -> {
            device.connect();
            return device.command(command);
        });
    }

    public Device getDevice(String id) {
        for (Device device : devices) {
            if (id.equals(device.getId())) {
                return device;
            }
        }
        return null;
    }

    public List<Device> getDevices() {
        return devices;
    }

    @Override
    protected void doStop() {
        ListeningExecutorService closers = listeningDecorator(Executors.newCachedThreadPool());

        List<ListenableFuture<?>> closeFutures = new ArrayList<>();
        for (ExecutorService exec : executorsById.values()) {
            closeFutures.add(closers.submit(() -> {
                exec.shutdown();
                return exec.awaitTermination(10, TimeUnit.SECONDS);
            }));
        }
        executorsById.clear();

        closers.shutdown();
        Futures.addCallback(Futures.allAsList(closeFutures), new FutureCallback<Object>() {

            @Override
            public void onSuccess(Object result) {
                notifyStopped();
            }

            @Override
            public void onFailure(Throwable t) {
                notifyFailed(t);
            }
        });
    }
}
