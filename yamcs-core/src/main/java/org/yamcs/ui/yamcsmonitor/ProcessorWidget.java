package org.yamcs.ui.yamcsmonitor;

import javax.swing.JComponent;
import javax.swing.text.JTextComponent;

/**
 * Component to be displayed when the matching yproc type is selected from
 * within the Yamcs Monitor
 */
public abstract class ProcessorWidget {
    
    protected String processorType;
    protected JTextComponent nameComponent;
    
    public ProcessorWidget(String channelType) {
        this.processorType = channelType;
    }

    void setSuggestedNameComponent(JTextComponent nameComponent) {
        this.nameComponent = nameComponent;
    }
    
    public abstract JComponent createConfigurationPanel();
    
    /**
     * Invoked when the channel panel is brought to the front
     */
    public abstract void activate();
    
    /**
     * Returns the spec string forwarded to createChannel()
     */
    public abstract String getSpec();
    
    /**
     * Whether this channel type needs an archive browser
     */
    public boolean requiresArchiveBrowser() {
        return false;
    }
    
    @Override
    public String toString() {
        return processorType;
    }
}
