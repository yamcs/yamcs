package org.yamcs.api;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.hornetq.api.core.HornetQException;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.Yamcs.YamcsInstance;
import org.yamcs.protobuf.Yamcs.YamcsInstances;


/**
 * Dialog for entering yamcs connection parameters
 * @author nm
 *
 */
public class YamcsConnectDialog extends JDialog implements ActionListener {
	private static final long serialVersionUID = 1L;
	private YamcsConnectData values;
	JTextField hostTextField;
	JTextField portTextField;
	JTextField usernameTextField;
	private JPasswordField passwordTextField;
	//JCheckBox sslCheckBox;
	private JComboBox instanceCombo;
	boolean getInstance=false;
	
	//if set to true it will show the username/password login
	boolean authEnabled;
	
	static YamcsConnectDialog dialog;
	YamcsConnectDialog( JFrame parent, boolean getInstance, boolean enableAuth ) {
		super(parent, "Connect to Yamcs", true);
		this.getInstance=getInstance;
		this.authEnabled = enableAuth;
		
		values = new YamcsConnectData();
		values.load();

		JPanel inputPanel, buttonPanel;
		JLabel lab;
		JButton button;

		// input panel

		inputPanel = new JPanel(new GridBagLayout());
		GridBagConstraints ceast = new GridBagConstraints();
		ceast.anchor=GridBagConstraints.EAST;
		GridBagConstraints cwest = new GridBagConstraints();
		cwest.weightx=1; cwest.fill=GridBagConstraints.HORIZONTAL;
		
		inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
		getContentPane().add(inputPanel, BorderLayout.CENTER);

		ceast.gridy = 1;
		cwest.gridy = 1;
		lab = new JLabel("Host: ");
		lab.setHorizontalAlignment(SwingConstants.RIGHT);
		inputPanel.add(lab,ceast);
		hostTextField = new JTextField(values.host);
		hostTextField.setPreferredSize(new Dimension(160, hostTextField.getPreferredSize().height));
		inputPanel.add(hostTextField,cwest);

		ceast.gridy++;
		cwest.gridy++;
		lab = new JLabel("Port: ");
		lab.setHorizontalAlignment(SwingConstants.RIGHT);
		inputPanel.add(lab,ceast);
		portTextField = new JTextField(Integer.toString(values.port));
		inputPanel.add(portTextField,cwest);
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
		if(authEnabled) {
		    lab = new JLabel("Username: ");
		    lab.setHorizontalAlignment(SwingConstants.RIGHT);
		    inputPanel.add(lab,ceast);
		    usernameTextField = new JTextField(values.username);
		    usernameTextField.setPreferredSize(new Dimension(160, usernameTextField.getPreferredSize().height));
		    inputPanel.add(usernameTextField,cwest);
		
		    ceast.gridy++;
		    cwest.gridy++;
		    lab = new JLabel("Password: ");
		    lab.setHorizontalAlignment(SwingConstants.RIGHT);
		    inputPanel.add(lab,ceast);
		    passwordTextField = new JPasswordField();
		    passwordTextField.setPreferredSize(new Dimension(160, passwordTextField.getPreferredSize().height));
		    inputPanel.add(passwordTextField,cwest);
		}
		
		if(getInstance) {
			ceast.gridy++;
			cwest.gridy++;
		    lab = new JLabel("Instance: ");
		    lab.setHorizontalAlignment(SwingConstants.RIGHT);
		    inputPanel.add(lab,ceast);
		    instanceCombo = new JComboBox(new String[]{values.instance});
		    instanceCombo.setPreferredSize(hostTextField.getPreferredSize());
		    instanceCombo.setEditable(true);
		    inputPanel.add(instanceCombo,cwest);
		    button = new JButton("Update");
		    button.setToolTipText("Fetch available instances from chosen Yamcs server");
	        button.setActionCommand("getInstances");
	        button.addActionListener(this);
	        inputPanel.add(button,ceast);
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

		setMinimumSize(new Dimension(150, 100));
		setLocationRelativeTo(parent);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
	}

	@Override
    public void actionPerformed( ActionEvent e ) {
		if ( e.getActionCommand().equals("connect") ) {
		    if(getInstance){
		        String inst=(String)instanceCombo.getSelectedItem();
		        if(inst==null || inst.length()==0) {
		            JOptionPane.showMessageDialog(this, "Please select the instance");
		            return;
		        }
		    }
		           
			try {
				values.isOk = true;
				values.host = hostTextField.getText();
				values.port = Integer.parseInt(portTextField.getText());
				if(authEnabled) {
				    values.username = usernameTextField.getText();
				    values.password = passwordTextField.getText();
				    passwordTextField.setText("");
				    // Treat empty strings as null
				    if( "".equals( values.username ) ) values.username = null;
				    if( "".equals( values.password ) ) values.password = null;
				} else {
					// If not authenticating, don't use last credentials
					values.username = null;
					values.password = null;
				}
				if(instanceCombo!=null) 	values.instance=(String)instanceCombo.getSelectedItem();
				setVisible(false);
				values.save();
			} catch (NumberFormatException x) {
				// do not close the dialogue
			}
		} else if ( e.getActionCommand().equals("cancel") ) {
			values.isOk = false;
			setVisible(false);
		} else if(e.getActionCommand().equals("getInstances") ) { 
		    try {
		        String host=hostTextField.getText();
		        int port=Integer.parseInt(portTextField.getText());
		        String username = null;
		        String password = null;
		        if(authEnabled) {
		            username = usernameTextField.getText();
		            password = passwordTextField.getText();
		            // Treat empty strings as null
		            if( "".equals( username ) ) username = null;
		            if( "".equals( password ) ) password = null;
		        }
		        YamcsSession ys=YamcsSession.newBuilder().setConnectionParams(host, port, username, password).build();
		        YamcsClient yclient=ys.newClientBuilder().setRpc(true).build();
		        YamcsInstances ainst=(YamcsInstances)yclient.executeRpc(Protocol.YAMCS_SERVER_CONTROL_ADDRESS, "getYamcsInstances", null, YamcsInstances.newBuilder());
		        instanceCombo.removeAllItems();
		        for(YamcsInstance ai:ainst.getInstanceList()) instanceCombo.addItem(ai.getName());
		        instanceCombo.setPopupVisible(true);
		        yclient.close();
		        ys.close();
		    } catch (NumberFormatException x) {
		        JOptionPane.showMessageDialog(this, "please enter a valid port number", x.getMessage(), JOptionPane.WARNING_MESSAGE);
            } catch (HornetQException e1) {
                JOptionPane.showMessageDialog(this, "Cannot retrieve the archive instances: "+e1.getMessage(), e1.getMessage(), JOptionPane.WARNING_MESSAGE);
            }  catch (YamcsException e1) {
                JOptionPane.showMessageDialog(this, "Cannot retrieve the archive instances: "+e1.getMessage(), e1.getMessage(), JOptionPane.WARNING_MESSAGE);
            } catch (YamcsApiException e1) {
                JOptionPane.showMessageDialog(this, "Cannot retrieve the archive instances: "+e1.getMessage(), e1.getMessage(), JOptionPane.WARNING_MESSAGE);
            }
		}
	}

	public final static YamcsConnectData showDialog(JFrame parent, boolean getInstance, boolean enableAuth) {
		if(dialog==null) dialog = new YamcsConnectDialog(parent, getInstance, enableAuth);
		dialog.setVisible(true);
		return dialog.values.clone();
	}
	
	public static void main(String[] args){
	    YamcsConnectDialog.showDialog(null, true, true);
	}

}


