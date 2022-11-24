package org.yamcs.xtce;

public enum OperatorType {
    EQUALITY("=="),
    INEQUALITY("!="),
    LARGERTHAN(">"),
    LARGEROREQUALTHAN(">="),
    SMALLERTHAN("<"),
    SMALLEROREQUALTHAN("<=");

    private String symbol;

    private OperatorType(String symbol) {
        this.symbol = symbol;
    }

    public static OperatorType fromSymbol(String symbol) {
        for (OperatorType type : values()) {
            if (type.symbol.equals(symbol)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unexpected symbol '" + symbol + "'");
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return getSymbol();
    }
}
