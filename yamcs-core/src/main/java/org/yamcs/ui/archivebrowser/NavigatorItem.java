package org.yamcs.ui.archivebrowser;

import org.yamcs.ui.UiColors;

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
    private DataView dataView;

    public NavigatorItem(SideNavigator navigator, String label, DataView dataView) {
        this(navigator, label, dataView, 0);
    }

    /**
     * @param indent multiplier for left indentation
     */
    public NavigatorItem(SideNavigator navigator, String label, DataView dataView, int indent) {
        super(label);
        this.navigator = navigator;
        this.dataView = dataView;
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
            navigator.deactivateAll();
            setBackground(UiColors.BORDER_COLOR);
            navigator.openItem(this);
            on = true;
        } else if (!on && this.on) {
            setBackground(defaultBackground);
        }
        this.on = on;
    }

    public DataView getDataView() {
        return dataView;
    }
}
