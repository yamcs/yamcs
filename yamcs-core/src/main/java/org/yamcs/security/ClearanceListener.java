package org.yamcs.security;

import org.yamcs.security.protobuf.Clearance;

@FunctionalInterface
public interface ClearanceListener {

    void onChange(Clearance clearance);
}
