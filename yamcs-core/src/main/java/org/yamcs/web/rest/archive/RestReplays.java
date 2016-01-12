package org.yamcs.web.rest.archive;


import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.archive.ReplayListener;
import org.yamcs.archive.ReplayServer;
import org.yamcs.archive.YarchReplay;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestReplayListener;

import com.google.protobuf.MessageLite;


/**
 * Abstracts some common logic for creating replays 
 */
public class RestReplays {
    
    private static final Logger log = LoggerFactory.getLogger(RestReplays.class);
    
    /**
     * launches a replay will only return when the replay is done (either
     * through success or through error)
     * 
     * TODO we should be more helpful here with catching errored state and
     * throwing it up as RestException
     */
    public static ReplayWrapper replay(String instance, AuthenticationToken token, ReplayRequest replayRequest, RestReplayListener l) throws HttpException {
        ReplayServer replayServer = getReplayServer(instance);
        
        ReplayWrapper wrapper = new ReplayWrapper(l);
        try {
             YarchReplay yarchReplay = replayServer.createReplay(replayRequest, wrapper, token);
             wrapper.setYarchReplay(yarchReplay);
             yarchReplay.start();
             return wrapper;
        } catch (YamcsException e) {
            throw new InternalServerErrorException("Exception creating the replay", e);
        }
    }
    
    public static void replayAndWait(String instance, AuthenticationToken token, ReplayRequest replayRequest, RestReplayListener l) throws HttpException {
        replay(instance, token, replayRequest, l).await();
    }
    
    private static ReplayServer getReplayServer(String instance) throws BadRequestException {
        ReplayServer replayServer = YamcsServer.getService(instance, ReplayServer.class);
        if (replayServer == null) {
            throw new BadRequestException("ReplayServer not configured for this instance");
        }
        return replayServer;
    }
    
    private static class ReplayWrapper implements ReplayListener {
        Semaphore semaphore;
        RestReplayListener wrappedListener;
        YarchReplay yarchReplay;
        
        ReplayWrapper(RestReplayListener l) {
            this.wrappedListener = l;
            semaphore = new Semaphore(0);
        }
        
        void setYarchReplay(YarchReplay yarchReplay) {
            this.yarchReplay = yarchReplay;
        }
        
        public void await() throws InternalServerErrorException {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new InternalServerErrorException(e);
            }
        }
        
        @Override
        public void newData(ProtoDataType type, MessageLite data) {
            if (!wrappedListener.isReplayAbortRequested()) {
                wrappedListener.newData(type, data);
            } else {
                yarchReplay.quit();
                // Call explicitely, because doesn't happen after above quit()
                stateChanged(ReplayStatus.newBuilder().setState(ReplayState.CLOSED).build());
            }
        }

        @Override
        public void stateChanged(ReplayStatus rs) {
            if (rs.getState() == ReplayState.ERROR) {
                log.error("Replay errored");
            }
            semaphore.release();
            wrappedListener.stateChanged(rs);
        }
    }
}
