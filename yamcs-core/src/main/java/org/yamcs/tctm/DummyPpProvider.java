package org.yamcs.tctm;

import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.ParameterListener;
import org.yamcs.ParameterProvider;
import org.yamcs.ppdb.PpDbFactory;
import org.yamcs.ppdb.PpDefDb;

import com.google.common.util.concurrent.AbstractService;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;


/**
 * This class is used by the data sessions that can not provide processed parameters (like tcp or pacts file replay), 
 *     in order for the subscribe requests not to fail.
 * It basically accepts any request for the processed parmeters defined in the MDB but it never sends
 * an update for any of them.
 * @author nm
 *
 */
public class DummyPpProvider extends AbstractService implements ParameterProvider {
	private volatile boolean disabled=false;
	final PpDefDb ppdb;
	
	public DummyPpProvider(String instance, String spec) throws ConfigurationException  {
	    ppdb=PpDbFactory.getInstance(instance);
	}
	
	public DummyPpProvider(PpDefDb ppdb) {
	    this.ppdb=ppdb;
	}
	
	@Override
    public void setParameterListener(ParameterListener parameterRequestManager) {
	}

	@Override
    public void startProviding(Parameter paramDef) {
	}
	@Override
    public void startProvidingAll() {
    }
	@Override
    public void stopProviding(Parameter paramDef) {
	}
	
	@Override
    public boolean canProvide(NamedObjectId id) {
	    if(ppdb.getProcessedParameter(id)!=null) return true;
	    else return false;
	}

	public String getDownlinkStatus() {
		return disabled ? "DISABLED" : "OK";
	}
	
	@Override
    public Parameter getParameter(NamedObjectId id) throws InvalidIdentification {
	    Parameter p=ppdb.getProcessedParameter(id);
	    if(p==null) throw new InvalidIdentification();
	    else return p;
	}

	public String getDetailedStatus() {
		return "unused";
	}

	@Override
	protected void doStart() {
		notifyStarted();
	}

	@Override
	protected void doStop() {
		notifyStopped();
	}

    
}

