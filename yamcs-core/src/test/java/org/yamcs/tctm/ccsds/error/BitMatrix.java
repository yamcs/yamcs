package org.yamcs.tctm.ccsds.error;

import java.util.Arrays;
import java.util.BitSet;

/**
 * simple bit matrix based on java bitsets
 * 
 * used only for testing the LDPC coding implementation
 * 
 * @author nm
 *
 */
public class BitMatrix {
    int n; // num rows
    int m;// num cols

    BitSet[] rows;

    static byte lookup[] = {
            0x0, 0x8, 0x4, 0xc, 0x2, 0xa, 0x6, 0xe,
            0x1, 0x9, 0x5, 0xd, 0x3, 0xb, 0x7, 0xf, };

    public BitMatrix(int n, int m) {
        this(n, m, true);
    }

    private BitMatrix(int n, int m, boolean init) {
        this.m = m;
        this.n = n;
        rows = new BitSet[n];
        if (init) {
            for (int i = 0; i < n; i++) {
                rows[i] = new BitSet(m);
            }
        }
    }

    public static BitMatrix ZeroMatrix(int n) {
        return new BitMatrix(n, n);
    }

    public static BitMatrix IdentityMatrix(int n) {
        BitMatrix bm = new BitMatrix(n, n);
        for (int i = 0; i < n; i++) {
            bm.rows[i].set(i);
        }
        return bm;
    }

    public static BitMatrix compose(BitMatrix[][] bm) {
        int n = 0;
        int m = 0;
        for (int i = 0; i < bm.length; i++) {
            n += bm[i][0].n;
        }
        for (int j = 0; j < bm[0].length; j++) {
            m += bm[0][j].m;
        }
        int rc = 0;
        BitMatrix r = new BitMatrix(n, m, false);
        for (BitMatrix[] bmr : bm) {
            for (int i = 0; i < bmr[0].n; i++) {
                BitSet bs = bmr[0].rows[i];
                int bsl = bmr[0].m;
                for (int j = 1; j < bmr.length; j++) {
                    bs = concat(bs, bsl, bmr[j].rows[i], bmr[0].m);
                    bsl += bmr[0].m;
                }
                r.rows[rc] = bs;
                rc++;
            }
        }
        return r;
    }

    public static BitMatrix add(BitMatrix bm1, BitMatrix bm2) {
        if (bm1.n != bm2.n || bm1.m != bm2.m) {
            throw new IllegalArgumentException(" the matrices should have the same number of rows and columns");
        }
        BitMatrix r = new BitMatrix(bm1.n, bm2.m, false);
        for (int i = 0; i < bm1.n; i++) {
            BitSet bs = (BitSet) bm1.rows[i].clone();
            bs.xor(bm2.rows[i]);
            r.rows[i] = bs;
        }

        return r;
    }

    // right shifts all rows by x positions
    public BitMatrix circularRightShift(int x) {
        BitMatrix bm = new BitMatrix(n, m, false);
        for (int i = 0; i < n; i++) {
            bm.rows[i] = shiftRightCircular(rows[i], m, x);
        }
        return bm;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(n).append("x").append(m).append(": {\n");
        for (int i = 0; i < n; i++) {
            sb.append("  ");
            sb.append(i).append(":").append(rows[i]).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private static BitSet concat(BitSet bs1, int m1, BitSet bs2, int m2) {
        BitSet bs = new BitSet(m1 + m2);
        for (int i = 0; i < m1; i++) {
            bs.set(i, bs1.get(i));
        }
        for (int i = 0; i < m2; i++) {
            bs.set(i + m1, bs2.get(i));
        }
        return bs;
    }

    private static BitSet shiftRightCircular(BitSet bs, int m, int x) {
        BitSet bs1 = new BitSet(m);
        for (int i = 0; i < m; i++) {
            bs1.set((i + x) % m, bs.get(i));
        }
        return bs1;
    }

    public byte[] multiply(byte[] d) {
        if (d.length != m / 8) {
            throw new IllegalArgumentException("Bad array length, should be " + (m / 8));
        }
        byte[] d1 = new byte[d.length];
        for (int i = 0; i < d.length; i++) {
            d1[i] = reverse(d[i]);
        }
        BitSet dbs = BitSet.valueOf(d1);
       // System.out.println("dbs: "+dbs);
        
        BitSet r = new BitSet(n);

        for (int i = 0; i < n; i++) {
            BitSet bs = (BitSet) rows[i].clone();
            bs.and(dbs);
            
            r.set(i, (bs.cardinality()&1)==1);
        }
        
        d1 = r.toByteArray();
        byte[] d2 = new byte[n / 8];
        for (int i = 0; i < d1.length; i++) {
            d2[i] = reverse(d1[i]);
        }

        return d2;
    }

    byte reverse(byte x) {
        return (byte) ((lookup[x & 0xF] << 4) | lookup[(x >>> 4) & 0xF]);
    }

  

    public static BitMatrix from(long[][] x) {
        BitMatrix bm = new BitMatrix(x.length, 64 * x[0].length, false);
        for (int i = 0; i < x.length; i++) {
            bm.rows[i] = getBitset(x[i]);
        }
        return bm;
    }

    public static BitMatrix from(short[][] x) {
        BitMatrix bm = new BitMatrix(x.length, 16 * x[0].length, false);
        for (int i = 0; i < x.length; i++) {
            bm.rows[i] = getBitset(x[i]);
        }
        return bm;
    }

    private static BitSet getBitset(short[] a) {
        BitSet bs = new BitSet(a.length * 16);
        for (int i = 0; i < a.length; i++) {
            int x = 0xFFFF & a[i];
            for (int j = 0; j < 16; j++) {
                bs.set(16 * i + j, (((x >> (15-j)) & 1) == 1));
            }
        }
        return bs;
    }

    private static BitSet getBitset(long[] l) {
        BitSet bs = new BitSet(l.length * 64);
        for (int i = 0; i < l.length; i++) {
            long x = l[i];
            for (int j = 0; j < 64; j++) {
                bs.set(8 * i + j, (((x >> (63-j)) & 1) == 1));
            }
        }
        return bs;
    }

    public BitMatrix transpose() {
        BitMatrix bm = new BitMatrix(m, n, true);
        for(int i = 0; i<m; i++) {
            for(int j = 0; j<n; j++) {
                bm.rows[i].set(j, rows[j].get(i));
            }
        }
        return bm;
    }
    
    
    public static void main(String[] args) {
        BitSet bs = new BitSet();
        bs.set(0);
        bs.set(2);

        System.out.println("bs: " + bs);

        BitSet bs2 = shiftRightCircular(bs, 4, 1);
        System.out.println("bs2: " + bs2);

        BitMatrix b0 = ZeroMatrix(2);
        BitMatrix b1 = IdentityMatrix(2);

        System.out.println("b1: " + b1);

        System.out.println("b0 + b1: " + add(b0, b1));
        System.out.println("b1 + b1: " + add(b1, b1));

        BitMatrix b = compose(new BitMatrix[][] { { b1, b0 }, { b1, b1 } });
        System.out.println(b);

        System.out.println("------ circular right shift");

        System.out.println(b.circularRightShift(1));
        System.out.println("------ circular right shift transposed");
        System.out.println(b.circularRightShift(1).transpose());

        byte[] d = new byte[] { 5 };
        BitMatrix i8 = IdentityMatrix(8);
        System.out.println("I8*5: " + Arrays.toString(i8.multiply(d)));

        BitMatrix z8 = ZeroMatrix(8);
        System.out.println("Z8*d: " + Arrays.toString(z8.multiply(d)));
        
        System.out.println("I8.transposed: " + i8.transpose());
    }
}
