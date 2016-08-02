package org.yamcs.ui.archivebrowser;

import org.yamcs.protobuf.Yamcs;
import org.yamcs.ui.YamcsConnector;

import javax.swing.*;

import java.util.List;

/**
 * Defines a ContentPanel to be included in the SideNavigator.
 * Optionally includes an inset JComponent as well for display in the lower region of the side navigator.
 * Provides access to an API similar to {@link org.yamcs.ui.archivebrowser.ArchiveIndexListener} but
 * with a finished event that only triggers when *both* tags and regular records are received.
 */
public abstract class NavigatorItem {
    protected YamcsConnector yconnector;
    protected ArchiveIndexReceiver indexReceiver;

    protected JComponent contentPanel;
    protected JComponent navigatorInset;

    public NavigatorItem(YamcsConnector yconnector, ArchiveIndexReceiver indexReceiver) {
        this.yconnector = yconnector;
        this.indexReceiver = indexReceiver;
    }

    /**
     * @return the label name of this item in the SideNavigator
     */
    public abstract String getLabelName();

    /**
     * @return a multiplier indicating the indent level of
     * the label name in the SideNavigator (defaults to 0)
     */
    public int getIndent() {
        return 0;
    }

    /**
     * Build the GUI component for the content panel (called only once)
     * @return the content pane to be visualized when the item is selected from the SideNavigator
     */
    public abstract JComponent createContentPanel();

    /**
     * Build the GUI component for the navigator inset (called only once)
     * @return optional panel to be put in the lower part of the SideNavigator
     * when this NavigatorItem is active
     */
    public JComponent createNavigatorInset() {
        return null;
    }

    /**
     * Called when this item is opened
     * Defaults to NOP.
     */
    public void onOpen() {
    }

    /**
     * Called when this item is closed
     * Defaults to NOP.
     */
    public void onClose() {
    }

    public void windowResized() {}

    public void startReloading() {}

    public void receiveArchiveRecords(Yamcs.IndexResult ir) {}
    public void receiveArchiveRecordsError(String errorMessage) {}
    public void receiveTags(List<Yamcs.ArchiveTag> tagList) {}

    /**
     * Triggered when *both* tags and archive records have been
     * received.
     */
    public void archiveLoadFinished() {}

    public void tagAdded(Yamcs.ArchiveTag ntag) {}
    public void tagRemoved(Yamcs.ArchiveTag rtag) {}
    public void tagChanged(Yamcs.ArchiveTag oldTag, Yamcs.ArchiveTag newTag) {}

    JComponent getContentPanel() {
        if(contentPanel==null) {
            contentPanel = createContentPanel();
        }
        return contentPanel;
    }

    JComponent getNavigatorInset() {
        if(navigatorInset==null) {
            navigatorInset = createNavigatorInset();
        }
        return navigatorInset;
    }
}
