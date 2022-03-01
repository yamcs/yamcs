package org.yamcs.ui.packetviewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Select a filename and a XTCE db config version to be used in the standalone packet viewer
 */
public class OpenFileDialog extends JDialog implements ActionListener {
    private static final long serialVersionUID = 1L;

    /**
     * Return value if cancel is chosen
     */
    public static final int CANCEL_OPTION = 1;

    /**
     * Return value if approve (yes, ok) is chosen
     */
    public static final int APPROVE_OPTION = 0;

    private Map<String, FileFormat> fileFormats;
    private JComboBox<String> fileFormatCombo;
    private JComboBox<String> dbConfigCombo;
    private JFileChooser fileChooser;
    private Preferences prefs;
    private int returnValue;

    public OpenFileDialog(Map<String, FileFormat> fileFormats) throws ConfigurationException {
        this.fileFormats = fileFormats;
        String[] dbconfigs = new String[0];
        if (YConfiguration.isDefined("mdb")) {
            YConfiguration c = YConfiguration.getConfiguration("mdb");
            dbconfigs = c.getKeys().toArray(new String[0]);
        }
        Arrays.sort(dbconfigs);
        prefs = Preferences.userNodeForPackage(PacketViewer.class);

        String[] fileFormatNames = fileFormats.values().stream()
                .map(FileFormat::getName)
                .collect(Collectors.toList())
                .toArray(new String[0]);

        JPanel fields = new JPanel(new GridLayout(0, 1));
        fileFormatCombo = new JComboBox<>(fileFormatNames);
        fileFormatCombo.setSelectedIndex(0);
        fields.add(fileFormatCombo);

        dbConfigCombo = new JComboBox<>(dbconfigs);
        dbConfigCombo.setSelectedItem(prefs.get("LastUsedDbConfig", null));
        fields.add(dbConfigCombo);

        JPanel xtcePanel = new JPanel();
        xtcePanel.setLayout(new BoxLayout(xtcePanel, BoxLayout.Y_AXIS));

        JPanel opts = new JPanel(new BorderLayout());
        opts.setBorder(BorderFactory.createEmptyBorder(12, 12, 11, 11));

        JPanel labels = new JPanel(new GridLayout(0, 1));

        JLabel fileFormatLbl = new JLabel("File Format: ");
        fileFormatLbl.setDisplayedMnemonic(KeyEvent.VK_F);
        fileFormatLbl.setLabelFor(fileFormatCombo);
        labels.add(fileFormatLbl);

        JLabel xtceDbLbl = new JLabel("XTCE DB: ");
        xtceDbLbl.setDisplayedMnemonic(KeyEvent.VK_D);
        xtceDbLbl.setLabelFor(dbConfigCombo);
        labels.add(xtceDbLbl);

        opts.add(labels, BorderLayout.WEST);
        opts.add(fields, BorderLayout.CENTER);

        xtcePanel.add(opts);
        xtcePanel.add(new JSeparator());

        getContentPane().add(xtcePanel, BorderLayout.NORTH);

        String oldDir = prefs.get("LastUsedDirectory", null);
        fileChooser = new JFileChooser(oldDir);
        fileChooser.setDialogTitle("Open File");
        fileChooser.addActionListener(this);

        getContentPane().add(fileChooser, BorderLayout.CENTER);

        setMinimumSize(new Dimension(500, 400));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setTitle("Open File");
        setModal(true);
        installActions();
    }

    private void installActions() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "closeDialog");
        root.getActionMap().put("closeDialog", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                dispatchEvent(new WindowEvent(OpenFileDialog.this, WindowEvent.WINDOW_CLOSING));
            }
        });
    }

    public int showDialog(Component parent) {
        returnValue = CANCEL_OPTION;
        setLocationRelativeTo(parent);
        setVisible(true);
        return returnValue;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals(JFileChooser.APPROVE_SELECTION)) {
            prefs.put("LastUsedDirectory", fileChooser.getSelectedFile().getParent());
            prefs.put("LastUsedDbConfig", "" + dbConfigCombo.getSelectedItem());
            returnValue = APPROVE_OPTION;
            setVisible(false);
        } else if (cmd.equals(JFileChooser.CANCEL_SELECTION)) {
            returnValue = CANCEL_OPTION;
            setVisible(false);
        }
    }

    public File getSelectedFile() {
        return fileChooser.getSelectedFile();
    }

    public FileFormat getSelectedFileFormat() {
        return fileFormats.get((String) fileFormatCombo.getSelectedItem());
    }

    public String getSelectedDbConfig() {
        return (String) dbConfigCombo.getSelectedItem();
    }
}
