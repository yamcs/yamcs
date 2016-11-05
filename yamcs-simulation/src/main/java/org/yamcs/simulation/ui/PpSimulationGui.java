package org.yamcs.simulation.ui;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;

import java.awt.*;
import java.awt.event.*;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsVersion;
import org.yamcs.ui.yamcsmonitor.YamcsMonitor;

public class PpSimulationGui implements ActionListener {

	JFrame frame;
	JFrame optionsFrame;
	String scenariosLibPath = "/home/msc/development/svn/usoc/trunk/tyna/src/test/resources";
	String runningScenarioPath = "/home/msc/development/git/yamcs/live/etc/simulation.xml";
	String selectedScenario = "-";
	JTextField scenariosLibField;
	JTextField yamcsInstallField;
	private JTextArea logTextArea;
	private static final String TITLE = "Yamcs PP Simulation";

	private void createAndShowGui() {
		
		// Load user preference
		LoadUserPreferences();
		
		// create main frame
		frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setIconImage(getIcon("yamcs-monitor-32.png").getImage());

		// create Menu
		frame.setJMenuBar(createMenu());

		// build GUI
		Box dsp = Box.createVerticalBox();
		dsp.add(createScenarioList());

		Box csp = Box.createVerticalBox();
		csp.add(createCurrentScenarioPanel());
		// csp.add(buildClientTable());
		// csp.add(buildCreateChannelPanel());

		logTextArea = new JTextArea(5, 20);
		logTextArea.setEditable(false);
		JScrollPane scroll = new JScrollPane(logTextArea);
		scroll.setBorder(BorderFactory.createEtchedBorder());

		JSplitPane phoriz = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dsp,
				csp);
		phoriz.setResizeWeight(0);
		JSplitPane pvert = new JSplitPane(JSplitPane.VERTICAL_SPLIT, phoriz,
				scroll);
		pvert.setResizeWeight(1.0);
		frame.getContentPane().add(pvert, BorderLayout.CENTER);
		frame.setPreferredSize(new Dimension(1280, 600));

		// Display the window.
		setTitle("not selected");

		frame.pack();
		frame.setVisible(true);
	}

	public ImageIcon getIcon(String imagename) {
		return new ImageIcon();
		// return new ImageIcon(getClass().getResource(
		// "/org/yamcs/images/" + imagename));
	}

	// /
	// action Performed
	//
	//
	@Override
	public void actionPerformed(ActionEvent ae) {
		String cmd = ae.getActionCommand();
		if (cmd.equals("exit")) {
			System.exit(0);
		} else if (cmd.equals("about")) {
			showAbout();
		} else if (cmd.equals("directories")) {
			showDirectories();
		} else if (cmd.equals("selectScenarioFolder")) {
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int result = fc.showDialog(frame, "Select");
			if (result == JFileChooser.APPROVE_OPTION) {
				File f = fc.getSelectedFile();
				scenariosLibField.setText(f.getAbsolutePath());
			}
		} else if (cmd.equals("selectRunningScenario")) {
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
			int result = fc.showDialog(frame, "Select");
			if (result == JFileChooser.APPROVE_OPTION) {
				File f = fc.getSelectedFile();
				yamcsInstallField.setText(f.getAbsolutePath());
			}
		} else if (cmd.equals("optionsOk")) {
			scenariosLibPath = scenariosLibField.getText();
			runningScenarioPath = yamcsInstallField.getText();

			// Store values in user's preferences
			Preferences prefs = Preferences
					.userNodeForPackage(org.yamcs.simulation.ui.PpSimulationGui.class);
			prefs.put(USER_SCENARIO_LIB_PATH, scenariosLibPath);
			prefs.put(USER_RUNNING_SCENARIO_PATH, runningScenarioPath);

			optionsFrame.setVisible(false);
			refreshScenarios();
		}
	}

	// /
	// createMenu
	//
	private JMenuBar createMenu() {
		JMenuBar menuBar = new JMenuBar();
		int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

		// File
		JMenu menu = new JMenu("File");
		menu.setMnemonic(KeyEvent.VK_F);
		menu.addSeparator();
		// item Quit
		JMenuItem menuItem = new JMenuItem("Quit", KeyEvent.VK_Q);
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuKey));
		menuItem.getAccessibleContext().setAccessibleDescription(
				"Quit PP Simulation");
		menuItem.addActionListener(this);
		menuItem.setActionCommand("exit");
		menu.add(menuItem);
		menuBar.add(menu);

		// Option
		menu = new JMenu("Options");
		menu.setMnemonic(KeyEvent.VK_O);
		menuItem = new JMenuItem("Directories", KeyEvent.VK_D);
		menuItem.getAccessibleContext().setAccessibleDescription(
				"Select directories for simulation scenario files");
		menuItem.addActionListener(this);
		menuItem.setActionCommand("directories");
		menu.add(menuItem);
		menuBar.add(menu);

		// Help
		menu = new JMenu("Help");
		menu.setMnemonic(KeyEvent.VK_H);
		menuBar.add(menu);

		menuItem = new JMenuItem("About " + TITLE);
		menuItem.addActionListener(this);
		menuItem.setActionCommand("about");
		menu.add(menuItem);

		return menuBar;
	}

	private JTree scenarioTree;
	private TreeNodeSimulationScenario currentScenarioSelection = null;

	// /
	// createScenarioList()
	//
	private JScrollPane createScenarioList() {
		scenarioTree = new JTree();
		scenarioTree.getSelectionModel().setSelectionMode(
				TreeSelectionModel.SINGLE_TREE_SELECTION);
		scenarioTree.addTreeSelectionListener(new TreeSelectionListener() {

			@Override
			public void valueChanged(TreeSelectionEvent e) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) scenarioTree
						.getLastSelectedPathComponent();

				if (node == null) {
					buttonSelect.setEnabled(false);
					currentScenarioSelection = null;
					refreshCurrentScenarioPreview(null);
					return;
				}
				Object nodeInfo = node.getUserObject();
				if (node.isLeaf() && !node.getAllowsChildren()) {
					currentScenarioSelection = (TreeNodeSimulationScenario) nodeInfo;
					refreshCurrentScenarioPreview(currentScenarioSelection.absolutePath);
					buttonSelect.setEnabled(true);
					return;
				} else {
					buttonSelect.setEnabled(false);
					currentScenarioSelection = null;
					refreshCurrentScenarioPreview(null);
					return;
				}
			}
		});

		JScrollPane listScroller = new JScrollPane(scenarioTree);
		listScroller.setBorder(BorderFactory.createTitledBorder("Library"));
		listScroller.setPreferredSize(new Dimension(300, 80));
		refreshScenarios();
		return listScroller;
	}

	// /
	// refreshScenarios()
	//
	// Load scenario list from folder, recursively visiting subfolders
	private void refreshScenarios() {

		log("Refreshing scenarios library...");

		// JTree scenarioTree = new JTree();
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(
				"Simulation Scenarios");
		refreshScenariosProcessNode(root, scenariosLibPath);
		TreeModel treeModel = new DefaultTreeModel(root, true);
		scenarioTree.setModel(treeModel);

		// expand nodes
		for (int i = 0; i < scenarioTree.getRowCount(); i++) {
			scenarioTree.expandRow(i);
		}

		log("ok");
	}

	// /
	// refreshScenariosProcessNode()
	// recursive method to get scenario files in a folder
	private void refreshScenariosProcessNode(
			DefaultMutableTreeNode currentNode, String currentScenariosLibPath) {
		File folder = null;
		File[] listOfFiles = null;
		Vector<TreeNodeSimulationScenario> leafs = new Vector<TreeNodeSimulationScenario>();
		try {
			// read files in current folder
			folder = new File(currentScenariosLibPath);
			listOfFiles = folder.listFiles();
			Arrays.sort(listOfFiles);
		} catch (Exception e) {
		}
		if (folder == null || listOfFiles == null) {
			log("Unable to open scenarios folder at '" + scenariosLibPath + "'");
			return;
		}
		for (int i = 0; i < listOfFiles.length; i++) {
			File f = listOfFiles[i];
			// process sub folder
			if (f.isDirectory()) {
				DefaultMutableTreeNode node = new DefaultMutableTreeNode(
						f.getName());
				currentNode.add(node);
				refreshScenariosProcessNode(node, f.getAbsolutePath());
			}
			// store leaf files for latter processing
			TreeNodeSimulationScenario tnss = new TreeNodeSimulationScenario(f);
			leafs.add(tnss);
		}

		// add leaf files at the end of the list
		for (TreeNodeSimulationScenario tnss : leafs) {
			if (tnss.valid) {
				currentNode.add(new DefaultMutableTreeNode(tnss, false));
			}
		}

	}

	// /
	// subclass TreeNodeSimulationScenario
	// To display nodes in the selection tree
	class TreeNodeSimulationScenario {
		public String absolutePath = null;
		public String displayName = null;
		public boolean valid;

		public TreeNodeSimulationScenario(File f) {
			valid = f.isFile() && f.getName().endsWith(".xml");
			if (valid) {
				this.absolutePath = f.getAbsolutePath();
				this.displayName = f.getName().substring(0,
						f.getName().length() - 4);
			}
		}

		public String toString() {
			return displayName;
		}
	}

	// /
	// createCurrentScenarioPanel()
	//
	JTextArea panelDescription;
	JTextArea panelXml;
	JButton buttonSelect;
	JLabel labelSelectedScenario;

	private JPanel createCurrentScenarioPanel() {
		JPanel panel = new JPanel();

		// setup main layout
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		JTabbedPane tabbedPreview = new JTabbedPane();
		buttonSelect = new JButton("Select");
		buttonSelect.setEnabled(false);

		c.weightx = 1;
		c.weighty = 1;
		c.gridy = 0;
		c.fill = GridBagConstraints.BOTH;
		panel.add(tabbedPreview, c);

		JPanel panelRunningScenario = new JPanel();
		panelRunningScenario.setBorder(BorderFactory
				.createTitledBorder("Play Scenario"));
		panelRunningScenario.setLayout(new GridBagLayout());
		c.weightx = 1;
		c.weighty = 0;
		c.gridy = 1;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.WEST;
		panel.add(panelRunningScenario, c);

		// add sub element of play panel
		c = new GridBagConstraints();
		c.weightx = 1;
		c.weighty = 0;
		c.gridy = 0;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.WEST;
		panelRunningScenario.add(buttonSelect, c);
		JPanel panelSelected = new JPanel();
		c.weighty = 1;
		c.gridy = 1;
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.WEST;
		panelRunningScenario.add(panelSelected, c);

		// build preview panel
		tabbedPreview.setBorder(BorderFactory.createTitledBorder("Preview"));
		panelDescription = new JTextArea();
		JScrollPane scrollPanelDescription = new JScrollPane(panelDescription);
		panelXml = new JTextArea();
		JScrollPane scrollPanelXml = new JScrollPane(panelXml);
		// scrollPanelXml.setPreferredSize(new Dimension(600, 600));
		tabbedPreview.addTab("Description", scrollPanelDescription);
		tabbedPreview.addTab("Details - Xml", scrollPanelXml);

		// button select
		buttonSelect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selectScenario();
			}
		});

		// panel play
		panelSelected.add(new JLabel("Selected Scenario:"));
		labelSelectedScenario = new JLabel(selectedScenario);
		panelSelected.add(labelSelectedScenario);

		return panel;
	}

	private void refreshCurrentScenarioPreview(String scenarioPath) {

		if (scenarioPath == null) {
			panelDescription.setText("");
			panelDescription.setEnabled(false);
			panelXml.setText("");
			panelXml.setEnabled(false);
			return;
		} else {
			panelDescription.setEnabled(true);
			panelXml.setEnabled(true);
		}

		// read scenario file
		String scenarioText = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(scenarioPath));

			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append("\n");
				line = br.readLine();
			}
			scenarioText = sb.toString();

			br.close();
		} catch (Exception e) {
			log("Unable to read Scenario file '" + scenarioPath + "'. Details:"
					+ e);
			return;
		}

		// Extract description
		try {
			int s = scenarioText.indexOf("<description>");
			int e = scenarioText.indexOf("</description>");
			String description = scenarioText.substring(s + 13, e);
			panelDescription.setText(description);
		} catch (Exception e) {
			log("Unable to display Scenario description");
			panelDescription.setText("-");
		}

		// put full xml text
		panelXml.setText(scenarioText);
	}

	private void selectScenario() {
		// get selection from scenario list
		selectedScenario = currentScenarioSelection.displayName;

		// Update selected scenario
		labelSelectedScenario.setText(selectedScenario);

		// update current scenario in filesystem
		try {
			InputStream is = null;
			OutputStream os = null;

			is = new FileInputStream(currentScenarioSelection.absolutePath);
			os = new FileOutputStream(runningScenarioPath);
			byte[] buffer = new byte[1024];
			int length;
			while ((length = is.read(buffer)) > 0) {
				os.write(buffer, 0, length);
			}
			is.close();
			os.close();
		} catch (Exception e) {
			log("Unable to set the selected Scenario as the running Scenario (path is '"
					+ runningScenarioPath + "'). Details:\n" + e);
			return;
		}
		log("Scenario '"
				+ selectedScenario
				+ "' selected. To start playing the scenario, (re)enable the simulation link in Yamcs Monitor");
		setTitle("selected");

	}

	void setTitle(final String title) {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				frame.setTitle(TITLE + " (" + title + ")");
			}
		});
	}

	// @Override
	public void log(final String s) {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				logTextArea.append(s + "\n");
			}
		});
	}

	public void showAbout() {
		JTextPane pane = new JTextPane();
		pane.setContentType("text/html");
		pane.setEditable(false);
		pane.setText("<center>"
				+ "<h2>"
				+ TITLE
				+ " GUI</h2>"
				+ "<h3>&copy; Space Applications Services</h3>"
				+ "<h3>Version "
				+ YamcsVersion.version
				+ "</h3>"
				+ "<p>This program is used to simulate Processed Parameters in a Yamcs server "
				+ "</center>");
		pane.setPreferredSize(new Dimension(350, 180));

		JOptionPane.showMessageDialog(frame, pane, TITLE,
				JOptionPane.PLAIN_MESSAGE, getIcon("yamcs-64x64.png"));
	}
	
	
	private void LoadUserPreferences()
	{
		// Retrieve the user settings
		Preferences prefs = Preferences
				.userNodeForPackage(org.yamcs.simulation.ui.PpSimulationGui.class);
		scenariosLibPath = prefs.get(USER_SCENARIO_LIB_PATH, scenariosLibPath);
		runningScenarioPath = prefs.get(USER_RUNNING_SCENARIO_PATH,
				runningScenarioPath);
	}

	// //
	// showDirectories()
	//
	// Build option panel to choose directories
	//
	final String USER_SCENARIO_LIB_PATH = "scenariosLibPath";
	final String USER_RUNNING_SCENARIO_PATH = "runningScenarioPath";

	public void showDirectories() {

		LoadUserPreferences();

		if (optionsFrame != null && optionsFrame.isVisible()) {
			optionsFrame.toFront();
			optionsFrame.repaint();
			return;
		}
		optionsFrame = new JFrame();
		optionsFrame.setLayout(new GridBagLayout());

		// create default directories
		scenariosLibField = new JTextField(scenariosLibPath);
		yamcsInstallField = new JTextField(runningScenarioPath);

		// constraints on the grid layout
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;

		c.weightx = 0;
		c.gridx = 0;
		c.gridy = 0;
		optionsFrame.add(new JLabel("Scenario Library:"), c);
		c.weightx = 5;
		c.gridx = 1;
		c.gridy = 0;
		optionsFrame.add(scenariosLibField, c);
		c.weightx = 0;
		c.gridx = 2;
		c.gridy = 0;
		JButton b1 = new JButton("Select Folder");
		b1.addActionListener(this);
		b1.setActionCommand("selectScenarioFolder");
		optionsFrame.add(b1, c);
		c.weightx = 0;
		c.gridx = 0;
		c.gridy = 1;
		optionsFrame.add(new JLabel("Running Scenario Target:"), c);
		c.weightx = 5;
		c.gridx = 1;
		c.gridy = 1;
		optionsFrame.add(yamcsInstallField, c);
		c.weightx = 0;
		c.gridx = 2;
		c.gridy = 1;
		JButton b2 = new JButton("Select File");
		b2.addActionListener(this);
		b2.setActionCommand("selectRunningScenario");
		optionsFrame.add(b2, c);
		c.weightx = 0;
		c.gridx = 2;
		c.gridy = 3;
		JButton b3 = new JButton("Ok");
		b3.addActionListener(this);
		b3.setActionCommand("optionsOk");
		optionsFrame.add(b3, c);
		optionsFrame.pack();
		optionsFrame.setSize(1024, optionsFrame.getSize().height);

		optionsFrame.setTitle(TITLE + " - Options");
		optionsFrame.setVisible(true);
	}

	// //
	// main()
	//
	//
	public static void main(String[] args) throws IOException,
			URISyntaxException, ConfigurationException {

		final PpSimulationGui app = new PpSimulationGui();
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				app.createAndShowGui();
			}
		});
	}
}
