package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.api.Observer;
import org.yamcs.client.ClientException;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.client.processor.ProcessorClient;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.activities.ActivitiesApiClient;
import org.yamcs.protobuf.activities.ActivityInfo;
import org.yamcs.protobuf.activities.ActivityTriggersApiClient;
import org.yamcs.protobuf.activities.ConfirmTriggerFiringRequest;
import org.yamcs.protobuf.activities.DisableAllTriggersRequest;
import org.yamcs.protobuf.activities.DisableTriggerRequest;
import org.yamcs.protobuf.activities.EnableAllTriggersRequest;
import org.yamcs.protobuf.activities.EnableTriggerRequest;
import org.yamcs.protobuf.activities.GetActivityRequest;
import org.yamcs.protobuf.activities.ListTriggerFiringsRequest;
import org.yamcs.protobuf.activities.ListTriggerFiringsResponse;
import org.yamcs.protobuf.activities.ListTriggersRequest;
import org.yamcs.protobuf.activities.ListTriggersResponse;
import org.yamcs.protobuf.activities.TriggerFiringInfo;
import org.yamcs.protobuf.activities.TriggerFiringOutcome;
import org.yamcs.protobuf.activities.TriggerInfo;
import org.yamcs.protobuf.activities.TriggerServiceStatus;
import org.yamcs.utils.ValueHelper;

public class ActivityTriggerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_SOURCE = "IntegrationTest";

    private ActivityTriggersApiClient triggerClient;
    private ActivitiesApiClient activityClient;
    private ProcessorClient processorClient;

    @BeforeEach
    public void prepare() {
        triggerClient = new ActivityTriggersApiClient(yamcsClient.getMethodHandler());
        activityClient = new ActivitiesApiClient(yamcsClient.getMethodHandler());
        processorClient = yamcsClient.createProcessorClient(yamcsInstance, "realtime");
    }

    /**
     * The triggers are declared in the instance configuration, all disabled. Each test enables the one it needs.
     */
    @AfterEach
    public void cleanup() throws Exception {
        ListTriggersResponse response = call((r, o) -> triggerClient.listTriggers(null,
                ListTriggersRequest.newBuilder().setInstance(yamcsInstance).build(), o));
        for (var trigger : response.getTriggersList()) {
            if (trigger.getEnabled()) {
                this.<TriggerInfo> call((r, o) -> triggerClient.disableTrigger(null, DisableTriggerRequest.newBuilder()
                        .setInstance(yamcsInstance)
                        .setTrigger(trigger.getId())
                        .build(), o));
            }
        }
    }

    @Test
    public void testListConfigTriggers() throws Exception {
        ListTriggersResponse response = call((r, o) -> triggerClient.listTriggers(null,
                ListTriggersRequest.newBuilder().setInstance(yamcsInstance).build(), o));
        var trigger = response.getTriggersList().stream()
                .filter(t -> t.getName().equals("parameter-trigger"))
                .findFirst().orElseThrow();
        assertFalse(trigger.getEnabled());
        assertEquals("/REFMDB/SUBSYS1/LocalPara1 > 10", trigger.getParameterCondition().getExpression());
        assertEquals("MANUAL", trigger.getActivity().getType());
    }

    @Test
    public void testEventAlarmTrigger() throws Exception {
        var trigger = enableTrigger("event-trigger");

        createEvent("Trigger-Test-1a", "warning", "battery low");
        var firings = waitForFirings(trigger.getName(), 1);
        var firing = firings.get(0);
        assertEquals(TriggerFiringOutcome.STARTED, firing.getOutcome());
        assertEquals("battery low", firing.getContext().getFieldsOrThrow("event.message").getStringValue());

        var activity = getActivity(firing.getActivityId());
        assertEquals("Handle battery low (WARNING)", activity.getDetail());
        assertEquals("TRIGGER", activity.getOrigin().getType());
        assertEquals(trigger.getId(), activity.getOrigin().getRef());
        assertEquals("event-trigger", activity.getOrigin().getName());
        assertEquals("admin", activity.getStartedBy());

        // Not matching the subject
        createEvent("Other-Type", "warning", "unrelated");
        // A new alarm with a matching subject fires again
        createEvent("Trigger-Test-1b", "critical", "battery very low");
        firings = waitForFirings(trigger.getName(), 2);
        assertEquals("battery very low", firings.get(0).getContext()
                .getFieldsOrThrow("event.message").getStringValue());
        Thread.sleep(300);
        assertEquals(2, listFirings(trigger.getName()).size());
    }

    @Test
    public void testMinSeverityAndConcurrency() throws Exception {
        var trigger = enableTrigger("severity-trigger");

        createEvent("Trigger-Test-2a", "warning", "not severe enough");
        createEvent("Trigger-Test-2b", "critical", "first");
        createEvent("Trigger-Test-2c", "critical", "second");

        var firings = waitForFirings(trigger.getName(), 2);
        // Most recent first. The manual activity of the first firing is still running.
        assertEquals(TriggerFiringOutcome.SUPPRESSED_CONCURRENCY, firings.get(0).getOutcome());
        assertEquals(TriggerFiringOutcome.STARTED, firings.get(1).getOutcome());
        Thread.sleep(300);
        assertEquals(2, listFirings(trigger.getName()).size());
    }

    @Test
    public void testConfirmation() throws Exception {
        var trigger = enableTrigger("confirm-trigger");

        createEvent("Trigger-Test-3", "warning", "please confirm");
        var firing = waitForFirings(trigger.getName(), 1).get(0);
        assertEquals(TriggerFiringOutcome.PENDING_CONFIRMATION, firing.getOutcome());
        assertFalse(firing.hasActivityId());

        TriggerFiringInfo confirmed = call((r, o) -> triggerClient.confirmTriggerFiring(null,
                ConfirmTriggerFiringRequest.newBuilder()
                        .setInstance(yamcsInstance)
                        .setFiring(firing.getId())
                        .build(),
                o));
        assertEquals(TriggerFiringOutcome.STARTED, confirmed.getOutcome());
        assertEquals("admin", confirmed.getDecidedBy());
        assertTrue(confirmed.hasActivityId());

        // A second confirmation is refused
        try {
            this.<TriggerFiringInfo> call((r, o) -> triggerClient.confirmTriggerFiring(null, ConfirmTriggerFiringRequest.newBuilder()
                    .setInstance(yamcsInstance)
                    .setFiring(firing.getId())
                    .build(), o));
            fail("Expected an exception");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof ClientException);
        }
    }

    @Test
    public void testParameterTrigger() throws Exception {
        var trigger = enableTrigger("parameter-trigger");

        setLocalPara1(5); // initial state: false
        setLocalPara1(20); // rising: fire
        setLocalPara1(30); // unchanged
        setLocalPara1(1); // falling: no fire
        setLocalPara1(15); // rising: fire

        var firings = waitForFirings(trigger.getName(), 2);
        Thread.sleep(300);
        firings = listFirings(trigger.getName());
        assertEquals(2, firings.size());
        assertEquals(TriggerFiringOutcome.STARTED, firings.get(0).getOutcome());
        assertEquals("Value is 15", getActivity(firings.get(0).getActivityId()).getDetail());
        assertEquals("Value is 20", getActivity(firings.get(1).getActivityId()).getDetail());
    }

    @Test
    public void testKillSwitch() throws Exception {
        var trigger = enableTrigger("killswitch-trigger");

        TriggerServiceStatus status = call((r, o) -> triggerClient.disableAllTriggers(null,
                DisableAllTriggersRequest.newBuilder().setInstance(yamcsInstance).build(), o));
        assertFalse(status.getEnabled());
        try {
            createEvent("Trigger-Test-5a", "warning", "ignored");
            Thread.sleep(500);
            assertEquals(0, listFirings(trigger.getName()).size());
        } finally {
            status = call((r, o) -> triggerClient.enableAllTriggers(null,
                    EnableAllTriggersRequest.newBuilder().setInstance(yamcsInstance).build(), o));
            assertTrue(status.getEnabled());
        }

        createEvent("Trigger-Test-5b", "warning", "handled");
        waitForFirings(trigger.getName(), 1);
    }

    private TriggerInfo enableTrigger(String name) throws Exception {
        return call((r, o) -> triggerClient.enableTrigger(null, EnableTriggerRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setTrigger(name)
                .build(), o));
    }

    private void createEvent(String type, String severity, String message) throws Exception {
        yamcsClient.createEvent(CreateEventRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setSeverity(severity)
                .setSource(EVENT_SOURCE)
                .setType(type)
                .setMessage(message)
                .build()).get();
    }

    private void setLocalPara1(int value) throws Exception {
        processorClient.setValue("/REFMDB/SUBSYS1/LocalPara1", ValueHelper.newValue(value)).get();
        Thread.sleep(100);
    }

    private ActivityInfo getActivity(String id) throws Exception {
        return call((r, o) -> activityClient.getActivity(null, GetActivityRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setActivity(id)
                .build(), o));
    }

    private List<TriggerFiringInfo> listFirings(String trigger) throws Exception {
        ListTriggerFiringsResponse response = call((r, o) -> triggerClient.listTriggerFirings(null,
                ListTriggerFiringsRequest.newBuilder()
                        .setInstance(yamcsInstance)
                        .setTrigger(trigger)
                        .build(),
                o));
        return response.getFiringsList();
    }

    /**
     * Waits until there are at least {@code count} firings for the trigger. Returns them, most recent first.
     */
    private List<TriggerFiringInfo> waitForFirings(String trigger, int count) throws Exception {
        var deadline = System.currentTimeMillis() + 5000;
        while (true) {
            var firings = listFirings(trigger);
            if (firings.size() >= count) {
                return firings;
            }
            if (System.currentTimeMillis() > deadline) {
                fail("Expected " + count + " firings, got " + firings);
            }
            Thread.sleep(50);
        }
    }

    private <T> T call(BiConsumer<Void, Observer<T>> method) throws Exception {
        var f = new CompletableFuture<T>();
        method.accept(null, new ResponseObserver<>(f));
        return f.get(5, TimeUnit.SECONDS);
    }
}
