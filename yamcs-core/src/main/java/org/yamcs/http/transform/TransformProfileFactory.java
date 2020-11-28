package org.yamcs.http.transform;

import java.util.HashMap;
import java.util.Map;

public class TransformProfileFactory {
    static Map<String, TransformProfile> xformers = new HashMap<>();
    
    static {
        YamcsWebTransform.YamcsWebProfile ywp = new YamcsWebTransform.YamcsWebProfile();
        xformers.put("yamcs-web", ywp);
        xformers.put("yw", ywp);
    }
    
    public static TransformProfile getProfile(String name) {
        TransformProfile prof = xformers.get(name);
        if(prof == null) {
            throw new IllegalArgumentException("Unknown profile "+name);
        }
        
        return prof;
    }
}
