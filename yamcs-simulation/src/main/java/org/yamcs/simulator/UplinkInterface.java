package org.yamcs.simulator;


interface UplinkInterface
{
	public boolean haveAOS();
	public void sendMessageAndIgnoreLOS(PayloadInterface payload);
	public void sendMessage(PayloadInterface payload);
	public void sendMessage(PayloadInterface payload, int compid);
}
