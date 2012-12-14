package org.yamcs.management;


public interface LinkControl {
    /**
	 * @return more extended status info (displayed by the yamcs monitor)
	 */
	public String getDetailedStatus();

	
	public String getType();	    
	
	public boolean isDisabled();
	/**
	 * Disable the receiving of telemetry. Any connection to a server is closed. Can be reenabled using the enable method.
	 */
	public void disable();
	
	/**
	 * Re-enable the receiving of telemetry if disabled by the disable() method. 
	 */
	public void enable();
	
	
}
