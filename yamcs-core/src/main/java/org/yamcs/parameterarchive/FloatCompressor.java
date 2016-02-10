package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;

import org.yamcs.utils.BitReader;
import org.yamcs.utils.BitWriter;

/**
 * Implements the floating point compression scheme described here:
 * http://www.vldb.org/pvldb/vol8/p1816-teller.pdf
 * 
 * @author nm
 *
 */
public class FloatCompressor {
   /**
    * *compress the first n elements from the array of floats into the ByteBuffer
    * */
    static public void compress(float[] fa, int n, ByteBuffer bb) {
        BitWriter bw=new BitWriter(bb);

        int xor;
        int prevV = Float.floatToRawIntBits(fa[0]);
        bw.write(prevV, 32);
        
        int prevLz = 100; //such that the first comparison lz>=prevLz will fail
        int prevTz = 0;

        for(int i=1; i<n; i++) {
        //	System.out.println("bb.position: "+bb.position()+" i: "+i+" fa.length: "+fa.length);
            int v = Float.floatToRawIntBits(fa[i]);
            xor = v^prevV;
            //If XOR with the previous is zero (same value), store single ‘0’ bit
            if(xor==0) {
                bw.write(0, 1);
            } else {
                //When XOR is non-zero, calculate the number of leading and trailing zeros in the XOR, store bit ‘1’ followed
                // by either a) or b):
                bw.write(1, 1);
                int lz = Integer.numberOfLeadingZeros(xor);
                int tz = Integer.numberOfTrailingZeros(xor);
                if((lz>=prevLz) && (tz>=prevTz) &&(lz<prevLz+7)) {
                	//if((lz==prevLz)&&(tz==prevTz)) {
                    //(a) (Control bit ‘0’) If the block of meaningful bits falls within the block of previous meaningful bits,
                    //i.e., there are at least as many leading zeros and as many trailing zeros as with the previous value,
                    //use that information for the block position and just store the meaningful XORed value.
                    bw.write(0, 1);
                    bw.write(xor>>prevTz, 32-prevLz-prevTz);
                } else {
                    //(b) (Control bit ‘1’) Store the length of the number  of leading zeros in the next 5 bits, then store the
                    // length of the meaningful XORed value in the next 6 bits. Finally store the meaningful bits of the XORed value.
                    int mb = 32-lz-tz; //meaningful bits
                    
                    bw.write(1, 1);
                    bw.write(lz, 5);
                    bw.write(mb, 5);
                    bw.write(xor>>tz, mb);
                    prevLz = lz;
                    prevTz = tz;
                }

            }
            prevV = v;
        }
        bw.flush();
    }

    public static float[] decompress(ByteBuffer bb, int n) {
        BitReader br = new BitReader(bb);
        float[] fa = new float[n];
        int xor;
        int v = (int)br.read(32);
        fa[0] = Float.intBitsToFloat(v);
        
        int lz = 0; //leading zeros
        int tz = 0; //trailing zeros
        int mb = 0; //meaningful bits
        for(int i=1; i<fa.length; i++) {
            int bit = br.read(1);
            if(bit==0) {
                //same with the previous value
                fa[i]=fa[i-1];
            } else {
            	
                bit = br.read(1);
                if(bit==0) {//the block of meaningful bits falls within the block of previous meaningful bits,
                    xor = br.read(mb)<<tz;
                    v = xor^v;
                } else {
                    lz = br.read(5);
                    mb = br.read(5);
                    //this happens when mb is 32 and overflows the 5 bits
                    if(mb==0) mb=32;
                    tz = 32-lz-mb;
                    xor = br.read(mb)<<tz;
                    v = xor^v;
                }
                fa[i] = Float.intBitsToFloat(v);
            }
        }
        
        return fa;
    }

    public static void compress(float[] fa, ByteBuffer bb) {
        compress(fa, fa.length, bb);
    }
}




