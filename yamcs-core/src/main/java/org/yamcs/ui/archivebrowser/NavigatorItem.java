package org.yamcs.ui.archivebrowser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * A button-like JLabel for inclusion in the side-navigator
 * Can be toggled on and off.
 *
 */
public class NavigatorItem extends JLabel implements MouseListener {
    private static final long serialVersionUID = 1L;
    private final Color defaultBackground;
    private boolean on = false;
    private SideNavigator navigator;

    public NavigatorItem(SideNavigator navigator, String label) {
        this(navigator, label, 0);
    }

    /**
     * @param indent multiplier for left indentation
     */
    public NavigatorItem(SideNavigator navigator, String label, int indent) {
        super(label);
        this.navigator = navigator;
        int lpad = 10 + indent*10;
        setBorder(BorderFactory.createEmptyBorder(3, lpad, 3, 10));
        defaultBackground = getBackground();
        setOpaque(true);
        setBackground(defaultBackground);
        addMouseListener(this);
    }

    @Override public void mouseClicked(MouseEvent me) {}
    @Override public void mouseEntered(MouseEvent me) {}
    @Override public void mouseExited(MouseEvent me) {}
    @Override public void mouseReleased(MouseEvent me) {}

    @Override public void mousePressed(MouseEvent me) {
        toggleState(true);
    }

    public void toggleState(boolean on) {
        if(on && !this.on) {
            navigator.openItem(getText());
            on = true;
        }
        this.on = on;
    }

    public void setDefaultBackground() {
        setBackground(defaultBackground);
    }
}
