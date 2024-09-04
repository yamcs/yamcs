package org.yamcs.http.api;

import org.yamcs.api.FilterSyntaxException;
import org.yamcs.http.BadRequestException;
import org.yamcs.utils.parser.IncorrectTypeException;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.TokenMgrError;
import org.yamcs.utils.parser.UnknownFieldException;

public class EventFilterFactory {

    public static EventFilter create(String query) {
        try {
            return new EventFilter(query);
        } catch (UnknownFieldException | IncorrectTypeException e) {
            throw mapCustomParseException(e);
        } catch (ParseException e) {
            throw mapParseException(e);
        } catch (TokenMgrError e) {
            throw mapTokenMgrError(e);
        }
    }

    private static BadRequestException mapCustomParseException(ParseException e) {
        var exc = new BadRequestException(e.getMessage());
        if (e.currentToken != null) {
            exc.setDetail(FilterSyntaxException.newBuilder()
                    .setBeginLine(e.currentToken.beginLine)
                    .setBeginColumn(e.currentToken.beginColumn)
                    .setEndLine(e.currentToken.endLine)
                    .setEndColumn(e.currentToken.endColumn)
                    .build());
        }
        throw exc;
    }

    private static BadRequestException mapParseException(ParseException e) {
        var exc = new BadRequestException("Syntax error in filter");
        if (e.currentToken != null) {
            exc.setDetail(FilterSyntaxException.newBuilder()
                    .setBeginLine(e.currentToken.beginLine)
                    .setBeginColumn(e.currentToken.beginColumn)
                    .setEndLine(e.currentToken.endLine)
                    .setEndColumn(e.currentToken.endColumn)
                    .build());
        }
        throw exc;
    }

    private static BadRequestException mapTokenMgrError(TokenMgrError e) {
        var exc = new BadRequestException("Syntax error in filter");
        if (e.errorLine >= 0 && e.errorColumn >= 0) {
            exc.setDetail(FilterSyntaxException.newBuilder()
                    .setBeginLine(e.errorLine)
                    .setBeginColumn(e.errorColumn)
                    .setEndLine(e.errorLine)
                    .setEndColumn(e.errorColumn)
                    .build());
        }
        throw exc;
    }
}
