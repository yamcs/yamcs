package org.yamcs.ui.archivebrowser;

import org.yamcs.ui.UiColors;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class SideNavigator extends JPanel {
    private static final long serialVersionUID = 1L;
    private ArchivePanel archivePanel;
    private JPanel itemsPanel;
    private Set<DataView> dataViews = new HashSet<DataView>();

    public SideNavigator(ArchivePanel archivePanel) {
        super(new BorderLayout());
        this.archivePanel = archivePanel;

        // Borders
        Border outsideBorder = BorderFactory.createMatteBorder(0, 0, 0, 1, UiColors.BORDER_COLOR);
        Border insideBorder = BorderFactory.createEmptyBorder(10, 0, 10, 0);
        setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));
        
        // Container for Navigation items
        itemsPanel = new JPanel(new GridBagLayout());
        
        // Put items on top and fill with emptiness
        add(itemsPanel, BorderLayout.NORTH);
        add(new JPanel(), BorderLayout.CENTER);
    }

    public void addItem(String title, int indent, DataView dataView) {
        addItem(title, indent, dataView, false);
    }

    public void addItem(String title, int indent, DataView dataView, boolean selected) {
        NavigatorItem item = new NavigatorItem(this, title, dataView, indent);
        GridBagConstraints cons = new GridBagConstraints();
        cons.fill = GridBagConstraints.HORIZONTAL;
        cons.weightx = 1;
        cons.gridx = 0;
        itemsPanel.add(item, cons);
        dataViews.add(dataView);
        archivePanel.dataViewPanel.add(dataView, title);
        if(selected) {
            item.toggleState(true);
        }
    }
    
    public void deactivateAll() {
        for(int i=0;i<itemsPanel.getComponentCount();i++) {
            Component component = itemsPanel.getComponent(i);
            if (component instanceof NavigatorItem) {
                ((NavigatorItem) component).toggleState(false);
            }
        }
    }

    public void openItem(NavigatorItem item) {
        CardLayout cl = (CardLayout) archivePanel.dataViewPanel.getLayout();
        cl.show(archivePanel.dataViewPanel, item.getText());
    }
}
