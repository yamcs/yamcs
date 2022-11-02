package org.yamcs.tests;

import org.junit.jupiter.api.Test;
import org.yamcs.client.TimeSubscription;
import org.yamcs.protobuf.SubscribeTimeRequest;

import com.google.protobuf.Timestamp;

public class TimeSubscriptionTest extends AbstractIntegrationTest {

    @Test
    public void testSimpleSubscription() throws Exception {
        TimeSubscription subscription = yamcsClient.createTimeSubscription();
        MessageCaptor<Timestamp> captor = MessageCaptor.of(subscription);

        SubscribeTimeRequest request = SubscribeTimeRequest.newBuilder()
                .setInstance(yamcsInstance)
                .build();
        subscription.sendMessage(request);
        captor.expectTimely();
    }
}
