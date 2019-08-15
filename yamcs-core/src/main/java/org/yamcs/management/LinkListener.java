package org.yamcs.management;

import org.yamcs.protobuf.LinkInfo;

/**
 * Used by ManagementService to distribute data link related updates
 */
public interface LinkListener {

    void linkRegistered(LinkInfo linkInfo);

    void linkUnregistered(LinkInfo linkInfo);

    void linkChanged(LinkInfo linkInfo);
}
