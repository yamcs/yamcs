package org.yamcs.tctm;

import org.yamcs.TmProcessor;

import com.google.common.util.concurrent.AbstractService;

/**
 * Tm provider that doesn't provide any telemetry. Used for the "lounge" type.
 * @author nm
 *
 */
public class DummyTmProvider extends AbstractService implements TmPacketProvider {
	private volatile boolean disabled=false;
	private TmProcessor tmProcessor;
	
	
	public DummyTmProvider(String instance, String spec) {
	}

	@Override
    public void disable() {
		disabled=true;
	}

	@Override
    public void enable() {
		disabled=false;
	}
	@Override
    public boolean isArchiveReplay() {
        return false;
    }
	@Override
    public String getDetailedStatus() {
//		return "Please sit down. While you are here, nobody will bother you with any data so you can relax and enjoy your coffee"; 
		return "This channel is used while waiting for something more interesting to be created"; 
	}

	@Override
    public String getLinkStatus() {
		return disabled ? "DISABLED" : "OK";
	}

	@Override
    public String getTmMode() {
		return "RT_NORM";
	}

	@Override
    public boolean isDisabled() {
		return disabled;
	}

	@Override
    public void doStop() {
	    tmProcessor.finished();
	    notifyStopped();
	}

    @Override
    public void setTmProcessor(TmProcessor tmProcessor) {
        this.tmProcessor=tmProcessor;
        
    }

	@Override
	protected void doStart() {
	    notifyStarted();
	}

    @Override
    public long getDataCount() {
        return 0;
    }

}
