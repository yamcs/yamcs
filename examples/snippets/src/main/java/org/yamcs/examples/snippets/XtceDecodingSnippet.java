package org.yamcs.examples.snippets;

import java.util.logging.LogManager;

import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.TimeEncoding;

/**
 * Shows how to use Yamcs as a general-purpose library for decoding packets based on an XTCE XML definition.
 */
public class XtceDecodingSnippet {

    public static void main(String... args) {
        // Make Yamcs aware of leap seconds
        TimeEncoding.setUp();

        // Silence log messages from Yamcs libraries
        LogManager.getLogManager().reset();

        // The name here must match with an entry in mdb.yaml which by
        // default is scanned from the classpath.
        Mdb mdb = MdbFactory.createInstanceByConfig("example", false);
        XtceTmExtractor extractor = new XtceTmExtractor(mdb);

        // Tell the extractor to do a full scan. For efficiency reasons
        // you may also instruct it to scan only the parameters or containers
        // of your choice.
        extractor.provideAll();

        // Generate a fake packet that matches our example XTCE definition.
        byte[] p1 = new byte[] {
                (byte) 0xA7, // SyncByte1
                (byte) 0xF3, // SyncByte2
                (byte) ((5 << 5) | 31), // SubsystemID
                0x00, 0x00, 0x00, 0x06, // NumberOfDataBytesFollowing
                0x00, 0x10, 0x02, 0x00, 0x30, 0x04
        }; // 4 Samples

        long t = TimeEncoding.getWallclockTime();
        ContainerProcessingResult result = extractor.processPacket(p1, t, t, 0);

        System.out.printf("%-30s %-15s %-15s\n", "Parameter", "Engineering", "Raw");
        System.out.printf("%-30s %-15s %-15s\n", "---------", "-----------", "---");
        for (ParameterValue value : result.getParameterResult()) {
            String parameterName = value.getParameter().getName();
            Value engValue = value.getEngValue();
            Value rawValue = value.getRawValue();
            System.out.printf("%-30s %-15s %-15s\n", parameterName, engValue, rawValue);
        }
    }
}
