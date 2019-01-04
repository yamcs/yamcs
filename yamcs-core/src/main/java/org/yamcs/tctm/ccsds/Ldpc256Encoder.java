package org.yamcs.tctm.ccsds;

import org.yamcs.utils.ByteArrayUtils;

/**
 * Encoder for the LDPC codec (n=128,k=64) as specified in
 * RECOMMENDED STANDARD FOR TC SYNCHRONIZATION AND CHANNEL CODING
 * CCSDS 231.0-B-3  September 2017
 * 
 * @author nm
 *
 */
public class Ldpc256Encoder {

    static final long[][] W256 = new long[][] {
        { 0x1D21794A22761FAEL, 0x59945014257E130DL, 0x74D6054003794014L, 0x2DADEB9CA25EF12EL },
        { 0x60E0B6623C5CE512L, 0x4D2C81ECC7F469ABL, 0x20678DBFB7523ECEL, 0x2B54B906A9DBE98CL },
        { 0xF6739BCF54273E77L, 0x167BDA120C6C4774L, 0x4C071EFF5E32A759L, 0x3138670C095C39B5L },
        { 0x28706BD045300258L, 0x2DAB85F05B9201D0L, 0x8DFDEE2D9D84CA88L, 0xB371FAE63A4EB07EL }
};
    
    public static void encode(byte[] in, int inOffset, byte[] out, int outOffset) {
        long r0 = 0;
        long r1 = 0;
        long r2 = 0;
        long r3 = 0;
        
        for (int i = 0; i < 4; i++) {
            long wl0 = W256[i][0];
            long wl1 = W256[i][1];
            long wl2 = W256[i][2];
            long wl3 = W256[i][3];
            
            for (int j = 0; j < 8; j++) {
                int d = in[inOffset++];
                for (int k = 7; k >= 0; k--) {
                    if (((d >>> k) & 1) == 1) {
                        r0 = r0 ^ wl0;
                        r1 = r1 ^ wl1;
                        r2 = r2 ^ wl2;
                        r3 = r3 ^ wl3;
                    }
                    wl0 = Long.rotateRight(wl0, 1);
                    wl1 = Long.rotateRight(wl1, 1);
                    wl2 = Long.rotateRight(wl2, 1);
                    wl3 = Long.rotateRight(wl3, 1);
                }
            }
        }
        ByteArrayUtils.encodeLong(r0, out, outOffset);
        ByteArrayUtils.encodeLong(r1, out, outOffset+8);
        ByteArrayUtils.encodeLong(r2, out, outOffset+16);
        ByteArrayUtils.encodeLong(r3, out, outOffset+24);
    }
    

    //this performs slightly worse than the method above   
    public static void encode2(byte[] in, int inOffset, byte[] out, int outOffset) {
        long[] r = new long[4];//{0, 0, 0, 0};
        
        for (int i = 0; i < 4; i++) {
            long[] wl = {W256[i][0], W256[i][1], W256[i][2], W256[i][3]};//Arrays.copyOf(W256[i], 4);
            
            for (int j = 0; j < 8; j++) {
                int d = in[inOffset++];
                for (int k = 7; k >= 0; k--) {
                    if (((d >>> k) & 1) == 1) {
                        for(int v = 0; v<4; v++) {
                            r[v] =r[v]^wl[v];
                        }
                    }
                    for(int v = 0; v<4; v++) {
                        wl[v] = Long.rotateRight(wl[v], 1);
                    }
                }
            }
        }
        for(int v = 0; v<4; v++) {
            ByteArrayUtils.encodeLong(r[v], out, outOffset+8*v);
        }
    }
}
