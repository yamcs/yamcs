package org.yamcs.ui.archivebrowser;

import org.yamcs.TimeInterval;
import org.yamcs.ui.datepicker.DatePicker;
import org.yamcs.utils.TimeEncoding;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.prefs.Preferences;


/*
 * Also used by CcsdsCompletessGui
 */
public class PrefsToolbar extends JPanel {
    private static final long serialVersionUID = 1L;
    private JComboBox instances;
    DatePicker datePicker;
    Preferences prefs;
    public JButton reloadButton;

    public PrefsToolbar() {
        super(new FlowLayout(FlowLayout.LEFT));
        prefs = Preferences.userNodeForPackage(ArchivePanel.class);

        JLabel label;

        label = new JLabel("Archive Instance:");
        add(label);
        
        instances = new JComboBox();
        instances.setEditable(false);
        instances.setPreferredSize(new Dimension(100, instances.getPreferredSize().height));
        instances.setMaximumSize(new Dimension(300, instances.getPreferredSize().height));
        add(instances);

        add(new JSeparator(SwingConstants.VERTICAL));
        label = new JLabel("Reload Range:");
        add(label);
        datePicker=new DatePicker();
        datePicker.setMaximumSize(datePicker.getPreferredSize());
        
        add(datePicker);
        
        add(new JSeparator(SwingConstants.VERTICAL));
        
        reloadButton = new JButton("Reload");
        reloadButton.setActionCommand("reload");
        reloadButton.setEnabled(false);
        add(reloadButton);
        
        loadFromPrefs();
    }

    public void savePreferences() {
        prefs.put("instance", getInstance());
        prefs.putLong("rangeStart", getStartTimestamp());
        prefs.putLong("rangeEnd", getEndTimestamp());
    }
    
    public TimeInterval getInterval() {
        return datePicker.getInterval();
    }
    public long getEndTimestamp() {
        return datePicker.getEndTimestamp();
    }

    public long getStartTimestamp() {
        return datePicker.getStartTimestamp();
    }

    public void setInstances(List<String> archiveInstances) {
        instances.removeAllItems();
        for (String item:archiveInstances) {
            instances.addItem(item);
        }
        
        String preferred=prefs.get("instance",archiveInstances.get(0));
        if(archiveInstances.contains(preferred))
            instances.setSelectedItem(preferred);
    }

    public void setVisiblePackets(Object[] packets) {
        StringBuffer strbuf = new StringBuffer();
        for (Object s:packets) {
            strbuf.append(" ");
            strbuf.append(s.toString());
        }
        prefs.put("packets", strbuf.toString().trim());
    }

    void loadFromPrefs() {
        datePicker.setStartTimestamp(prefs.getLong("rangeStart", TimeEncoding.currentInstant()-30*24*3600));
        datePicker.setEndTimestamp(prefs.getLong("rangeEnd", TimeEncoding.currentInstant()));
    }

    public String getInstance() {
        return (String) instances.getSelectedItem();
    }

    public String[] getVisiblePackets() {
        String[] s = prefs.get("packets", "").split(" ");
        return s[0].equals("") ? new String[0] : s;
    }
}
