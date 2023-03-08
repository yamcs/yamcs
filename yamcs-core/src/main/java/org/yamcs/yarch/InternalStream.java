package org.yamcs.yarch;

/**
 * Stream created by the "create stream statement"
 * <p>
 * It has an execution context associated and is responsible for closing it when the stream closes.
 * 
 */
public class InternalStream extends Stream implements StreamSubscriber {
    final ExecutionContext ctx;
    Stream inner;

    public InternalStream(ExecutionContext ctx, String name, TupleDefinition definition) {
        super(ctx.getDb(), name, definition);
        this.ctx = ctx;
    }

    public void setInner(Stream inner) {
        this.inner = inner;
        inner.addSubscriber(this);
    }

    @Override
    protected void doClose() {
        if (inner != null) {
            inner.close();
        }
        ctx.close();
    }

    @Override
    public void doStart() {
        if (inner != null) {
            inner.start();
        }
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        emitTuple(tuple);
    }



    @Override
    public void streamClosed(Stream stream) {
        if (stream == inner) {
            close();
        }
    }
}
