package org.yamcs.tctm.ccsds;

import java.util.Arrays;

import org.yamcs.ConfigurationException;
import org.yamcs.time.Instant;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * <p>
 * Saves frames into streams.
 * <p>
 * Creates the streams if they don't already exist
 * 
 */
public class FrameStreamHelper {

    final static String RECTIME_CNAME = "rectime";
    final static String SEQ_CNAME = "seq";

    final static String ERTIME_CNAME = "ertime";
    final static String SCID_CNAME = "scid";
    final static String VCID_CNAME = "vcid";
    final static String DATA_CNAME = "data";
    final static String ERROR_CNAME = "error";

    static TupleDefinition gftdef;
    static TupleDefinition bftdef;

    static {
        gftdef = new TupleDefinition();
        gftdef.addColumn(new ColumnDefinition(RECTIME_CNAME, DataType.TIMESTAMP));
        gftdef.addColumn(new ColumnDefinition(SEQ_CNAME, DataType.INT));
        gftdef.addColumn(new ColumnDefinition(ERTIME_CNAME, DataType.HRES_TIMESTAMP));
        gftdef.addColumn(new ColumnDefinition(SCID_CNAME, DataType.INT));
        gftdef.addColumn(new ColumnDefinition(VCID_CNAME, DataType.INT));
        gftdef.addColumn(new ColumnDefinition(DATA_CNAME, DataType.BINARY));

        bftdef = new TupleDefinition();
        bftdef.addColumn(new ColumnDefinition(RECTIME_CNAME, DataType.TIMESTAMP));
        bftdef.addColumn(new ColumnDefinition(SEQ_CNAME, DataType.INT));
        bftdef.addColumn(new ColumnDefinition(ERTIME_CNAME, DataType.HRES_TIMESTAMP));
        bftdef.addColumn(new ColumnDefinition(DATA_CNAME, DataType.BINARY));
        bftdef.addColumn(new ColumnDefinition(ERROR_CNAME, DataType.STRING));

    }
    Stream goodFrameStream;
    Stream badFrameStream;

    public FrameStreamHelper(String yamcsInstance, String goodFrameStreamName, String badFrameStreamName) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        if (goodFrameStreamName != null) {
            goodFrameStream = getStream(ydb, goodFrameStreamName);
        }
        if (badFrameStreamName != null) {
            badFrameStream = getStream(ydb, badFrameStreamName);
        }
    }

    private static Stream getStream(YarchDatabaseInstance ydb, String streamName) {
        Stream stream = ydb.getStream(streamName);
        if (stream == null) {
            try {
                ydb.execute("create stream " + streamName + gftdef.getStringDefinition());
            } catch (Exception e) {
                throw new ConfigurationException(e);
            }
            stream = ydb.getStream(streamName);
        }
        return stream;
    }

    public void sendGoodFrame(int seq, DownlinkTransferFrame frame, byte[] data, int offset, int length) {
        if (goodFrameStream == null) {
            return;
        }
        long rectime = TimeEncoding.getWallclockTime();
        goodFrameStream.emitTuple(new Tuple(gftdef, Arrays.asList(rectime, seq, frame.getEarthRceptionTime(),
                frame.getSpacecraftId(), frame.getVirtualChannelId(),
                getData(data, offset, length))));
    }

    public void sendBadFrame(int seq, Instant ertime, byte[] data, int offset, int length, String errMsg) {
        if (badFrameStream == null) {
            return;
        }
        long rectime = TimeEncoding.getWallclockTime();
        badFrameStream.emitTuple(
                new Tuple(bftdef, Arrays.asList(rectime, seq, ertime, getData(data, offset, length), errMsg)));
    }

    private byte[] getData(byte[] data, int offset, int length) {
        if (offset == 0 && length == data.length) {
            return data;
        } else {
            return Arrays.copyOfRange(data, offset, offset + length);
        }
    }
}
