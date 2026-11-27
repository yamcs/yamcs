package org.yamcs.simulator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.simulator.pus.PusPacketConfig;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.IParameterSplitter;
import com.beust.jcommander.converters.PathConverter;

public class SimulatorArgs {

    @Parameter(names = "--telnet-port")
    public Integer telnetPort = 10023;

    @Parameter(names = "--tc-port")
    public Integer tcPort = 10025;

    @Parameter(names = "--tm-port")
    public Integer tmPort = 10015;

    @Parameter(names = "--tm2-port")
    public Integer tm2Port = 10016;

    @Parameter(names = "--los-port")
    public Integer losPort = 10115;

    @Parameter(names = "--scid", description = "spacecraft identifier (between 0 and 255)")
    public Integer scid = 0xAB;

    @Parameter(names = "--tm-frame-type", description = "which frame type to send: TM, AOS or USLP")
    public String tmFrameType = "AOS";

    @Parameter(names = "--tm-frame-host", description = "the UDP host where to send TM/AOS/USLP frames")
    public String tmFrameHost = "localhost";

    @Parameter(names = "--tm-frame-port", description = "the UDP port where to send TM/AOS/USLP frames")
    public int tmFramePort = 10017;

    @Parameter(names = "--tc-frame-port", description = "the UDP port where the simulator listens for TC frames")
    public int tcFramePort = 10018;

    @Parameter(names = "--tc-frame-encoding", description = "TC frame UDP encoding: BCH, LDPC64, LDPC256 or RAW")
    public String tcFrameEncoding = "BCH";

    @Parameter(names = "--tc-max-frame-length", description = "maximum accepted TC transfer frame length")
    public int tcMaxFrameLength = TcVcFrameLink.MAX_FRAME_LENGTH;

    @Parameter(names = "--tc-frame-error-detection", description = "TC frame error detection: CRC16 or NONE")
    public String tcFrameErrorDetection = "CRC16";

    @Parameter(names = "--tm-frame-error-detection", description = "TM frame error detection: CRC16 or NONE")
    public String tmFrameErrorDetection = "CRC16";

    @Parameter(names = "--tm-ocf-present", arity = 1, description = "include the TM frame operational control field")
    public boolean tmOcfPresent = true;

    @Parameter(names = "--uslp-tc-frame-port", description = "the UDP port where the simulator listens for USLP frames (0 = disabled)")
    public int uslpTcFramePort = 0;

    @Parameter(names = "--tm-frame-length", description = "the TM/AOS/USLP frame length (set to 0 to disable the " +
            "frame functionality)")
    public int tmFrameLength = 0;

    @Parameter(names = "--tm-frame-freq", description = "the number of TM frames to send per second")
    public double tmFrameFreq = 10;

    @Parameter(names = "--perf-np", description = "performance test: number of packets. Set to 0 to disable sending " +
            "the performance packets")
    public int perfNp = 0;

    @Parameter(names = "--perf-ps", description = "performance test: packet size")
    public int perfPs = 1400;

    @Parameter(names = "--perf-ms", description = "performance test: interval in between batch of packets in " +
            "milliseconds")
    public long perfMs = 100l;

    @Parameter(names = "--perf-cp", description = "performance test: percentange (0-100) of data changed between two " +
            "subsequent versions of the same packet")
    public float perfChangePercent = 10;

    @Parameter(names = "--type", description = "one of: pus or col")
    public String type = "col";

    @Parameter(names = "--no-tcp-tmtc", description = "disable TCP TM/TC/LOS packet links")
    public boolean noTcpTmtc;

    @Parameter(names = "--pus-storage-time-pfield", description = "ST15 implicit CUC p-field")
    public int pusStorageTimePfield = 0x2E;

    @Parameter(names = "--pus-storage-time-pfield-cont", description = "ST15 CUC extension octet")
    public int pusStorageTimePfieldCont = -1;

    @Parameter(names = "--pus-storage-time-epoch", description = "ST15 CUC epoch")
    public String pusStorageTimeEpoch = "UNIX";

    @Parameter(names = "--pus-time-type", description = "PUS time encoding type")
    public String pusTimeType = "CUC";

    @Parameter(names = "--pus-time-epoch", description = "PUS time epoch: SIMULATOR or UNIX")
    public String pusTimeEpoch = "SIMULATOR";

    @Parameter(names = "--pus-time-pfield", description = "PUS CUC time p-field")
    public int pusTimePfield = 0x2F;

    @Parameter(names = "--pus-time-pfield-cont", description = "PUS CUC time continuation p-field")
    public int pusTimePfieldCont = -1;

    @Parameter(names = "--pus-time-implicit-pfield", description = "do not emit the PUS CUC p-field on the wire")
    public boolean pusTimeImplicitPfield = false;

    @Parameter(names = "--pus-apid")
    public int pusApid = 1;

    @Parameter(names = "--pus-spare-bytes")
    public int pusSpareBytes = 4;

    @Parameter(names = "--pus-packet-store-size-unit-bytes", description = "bytes per S15 store capacity unit")
    public int pusPacketStoreSizeUnitBytes = PusPacketConfig.DEFAULT_PACKET_STORE_SIZE_UNIT_BYTES;

    @Parameter(names = "--pus-telemetry-mdb", converter = PathConverter.class, splitter = MdbPathSplitter.class,
            description = "XTCE file for PUS telemetry (repeat for multiple files)")
    public List<Path> pusTelemetryMdbFiles = new ArrayList<>();

    @Parameter(names = "--pus-housekeeping-period")
    public int pusHousekeepingPeriod = 200;

    @Parameter(names = "--pus-event-period")
    public int pusEventPeriod = 1000;

    @Parameter(names = "--pus-tm-packet-crc", arity = 1)
    public boolean pusTmPacketCrc = true;

    @Parameter(names = "--pus-tc-packet-crc", arity = 1)
    public boolean pusTcPacketCrc = true;

    @Parameter(names = "--pus-embedded-tc-crc", arity = 1)
    public Boolean pusEmbeddedTcCrc;

    @Parameter(names = "--pus11-wrapper-crc", arity = 1)
    public Boolean pus11WrapperCrc;

    @Parameter(names = "--pus22-wrapper-crc", arity = 1)
    public Boolean pus22WrapperCrc;

    @Parameter(names = "--pus11-tc-implicit-p-field", arity = 1)
    public Boolean pus11TcImplicitPfield;

    @Parameter(names = "--pus11-subschedule-bytes")
    public int pus11SubScheduleBytes = 1;

    @Parameter(names = "--pus11-subschedule-default")
    public long pus11SubScheduleDefault;

    @Parameter(names = "--pus11-group-bytes")
    public int pus11GroupBytes = 1;

    @Parameter(names = "--pus11-group-default")
    public long pus11GroupDefault;

    @Parameter(names = "--los-dir", converter = PathConverter.class)
    public Path losDir = Path.of("losData");

    @Parameter(names = "--data-dir", converter = PathConverter.class)
    public Path dataDir = Path.of("data");

    @Parameter(names = "--encryption-class", description = "The SDLS implementation to use")
    public String encryptionClass;

    @Parameter(names = "--encryption-spi", description = "SPI to use for SDLS encryption of frames")
    public int encryptionSpi;

    @Parameter(names = "--encryption-args", description = "Arguments to pass to the SDLS implementation")
    public String encryptionArgs;

    public static class MdbPathSplitter implements IParameterSplitter {
        @Override
        public List<String> split(String path) {
            return List.of(path);
        }
    }
}
