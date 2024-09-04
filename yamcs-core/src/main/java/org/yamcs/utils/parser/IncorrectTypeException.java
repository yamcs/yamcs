package org.yamcs.utils.parser;

@SuppressWarnings("serial")
public class IncorrectTypeException extends ParseException {

    private String value;

    public IncorrectTypeException(String value, Token currentToken, String[] tokenImage) {
        super("Value '" + value + "' is of incorrect type");
        this.value = value;
        this.currentToken = currentToken;
        this.tokenImage = tokenImage;
    }

    public String getValue() {
        return value;
    }

    public String getKind() {
        return tokenImage[currentToken.kind];
    }
}
