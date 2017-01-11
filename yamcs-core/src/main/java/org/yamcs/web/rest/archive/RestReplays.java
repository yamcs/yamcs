package org.yamcs.web.rest.archive;


import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ProcessorFactory;
import org.yamcs.YConfiguration;
import org.yamcs.YProcessor;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.ServiceUnavailableException;
import org.yamcs.web.rest.RestReplayListener;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service.Listener;
import com.google.common.util.concurrent.Service.State;


/**
 * Abstracts some common logic for creating replays 
 */
public class RestReplays {
    static AtomicInteger count = new AtomicInteger();
    private static int MAX_CONCURRENT_REPLAYS = YConfiguration.getConfiguration("yamcs").getInt("WebConfig", "maxConcurrentReplays", 2*Runtime.getRuntime().availableProcessors());
    static AtomicInteger concurrentCount = new AtomicInteger();
    
    /**
     * launches a replay will only return when the replay is done (either
     * through success or through error)
     * 
     * TODO we should be more helpful here with catching errored state and
     * throwing it up as RestException
     */
    public static ReplayWrapper replay(String instance, AuthenticationToken token, ReplayRequest replayRequest, RestReplayListener l) throws HttpException {
        int n = concurrentCount.incrementAndGet();
       
        if(n>MAX_CONCURRENT_REPLAYS) {
            concurrentCount.decrementAndGet();
            throw new ServiceUnavailableException("Maximum number of concurrent replays has been reached");
        }
        
        try {
            YProcessor yproc = ProcessorFactory.create(instance, "RestReplays"+count.incrementAndGet(), "ArchiveRetrieval", "internal", replayRequest);
            ReplayWrapper wrapper = new ReplayWrapper(l, yproc);
        
            ParameterWithIdRequestHelper pidrm = new ParameterWithIdRequestHelper(yproc.getParameterRequestManager(), wrapper);
            pidrm.addRequest(replayRequest.getParameterRequest().getNameFilterList(), token);
            yproc.startAsync();
            yproc.addListener(new Listener() {
                public void terminated(State from){concurrentCount.decrementAndGet();}
                public void failed(State from, Throwable failure) {concurrentCount.decrementAndGet();}                    
            }, MoreExecutors.directExecutor());
            
            return wrapper;
            
        } catch (Exception e) {
            throw new InternalServerErrorException("Exception creating the replay", e);
        }
    }
    
    
    private static class ReplayWrapper implements ParameterWithIdConsumer {
        RestReplayListener wrappedListener;
        YProcessor yproc;
        
        ReplayWrapper(RestReplayListener l, YProcessor yproc) {
            this.wrappedListener = l;
            this.yproc = yproc;
            yproc.addListener(l, MoreExecutors.directExecutor());
        }
        
        
        @Override
        public void update(int subscriptionId, List<ParameterValueWithId> params) {
            if (!wrappedListener.isReplayAbortRequested()) {
                wrappedListener.update(subscriptionId, params);
            } else {
                yproc.quit();
            }
        }
    }
}
