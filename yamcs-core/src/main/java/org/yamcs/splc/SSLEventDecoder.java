/*
 * Created on Sep 12, 2006
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.yamcs.splc;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.Vector;

import org.yamcs.usoctools.PayloadModel;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;

/**
 * Decodes SSL events. It's mainly a translation from C found in pacts.
 * @author nm
 *
 */
public class SSLEventDecoder {
	static private Map<Integer,String> eventText=new HashMap<Integer,String>();
	PayloadModel payloadModel;
	static private Map<Integer, EventSeverity> eventLevel=new HashMap<Integer,EventSeverity>();
	static private Vector<String> allKnownEventNames = new Vector<String>();
	
	
	private class memErrMsg_t {
		memErrMsg_t(ByteBuffer data) {
			address=data.getInt();
			value=data.getInt();
			counter=data.getInt();
			spare=data.getInt();
		}
		@Override
        public String toString() {
			return String.format("addr %#010x value %#x counter %#x spare %#x",
				address, value, counter, spare);
		}

		int address; /* of memory failure */
		int value;
		int counter;
		int spare;
	};


	public SSLEventDecoder (PayloadModel payloadModel) {
		this.payloadModel=payloadModel;
	}

	/**
	 * 
	 * @param data
	 * @param skip the number of bytes to skip after the sslEventId (for EuTEF is 2,  for EDR is 0)
	 * @return
	 */
	public Event.Builder decode(ByteBuffer data, int skip) {
		// here we store the attached data of the log message
		String message="";
		Event.Builder eventb=Event.newBuilder();
		eventb.setType("SSL");
		int sslEventId=0xFFFF & data.getShort();
		while((skip--)>0) data.get();
		short sslEventYear=data.getShort();
		short sslEventMsec=data.getShort();
		int sslEventSecOfYear=data.getInt();
		int sslEventLength=data.getInt();
		long d = TimeEncoding.fromGpsYearSecMillis(sslEventYear,sslEventSecOfYear, sslEventMsec);
		
/*		System.err.println("SSL event:"
			+ " id = 0x"+ Integer.toHexString(sslEventId)
			+ " sslEventYear="+sslEventYear
			+ " sslEventSecOfYear="+sslEventSecOfYear
			+ " Time = "+d
			+ " dataBytes = "+ sslEventLength
		);*/
		eventb.setGenerationTime(d);
		// switch on the event id. Group according to the layout of the attached
		// data.
		switch(sslEventId) {
		// events with no additional data
		case SSL_EVENT_USL_CMD_PKT_DISCARDED: 
		case SSL_EVENT_USL_CMD_PKT_LEN_TOO_SHORT:
		case SSL_EVENT_USL_FT_UPLD_COMPLETED:
		case SSL_EVENT_USL_FT_DNLD_COMPLETED:
			// following 3 added for Columbus
		case SSL_EVENT_COL_PKT_CHECKSUM_ERR:
		case SSL_EVENT_COL_INIT_FAIL_LAN:
		case SSL_EVENT_COL_INIT_FAIL_MILBUS:	
			message = "";
			break;	

		// events containing private header only
		// these are normally processed by a different application
		case SSL_EVENT_CMD_UNKNOWN:
		case SSL_EVENT_CMD_NO_CMD_PKT:
		case SSL_EVENT_CMD_SRC_INVALID:
		case SSL_EVENT_CMD_DEST_INVALID:
		case SSL_EVENT_CMD_PKT_SSL_DISCARDED:
		case SSL_EVENT_CMD_PKT_ASW_DISCARDED:
		case SSL_EVENT_CMDACK_RECEIVED:
		case SSL_EVENT_CMDACK_EXECUTED_OK:
		case SSL_EVENT_CMDACK_EXECUTED_FAIL: {
			message= " Private Header is 0x"+Integer.toHexString(data.getInt());
		}
		break;

		// event containing APID + sequence count
		case SSL_EVENT_USL_CMD_SEQ_CNT_INVALID: {
			short apid=data.getShort();
			short seqCount=data.getShort();
			message="apid="+apid+" seqCount="+seqCount;
		}
		break;

		// events containing parameter id and attributes
		case SSL_EVENT_MON_WARN_WarnLimit_EXCEED:
		case SSL_EVENT_MON_WARN_ActionLimit_EXCEED: {
			int parId=data.getInt();
			SslMonAttrib attrib=new SslMonAttrib(data,payloadModel);
			message=attrib.getOverLimitsMessage(sslEventId);
		}
		break;
		// events containing memory error data
		case SSL_EVENT_EDAC_MEMORY_FAILURE: {
			memErrMsg_t memErr=new memErrMsg_t(data);
			message=memErr.toString();
		}
		break;

		// events containing a 16 bit error code
		case SSL_EVENT_USL_FT_UPLD_ABORTED:
		case SSL_EVENT_USL_FT_DNLD_ABORTED: {
			short errorCode=data.getShort();
			message="0x"+Integer.toHexString(errorCode);
		}
		break;
		// events containing private header + variable data
		case SSL_EVENT_CMDACK_DATA: {
			PrivateHeader privHdr=new PrivateHeader(data);

			switch (privHdr.type) {
			case ssl_cmd_t.SSL_CMD_MON_getAttribs:   // commands returning ssl_MonAttrib_t
				SslMonAttrib attrib=new SslMonAttrib(data, payloadModel);
				message="monAttribs:"+attrib;			
				break;	
			case ssl_cmd_t.SSL_CMD_TASK_statusGet:  // commands returning TASK_DESC + c-string
				TASK_DESC taskDesc=new TASK_DESC(data);
				byte[] name=new byte[sslEventLength-5-taskDesc.size()];
				data.get(name);
				message="TaskStatus: name: "+new String(name);
				message+="\n\ttid: "+taskDesc.td_id+", prio:"+taskDesc.td_priority+", status: "+taskDesc.td_status+", options: "+taskDesc.td_options+" entry: 0x"+Long.toHexString(taskDesc.td_entry_ptr);
				message+="\n\tsp: 0x"+Long.toHexString(taskDesc.td_sp_ptr)+", base: 0x"+Long.toHexString(taskDesc.td_pStackBase_ptr)+", lim: 0x"+Long.toHexString(taskDesc.td_pStackLimit_ptr)+", end: 0x"+Long.toHexString(taskDesc.td_pStackEnd_ptr);
				message+="\n\tsize:"+taskDesc.td_stackSize+", current:"+taskDesc.td_stackCurrent+", high:"+taskDesc.td_stackHigh+", margin: "+taskDesc.td_stackMargin;
				message+="\n\terrror:"+taskDesc.td_errorStatus+", delay:"+taskDesc.td_delay;
				break;
		
			case ssl_cmd_t.SSL_CMD_TASK_listGet:// commands returning c-string
				byte[] str=new byte[sslEventLength-5];
				data.get(str);
				if (str.length > 0)
					message="\tTaskList: "+new String(str);
				else
					message="End of TaskList";
				break;

			case ssl_cmd_t.SSL_CMD_FILE_pwd:
				str=new byte[sslEventLength-5];
				data.get(str);
				String s="";
				try {
					s = new String(str,"ISO-8859-1");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				message="FilePwd: "+s;
				break;
			case ssl_cmd_t.SSL_CMD_FILE_ls:
				str=new byte[sslEventLength-5];
				data.get(str);
				s="";
				try {
					s = new String(str,"ISO-8859-1");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				if (str.length > 0)
					message="FileList: "+s;
				else
					message="End of FileList";		
				break;
			}
		}
		break;

		// following cases added for Columbus
		case SSL_EVENT_COL_PKT_APID_UNKNOWN: {
			short apid=data.getShort();	
			message="primary header word #1 = 0x"+ Integer.toHexString(apid);
		}
		break;

		case SSL_EVENT_COL_PKT_SID_UNKNOWN: {
			int sid=data.getInt();
			short apid=data.getShort();		
			message="SID: "+sid+" primary header word #1 = "+apid;
		}
		break;
		case SSL_EVENT_COL_CMD_SEQ_CNT_INVALID: {
			int word1and2=data.getInt();
			message="primary header word #1+2 = 0x"+ Integer.toHexString(word1and2);
		}
		break;
		case SSL_EVENT_COL_IRC_LAN_RATE_SET: {
			int lanRate=data.getInt();
			message="LAN rate = 0x" + Integer.toHexString(lanRate);
		}
		break;

		case SSL_EVENT_COL_IRC_UPLD_COMPLETED:
		case SSL_EVENT_COL_IRC_DNLD_COMPLETED: {
			byte[] fileNameB=new byte[sslEventLength];
			data.get(fileNameB);
			String fileName="";
			try {
				fileName = new String(fileNameB,"ISO-8859-1");
				int index = fileName.indexOf(0);
				if (index != -1) fileName = fileName.substring(0, index);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			message="filename = "+ fileName;
		}
		break;
		case SSL_EVENT_COL_PKT_PATHID_UNKNOWN: {
			short pathId=data.getShort();
			message="\tPathID = %d"+pathId;
		}
		break;

		case SSL_EVENT_COL_ADS_UNSUBSCRIBED_SET_RCVD: {
			int set=data.getInt();
			message="set no. 0x"+Integer.toHexString(set);
		}
		break;

		case SSL_EVENT_COL_APID_CFG_VERSION_ID:
		case SSL_EVENT_COL_SID_CFG_VERSION_ID: {
			short pathId=data.getShort();
			message="Version = "+pathId;
		}
		break;

		case SSL_EVENT_EXEPTION_OCCURED: {
			int excCause = data.getInt();
			int excTime = data.getInt();
			int taskId = data.getInt();
			int failAdrs = data.getInt();
			int progCount = data.getInt();
			int edacRelated = data.getInt();
			data.position(data.position() + 24); // spare
			byte[] eventDataB = new byte[sslEventLength - 48];
			data.get(eventDataB);
			String eventData = "";
			try {
				eventData = new String(eventDataB,"ISO-8859-1");
				int index = eventData.indexOf(0);
				if (index != -1) eventData = eventData.substring(0, index);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			message = String.format("cause %d, taskid %#010x, failaddr %#010x, PC %#010x, message:\n%s",
				excCause, taskId, failAdrs, progCount, eventData);
		}
		break;

		default:
			message="";
		break;
		}
		if(message.length()==0){
		    String msg=eventText.get(sslEventId);
		    if(msg==null) msg="Unknown sslEventId "+sslEventId;
			eventb.setMessage(msg);
		} else {
			eventb.setMessage(eventText.get((int)sslEventId)+": "+message);
		}
		if(eventLevel.containsKey(sslEventId))eventb.setSeverity(eventLevel.get(sslEventId));
		return eventb;
	}

	public static String[] getAllKnownEventNames() {
	  String[] names  = new String[allKnownEventNames.size()];
	  allKnownEventNames.toArray(names);
	  return names;
	}
	
	public static void main(String[] args) {
	}

	public final static int SSL_EVENT_MASK 								=0xFF00;
	/* purpose of SSL_EVENT_MASK : if ((event.id BIT_AND SSL_EVENT_MASK) EQ SSL_EVENT_xxx_BASE) ... */

	/* ************ Unspecified event (for testing only) ******************** */
	public final static int SSL_EVENT_UNSPECIFIED                             =0x0001; /* variable */

	/* ************ events, indicating an error during commanding *********** */

	public final static int SSL_EVENT_CMD_BASE                                =0x0010;

	public final static int SSL_EVENT_CMD_UNKNOWN                             =0x0010; /* priv.Hdr */
	public final static int SSL_EVENT_CMD_NO_CMD_PKT                          =0x0011; /* priv.Hdr */
	public final static int SSL_EVENT_CMD_SRC_INVALID                         =0x0012; /* priv.Hdr */
	public final static int SSL_EVENT_CMD_DEST_INVALID                        =0x0013; /* priv.Hdr */
	public final static int SSL_EVENT_CMD_PKT_SSL_DISCARDED                   =0x0014; /* priv.Hdr */
	public final static int SSL_EVENT_CMD_PKT_ASW_DISCARDED                   =0x0015; /* priv.Hdr */
	public final static int SSL_EVENT_USL_CMD_SEQ_CNT_INVALID                 =0x0016; /* (UINT16)pktSeqCnt */
	public final static int SSL_EVENT_USL_CMD_PKT_LEN_TOO_SHORT               =0x0017; /* packet () */


	/* ****************    events caused by remote commands   *************** */

	public final static int SSL_EVENT_CMDACK_BASE                             =0x0020;

	public final static int SSL_EVENT_CMDACK_RECEIVED                         =0x0020;
	public final static int SSL_EVENT_CMDACK_EXECUTED_OK                      =0x0021;
	public final static int SSL_EVENT_CMDACK_EXECUTED_FAIL                    =0x0022;
	public final static int SSL_EVENT_CMDACK_DATA                             =0x0023;


	/* ************ events caused by limit violations during monitoring *********** */

	public final static int SSL_EVENT_MON_WARN_BASE                           =0x0030;

	public final static int SSL_EVENT_MON_WARN_WarnLimit_EXCEED               =0x0030; /* (int)parId, (sslMonAttrib_t)attributes */
	public final static int SSL_EVENT_MON_WARN_ActionLimit_EXCEED             =0x0031; /* (int)parId, (sslMonAttrib_t)attributes */


	/* ************************* events caused by SPT Driver ********************** */

	public final static int SSL_EVENT_SPT_BASE                                =0x0040;

	public final static int SSL_EVENT_USL_CMD_PKT_DISCARDED                   =0x0040; /* - */
	public final static int SSL_EVENT_USL_FT_UPLD_COMPLETED                   =0x0041; /* - */
	public final static int SSL_EVENT_USL_FT_UPLD_ABORTED                     =0x0042; /* (short)sptRetCode */
	public final static int SSL_EVENT_USL_FT_DNLD_COMPLETED                   =0x0043; /* (short)sptRetCode */
	public final static int SSL_EVENT_USL_FT_DNLD_ABORTED                     =0x0044; /* - */

	public final static int SSL_EVENT_USL_HS_NOT_PROVIDED_IN_TIME             =0x004F;

	/* *********************** events caused by EDAC/MemScrub ********************* */

	public final static int SSL_EVENT_EDAC_BASE                               =0x0050;

	public final static int SSL_EVENT_EDAC_MEMORY_FAILURE                     =0x0050;

	/* ******** events caused by any BSP Exception (generated from sslMemScrub) ****
	 ******* */
	public final static int SSL_EVENT_EXEPTION_BASE                           =0x0058;
	public final static int SSL_EVENT_EXEPTION_OCCURED                        =0x0058;


	/* ********************** events caused by Columbus Driver ******************* */
	public final static int SSL_EVENT_COL_BASE                                =0x0060;
	public final static int SSL_EVENT_COL_PKT_CHECKSUM_ERR                    =0x0060;  /* - */
	public final static int SSL_EVENT_COL_PKT_APID_UNKNOWN                    =0x0061;  /* CCSDS Hdr.Word 1 (2 bytes)         */
	public final static int SSL_EVENT_COL_PKT_SID_UNKNOWN                     =0x0062;  /* Sid, CCSDSHdr. Word 1 (6 bytes)    */
	public final static int SSL_EVENT_COL_CMD_SEQ_CNT_INVALID                 =0x0063;  /* CCSDS Hdr.Word 1 and 2 (4 bytes)   */
	public final static int SSL_EVENT_COL_IRC_LAN_RATE_SET                    =0x0064;  /* LanRate (4bytes)                   */
	public final static int SSL_EVENT_COL_IRC_UPLD_COMPLETED                  =0x0065;  /* Filename (max. 41 bytes)            */
	public final static int SSL_EVENT_COL_IRC_DNLD_COMPLETED                  =0x0066;  /* Filename (max. 41 bytes)            */
	public final static int SSL_EVENT_COL_PKT_PATHID_UNKNOWN                  =0x0067;  /* PathId (2 bytes)                    */
	public final static int SSL_EVENT_COL_ADS_UNSUBSCRIBED_SET_RCVD           =0x0068;  /* AdsSetId (4 bytes)                  */
	public final static int SSL_EVENT_COL_APID_CFG_VERSION_ID                 =0x0069;  /* ApidFile VerId (2 bytes)            */
	public final static int SSL_EVENT_COL_SID_CFG_VERSION_ID                  =0x006A;  /* SidFile VerId (2 bytes)             */
	/* Generated by SSL in case of Columbus
 Lan or Milbus init failure */
	public final static int SSL_EVENT_COL_INIT_FAIL_LAN                       =0x006B;  /* - */
	public final static int SSL_EVENT_COL_INIT_FAIL_MILBUS                    =0x006C;  /* - */


	private class ssl_cmd_t {

		public final static int SSL_CMD_TASK_BASE   =0x20;
		public final static int SSL_CMD_FILE_BASE   =0x40;
		public final static int SSL_CMD_MON_BASE    =0x60;
		public final static int SSL_CMD_NEXT_BASE   =0x80;

		/*---------------------------------------------------
		  Task Commands (start at 0x21)
		  -----------------------------------------------------*/
		public final static int SSL_CMD_TASK_execFkt       = SSL_CMD_TASK_BASE;
		public final static int SSL_CMD_TASK_spawn         = SSL_CMD_TASK_BASE + 0x01;
		public final static int SSL_CMD_TASK_delete        = SSL_CMD_TASK_BASE + 0x02;
		public final static int SSL_CMD_TASK_suspend       = SSL_CMD_TASK_BASE + 0x03;
		public final static int SSL_CMD_TASK_resume        = SSL_CMD_TASK_BASE + 0x04;
		public final static int SSL_CMD_TASK_prioSet       = SSL_CMD_TASK_BASE + 0x05;
		public final static int SSL_CMD_TASK_statusGet     = SSL_CMD_TASK_BASE + 0x06;
		public final static int SSL_CMD_TASK_reboot        = SSL_CMD_TASK_BASE + 0x07;
		public final static int SSL_CMD_TASK_listGet       = SSL_CMD_TASK_BASE + 0x08;

		public final static int SSL_CMD_TASK_MAX           = SSL_CMD_TASK_BASE + 0x09;

		/*----------------------------------------------------
		  File Commands (start at 0x40)
		  -----------------------------------------------------*/
		public final static int SSL_CMD_FILE_mkdir                = SSL_CMD_FILE_BASE;
		public final static int SSL_CMD_FILE_rmdir                = SSL_CMD_FILE_BASE + 0x01;
		public final static int SSL_CMD_FILE_copy                 = SSL_CMD_FILE_BASE + 0x02;
		public final static int SSL_CMD_FILE_load                 = SSL_CMD_FILE_BASE + 0x03;
		public final static int SSL_CMD_FILE_unload               = SSL_CMD_FILE_BASE + 0x04;
		public final static int SSL_CMD_FILE_cd                   = SSL_CMD_FILE_BASE + 0x05;
		public final static int SSL_CMD_FILE_rm                   = SSL_CMD_FILE_BASE + 0x06;
		public final static int SSL_CMD_FILE_move                 = SSL_CMD_FILE_BASE + 0x07;
		public final static int SSL_CMD_FILE_pwd                  = SSL_CMD_FILE_BASE + 0x08;
		public final static int SSL_CMD_FILE_ls                   = SSL_CMD_FILE_BASE + 0x09;
		public final static int SSL_CMD_FILE_USL_FT_uploadStart   = SSL_CMD_FILE_BASE + 0x0A;
		public final static int SSL_CMD_FILE_USL_FT_downloadStart = SSL_CMD_FILE_BASE + 0x0B;

		public final static int SSL_CMD_FILE_MAX                  = SSL_CMD_FILE_BASE + 0x0C;


		/*----------------------------------------------------
		  Monitor Commands (start at 0x60)
		  -----------------------------------------------------*/
		public final static int SSL_CMD_MON_setBOOL          = SSL_CMD_MON_BASE + 0x00;
		public final static int SSL_CMD_MON_setUINT          = SSL_CMD_MON_BASE + 0x01;
		public final static int SSL_CMD_MON_setINT           = SSL_CMD_MON_BASE + 0x02;
		public final static int SSL_CMD_MON_setFLOAT         = SSL_CMD_MON_BASE + 0x03;
		public final static int SSL_CMD_MON_setLimitsBOOL    = SSL_CMD_MON_BASE + 0x04;
		public final static int SSL_CMD_MON_setLimitsUINT    = SSL_CMD_MON_BASE + 0x05;
		public final static int SSL_CMD_MON_setLimitsINT     = SSL_CMD_MON_BASE + 0x06;
		public final static int SSL_CMD_MON_setLimitsFLOAT   = SSL_CMD_MON_BASE + 0x07;
		public final static int SSL_CMD_MON_getAttribs       = SSL_CMD_MON_BASE + 0x08;
		public final static int SSL_CMD_MON_enablePar        = SSL_CMD_MON_BASE + 0x09;
		public final static int SSL_CMD_MON_setScanPer       = SSL_CMD_MON_BASE + 0x0A;
		public final static int SSL_CMD_MON_setViolations    = SSL_CMD_MON_BASE + 0x0B;
		public final static int SSL_CMD_MON_setCheckInterval = SSL_CMD_MON_BASE + 0x0C;

		public final static int SSL_CMD_MON_MAX            = SSL_CMD_MON_BASE + 0x0D;

		/*----------------------------------------------------
		  Next (start at 0x80)
		  -----------------------------------------------------*/
		public final static int SSL_CMD_NEXT_xxx0        = SSL_CMD_NEXT_BASE;

		public final static int SSL_CMD_MAX             = 0xff;
	}
	{
		eventLevel.put(SSL_EVENT_UNSPECIFIED,  EventSeverity.INFO);
		eventText.put(SSL_EVENT_UNSPECIFIED,  "Unspecified event type received");
		allKnownEventNames.add("SSL_EVENT_UNSPECIFIED");

		eventLevel.put(SSL_EVENT_CMD_UNKNOWN,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_CMD_UNKNOWN,  "Cmd unknown");
		allKnownEventNames.add("SSL_EVENT_CMD_UNKNOWN");
		eventLevel.put(SSL_EVENT_CMD_NO_CMD_PKT, EventSeverity.ERROR);
		eventText.put(SSL_EVENT_CMD_NO_CMD_PKT, "Cmd not a packet");
		allKnownEventNames.add("SSL_EVENT_CMD_NO_CMD_PKT");
		eventLevel.put(SSL_EVENT_CMD_SRC_INVALID,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_CMD_SRC_INVALID,  "Cmd src invalid");
		allKnownEventNames.add("SSL_EVENT_CMD_SRC_INVALID");
		eventLevel.put(SSL_EVENT_CMD_DEST_INVALID,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_CMD_DEST_INVALID,  "Cmd dest invalid");
		allKnownEventNames.add("SSL_EVENT_CMD_DEST_INVALID");
		eventLevel.put(SSL_EVENT_CMD_PKT_SSL_DISCARDED,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_CMD_PKT_SSL_DISCARDED,  "Cmd SSL discarded");
		allKnownEventNames.add("SSL_EVENT_CMD_PKT_SSL_DISCARDED");
		eventLevel.put(SSL_EVENT_CMD_PKT_ASW_DISCARDED,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_CMD_PKT_ASW_DISCARDED,  "Cmd ASW discarded");
		allKnownEventNames.add("SSL_EVENT_CMD_PKT_ASW_DISCARDED");
		eventLevel.put(SSL_EVENT_USL_CMD_SEQ_CNT_INVALID,  EventSeverity.WARNING);
		eventText.put(SSL_EVENT_USL_CMD_SEQ_CNT_INVALID,  "Cmd invalid seq cnt");
		allKnownEventNames.add("SSL_EVENT_USL_CMD_SEQ_CNT_INVALID");
		eventLevel.put(SSL_EVENT_USL_CMD_PKT_LEN_TOO_SHORT ,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_USL_CMD_PKT_LEN_TOO_SHORT ,  "Cmd pkt too short");
		allKnownEventNames.add("SSL_EVENT_USL_CMD_PKT_LEN_TOO_SHORT");
		
		eventLevel.put(SSL_EVENT_CMDACK_RECEIVED,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_CMDACK_RECEIVED,  "Cmd received OK");
		allKnownEventNames.add("SSL_EVENT_CMDACK_RECEIVED");
		eventLevel.put(SSL_EVENT_CMDACK_EXECUTED_OK,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_CMDACK_EXECUTED_OK,  "Cmd executed OK");
		allKnownEventNames.add("SSL_EVENT_CMDACK_EXECUTED_OK");
		eventLevel.put(SSL_EVENT_CMDACK_EXECUTED_FAIL,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_CMDACK_EXECUTED_FAIL,  "Cmd executed not OK");
		allKnownEventNames.add("SSL_EVENT_CMDACK_EXECUTED_FAIL");
		eventLevel.put(SSL_EVENT_CMDACK_DATA,  EventSeverity.INFO);
		eventText.put(SSL_EVENT_CMDACK_DATA,  "Cmd data");
		allKnownEventNames.add("SSL_EVENT_CMDACK_DATA");

		eventLevel.put(SSL_EVENT_MON_WARN_WarnLimit_EXCEED,  EventSeverity.WARNING);
		eventText.put(SSL_EVENT_MON_WARN_WarnLimit_EXCEED,  "Warning limit exceed");
		allKnownEventNames.add("SSL_EVENT_MON_WARN_WarnLimit_EXCEED");
		eventLevel.put(SSL_EVENT_MON_WARN_ActionLimit_EXCEED,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_MON_WARN_ActionLimit_EXCEED,  "Action limit exceed");
		allKnownEventNames.add("SSL_EVENT_MON_WARN_ActionLimit_EXCEED");

		eventLevel.put(SSL_EVENT_USL_CMD_PKT_DISCARDED,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_USL_CMD_PKT_DISCARDED,  "Cmd invalid checksum");
		allKnownEventNames.add("SSL_EVENT_USL_CMD_PKT_DISCARDED");
		eventLevel.put(SSL_EVENT_USL_FT_UPLD_COMPLETED,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_USL_FT_UPLD_COMPLETED,  "File upload completed");
		allKnownEventNames.add("SSL_EVENT_USL_FT_UPLD_COMPLETED");
		eventLevel.put(SSL_EVENT_USL_FT_UPLD_ABORTED,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_USL_FT_UPLD_ABORTED,  "File upload aborted");
		allKnownEventNames.add("SSL_EVENT_USL_FT_UPLD_ABORTED");
		eventLevel.put(SSL_EVENT_USL_FT_DNLD_COMPLETED,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_USL_FT_DNLD_COMPLETED,  "File download completed");
		allKnownEventNames.add("SSL_EVENT_USL_FT_DNLD_COMPLETED");
		eventLevel.put(SSL_EVENT_USL_FT_DNLD_ABORTED,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_USL_FT_DNLD_ABORTED,  "File download aborted");
		allKnownEventNames.add("SSL_EVENT_USL_FT_DNLD_ABORTED");

		eventLevel.put(SSL_EVENT_USL_HS_NOT_PROVIDED_IN_TIME,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_USL_HS_NOT_PROVIDED_IN_TIME,  "H&S not provided");
		allKnownEventNames.add("SSL_EVENT_USL_HS_NOT_PROVIDED_IN_TIME");
		eventLevel.put(SSL_EVENT_EDAC_MEMORY_FAILURE,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_EDAC_MEMORY_FAILURE,  "EDAC memory error");
		allKnownEventNames.add("SSL_EVENT_EDAC_MEMORY_FAILURE");

		eventLevel.put(SSL_EVENT_EXEPTION_OCCURED,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_EXEPTION_OCCURED,  "Exception occurred");
		allKnownEventNames.add("SSL_EVENT_EXEPTION_OCCURED");

		eventLevel.put(SSL_EVENT_COL_PKT_CHECKSUM_ERR,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_COL_PKT_CHECKSUM_ERR,  "Cmd checksum error");
		allKnownEventNames.add("SSL_EVENT_COL_PKT_CHECKSUM_ERR");
		eventLevel.put(SSL_EVENT_COL_PKT_APID_UNKNOWN,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_COL_PKT_APID_UNKNOWN,  "Cmd invalid APID");
		allKnownEventNames.add("SSL_EVENT_COL_PKT_APID_UNKNOWN");
		eventLevel.put(SSL_EVENT_COL_PKT_SID_UNKNOWN,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_COL_PKT_SID_UNKNOWN,  "Cmd invalid SID");
		allKnownEventNames.add("SSL_EVENT_COL_PKT_SID_UNKNOWN");
		eventLevel.put(SSL_EVENT_COL_CMD_SEQ_CNT_INVALID,  EventSeverity.WARNING);
		eventText.put(SSL_EVENT_COL_CMD_SEQ_CNT_INVALID,  "Cmd invalid seq cnt");
		allKnownEventNames.add("SSL_EVENT_COL_CMD_SEQ_CNT_INVALID");
		eventLevel.put(SSL_EVENT_COL_IRC_LAN_RATE_SET,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_COL_IRC_LAN_RATE_SET,  "LAN rate set command received");
		allKnownEventNames.add("SSL_EVENT_COL_IRC_LAN_RATE_SET");
		eventLevel.put(SSL_EVENT_COL_IRC_UPLD_COMPLETED,  EventSeverity.INFO);
		eventText.put(SSL_EVENT_COL_IRC_UPLD_COMPLETED,  "File upload completed");
		allKnownEventNames.add("SSL_EVENT_COL_IRC_UPLD_COMPLETED");
		eventLevel.put(SSL_EVENT_COL_IRC_DNLD_COMPLETED,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_COL_IRC_DNLD_COMPLETED,  "File download completed");
		allKnownEventNames.add("SSL_EVENT_COL_IRC_DNLD_COMPLETED");
		eventLevel.put(SSL_EVENT_COL_PKT_PATHID_UNKNOWN,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_COL_PKT_PATHID_UNKNOWN,  "Cmd invalid path ID");
		allKnownEventNames.add("SSL_EVENT_COL_PKT_PATHID_UNKNOWN");
		eventLevel.put(SSL_EVENT_COL_ADS_UNSUBSCRIBED_SET_RCVD,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_COL_ADS_UNSUBSCRIBED_SET_RCVD,  "Unsubscribed ADS set received");
		allKnownEventNames.add("SSL_EVENT_COL_ADS_UNSUBSCRIBED_SET_RCVD");
		eventLevel.put(SSL_EVENT_COL_APID_CFG_VERSION_ID ,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_COL_APID_CFG_VERSION_ID ,  "APID configuration file version");
		allKnownEventNames.add("SSL_EVENT_COL_APID_CFG_VERSION_ID");
		eventLevel.put(SSL_EVENT_COL_SID_CFG_VERSION_ID,   EventSeverity.INFO);
		eventText.put(SSL_EVENT_COL_SID_CFG_VERSION_ID,  "SID configuration file version");
		allKnownEventNames.add("SSL_EVENT_COL_SID_CFG_VERSION_ID");
		eventLevel.put(SSL_EVENT_COL_INIT_FAIL_LAN,  EventSeverity.ERROR);
		eventText.put(SSL_EVENT_COL_INIT_FAIL_LAN,  "LAN initialisation failed");
		allKnownEventNames.add("SSL_EVENT_COL_INIT_FAIL_LAN");
		eventLevel.put(SSL_EVENT_COL_INIT_FAIL_MILBUS,   EventSeverity.ERROR);
		eventText.put(SSL_EVENT_COL_INIT_FAIL_MILBUS,   "Milbus initialisation failed");
		allKnownEventNames.add("SSL_EVENT_COL_INIT_FAIL_MILBUS");

	}
	
	private class TASK_DESC {                  /* TASK_DESC - information structure */
		  int     td_id;          /* task id */
		  int     td_name_ptr;        /* name of task */
		  int     td_priority;    /* task priority */
		  int     td_status;      /* task status */
		  int     td_options;     /* task option bits (see below) */
		  int     td_entry_ptr;       /* original entry point of task */
		  int     td_sp_ptr;          /* saved stack pointer */
		  int     td_pStackBase_ptr;  /* the bottom of the stack */
		  int     td_pStackLimit_ptr; /* the effective end of the stack */
		  int     td_pStackEnd_ptr;   /* the actual end of the stack */
		  int     td_stackSize;   /* size of stack in bytes */
		  int     td_stackCurrent;/* current stack usage in bytes */
		  int     td_stackHigh;   /* maximum stack usage in bytes */
		  int     td_stackMargin; /* current stack margin in bytes */
		  int     td_errorStatus; /* most recent task error status */
		  int     td_delay;       /* delay/timeout ticks */
	
		  TASK_DESC(ByteBuffer bb) {
			  td_id=bb.getInt();
			  td_name_ptr=bb.getInt();
			  td_priority=bb.getInt();
			  td_status=bb.getInt();
			  td_options=bb.getInt();
			  td_entry_ptr=bb.getInt();
			  td_sp_ptr=bb.getInt();
			  td_pStackBase_ptr=bb.getInt();
			  td_pStackLimit_ptr=bb.getInt();
			  td_pStackEnd_ptr=bb.getInt();
			  td_stackSize=bb.getInt();
			  td_stackCurrent=bb.getInt();
			  td_stackHigh=bb.getInt();
			  td_stackMargin=bb.getInt();
			  td_errorStatus=bb.getInt();
			  td_delay=bb.getInt();
		  }

		public int size() {
			return 64;
		}
	} ;

	
}
