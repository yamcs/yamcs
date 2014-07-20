package org.yamcs.ui.archivebrowser;

import org.yamcs.ui.UiColors;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * For navigating between DataViewers. Also includes a date range
 * for showing the selected interval, and a field that follows the
 * mouse position (similar to TT, but without day of the year
 * formatting.)
 */
public class SideNavigator extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color DEFAULT_LABEL_COLOR = Color.GRAY;
    private ArchivePanel archivePanel;
    private JPanel itemsPanel;

    private JFormattedTextField mouseLocator;
    private JFormattedTextField selectionStart;
    private JFormattedTextField selectionStop;

    private JLabel mouseLocatorLabel;
    private JLabel dottedSquare;

    public SideNavigator(ArchivePanel archivePanel) {
        super(new BorderLayout());
        this.archivePanel = archivePanel;

        // Borders
        Border outsideBorder = BorderFactory.createMatteBorder(0, 0, 0, 1, UiColors.BORDER_COLOR);
        Border insideBorder = BorderFactory.createEmptyBorder(10, 0, 10, 0);
        setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));
        
        // Container for Navigation items
        itemsPanel = new JPanel(new GridBagLayout());

        // Various dates based on what's happening in DataViewers
        Box selectionRangePanel = createSelectionRangePanel();
        
        // Put items on top and fill with emptiness
        add(itemsPanel, BorderLayout.NORTH);
        add(new JPanel(), BorderLayout.CENTER);
        add(selectionRangePanel, BorderLayout.SOUTH);
    }

    private Box createSelectionRangePanel() {
        Box vbox = Box.createVerticalBox();
        Border outsideBorder = BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_COLOR);
        Border insideBorder = BorderFactory.createEmptyBorder(0, 10, 0, 10);
        vbox.setBorder(BorderFactory.createCompoundBorder(outsideBorder, insideBorder));

        InstantFormat iformat=new InstantFormat();

        Box mouseBox = Box.createHorizontalBox();
        mouseLocatorLabel = new JLabel("\u27a5");
        mouseLocatorLabel.setForeground(DEFAULT_LABEL_COLOR);
        mouseLocatorLabel.setToolTipText("Mouse position");
        mouseBox.add(mouseLocatorLabel);
        mouseLocator = new JFormattedTextField(iformat);
        mouseLocator.setHorizontalAlignment(JTextField.CENTER);
        mouseLocator.setEditable(false);
        mouseLocator.setMaximumSize(new Dimension(150, mouseLocator.getPreferredSize().height));
        mouseLocator.setMinimumSize(mouseLocator.getMaximumSize());
        mouseLocator.setPreferredSize(mouseLocator.getMaximumSize());
        mouseLocator.setFont(mouseLocator.getFont().deriveFont(mouseLocator.getFont().getSize2D() - 3));
        mouseBox.add(mouseLocator);

        Box selectionStartBox = Box.createHorizontalBox();
        dottedSquare = new JLabel("\u2b1a");
        dottedSquare.setForeground(DEFAULT_LABEL_COLOR);
        dottedSquare.setToolTipText("Selected date range");
        selectionStartBox.add(dottedSquare);
        selectionStart = new JFormattedTextField(iformat);
        selectionStart.setHorizontalAlignment(JTextField.CENTER);
        selectionStart.setEditable(false);
        selectionStart.setMaximumSize(new Dimension(150, selectionStart.getPreferredSize().height));
        selectionStart.setMinimumSize(selectionStart.getMaximumSize());
        selectionStart.setPreferredSize(selectionStart.getMaximumSize());
        selectionStart.setFont(selectionStart.getFont().deriveFont(selectionStart.getFont().getSize2D() - 3));
        selectionStartBox.add(selectionStart);
        selectionStart.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                // TODO dataView.updateSelection((Long) selectionStart.getValue(), (Long) selectionStop.getValue());
            }
        });

        Box selectionStopBox = Box.createHorizontalBox();
        selectionStop = new JFormattedTextField(iformat);
        selectionStop.setHorizontalAlignment(JTextField.CENTER);
        selectionStop.setEditable(false);
        selectionStop.setMaximumSize(new Dimension(150, selectionStop.getPreferredSize().height));
        selectionStop.setMinimumSize(selectionStop.getMaximumSize());
        selectionStop.setPreferredSize(selectionStop.getMaximumSize());
        selectionStop.setFont(selectionStop.getFont().deriveFont(selectionStop.getFont().getSize2D() - 3));
        selectionStopBox.add(selectionStop);
        selectionStop.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                // TODO dataView.updateSelection((Long) selectionStart.getValue(), (Long) selectionStop.getValue());
            }
        });

        mouseBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
        vbox.add(mouseBox);
        selectionStartBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
        vbox.add(selectionStartBox);
        selectionStopBox.setAlignmentX(Component.RIGHT_ALIGNMENT);
        vbox.add(selectionStopBox);

        return vbox;
    }

    public void addItem(String title, int indent, DataViewer dataViewer) {
        addItem(title, indent, dataViewer, false);
    }

    public void addItem(String title, int indent, DataViewer dataViewer, boolean selected) {
        NavigatorItem item = new NavigatorItem(this, title, indent);
        GridBagConstraints cons = new GridBagConstraints();
        cons.fill = GridBagConstraints.HORIZONTAL;
        cons.weightx = 1;
        cons.gridx = 0;
        itemsPanel.add(item, cons);
        archivePanel.dataViewerPanel.add(dataViewer, title);
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

    public void openItem(String name) {
        deactivateAll();
        for(int i=0;i<itemsPanel.getComponentCount();i++) {
            Component component = itemsPanel.getComponent(i);
            if (component instanceof NavigatorItem) {
                NavigatorItem item = ((NavigatorItem) component);
                if (item.getText().equals(name)) {
                    item.setBackground(UiColors.BORDER_COLOR);
                    archivePanel.openItem(name);
                } else {
                    item.setDefaultBackground();
                }
            }
        }
    }

    public void signalMousePosition(long instant) {
        mouseLocatorLabel.setForeground((instant== TimeEncoding.INVALID_INSTANT) ? DEFAULT_LABEL_COLOR : Color.BLACK);
        mouseLocator.setValue(instant);
    }

    public void signalSelectionChange(Selection selection) {
        if (selection != null) {
            signalSelectionStartChange(selection.getStartInstant());
            signalSelectionStopChange(selection.getStopInstant());
            if(selection.getStartInstant()!=TimeEncoding.INVALID_INSTANT
                    || selection.getStopInstant()!=TimeEncoding.INVALID_INSTANT) {
                dottedSquare.setForeground(Color.BLUE);
            } else {
                dottedSquare.setForeground(DEFAULT_LABEL_COLOR);
            }
        } else {
            signalSelectionStartChange(TimeEncoding.INVALID_INSTANT);
            signalSelectionStopChange(TimeEncoding.INVALID_INSTANT);
            dottedSquare.setForeground(DEFAULT_LABEL_COLOR);
        }
    }

    public void signalSelectionStartChange(long startInstant) {
        selectionStart.setEditable((startInstant!=TimeEncoding.INVALID_INSTANT));
        selectionStart.setValue(startInstant);
    }

    public void signalSelectionStopChange(long stopInstant) {
        selectionStop.setEditable((stopInstant!=TimeEncoding.INVALID_INSTANT));
        selectionStop.setValue(stopInstant);
    }
}
