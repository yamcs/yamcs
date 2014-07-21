package org.yamcs.ui.archivebrowser;

import org.yamcs.ui.UiColors;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * For switching between NavigatorItems.
 */
public class SideNavigator extends JPanel implements MouseListener {
    private static final long serialVersionUID = 1L;
    private ArchivePanel archivePanel;
    private JPanel itemLabelsPanel;

    public SideNavigator(ArchivePanel archivePanel) {
        super(new BorderLayout());
        this.archivePanel = archivePanel;

        // Borders
        Border outsideBorder = BorderFactory.createMatteBorder(0, 0, 0, 1, UiColors.BORDER_COLOR);
        Border insideBorder = BorderFactory.createEmptyBorder(10, 0, 10, 0);
        setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));
        
        // Container for Navigation items
        itemLabelsPanel = new JPanel(new GridBagLayout());

        // Put items on top and fill with emptiness
        add(itemLabelsPanel, BorderLayout.NORTH);
        add(new JPanel(), BorderLayout.CENTER);
    }

    public void addItem(String name, int indent, NavigatorItem navigatorItem) {
        NavigatorItemLabel item = new NavigatorItemLabel(name, navigatorItem, indent);
        item.addMouseListener(this);
        GridBagConstraints cons = new GridBagConstraints();
        cons.fill = GridBagConstraints.HORIZONTAL;
        cons.weightx = 1;
        cons.gridx = 0;
        itemLabelsPanel.add(item, cons);
    }

    public void updateActiveItem(NavigatorItem targetItem) {
        for(int i=0;i< itemLabelsPanel.getComponentCount();i++) {
            Component component = itemLabelsPanel.getComponent(i);
            if (component instanceof NavigatorItemLabel) {
                NavigatorItemLabel label = ((NavigatorItemLabel) component);
                label.toggleState(label.getText().equals(targetItem.getLabelName()));
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if(e.getSource() instanceof NavigatorItemLabel) {
            NavigatorItemLabel lbl = (NavigatorItemLabel) e.getSource();
            archivePanel.fireIntentionToSwitchActiveItem(lbl.getNavigatorItem());
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    /**
     * A button-like JLabel for inclusion in the side-navigator
     * Can be toggled on and off.
     */
    private static class NavigatorItemLabel extends JLabel {
        private static final long serialVersionUID = 1L;
        private final Color defaultBackground;
        private boolean on = false;
        private final NavigatorItem navigatorItem; // To be opened when on-click

        /**
         * @param indent multiplier for left indentation
         */
        public NavigatorItemLabel(String label, NavigatorItem navigatorItem, int indent) {
            super(label);
            this.navigatorItem = navigatorItem;
            int lpad = 10 + indent * 10;
            setBorder(BorderFactory.createEmptyBorder(3, lpad, 3, 10));
            defaultBackground = getBackground();
            setOpaque(true);
            setBackground(defaultBackground);
        }

        public NavigatorItem getNavigatorItem() {
            return navigatorItem;
        }

        public void toggleState(boolean on) {
            if (on && !this.on) {
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
}
