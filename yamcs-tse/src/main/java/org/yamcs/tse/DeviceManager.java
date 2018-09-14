package org.yamcs.tse;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

/**
 * Guarantees devices are used by only one thread at a time, and establishes/closes device connections as-needed.
 */
public class DeviceManager extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(DeviceManager.class);

    private List<Device> devices = new ArrayList<>();
    private Map<String, ListeningExecutorService> executorsByName = new HashMap<>();

    private List<ResponseListener> responseListeners = new CopyOnWriteArrayList<>();

    public void addDevice(Device device) {
        devices.add(device);
    }

    public void addResponseListener(ResponseListener responseListener) {
        responseListeners.add(responseListener);
    }

    public void removeResponseListener(ResponseListener responseListener) {
        responseListeners.remove(responseListener);
    }

    @Override
    protected void doStart() {
        for (Device device : devices) {
            executorsByName.put(device.getName(), listeningDecorator(Executors.newSingleThreadExecutor()));
        }
        notifyStarted();
    }

    public ListenableFuture<String> queueCommand(Device device, String command) {
        ListeningExecutorService exec = executorsByName.get(device.getName());
        ListenableFuture<String> f = exec.submit(() -> device.command(command));
        f.addListener(() -> {
            try {
                String response = f.get();
                responseListeners.forEach(l -> l.onResponse(command, response));
            } catch (ExecutionException e) {
                log.error("Failed to execute command", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, directExecutor());
        return f;
    }

    public Device getDevice(String name) {
        for (Device device : devices) {
            if (name.equals(device.getName())) {
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
        for (ExecutorService exec : executorsByName.values()) {
            closeFutures.add(closers.submit(() -> {
                exec.shutdown();
                return exec.awaitTermination(10, TimeUnit.SECONDS);
            }));
        }
        executorsByName.clear();

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
