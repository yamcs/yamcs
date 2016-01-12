package org.yamcs.web.rest;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class RestStreams {
    
    private static AtomicInteger streamCounter = new AtomicInteger();
    private static final Logger log = LoggerFactory.getLogger(RestStreams.class);
    
    public static void streamAndWait(String instance, String selectSql, RestStreamSubscriber s) throws HttpException {
        stream(instance, selectSql, s).await();
    }
    
    public static StreamSubscriberWrapper stream(String instance, String selectSql, RestStreamSubscriber s) throws HttpException {
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        
        String streamName = "rest_archive" + streamCounter.incrementAndGet();
        String sql = new StringBuilder("create stream ")
                .append(streamName)
                .append(" as ")
                .append(selectSql)
                .append(" nofollow")
                .toString();
        
        log.debug("Executing: {}", sql);
        try {
            ydb.execute(sql);
        } catch (StreamSqlException | ParseException e) {
            throw new InternalServerErrorException(e);
        }
        
        StreamSubscriberWrapper wrapper = new StreamSubscriberWrapper(s);
        
        Stream stream = ydb.getStream(streamName);
        stream.addSubscriber(wrapper);
        stream.start();
        return wrapper;
    }
    
    private static class StreamSubscriberWrapper implements StreamSubscriber {
        Semaphore semaphore;
        StreamSubscriber wrappedSubscriber;
        
        public StreamSubscriberWrapper(StreamSubscriber s) {
            this.wrappedSubscriber = s;
            semaphore = new Semaphore(0);
        }
        
        public void await() throws InternalServerErrorException {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new InternalServerErrorException(e);
            }
        }

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            wrappedSubscriber.onTuple(stream, tuple);
        }

        @Override
        public void streamClosed(Stream stream) {
            semaphore.release();
            wrappedSubscriber.streamClosed(stream);
        }
    }
}
