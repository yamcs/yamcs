package org.yamcs.tctm;

import com.google.common.util.concurrent.Service;

/**
 * Provides DaSS Processed Parameters. Currently the DaSS PPs are received and stored by yamcs as they are.
 * TODO: This should be moved to the yamcs-dass module once there is a yamcs own parameter recording
 * @author nm
 *
 */
public interface PpProvider extends Service, Link {
	public void setPpListener(PpListener ppListener);
}
