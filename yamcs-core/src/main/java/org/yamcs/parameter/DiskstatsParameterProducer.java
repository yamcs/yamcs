package org.yamcs.parameter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.logging.Log;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.UnitType;

import static org.yamcs.utils.ValueUtility.getFloatValue;

/**
 * Generates parameters containing information about the disk IO, similarly with what the command iostat provides.
 * <p>
 * Works only on Linux, reads all the info from /proc/diskstats.
 * 
 */
public class DiskstatsParameterProducer implements SystemParametersProducer {
    final static Log log = new Log(DiskstatsParameterProducer.class);

    final List<DiskStatsParam> diskstatsParams;

    private AggregateParameterType diskstatAggrType;

    public DiskstatsParameterProducer(SystemParametersService sysParamsService) throws IOException {
        UnitType kbsecunit = new UnitType("KB/s");
        UnitType readsecunit = new UnitType("reads/s");
        UnitType writesecunit = new UnitType("writes/s");
        UnitType millisecunit = new UnitType("millis");

        UnitType pctunit = new UnitType("%");

        Member diskReadsMember = new Member("diskReads", sysParamsService.getBasicType(Type.FLOAT, readsecunit));
        diskReadsMember.setShortDescription("Number of reads");

        Member kbReadsMember = new Member("kbReads", sysParamsService.getBasicType(Type.FLOAT, kbsecunit));
        kbReadsMember.setShortDescription("Amount of data read");

        Member readWaitMember = new Member("readWait", sysParamsService.getBasicType(Type.FLOAT, millisecunit));
        readWaitMember.setShortDescription("Average wait for a read");

        Member diskWritesMember = new Member("diskWrites", sysParamsService.getBasicType(Type.FLOAT, writesecunit));
        diskWritesMember.setShortDescription("Number of writes");

        Member kbWritesMember = new Member("kbWrites", sysParamsService.getBasicType(Type.FLOAT, kbsecunit));
        kbWritesMember.setShortDescription("Amount of data written");

        Member writeWaitMember = new Member("writeWait", sysParamsService.getBasicType(Type.FLOAT, millisecunit));
        writeWaitMember.setShortDescription("Average wait for a write");

        Member utilMember = new Member("util", sysParamsService.getBasicType(Type.FLOAT, pctunit));
        utilMember.setShortDescription("Percentage  of  elapsed  time during which "
                + "I/O requests were issued to the device");

        diskstatAggrType = new AggregateParameterType.Builder().setName("DiskStats")
                .addMember(diskReadsMember)
                .addMember(kbReadsMember)
                .addMember(readWaitMember)
                .addMember(diskWritesMember)
                .addMember(kbWritesMember)
                .addMember(writeWaitMember)
                .addMember(utilMember)
                .build();

        diskstatsParams = new ArrayList<>();

        for (var me : readStats().entrySet()) {
            String device = me.getKey();
            Parameter p = sysParamsService.createSystemParameter("diskstats/" + device, diskstatAggrType,
                    "Disk statistics for " + device);
            diskstatsParams.add(new DiskStatsParam(device, p, me.getValue()));
        }
    }

    @Override
    public Collection<ParameterValue> getSystemParameters(long gentime) {
        List<ParameterValue> pvlist = new ArrayList<>();
        try {
            Map<String, DiskStat> stats = readStats();

            for (DiskStatsParam ioparam : diskstatsParams) {
                var s1 = stats.get(ioparam.devName);
                if (s1 == null) {
                    continue;
                }
                var s0 = ioparam.stats;
                float timeMillis = (float) ((s1.nanoTime - s0.nanoTime) / 1000_000.0);

                float timeSec = timeMillis / 1000f;
                if (timeSec < 0) {
                    return pvlist;
                }

                AggregateValue v = new AggregateValue(diskstatAggrType.getMemberNames());

                v.setMemberValue("diskReads", getFloatValue((s1.diskReads - s0.diskReads) / timeSec));
                v.setMemberValue("kbReads", getFloatValue((s1.sectorReads - s0.sectorReads) / timeSec / 2f));
                float readWait = s1.diskReads > s0.diskReads
                        ? (s1.readTime - s0.readTime) / (float) (s1.diskReads - s0.diskReads)
                        : 0;
                
                v.setMemberValue("readWait", getFloatValue(readWait));
                v.setMemberValue("diskWrites", getFloatValue((s1.diskWrites - s0.diskWrites) / timeSec));
                v.setMemberValue("kbWrites", getFloatValue((s1.sectorWrites - s0.sectorWrites) / timeSec / 2f));
                
                float writeWait = s1.diskWrites > s0.diskWrites
                        ? (s1.writeTime - s0.writeTime) / (float) (s1.diskWrites - s0.diskWrites)
                        : 0;
                v.setMemberValue("writeWait", getFloatValue(writeWait));
                v.setMemberValue("util", getFloatValue((s1.ioTime - s0.ioTime) / timeSec / 10f));

                ParameterValue pv = new ParameterValue(ioparam.param);
                pv.setGenerationTime(gentime);
                pv.setAcquisitionTime(gentime);
                pv.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);
                pv.setEngValue(v);

                pv.setExpireMillis((long) (1.9 * getFrequency() * 1000));
                pvlist.add(pv);

                ioparam.stats = s1;
            }
        } catch (IOException e) {
            log.error("Failed to collect disk statistics", e);
        }
        return pvlist;
    }

    Map<String, DiskStat> readStats() throws IOException {
        Map<String, DiskStat> r = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/diskstats"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                long nanoTime = System.nanoTime();
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 14) {
                    continue;
                }
                if ("7".equals(parts[0])) {
                    // skip loopback devices
                    continue;
                }
                String device = parts[2];
                DiskStat stat = new DiskStat(nanoTime,
                        Long.parseLong(parts[3]), Long.parseLong(parts[5]), Long.parseLong(parts[6]),
                        Long.parseLong(parts[7]), Long.parseLong(parts[9]), Long.parseLong(parts[10]),
                        Long.parseLong(parts[12]));

                r.put(device, stat);
            }
        }
        return r;
    }

    public long uptime() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/uptime"))) {
            String line = reader.readLine();
            String[] parts = line.trim().split("\\s+");
            double uptimeSeconds = Double.parseDouble(parts[0]);
            return (long) (uptimeSeconds * 1000);
        }
    }

    @Override
    public int getFrequency() {
        return 5;
    }

    static class DiskStatsParam {
        final String devName;
        final Parameter param;

        DiskStat stats;

        public DiskStatsParam(String devName, Parameter param, DiskStat stats) {
            this.devName = devName;
            this.param = param;
            this.stats = stats;
        }
    }

    static class DiskStat {

        final long nanoTime;
        final long sectorReads;
        final long diskReads;
        final long readTime;

        final long diskWrites;
        final long sectorWrites;
        final long writeTime;

        final long ioTime;

        public DiskStat(long nanoTime,
                long diskReads, long sectorReads, long readTime,
                long diskWrites, long sectorWrites, long writeTime,
                long ioTime) {
            this.nanoTime = nanoTime;
            this.diskReads = diskReads;
            this.sectorReads = sectorReads;
            this.readTime = readTime;

            this.sectorWrites = sectorWrites;
            this.diskWrites = diskWrites;
            this.writeTime = writeTime;

            this.ioTime = ioTime;
        }

        @Override
        public String toString() {
            return "DiskStat [nanoTime=" + nanoTime + ", sectorReads=" + sectorReads + ", diskReads=" + diskReads
                    + ", readTime=" + readTime + ", diskWrites=" + diskWrites + ", sectorWrites=" + sectorWrites
                    + ", writeTime=" + writeTime + ", ioTime=" + ioTime + "]";
        }
    }

    public static boolean hasDisksStats() {
        return new File("/proc/diskstats").canRead();
    }

}
