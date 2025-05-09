package org.yamcs.http.api;

import java.util.List;

import org.yamcs.protobuf.ArchivedParameterInfo;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.utils.parser.Filter;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.UnknownFieldException;

public class ArchivedParameterFilter extends Filter<ArchivedParameterInfo> {

    private static final String FIELD_PID = "pid";
    private static final String FIELD_PARAMETER = "parameter";
    private static final String FIELD_RAW_TYPE = "rawType";
    private static final String FIELD_ENG_TYPE = "engType";
    private static final String FIELD_GID = "gid";

    private String lcParameter;

    public ArchivedParameterFilter(String query) throws ParseException, UnknownFieldException {
        super(query);
        addNumberField(FIELD_PID, this::getPid);
        addStringField(FIELD_PARAMETER, this::getParameter);
        addEnumField(FIELD_RAW_TYPE, Value.Type.class, this::getRawType);
        addEnumField(FIELD_ENG_TYPE, Value.Type.class, this::getEngType);
        addNumberCollectionField(FIELD_GID, this::getGids);
        parse();
    }

    @Override
    public void beforeItem(ArchivedParameterInfo info) {
        // Preload lowercase variants to boost non-field text search
        // with multiple terms

        // Reset previous state
        lcParameter = null;

        if (includesTextSearch()) {
            lcParameter = info.getParameter().toLowerCase();
        }
    }

    private Number getPid(ArchivedParameterInfo info) {
        return info.getPid();
    }

    private String getParameter(ArchivedParameterInfo info) {
        return info.getParameter();
    }

    private Value.Type getRawType(ArchivedParameterInfo info) {
        return info.hasRawType() ? info.getRawType() : null;
    }

    private Value.Type getEngType(ArchivedParameterInfo info) {
        return info.hasEngType() ? info.getEngType() : null;
    }

    private List<? extends Number> getGids(ArchivedParameterInfo info) {
        return info.getGidsList();
    }

    @Override
    protected boolean matchesLiteral(ArchivedParameterInfo info, String lowercaseLiteral) {
        return (lcParameter != null && lcParameter.contains(lowercaseLiteral));
    }
}
