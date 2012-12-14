package org.yamcs.xtce.service;

import java.util.Arrays;
import java.util.Collection;

public class MdbServiceConfig {

    private String mdbDirectory = "/home/mu/Projects/mdb";
    
    private Class<?> theClass;
    
    public MdbServiceConfig() {
        theClass = String.class;
        
    }
    
    public static void main(String[] args) {
        
        Class theClass = String.class;
        
        Object obj = "Some string";
        
        String str = (String) theClass.cast(obj);
        System.out.println(str);

        
        String[] aa = { "a", "c", "b", "x", "q"};
        System.out.println(Arrays.asList(aa));
    }
}
