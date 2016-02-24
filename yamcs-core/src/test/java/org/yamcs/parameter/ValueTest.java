package org.yamcs.parameter;

import org.junit.Ignore;
import org.junit.Test;
import org.yamcs.protobuf.Yamcs.Value.Type;

public class ValueTest {
    int n = 10000000;
    int m = 50;
    Value[] newValues;
    org.yamcs.protobuf.Yamcs.Value[] oldValues;
    
    public void testNewV() {
        long t0 = System.currentTimeMillis();
        
        Value[] values = new Value[n];
        newValues = values;
        for(int i =0;i<n;i++) {
            values[i] = new UInt32Value(i);
        }

        long s =0;
        for(int j=0; j<m; j++) {
            for(int i =0;i<n;i++) {
                if(values[i].getType()==Type.UINT32) {
                    s+=values[i].getUint32Value();
                }
            }
        }
        long t1= System.currentTimeMillis();
        System.out.println("new values: s: "+s+" in "+(t1-t0)+" millisec");

    }


    public void testOldV() {
        long t0 = System.currentTimeMillis();
        org.yamcs.protobuf.Yamcs.Value[] values = new org.yamcs.protobuf.Yamcs.Value[n];
        oldValues = values;
        for(int i =0; i<n; i++) {
            values[i] = org.yamcs.protobuf.Yamcs.Value.newBuilder().setUint32Value(i).setType(Type.UINT32).build();
        }

        long s =0;
        for(int j=0; j<m; j++) {
            for(int i =0;i<n;i++) {
                if(values[i].getType()==Type.UINT32) {
                    s+=values[i].getUint32Value();
                }
            }
        }
        long t1= System.currentTimeMillis();
        System.out.println("old values s: "+s+" in "+(t1-t0)+" millisec");

    }
    
    @Ignore
    @Test
    public void test() throws Exception {
        testOldV();
        testNewV();
    }
}
