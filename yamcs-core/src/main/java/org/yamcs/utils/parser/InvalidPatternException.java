package org.yamcs.utils.parser;

@SuppressWarnings("serial")
public class InvalidPatternException extends ParseException {

    private String pattern;

    public InvalidPatternException(String pattern, Token currentToken, String[] tokenImage) {
        super("Invalid regex '" + pattern + "'");
        this.pattern = pattern;
        this.currentToken = currentToken;
        this.tokenImage = tokenImage;
    }

    public String getPattern() {
        return pattern;
    }

    public String getKind() {
        return tokenImage[currentToken.kind];
    }
}
