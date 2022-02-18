package org.yamcs.yarch;

import java.io.IOException;

import com.google.protobuf.Message;

/**
 * A database that allows to store and retrieve protobuf messages.
 */
public interface ProtobufDatabase {

    /**
     * Save a protobuf message. If a message is already saved under the given id, it is overwritten.
     * 
     * @param id
     *            the identifier
     * @param message
     *            the protobuf message
     */
    void save(String id, Message message) throws IOException;

    /**
     * Retrieves a protobuf message for a specific id.
     * 
     * @param id
     *            the identifier to search for
     * @param messageClass
     *            the expected message class
     * 
     * @return the found message, or {@code null}
     */
    <T extends Message> T get(String id, Class<T> messageClass) throws IOException;

    /**
     * Delete a protobuf message for a specific id.
     * 
     * @param id
     *            the identifier to search for
     * 
     * @return whether a record was found and deleted.
     */
    boolean delete(String id) throws IOException;
}
