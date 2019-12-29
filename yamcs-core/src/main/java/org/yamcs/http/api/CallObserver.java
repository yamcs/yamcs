package org.yamcs.http.api;

import org.yamcs.NotThreadSafe;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.utils.ExceptionUtil;

import com.google.protobuf.Empty;
import com.google.protobuf.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Observes the state of a single RPC call where both request and response are non-streaming.
 */
@NotThreadSafe
public class CallObserver implements Observer<Message> {

    private Context ctx;

    private boolean completed;

    public CallObserver(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void next(Message message) {
        if (message instanceof Empty) {
            RestHandler.completeOK(ctx);
        } else if (message instanceof HttpBody) {
            HttpBody responseBody = (HttpBody) message;
            ByteBuf buf = Unpooled.wrappedBuffer(responseBody.getData().toByteArray());
            RestHandler.completeOK(ctx, responseBody.getContentType(), buf);
        } else {
            RestHandler.completeOK(ctx, message);
        }
    }

    @Override
    public void completeExceptionally(Throwable t) {
        if (completed) {
            throw new IllegalStateException("Observer already completed");
        }
        completed = true;

        t = ExceptionUtil.unwind(t);
        if (t instanceof HttpException) {
            RestHandler.completeWithError(ctx, (HttpException) t);
        } else {
            HttpException httpException = new InternalServerErrorException(t);
            RestHandler.completeWithError(ctx, httpException);
        }
    }

    @Override
    public void complete() {
        if (completed) {
            throw new IllegalStateException("Observer already completed");
        }
        completed = true;
    }
}
