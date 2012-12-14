package org.yamcs;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.Channel;
import org.yamcs.ChannelException;
import org.yamcs.ChannelFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.ParameterConsumer;
import org.yamcs.ParameterRequestManager;
import org.yamcs.ParameterValue;
import org.yamcs.ParameterValueWithId;
import org.yamcs.tctm.FileTmPacketProvider;
import org.yamcs.tctm.NoPacketSelectedException;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.Service.State;
import org.yamcs.protobuf.Yamcs.NamedObjectId;

import static org.junit.Assert.*;

public class ParameterRequestManagerTest {
	
    @BeforeClass
    public static void setUpBeforeClass() throws ConfigurationException {
        YConfiguration.setup();
	}
    
   
	static int edrhkCounter=0;
	static int sysVarCounter=0;
	static int counter=0;
	class SimpleConsummer implements ParameterConsumer {
		BufferedReader reader=null;
		public SimpleConsummer(String filename) throws FileNotFoundException {
			reader=new BufferedReader(new FileReader(filename));
		}
		
		@Override
        public void updateItems(int subscriptionID, ArrayList<ParameterValueWithId> itemValueList) {
			counter++;
		//System.out.println("delivery "+counter+" edrHkCounter=:"+edrhkCounter+" \n"+itemValueList);
			if(itemValueList.size()==7) {
				edrhkCounter++;
				ParameterValueWithId p_coarse_time=itemValueList.get(0);
				ParameterValueWithId p_vmutemp=itemValueList.get(2);
				ParameterValueWithId p_aaatemp=itemValueList.get(3);
				ParameterValueWithId p_esem3estemp=itemValueList.get(4);
				ParameterValueWithId p_scriptstat=itemValueList.get(1);
				ParameterValueWithId p_pwrconsumption=itemValueList.get(5);
				ParameterValueWithId p_ccsdstime=itemValueList.get(6);
				
				assertEquals("EDR_SH_Coarse_Time",p_coarse_time.getId().getName());
				assertEquals("EDR_PCCU_Thermistor21_VMU_Air_Outlet_Temp",p_vmutemp.getId().getName());
				assertEquals("EDR_PDU_ESEM3ES_Temp",p_esem3estemp.getId().getName());
				assertEquals("EDR_Script_Stat2",p_scriptstat.getId().getName());
				assertEquals("EDR_HK_CCSDS_TIME",p_ccsdstime.getId().getName());
				assertEquals("EDR_AAA_Electronic_Unit_Temp",p_aaatemp.getId().getName());
				assertEquals("EDR_PCCU_Pwr_Consumption",p_pwrconsumption.getId().getName());
				ParameterValue m_coarse_time=p_coarse_time.getParameterValue();
				ParameterValue m_vmutemp=p_vmutemp.getParameterValue();
				ParameterValue m_esem3estemp=p_esem3estemp.getParameterValue();
				ParameterValue m_ccsdstime=p_ccsdstime.getParameterValue();
				ParameterValue m_aaatemp=p_aaatemp.getParameterValue();
				
				String line=null;
				try {
					line = reader.readLine();
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
				if(line==null)return;
				//we check here that the order in which properties are inserted are the same like in CGS CIS
				String params[]=line.split("\t");
				assertEquals(3,params.length);
				assertEquals(Float.parseFloat(params[0]),m_vmutemp.getEngValue().getFloatValue(),0.00001);
				assertEquals(Float.parseFloat(params[1]),m_esem3estemp.getEngValue().getFloatValue(),0.00001);
				assertEquals(Float.parseFloat(params[2]),m_aaatemp.getEngValue().getFloatValue(),0.00001);
				
			} else if (itemValueList.size()==2) {
				//tmCounter++;
				ParameterValueWithId p1=itemValueList.get(0);
				ParameterValueWithId p2=itemValueList.get(1);
				
				assertEquals("EDR_SH_Coarse_Time",p1.getId().getName());
				assertEquals("EDR_HK_CCSDS_TIME",p2.getId().getName());
			} else if(itemValueList.size()==1) {
				sysVarCounter++;
				ParameterValueWithId p1=itemValueList.get(0);
				if(p1.getId().getName().equals("EDR_SH_Coarse_Time")) return;
				assertEquals("CDMCS_STATUS",p1.getId().getName());
				assertEquals("RUNNING",new String(p1.getParameterValue().getEngValue().getBinaryValue().toByteArray()));
			}  else{
				assertTrue(false);
			}
		}
	}
	
	@Ignore
	@Test
	public void testInvalidSubscriptions() throws ConfigurationException {
		ParameterRequestManager rm=new ParameterRequestManager("test", XtceDbFactory.getInstance("yamcs"));
		String[] opsnames={"EDR_PCDF_SC33_T13_PUE1_Front_Panel_Temp", "Invalid0", "EDR_PCCU_Dump_SD1700156","Invalid1"};
		try {
			rm.addRequest(MdbMappings.getParameterIdsForOpsnames(opsnames), new DummyConsummer());
			assertTrue(false);
		} catch (InvalidIdentification e) {
			List<NamedObjectId> invalid=e.invalidParameters;
			assertEquals(2,invalid.size());
			assertEquals("Invalid0",invalid.get(0).getName());
			assertEquals("Invalid1",invalid.get(1).getName());
		}
	}

	@Ignore
	@Test
	public void testSubscriptions() throws InvalidIdentification, InvalidRequestIdentification, ConfigurationException {
	
		ParameterRequestManager rm= new ParameterRequestManager("test", XtceDbFactory.getInstance("yamcs"));
		String[] on1={"EDR_PCDF_SC33_T13_PUE1_Front_Panel_Temp", "EDR_PCCU_Dump_SD1700156"};
		int id1=rm.addRequest(MdbMappings.getParameterIdsForOpsnames(on1), new DummyConsummer());
		String[] on2={"EUTEF_EUTEMP_Health_Stat","EDR_Parameter367_Exec"};
		rm.addItemsToRequest(id1,MdbMappings.getParameterIdsForOpsnames(on2));
		System.out.println(rm.toString());
		System.out.println("removed items: "+rm.removeRequest(id1));
		System.out.println(rm.toString());
		int id2=rm.addRequest(MdbMappings.getParameterIdsForOpsnames(on1), new DummyConsummer());
		String[] on3={"EDR_Parameter367_Exec","SOLAR_CPD_Encoder_Measure_X","EDR_PCDF_SC33_T13_PUE1_Front_Panel_Temp"};
		rm.addItemsToRequest(id2,MdbMappings.getParameterIdsForOpsnames(on3));
		
		String[] on4={"EDR_Parameter367_Exec","EDR_Parameter193_Exec"};
		int id3=rm.addRequest(MdbMappings.getParameterIdsForOpsnames(on4), new DummyConsummer());
		System.out.println(rm.toString());
	/* we have to make it differently because we have no control on how things are sorted in a hashmap	
		
		String expected1="Current Subscription list:\n"+
			"(apid=971, packetId=318809001, sid=311712357, opsName=EUTEF_DHPUHK, pathName=\\APM\\USOC\\ERASMUS\\EUTEF\\ACQ\\PKTS\\DHPUHK noparams=322) with parameters:\n"+
			"	Offset: 67 param(opsName=EUTEF_INSTRH_128_HEA_ST_HK, type=EGSE_BYTE_STREAM_MEASUREMENT, calibration=-1) in the requests: [ 0 ]\n"+
			"(apid=943, packetId=318804130, sid=313214652, opsName=EDR_Tlm_Pkt_PCDF_Science33, pathName=\\APM\\USOC\\ERASMUS\\EDR\\ACQ\\PKTS\\PCDF_SC_33 noparams=260) with parameters:\n"+
			"	Offset: 68 param(opsName=EDR_PCDF_SC33_T13_PUE1_Front_Panel_Temp, type=EGSE_FLOAT_MEASUREMENT, calibration=2) in the requests: [ 0 1 1 ]\n"+
			"(apid=991, packetId=318813001, sid=313198645, opsName=SOLAR_Tlm_Pkt_HK, pathName=\\APM\\USOC\\B_USOC\\SOLAR\\ACQ\\PKTS\\HK noparams=320) with parameters:\n"+
			"	Offset: 162 param(opsName=SOLAR_CPD_Encoder_Measure_X, type=EGSE_FLOAT_MEASUREMENT, calibration=2) in the requests: [ 1 ]\n"+
			"(apid=941, packetId=318804127, sid=313202190, opsName=EDR_Tlm_Pkt_ON_Event_Report, pathName=\\APM\\USOC\\ERASMUS\\EDR\\ACQ\\PKTS\\ON_EVENTREP noparams=407) with parameters:\n"+
			"	Offset: 344 param(opsName=EDR_PCCU_Dump_SD1700156, type=UNSIGNED_INTEGER_MEASUREMENT, calibration=-1) in the requests: [ 0 1 ]\n";
		String expected2="(apid=941, packetId=318804514, sid=313202225, opsName=EDR_Tlm_Pkt_Exec_TM_Rep, pathName=\\APM\\USOC\\ERASMUS\\EDR\\ACQ\\PKTS\\EXC_TM_REP noparams=407) with parameters:\n"+
			"	Offset: 418 param(opsName=EDR_Parameter193_Exec, type=UNSIGNED_INTEGER_MEASUREMENT, calibration=-1) in the requests: [ 2 ]\n"+
			"	Offset: 766 param(opsName=EDR_Parameter367_Exec, type=UNSIGNED_INTEGER_MEASUREMENT, calibration=-1) in the requests: [ 0 1 2 ]\n";
		assertEquals(expected1+expected2,rm.toString());
		String[] on5={"EDR_Parameter367_Exec","EDR_Parameter193_Exec"};
		rm.removeItemsFromRequest(id1,StringConvertors.getItemIdentificationForOpsnames(on5));
		rm.removeItemsFromRequest(id2,StringConvertors.getItemIdentificationForOpsnames(on5));
		rm.removeItemsFromRequest(id3,StringConvertors.getItemIdentificationForOpsnames(on5));
		assertEquals(expected1,rm.toString());		
		*/
	}	
	
	@Ignore
	@Test
	public void testPactsTmProcessing() throws InvalidIdentification, FileNotFoundException, InterruptedException, ChannelException, NoPacketSelectedException, ConfigurationException {	
		Channel ds=ChannelFactory.create("test", "test","Pacts File","QUIT 13","system");
		((FileTmPacketProvider)ds.getTmPacketProvider()).setDelayBetweenPackets(0);
		ParameterRequestManager rm=ds.getParameterRequestManager();
		String[] opsnames={"EDR_PCCU_Thermistor21_VMU_Air_Outlet_Temp", "EDR_PCCU_Pwr_Consumption","EDR_AAA_Electronic_Unit_Temp","EDR_PDU_ESEM3ES_Temp", "EDR_Script_Stat2","CDMCS_STATUS", "EDR_SH_Coarse_Time","EDR_HK_CCSDS_TIME"};
//		String[] opsnames={"EDR_PCCU_Thermistor21_VMU_Air_Outlet_Temp", "EDR_PDU_ESEM3ES_Temp", "CDMCS_STATUS"};

		rm.addRequest(MdbMappings.getParameterIdsForOpsnames(opsnames), new SimpleConsummer("/home/nm/USOC/trunk/tests/13.withcis"));
		System.out.println("subscription status: "+rm.toString());
		rm.start();
		while(rm.getTmProcessor().state()==State.RUNNING) {
			Thread.sleep(1000);
		}
		assertEquals(2016, edrhkCounter);
		if(sysVarCounter==0) assertTrue(false);
		System.out.println("In total "+edrhkCounter+" tm and "+sysVarCounter+" sysvar deliveries");
	}
	 
    class DummyConsummer implements ParameterConsumer {
        public void updateItems(int subscriptionId, ArrayList<ParameterValueWithId> itemValueList) {            
        }
    }
}
