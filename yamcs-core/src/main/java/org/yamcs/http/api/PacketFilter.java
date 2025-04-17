package org.yamcs.http.api;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.utils.parser.Filter;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.UnknownFieldException;
import org.yamcs.yarch.Tuple;

public class PacketFilter extends Filter<Tuple> {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_LINK = "link";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_SEQ_NUMBER = "seqNumber";
    private static final String FIELD_BINARY = "binary";

    private String lcName;
    private String lcLink;

    public PacketFilter(String query) throws ParseException, UnknownFieldException {
        super(query);
        addStringField(FIELD_NAME, this::getName);
        addStringField(FIELD_LINK, this::getLink);
        addNumberField(FIELD_SIZE, this::getSize);
        addNumberField(FIELD_SEQ_NUMBER, this::getSequenceNumber);
        addBinaryField(FIELD_BINARY, this::getBinary);
        parse();
    }

    @Override
    public void beforeItem(Tuple tuple) {
        // Preload lowercase variants to boost non-field text search
        // with multiple terms

        // Reset previous state
        lcName = null;
        lcLink = null;

        if (includesTextSearch()) {
            if (tuple.hasColumn(XtceTmRecorder.PNAME_COLUMN)) {
                lcName = getName(tuple).toLowerCase();
            }
            if (tuple.hasColumn(StandardTupleDefinitions.TM_LINK_COLUMN)) {
                lcLink = getLink(tuple).toLowerCase();
            }
        }
    }

    private String getName(Tuple tuple) {
        return tuple.getColumn(XtceTmRecorder.PNAME_COLUMN);
    }

    private String getLink(Tuple tuple) {
        return tuple.getColumn(StandardTupleDefinitions.TM_LINK_COLUMN);
    }

    private int getSize(Tuple tuple) {
        var data = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
        return data.length;
    }

    private Number getSequenceNumber(Tuple tuple) {
        return tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
    }

    private byte[] getBinary(Tuple tuple) {
        return (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
    }

    @Override
    protected boolean matchesLiteral(Tuple item, String lowercaseLiteral) {
        return (lcName != null && lcName.contains(lowercaseLiteral))
                || (lcLink != null && lcLink.contains(lowercaseLiteral));
    }
}
