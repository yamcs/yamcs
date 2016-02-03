package org.yamcs.web.rest.archive;


import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ProcessorFactory;
import org.yamcs.YProcessor;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestReplayListener;



/**
 * Abstracts some common logic for creating replays 
 */
public class RestReplays {
    static AtomicInteger count = new AtomicInteger();
    
    private static final Logger log = LoggerFactory.getLogger(RestReplays.class);
    
    /**
     * launches a replay will only return when the replay is done (either
     * through success or through error)
     * 
     * TODO we should be more helpful here with catching errored state and
     * throwing it up as RestException
     */
    public static ReplayWrapper replay(String instance, AuthenticationToken token, ReplayRequest replayRequest, RestReplayListener l) throws HttpException {
        try {
            YProcessor yproc = ProcessorFactory.create(instance, "RestReplays"+count.incrementAndGet(), "ArchiveRetrieval", "internal", replayRequest);
            ReplayWrapper wrapper = new ReplayWrapper(l, yproc);
        
            ParameterWithIdRequestHelper pidrm = new ParameterWithIdRequestHelper(yproc.getParameterRequestManager(), wrapper);
            pidrm.addRequest(replayRequest.getParameterRequest().getNameFilterList(), token);
            yproc.startAsync();
            
            return wrapper;
            
        } catch (Exception e) {
            throw new InternalServerErrorException("Exception creating the replay", e);
        }
       
        
      
    }
    
    public static void replayAndWait(String instance, AuthenticationToken token, ReplayRequest replayRequest, RestReplayListener l) throws HttpException {
        replay(instance, token, replayRequest, l).await();
    }
    
    
    private static class ReplayWrapper implements ParameterWithIdConsumer {
        RestReplayListener wrappedListener;
        YProcessor yproc;
        
        ReplayWrapper(RestReplayListener l, YProcessor yproc) {
            this.wrappedListener = l;
            this.yproc = yproc;
        }
        
        
        public void await() throws InternalServerErrorException {
           yproc.awaitTerminated();
        }
        

        @Override
        public void update(int subscriptionId, List<ParameterValueWithId> params) {
            if (!wrappedListener.isReplayAbortRequested()) {
                wrappedListener.onParameterData(params);;
            } else {
                yproc.quit();
            }
        }
    }
}
