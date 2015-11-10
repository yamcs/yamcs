package org.yamcs.web.rest;


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
    public static void replaySynchronously(RestRequest req, ReplayRequest replayRequest, ReplayListener l) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        ReplayServer replayServer = getReplayServer(instance);
        
        Semaphore semaphore = new Semaphore(0);
        ReplayWrapper wrapper = new ReplayWrapper(semaphore, l);
        try {
             YarchReplay yarchReplay = replayServer.createReplay(replayRequest, wrapper, req.authToken);
             yarchReplay.start();
        } catch (YamcsException e) {
            throw new InternalServerErrorException("Exception creating the replay", e);
        }
        
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new InternalServerErrorException(e);
        }
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
        ReplayListener wrappedListener;
        
        ReplayWrapper(Semaphore semaphore, ReplayListener l) {
            this.semaphore = semaphore;
            this.wrappedListener = l;
        }
        
        @Override
        public void newData(ProtoDataType type, MessageLite data) {
            wrappedListener.newData(type, data);
        }

        @Override
        public void stateChanged(ReplayStatus rs) {
            if(rs.getState()==ReplayState.CLOSED) {
                semaphore.release();
            } else if (rs.getState() == ReplayState.ERROR) {
                log.error("Replay errored");
                semaphore.release();
            }
            semaphore.release();
            wrappedListener.stateChanged(rs);
        }
    }
}
