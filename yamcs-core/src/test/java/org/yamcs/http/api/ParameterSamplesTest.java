package org.yamcs.http.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.parameter.EnumeratedValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.parameter.ValueArray;
import org.yamcs.parameterarchive.ParameterValueArray;
import org.yamcs.parameterarchive.ParameterValueSegment;
import org.yamcs.parameterarchive.SortedTimeSegment;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.ValueEnumerationRange;

class ParameterSamplesTest {
    @Test
    void unknownEnumLabelsRemainGapsAndRawEnumValuesStillAverage() {
        var type = new EnumeratedParameterType.Builder().setName("state")
                .addEnumerationValue(1, "ON").build();
        for (var pid : requests(type)) {
            var row = sampler(pid, false);
            var columnar = sampler(pid, false);
            var unknown = new ParameterValue("value");
            unknown.setGenerationTime(1);
            unknown.setEngValue(new EnumeratedValue(42, "UNDEFINED"));
            row.process(unknown);
            columnar.accept(new ParameterValueArray(new long[] { 1 },
                    new ValueArray(new String[] { "UNDEFINED" }), null, null));
            assertTrue(Double.isNaN(row.collect().get(0).avg));
            assertTrue(Double.isNaN(columnar.collect().get(0).avg));

            var raw = sampler(pid, true);
            unknown.setRawValue(ValueUtility.getUint32Value(10));
            raw.process(unknown);
            unknown.setGenerationTime(2);
            unknown.setRawValue(ValueUtility.getUint32Value(20));
            raw.process(unknown);
            assertEquals(15.0, raw.collect().get(0).avg);
        }
    }

    @Test
    void enumMembersPreserveWideCodesAndRangeLabelsAcrossRetrievalPaths() {
        var type = new EnumeratedParameterType.Builder().setName("state")
                .addEnumerationValue(4_294_967_295L, "WIDE")
                .addEnumerationRange(new ValueEnumerationRange(10, 20, true, true, "RANGE")).build();
        for (var pid : requests(type)) {
            var row = sampler(pid, false);
            var archive = sampler(pid, false);
            var times = new SortedTimeSegment(0);
            var segment = new ParameterValueSegment(0, times, Type.ENUMERATED, null);
            var values = List.of(new EnumeratedValue(4_294_967_295L, "WIDE"),
                    new EnumeratedValue(12, "RANGE"), new EnumeratedValue(19, "RANGE"));
            long[] timestamps = { 1, 11, 12 };
            for (int i = 0; i < values.size(); i++) {
                var pv = new ParameterValue("value");
                pv.setGenerationTime(timestamps[i]);
                pv.setEngValue(values.get(i));
                row.process(pv);
                segment.insert(i, pv);
                times.add(timestamps[i]);
            }
            archive.accept(segment.getRange(0, 3, true, false));
            for (var s : List.of(row, archive)) {
                assertEquals(4_294_967_295.0, s.collect().get(0).avg);
                assertEquals(15.0, s.collect().get(1).avg);
                assertEquals(15.0, s.collect().get(1).min);
                assertEquals(15.0, s.collect().get(1).max);
            }
        }
    }

    @Test
    void booleanMembersKeepLastStateAndRawSamplingStillAverages() {
        for (var pid : requests(new BooleanParameterType.Builder().setName("flag").build())) {
            for (boolean raw : new boolean[] { false, true }) {
                var sampler = sampler(pid, raw);
                for (int i = 0; i < 2; i++) {
                    var pv = new ParameterValue("value");
                    pv.setGenerationTime(i + 1);
                    pv.setEngValue(ValueUtility.getBooleanValue(i == 1));
                    pv.setRawValue(ValueUtility.getUint32Value(i));
                    sampler.process(pv);
                }
                var sample = sampler.collect().get(0);
                assertEquals(raw ? 0.5 : 1.0, sample.avg);
            }
            var columnar = sampler(pid, false);
            var bits = new BitSet();
            bits.set(1);
            columnar.accept(new ParameterValueArray(new long[] { 1, 2 },
                    new ValueArray(bits, 2), null, null));
            assertEquals(1.0, columnar.collect().get(0).avg);
        }
    }

    private Downsampler sampler(ParameterWithId pid, boolean raw) {
        var sampler = new Downsampler(0, 20, 2);
        sampler.setUseRawValue(raw);
        ParameterValuesApi.configureCategoricalSampling(sampler, pid, raw);
        return sampler;
    }

    private List<ParameterWithId> requests(ParameterType leaf) {
        var array = new ArrayParameterType.Builder().setName("array").setNumberOfDimensions(1)
                .setElementType(leaf).build();
        var member = new Member("states");
        member.setDataType(array);
        var aggregate = new AggregateParameterType.Builder().setName("aggregate").addMember(member).build();
        var scalarMember = new Member("state");
        scalarMember.setDataType(leaf);
        var scalarAggregate = new AggregateParameterType.Builder().setName("scalarAggregate")
                .addMember(scalarMember).build();
        return List.of(request(leaf, null), request(array, "[0]"), request(aggregate, "states[0]"),
                request(scalarAggregate, "state"));
    }

    private ParameterWithId request(ParameterType type, String path) {
        var p = new Parameter("p");
        p.setParameterType(type);
        return new ParameterWithId(p, NamedObjectId.newBuilder().setName("p").build(),
                path == null ? null : AggregateUtil.parseReference(path));
    }
}
