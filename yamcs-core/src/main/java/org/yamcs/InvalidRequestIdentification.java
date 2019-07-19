package org.yamcs;

@SuppressWarnings("serial")
public class InvalidRequestIdentification extends RuntimeException {

    public int subscriptionId;

    public InvalidRequestIdentification(String string, int subscriptionId) {
        super(string);
        this.subscriptionId = subscriptionId;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }
}
