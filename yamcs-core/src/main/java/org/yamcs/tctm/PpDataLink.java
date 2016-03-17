package org.yamcs.tctm;

import com.google.common.util.concurrent.Service;

/**
 * Interface for components providing parameters aquired from external systems.
 * @author nm
 *
 */
public interface PpDataLink extends Service, Link {
	public void setPpListener(PpListener ppListener);
}
