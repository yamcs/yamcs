package org.yamcs.xtce;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple ParameterInstanceRef to value comparison.  
 * DIFFERS_FROM_XTCE:
 * 1) in xtce the value is stored as a string and it's not very clear how it's compared with an integer
 * 2) in xtce Comparison extends ParameterInstanceRef, and MatchCriteria is a choice of Comparison, ComparisonList, ...
 */
public class Comparison implements MatchCriteria {
	private static final long serialVersionUID = 200805131551L;
	ParameterInstanceRef instanceRef;
	
	OperatorType comparisonOperator;
	public enum OperatorType { EQUALITY, INEQUALITY, LARGERTHAN, LARGEROREQUALTHAN, SMALLERTHAN, SMALLEROREQUALTHAN };
	
	ValueType valueType;
	public enum ValueType { DOUBLE, LONG, STRING };
	
	//the string is used to create the object and then is changed to the other type, depending on the valueType
	String stringValue;
	double doubleValue;
	long longValue;
	byte[] binaryValue;
	
	transient static Logger log=LoggerFactory.getLogger(Comparison.class.getName());

	
	public Comparison(ParameterInstanceRef instanceRef, long longValue, OperatorType op) {
        this.instanceRef = instanceRef;
        this.comparisonOperator = op;
        setLongValue(longValue);
    }

	public Comparison(ParameterInstanceRef paraRef, String stringValue, OperatorType op) {
        this.instanceRef = paraRef;
        this.stringValue=stringValue;
        this.valueType=ValueType.STRING;
        this.comparisonOperator = op;
    }
	
	
	public Comparison(ParameterInstanceRef instanceRef, double doubleValue, OperatorType op) {
	    this.instanceRef = instanceRef;
	    setDoubleValue(doubleValue);
        this.comparisonOperator = op;
    }

    public ParameterInstanceRef getParameterRef() {
		return instanceRef;
	}
	
	public OperatorType getComparisonOperator() {
	    return comparisonOperator;
	}

	public ValueType getValueType() {
	    return valueType;
	}
	
	@Override
	public Set<Parameter> getDependentParameters() {
		Set<Parameter> pset=new HashSet<Parameter>();
		pset.add(instanceRef.getParameter());
		return pset;
	}

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

	public Parameter getParameter() {
		return instanceRef.getParameter();
	}
	
	public double getDoubleValue(){
	    return doubleValue;
	}
	
	public long getLongValue(){
        return longValue;
    }
	
	public String getStringValue(){
        return stringValue;
    }
    
	public byte[] getBinaryValue(){
        return binaryValue;
    }
    
	public void setDoubleValue(double doubleValue) {
	    this.doubleValue=doubleValue;
        this.stringValue=Double.toString(doubleValue);
        this.valueType=ValueType.DOUBLE;
	}

	@Override
    public String toString() {
	    return "Comparison: paraName="+ instanceRef.getParameter().getName() + 
            operatorToString(comparisonOperator) + stringValue;
	}

    public void setLongValue(long longValue) {
        this.longValue=longValue;
        this.stringValue=Long.toString(longValue);
        this.valueType=ValueType.LONG;
    }

  
   
}
