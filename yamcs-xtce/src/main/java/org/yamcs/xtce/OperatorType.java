package org.yamcs.xtce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum OperatorType { 
	EQUALITY, INEQUALITY, LARGERTHAN, LARGEROREQUALTHAN, SMALLERTHAN, SMALLEROREQUALTHAN;
	
	transient static Logger log=LoggerFactory.getLogger(OperatorType.class.getName());
	
	
    public static String operatorToString(OperatorType op) {
        switch (op) {
        case EQUALITY: {
            return "==";
        }
        case INEQUALITY: {
            return "!=";
        }
        case LARGERTHAN: {
            return ">";
        }
        case LARGEROREQUALTHAN: {
            return ">=";
        }
        case SMALLERTHAN: {
            return "<";
        }
        case SMALLEROREQUALTHAN: {
            return "<=";
        }
        }
        return "unknown";
    }

    public static OperatorType stringToOperator(String s) {
        if("==".equals(s)) {
            return OperatorType.EQUALITY;
        } else if("!=".equals(s)) {
            return OperatorType.INEQUALITY;
        } else if(">".equals(s)) {
            return OperatorType.LARGERTHAN;
        } else if(">=".equals(s)) {
            return OperatorType.LARGEROREQUALTHAN;
        } else if("<".equals(s)) {
            return OperatorType.SMALLERTHAN;
        } else if("<=".equals(s)) {
            return OperatorType.SMALLEROREQUALTHAN;
        } else {
            log.warn("unknown operator type "+s);
        }
        return null;
    }
}