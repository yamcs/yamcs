package org.yamcs.cfdp;

/**
 * CRC32C helpers complementing {@link java.util.zip.CRC32C}.
 * <p>
 * {@link #combine(int, int, long)} computes the CRC of the concatenation of two blocks from their individual CRCs in
 * O(log(len2)) without accessing the data (same algorithm as zlib's crc32_combine, using the Castagnoli polynomial).
 */
public class Crc32cUtils {
    // reflected Castagnoli polynomial
    private static final int POLY = 0x82F63B78;
    // X2N[k] = x^(2^k) mod p(x)
    private static final int[] X2N = new int[32];

    static {
        int p = 1 << 30; // x^1
        X2N[0] = p;
        for (int n = 1; n < 32; n++) {
            X2N[n] = p = multmodp(p, p);
        }
    }

    // a(x) * b(x) mod p(x), with a and b in reflected order
    private static int multmodp(int a, int b) {
        int m = 1 << 31;
        int p = 0;
        for (;;) {
            if ((a & m) != 0) {
                p ^= b;
                if ((a & (m - 1)) == 0) {
                    break;
                }
            }
            m >>>= 1;
            b = (b & 1) != 0 ? (b >>> 1) ^ POLY : b >>> 1;
        }
        return p;
    }

    // x^(n * 2^k) mod p(x)
    private static int x2nmodp(long n, int k) {
        int p = 1 << 31; // x^0 == 1
        while (n != 0) {
            if ((n & 1) != 0) {
                p = multmodp(X2N[k & 31], p);
            }
            n >>>= 1;
            k++;
        }
        return p;
    }

    /**
     * Returns the CRC32C of the concatenation A|B given crc1 = CRC32C(A), crc2 = CRC32C(B) and len2 = length(B).
     */
    public static int combine(int crc1, int crc2, long len2) {
        if (len2 <= 0) {
            return crc1;
        }
        return multmodp(x2nmodp(len2, 3), crc1) ^ crc2;
    }
}
