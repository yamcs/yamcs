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

    private static final String FIELD_SEVERITY = "severity";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_SEQ_NUMBER = "seqNumber";

    private String lcMessage;
    private String lcSource;
    private String lcType;

    public EventFilter(String query) throws ParseException, UnknownFieldException {
        super(query);
        addEnumField(FIELD_SEVERITY, EventSeverity.class, this::getSeverity);
        addStringField(FIELD_MESSAGE, this::getMessage);
        addStringField(FIELD_SOURCE, this::getSource);
        addStringField(FIELD_TYPE, this::getType);
        addNumberField(FIELD_SEQ_NUMBER, this::getSequenceNumber);
        parse();
    }

    @Override
    public void beforeItem(Tuple tuple) {
        // Preload lowercase variants to boost non-field text search
        // with multiple terms

        // Reset previous state
        lcMessage = null;
        lcSource = null;
        lcType = null;

        if (includesTextSearch()) {
            var event = (Db.Event) tuple.getColumn(BODY_COLUMN);
            if (event.hasMessage()) {
                lcMessage = event.getMessage().toLowerCase();
            }
            if (event.hasSource()) {
                lcSource = event.getSource().toLowerCase();
            }
            if (event.hasType()) {
                lcType = event.getType().toLowerCase();
            }
        }
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
    protected boolean matchesLiteral(Tuple tuple, String lowercaseLiteral) {
        return (lcMessage != null && lcMessage.contains(lowercaseLiteral))
                || (lcSource != null && lcSource.contains(lowercaseLiteral))
                || (lcType != null && lcType.contains(lowercaseLiteral));
    }
}
