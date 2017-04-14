package org.yamcs.tctm;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;

/**
 * Plays files in pacts format, hrdp format or containing raw ccsds packets.
 * @author nm
 *
 */
public class TmFileReader  {
    protected InputStream inputStream;
    int fileoffset = 0;
    int packetcount = 0;

    /**
     * Constructs a reader for telemetry files. It reads the first two bytes to see if it's gzip
     * @param fileName
     * @throws IOException 
     */
    public TmFileReader(String fileName) throws IOException {
        inputStream = new FileInputStream(fileName);
        boolean gzip = false;
        
        //read the first two bytes to check if it's gzip
        byte[] b = new byte[2];
        int x = inputStream.read(b);
        if((x==2) && (b[0]==0x1F) && ((b[1]&0xFF)==0x8B)) {
            gzip = true;
        }
        inputStream.close();
        
        if(gzip) {
            inputStream = new BufferedInputStream(new GZIPInputStream(new FileInputStream(fileName)));
        } else {
            inputStream = new BufferedInputStream(new FileInputStream(fileName));
        }
    }

    public PacketWithTime readPacket(long rectime) throws IOException {
        int  res;
        byte[] buffer;
        byte[] fourb=new byte[4];
        res = inputStream.read(fourb);
        if ( res == -1 ) {
            inputStream.close();
            return null;
        } else if(res!=4){
            System.err.println("fourb: "+StringConverter.arrayToHexString(fourb));
            inputStream.close();
            throw new IOException("Could only read "+res+" out of 4 bytes. Corrupted file?");
        } 

        byte[] ccsdshdr=new byte[16];
        int ccsdshdroffset=0;
        boolean isPacts=false;

        if((fourb[2]==0)&&(fourb[3]==0)) {//hrdp packet: first 4 bytes are the size in little endian
            byte[] b=new byte[6];
            res=inputStream.read(b);
            if(res!=6) {
                inputStream.close();
                throw new IOException("Could only read "+res+" out of 6 bytes. Corrupted file?");
            } else {
                ByteBuffer bb=ByteBuffer.wrap(b);
                long unixTimesec=(0xFFFFFFFFL & (long)bb.getInt(1))+315964800L;
                int unixTimeMicrosec=(bb.get()&0xFF)*(1000000/256);
                rectime=TimeEncoding.fromUnixTime(unixTimesec,unixTimeMicrosec);
            }
        } else if ((fourb[0] & 0xe8) == 0x08) {// CCSDS packet
            System.arraycopy(fourb, 0, ccsdshdr, 0, 4);
            ccsdshdroffset=4;
        } else {//pacts packet
            isPacts=true;
            // read ASCII header up to the second blank
            int i, j;
            StringBuilder hdr = new StringBuilder();
            j = 0;
            for(i=0;i<4;i++) {
                hdr.append((char)fourb[i]);
                if ( fourb[i] == 32 ) {
                    ++j;
                }
            }
            while((j < 2) && (i < 20)) {
                int c = inputStream.read();
                if(c==-1) {
                    inputStream.close();
                    throw new IOException("short PaCTS ASCII header: '"+ hdr.toString() + "'");
                }
                hdr.append((char)c);
                if ( c == 32 ) {
                    ++j;
                }
                i++;
            }

            if ( i == 20 ) {
                inputStream.close();
                throw new IOException("ASCII header too long, probably not a PaCTS archive file: '" +	hdr.toString() + "'");
            }
        }

        res = inputStream.read(ccsdshdr, ccsdshdroffset, 16-ccsdshdroffset);
        //System.out.println("Read ccsdshdr: "+StringConvertors.arrayToHexString(ccsdshdr));
        if (res != 16-ccsdshdroffset) {
            inputStream.close();
            throw new IOException("CCSDS packet header short read " + res + "/16-ccsdshdroffset");
        }
        int len = ((ccsdshdr[4] & 0xff)<<8) + (ccsdshdr[5] & 0xff) + 7;
        if((len<16)||len>CcsdsPacket.MAX_CCSDS_SIZE) {
            inputStream.close();
            throw new IOException("invalid ccsds packet of length "+len+". Corrupted file?");
        }
        buffer = Arrays.copyOf(ccsdshdr, len);
        res = inputStream.read(buffer, 16, len - 16);
        if (res != len - 16) {
            inputStream.close();
            throw new IOException("CCSDS packet body short read " + res + "/" + (len - 16));
        }
        if(isPacts) {
            if(inputStream.skip(1)!=1) {// terminator newline
                inputStream.close();
                throw new IOException("no new line at the end of the PaCTS packet");
            }
        }
        return new PacketWithTime(rectime, CcsdsPacket.getInstant(buffer), buffer);		
    }

    public void close() throws IOException {
        inputStream.close();
    }

    public static void main(String[] args) throws IOException, ConfigurationException {
        YConfiguration.setup();
        TmFileReader tfr=new TmFileReader(args[0]);
        PacketWithTime pwrt;

        while((pwrt=tfr.readPacket(TimeEncoding.getWallclockTime()))!=null) {
            CcsdsPacket c=new CcsdsPacket(pwrt.getPacket());
            System.out.println("rectime: "+TimeEncoding.toString(pwrt.rectime)+" apid:" +c.getAPID()+" seq: "+c.getSequenceCount()+" coarse: "+c.getCoarseTime()+" fine: "+c.getFineTime()+
                    " time: "+ TimeEncoding.toCombinedFormat(c.getInstant())+" received: "+TimeEncoding.toCombinedFormat(pwrt.rectime)+" delta: "+(pwrt.rectime-c.getInstant()));

        }
    }
}
