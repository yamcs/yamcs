package org.yamcs.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.util.AggregateMemberNames;

public class ParameterRetrievalServiceCacheTest {
    private ParameterRetrievalService service;

    @BeforeAll
    public static void beforeAll() {
        TimeEncoding.setUp();
    }

    @AfterEach
    public void afterEach() {
        if (service != null && service.executor != null) {
            service.executor.shutdownNow();
        }
    }

    @Test
    public void testMultipleMembersFromSameCachedAggregate() throws Exception {
        Parameter parameter = new Parameter("aggregate");
        AggregateValue aggregate = new AggregateValue(AggregateMemberNames.get(new String[] { "x", "y" }));
        aggregate.setMemberValue("x", ValueUtility.getSint32Value(1));
        aggregate.setMemberValue("y", ValueUtility.getSint32Value(2));

        ParameterValue pv = new ParameterValue(parameter);
        pv.setGenerationTime(10);
        pv.setEngValue(aggregate);

        ArrayParameterCache cache = new ArrayParameterCache("test",
                new ParameterCacheConfig(true, true, 1000, 4096));
        cache.update(List.of(pv));

        service = new ParameterRetrievalService();
        service.init("test", "retrieval", YConfiguration.emptyConfig());
        service.pcache = cache;

        var requests = List.of(
                request(parameter, "aggregate.x", "x"),
                request(parameter, "aggregate.y", "y"));
        var options = ParameterRetrievalOptions.newBuilder()
                .withStartStop(0, 30)
                .withAscending(false)
                .withoutReplay(true)
                .build();
        List<List<ParameterValueWithId>> received = new ArrayList<>();

        service.retrieveMultiReplayOrCache(requests, options, received::add);

        assertEquals(1, received.size());
        assertEquals(2, received.get(0).size());
        assertEquals("aggregate.x", received.get(0).get(0).getId().getName());
        assertEquals(1, received.get(0).get(0).getParameterValue().getEngValue().getSint32Value());
        assertEquals("aggregate.y", received.get(0).get(1).getId().getName());
        assertEquals(2, received.get(0).get(1).getParameterValue().getEngValue().getSint32Value());
    }

    private static ParameterWithId request(Parameter parameter, String id, String member) {
        return new ParameterWithId(parameter, NamedObjectId.newBuilder().setName(id).build(),
                AggregateUtil.parseReference(member));
    }
}
