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
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tc.PusTcCcsdsPacket;
import org.yamcs.tctm.pus.services.tm.BucketSaveHandler;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SubServiceTen extends BucketSaveHandler implements PusSubService {
    String yamcsInstance;
    Log log;

    private static int DEFAULT_REPORT_COUNT_SIZE = 4;

    private static int reportCountSize;
    private static int packIdPackSeqControlLength = 4;

    Bucket timetagScheduleDetailReportBucket;
    Gson gson;

    public SubServiceTen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        reportCountSize = config.getInt("reportCountSize", DEFAULT_REPORT_COUNT_SIZE);

        try {
            timetagScheduleDetailReportBucket = getBucket("timetagScheduleDetailReport", yamcsInstance);
        } catch (InitException e) {
            log.error("Unable to create a `timetagScheduleDetailReport bucket` for (Service - 11 | SubService - 10)", e);
            throw new YarchException("Failed to create RDB based bucket: timetagScheduleDetailReport | (Service - 11 | SubService - 10)", e);
        }

        // Create Gson instance
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void generateTimetagScheduleDetailReport(Map<Long, byte[]> requestTcPacketsMap) {
        // Apid, SeqCount, Timetag, Service, SubService, sourceId

        String timetagDetailReportName = "";   // FIXME: What name & metadata to put?
        HashMap<String, String> timetagDetailReportMetadata = new HashMap<>();
        
        try (StringWriter stringWriter = new StringWriter();
             BufferedWriter writer = new BufferedWriter(stringWriter)) {
            
            // Write header
            writer.write("Release Timetag, Source ID, Command APID, Command Ccsds SeqCount, Pus Service, Pus SubService\n");

            for(Map.Entry<Long, byte[]> requestTcMap: requestTcPacketsMap.entrySet()) {
                byte[] tcPacket = requestTcMap.getValue();

                String timetagStr = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(requestTcMap.getKey()),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));

                int commandApid = PusTcCcsdsPacket.getAPID(tcPacket);
                int sourceId = PusTcCcsdsPacket.getSourceId(tcPacket);
                int commandCcsdsSeqCount = PusTcCcsdsPacket.getSequenceCount(tcPacket);
                int commandPusService = PusTcCcsdsPacket.getMessageType(tcPacket);
                int commandPusSubService = PusTcCcsdsPacket.getMessageSubType(tcPacket);

                writer.write(timetagStr + "," + sourceId + "," + commandApid + "," + commandCcsdsSeqCount + "," + commandPusService + "," + commandPusSubService + "\n");
            }

            // Put report in the bucket
            timetagScheduleDetailReportBucket.putObject(timetagDetailReportName, "CSV", timetagDetailReportMetadata, stringWriter.toString().getBytes());

        } catch (IOException e) {
            // FIXME: Should not happen
        }
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int numOfReports = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, reportCountSize);
        byte[] reportArr = Arrays.copyOfRange(dataField, reportCountSize, dataField.length);

        Map<Long, byte[]> requestTcPacketsMap = new HashMap<>(numOfReports);

        for (int reportIndex = 0; reportIndex < numOfReports; reportIndex++) {
            long releaseTime = ByteArrayUtils.decodeLong(reportArr, 0);
            int requestTcPacketLength = (ByteArrayUtils.decodeShort(reportArr,
                    PusTmManager.absoluteTimeLength + packIdPackSeqControlLength) + 1) + 6;

            byte[] requestTcPacket = Arrays.copyOfRange(reportArr, PusTmManager.absoluteTimeLength,
                    PusTmManager.absoluteTimeLength + requestTcPacketLength);
            requestTcPacketsMap.put(releaseTime, requestTcPacket);

            reportArr = Arrays.copyOfRange(reportArr, PusTmManager.absoluteTimeLength + requestTcPacketLength,
                    reportArr.length);
        }

        // Generate the report
        generateTimetagScheduleDetailReport(requestTcPacketsMap);

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        // for (Map.Entry<Long, byte[]> requestTcMap : requestTcPacketsMap.entrySet()) {
        //     ByteBuffer bb = ByteBuffer.allocate(
        //             pPkt.getPrimaryHeader().length + pPkt.getSecondaryHeader().length + PusTmManager.absoluteTimeLength
        //                     + requestTcMap.getValue().length);
        //     bb.put(pPkt.getPrimaryHeader());
        //     bb.put(pPkt.getSecondaryHeader());
        //     bb.putLong(requestTcMap.getKey());
        //     bb.put(requestTcMap.getValue());

        //     TmPacket newPkt = new TmPacket(
        //             tmPacket.getReceptionTime(), tmPacket.getGenerationTime(), tmPacket.getSeqCount(), bb.array());
        //     newPkt.setEarthReceptionTime(tmPacket.getEarthReceptionTime());
        //     pPkts.add(newPkt);
        // }

        pPkts.add(tmPacket);
        return pPkts;
    }
}
