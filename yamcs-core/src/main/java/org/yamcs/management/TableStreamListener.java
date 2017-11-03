package org.yamcs.management;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;

/**
 * Implement and subscribe to the {@link ManagementService} to know when new tables or streams are created/removed
 * @author nm
 *
 */
public interface TableStreamListener {
    default void tableRegistered(String instance, TableDefinition tblDef) {}
    default void streamRegistered(String instance, Stream stream) {}
    default void tableUnregistered(String instance, String tblName) {}
    default void streamUnregistered(String instance, String name) {}
}
