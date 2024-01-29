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

public class SubServiceThirteen extends BucketSaveHandler implements PusSubService {
    String yamcsInstance;
    Log log;

    private static int DEFAULT_REPORT_COUNT_SIZE = 4;
    private static int DEFAULT_SOURCE_ID_SIZE = 2;
    private static int DEFAULT_APID_SIZE = 2;
    private static int DEFAULT_SEQ_COUNT_SIZE = 2;

    private static int reportCountSize;
    private static int sourceIdSize;
    private static int apidSize;
    private static int seqCountSize;
    private static int requestIdSize;

    private static int packIdPackSeqControlLength = 4;

    Bucket timetagScheduleSummaryReportBucket;
    Gson gson;

    public SubServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        reportCountSize = config.getInt("reportCountSize", DEFAULT_REPORT_COUNT_SIZE);
        sourceIdSize = config.getInt("sourceIdSize", DEFAULT_SOURCE_ID_SIZE);
        apidSize = config.getInt("apidSize", DEFAULT_APID_SIZE);
        seqCountSize = config.getInt("seqCountSize", DEFAULT_SEQ_COUNT_SIZE);
        requestIdSize = sourceIdSize + apidSize + seqCountSize;


        try {
            timetagScheduleSummaryReportBucket = getBucket("timetagScheduleSummaryReport", yamcsInstance);
        } catch (InitException e) {
            log.error("Unable to create a `timetagScheduleSummaryReport bucket` for (Service - 11 | SubService - 13)", e);
            throw new YarchException("Failed to create RDB based bucket: timetagScheduleSummaryReport | (Service - 11 | SubService - 13)", e);
        }

        // Create Gson instance
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void generateTimetagScheduleSummaryReport(Map<Long, ArrayList<Integer>> requestTcPacketsMap) {
        // Apid, SeqCount, Timetag, Service, SubService, sourceId

        String timetagSummaryReportName = "";   // FIXME: What name & metadata to put?
        HashMap<String, String> timetagSummaryReportMetadata = new HashMap<>();
        
        try (StringWriter stringWriter = new StringWriter();
             BufferedWriter writer = new BufferedWriter(stringWriter)) {
            
            // Write header
            writer.write("Release Timetag, Source ID, Command APID, Command Ccsds SeqCount, Pus Service, Pus SubService\n");

            for(Map.Entry<Long, ArrayList<Integer>> requestTcMap: requestTcPacketsMap.entrySet()) {
                ArrayList<Integer> requestId = requestTcMap.getValue();

                String timetagStr = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(requestTcMap.getKey()),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));

                int sourceId = requestId.get(0);
                int commandApid = requestId.get(1);
                int commandCcsdsSeqCount = requestId.get(2);

                writer.write(timetagStr + "," + sourceId + "," + commandApid + "," + commandCcsdsSeqCount + "\n");
            }

            // Put report in the bucket
            timetagScheduleSummaryReportBucket.putObject(timetagSummaryReportName, "CSV", timetagSummaryReportMetadata, stringWriter.toString().getBytes());

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

        Map<Long, ArrayList<Integer>> requestTcPacketsMap = new HashMap<>(numOfReports);

        for (int reportIndex = 0; reportIndex < numOfReports; reportIndex++) {
            long releaseTime = ByteArrayUtils.decodeLong(reportArr, 0);
            byte[] requestIdArr = Arrays.copyOfRange(reportArr, PusTmManager.absoluteTimeLength, PusTmManager.absoluteTimeLength + requestIdSize);

            ArrayList<Integer> requestId = new ArrayList<>();
            requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, 0, sourceIdSize));
            requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, sourceIdSize, apidSize));
            requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, (sourceIdSize + apidSize), seqCountSize));

            requestTcPacketsMap.put(releaseTime, requestId);
            reportArr = Arrays.copyOfRange(reportArr, PusTmManager.absoluteTimeLength + requestIdSize, reportArr.length);
        }

        // Generate the report
        generateTimetagScheduleSummaryReport(requestTcPacketsMap);

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        // for(Map.Entry<Long, ArrayList<Integer>> requestTcMap: requestTcPacketsMap.entrySet()) {
        //     ByteBuffer bb = ByteBuffer.allocate(
        //         pPkt.getPrimaryHeader().length + pPkt.getSecondaryHeader().length + PusTmManager.absoluteTimeLength + requestTcMap.getValue().length
        //     );
        //     bb.put(pPkt.getPrimaryHeader());
        //     bb.put(pPkt.getSecondaryHeader());
        //     bb.putLong(requestTcMap.getKey());
        //     bb.putShort(requestTcMap.getValue().get(0).shortValue());   // Source ID
        //     bb.putShort(requestTcMap.getValue().get(1).shortValue());   // APID
        //     bb.putShort(requestTcMap.getValue().get(2).shortValue());   // Command SeqCount

        //     TmPacket newPkt = new TmPacket(
        //         tmPacket.getReceptionTime(), tmPacket.getGenerationTime(), tmPacket.getSeqCount(), bb.array()
        //     );
        //     newPkt.setEarthReceptionTime(tmPacket.getEarthReceptionTime());
        //     pPkts.add(newPkt);
        // }
        // return pPkts;

        pPkts.add(tmPacket);
        return pPkts;
    }
}
