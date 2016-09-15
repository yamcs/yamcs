package org.yamcs.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.utils.ActiveMQBufferInputStream;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.YamcsManagement.MissionDatabaseRequest;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.XtceDb;

/**
 * Displays packets with their parameters in a hierarchical view, allowing the
 * user to select parameters.
 * 
 * Basic search facility restricts view to parameters which contain specified
 * text, with mutliple terms separated by *.
 * 
 * Tree behaviour is currently default, so selecting a packet will not
 * automatically select all its parameters.
 * 
 * @author atu
 *
 */
public class ParameterSelectDialog extends JDialog implements ActionListener, KeyListener, TreeSelectionListener {
    private static final long serialVersionUID = 201212121400L;
    private YamcsConnectionProperties connectData;
    private XtceDb xtcedb;
    private final JTree treeView;
    private JLabel errorLabel;
    private FilterableXtceDbTreeModel tm;
    private JTextField searchField;
    private List<ParameterSelectDialogListener> listeners;
    ListIndexBar bar;

    public ParameterSelectDialog(JFrame parent, YamcsConnectionProperties ycd) {
	this( parent, ycd, null );
    }

    public ParameterSelectDialog(JFrame parent, YamcsConnectionProperties ycd, XtceDb db) {
	super(parent, "Parameter Selection", true);
	xtcedb = db;
	connectData = ycd;
	listeners = new ArrayList<ParameterSelectDialogListener>();

	JPanel searchButtonPanel = new JPanel();
	searchButtonPanel.setLayout( new BorderLayout() );
	getContentPane().add(searchButtonPanel, BorderLayout.NORTH);
	searchButtonPanel.add( new JLabel( "Search" ), BorderLayout.WEST );
	searchField = new JTextField( "" );
	searchField.addKeyListener( this );
	searchButtonPanel.add( searchField, BorderLayout.CENTER );

	errorLabel = new JLabel( "Loading..." );
	getContentPane().add( new JScrollPane( errorLabel ), BorderLayout.CENTER );

	loadXtcedb();

	tm = new FilterableXtceDbTreeModel( xtcedb );
	treeView = new JTree( tm );
	treeView.addTreeSelectionListener( this );
	ToolTipManager.sharedInstance().registerComponent(treeView);
	treeView.setCellRenderer( new XtceDbCellRenderer() );
	treeView.setExpandsSelectedPaths(true);
	if( xtcedb != null ) {
	    JScrollPane scrollPane = new JScrollPane( treeView );
	    scrollPane.setVerticalScrollBarPolicy( JScrollPane.VERTICAL_SCROLLBAR_ALWAYS );
	    getContentPane().add( scrollPane, BorderLayout.CENTER );
	}

	bar = new ListIndexBar( treeView.getRowCount() );
	bar.setBackground( Color.white );
	bar.setForeground( Color.BLUE );
	bar.setOpaque( false );
	getContentPane().add( bar, BorderLayout.EAST );
	bar.addSelectionListener( new ListSelectionListener() {
	    @Override
	    public void valueChanged( ListSelectionEvent e ) {
		int selectedIndex = e.getFirstIndex();
		treeView.scrollRowToVisible( selectedIndex );
	    }
	} );

	// OK and Cancel buttons
	JPanel buttonPanel = new JPanel();
	getContentPane().add(buttonPanel, BorderLayout.SOUTH);

	JButton button = new JButton("Add");
	button.setActionCommand("add");
	button.addActionListener(this);
	getRootPane().setDefaultButton(button);
	buttonPanel.add(button);

	button = new JButton("Close");
	button.setActionCommand("close");
	button.addActionListener(this);
	buttonPanel.add(button);

	// Final setup then pack
	setMinimumSize(new Dimension(350, 100));
	setLocationRelativeTo(parent);
	setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	pack();
	searchField.requestFocusInWindow();
    }

    private YamcsClient createYamcsClient() throws YamcsApiException, ActiveMQException {
	YamcsClient yamcsClient = null;
	YamcsSession ys=YamcsSession.newBuilder().setConnectionParams(connectData).build();
	yamcsClient=ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
	return yamcsClient;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
	String cmd=e.getActionCommand();
	if ( "add".equals( cmd ) ) {
	    for( ParameterSelectDialogListener l : listeners ) {
		l.parametersAdded( getSelectedParameterOpsNames() );
	    }
	} else {
	    setVisible( false );
	}
    }

    public void updateBar() {
	bar.setItemCount( treeView.getRowCount() );
	bar.clearMarkers();
	int [] selectedRows = treeView.getSelectionRows();
	if( selectedRows != null ) {
	    for( int row : selectedRows ) {
		bar.addMarker( row );
	    }
	}
    }

    @Override
    public void valueChanged( TreeSelectionEvent e ) {
	updateBar();
    }

    public void loadXtcedb() {
	if( xtcedb != null ) {
	    return;
	}
	YamcsClient yamcsClient = null;
	ObjectInputStream ois = null;
	try {
	    yamcsClient = createYamcsClient();
	    MissionDatabaseRequest mdr = MissionDatabaseRequest.newBuilder().setInstance(connectData.getInstance()).build();
	    yamcsClient.executeRpc(Protocol.YAMCS_SERVER_CONTROL_ADDRESS, "getMissionDatabase", mdr, null);
	    ClientMessage msg=yamcsClient.dataConsumer.receive(5000);
	    ois=new ObjectInputStream(new ActiveMQBufferInputStream(msg.getBodyBuffer()));
	    Object o=ois.readObject();
	    xtcedb=(XtceDb) o;
	    setLoadSuccess();
	} catch (Exception e) {
	    System.out.println( "Exception whilst getting mission database: "+e.getMessage() );
	    setLoadFail( e.getMessage() );
	} finally {
	    if( yamcsClient != null ) {
		try {
		    yamcsClient.close();
		} catch (ActiveMQException e) {
		    // Ignore
		}
		try {
		    yamcsClient.getYamcsSession().close();
		} catch (ActiveMQException e) {
		    // Ignore
		}
	    }
	    if( ois != null ) {
		try {
		    ois.close();
		} catch (IOException e) {
		    // Ignore
		}
	    }
	}
    }

    private void setLoadFail( String message ) {
	if( treeView != null ) {
	    treeView.setVisible( false );
	}
	if( searchField != null ) {
	    searchField.setVisible( false );
	}
	errorLabel.setVisible( true );
	errorLabel.setText( message );
    }
    private void setLoadSuccess() {
	errorLabel.setVisible( false );
	if( treeView != null ) {
	    treeView.setVisible( true );
	}
	if( searchField != null ) {
	    searchField.setVisible( true );
	}
    }

    /**
     * Convenience method to get selected Parameter opsnames.
     * @return
     */
    public List<String> getSelectedParameterOpsNames() {
	List<String> params = new ArrayList<String>();
	for( Parameter p : getSelectedParameters() ) {
	    params.add( p.getOpsName() );
	}
	return params;
    }

    /**
     * Gets all selected ParameterEntry instances, ignoring any other selected
     * object.
     * 
     * @return List, always valid but may be empty.
     */
    public List<Parameter> getSelectedParameters() {
	// Assumes model has parameters as leaves
	List<Parameter> params = new ArrayList<Parameter>();
	TreePath[] selectedPaths = treeView.getSelectionPaths();
	if( selectedPaths != null ) {
	    for( TreePath tp : selectedPaths ) {
		Object node = tp.getLastPathComponent();
		if( node instanceof ParameterEntry ) {
		    params.add( ((ParameterEntry)node).getParameter() );
		}
	    }
	}
	return params;
    }

    /**
     * Gets all selected objects.
     * 
     * @return List, always valid but may be empty.
     */
    public List<Object> getSelected() {
	List<Object> selected = new ArrayList<Object>();
	TreePath[] selectedPaths = treeView.getSelectionPaths();
	if( selectedPaths != null ) {
	    for( TreePath tp : selectedPaths ) {
		selected.add( tp.getLastPathComponent() );
	    }
	}
	return selected;
    }

    // KeyListener - for the searchField
    @Override
    public void keyPressed(KeyEvent arg0) { /* Ignore in favour of keyReleased */ }
    @Override
    public void keyTyped(KeyEvent arg0) { /* Ignore in favour of keyReleased */ }
    @Override
    public void keyReleased(KeyEvent arg0) {
	// Make sure currently selected objects continue to be shown
	TreePath [] selected = treeView.getSelectionPaths();
	tm.setAlwaysShown( getSelected() );
	tm.setFilterText( searchField.getText() );
	// Re-select previously selected.
	treeView.setSelectionPaths( selected );
	updateBar();
    }

    public void addListener( ParameterSelectDialogListener l ) {
	listeners.add( l );
    }
    public void removeListener( ParameterSelectDialogListener l ) {
	listeners.remove( l );
    }

    /**
     * Presents the dialog to the user to get the current selection.
     * 
     * @return List of parameter opsnames if OK button clicked, null otherwise.
     */
    public List<String> showDialog() {
	setVisible( true );
	/*if( returnValue == APPROVE_OPTION ) {
        	return getSelectedParameterOpsNames();
        }*/
	return null;
    }

    public static void main( String[] args ) throws YamcsApiException, ActiveMQException, ConfigurationException {
	YConfiguration config = YConfiguration.getConfiguration("yamcs-ui");
/*ARTEMIS
	YamcsConnectDialogResult ycd = YamcsConnectDialog.showDialog(null, true, config.getBoolean("authenticationEnabled"));
	if( ycd.isOk() ) {
	    ParameterSelectDialog psd = new ParameterSelectDialog(null, ycd.getConnectionProperties()e);
	    List<String> params = psd.showDialog();
	    if( params != null ) {
		for( String param : params ) {
		    System.out.println( param );
		}
	    }
	}*/
	System.exit(0);
    }
}

interface ParameterSelectDialogListener {
    public void parametersAdded( List<String> opsnames );
}