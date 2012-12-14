package org.yamcs.ui.archivebrowser;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ColorUtils {
    static Map<String, String> colors=new HashMap<String,String>();
    
    static {
        colors.put("black","white");
        colors.put("blue","white");
        colors.put("cyan","black");
        colors.put("gray","white");
        colors.put("green","black");
        colors.put("magenta","black");
        colors.put("orange","black");
        colors.put("pink","black");
        colors.put("red","white");
        colors.put("yellow","black");
    }
    static public Color getColor(String colorName) {
        try {
            Field field = Class.forName("java.awt.Color").getField(colorName);
            return (Color) field.get(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    static public Color getOpposite(String colorName) {
        return getColor(colors.get(colorName));
    }
}
