package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.InitException;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.simulator.SimulatorArgs;
import org.yamcs.simulator.SimulatorCommander;
import org.yamcs.utils.TimeEncoding;

import com.beust.jcommander.JCommander;

class PusMdbTelemetryTest {
    @TempDir
    Path directory;

    @Test
    void emitsMdbDefinedApidsAndPreservesPackedAndAbsoluteOffsets() throws IOException {
        TimeEncoding.setUp();
        Path file = directory.resolve("flight.xml");
        Files.writeString(file, FLIGHT_MDB);

        var telemetry = PusMdbTelemetry.load(file);
        assertEquals(25, telemetry.sourceDataOffset());
        assertEquals(1, telemetry.housekeeping().size());
        assertEquals(1, telemetry.events().size());
        var hk = telemetry.housekeeping().getFirst();
        assertEquals(19, hk.apid());
        assertEquals(3, hk.service());
        assertEquals(25, hk.subtype());
        assertEquals(42, hk.id());
        assertArrayEquals(new byte[] { 42, (byte) 0xA0, 0, 1, 0 }, hk.data());
        hk.data()[0] = 0;
        assertEquals(42, hk.data()[0]);
        var event = telemetry.events().getFirst();
        assertEquals(19, event.apid());
        assertEquals(5, event.service());
        assertEquals(2, event.subtype());
        assertEquals(7, event.id());
        assertArrayEquals(new byte[] { 7, 0x3F, (byte) 0x80, 0, 0, 1, 1 }, event.data());

        var mdb = MdbFactory.createInstance(List.of(YConfiguration.wrap(Map.of("type", "xtce", "spec", file.toString()))),
                false, false);
        var extractor = new XtceTmExtractor(mdb);
        extractor.provideAll();
        var packet = new PusTmPacket(hk.apid(), hk.data().length, hk.service(), hk.subtype());
        packet.getUserDataBuffer().put(hk.data());
        long now = TimeEncoding.getWallclockTime();
        var result = extractor.processPacket(packet.getBytes(), now, now, 0);
        var sample = result.getParameterResult().getFirstInserted(mdb.getParameter("/Flight/sample"));
        assertNotNull(sample);
        assertEquals(1, sample.getEngValue().toLong());

        packet = new PusTmPacket(event.apid(), event.data().length, event.service(), event.subtype());
        packet.getUserDataBuffer().put(event.data());
        result = extractor.processPacket(packet.getBytes(), now, now, 1);
        var counters = result.getParameterResult().getFirstInserted(mdb.getParameter("/Flight/counters"));
        assertNotNull(counters);
        var values = (ArrayValue) counters.getEngValue();
        assertArrayEquals(new int[] { 2 }, values.getDimensions());
        assertEquals(1, values.getElementValue(0).toLong());
        assertEquals(1, values.getElementValue(1).toLong());
    }

    @Test
    void forwardsEachConfiguredMdbPathToTheSimulatorProcess() throws Exception {
        var first = directory.resolve("flight.xml");
        var second = directory.resolve("payload, engineering.xml");
        var commander = new SimulatorCommander();
        Map<String, Object> args = Map.of("pus",
                Map.of("telemetry", Map.of("mdbFiles", List.of(first.toString(), second.toString()))));
        var config = commander.getSpec().validate(YConfiguration.wrap(args));
        commander.init(null, "simulator", config);

        List<String> command = commander.getConfig().getList("command");
        var parsed = new SimulatorArgs();
        JCommander.newBuilder().addObject(parsed).build().parse(command.subList(2, command.size()).toArray(String[]::new));
        assertEquals(List.of(first, second), parsed.pusTelemetryMdbFiles);
    }

    @Test
    void resolvesReportDefinitionsAcrossMdbFiles() throws IOException {
        Path flight = directory.resolve("flight.xml");
        Path payload = directory.resolve("payload.xml");
        Files.writeString(flight, FLIGHT_MDB);
        Files.writeString(payload, """
                <SpaceSystem name="Payload" xmlns="http://www.omg.org/spec/XTCE/20180204">
                  <TelemetryMetaData>
                    <ContainerSet>
                      <SequenceContainer name="payloadHousekeeping">
                        <EntryList><ParameterRefEntry parameterRef="/Flight/sample"/></EntryList>
                        <BaseContainer containerRef="/Flight/hk"><RestrictionCriteria><ComparisonList>
                          <Comparison parameterRef="/Flight/ccsds_packet_id/apid" value="FLIGHT"/>
                          <Comparison parameterRef="/Flight/structure_id" value="44"/>
                        </ComparisonList></RestrictionCriteria></BaseContainer>
                      </SequenceContainer>
                    </ContainerSet>
                  </TelemetryMetaData>
                </SpaceSystem>
                """);

        var telemetry = PusMdbTelemetry.load(List.of(flight, payload));
        assertEquals(List.of(42, 44), telemetry.housekeeping().stream().map(PusMdbTelemetry.Report::id).toList());
        assertArrayEquals(new byte[] { 44, 1, 0 }, telemetry.housekeeping().getLast().data());
        assertEquals(1, telemetry.events().size());
    }

    @Test
    void rejectsAmbiguousOrEmptyMdbSources() throws Exception {
        var commander = new SimulatorCommander();
        var spec = commander.getSpec();
        var ambiguous = YConfiguration.wrap(Map.of("pus", Map.of("telemetry",
                Map.of("mdbFile", "flight.xml", "mdbFiles", List.of("payload.xml")))));
        assertThrows(ValidationException.class, () -> spec.validate(ambiguous));

        var empty = spec.validate(YConfiguration.wrap(Map.of("pus", Map.of("telemetry", Map.of("mdbFiles", List.of())))));
        assertThrows(InitException.class, () -> commander.init(null, "simulator", empty));
    }

    private static final String FLIGHT_MDB = """
            <SpaceSystem name="Flight" xmlns="http://www.omg.org/spec/XTCE/20180204">
              <TelemetryMetaData>
                <ParameterTypeSet>
                  <IntegerParameterType name="byte" signed="false">
                    <IntegerDataEncoding sizeInBits="8" encoding="unsigned"/>
                  </IntegerParameterType>
                  <EnumeratedParameterType name="Apid">
                    <IntegerDataEncoding sizeInBits="11" encoding="unsigned"/>
                    <EnumerationList><Enumeration value="19" label="FLIGHT"/></EnumerationList>
                  </EnumeratedParameterType>
                  <AggregateParameterType name="PacketId"><MemberList>
                    <Member name="apid" typeRef="Apid"/>
                  </MemberList></AggregateParameterType>
                  <EnumeratedParameterType name="Status">
                    <IntegerDataEncoding sizeInBits="3" encoding="unsigned"/>
                    <EnumerationList><Enumeration value="5" label="READY"/></EnumerationList>
                  </EnumeratedParameterType>
                  <IntegerParameterType name="word" signed="false">
                    <IntegerDataEncoding sizeInBits="16" encoding="unsigned" byteOrder="leastSignificantByteFirst"/>
                  </IntegerParameterType>
                  <FloatParameterType name="float">
                    <FloatDataEncoding sizeInBits="32" encoding="IEEE754_1985"/>
                  </FloatParameterType>
                  <ArrayParameterType name="counters" arrayTypeRef="byte">
                    <DimensionList><Dimension>
                      <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
                      <EndingIndex><FixedValue>1</FixedValue></EndingIndex>
                    </Dimension></DimensionList>
                  </ArrayParameterType>
                  <BinaryParameterType name="variable"><BinaryDataEncoding/></BinaryParameterType>
                </ParameterTypeSet>
                <ParameterSet>
                  <Parameter name="ccsds_packet_id" parameterTypeRef="PacketId"/>
                  <Parameter name="pus_service_type" parameterTypeRef="byte"/>
                  <Parameter name="pus_subservice_type" parameterTypeRef="byte"/>
                  <Parameter name="structure_id" parameterTypeRef="byte"/>
                  <Parameter name="event_id" parameterTypeRef="byte"/>
                  <Parameter name="status" parameterTypeRef="Status"/>
                  <Parameter name="sample" parameterTypeRef="word"/>
                  <Parameter name="eventSample" parameterTypeRef="float"/>
                  <Parameter name="counters" parameterTypeRef="counters"/>
                  <Parameter name="unsupported" parameterTypeRef="variable"/>
                </ParameterSet>
                <ContainerSet>
                  <SequenceContainer name="root"><EntryList>
                    <ParameterRefEntry parameterRef="ccsds_packet_id"><LocationInContainerInBits referenceLocation="containerStart"><FixedValue>5</FixedValue></LocationInContainerInBits></ParameterRefEntry>
                    <ParameterRefEntry parameterRef="pus_service_type"><LocationInContainerInBits referenceLocation="containerStart"><FixedValue>56</FixedValue></LocationInContainerInBits></ParameterRefEntry>
                    <ParameterRefEntry parameterRef="pus_subservice_type"/>
                  </EntryList></SequenceContainer>
                  <SequenceContainer name="hk" abstract="true">
                    <EntryList><ParameterRefEntry parameterRef="structure_id"><LocationInContainerInBits referenceLocation="containerStart"><FixedValue>200</FixedValue></LocationInContainerInBits></ParameterRefEntry></EntryList>
                    <BaseContainer containerRef="root"><RestrictionCriteria><ComparisonList>
                      <Comparison parameterRef="pus_service_type" value="3"/>
                      <Comparison parameterRef="pus_subservice_type" value="25"/>
                    </ComparisonList></RestrictionCriteria></BaseContainer>
                  </SequenceContainer>
                  <SequenceContainer name="event" abstract="true">
                    <EntryList><ParameterRefEntry parameterRef="event_id"><LocationInContainerInBits referenceLocation="containerStart"><FixedValue>200</FixedValue></LocationInContainerInBits></ParameterRefEntry></EntryList>
                    <BaseContainer containerRef="root"><RestrictionCriteria><ComparisonList>
                      <Comparison parameterRef="pus_service_type" value="5"/>
                      <Comparison parameterRef="pus_subservice_type" value="2"/>
                    </ComparisonList></RestrictionCriteria></BaseContainer>
                  </SequenceContainer>
                  <SequenceContainer name="housekeeping">
                    <EntryList>
                      <ParameterRefEntry parameterRef="status"/>
                      <ParameterRefEntry parameterRef="sample"><LocationInContainerInBits referenceLocation="containerStart"><FixedValue>224</FixedValue></LocationInContainerInBits></ParameterRefEntry>
                    </EntryList>
                    <BaseContainer containerRef="hk"><RestrictionCriteria><BooleanExpression><ANDedConditions>
                      <Condition><ParameterInstanceRef parameterRef="ccsds_packet_id/apid"/><ComparisonOperator>==</ComparisonOperator><Value>FLIGHT</Value></Condition>
                      <Condition><ParameterInstanceRef parameterRef="structure_id"/><ComparisonOperator>==</ComparisonOperator><Value>42</Value></Condition>
                    </ANDedConditions></BooleanExpression></RestrictionCriteria></BaseContainer>
                  </SequenceContainer>
                  <SequenceContainer name="typedEvent">
                    <EntryList><ParameterRefEntry parameterRef="eventSample"/><ArrayParameterRefEntry parameterRef="counters"/></EntryList>
                    <BaseContainer containerRef="event"><RestrictionCriteria><ComparisonList>
                      <Comparison parameterRef="ccsds_packet_id/apid" value="FLIGHT"/>
                      <Comparison parameterRef="event_id" value="7"/>
                    </ComparisonList></RestrictionCriteria></BaseContainer>
                  </SequenceContainer>
                  <SequenceContainer name="unsupportedHk">
                    <EntryList><ParameterRefEntry parameterRef="unsupported"/></EntryList>
                    <BaseContainer containerRef="hk"><RestrictionCriteria><ComparisonList>
                      <Comparison parameterRef="ccsds_packet_id/apid" value="FLIGHT"/>
                      <Comparison parameterRef="structure_id" value="43"/>
                    </ComparisonList></RestrictionCriteria></BaseContainer>
                  </SequenceContainer>
                </ContainerSet>
              </TelemetryMetaData>
            </SpaceSystem>
            """;
}
