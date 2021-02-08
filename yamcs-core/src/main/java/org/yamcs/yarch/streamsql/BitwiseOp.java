package org.yamcs.yarch.streamsql;

public enum BitwiseOp {
    AND, OR, XOR, NOT, LSHIFT, RSHIFT;
  public String getSign() {
    switch(this) { 
        case AND:
            return "&";
        case OR:
            return "|";
        case XOR:
            return "^";
        case NOT:
            return "~";
        case LSHIFT:
            return "<<";
        case RSHIFT:
            return ">>";
    default: return null;
    }
  }
}
