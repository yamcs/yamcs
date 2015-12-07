package org.yamcs.parameterarchive;

import java.util.Random;

public class FloatCompress {
    static float f  = 20;
    
    
    public static void main(String[] args) {
        Random rand = new Random();
        for(int i =0;i<100; i++) {
            float g = 20.1f;
            int xor = Float.floatToIntBits(f)^Float.floatToIntBits(g);
            System.out.println("xor: "+String.format("%x", xor));
        }
    }
}
