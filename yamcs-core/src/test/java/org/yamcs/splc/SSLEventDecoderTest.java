package org.yamcs.splc;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.splc.SSLEventDecoder;
import org.yamcs.usoctools.PayloadModel;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.protobuf.Yamcs.Event;




public class SSLEventDecoderTest {
    
    @BeforeClass
    static public void setUpBeforeClass() throws ConfigurationException {
    	YConfiguration.setup();
    }
    
    @Test
	public void testDecode() {
		SSLEventDecoder ed=new SSLEventDecoder(new PayloadModelMockup());
		ByteBuffer data=ByteBuffer.allocate(1256);
		int b[]={0x003007D6, 0x00F10021, 0x6F480000, 0x003C0000,
				 0x004C0000, 0x004C02EE, 0xEEEE0000, 0x0FA10000, 
				 0x0E460000, 0x0F810000, 0x0D890000, 0x0FFF0000, 
				 0x00010000, 0x00010000, 0x02C40000, 0x00000000, 
				 0x00010000, 0x00000000, 0x00020000};
		for (int i=0;i<b.length;i++) {
			data.putInt(b[i]);
		}
		data.rewind();
		Event.Builder e=ed.decode(data,0);
		assertEquals("2006-01-26T08:39:22.241", TimeEncoding.toString(e.getGenerationTime()));
		assertEquals("Warning limit exceed: parameter 76(mock_76) currentValue 4001>3969",e.getMessage());
		
		int b1[] = {0xff2207d6, 0x0029001f, 0x12f30000 ,0x00280004, 
				    0x50434446, 0x69766d75, 0x2e667370, 0x00000000, 
				    0x00000000, 0x00000000, 0x00000000, 0x00000000, 
				    0x00000000, 0x00000a00};
		data.rewind();
		for (int i=0;i<b1.length;i++) {
			data.putInt(b1[i]);
		}
		data.rewind();
		Event.Builder e1=ed.decode(data,0);
		System.out.println("generationTime="+e1.getGenerationTime());
		System.out.println("message="+e1.getMessage());
	}

}


class PayloadModelMockup implements PayloadModel {

    @Override
    public Collection<Event> decode(long genTime, byte[] ccsds) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String[] getEventPacketsOpsnames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getMonParameterName(int parId) {
        return "mock_"+parId;
    }

	@Override
	public String getPayloadName() {
		return "PayloadModelMockup";
	}
    
}