package org.yamcs;

import org.yamcs.tctm.SimpleTcTmService;

public class RefMdbTmService extends SimpleTcTmService {
    public RefMdbTmService(RefMdbPacketGenerator tmGenerator) throws ConfigurationException {
        super(tmGenerator, null , null);
    }
}