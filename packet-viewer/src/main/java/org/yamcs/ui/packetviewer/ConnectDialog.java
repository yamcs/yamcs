package org.yamcs.ui.packetviewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.client.ClientException;
import org.yamcs.client.RestClient;
import org.yamcs.client.YamcsConnectionProperties;
import org.yamcs.protobuf.YamcsInstance;

import io.netty.handler.codec.http.HttpMethod;

/**
 * Dialog for entering yamcs connection parameters.
 * 
 * @author nm
 *
 */
public class ConnectDialog extends JDialog implements ActionListener {

    private static final long serialVersionUID = 1L;
    private static final String PREF_FILENAME = "YamcsConnectionProperties"; // relative to the <home>/.yamcs directory

    private String PREF_HOST = "host";
    private String PREF_PORT = "port";
    private String PREF_USERNAME = "username";
    private String PREF_INSTANCE = "instance";

    private String host;
    private Integer port;
    private String username;
    private String password;
    private String instance;

    JTextField hostTextField;
    JTextField portTextField;
    JTextField usernameTextField;
    private JPasswordField passwordTextField;
    // JCheckBox sslCheckBox;
    private JComboBox<String> instanceCombo;
    private JComboBox<String> localMdbConfigCombo;
    boolean getInstance = false;
    boolean getStreamName = false;

    String dbConfig;
    boolean isDbConfigLocal;
    int returnValue;
    Preferences prefs;
    boolean useServerMdb;
    JTextField streamName;

    /**
     * Return value if cancel is chosen.
     */
    public static final int CANCEL_OPTION = 1;

    /**
     * Return value if approve (yes, ok) is chosen.
     */
    public static final int APPROVE_OPTION = 0;

    private void loadConnectionPreferences() throws FileNotFoundException, IOException {
        String home = System.getProperty("user.home") + "/.yamcs";
        Properties prefs = new Properties();
        try (InputStream in = new FileInputStream(new File(home, PREF_FILENAME))) {
            prefs.load(in);
        }

        host = prefs.getProperty("host");
        try {
            port = Integer.parseInt(prefs.getProperty("port"));
        } catch (NumberFormatException e) {
        }

        instance = prefs.getProperty("instance");
        if (prefs.containsKey("username")) {
            username = prefs.getProperty("username");
            password = null;
        }
    }

    private void saveConnectionPreferences() {
        String home = System.getProperty("user.home") + "/.yamcs";
        new File(home).mkdirs();
        Properties prefs = new Properties();
        if (host != null) {
            prefs.setProperty(PREF_HOST, host);
        }
        if (port != null) {
            prefs.setProperty(PREF_PORT, Integer.toString(port));
        }
        if (instance != null) {
            prefs.setProperty(PREF_INSTANCE, instance);
        }
        if (username != null) {
            prefs.setProperty(PREF_USERNAME, username);
        }

        try (OutputStream out = new FileOutputStream(home + "/" + PREF_FILENAME)) {
            prefs.store(out, null);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    ConnectDialog(JFrame parent, boolean getInstance, boolean getStreamName, boolean getDbConfig) {
        super(parent, "Connect to Yamcs", true);
        this.getInstance = getInstance;
        this.getStreamName = getStreamName;
        installActions();

        try {
            loadConnectionPreferences();
        } catch (IOException e) {
            // ignore
        }
        prefs = Preferences.userNodeForPackage(this.getClass());

        JPanel inputPanel, buttonPanel;
        JLabel lab;
        JButton button;

        // input panel

        inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints ceast = new GridBagConstraints();
        ceast.anchor = GridBagConstraints.EAST;
        GridBagConstraints cwest = new GridBagConstraints();
        cwest.weightx = 1;
        cwest.fill = GridBagConstraints.HORIZONTAL;

        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        getContentPane().add(inputPanel, BorderLayout.CENTER);

        lab = new JLabel("Host: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        ceast.gridy = 1;
        inputPanel.add(lab, ceast);
        hostTextField = new JTextField(host);
        hostTextField.setPreferredSize(new Dimension(160, hostTextField.getPreferredSize().height));
        cwest.gridy = 1;
        inputPanel.add(hostTextField, cwest);

        lab = new JLabel("Port: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        ceast.gridy = 2;
        inputPanel.add(lab, ceast);
        portTextField = new JTextField(String.valueOf(port != null ? port : 8090));
        cwest.gridy = 2;
        inputPanel.add(portTextField, cwest);
        /*
        	lab = new JLabel("Use SSL (not implemented): ");
        	lab.setHorizontalAlignment(SwingConstants.RIGHT);
        	c.gridy=3;c.gridx=0;c.anchor=GridBagConstraints.EAST;inputPanel.add(lab,c);
        	sslCheckBox = new JCheckBox(); sslCheckBox.setSelected(values.ssl);
        	c.gridy=3;c.gridx=1;c.anchor=GridBagConstraints.WEST;inputPanel.add(sslCheckBox,c);
         */

        ceast.gridy++;
        cwest.gridy++;
        lab = new JLabel("Username: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(lab, ceast);
        usernameTextField = new JTextField(username);
        usernameTextField.setPreferredSize(new Dimension(160, usernameTextField.getPreferredSize().height));
        inputPanel.add(usernameTextField, cwest);

        ceast.gridy++;
        cwest.gridy++;
        lab = new JLabel("Password: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(lab, ceast);
        passwordTextField = new JPasswordField();
        passwordTextField.setPreferredSize(new Dimension(160, passwordTextField.getPreferredSize().height));
        inputPanel.add(passwordTextField, cwest);

        if (getInstance) {
            lab = new JLabel("Instance: ");
            lab.setHorizontalAlignment(SwingConstants.RIGHT);

            ceast.gridy++;
            cwest.gridy++;

            inputPanel.add(lab, ceast);
            instanceCombo = new JComboBox<>(new String[] { instance });
            instanceCombo.setPreferredSize(hostTextField.getPreferredSize());
            instanceCombo.setEditable(true);

            inputPanel.add(instanceCombo, cwest);
            button = new JButton("Update");
            button.setActionCommand("getInstances");
            button.addActionListener(this);
            inputPanel.add(button, ceast);
        }

        if (getStreamName) {
            ceast.gridy++;
            cwest.gridy++;

            lab = new JLabel("Stream: ");

            lab.setHorizontalAlignment(SwingConstants.RIGHT);
            inputPanel.add(lab, ceast);

            String name = prefs.get("streamName", "tm_realtime");
            streamName = new JTextField(name);
            streamName.setEditable(true);

            inputPanel.add(streamName, cwest);
        }

        if (getDbConfig) {
            ceast.gridy++;
            cwest.gridy++;

            useServerMdb = prefs.getBoolean("useServerMdb", true);

            ButtonGroup bgroup = new ButtonGroup();
            JRadioButton jrb = new JRadioButton("Server MDB");
            if (useServerMdb) {
                jrb.setSelected(true);
            }
            jrb.setActionCommand("use-server-mdb");
            jrb.addActionListener(this);
            bgroup.add(jrb);
            // lab = new JLabel("Server MDB: ");
            // lab.setHorizontalAlignment(SwingConstants.RIGHT);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = ceast.gridy;
            c.anchor = GridBagConstraints.WEST;
            inputPanel.add(jrb, c);

            inputPanel.add(new Label(), cwest);
            inputPanel.add(new Label(), ceast);

            ceast.gridy++;
            cwest.gridy++;
            jrb = new JRadioButton("Local MDB: ");
            jrb.setActionCommand("use-local-mdb");
            jrb.addActionListener(this);
            if (!useServerMdb) {
                jrb.setSelected(true);
            }
            bgroup.add(jrb);
            // lab = new JLabel("Local MDB: ");
            // lab.setHorizontalAlignment(SwingConstants.RIGHT);
            c.gridy = ceast.gridy;
            inputPanel.add(jrb, c);
            String[] dbconfigs;
            try {
                dbconfigs = getLocalDbConfigs();
            } catch (ConfigurationException e) {
                JOptionPane.showMessageDialog(parent, "Cannot load local MDB configurations: " + e.getMessage(),
                        "Cannot load local MDB configs", JOptionPane.ERROR_MESSAGE);
                dbconfigs = new String[0];
            }

            if (dbconfigs.length > 0) {
                localMdbConfigCombo = new JComboBox<>(dbconfigs);
                localMdbConfigCombo.setPreferredSize(hostTextField.getPreferredSize());
                localMdbConfigCombo.setEditable(false);

                String selectedLocalMdbConfig = prefs.get("selectedLocalMdbConfig",
                        dbconfigs.length > 0 ? dbconfigs[0] : null);
                localMdbConfigCombo.setSelectedItem(selectedLocalMdbConfig);
                inputPanel.add(localMdbConfigCombo, cwest);
                if (useServerMdb) {
                    localMdbConfigCombo.setEnabled(false);
                }
            } else {
                localMdbConfigCombo = new JComboBox<>(new String[] { "unavailable" });
                localMdbConfigCombo.setPreferredSize(hostTextField.getPreferredSize());
                localMdbConfigCombo.setEnabled(false);
                localMdbConfigCombo.setSelectedItem("unavailable");
                inputPanel.add(localMdbConfigCombo, cwest);
                jrb.setSelected(false);
                jrb.setEnabled(false);
            }
        }

        // button panel

        buttonPanel = new JPanel();
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        button = new JButton("Connect");
        button.setActionCommand("connect");
        button.addActionListener(this);
        getRootPane().setDefaultButton(button);
        buttonPanel.add(button);

        button = new JButton("Cancel");
        button.setActionCommand("cancel");
        button.addActionListener(this);
        buttonPanel.add(button);

        setMinimumSize(new Dimension(350, 100));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
    }

    private void installActions() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "closeDialog");
        root.getActionMap().put("closeDialog", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                dispatchEvent(new WindowEvent(ConnectDialog.this, WindowEvent.WINDOW_CLOSING));
            }
        });
    }

    private String[] getLocalDbConfigs() throws ConfigurationException {
        if (YConfiguration.isDefined("mdb")) {
            YConfiguration conf = YConfiguration.getConfiguration("mdb");
            return conf.getKeys().toArray(new String[0]);
        } else {
            return new String[0];
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if ("connect".equals(cmd)) {
            host = hostTextField.getText();
            try {
                port = Integer.parseInt(portTextField.getText());
            } catch (NumberFormatException x) {
                JOptionPane.showMessageDialog(this, "Cannot parse port number; please enter a number", "Invalid port",
                        JOptionPane.ERROR_MESSAGE);
                return; // do not close the dialog
            }

            // values.ssl= sslCheckBox.isSelected();
            if (!usernameTextField.getText().isEmpty()) {
                username = usernameTextField.getText();
                password = new String(passwordTextField.getPassword());
            } else {
                // If not authenticating, don't use last credentials
                username = null;
                password = null;
            }
            passwordTextField.setText("");

            instance = (String) instanceCombo.getSelectedItem();
            if (instance == null) {
                JOptionPane.showMessageDialog(this, "You must specify an instance", "Missing instance",
                        JOptionPane.ERROR_MESSAGE);
                return; // do not close the dialog
            }

            // Verify the instance
            try {
                RestClient restClient = createClientForUseInDialogOnly();
                restClient.doRequest("/instances/" + instance, HttpMethod.GET).get();
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(this, "Cannot verify instance: " + e1.getMessage(),
                        e1.getMessage(), JOptionPane.WARNING_MESSAGE);
                return;
            }

            saveConnectionPreferences();
            prefs.putBoolean("useServerMdb", useServerMdb);
            if (!useServerMdb) {
                prefs.put("selectedLocalMdbConfig", (String) localMdbConfigCombo.getSelectedItem());
            }

            if (getStreamName) {
                prefs.put("streamName", streamName.getText());
            }
            returnValue = APPROVE_OPTION;
            setVisible(false);
        } else if ("cancel".equals(cmd)) {
            returnValue = CANCEL_OPTION;
            setVisible(false);
        } else if ("getInstances".equals(cmd)) {
            try {
                RestClient restClient = createClientForUseInDialogOnly();
                List<YamcsInstance> list = restClient.blockingGetYamcsInstances();
                instanceCombo.removeAllItems();
                for (YamcsInstance ai : list) {
                    if (getInstance) {
                        instanceCombo.addItem(ai.getName());
                    }
                }
            } catch (NumberFormatException x) {
                JOptionPane.showMessageDialog(this, "Enter a valid port number", x.getMessage(),
                        JOptionPane.WARNING_MESSAGE);
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(this, "Cannot retrieve instances: " + e1.getMessage(),
                        e1.getMessage(), JOptionPane.WARNING_MESSAGE);
            }
        } else if ("use-server-mdb".equals(cmd)) {
            useServerMdb = true;
            localMdbConfigCombo.setEnabled(false);
        } else if ("use-local-mdb".equals(cmd)) {
            useServerMdb = false;
            localMdbConfigCombo.setEnabled(true);
        }
    }

    private RestClient createClientForUseInDialogOnly() throws ClientException {
        String host = hostTextField.getText();
        int port = Integer.parseInt(portTextField.getText());
        YamcsConnectionProperties ycp = new YamcsConnectionProperties(host, port);
        RestClient restClient = new RestClient(ycp);
        if (!usernameTextField.getText().isEmpty()) {
            restClient.login(usernameTextField.getText(), passwordTextField.getPassword());
        }

        return new RestClient(ycp);
    }

    public YamcsConnectionProperties getConnectData() {
        YamcsConnectionProperties yprops = new YamcsConnectionProperties(host, port);
        if (username != null) {
            yprops.setCredentials(username, password.toCharArray());
        }
        return yprops;
    }

    public String getInstance() {
        return instance;
    }

    public boolean getUseServerMdb() {
        return useServerMdb;
    }

    public String getLocalMdbConfig() {
        return (String) localMdbConfigCombo.getSelectedItem();
    }

    public int showDialog() {
        // Prevent caching of last returnValue
        returnValue = CANCEL_OPTION;
        setVisible(true);
        return returnValue;
    }

    public static void main(String[] args) {
        ConnectDialog ycd = new ConnectDialog(null, true, true, true);
        ycd.showDialog();
    }

    public String getStreamName() {
        return streamName.getText();
    }

}
