package org.yamcs.api;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;

/**
 * Dialog for entering yamcs connection parameters
 * 
 * @author nm
 *
 */
public class YamcsConnectDialog extends JDialog implements ActionListener {
    private static final long serialVersionUID = 1L;
    private YamcsConnectDialogResult result;
    YamcsConnectionProperties connectionProperties;
    JTextField hostTextField;
    JTextField portTextField;
    JTextField usernameTextField;
    private JPasswordField passwordTextField;
    // JCheckBox sslCheckBox;
    private JComboBox<String> instanceCombo;
    boolean getInstance = false;

    static YamcsConnectDialog dialog;

    YamcsConnectDialog(JFrame parent, boolean getInstance) {
        super(parent, "Connect to Yamcs", true);
        this.getInstance = getInstance;
        installActions();
        connectionProperties = new YamcsConnectionProperties();
        try {
            connectionProperties.load();
        } catch (IOException e) {
            // ignore
        }
        result = new YamcsConnectDialogResult(connectionProperties);

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

        ceast.gridy = 1;
        cwest.gridy = 1;
        lab = new JLabel("Host: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(lab, ceast);
        hostTextField = new JTextField(connectionProperties.getHost());
        hostTextField.setPreferredSize(new Dimension(160, hostTextField.getPreferredSize().height));
        inputPanel.add(hostTextField, cwest);

        ceast.gridy++;
        cwest.gridy++;
        lab = new JLabel("Port: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(lab, ceast);
        portTextField = new JTextField(Integer.toString(connectionProperties.getPort()));
        inputPanel.add(portTextField, cwest);
        /*
        	ceast.gridy++;
        	cwest.gridy++;
        	lab = new JLabel("Use SSL: ");
        	lab.setHorizontalAlignment(SwingConstants.RIGHT);
        	inputPanel.add(lab, ceast);
        	JCheckBox sslCheckBox = new JCheckBox(); sslCheckBox.setSelected(values.ssl);
        	cwest.anchor=GridBagConstraints.WEST; inputPanel.add(sslCheckBox, cwest);
         */
        ceast.gridy++;
        cwest.gridy++;
        lab = new JLabel("Username: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(lab, ceast);
        usernameTextField = new JTextField();
        if (connectionProperties.getUsername() != null) {
            usernameTextField.setText(connectionProperties.getUsername());
        }
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

        ceast.gridy++;
        cwest.gridy++;
        lab = new JLabel("Instance: ");
        lab.setHorizontalAlignment(SwingConstants.RIGHT);
        inputPanel.add(lab, ceast);
        instanceCombo = new JComboBox<>(new String[] { connectionProperties.getInstance() });
        instanceCombo.setPreferredSize(hostTextField.getPreferredSize());
        instanceCombo.setEditable(true);
        inputPanel.add(instanceCombo, cwest);
        button = new JButton("Update");
        button.setToolTipText("Fetch available instances from chosen Yamcs server");
        button.setActionCommand("getInstances");
        button.addActionListener(this);
        inputPanel.add(button, ceast);

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

        setMinimumSize(new Dimension(150, 100));
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
                dispatchEvent(new WindowEvent(YamcsConnectDialog.this, WindowEvent.WINDOW_CLOSING));
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getActionCommand().equals("connect")) {
            if (getInstance) {
                String inst = (String) instanceCombo.getSelectedItem();
                if (inst == null || inst.length() == 0) {
                    JOptionPane.showMessageDialog(this, "Please select the instance");
                    return;
                }
            }

            try {
                result.isOk = true;
                connectionProperties.setHost(hostTextField.getText());
                connectionProperties.setPort(Integer.parseInt(portTextField.getText()));

                if (!usernameTextField.getText().isEmpty()) {
                    connectionProperties.setCredentials(usernameTextField.getText(), passwordTextField.getPassword());
                } else {
                    connectionProperties.setCredentials(null, null);
                }
                passwordTextField.setText("");

                if (instanceCombo != null) {
                    connectionProperties.setInstance((String) instanceCombo.getSelectedItem());
                }
                setVisible(false);
                connectionProperties.save();
            } catch (NumberFormatException x) {
                // do not close the dialogue
            }
        } else if (e.getActionCommand().equals("cancel")) {
            result.isOk = false;
            setVisible(false);
        } else if (e.getActionCommand().equals("getInstances")) {
            try {
                String host = hostTextField.getText();
                int port = Integer.parseInt(portTextField.getText());
                YamcsConnectionProperties tmp = new YamcsConnectionProperties(host, port);
                if (!usernameTextField.getText().isEmpty()) {
                    String username = usernameTextField.getText();
                    char[] password = passwordTextField.getPassword();
                    tmp.setCredentials(username, password);
                }
                RestClient restClient = new RestClient(tmp);
                List<YamcsInstance> yinstances = restClient.blockingGetYamcsInstances();
                instanceCombo.removeAllItems();
                for (YamcsInstance ai : yinstances) {
                    instanceCombo.addItem(ai.getName());
                }
                instanceCombo.setPopupVisible(true);
            } catch (NumberFormatException x) {
                JOptionPane.showMessageDialog(this, "please enter a valid port number", x.getMessage(),
                        JOptionPane.WARNING_MESSAGE);
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(this, "Cannot retrieve the archive instances: " + e1.getMessage(),
                        e1.getMessage(), JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    public final static YamcsConnectDialogResult showDialog(JFrame parent, boolean getInstance) {
        if (dialog == null) {
            dialog = new YamcsConnectDialog(parent, getInstance);
        }
        dialog.setVisible(true);

        return dialog.result;
    }

    public static void main(String[] args) {
        YamcsConnectDialog.showDialog(null, true);
    }

    public static class YamcsConnectDialogResult {
        private boolean isOk = false;
        private YamcsConnectionProperties connectionProperties;

        public YamcsConnectDialogResult(YamcsConnectionProperties connectionProperties) {
            this.connectionProperties = connectionProperties;
        }

        public boolean isOk() {
            return isOk;
        }

        public YamcsConnectionProperties getConnectionProperties() {
            return connectionProperties;
        }
    }
}
