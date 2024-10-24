package org.yamcs.rs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ReedSolomonTest {
    int n = 10;
    Random rand = new Random();

    public static Stream<RsParams> data() {
        return Stream.of(
                new RsParams(4, 4, 6, 1, 0x13, 5),
                new RsParams(32, 8, 112, 11, 0x187, 0));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test1(RsParams rsp) throws ReedSolomonException {
        ReedSolomon rs = new ReedSolomon(rsp.nroots, rsp.symsize, rsp.fcr, rsp.prim, rsp.gfpoly, rsp.pad);

        byte[] parity = new byte[rsp.nroots];
        int nn = (1 << rsp.symsize) - 1;
        int ds = nn - rsp.nroots - rsp.pad;
        System.out.println("ds: " + ds);
        for (int i = 0; i < n; i++) {
            byte[] orig_data = new byte[ds];
            fillRandom(orig_data, nn);

            byte[] data = Arrays.copyOf(orig_data, orig_data.length);
            rs.encode(data, parity);
            assertArrayEquals(orig_data, data);

            byte[] ddata = Arrays.copyOf(data, ds + rsp.nroots);
            System.arraycopy(parity, 0, ddata, ds, rsp.nroots);
            ddata[rand.nextInt(ds)] = 0;
            rs.decode(ddata, null);
            for (int j = 0; j < ds; j++) {
                assertEquals(orig_data[j], ddata[j]);
            }
        }

    }

    void fillRandom(byte[] data, int max) {
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) rand.nextInt(max);
        }
    }

    static class RsParams {
        int nroots;
        int symsize;
        int fcr;
        int prim;
        int gfpoly;
        int pad;

        public RsParams(int nroots, int symsize, int fcr, int prim, int gfpoly, int pad) {
            super();
            this.nroots = nroots;
            this.symsize = symsize;
            this.fcr = fcr;
            this.prim = prim;
            this.gfpoly = gfpoly;
            this.pad = pad;
        }

    }
}
