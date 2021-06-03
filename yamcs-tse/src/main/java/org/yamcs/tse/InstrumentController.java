package org.yamcs.tse;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.yamcs.tse.api.TseCommand;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Guarantees instruments are used by only one thread at a time, and establishes/closes device connections as-needed.
 */
public class InstrumentController extends AbstractService {

    private List<InstrumentDriver> instruments = new ArrayList<>();
    private Map<String, ListeningExecutorService> executorsByName = new HashMap<>();

    public void addInstrument(InstrumentDriver instrument) {
        instruments.add(instrument);
    }

    @Override
    protected void doStart() {
        for (InstrumentDriver instrument : instruments) {
            executorsByName.put(instrument.getName(), listeningDecorator(Executors.newSingleThreadExecutor()));
        }
        notifyStarted();
    }

    public ListenableFuture<List<String>> queueCommand(InstrumentDriver instrument, TseCommand metadata, String command,
            boolean expectResponse) {
        ListeningExecutorService exec = executorsByName.get(instrument.getName());
        return exec.submit(() -> instrument.command(command, metadata, expectResponse));
    }

    public InstrumentDriver getInstrument(String name) {
        for (InstrumentDriver instrument : instruments) {
            if (name.equals(instrument.getName())) {
                return instrument;
            }
        }
        return null;
    }

    public List<InstrumentDriver> getInstruments() {
        return instruments.stream()
                .sorted((i1, i2) -> i1.instrument.compareTo(i2.instrument))
                .collect(Collectors.toList());
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
        }, MoreExecutors.directExecutor());
    }
}
