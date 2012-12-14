package org.yamcs;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.TmProcessor;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.TmPacketProvider;

import com.google.common.util.concurrent.AbstractService;

import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.GpsCcsdsTime;
import org.yamcs.utils.TimeEncoding;

/**
 * Generates packets according to the refmdb database
 * @author nm
 *
 */
public class RefMdbPacketGenerator extends AbstractService implements TmPacketProvider {
    TmProcessor tmProcessor;
    final int headerLength=16;
    final int pkt1Length=3;
    final int pkt11Length=headerLength+pkt1Length+50;
    final int pkt12Length=headerLength+pkt1Length+7;
    final int pkt13Length=headerLength+pkt1Length+100;
    final int pkt14Length=headerLength+pkt1Length+100;
    final int pkt15Length=headerLength+pkt1Length+50;
    
    //raw values of parameters 
    public volatile short pIntegerPara1_1=5;
    
    
    public volatile byte pIntergerPara11_1=20;
    public volatile short pFloatPara11_2=1000;
    public volatile float pFloatPara11_3=2;
    public volatile byte pEnumerationPara11_4=0;
    public volatile String pStringPara11_5="cucu";
    public volatile int pIntegerPara11_6=99;
    public volatile byte pIntegerPara11_7=34;
    
    public volatile String pFixedStringPara13_1="Ab"; // 16 bits
    public volatile String pFixedStringPara13_2="A"; // 8 bits
    public volatile String pTerminatedStringPara13_3="Abcdef"; // Null terminated
    public volatile String pTerminatedStringPara13_4="Abcdef"; // Comma terminated
    public volatile String pPrependedSizeStringPara13_5="Abcdefghijklmnopqrstuvwxyz"; // First 16 bits (2 bytes) set size in bits of size tag
    public volatile String pPrependedSizeStringPara13_6="Abcdef"; // First 8 bits (1 byte) set size in bits of size tag
    public volatile String pFixedStringPara13_7="Abcdefghijklmnop"; // 128 bits
    
    // Get floats from strings
    public volatile String pStringFloatFSPara14_1="1.34"; // Fixed size 32 bit
    public volatile String pStringFloatTSCPara14_2="0.0000001"; // Comma terminated, leading zeros and calibrated
    public volatile String pStringFloatTSSCPara14_3="0.12"; // Semi-colon terminated, leading zero
    public volatile String pStringFloatFSBPara14_4="1.34567890123456"; // 128 bit string
    public volatile String pStringFloatPSPara14_5="1.345678"; // Prepended size string, first 8 bits (1 byte) set size in bits of size tag
    
    // Get integers from strings
    public volatile String pStringIntFixedPara15_1="120"; // Fixed size, 24 bits
    public volatile String pStringIntTermPara15_2="12"; // Comma terminated
    public volatile String pStringIntTermPara15_3="12045"; // Semi-colon terminated
    public volatile String pStringIntPrePara15_4="1204507"; // Prepended size (16 bits)
    public volatile String pStringIntStrPara15_5="123406789"; // string
    
    Map<Integer, AtomicInteger> seqCount=new HashMap<Integer, AtomicInteger>();
    
    @Override
    public void setTmProcessor(TmProcessor tmProcessor) {
        this.tmProcessor=tmProcessor;
    }


    public ByteBuffer generate_PKT11() {
        ByteBuffer bb=ByteBuffer.allocate(pkt11Length);
        fill_PKT11(bb);
        sendToTmProcessor(bb);
        return bb;
    }
    
    public ByteBuffer generate_PKT13() {
        ByteBuffer bb=ByteBuffer.allocate(pkt13Length);
        fill_PKT13(bb);
        sendToTmProcessor(bb);
        return bb;
    }
    
    public ByteBuffer generate_PKT14() {
        ByteBuffer bb=ByteBuffer.allocate(pkt14Length);
        fill_PKT14(bb);
        sendToTmProcessor(bb);
        return bb;
    }
    
    public ByteBuffer generate_PKT15() {
        ByteBuffer bb=ByteBuffer.allocate(pkt15Length);
        fill_PKT15(bb);
        sendToTmProcessor(bb);
        return bb;
    }

    private void fill_CcsdsHeader(ByteBuffer bb, int apid, int packetId) {
        short xs;
        //Primary header:
        // version(3bits) type(1bit) secondary header flag(1bit) apid(11 bits)
        xs=(short)((3<<11)|apid);
        bb.putShort(0, xs);
        
        AtomicInteger a=seqCount.get(apid);
        if(a==null) {
        	a=new AtomicInteger(0);
        	seqCount.put(apid,a);
        }
        //Seq Flags (2 bits) Seq Count(14 bits)
        xs=(short) ((3<<14)|a.getAndIncrement());
        
        bb.putShort(2, xs);
        //packet length (16 bits).
        bb.putShort(4,(short)(bb.capacity()-7));
        
        //Secondary header:
        //coarse time(32 bits)
        GpsCcsdsTime t=TimeEncoding.getCurrentGpsTime();
        bb.putInt(6, t.coarseTime);
        //fine time(8 bits) timeID(2bits) checkword(1 bit) spare(1 bit) pktType(4 bits)
       // xs=(short)((shTimeId<<6)|(shChecksumIndicator<<5)|shPacketType);

        bb.put(10, t.fineTime);
        //packetId(32 bits)
       bb.putInt(12, packetId);
    }
    
    private void fill_PKT1(ByteBuffer bb, int packetType) {
        fill_CcsdsHeader(bb, 995, 318813007);
        bb.put(headerLength, (byte)((pIntegerPara1_1<<4)+packetType));
    }

    private void fill_PKT11(ByteBuffer bb) {
        fill_PKT1(bb, 1);
        int offset=headerLength+pkt1Length;
        bb.position(offset);
        bb.put(pIntergerPara11_1);
        bb.putShort(pFloatPara11_2);
        bb.putFloat(pFloatPara11_3);
        bb.put(pEnumerationPara11_4);
        
        bb.put((byte)(pIntegerPara11_6>>16));
        bb.putShort((short)(pIntegerPara11_6&0xFFFF));
        bb.put(pIntegerPara11_7);
        
        byte[] b=new byte[10];
        System.arraycopy(pStringPara11_5.getBytes(), 0, b, 0, pStringPara11_5.getBytes().length);
        bb.put(b);
    }
    
    private void fill_PKT13(ByteBuffer bb) {
        fill_PKT1(bb, 3);
        int offset=headerLength+pkt1Length;
        bb.position(offset);
        
        putFixedStringParam(bb, pFixedStringPara13_1, 16);
        putFixedStringParam(bb, pFixedStringPara13_2, 8);
        
        putTerminatedStringParam(bb, pTerminatedStringPara13_3, (byte)0);
        putTerminatedStringParam(bb, pTerminatedStringPara13_4, (byte)',');
        
        putPrependedSizeStringParam(bb, pPrependedSizeStringPara13_5, 16);
        putPrependedSizeStringParam(bb, pPrependedSizeStringPara13_6, 8);
        
        putFixedStringParam(bb, pFixedStringPara13_7, 128);
    }
    
    private void fill_PKT14(ByteBuffer bb) {
        fill_PKT1(bb, 4);
        int offset=headerLength+pkt1Length;
        bb.position(offset);
        
        // Floats in strings
        putFixedStringParam(bb, pStringFloatFSPara14_1, 32);
        putTerminatedStringParam(bb, pStringFloatTSCPara14_2, (byte)',');
        putTerminatedStringParam(bb, pStringFloatTSSCPara14_3, (byte)';');
        putTerminatedStringParam(bb, pStringFloatTSSCPara14_3, (byte)';');
        putPrependedSizeStringParam(bb, pStringFloatPSPara14_5, 8);
        putFixedStringParam(bb, pStringFloatFSBPara14_4, 128);
    }
    
    private void fill_PKT15(ByteBuffer bb) {
        fill_PKT1(bb, 5);
        int offset=headerLength+pkt1Length;
        bb.position(offset);
        
        // Integers in strings
        putFixedStringParam(bb, pStringIntFixedPara15_1, 24);
        putTerminatedStringParam(bb, pStringIntTermPara15_2, (byte)',');
        putTerminatedStringParam(bb, pStringIntTermPara15_3, (byte)';');
        putPrependedSizeStringParam(bb, pStringIntPrePara15_4, 16);
        // Straight string is null terminated
        putTerminatedStringParam(bb, pStringIntStrPara15_5, (byte)0);
    }
    
    private void putFixedStringParam( ByteBuffer bb, String value, int bits ) {
    	int baSize = bits / 8;
    	if( bits == -1 ) {
    		baSize = value.getBytes().length;
    	}
    	byte[] ba=new byte[ baSize ];
        System.arraycopy(value.getBytes(), 0, ba, 0, value.getBytes().length);
        bb.put( ba );
        //System.out.println( String.format( "- put FixedString '%s' length %d bits in %d bits", value, value.getBytes().length*8, baSize*8 ) );
    }
    private void putTerminatedStringParam( ByteBuffer bb, String value, byte terminator ) {
    	byte[] ba=new byte[ value.getBytes().length+1];
        System.arraycopy(value.getBytes(), 0, ba, 0, value.getBytes().length);
        ba[ba.length-1] = terminator;
        bb.put( ba );
        /*
        if( terminator == 0 ) {
        	System.out.println( String.format( "- put TerminatedString '%s' length %d bits (%d bytes) with terminator null", value, value.getBytes().length*8, value.getBytes().length ) );
        } else {
        	System.out.println( String.format( "- put TerminatedString '%s' length %d bits (%d bytes) with terminator '%c'", value, value.getBytes().length*8, value.getBytes().length, terminator ) );
        }
        */
    }
    private void putPrependedSizeStringParam( ByteBuffer bb, String value, int tagSizeInBits ) {
    	if( tagSizeInBits <= 8 ) {
    		bb.put( ((byte)(value.getBytes().length)) );
    	} else {
    		bb.putShort( ((short)(value.getBytes().length)) );
    	}
    	byte[] ba=new byte[ value.getBytes().length ];
    	System.arraycopy(value.getBytes(), 0, ba, 0, value.getBytes().length);
        bb.put( ba );
    	//System.out.println( String.format("- put PrependedSizeString '%s' with leading %d bits filled with number %d to specify the number of bytes the string uses.",value,tagSizeInBits,value.getBytes().length) );
    }
    
    private void sendToTmProcessor(ByteBuffer bb) {
        if(tmProcessor!=null) tmProcessor.processPacket(new PacketWithTime(TimeEncoding.currentInstant(), TimeEncoding.currentInstant(), bb));
    }
    
    
    private void fill_PKT12(ByteBuffer bb) {
        fill_PKT1(bb, 2);
        //TODO
    }
    
    @Override
    public String getLinkStatus() {
        return null;
    }

    @Override
    public String getDetailedStatus() {
        return null;
    }

    @Override
    public void enable() {
    }

    @Override
    public void disable() {
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public long getDataCount() {
        return 0;
    }

    @Override
    public String getTmMode() {
        return null;
    }

    @Override
    public boolean isArchiveReplay() {
        return false;
    }
    
    
    public static void main(String[] args) throws FileNotFoundException, InterruptedException, ConfigurationException {
    	String f="/tmp/refmdb.pktfile";
    	RefMdbPacketGenerator mdbgen=new RefMdbPacketGenerator();
    	YConfiguration.setup();
    	final BufferedOutputStream os=new BufferedOutputStream(new FileOutputStream(f));
    	final Semaphore s=new Semaphore(0);
    	final AtomicInteger count=new AtomicInteger(0);
    	
    	mdbgen.setTmProcessor(new TmProcessor() {
			@Override
			public void processPacket(PacketWithTime pwrt) {
				try {
					os.write(pwrt.bb.array());
					count.incrementAndGet();
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				s.release();
			}
			
			@Override
			public void finished() {
				// TODO Auto-generated method stub
				
			}
		});
    	mdbgen.start();
    	
    	s.acquire();
    	mdbgen.stop();
    	
    	System.out.println("wrote "+count.get()+" packets into "+f);
    }


    @Override
    protected void doStart() {
        notifyStarted();        
    }


    @Override
    protected void doStop() {
        notifyStopped();
    }
}
