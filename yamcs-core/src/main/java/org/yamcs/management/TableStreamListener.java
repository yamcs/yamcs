package org.yamcs.management;

import org.yamcs.protobuf.Table.StreamInfo;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;

/**
 * Implement and subscribe to the {@link ManagementService} to know when new tables or streams are created/removed
 * 
 * @author nm
 *
 */
public interface TableStreamListener {

    default void streamRegistered(String instance, Stream stream) {
    }

    default void streamUnregistered(String instance, String name) {
    }

    default void streamUpdated(String instance, StreamInfo stream) {
    }

    default void tableRegistered(String instance, TableDefinition tblDef) {
    }

    default void tableUnregistered(String instance, String tblName) {
    }
}
