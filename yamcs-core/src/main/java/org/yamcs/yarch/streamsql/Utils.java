package org.yamcs.yarch.streamsql;

public class Utils {
    public static boolean like(String str, String pattern) {
        pattern = pattern.toLowerCase(); 
        pattern = pattern.replace(".", "\\."); 
        pattern = pattern.replace("?", ".");
        pattern = pattern.replace("%", ".*");
        str = str.toLowerCase();
        return str.matches(pattern);
    }
}
