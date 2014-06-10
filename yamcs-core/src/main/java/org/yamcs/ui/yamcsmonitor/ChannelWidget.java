package org.yamcs.ui.yamcsmonitor;

import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

/**
 * Component to be displayed when the matching channel type is selected from
 * within the Yamcs Monitor
 */
public abstract class ChannelWidget extends JPanel {
    private static final long serialVersionUID = 1L;
    
    protected String channelType;
    protected JTextComponent nameComponent;
    
    public ChannelWidget(String channelType) {
        this.channelType = channelType;
    }

    void setSuggestedNameComponent(JTextComponent nameComponent) {
        this.nameComponent = nameComponent;
    }
    
    /**
     * Invoked when the channel panel is brought to the front
     */
    public abstract void activate();
    
    /**
     * Returns the spec string forwarded to createChannel()
     */
    public abstract String getSpec();
    
    @Override
    public String toString() {
        return channelType;
    }
}
