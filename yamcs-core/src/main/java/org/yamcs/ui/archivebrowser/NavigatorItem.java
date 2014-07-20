package org.yamcs.ui.archivebrowser;

import org.yamcs.ui.UiColors;

import javax.swing.*;
import java.awt.*;

/**
 * A button-like JLabel for inclusion in the side-navigator
 * Can be toggled on and off.
 */
public class NavigatorItem extends JLabel {
    private static final long serialVersionUID = 1L;
    private final Color defaultBackground;
    private boolean on = false;
    private final JComponent contentPanel; // To be opened when on-click

    public NavigatorItem(String label, JComponent contentPanel) {
        this(label, contentPanel, 0);
    }

    /**
     * @param indent multiplier for left indentation
     */
    public NavigatorItem(String label, JComponent contentPanel, int indent) {
        super(label);
        this.contentPanel = contentPanel;
        int lpad = 10 + indent*10;
        setBorder(BorderFactory.createEmptyBorder(3, lpad, 3, 10));
        defaultBackground = getBackground();
        setOpaque(true);
        setBackground(defaultBackground);
    }

    public JComponent getMatchingContentPanel() {
        return contentPanel;
    }

    public void toggleState(boolean on) {
        if(on && !this.on) {
            setBackground(UiColors.BORDER_COLOR);
        } else if (!on && this.on) {
            setDefaultBackground();
        }
        this.on = on;
    }

    public void setDefaultBackground() {
        setBackground(defaultBackground);
    }
}
