package org.yamcs.management;

import org.yamcs.protobuf.links.LinkInfo;

/**
 * Used by LinkManager to distribute data link related updates
 */
public interface LinkListener {

    void linkRegistered(LinkInfo linkInfo);

    void linkUnregistered(LinkInfo linkInfo);
}
