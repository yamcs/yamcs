package org.yamcs.simulator;


public interface UplinkInterface {
	boolean haveAOS();
	void sendMessageAndIgnoreLOS(PayloadInterface payload);
	void sendMessage(PayloadInterface payload);
	void sendMessage(PayloadInterface payload, int compid);
}
