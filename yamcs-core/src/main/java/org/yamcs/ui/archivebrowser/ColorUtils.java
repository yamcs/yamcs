package org.yamcs.ui.archivebrowser;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ColorUtils {
    // The 'original' set of tag colors. Swing clients limit to this
    static final Map<String, Color> colors = new HashMap<>();
    // Cache Colors for hex names (non-swing clients might support any color)
    private static final Map<String, Color> hexColors = new HashMap<>();
    // For all of the above, keep track of a calculated opposing color (white/black depending on brightness)
    private static final Map<String, Color> opposingColors = new HashMap<>();
    
    static {
        colors.put("black", Color.BLACK);
        opposingColors.put("black", Color.WHITE);
        colors.put("blue", Color.BLUE);
        opposingColors.put("blue", Color.WHITE);
        colors.put("cyan", Color.CYAN);
        opposingColors.put("cyan", Color.BLACK);
        colors.put("gray", Color.GRAY);
        opposingColors.put("gray", Color.WHITE);
        colors.put("green", Color.GREEN);
        opposingColors.put("green", Color.BLACK);
        colors.put("magenta", Color.MAGENTA);
        opposingColors.put("magenta", Color.BLACK);
        colors.put("orange", Color.ORANGE);
        opposingColors.put("orange", Color.BLACK);
        colors.put("pink", Color.PINK);
        opposingColors.put("pink", Color.BLACK);
        colors.put("red", Color.RED);
        opposingColors.put("red", Color.WHITE);
        colors.put("yellow", Color.YELLOW);
        opposingColors.put("yellow", Color.BLACK);
    }
    static public Color getColor(String colorName) {
        Color color = colors.get(colorName);
        if (color == null)
            color = hexColors.get(colorName);
        if (color == null && colorName.startsWith("#")) {
            int r = Integer.valueOf(colorName.substring(1, 3), 16);
            int g = Integer.valueOf(colorName.substring(3, 5), 16);
            int b = Integer.valueOf(colorName.substring(5, 7), 16);
            color = new Color(r, g, b);
            hexColors.put(colorName, color);

            int brightness = (int) Math.sqrt(.241 * r * r + .691 * g * g + .068 * b * b);
            opposingColors.put(colorName, (brightness < 130) ? Color.WHITE : Color.BLACK);
        }
        return color;
    }
    
    static public Color getOpposite(String colorName) {
        return opposingColors.get(colorName);
    }
}
