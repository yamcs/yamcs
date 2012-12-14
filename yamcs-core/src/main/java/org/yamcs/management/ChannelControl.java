package org.yamcs.management;


public interface ChannelControl {
	public String getName();
	public String getType();
	public String getCreator();
	public boolean isReplay();
	String getReplayState();
}
