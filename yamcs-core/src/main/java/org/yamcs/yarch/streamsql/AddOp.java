package org.yamcs.yarch.streamsql;

public enum AddOp {
  PLUS, MINUS, STRING_PLUS;
  public String getSign() {
    switch(this) { 
    case MINUS: return "-";
    case PLUS: return "+";
    case STRING_PLUS: return "+";
    default: return null;
    }
  }
}
