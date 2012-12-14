package org.yamcs.tctm;

import org.yamcs.ChannelException;
import org.yamcs.ConfigurationException;


public class ArchiveService extends AbstractTcTmService {
	public ArchiveService(String instance, String spec) throws ConfigurationException, ChannelException {
		YamcsCascading tm2 = new YamcsCascading(instance, spec);
		tm = tm2;
		pp = tm2;
	}
    
    @Override
    protected void doStart() {
       tm.start();
    }

    @Override
    protected void doStop() {
        tm.stop();
    }
}
