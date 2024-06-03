package org.yamcs.yarch.streamsql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static boolean like(String str, String pattern) {
        pattern = pattern.toLowerCase();
        pattern = pattern.replace(".", "\\.");
        pattern = pattern.replace("?", ".");
        pattern = pattern.replace("%", ".*");
        if (str != null) { // Avoid NPE on null values
            str = str.toLowerCase();
            Matcher m = Pattern.compile(pattern, Pattern.DOTALL).matcher(str);
            return m.matches();
        } else {
            return false;
        }
    }
}
