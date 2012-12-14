package org.yamcs.splc;

import java.nio.ByteBuffer;

import org.yamcs.usoctools.PayloadModel;



public class SslMonAttrib {

	private class monPar_t	 {

		public monPar_t(ByteBuffer data, SSL_PAR_TYPE type) {
			this.type=type;
			intPar=data.getInt();
			switch (type.value) {
			case SSL_PAR_TYPE.SSL_BOOL_T:
				boolPar=intPar;
				break;
			case SSL_PAR_TYPE.SSL_UINT_T:
				uintPar=intPar;
				break;
			case SSL_PAR_TYPE.SSL_INT_T:
				break;
			case SSL_PAR_TYPE.SSL_FLOAT_T:
				floatPar=Float.intBitsToFloat(intPar);
				break;
			default: 
				System.err.println("unknown type: "+type.value);
			}	
		}
		/**
		 * Returns a string representation of the parameter depending on its type
		 */
		public String toString() {

			String stringValue;
			switch (type.value) {
			case SSL_PAR_TYPE.SSL_BOOL_T:
				stringValue=String.valueOf(boolPar);
				break;
			case SSL_PAR_TYPE.SSL_UINT_T:
				stringValue=String.valueOf(uintPar);
				break;
			case SSL_PAR_TYPE.SSL_INT_T:
				stringValue=String.valueOf(intPar);
				break;
			case SSL_PAR_TYPE.SSL_FLOAT_T:
				stringValue=String.valueOf(floatPar);
				break;
			default: 
				stringValue="unknown type: "+type.value;
			}
			return stringValue;
		}
		int           boolPar;
		int  		  uintPar;
		int           intPar;
		float         floatPar;
		SSL_PAR_TYPE  type;

	};

	private class SSL_PAR_TYPE {
		public SSL_PAR_TYPE(byte value) {
			this.value=value;
		}
		
		public String toString() {
			String s;
			switch (type.value) {
			case SSL_PAR_TYPE.SSL_BOOL_T:
				s="BOOL";
				break;
			case SSL_PAR_TYPE.SSL_UINT_T:
				s="UINT";
				break;
			case SSL_PAR_TYPE.SSL_INT_T:
				s="INT";
				break;
			case SSL_PAR_TYPE.SSL_FLOAT_T:
				s="float";
				break;
			default:
				s="UNKNOWN_TYPE";
				break;
			}
			return s;
		}
		
		public byte value;
		public static final byte  SSL_BOOL_T  = 1;
		public static final byte  SSL_UINT_T  = 2;
		public static final byte  SSL_INT_T   = 3;
		public static final byte  SSL_FLOAT_T = 4;
	}

	private class SSL_TRG_STATE {
		public short value;
		
		public String toString() {
			String s;
			switch (value) {
			case SSL_TRG_NORMAL:
				s="NORMAL   ";
				break;
			case SSL_TRG_TO_LOW:
				s="TO_LOW   ";
				break;
			case SSL_TRG_TO_HIGH:
				s="TO_HIGH  ";
				break;
			case SSL_TRG_LIMITS:
				s="LIMITS   ";
				break;
			default:
				s="UNKNOWN! ";
			}
			return s;
		}
		
		public static final short SSL_TRG_NORMAL   = 0;   /* no transgressions */
		public static final short SSL_TRG_TO_LOW   = 1;
		public static final short SSL_TRG_TO_HIGH  = 2;
		public static final short SSL_TRG_LIMITS   = 3;   
	}

	PayloadModel payloadModel;
        public SslMonAttrib(ByteBuffer data, PayloadModel payloadModel) {
	        this.payloadModel=payloadModel;
		//System.err.println("current position:"+data.position()+" limit:"+data.);
		setParId(data.getInt());
		type=new SSL_PAR_TYPE(data.get()); 
		data.position(data.position()+3);//skip three bytes because the splc allignes each thing on 4
		curValue=new monPar_t(data,type);
		warnLo=new monPar_t(data,type);
		warnHi=new monPar_t(data,type);
		actLo=new monPar_t(data,type);
		actHi=new monPar_t(data,type);
		enable=data.getInt();
		checkAllXScans=data.getInt();
		curWarn=data.getInt();
		curViol=data.getInt();
		maxViol=data.getInt();          
		curOK=data.getInt();
		trgState=new SSL_TRG_STATE();
		trgState.value=(short)data.getInt(); 
	}
	
	String getOverLimitsMessage(int id) {
		String trgStr, cmpVal;
		switch (trgState.value) {
		case SSL_TRG_STATE.SSL_TRG_NORMAL:
			trgStr="normal";
			cmpVal = "";
			break;
		case SSL_TRG_STATE.SSL_TRG_TO_LOW:
			trgStr="<";
			if (id == SSLEventDecoder.SSL_EVENT_MON_WARN_ActionLimit_EXCEED)
				cmpVal = actLo.toString();
			else
				cmpVal = warnLo.toString();
			break;
		case SSL_TRG_STATE.SSL_TRG_TO_HIGH:
			trgStr=">";
			if (id == SSLEventDecoder.SSL_EVENT_MON_WARN_ActionLimit_EXCEED)
				cmpVal = actHi.toString();
			else
				cmpVal = warnHi.toString();
			break;
		case SSL_TRG_STATE.SSL_TRG_LIMITS:
			trgStr="=";
			if (id == SSLEventDecoder.SSL_EVENT_MON_WARN_ActionLimit_EXCEED)
				cmpVal = actHi.toString();
			else
				cmpVal = warnHi.toString();
			break;
		default:
			trgStr= "UNKNOWN!";
		cmpVal = "";
		break;
		}
		return "parameter" + " " + getParId() + "("+payloadModel.getMonParameterName(getParId())+") currentValue " + curValue + trgStr+ cmpVal;
	}

	public String toString() {
		String s;
		s= "parameter type="+type+", curValue="+curValue.toString()
			+ "\n\tlimits: "+ warnLo+" "+warnHi+" "+actLo+" "+actHi
			+ "\n\tenable: "+enable+", checks: "+checkAllXScans
		        + "\n\tcurWarn: " + curWarn + ", curViol: " +curViol + ", maxViol: " +maxViol + ", curOK: " +curOK 
                        + "\n\ttrgState: " + trgState;
		return s;
	}
	
	public void setParId(int parId) {
        this.parId = parId;
    }

    public int getParId() {
        return parId;
    }

    private int             parId;            /* parameter Id */
	SSL_PAR_TYPE    type;             /* 0=BOOL, 1=UINT, 2=int, 3=float   */
	monPar_t        curValue;         /* last monitored value of the parameter  */
	monPar_t        warnLo;
	monPar_t        warnHi;
	monPar_t        actLo;
	monPar_t        actHi;
	int             enable;           /* flag, whether par. is to monitor */
	int             checkAllXScans;
	int             curWarn;          /* Current count of repeated warning limit violations */
	int             curViol;          /* Current count of repeated action limit violations */
	int             maxViol;          /* Max. # of allowed limit violations before generate warning/action */
	int             curOK;            /* Current count of repeated OK's (paramater in range) */
	SSL_TRG_STATE   trgState;         /* transgression status */
}
