package org.yamcs.tctm.pus.services.tm.fifteen;

import org.yamcs.InitException;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.BucketSaveHandler;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.YarchException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SubServiceNineteen extends BucketSaveHandler implements PusSubService {
    enum PacketStoreStatus {
        ENABLED(1), DISABLED(0);

        int value;

        PacketStoreStatus(int value) {
            this.value = value;
        }

        public static PacketStoreStatus fromValue(int value) {
            for (PacketStoreStatus enumValue : PacketStoreStatus.values()) {
                if (enumValue.value == value) {
                    return enumValue;
                }
            }
            return null;
        }
        
        public int getValue() {
            return value;
        }
    }


    String yamcsInstance;
    Log log;

    private int DEFAULT_STATUS_SIZE = 1;
    private int DEFAULT_REPORT_COUNT_SIZE = 1;
    private int DEFAULT_PACKET_STORE_ID_SIZE = 1;

    protected int packetStoreIdSize;
    protected int reportCountSize;
    protected int statusSize;

    Bucket packetStoreStatusReportBucket;
    TimeService timeService;

    public SubServiceNineteen(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        packetStoreIdSize = config.getInt("packetStoreIdSize", DEFAULT_PACKET_STORE_ID_SIZE);
        reportCountSize = config.getInt("reportCountSize", DEFAULT_REPORT_COUNT_SIZE);
        statusSize = config.getInt("statusSize", DEFAULT_STATUS_SIZE);

        try {
            packetStoreStatusReportBucket = getBucket("packetStoreStatusReportBucket", yamcsInstance);
        } catch (InitException e) {
            log.error("Unable to create a `packetStoreStatusReportBucket bucket` for (Service - 15 | SubService - 19)", e);
            throw new YarchException("Failed to create RDB based bucket: packetStoreStatusReportBucket | (Service - 15 | SubService - 19)", e);
        }

        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    public void generatePacketStoredStatusReport(Map<Integer, byte[]> packetStoreReportMap) {
        long missionTime = timeService.getMissionTime();
        String packetStoreStatusReportName = "PacketStoreStatusReport | " + LocalDateTime.ofInstant(
            Instant.ofEpochMilli(missionTime),
            ZoneId.of("GMT")
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss' UTC'"));
        

        try (StringWriter stringWriter = new StringWriter();
            BufferedWriter writer = new BufferedWriter(stringWriter)) {

            // Write header
            writer.write("PacketStoreID, PacketStoreStatus, PacketStoreOpenRetrievalStatus\n");

            for (Map.Entry<Integer, byte[]> packetStoreReport: packetStoreReportMap.entrySet()) {
                byte[] report = packetStoreReport.getValue();
                int packetStoreId = packetStoreReport.getKey();

                PacketStoreStatus packetStoreStatus = PacketStoreStatus.fromValue((int) ByteArrayUtils.decodeCustomInteger(report, 0, statusSize));
                PacketStoreStatus packetStoreOpenRetrievalStatus = PacketStoreStatus.fromValue((int) ByteArrayUtils.decodeCustomInteger(report, statusSize, statusSize));

                writer.write(packetStoreId + ", " + packetStoreStatus + ", " + packetStoreOpenRetrievalStatus + "\n");
            }

            // Put report in the bucket
            packetStoreStatusReportBucket.putObject(packetStoreStatusReportName, "csv", new HashMap<>(), stringWriter.toString().getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        byte[] dataField = pPkt.getDataField();

        int numberOfReports = (int) ByteArrayUtils.decodeCustomInteger(dataField, 0, reportCountSize);

        Map<Integer, byte[]> packetStoreReportMap = new HashMap<>();
        int reportSize = packetStoreIdSize + statusSize * 2; 
        for(int registerIndex = 0; registerIndex < numberOfReports; registerIndex++){
            int packetStoreId = (int) ByteArrayUtils.decodeCustomInteger(dataField, reportCountSize + registerIndex * reportSize, packetStoreIdSize);
            byte[] data = Arrays.copyOfRange(dataField, reportCountSize + packetStoreIdSize + registerIndex * reportSize, reportCountSize + (registerIndex + 1) * reportSize);

            packetStoreReportMap.put(packetStoreId, data);
        }

        // Generate CSV report
        generatePacketStoredStatusReport(packetStoreReportMap);

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(tmPacket); 

        return pPkts;
    }
}
