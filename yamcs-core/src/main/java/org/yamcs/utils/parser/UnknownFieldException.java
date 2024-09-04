package org.yamcs.utils.parser;

@SuppressWarnings("serial")
public class UnknownFieldException extends ParseException {

    private String field;

    public UnknownFieldException(String field, Token currentToken, String[] tokenImage) {
        super("Field not found '" + field + "'");
        this.field = field;
        this.currentToken = currentToken;
        this.tokenImage = tokenImage;
    }

    public String getField() {
        return field;
    }

    public String getKind() {
        return tokenImage[currentToken.kind];
    }
}
