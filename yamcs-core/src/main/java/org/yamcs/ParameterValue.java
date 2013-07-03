package org.yamcs;

import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.xtce.FloatRange;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;

import com.google.protobuf.ByteString;

/** 
 * Holds the value of a parameter
 * @author mache
 *
 */
public class ParameterValue {
	public Parameter def;
	ParameterEntry entry;
	int absoluteBitOffset, bitSize;
	
	public Value rawValue;
	private Value engValue;
	private long acquisitionTime;
	private long generationTime;
	private AcquisitionStatus acquisitionStatus;
	private boolean processingStatus;
	public MonitoringResult monitoringResult;

	public FloatRange warningRange=null;
	public FloatRange criticalRange=null;
	
	
	/**
	 * Creates a parameter value for a parameter which has critical or warning range associated
	 * @param def the parameter definition
	 * @param withRawValue if the parameter contains a raw value or not
	 * @param criticalRange the critical range associated to the parameter. Can be null.
	 * @param warningRange the critical range associated to the parameter. Can be null.
	 */
	public ParameterValue(Parameter def, boolean withRawValue) {
		this.def=def;
		
		setAcquisitionStatus(AcquisitionStatus.ACQUIRED);
		setProcessingStatus(true);
		monitoringResult=MonitoringResult.DISABLED;
		/*
		if(withRawValue) {
			rawValue=new RawValueType();
		}*/
	}

	/**
	 * Called only from the cascading provider 
	public ParameterValue(Parameter def, TelemetryItemProperty[] itemProps) {
		this.def=def;
		for(TelemetryItemProperty ip:itemProps) {
			switch(ip.discriminator().value()) {
			case TelemetryItemProperties._ACQUISITION_STATUS :
				acquisitionStatus=ip; break;
			case TelemetryItemProperties._ACQUISITION_TIME :
				acquisitionTime=ip.acquisitionTime().time; break;
			case TelemetryItemProperties._DANGER_LIMIT_HIGH_VALUE :
				errorRangeHigh=ip; break;
			case TelemetryItemProperties._DANGER_LIMIT_LOW_VALUE :
				errorRangeLow=ip; break;
			case TelemetryItemProperties._ENGINEERING_VALUE :
				engValue=ip.value(); break;
			case TelemetryItemProperties._MONITORING_RESULT :
				monitoringResult=ip; break;
			case TelemetryItemProperties._DELTA_MONITORING_RESULT :
				deltaMonitoringResult=ip; break;
			case TelemetryItemProperties._SOFT_LIMIT_HIGH_VALUE :
				warningRangeHigh=ip; break;
			case TelemetryItemProperties._SOFT_LIMIT_LOW_VALUE :
				warningRangeLow=ip; break;
			case TelemetryItemProperties._PROCESSING_STATUS:
				processingStatus=ip;break;
				
			}
		}
	}
*/
	public int getAbsoluteBitOffset() {
		return absoluteBitOffset;
	}

	public void setAbsoluteBitOffset(int absoluteBitOffset) {
		this.absoluteBitOffset = absoluteBitOffset;
	}

	public int getBitSize() {
		return bitSize;
	}

	public void setBitSize(int bitSize) {
		this.bitSize = bitSize;
	}

	public void setParameterEntry(ParameterEntry entry) {
		this.entry = entry;
	}

	public ParameterEntry getParameterEntry() {
		return entry;
	}

	public void setParameter(Parameter parameter) {
		this.def=parameter;
	}
	
	public void setAcquisitionStatus(AcquisitionStatus a) {
		acquisitionStatus=a;
	}
	
	public void setAcquisitionTime(long instant) {
		acquisitionTime=instant;
	}
	
	public void setGenerationTime(long instant) {
		generationTime=instant;
	}
	
	public Value getEngValue() {
		return engValue;
	}

	public Value getRawValue() {
		return rawValue;
	}

	public void setWarningRange(FloatRange range) {
	    warningRange=range;
	}

	public FloatRange getWarningRange() {
		return warningRange;
	}

	public void setCriticalRange(FloatRange range) {
	    criticalRange=range;
	}

	public FloatRange getCriticalRange() {
		return criticalRange;
	}
	
	
	public void setMonitoringResult(MonitoringResult m) {
		monitoringResult=m;
	}

	
	public void setProcessingStatus(boolean p) {
		processingStatus=p;
	}

	
		/**
	 * Retrieve the parameter definition for this parameter value
	 * @return parameter definition
	 */
	public Parameter getParameter() {
		return def;
	}
	
	public long getGenerationTime() {
		return generationTime;
	}
	
	public MonitoringResult getMonitoringResult() {
		return monitoringResult;
	}

    public long getAcquisitionTime() {
        return acquisitionTime;
    }

    public AcquisitionStatus getAcquisitionStatus() {
        return acquisitionStatus;
    }

    public boolean getProcessingStatus() {
        return processingStatus;
    }

   
    public void setRawValue(Value rv) {
        this.rawValue=rv;
    }
    
    public void setRawValue(byte[] b) {
       rawValue=Value.newBuilder().setType(Value.Type.BINARY)
           .setBinaryValue(ByteString.copyFrom(b)).build();
        
    }
    
    public void setRawValue(float f) {
        rawValue=Value.newBuilder().setType(Value.Type.FLOAT)
            .setFloatValue(f).build();
    }
    
    public void setRawValue(double d) {
        rawValue=Value.newBuilder().setType(Value.Type.DOUBLE)
            .setDoubleValue(d).build();
    }
    
    public void setRawSignedInteger(int x) {
        rawValue=Value.newBuilder().setType(Value.Type.SINT32)
            .setSint32Value(x).build();
    }
    
    public void setRawUnsignedInteger(int x) {
        rawValue=Value.newBuilder().setType(Value.Type.UINT32)
            .setUint32Value(x).build();
    }
    
    public void setRawSignedLong(long x) {
        rawValue=Value.newBuilder().setType(Value.Type.SINT64)
            .setSint64Value(x).build();
    }
    
    public void setRawUnsignedLong(long x) {
        rawValue=Value.newBuilder().setType(Value.Type.UINT64)
            .setUint64Value(x).build();
    }
    
    public void setStringValue(String s) {
        engValue=Value.newBuilder().setType(Value.Type.STRING)
            .setStringValue(s).build();
    }
    public void setBinaryValue(byte[] v) {
        engValue = Value.newBuilder().setType(Value.Type.BINARY)
            .setBinaryValue(ByteString.copyFrom(v)).build();
    }
    
    public void setDoubleValue(double v) {
        engValue = Value.newBuilder().setType(Value.Type.DOUBLE)
            .setDoubleValue(v).build();
    }

    public void setFloatValue(float v) {
        engValue = Value.newBuilder().setType(Value.Type.FLOAT)
            .setFloatValue(v).build();
    }
    
    public void setSignedIntegerValue(int v) {
        engValue = Value.newBuilder().setType(Value.Type.SINT32)
            .setSint32Value(v).build();
    }
    
    public void setUnsignedIntegerValue(int v) {
        engValue = Value.newBuilder().setType(Value.Type.UINT32)
            .setUint32Value(v).build();
    }
    
    public void setSignedLongValue(long v) {
        engValue = Value.newBuilder().setType(Value.Type.SINT64)
            .setSint64Value(v).build();
    }
    
    public void setUnsignedLongValue(long v) {
        engValue = Value.newBuilder().setType(Value.Type.UINT64)
            .setUint64Value(v).build();
    }

    public void setEngineeringValue(Value ev) {
        this.engValue=ev;
    }

    
    public org.yamcs.protobuf.Pvalue.ParameterValue toGpb(NamedObjectId id) {
        org.yamcs.protobuf.Pvalue.ParameterValue.Builder gpvb=org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder()
        .setAcquisitionStatus(getAcquisitionStatus())
        .setAcquisitionTime(getAcquisitionTime())
        .setEngValue(getEngValue())
        .setGenerationTime(getGenerationTime())
        .setMonitoringResult(getMonitoringResult())
        .setProcessingStatus(getProcessingStatus());
        
        if(id!=null) gpvb.setId(id);
        if(getRawValue()!=null) gpvb.setRawValue(getRawValue());
        return gpvb.build();
    }
    
    public static ParameterValue fromGpb(Parameter pdef, org.yamcs.protobuf.Pvalue.ParameterValue gpv) {
        ParameterValue pv=new ParameterValue(pdef, gpv.hasRawValue());
        pv.setAcquisitionStatus(gpv.getAcquisitionStatus());
        pv.setAcquisitionTime(gpv.getAcquisitionTime());
        pv.setEngineeringValue(gpv.getEngValue());
        pv.setGenerationTime(gpv.getGenerationTime());
        pv.setMonitoringResult(gpv.getMonitoringResult());
        pv.setProcessingStatus(gpv.getProcessingStatus());
        if(gpv.hasRawValue()) {
            pv.setRawValue(gpv.getRawValue());
        }
        return pv;
    }
    
    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("name: ").append(def.getName());
        if(rawValue!=null) sb.append(" rawValue: {").append(rawValue.toString()).append("}");
        if(engValue!=null) sb.append(" engValue: {").append(engValue.toString()).append("}");
        return sb.toString();
    }
}