package org.yamcs.tctm.pus.services.tm.eleven;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.InitException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.PusTcManager;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;
import org.yamcs.tctm.pus.services.tm.BucketSaveHandler;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;


public class SubServiceTen extends BucketSaveHandler implements PusSubService {
    String yamcsInstance;
    Log log;

    private static final int packIdPackSeqControlLength = 4;
    private static final int messageTypeSize = 2;
    private static final int messageSubTypeSize = 2;

    Bucket timetagScheduleDetailReportBucket;
    TimeService timeService;

    public SubServiceTen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        try {
            timetagScheduleDetailReportBucket = getBucket("timetagScheduleDetailReport", yamcsInstance);
        } catch (InitException e) {
            log.error("Unable to create a `timetagScheduleDetailReport bucket` for (Service - 11 | SubService - 10)", e);
            throw new YarchException("Failed to create RDB based bucket: timetagScheduleDetailReport | (Service - 11 | SubService - 10)", e);
        }

        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    public ArrayList<byte[]> generateTimetagScheduleDetailReport(Map<Long, byte[]> requestTcPacketsMap) {
        // Apid, SeqCount, Timetag, Service, SubService, sourceId

        long missionTime = timeService.getMissionTime();
        String timetagDetailReportName = "TimeTagSchedule_DetailReport | " + LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));
        
        ArrayList<byte[]> releaseAndRequestTimes = new ArrayList<>();
        try (StringWriter stringWriter = new StringWriter();
            BufferedWriter writer = new BufferedWriter(stringWriter)) {
            
            // Write header
            writer.write("ReleaseTimetag, SourceID, CommandApid, CommandCcsdsSeqCount, PusService, PusSubService\n");

            for(Map.Entry<Long, byte[]> requestTcMap: requestTcPacketsMap.entrySet()) {
                byte[] tcPacket = requestTcMap.getValue();
                long timetag = requestTcMap.getKey();

                String timetagStr = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timetag),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));

                int commandApid = PusTcCcsdsPacket.getAPID(tcPacket);
                int sourceId = PusTcCcsdsPacket.getSourceId(tcPacket);
                int commandCcsdsSeqCount = PusTcCcsdsPacket.getSequenceCount(tcPacket);
                int commandPusService = PusTcCcsdsPacket.getMessageType(tcPacket);
                int commandPusSubService = PusTcCcsdsPacket.getMessageSubType(tcPacket);

                writer.write(timetagStr + "," + sourceId + "," + commandApid + "," + commandCcsdsSeqCount + "," + commandPusService + "," + commandPusSubService + "\n");

                // Create new Payload
                ByteBuffer bb = ByteBuffer.wrap(new byte[PusTcManager.timetagLength + ServiceEleven.sourceIdSize + ServiceEleven.apidSize + ServiceEleven.seqCountSize + messageTypeSize + messageSubTypeSize]);
                bb.put(ByteArrayUtils.encodeCustomInteger(timetag, PusTcManager.timetagLength));
                bb.put(ByteArrayUtils.encodeCustomInteger(sourceId, ServiceEleven.sourceIdSize));
                bb.put(ByteArrayUtils.encodeCustomInteger(commandApid, ServiceEleven.apidSize));
                bb.put(ByteArrayUtils.encodeCustomInteger(commandCcsdsSeqCount, ServiceEleven.seqCountSize));
                bb.put(ByteArrayUtils.encodeCustomInteger(commandPusService, messageTypeSize));
                bb.put(ByteArrayUtils.encodeCustomInteger(commandPusSubService, messageSubTypeSize));

                releaseAndRequestTimes.add(bb.array());
            }

            // Put report in the bucket
            timetagScheduleDetailReportBucket.putObject(timetagDetailReportName, "csv", new HashMap<>(), stringWriter.toString().getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }

        return releaseAndRequestTimes;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        Map<Long, byte[]> requestTcPacketsMap = getRequestTcPacketMap(dataField);

        // Generate the report
        ArrayList<byte[]> newPayload = generateTimetagScheduleDetailReport(requestTcPacketsMap);

        // Create a new TmPacket similar S(11, 13)
        byte[] primaryHeader = pPkt.getPrimaryHeader();
        byte[] secondaryHeader = pPkt.getSecondaryHeader();
        byte[] numOfReportsArr = Arrays.copyOfRange(dataField, 0, ServiceEleven.reportCountSize);

        ByteBuffer bb = ByteBuffer.wrap(new byte[PusTmManager.PRIMARY_HEADER_LENGTH + PusTmManager.secondaryHeaderLength + ServiceEleven.reportCountSize + PusTcManager.timetagLength + ServiceEleven.sourceIdSize + ServiceEleven.apidSize + ServiceEleven.seqCountSize + messageTypeSize + messageSubTypeSize]);
        bb.put(primaryHeader);
        bb.put(secondaryHeader);
        bb.put(numOfReportsArr);
        newPayload.forEach(bb::put);

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket);
        
        return pPkts;
    }

    private static Map<Long, byte[]> getRequestTcPacketMap(byte[] dataField) {
        int numOfReports = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceEleven.reportCountSize);
        byte[] reportArr = Arrays.copyOfRange(dataField, ServiceEleven.reportCountSize, dataField.length);

        Map<Long, byte[]> requestTcPacketsMap = new HashMap<>(numOfReports);

        for (int reportIndex = 0; reportIndex < numOfReports; reportIndex++) {
            long releaseTime = ByteArrayUtils.decodeLong(reportArr, 0);
            int requestTcPacketLength = (ByteArrayUtils.decodeShort(reportArr,
                    PusTcManager.timetagLength + packIdPackSeqControlLength) + 1) + 6;

            byte[] requestTcPacket = Arrays.copyOfRange(reportArr, PusTcManager.timetagLength,
                    PusTcManager.timetagLength + requestTcPacketLength);
            requestTcPacketsMap.put(releaseTime, requestTcPacket);

            reportArr = Arrays.copyOfRange(reportArr, PusTcManager.timetagLength + requestTcPacketLength,
                    reportArr.length);
        }
        
        return requestTcPacketsMap;
    }
}
