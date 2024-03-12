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
import org.yamcs.tctm.pus.PusTcManager;
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
    private static int requestIdSize;
    
    Bucket timetagScheduleSummaryReportBucket;
    Gson gson;

    public SubServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        requestIdSize = ServiceEleven.sourceIdSize + ServiceEleven.apidSize + ServiceEleven.seqCountSize;

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

        long missionTime = ServiceEleven.timeService.getMissionTime();
        String timetagSummaryReportName = "TimeTagSchedule_SummaryReport | " + LocalDateTime.ofInstant(
                Instant.ofEpochMilli(missionTime),
                ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));        HashMap<String, String> timetagSummaryReportMetadata = new HashMap<>();
        
        try (StringWriter stringWriter = new StringWriter();
             BufferedWriter writer = new BufferedWriter(stringWriter)) {
            
            // Write header
            writer.write("Release Timetag, Source ID, Command APID, Command Ccsds SeqCount, Pus Service, Pus SubService\n");

            for(Map.Entry<Long, ArrayList<Integer>> requestTcMap: requestTcPacketsMap.entrySet()) {
                ArrayList<Integer> requestId = requestTcMap.getValue();

                String timetagStr = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(requestTcMap.getKey()),
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
            e.printStackTrace();
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

        int numOfReports = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, ServiceEleven.reportCountSize);
        byte[] reportArr = Arrays.copyOfRange(dataField, ServiceEleven.reportCountSize, dataField.length);

        Map<Long, ArrayList<Integer>> requestTcPacketsMap = new HashMap<>(numOfReports);

        for (int reportIndex = 0; reportIndex < numOfReports; reportIndex++) {
            long releaseTime = ByteArrayUtils.decodeLong(reportArr, 0);
            ArrayList<Integer> tcIdentification = extractFromRequestId(reportArr);

            requestTcPacketsMap.put(releaseTime, tcIdentification);
            reportArr = Arrays.copyOfRange(reportArr, PusTcManager.timetagLength + requestIdSize, reportArr.length);
        }

        // Generate the report
        generateTimetagScheduleSummaryReport(requestTcPacketsMap);

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket);
        
        return pPkts;
    }

    private static ArrayList<Integer> extractFromRequestId(byte[] reportArr) {
        byte[] requestIdArr = Arrays.copyOfRange(reportArr, PusTcManager.timetagLength, PusTcManager.timetagLength + requestIdSize);

        ArrayList<Integer> requestId = new ArrayList<>();
        requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, 0, ServiceEleven.sourceIdSize));
        requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, ServiceEleven.sourceIdSize, ServiceEleven.apidSize));
        requestId.add((int) ByteArrayUtils.decodeCustomInteger(requestIdArr, (ServiceEleven.sourceIdSize + ServiceEleven.apidSize), ServiceEleven.seqCountSize));
        return requestId;
    }
}
