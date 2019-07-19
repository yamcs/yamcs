package org.yamcs;

import org.yamcs.utils.DeprecationInfo;

@Deprecated
@DeprecationInfo(info = "Services must extend org.yamcs.api.YamcsService")
public interface YamcsService extends org.yamcs.api.YamcsService {
}
