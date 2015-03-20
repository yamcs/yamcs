package org.yamcs;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.utils.GpsCcsdsTime;
import org.yamcs.utils.StringConvertors;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractService;

/**
 * Generates packets according to the refmdb database
 * @author nm
 *
 */
public class RefMdbPacketGenerator extends AbstractService implements TmPacketProvider {
    TmProcessor tmProcessor;
    public final int headerLength=16;
    public final int pkt1Length=headerLength+3;
    public final int pkt11Length=pkt1Length+56;
    public final int pkt12Length=pkt1Length+7;
    public final int pkt13Length=pkt1Length+100;
    public final int pkt14Length=pkt1Length+100;
    public final int pkt15Length=pkt1Length+50;
    public final int pkt16Length=pkt1Length+4;
    public final int pkt17Length=pkt1Length+6;
    public final int pkt18Length=pkt1Length+6;
    public final int pkt19Length=pkt1Length+1;
    public final int pkt1_10Length=pkt1Length+8;
    public final int pkt1_11Length=pkt1Length+4;
    public final int pkt2Length=8;
    
    //raw values of parameters 
    public volatile short pIntegerPara1_1=5;
    
    
    public volatile byte pIntegerPara11_1=20;
    public volatile short pFloatPara11_2=1000;
    public volatile float pFloatPara11_3=2;
    public volatile byte pEnumerationPara11_4=0;
    public volatile String pStringPara11_5="cucu";
    public volatile int pIntegerPara11_6=236;
    public volatile byte pIntegerPara11_7=34;
    public volatile long pIntegerPara11_8=5084265585L;
    public volatile int pIntegerPara1_11_1=0xAFFFFFFE; // a uint32 stored in signed java int
    public volatile long pIntegerPara1_11_1_unsigned_value=2952790014L; // the equivalent unsigned value
    
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
    
    public volatile int pIntegerPara2_1 = 123;
    public volatile int pIntegerPara2_2 = 25;
    
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
    
    public ByteBuffer generate_PKT2() {
        ByteBuffer bb=ByteBuffer.allocate(pkt2Length);
        fill_PKT2(bb);
        sendToTmProcessor(bb);
        return bb;
    }
    
    /**
     * Generate a packet with configurable content
     */
    public ByteBuffer generate_PKT16(int pIntegerPara16_1, int pIntegerPara16_2) {
        return generate_PKT16(pIntegerPara16_1, pIntegerPara16_2, TimeEncoding.currentInstant(), TimeEncoding.currentInstant());
    }
    
    /**
     * Generate a packet with configurable content
     */
    public ByteBuffer generate_PKT16(int pIntegerPara16_1, int pIntegerPara16_2, long rectime, long gentime) {
        ByteBuffer bb=ByteBuffer.allocate(pkt16Length);
        fill_PKT16(bb, pIntegerPara16_1, pIntegerPara16_2);
        sendToTmProcessor(bb, rectime, gentime);
        return bb;
    }
    
    public ByteBuffer generate_PKT17() {
        ByteBuffer bb=ByteBuffer.allocate(pkt17Length);
        fill_PKT17(bb);
        sendToTmProcessor(bb);
        return bb;
    }
    
    public ByteBuffer generate_PKT18(int pIntegerPara18_1, int pIntegerPara18_2) {
        ByteBuffer bb=ByteBuffer.allocate(pkt18Length);
        fill_PKT18(bb, pIntegerPara18_1, pIntegerPara18_2);
        sendToTmProcessor(bb);
        return bb;
    }
    
    public ByteBuffer generate_PKT19() {
        ByteBuffer bb=ByteBuffer.allocate(pkt19Length);
        fill_PKT19(bb);
        sendToTmProcessor(bb);
        return bb;
    }
    
    public ByteBuffer generate_PKT1_10(int pIntegerPara1_10_1, int pEnumerationPara1_10_2, float pFloatPara1_10_3) {
        ByteBuffer bb=ByteBuffer.allocate(pkt1_10Length);
        fill_PKT1_10(bb, pIntegerPara1_10_1, pEnumerationPara1_10_2, pFloatPara1_10_3);
        sendToTmProcessor(bb);
        return bb;
    }
    
    public ByteBuffer generate_PKT1_11() {
        ByteBuffer bb=ByteBuffer.allocate(pkt1_11Length);
        fill_PKT1_11(bb);
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
        int offset=pkt1Length;
        bb.position(offset);
        bb.put(pIntegerPara11_1);
        bb.putShort(pFloatPara11_2);
        bb.putFloat(pFloatPara11_3);
        bb.put(pEnumerationPara11_4);
        
        bb.put((byte)(pIntegerPara11_6>>16));
        bb.putShort((short)(pIntegerPara11_6&0xFFFF));
        bb.put(pIntegerPara11_7);
        
        bb.putShort((short)(pIntegerPara11_8>>32));
        bb.putInt((int)pIntegerPara11_8&0xFFFFFFFF);
        
        byte[] b=new byte[10];
        System.arraycopy(pStringPara11_5.getBytes(), 0, b, 0, pStringPara11_5.getBytes().length);
        bb.put(b);
    }
    
    private void fill_PKT13(ByteBuffer bb) {
        fill_PKT1(bb, 3);
        int offset=pkt1Length;
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
        int offset=pkt1Length;
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
        int offset=pkt1Length;
        bb.position(offset);
        
        // Integers in strings
        putFixedStringParam(bb, pStringIntFixedPara15_1, 24);
        putTerminatedStringParam(bb, pStringIntTermPara15_2, (byte)',');
        putTerminatedStringParam(bb, pStringIntTermPara15_3, (byte)';');
        putPrependedSizeStringParam(bb, pStringIntPrePara15_4, 16);
        // Straight string is null terminated
        putTerminatedStringParam(bb, pStringIntStrPara15_5, (byte)0);
    }
    
    private void fill_PKT2(ByteBuffer bb) {
        bb.position(4);
        bb.putShort((short)(pIntegerPara2_1&0xFFFF));
        bb.putShort((short)(pIntegerPara2_2&0xFFFF));
    }
    
    private void fill_PKT16(ByteBuffer bb, int pIntegerPara16_1, int pIntegerPara16_2) {
        fill_PKT1(bb, 6);
        int offset=pkt1Length;
        bb.position(offset);
        bb.putShort((short)(pIntegerPara16_1&0xFFFF));
        bb.putShort((short)(pIntegerPara16_2&0xFFFF));
    }
    
    private void fill_PKT17(ByteBuffer bb) {
        fill_PKT1(bb, 7);
        int offset=pkt1Length;
        bb.position(offset);
        
        // 16-bit signed integer (in sign-magnitude)
        bb.put(StringConvertors.hexStringToArray("BA50"));
        // 6 (000110), filler (000), -6 (100110) (sign-magnitude)
        bb.put(StringConvertors.hexStringToArray("1846"));
        // 6 (000110), filler (000), -6 (111010) (2's complement)
        bb.put(StringConvertors.hexStringToArray("187A"));
    }
    
    private void fill_PKT18(ByteBuffer bb, int pIntegerPara18_1, int pIntegerPara18_2) {
        fill_PKT1(bb, 8);
        int offset=pkt1Length;
        bb.position(offset);
        
        bb.putShort((short)(pIntegerPara18_1&0xFFFF));
        bb.putInt(pIntegerPara18_2);
    }
    
    private void fill_PKT19(ByteBuffer bb) {
        fill_PKT1(bb, 9);
        int offset=pkt1Length;
        bb.position(offset);
        bb.put((byte) 0xA1);
    }
    
    private void fill_PKT1_10(ByteBuffer bb, int pIntegerPara1_10_1, int pEnumerationPara1_10_2, float pFloatPara1_10_3) {
        fill_PKT1(bb, 10);
        int offset=pkt1Length;
        bb.position(offset);
        bb.putShort((short) pIntegerPara1_10_1);
        bb.put((byte) pEnumerationPara1_10_2);
        bb.put((byte) 0);
        bb.putFloat(pFloatPara1_10_3);
    }
    
    private void fill_PKT1_11(ByteBuffer bb) {
        fill_PKT1(bb, 11);
        int offset=pkt1Length;
        bb.position(offset);
        bb.putInt(pIntegerPara1_11_1);
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
        sendToTmProcessor(bb, TimeEncoding.currentInstant(), TimeEncoding.currentInstant());
    }
    
    private void sendToTmProcessor(ByteBuffer bb, long rectime, long gentime) {
	if(tmProcessor!=null) {
	    tmProcessor.processPacket(new PacketWithTime(rectime, gentime, bb.array()));
	}
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
					os.write(pwrt.getPacket());
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
    	mdbgen.startAsync();
    	
    	s.acquire();
    	mdbgen.stopAsync();
    	
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
