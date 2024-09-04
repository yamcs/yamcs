package org.yamcs.http.api;

import static org.yamcs.StandardTupleDefinitions.BODY_COLUMN;

import org.yamcs.api.Observer;
import org.yamcs.archive.EventRecorder;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.protobuf.Db;

public class SubscribeEventsObserver implements Observer<SubscribeEventsRequest>, StreamSubscriber {

    private Observer<Event> responseObserver;

    private Stream stream;

    private EventFilter filter;

    public SubscribeEventsObserver(Observer<Event> responseObserver) {
        this.responseObserver = responseObserver;
    }

    @Override
    public void next(SubscribeEventsRequest request) {
        if (stream != null) {
            stream.removeSubscriber(this);
            stream = null;
        }

        filter = request.hasFilter()
                ? EventFilterFactory.create(request.getFilter())
                : null;

        var instance = InstancesApi.verifyInstance(request.getInstance());
        var ydb = YarchDatabase.getInstance(instance);
        stream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        if (stream == null) {
            return; // No error, just don't send data
        }

        stream.addSubscriber(this);
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        if (filter != null && !filter.matches(tuple)) {
            return;
        }

        var event = (Db.Event) tuple.getColumn(BODY_COLUMN);
        responseObserver.next(EventsApi.fromDbEvent(event));
    }

    @Override
    public void streamClosed(Stream stream) {
        // Ignore
    }

    @Override
    public void completeExceptionally(Throwable t) {
        if (stream != null) {
            stream.removeSubscriber(this);
        }
    }

    @Override
    public void complete() {
        if (stream != null) {
            stream.removeSubscriber(this);
        }
    }
}
