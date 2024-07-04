package org.yamcs.tctm.pus.services.tm.eleven;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.PusTcManager;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;


public class SubServiceThirteen implements PusSubService {
    String yamcsInstance;
    Log log;
    private static int requestIdSize;
    
    Bucket timetagScheduleSummaryReportBucket;

    public SubServiceThirteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        requestIdSize = ServiceEleven.sourceIdSize + ServiceEleven.apidSize + ServiceEleven.seqCountSize;
        timetagScheduleSummaryReportBucket = PusTmManager.reports;

        try {
            timetagScheduleSummaryReportBucket.putObject("timetagScheduleSummaryReport/", "application/octet-stream", new HashMap<>(), new byte[0]);

        } catch (IOException e) {
            log.error("Unable to create a directory `" + timetagScheduleSummaryReportBucket.getName() + "reports/timetagScheduleSummaryReport` for (Service - 11 | SubService - 13)", e);
            throw new YarchException("Failed to create a directory `" + timetagScheduleSummaryReportBucket.getName() + "reports/timetagScheduleSummaryReport` for (Service - 11 | SubService - 13)", e);
        }
    }

    public void generateTimetagScheduleSummaryReport(Map<Long, ArrayList<Integer>> requestTcPacketsMap) {
        long missionTime = PusTmManager.timeService.getMissionTime();
        String filename = "timetagScheduleSummaryReport/" + LocalDateTime.ofInstant(
                Instant.ofEpochMilli(missionTime),
                ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

        // Populate metadata
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("CreationTime", LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));

        try (StringWriter stringWriter = new StringWriter();
            BufferedWriter writer = new BufferedWriter(stringWriter)) {
            
            // Write header
            writer.write("ReleaseTimetag,SourceId,CommandApid,CommandCcsdsSeqCount,PusService,PusSubService");
            writer.newLine();

            for(Map.Entry<Long, ArrayList<Integer>> requestTcMap: requestTcPacketsMap.entrySet()) {
                ArrayList<Integer> requestId = requestTcMap.getValue();

                String timetagStr = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(requestTcMap.getKey()),
                    ZoneId.of("GMT")
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

                int sourceId = requestId.get(0);
                int commandApid = requestId.get(1);
                int commandCcsdsSeqCount = requestId.get(2);

                writer.write(timetagStr + "," + sourceId + "," + commandApid + "," + commandCcsdsSeqCount);
                writer.newLine();
            }
            writer.flush();

            // Put report in the bucket
            timetagScheduleSummaryReportBucket.putObject(filename, "csv", metadata, stringWriter.getBuffer().toString().getBytes(StandardCharsets.UTF_8));

        } catch (IOException e) {
            throw new UncheckedIOException("S(11, 13) | Cannot save timetag summary report in bucket: " + filename + (timetagScheduleSummaryReportBucket != null ? " -> " + timetagScheduleSummaryReportBucket.getName() : ""), e);
        }
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
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
