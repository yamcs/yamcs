package org.yamcs.server;

import java.util.Map;

import org.yamcs.utils.DeprecationInfo;

@Deprecated
@DeprecationInfo(info = "Use org.yamcs.ProcessRunner instead")
public class ProcessRunner extends org.yamcs.ProcessRunner {

    public ProcessRunner(Map<String, Object> args) {
        super(args);
    }
}
