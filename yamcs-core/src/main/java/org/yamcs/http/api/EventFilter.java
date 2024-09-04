package org.yamcs.http.api;

import static org.yamcs.StandardTupleDefinitions.BODY_COLUMN;
import static org.yamcs.StandardTupleDefinitions.SEQNUM_COLUMN;
import static org.yamcs.StandardTupleDefinitions.SOURCE_COLUMN;

import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.utils.parser.Filter;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.UnknownFieldException;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db;

public class EventFilter extends Filter<Tuple> {

    public EventFilter(String query) throws ParseException, UnknownFieldException {
        super(query);
        addEnumField("severity", EventSeverity.class, this::getSeverity);
        addStringField("message", this::getMessage);
        addStringField("source", this::getSource);
        addStringField("type", this::getType);
        addNumberField("seqNumber", this::getSequenceNumber);
        parse();
    }

    private String getMessage(Tuple tuple) {
        var event = (Db.Event) tuple.getColumn(BODY_COLUMN);
        return event.hasMessage() ? event.getMessage() : null;
    }

    private String getSource(Tuple tuple) {
        return tuple.getColumn(SOURCE_COLUMN);
    }

    private String getType(Tuple tuple) {
        var event = (Db.Event) tuple.getColumn(BODY_COLUMN);
        return event.hasType() ? event.getType() : null;
    }

    private Number getSequenceNumber(Tuple tuple) {
        return tuple.getColumn(SEQNUM_COLUMN);
    }

    private EventSeverity getSeverity(Tuple tuple) {
        var event = (Db.Event) tuple.getColumn(BODY_COLUMN);
        return event.hasSeverity() ? event.getSeverity() : null;
    }

    @Override
    protected boolean matchesLiteral(Tuple tuple, String literal) {
        var event = (Db.Event) tuple.getColumn(BODY_COLUMN);
        if (event.getMessage().toLowerCase().contains(literal)) {
            return true;
        }

        if (event.getSource().toLowerCase().contains(literal)) {
            return true;
        }

        if (event.getType().toLowerCase().contains(literal)) {
            return false;
        }

        return false;
    }
}
