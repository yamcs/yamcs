package org.yamcs.ui.packetviewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.ui.PrefsObject;

/**
 * Selects a filename and a XTCE db config version to be used in the standalone packet viewer 
 * @author nm
 *
 */

public class FileAndDbChooser extends JDialog implements ActionListener {
    JComboBox fileNameCombo;
    JComboBox dbConfigCombo;
    Preferences prefs;
    JFileChooser fileChooser;
    Vector<String> recentFiles;
    DefaultComboBoxModel fileNameComboModel;
    int returnValue;

    /**
     * Return value if cancel is chosen.
     */
    public static final int CANCEL_OPTION = 1;
    /**
     * Return value if approve (yes, ok) is chosen.
     */
    public static final int APPROVE_OPTION = 0;

    public FileAndDbChooser() throws ConfigurationException {
        JPanel inputPanel, buttonPanel;
        JLabel lab;
        JButton button;
        prefs=Preferences.userNodeForPackage(this.getClass());
        // input panel

        inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints ceast = new GridBagConstraints();
        ceast.anchor=GridBagConstraints.EAST;
        GridBagConstraints cwest = new GridBagConstraints();
        cwest.weightx=1; cwest.fill=GridBagConstraints.HORIZONTAL;

        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        getContentPane().add(inputPanel, BorderLayout.CENTER);

        lab = new JLabel("Filename: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        ceast.gridy=0;      inputPanel.add(lab,ceast);
        recentFiles=(Vector<String>)PrefsObject.getObject(prefs, "RecentFiles");
        if(recentFiles==null) recentFiles=new Vector<String>();

        fileNameComboModel=new DefaultComboBoxModel(recentFiles);
        fileNameCombo = new JComboBox(fileNameComboModel);

        fileNameCombo.setEditable(true);
        cwest.gridy=0;inputPanel.add(fileNameCombo, cwest);
        button = new JButton("...");
        button.setActionCommand("selectFile");
        button.addActionListener(this);
        ceast.gridy=0;inputPanel.add(button,ceast);

        YConfiguration c=YConfiguration.getConfiguration("mdb");
        String[] dbconfigs=c.getKeys().toArray(new String[0]);
        lab = new JLabel("DB Config: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        ceast.gridy=1; inputPanel.add(lab,ceast);

        dbConfigCombo = new JComboBox(dbconfigs);
        //dbConfigCombo.setPreferredSize(hostTextField.getPreferredSize());
        dbConfigCombo.setEditable(true);
        cwest.gridy=1; inputPanel.add(dbConfigCombo,cwest);


        // button panel

        buttonPanel = new JPanel();
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        button = new JButton("OK");
        button.setActionCommand("ok");
        button.addActionListener(this);
        getRootPane().setDefaultButton(button);
        buttonPanel.add(button);

        button = new JButton("Cancel");
        button.setActionCommand("cancel");
        button.addActionListener(this);
        buttonPanel.add(button);

        setMinimumSize(new Dimension(350, 150));
        //setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        String oldDir=prefs.get("LastUsedDirectory", null);
        fileChooser=new JFileChooser(oldDir);
        setTitle("Open File");
        setModal(true);
    }

    public int showDialog(Component parent) {
        returnValue=CANCEL_OPTION;
        setLocationRelativeTo(parent);
        setVisible(true);
        return returnValue;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd=e.getActionCommand();
        if("selectFile".equals(cmd)) {
            int r=fileChooser.showOpenDialog(this);
            if(r==JFileChooser.APPROVE_OPTION) {
                File selectedFile=fileChooser.getSelectedFile();
                fileNameComboModel.insertElementAt(selectedFile.getAbsolutePath(), 0);
                fileNameCombo.setSelectedIndex(0);
                PrefsObject.putObject(prefs,"RecentFiles", recentFiles);
                prefs.put("LastUsedDirectory", fileChooser.getSelectedFile().getParent());
            }
        } else if("ok".equals(cmd)) {
            returnValue=APPROVE_OPTION;
            setVisible(false);
        } else if("cancel".equals(cmd)) {
            returnValue=CANCEL_OPTION;
            setVisible(false);
        }
    }

    String getSelectedFile() {
        return (String) fileNameComboModel.getSelectedItem();
    }

    String getSelectedDbConfig() {
        return (String) dbConfigCombo.getSelectedItem();
    }

    static public void main(String[] args) throws ConfigurationException {
        YConfiguration.setup();
        FileAndDbChooser fc=new FileAndDbChooser();
        int r=fc.showDialog(null);
        System.out.println("after the fact, r="+r+" file: "+fc.getSelectedFile()+" dbconfig: "+fc.getSelectedDbConfig());
    }

}