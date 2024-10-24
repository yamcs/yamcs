package org.yamcs.rs;
/* Reed-Solomon decoder
 * Copyright 2002 Phil Karn, KA9Q
 * May be used under the terms of the GNU Lesser General Public License (LGPL)
 * 
 * Translated from C to Java by Nicolae Mihalache, Space Applications Services
 */

import java.util.Arrays;

/**
 * Reed-Solomon encoder/decoder.
 * 
 */
public class ReedSolomon {
    int nroots;
    final int nn;
    final byte[] _alpha_to;
    final byte[] _index_of;
    final int fcr;
    final int prim;
    int iprim;

    // Special reserved value encoding zero in index form
    byte A0;
    int gfpoly;
    int symsize;
    final byte[] genpoly;
    final int pad;

    /**
     * Constructs a new encoder/decoder.
     * 
     * @param nroots
     *            the number of roots in the Reed Solomon code generator polynomial. This equals the number of parity
     *            symbols per code block.
     * @param symsize
     *            the symbol size in bits, up to 8
     * @param fcr
     *            in index form, the first consecutive root of the Reed Solomon code generator polynomial.
     * @param prim
     *            in index form, the primitive element in the Galois field used to generate the Reed Solomon code
     *            generator polynomial.
     * @param gfpoly
     *            the extended Galois field generator polynomial coefficients, with the 0th coefficient in the low order
     *            bit. The polynomial must be primitive; if not, an IllegalArgumentException will be thrown.
     * 
     * @param pad
     *            the number of leading symbols in the codeword that are implicitly padded to zero in a shortened code
     *            block
     */
    public ReedSolomon(int nroots, int symsize, int fcr, int prim, int gfpoly, int pad) {
        if (symsize < 2 || symsize > 8) {
            throw new IllegalArgumentException("Invalid symbol size");
        }
        this.nn = (1 << symsize) - 1;

        if (fcr < 0 || fcr > nn) {
            throw new IllegalArgumentException("Invalid fcr");
        }
        if (prim <= 0 || prim > nn) {
            throw new IllegalArgumentException("Invalid prim");
        }
        if (nroots < 0 || nroots > nn) {
            throw new IllegalArgumentException("Can't have more roots than symbol values!");
        }
        if (pad < 0 || pad >= (nn - nroots)) {
            throw new IllegalArgumentException("Too much padding");
        }

        this.nroots = nroots;
        this.symsize = symsize;

        this._alpha_to = new byte[nn + 1];
        this._index_of = new byte[nn + 1];
        this.fcr = fcr;
        this.prim = prim;
        this.A0 = (byte) nn;
        this.gfpoly = gfpoly;
        this.genpoly = new byte[nroots + 1];
        this.pad = pad;
        init();
    }

    private void init() {
        _index_of[0] = A0;
        _alpha_to[A0 & 0xFF] = 0;
        int sr = 1;
        for (int i = 0; i < nn; i++) {
            _index_of[sr] = (byte) i;
            _alpha_to[i] = (byte) sr;
            sr <<= 1;
            if ((sr & (1 << symsize)) != 0) {
                sr ^= gfpoly;
            }
            sr &= nn;
        }
        if (sr != 1) {
            throw new IllegalArgumentException("field generator polynomial is not primitive!");
        }

        /* Find prim-th root of 1, used in decoding */
        for (iprim = 1; (iprim % prim) != 0; iprim += nn)
            ;
        iprim = iprim / prim;

        genpoly[0] = 1;
        for (int i = 0, root = fcr * prim; i < nroots; i++, root += prim) {
            genpoly[i + 1] = 1;

            /* Multiply rs->genpoly[] by @**(root + x) */
            for (int j = i; j > 0; j--) {
                if (genpoly[j] != 0)
                    genpoly[j] = (byte) (genpoly[j - 1] ^ alpha_to(index_of(genpoly[j]), root));
                else
                    genpoly[j] = genpoly[j - 1];
            }
            /* rs->genpoly[0] can never be zero */
            genpoly[0] = alpha_to(index_of(genpoly[0]), root);
        }

        /* convert rs->genpoly[] to index form for quicker encoding */
        for (int i = 0; i <= nroots; i++) {
            genpoly[i] = index_of(genpoly[i]);
        }
    }

    /**
     * Generate the parity for the given data
     * 
     * @param data
     *            - data to be encoded
     * @param parity
     *            - the resulting parity bytes
     */
    public void encode(byte[] data, byte[] parity) {
        int i, j;
        byte feedback;

        Arrays.fill(parity, (byte) 0);

        for (i = 0; i < nn - nroots - pad; i++) {
            feedback = index_of(data[i] ^ parity[0]);
            if (feedback != A0) { /* feedback term is non-zero */
                for (j = 1; j < nroots; j++) {
                    parity[j] ^= alpha_to(feedback, genpoly[nroots - j]);
                }
            }
            /* Shift */
            System.arraycopy(parity, 1, parity, 0, nroots - 1);
            if (feedback != A0) {
                parity[nroots - 1] = alpha_to(feedback, genpoly[0]);
            } else {
                parity[nroots - 1] = 0;
            }
        }
    }

    /**
     * Corrects in place data and returns the number of error corrected.
     * 
     * @param data
     *            - data to be corrected
     * @param eras_pos
     *            - erasures positions (can be null)
     * @return
     * @throws ReedSolomonException
     *             - thrown if the data cannot be corrected
     */
    public int decode(byte[] data, int[] eras_pos) throws ReedSolomonException {
        int deg_lambda, el, deg_omega;
        int i, j, r, k;
        byte u, q, tmp, num1, num2, den, discr_r;

        byte[] lambda = new byte[nroots + 1];
        byte[] s = new byte[nroots]; /*
                                      * Err+Eras Locator poly
                                      * and syndrome poly
                                      */
        byte[] b = new byte[nroots + 1];
        byte[] t = new byte[nroots + 1];
        byte[] omega = new byte[nroots + 1];
        int[] root = new int[nroots];
        byte[] reg = new byte[nroots + 1];
        int[] loc = new int[nroots];
        int syn_error, count;
        for (i = 0; i < nroots; i++) {
            s[i] = data[0];
        }

        /* form the syndromes; i.e., evaluate data(x) at roots of g(x) */
        for (j = 1; j < nn - pad; j++) {
            for (i = 0; i < nroots; i++) {
                if (s[i] == 0) {
                    s[i] = data[j];
                } else {
                    s[i] = (byte) (data[j] ^ alpha_to(index_of(s[i]), (fcr + i) * prim));
                }
            }
        }
        /* Convert syndromes to index form, checking for nonzero condition */
        syn_error = 0;
        for (i = 0; i < nroots; i++) {
            syn_error |= s[i];
            s[i] = index_of(s[i]);
        }

        if (syn_error == 0) {
            /*
             * if syndrome is zero, data[] is a codeword and there are no
             * errors to correct. So return data[] unmodified
             */
            count = 0;
            return 0;
        }
        lambda[0] = 1;
        int no_eras = 0;
        if (eras_pos != null) {
            no_eras = eras_pos.length;
            /* Init lambda to be the erasure locator polynomial */
            lambda[1] = alpha_to(prim * (nn - 1 - eras_pos[0]));
            for (i = 1; i < eras_pos.length; i++) {
                u = (byte) modnn(prim * (nn - 1 - eras_pos[i]));
                for (j = i + 1; j > 0; j--) {
                    tmp = index_of(lambda[j - 1]);
                    if (tmp != A0)
                        lambda[j] ^= alpha_to(u, tmp);
                }
            }
        }
        for (i = 0; i < nroots + 1; i++)
            b[i] = index_of(lambda[i]);

        /*
         * Begin Berlekamp-Massey algorithm to determine error+erasure
         * locator polynomial
         */
        r = no_eras;
        el = no_eras;
        while (++r <= nroots) { /* r is the step number */
            /* Compute discrepancy at the r-th step in poly-form */
            discr_r = 0;
            for (i = 0; i < r; i++) {
                if ((lambda[i] != 0) && (s[r - i - 1] != A0)) {
                    discr_r ^= alpha_to(index_of(lambda[i]), s[r - i - 1]);
                }
            }
            discr_r = index_of(discr_r); /* Index form */
            if (discr_r == A0) {
                /* 2 lines below: B(x) <-- x*B(x) */
                // memmove(&b[1],b,NROOTS*sizeof(b[0]));
                System.arraycopy(b, 0, b, 1, nroots);
                b[0] = A0;
            } else {
                /* 7 lines below: T(x) <-- lambda(x) - discr_r*x*b(x) */
                t[0] = lambda[0];
                for (i = 0; i < nroots; i++) {
                    if (b[i] != A0)
                        t[i + 1] = (byte) (lambda[i + 1] ^ alpha_to(discr_r, b[i]));
                    else
                        t[i + 1] = lambda[i + 1];
                }
                if (2 * el <= r + no_eras - 1) {
                    el = r + no_eras - el;
                    /*
                     * 2 lines below: B(x) <-- inv(discr_r) *
                     * lambda(x)
                     */
                    for (i = 0; i <= nroots; i++) {
                        b[i] = (byte) ((lambda[i] == 0) ? A0
                                : modnn((0xFF & index_of(lambda[i])) - (0xFF & discr_r) + nn));
                    }
                } else {
                    /* 2 lines below: B(x) <-- x*B(x) */
                    // memmove(&b[1],b,NROOTS*sizeof(b[0]));
                    System.arraycopy(b, 0, b, 1, nroots);
                    b[0] = A0;
                }
                // memcpy(lambda,t,(NROOTS+1)*sizeof(t[0]));
                System.arraycopy(t, 0, lambda, 0, nroots + 1);
            }
        }

        /* Convert lambda to index form and compute deg(lambda(x)) */
        deg_lambda = 0;
        for (i = 0; i < nroots + 1; i++) {
            lambda[i] = index_of(lambda[i]);
            if (lambda[i] != A0) {
                deg_lambda = i;
            }
        }
        /* Find roots of the error+erasure locator polynomial by Chien search */
        // memcpy(&reg[1],&lambda[1],NROOTS*sizeof(reg[0]));
        System.arraycopy(lambda, 1, reg, 1, nroots);
        count = 0; /* Number of roots of lambda(x) */
        for (i = 1, k = iprim - 1; i <= nn; i++, k = modnn(k + iprim)) {
            q = 1; /* lambda[0] is always 0 */
            for (j = deg_lambda; j > 0; j--) {
                if (reg[j] != A0) {
                    reg[j] = (byte) modnn((0xFF & reg[j]) + j);
                    q ^= alpha_to(reg[j]);
                }
            }
            if (q != 0)
                continue; /* Not a root */
            /* store root (index-form) and error location number */
            root[count] = i;
            loc[count] = k;
            /*
             * If we've already found max possible roots,
             * abort the search to save time
             */
            if (++count == deg_lambda)
                break;
        }
        if (deg_lambda != count) {
            /*
             * deg(lambda) unequal to number of roots => uncorrectable
             * error detected
             */
            count = -1;
            throw new ReedSolomonException("Uncorrectable");
        }
        /*
         * Compute err+eras evaluator poly omega(x) = s(x)*lambda(x) (modulo
         * x**NROOTS). in index form. Also find deg(omega).
         */
        deg_omega = deg_lambda - 1;
        for (i = 0; i <= deg_omega; i++) {
            tmp = 0;
            for (j = i; j >= 0; j--) {
                if ((s[i - j] != A0) && (lambda[j] != A0))
                    tmp ^= alpha_to(s[i - j], lambda[j]);
            }
            omega[i] = index_of(tmp);
        }

        /*
         * Compute error values in poly-form. num1 = omega(inv(X(l))), num2 =
         * inv(X(l))**(FCR-1) and den = lambda_pr(inv(X(l))) all in poly-form
         */
        for (j = count - 1; j >= 0; j--) {
            num1 = 0;
            for (i = deg_omega; i >= 0; i--) {
                if (omega[i] != A0)
                    num1 ^= alpha_to(omega[i], i * root[j]);
            }
            num2 = alpha_to(root[j] * (fcr - 1) + nn);
            den = 0;

            /* lambda[i+1] for i even is the formal derivative lambda_pr of lambda[i] */
            for (i = Integer.min(deg_lambda, nroots - 1) & ~1; i >= 0; i -= 2) {
                if (lambda[i + 1] != A0)
                    den ^= alpha_to(lambda[i + 1], i * root[j]);
            }
            /* Apply error to data */
            if (num1 != 0 && loc[j] >= pad) {
                data[loc[j] - pad] ^= alpha_to(
                        (index_of(num1) & 0xFF) + (index_of(num2) & 0xFF) + nn - (index_of(den) & 0xFF));
            }
        }

        if (eras_pos != null) {
            for (i = 0; i < count; i++)
                eras_pos[i] = loc[i];
        }
        return count;

    }

    private byte index_of(int x) {
        return _index_of[x & 0xFF];
    }

    private byte alpha_to(int x) {
        while (x >= nn) {
            x -= nn;
        }
        return _alpha_to[x];
    }

    private byte alpha_to(byte x) {
        return _alpha_to[x & 0xFF];
    }

    private byte alpha_to(byte x, int y) {
        return alpha_to(y + (x & 0xFF));
    }

    private byte alpha_to(byte x, byte y) {
        return alpha_to((x & 0xFF) + (y & 0xFF));
    }

    private int modnn(int x) {
        while (x >= nn) {
            x -= nn;
        }
        return x;
    }

    public int nroots() {
        return nroots;
    }

    public int blockSize() {
        return nn;
    }
}
