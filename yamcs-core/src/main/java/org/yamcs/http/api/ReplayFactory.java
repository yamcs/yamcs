package org.yamcs.http.api;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.archive.ReplayOptions;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.ServiceUnavailableException;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.security.User;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service.Listener;
import com.google.common.util.concurrent.Service.State;

/**
 * Abstracts some common logic for creating replays
 */
public class ReplayFactory {

    static AtomicInteger count = new AtomicInteger();
    private static int MAX_CONCURRENT_REPLAYS = 2 * Runtime.getRuntime().availableProcessors();
    static AtomicInteger concurrentCount = new AtomicInteger();

    /**
     * launches a replay will only return when the replay is done (either through success or through error)
     */
    public static ReplayWrapper replay(String instance, User user, ReplayOptions replayRequest,
            ParameterReplayListener l) {
        int n = concurrentCount.incrementAndGet();

        if (n > MAX_CONCURRENT_REPLAYS) {
            concurrentCount.decrementAndGet();
            throw new ServiceUnavailableException("Maximum number of concurrent replays has been reached");
        }

        try {
            Processor processor = ProcessorFactory.create(instance, "api_replay" + count.incrementAndGet(),
                    "ArchiveRetrieval", "internal", replayRequest);
            ReplayWrapper wrapper = new ReplayWrapper(l, processor);

            ParameterWithIdRequestHelper pidrm = new ParameterWithIdRequestHelper(
                    processor.getParameterRequestManager(),
                    wrapper);
            pidrm.addRequest(replayRequest.getParameterRequest().getNameFilterList(), user);
            processor.startAsync();
            processor.addListener(new Listener() {
                @Override
                public void terminated(State from) {
                    concurrentCount.decrementAndGet();
                }

                @Override
                public void failed(State from, Throwable failure) {
                    concurrentCount.decrementAndGet();
                }
            }, MoreExecutors.directExecutor());

            return wrapper;

        } catch (Exception e) {
            throw new InternalServerErrorException("Exception creating the replay", e);
        }
    }

    private static class ReplayWrapper implements ParameterWithIdConsumer {
        ParameterReplayListener wrappedListener;
        Processor processor;

        ReplayWrapper(ParameterReplayListener l, Processor processor) {
            this.wrappedListener = l;
            this.processor = processor;
            processor.addListener(l, MoreExecutors.directExecutor());
        }

        @Override
        public void update(int subscriptionId, List<ParameterValueWithId> params) {
            if (!wrappedListener.isReplayAbortRequested()) {
                wrappedListener.update(subscriptionId, params);
            } else {
                processor.quit();
            }
        }
    }
}
