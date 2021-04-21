package org.yamcs.client.base;

import org.yamcs.api.Observer;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

// Some tricks to unpack data from the Any type without using descriptors
// Only implementing the getMessageClass() is required -- which is already annoying enough.
public interface DataObserver<T extends Message> extends Observer<T> {

    Class<T> getMessageClass();

    default void unpackNext(Any data) throws InvalidProtocolBufferException {
        next(data.unpack(getMessageClass()));
    }

    /**
     * Called when the server has confirmed this call.
     */
    void confirm();
}
