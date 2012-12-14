package org.yamcs;

public class InvalidRequestIdentification extends RuntimeException {
	public int subscriptionId;
	
	public InvalidRequestIdentification(String string, int subscriptionId) {
		super(string);
		this.subscriptionId=subscriptionId;
	}

}
