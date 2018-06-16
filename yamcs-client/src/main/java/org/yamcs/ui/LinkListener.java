package org.yamcs.ui;

import org.yamcs.protobuf.YamcsManagement.LinkInfo;

public interface LinkListener {
    public void log(String message);
    public void updateLink(LinkInfo li);
}
