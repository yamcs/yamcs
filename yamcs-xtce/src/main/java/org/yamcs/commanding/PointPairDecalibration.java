package org.yamcs.commanding;

import java.util.Arrays;

import org.yamcs.commanding.TcParameterDefinition.SwTypes;

public class PointPairDecalibration implements Decalibration {
	private static final long serialVersionUID = 200704191654L;
	PointPair[] pairs;
	
	public PointPairDecalibration(PointPair[] pairs) {
		this.pairs=pairs;
	}

	/**
	 * @param engValue 
	 * @param rawType 
	 * @param engType 
	 * @return raw value which can be either Long or Double 
	 * @throws DecalibrationNotSupportedException 
	 * 
	 */
	public Object decalibrate(Object engValue, SwTypes rawType, SwTypes engType) throws DecalibrationNotSupportedException {
		double ev=0;
		if(engValue instanceof Long) {
			ev=(double)((Long)engValue);
		} else if (engValue instanceof Double) {
			ev=(Double) engValue;
		} else {
			throw new DecalibrationNotSupportedException("Engineering value type "+engType+" is not supported for polynomial decalibration");
		}
		double val=0;
		
		int i=0,j=0;
        for(i=0;i<pairs.length;i++) {
                if(pairs[i].x>=ev) break;
        }
        if(i==0) {
                j=0;i=1;
        } else if(i==pairs.length) {
                j=pairs.length-2;
                i=pairs.length-1;
        } else {
                j=i-1;
        }
        double a1=pairs[i].x; double b1=pairs[i].y;
        double a2=pairs[j].x; double b2=pairs[j].y;
        val=((b1-b2)*ev+(a1*b2-b1*a2))/(a1-a2);


		switch(rawType) {
		case BYTE_TYPE:
		case INTEGER_TYPE:
		case UNSIGNED_INTEGER_TYPE:
			return Long.valueOf((long)val);
		case REAL_TYPE:
			return new Double(val);
			default:
				throw new DecalibrationNotSupportedException("Raw value type "+rawType+" is not supported for polynomial decalibration");
		}
	}
	
	public String toString() {
		return "PointPair"+Arrays.toString(pairs);
	}
}
