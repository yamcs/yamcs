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

import javax.naming.ConfigurationException;
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

import org.yamcs.YConfiguration;
import org.yamcs.client.BasicAuthCredentials;
import org.yamcs.client.ClientException;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.base.ServerURL;
import org.yamcs.protobuf.YamcsInstance;

/**
 * Dialog for entering yamcs connection parameters.
 * 
 * @author nm
 *
 */
public class ConnectDialog extends JDialog implements ActionListener {

    private static final long serialVersionUID = 1L;
    private static final String PREF_FILENAME = "YamcsConnectionProperties"; // relative to the <home>/.yamcs directory

    private String PREF_AUTH_TYPE = "authType";
    private String PREF_HOST = "host";
    private String PREF_PORT = "port";
    private String PREF_TLS = "tls";
    private String PREF_CONTEXT_PATH = "contextPath";
    private String PREF_USERNAME = "username";
    private String PREF_INSTANCE = "instance";

    private String serverUrl;
    private AuthType authType = AuthType.STANDARD;
    private String username;
    private String password;
    private String instance;

    private JTextField serverUrlTextField;

    private JComboBox<AuthType> authTypeCombo;
    private JLabel usernameLabel;
    private JTextField usernameTextField;
    private JLabel passwordLabel;
    private JPasswordField passwordTextField;

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

        String authTypeName = prefs.getProperty(PREF_AUTH_TYPE);
        if (authTypeName != null) {
            authType = AuthType.valueOf(authTypeName);
        } else {
            authType = AuthType.STANDARD;
        }

        String host = prefs.getProperty(PREF_HOST);
        Integer port = null;
        try {
            port = Integer.parseInt(prefs.getProperty(PREF_PORT));
        } catch (NumberFormatException e) {
        }

        String contextPath = prefs.getProperty(PREF_CONTEXT_PATH, null);
        boolean tls = Boolean.parseBoolean(prefs.getProperty("tls", "false"));

        if (host != null) {
            String serverUrl = tls ? "https://" : "http://";
            serverUrl += host;
            // 8090 was the default if the port text field was empty
            // (before the transition of url components to url)
            serverUrl += ":" + (port == null ? 8090 : port);
            if (contextPath != null && !contextPath.isBlank()) {
                serverUrl += "/" + contextPath;
            }
            try {
                this.serverUrl = ServerURL.parse(serverUrl).toString();
            } catch (IllegalArgumentException e) {
                // Ignore, just ignore prefs
            }
        }

        instance = prefs.getProperty(PREF_INSTANCE);
        if (prefs.containsKey(PREF_USERNAME)) {
            username = prefs.getProperty(PREF_USERNAME);
            password = null;
        }
    }

    private void saveConnectionPreferences() {
        String home = System.getProperty("user.home") + "/.yamcs";
        new File(home).mkdirs();
        Properties prefs = new Properties();
        if (this.serverUrl != null) {
            ServerURL serverUrl = ServerURL.parse(this.serverUrl);
            prefs.setProperty(PREF_HOST, serverUrl.getHost());
            prefs.setProperty(PREF_PORT, Integer.toString(serverUrl.getPort()));
            prefs.setProperty(PREF_TLS, Boolean.toString(serverUrl.isTLS()));
            if (serverUrl.getContext() == null) {
                prefs.remove(PREF_CONTEXT_PATH);
            } else {
                prefs.setProperty(PREF_CONTEXT_PATH, serverUrl.getContext());
            }
        }
        prefs.setProperty(PREF_AUTH_TYPE, authType.name());
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

        lab = new JLabel("Server URL: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        ceast.gridy = 1;
        inputPanel.add(lab, ceast);
        serverUrlTextField = new JTextField(serverUrl);
        serverUrlTextField.setPreferredSize(new Dimension(300, serverUrlTextField.getPreferredSize().height));
        cwest.gridy = 1;
        inputPanel.add(serverUrlTextField, cwest);

        ceast.gridy++;
        cwest.gridy++;
        lab = new JLabel("Auth Type: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(lab, ceast);
        authTypeCombo = new JComboBox<>(AuthType.values());
        authTypeCombo.setSelectedItem(authType);
        authTypeCombo.setPreferredSize(new Dimension(300, authTypeCombo.getPreferredSize().height));
        authTypeCombo.setEditable(false);
        authTypeCombo.setActionCommand("authType");
        authTypeCombo.addActionListener(this);
        inputPanel.add(authTypeCombo, cwest);

        ceast.gridy++;
        cwest.gridy++;
        usernameLabel = new JLabel("Username: ");
        usernameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(usernameLabel, ceast);
        usernameTextField = new JTextField(username);
        usernameTextField.setPreferredSize(new Dimension(300, usernameTextField.getPreferredSize().height));
        inputPanel.add(usernameTextField, cwest);

        ceast.gridy++;
        cwest.gridy++;
        passwordLabel = new JLabel("Password: ");
        passwordLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(passwordLabel, ceast);
        passwordTextField = new JPasswordField();
        passwordTextField.setPreferredSize(new Dimension(300, passwordTextField.getPreferredSize().height));
        inputPanel.add(passwordTextField, cwest);
        if (authType == AuthType.KERBEROS) {
            passwordLabel.setEnabled(false);
            passwordTextField.setEnabled(false);
            usernameLabel.setEnabled(false);
            usernameTextField.setEnabled(false);
            usernameTextField.setText(System.getProperty("user.name"));
        }

        if (getInstance) {
            lab = new JLabel("Instance: ");
            lab.setHorizontalAlignment(SwingConstants.RIGHT);

            ceast.gridy++;
            cwest.gridy++;

            inputPanel.add(lab, ceast);
            instanceCombo = new JComboBox<>(new String[] { instance });
            instanceCombo.setPreferredSize(new Dimension(300, instanceCombo.getPreferredSize().height));
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
                localMdbConfigCombo.setPreferredSize(serverUrlTextField.getPreferredSize());
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
                localMdbConfigCombo.setPreferredSize(serverUrlTextField.getPreferredSize());
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
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parent);
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
            authType = (AuthType) authTypeCombo.getSelectedItem();
            serverUrl = serverUrlTextField.getText();
            try {
                ServerURL.parse(serverUrl);
            } catch (IllegalArgumentException x) {
                JOptionPane.showMessageDialog(this, x.getMessage(), "Invalid Server URL",
                        JOptionPane.ERROR_MESSAGE);
                return; // do not close the dialog
            }

            if (!usernameTextField.getText().isEmpty()) {
                username = usernameTextField.getText();
                password = new String(passwordTextField.getPassword());
            } else {
                // If not authenticating, don't use last credentials
                username = null;
                password = null;
            }

            instance = (String) instanceCombo.getSelectedItem();
            if (instance == null) {
                JOptionPane.showMessageDialog(this, "You must specify an instance", "Missing instance",
                        JOptionPane.ERROR_MESSAGE);
                return; // do not close the dialog
            }

            // Verify the instance
            YamcsClient client = null;
            try {
                client = createClientForUseInDialogOnly();
                client.getInstance(instance).get();
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(this, "Cannot verify instance: " + e1.getMessage(),
                        e1.getMessage(), JOptionPane.WARNING_MESSAGE);
                return;
            } finally {
                if (client != null) {
                    client.close();
                }
            }

            passwordTextField.setText("");
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
        } else if ("authType".equals(cmd)) {
            var isKerberos = authTypeCombo.getSelectedItem() == AuthType.KERBEROS;
            usernameLabel.setEnabled(!isKerberos);
            if (isKerberos) {
                usernameTextField.setText(System.getProperty("user.name"));
            }
            usernameTextField.setEnabled(!isKerberos);
            passwordLabel.setEnabled(!isKerberos);
            passwordTextField.setText(null);
            passwordTextField.setEnabled(!isKerberos);
        } else if ("getInstances".equals(cmd)) {
            try {
                YamcsClient client = null;
                try {
                    client = createClientForUseInDialogOnly();
                    List<YamcsInstance> list = client.listInstances().get();
                    instanceCombo.removeAllItems();
                    for (YamcsInstance instance : list) {
                        if (getInstance) {
                            instanceCombo.addItem(instance.getName());
                        }
                    }
                } finally {
                    if (client != null) {
                        client.close();
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

    private YamcsClient createClientForUseInDialogOnly() throws ClientException {
        var clientBuilder = YamcsClient.newBuilder(serverUrlTextField.getText())
                .withUserAgent("PacketViewer")
                .withVerifyTls(false);

        var authType = (AuthType) authTypeCombo.getSelectedItem();
        if (authType == AuthType.BASIC_AUTH && !usernameTextField.getText().isEmpty()) {
            clientBuilder.withCredentials(
                    new BasicAuthCredentials(usernameTextField.getText(), passwordTextField.getPassword()));
        }

        var client = clientBuilder.build();

        if (usernameTextField.getText().isEmpty()) {
            client.pollServer();
        } else {
            if (authType == AuthType.STANDARD) {
                client.login(usernameTextField.getText(), passwordTextField.getPassword());
            } else if (authType == AuthType.KERBEROS) {
                client.loginWithKerberos(usernameTextField.getText());
            }
        }

        return client;
    }

    public ConnectData getConnectData() {
        ConnectData data = new ConnectData();
        data.authType = authType;
        data.serverUrl = serverUrl;
        if (username != null) {
            data.username = username;
            data.password = password.toCharArray();
        }
        data.useServerMdb = useServerMdb;
        data.localMdbConfig = (String) localMdbConfigCombo.getSelectedItem();
        data.streamName = streamName.getText();
        data.instance = instance;
        return data;
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
}
