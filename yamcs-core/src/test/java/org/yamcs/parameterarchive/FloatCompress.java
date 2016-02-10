package org.yamcs.parameterarchive;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.GZIPInputStream;


import org.junit.Test;
import org.yamcs.utils.FloatArray;

import static org.junit.Assert.*;

public class FloatCompress {

    public static float[] readFile(String file) throws Exception {
        InputStream s = new FileInputStream(file);
        if(file.endsWith(".gz")) {
            s = new GZIPInputStream(s);
        } 
        BufferedReader br = new BufferedReader(new InputStreamReader(s));
        String line;
        br.readLine();//skip first line
        FloatArray farray = new FloatArray();
        while((line=br.readLine())!=null) {
            float f = Float.parseFloat(line);
            farray.add(f);
        }
        br.close();
        return farray.toArray();
    }

    @Test
    public void test_5V_Current() throws Exception {
        String file = "src/test/resources/parameterarchive/5V_Current.txt.gz";
        float[] fa = readFile(file);
        ByteBuffer bb = ByteBuffer.allocate(4*fa.length);
        FloatCompressor.compress(fa, bb);
        //        int us = fa.length*4;
        //       int cs = bb.position();
        //      System.out.println("result for "+file+" size: "+cs+" fa.size: "+us+" ratio: "+(100*cs/us)+"% bitsPerValue: "+8*cs/(float)fa.length);
        bb.rewind();
        float[] fa1=FloatCompressor.decompress(bb, fa.length);
        assertArrayEquals(fa, fa1, 1e-10f);
    }


    @Test
    public void test1() throws Exception {
        float[] fa = new float[]{1.2f, 2.3f, 3.0f};
        ByteBuffer bb = ByteBuffer.allocate(24);
        FloatCompressor.compress(fa, bb);


        //        int us = fa.length*4;
        //       int cs = bb.position();
        //       System.out.println("result size: "+cs+" fa.size: "+us+" ratio: "+(100*cs/us)+"% bitsPerValue: "+8*cs/(float)fa.length);

        bb.rewind();
        float[] fa1=FloatCompressor.decompress(bb, fa.length);
        assertArrayEquals(fa, fa1, 1e-10f);
    }

    @Test
    public void test2() throws Exception {
        float[] fa = new float[300];
        Random rand = new Random();
        for(int i=0; i<9; i++) {
            fa[i]=rand.nextFloat();
        }
        fa[9]=10f;
        for(int i=10;i<fa.length;i++) {
            fa[i]=fa[i-1]+1e-7f;
        }
        ByteBuffer bb = ByteBuffer.allocate(5*fa.length);
        FloatCompressor.compress(fa, bb);


        //         int us = fa.length*4;
        //         int cs = bb.position();
        //         System.out.println("result size: "+cs+" fa.size: "+us+" ratio: "+(100*cs/us)+"% bitsPerValue: "+8*cs/(float)fa.length);

        bb.rewind();
        float[] fa1=FloatCompressor.decompress(bb, fa.length);
        assertArrayEquals(fa, fa1, 1e-10f);
    }
    @Test
    public void testRandom() throws Exception {
        float[] fa = new float[200];
        Random rand = new Random();
        for(int i=0; i<fa.length; i++) {
            fa[i]=rand.nextFloat()-0.5f;
        }
        ByteBuffer bb = ByteBuffer.allocate(5*fa.length);
        FloatCompressor.compress(fa, bb);


        //        int us = fa.length*4;
        //        int cs = bb.position();
        //        System.out.println("result size: "+cs+" fa.size: "+us+" ratio: "+(100*cs/us)+"% bitsPerValue: "+8*cs/(float)fa.length);

        bb.rewind();
        float[] fa1=FloatCompressor.decompress(bb, fa.length);
        assertArrayEquals(fa, fa1, 1e-10f);
    }

    @Test
    public void test5() throws Exception {
        float[] fa = new float[]{20.403667f, 20.403667f, -59.953f, -59.953f, -59.953f};
        ByteBuffer bb = ByteBuffer.allocate(fa.length*4);
        FloatCompressor.compress(fa, bb);


        //         int us = fa.length*4;
        //         int cs = bb.position();
        //         System.out.println("result size: "+cs+" fa.size: "+us+" ratio: "+(100*cs/us)+"% bitsPerValue: "+8*cs/(float)fa.length);

        bb.rewind();
        float[] fa1=FloatCompressor.decompress(bb, fa.length);
        assertArrayEquals(fa, fa1, 1e-10f);
    }

}
