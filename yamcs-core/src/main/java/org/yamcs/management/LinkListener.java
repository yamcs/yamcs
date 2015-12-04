package org.yamcs.management;

import org.yamcs.protobuf.YamcsManagement.LinkInfo;

/**
 * Used by ManagementService to distribute data link related updates
 */
public interface LinkListener {
    
    void registerLink(LinkInfo linkInfo);
    
    void unregisterLink(String instance, String name);
    
    void linkChanged(LinkInfo linkInfo);
}
