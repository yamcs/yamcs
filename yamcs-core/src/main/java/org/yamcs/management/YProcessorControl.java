package org.yamcs.management;


public interface YProcessorControl {
	public String getName();
	public String getType();
	public String getCreator();
	public boolean isReplay();
	String getReplayState();
}
