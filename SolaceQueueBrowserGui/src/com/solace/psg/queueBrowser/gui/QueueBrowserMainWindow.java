package com.solace.psg.queueBrowser.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.FlatLightLaf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solace.psg.brokers.semp.SempClient;
import com.solace.psg.brokers.semp.SempClient.QueueInfo;
import com.solace.psg.brokers.semp.SempClient.eApi;
import com.solace.psg.brokers.semp.SempException;
import com.solace.psg.queueBrowser.PaginatedCachingBrowser;
import com.solace.psg.queueBrowser.gui.QueueActionWindow.eAction;
import com.solace.psg.queueBrowser.gui.dragAndDrop.DroppableMessage;
import com.solace.psg.queueBrowser.gui.dragAndDrop.IDragDropTarget;
import com.solace.psg.queueBrowser.gui.dragAndDrop.QueueMessageTransferReceiverHandler;
import com.solacesystems.jcsmp.JCSMPException;

public class QueueBrowserMainWindow implements IDragDropTarget {
	private static final Logger logger = LoggerFactory.getLogger(QueueBrowserMainWindow.class.getName());

	private SempClient sempV2ConfigClient;
	private SempClient sempV2MonitorClient;
	private SempClient sempV2ActionClient;;
	Broker broker;
	List<String> queues;
	String selectedQueue = "";
	int selectedQueueMsgCount = 0;
	String configFile;
	Config thisCfg = null;
	private JFrame frame;
	private JLabel detailsLabel;
	private JPanel buttonPanel;
	private JButton moveAllButton;
	private JButton refreshButton;
	private JButton browseButton;
	private JButton copyAllButton;
	private JButton deleteAllButton;
	private JButton exitButton;

	private IconicTableCellRenderer iconCellRenderer;
	JLabel qIconlabel; 

	private DefaultTableModel tableModel;

	private JTable table;
	
	private JComboBox<String> brokerComboBox;
	private JLabel greetingLine0;
	private JLabel greetingLine1;


	class ListItem {
	    private String text;
	    private Icon icon;
	    public ListItem(String text, Icon icon) {
	        this.text = text;
	        this.icon = icon;
	    }
	    public String getText() {
	        return text;
	    }
	    public Icon getIcon() {
	        return icon;
	    }
	    public String toString() {
	        return text;
	    }
	}
	
	private String masterPasswordFromCommandLine = null;
	
	public QueueBrowserMainWindow(String configFile) throws BrokerException {
		this.configFile = configFile;
		this.initialize();
	}
	
	public QueueBrowserMainWindow(String configFile, String masterPasswordFromCommandLine) throws BrokerException {
		this.configFile = configFile;
		this.masterPasswordFromCommandLine = masterPasswordFromCommandLine;
		this.initialize();
	}

	private void initialize() throws BrokerException {
		thisCfg = new Config(this.configFile);
		
		// Check if config has encrypted passwords and prompt for master password if needed
		handleMasterPassword();
		
		thisCfg.load();
		broker = thisCfg.broker;
		
		// Initialize connections for the selected broker
		initializeBrokerConnections();
		
		this.iconCellRenderer = new IconicTableCellRenderer();
	}
	
	/**
	 * Handles master password: checks if encrypted passwords exist and prompts user if needed.
	 * Master password can be provided via command line or GUI prompt.
	 */
	private void handleMasterPassword() throws BrokerException {
		try {
			// Check if config file has encrypted passwords
			if (thisCfg.hasEncryptedPasswords()) {
				char[] masterPw = null;
				
				// Try to get master password from command line first (if available)
				if (masterPasswordFromCommandLine != null && !masterPasswordFromCommandLine.isEmpty()) {
					masterPw = masterPasswordFromCommandLine.toCharArray();
				} else {
					// Prompt user for master password via GUI
					masterPw = promptForMasterPassword();
					if (masterPw == null || masterPw.length == 0) {
						throw new BrokerException("Master password is required to decrypt encrypted passwords. Application will exit.");
					}
				}
				
				// Set master password in config
				thisCfg.setMasterPassword(masterPw);
				
				// Clear the array from memory after use
				java.util.Arrays.fill(masterPw, '\0');
			}
		} catch (BrokerException e) {
			throw e;
		} catch (Exception e) {
			throw new BrokerException("Error checking for encrypted passwords: " + e.getMessage());
		}
	}
	
	/**
	 * Prompts user for master password via a secure password dialog.
	 * @return Master password as char array, or null if cancelled
	 */
	private char[] promptForMasterPassword() {
		JPasswordField passwordField = new JPasswordField(20);
		passwordField.setEchoChar('*');
		
		Object[] message = {
			"Encrypted passwords detected in configuration file.",
			"Please enter the master password to decrypt them:",
			passwordField
		};
		
		int option = JOptionPane.showConfirmDialog(
			null,
			message,
			"Master Password Required",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.QUESTION_MESSAGE
		);
		
		if (option == JOptionPane.OK_OPTION) {
			return passwordField.getPassword();
		}
		
		return null;
	}
	
	private void initializeBrokerConnections() throws BrokerException {
		// Test SEMP connections upfront
		try {
			sempV2ConfigClient = SempClient.SempClientFactory(eApi.eConfig, broker.sempHost, broker.sempAdminUser,
					broker.sempAdminPw);
			sempV2ActionClient = SempClient.SempClientFactory(eApi.eAction, broker.sempHost, broker.sempAdminUser,
					broker.sempAdminPw);
			sempV2MonitorClient = SempClient.SempClientFactory(eApi.eMonitor, broker.sempHost, broker.sempAdminUser,
					broker.sempAdminPw);
			queues = sempV2ConfigClient.getAllQueueNames(broker.msgVpnName);
			Collections.sort(queues);
			logger.info("SEMP connection successful. Found " + queues.size() + " queues.");
		} catch (SempException e) {
			String errorMsg = "SEMP connection failed: " + e.getMessage();
			logger.error(errorMsg, e);
			throw new BrokerException(errorMsg);
		}
		
		// Test SMF connection upfront by creating a test browser instance
		try {
			if (queues.size() > 0) {
				// Test SMF connection with first available queue
				// This will throw exception if SMF connection fails
				PaginatedCachingBrowser testBrowser = new PaginatedCachingBrowser(broker, queues.get(0), 1);
				// Note: PaginatedCachingBrowser manages its own connection lifecycle
				// The connection will be closed when the browser is garbage collected
				logger.info("SMF connection test successful.");
			} else {
				logger.warn("No queues found to test SMF connection. SMF connection will be tested when browsing a queue.");
			}
		} catch (Exception e) {
			// Catch any exception (BrokerException, JCSMPException, etc.) from SMF connection
			String errorMsg = "SMF (Messaging) connection failed: " + e.getMessage() + 
				"\n\nSEMP connection is working, but SMF connection failed. " +
				"You will be able to see the queue list, but browsing messages will not work. " +
				"Please check your messaging credentials and network connectivity.";
			logger.error(errorMsg, e);
			// Show error dialog before launching UI
			javax.swing.SwingUtilities.invokeLater(() -> {
				JOptionPane.showMessageDialog(null, 
					errorMsg,
					"SMF Connection Failed",
					JOptionPane.ERROR_MESSAGE);
			});
			// Don't throw exception - allow UI to launch so user can see queue list
			// The error dialog will inform them of the issue
		}
	}
	
	/**
	 * Get font from config or use FlatLaf default
	 * @param size Font size
	 * @param style Font style (Font.PLAIN, Font.BOLD, etc.)
	 * @return Font instance
	 */
	private Font getFont(int size, int style) {
		if (thisCfg.fontFamily != null && !thisCfg.fontFamily.isEmpty()) {
			return new Font(thisCfg.fontFamily, style, size);
		}
		// Use FlatLaf default font (OS-agnostic)
		return UIManager.getFont("Label.font").deriveFont(style, size);
	}

	private Object[][] getTableData(List<String> queueNames) {
		ImageIcon qIcon = new ImageIcon("config/queueSm.png");
		Object[][] data = new Object[queueNames.size()][];
		for (int i = 0; i < queueNames.size(); i++) {
			//ArrayList<String> row = queueNames.get(i);
			String q = queueNames.get(i);
			data[i] = new Object[2];
			data[i][0] = qIcon;
			data[i][1] = q;
		}
		return data;

	}

	private void run() {
		// Create the frame
		String versionStr = thisCfg != null ? thisCfg.version : "v-nram-exp-cc-2.0";
		frame = new JFrame("SolQ!BAM - Solace Queue Browser and Manger - " + versionStr);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setSize(1200, 800);
			frame.setLayout(new BorderLayout());

			ImageIcon icon = new ImageIcon("config/queueBrowserIcon.png");
			Image image = icon.getImage();
			frame.setIconImage(image);
			
			// Resize icon to half size for display in header
			ImageIcon headerIcon = new ImageIcon(image.getScaledInstance(icon.getIconWidth() / 2, icon.getIconHeight() / 2, Image.SCALE_SMOOTH));

			Object[][] data = getTableData(queues);// new String[][] {};
			String[] columnNames = { "", "Queues"};

			// Create the table model
			tableModel = new DefaultTableModel(data, columnNames) {
				@Override
				public boolean isCellEditable(int row, int column) {
					return false; // Disable editing for all cells
				}
			};

			// Create the table with the table model
			table = new JTable(tableModel);
			table.setRowHeight(33);
			table.setFillsViewportHeight(true);
			// Set font for table cells to match queue details panel
			String tableFontFamily = (thisCfg.fontFamily != null && !thisCfg.fontFamily.isEmpty()) ? thisCfg.fontFamily : "Serif";
			table.setFont(new Font(tableFontFamily, Font.PLAIN, 16));
			table.getTableHeader().setFont(new Font(tableFontFamily, Font.PLAIN, 16));
			table.setDefaultRenderer(Object.class, new AlternatingRowColorRenderer());
			table.getColumnModel().getColumn(0).setCellRenderer(iconCellRenderer);
			table.getColumnModel().getColumn(0).setMaxWidth(48);
			// Use ListSelectionListener for reliable single-click selection
			// This handles selection changes regardless of how they occur (mouse, keyboard, programmatic)
			table.getSelectionModel().addListSelectionListener(e -> {
				if (!e.getValueIsAdjusting()) { // Only process when selection is finalized
					int row = table.getSelectedRow();
					if (row >= 0 && row < table.getRowCount()) {
						onSelectQueue(table, row);
					}
				}
			});
			
			// Handle double-click to browse
			table.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0 && row < table.getRowCount()) {
						// Ensure table has focus for proper selection
						table.requestFocus();
						
						// Handle double-click
						if (e.getClickCount() == 2) {
							System.out.println("Double-clicked row: " + row);
							try {
								// Ensure selection is set before browsing
								if (table.getSelectedRow() != row) {
									table.setRowSelectionInterval(row, row);
								}
								onBrowse(selectedQueue, frame);
							} catch (JCSMPException | SempException e1 ) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}
					}
				}
			});
	        table.setTransferHandler(new QueueMessageTransferReceiverHandler(this, "target-Q"));
	        table.getActionMap().put("upArrow", new AbstractAction() {
				public void actionPerformed(ActionEvent e) {
					int row = table.getSelectedRow();
					if (row > 0) {
						row--;
						table.setRowSelectionInterval(row, row);
						onSelectQueue(table, row);
					}
				}
			});

			// DOWN arrow key binding
			table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("DOWN"),"downArrow");
			table.getActionMap().put("downArrow", new AbstractAction() {
				public void actionPerformed(ActionEvent e) {
					int row = table.getSelectedRow();
					int rowCount = table.getRowCount();

					if (row < (rowCount - 1)) {
						row++;
						table.setRowSelectionInterval(row, row);
						onSelectQueue(table, row);
					}
				}
			});
			
			JPanel listPanel = new JPanel(new BorderLayout());
			listPanel.setPreferredSize(new Dimension(400, listPanel.getPreferredSize().height)); // Set the preferred width
			
			JScrollPane scrollingList = new JScrollPane(table);
			scrollingList.setBorder(new EmptyBorder(4, 4, 4, 4)); // Top, Left, Bottom, Right
			listPanel.add(scrollingList, BorderLayout.CENTER);

			detailsLabel = new JLabel();// TextArea();
			// Use system default font for better cross-platform appearance (especially on Windows)
			// Falls back to SansSerif if config font is not set, which looks more natural than Serif
			String detailsFontFamily = (thisCfg.fontFamily != null && !thisCfg.fontFamily.isEmpty()) ? thisCfg.fontFamily : Font.SANS_SERIF;
			detailsLabel.setFont(new Font(detailsFontFamily, Font.PLAIN, 14)); // Changed from ITALIC to PLAIN, reduced size slightly
			
			// Use same font family as the label for consistency
			String placeholderFontFamily = (thisCfg.fontFamily != null && !thisCfg.fontFamily.isEmpty()) ? thisCfg.fontFamily : Font.SANS_SERIF;
			detailsLabel.setText("<html>"
	                + "<div style='width: 280px; text-align: left; vertical-align:top; font-family: " + placeholderFontFamily + ";'>"
	                + "<p>Select a queue on the left to see details.</p>"
	                + "</div>"
	                + "</html>");
			detailsLabel.setVerticalAlignment(SwingConstants.TOP);
	        
	        JPanel detailsPanel = new JPanel();
	        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));

	        qIconlabel = new JLabel(new ImageIcon("config/queue.png"));
	        qIconlabel.setVisible(false);

	        //JPanel iconTopPanel = new JPanel(new BorderLayout());
	        //iconTopPanel.add(qIconlabel, BorderLayout.CENTER);

	        detailsPanel.add(qIconlabel);
	        detailsPanel.add(detailsLabel);
	        
			JScrollPane textScrollPane = new JScrollPane(detailsPanel);
			textScrollPane.setBorder(new EmptyBorder(4, 4, 4, 4)); // Top, Left, Bottom, Right

			buttonPanel = new JPanel();
			buttonPanel.setLayout(new BorderLayout());
			browseButton = new JButton("Browse");
			browseButton.setEnabled(false);
			browseButton.setBackground(new Color(240, 230, 255)); // Soft purple background
			browseButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						onBrowse(selectedQueue, frame);
					} catch (SempException | JCSMPException e1) {
						e1.printStackTrace();
					}
				}
			});

			copyAllButton = new JButton("Copy ALL");
			copyAllButton.setEnabled(false);
			copyAllButton.setBackground(new Color(220, 235, 255)); // Soft blue background
			copyAllButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					onCopyAll(selectedQueue, frame);
				}
			});

			moveAllButton = new JButton("Move ALL");
			moveAllButton.setEnabled(false);
			moveAllButton.setBackground(new Color(255, 245, 220)); // Soft yellow background
			moveAllButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					onMoveAll(selectedQueue, frame);
				}
			});

			deleteAllButton = new JButton("Delete ALL");
			deleteAllButton.setEnabled(false);
			deleteAllButton.setBackground(new Color(255, 220, 220)); // Soft red background
			deleteAllButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					onDeleteAll(selectedQueue, frame);
				}

			});

			refreshButton = new JButton("Refresh");
			refreshButton.setEnabled(true);
			refreshButton.setBackground(new Color(220, 245, 255)); // Soft cyan background
			refreshButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					onRefresh();
				}
			});

			// Create Exit button with soft red background
			String headerFontFamily = (thisCfg.fontFamily != null && !thisCfg.fontFamily.isEmpty()) ? thisCfg.fontFamily : "Serif";
			exitButton = new JButton("Exit");
			exitButton.setBackground(new Color(220, 150, 150)); // Very soft red
			exitButton.setForeground(Color.WHITE);
			exitButton.setFont(new Font(headerFontFamily, Font.BOLD, 14));
			exitButton.setPreferredSize(new Dimension(80, 30));
			exitButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					// Clean exit - dispose frame and exit application
					frame.dispose();
					System.exit(0);
				}
			});

			this.addButtons(buttonPanel);

			JLabel iconLabel = new JLabel("");
			iconLabel.setIcon(headerIcon);

			// Use Serif font like the queue details panel for consistency
			// headerFontFamily is already defined above for the Exit button
			
			// Create top panel with horizontal layout: icon, broker selector, and connection info
			JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); // 8px horizontal gap, 4px vertical gap
			
			// Add icon
			topPanel.add(iconLabel);
			
			// Create broker selection combo box if multiple brokers are available
			if (thisCfg.getBrokers().size() > 1) {
				JLabel brokerLabel = new JLabel("Event Broker:");
				brokerLabel.setFont(new Font(headerFontFamily, Font.PLAIN, 16));
				topPanel.add(brokerLabel);
				
				// Create combo box with broker names
				String[] brokerNames = new String[thisCfg.getBrokers().size()];
				for (int i = 0; i < thisCfg.getBrokers().size(); i++) {
					brokerNames[i] = thisCfg.getBrokers().get(i).name;
				}
				brokerComboBox = new JComboBox<String>(brokerNames);
				brokerComboBox.setSelectedIndex(thisCfg.getSelectedBrokerIndex());
				brokerComboBox.setFont(new Font(headerFontFamily, Font.PLAIN, 16));
				brokerComboBox.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						System.out.println("BROKER COMBO BOX ACTION EVENT TRIGGERED");
						System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						logger.info("BROKER COMBO BOX ACTION EVENT TRIGGERED");
						logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						
						@SuppressWarnings("unchecked")
						JComboBox<String> cb = (JComboBox<String>) e.getSource();
						String selectedBrokerName = (String) cb.getSelectedItem();
						System.out.println("Selected broker name from combo box: " + selectedBrokerName);
						logger.info("Selected broker name from combo box: " + selectedBrokerName);
						
						// Save the previous broker index BEFORE attempting switch
						// This ensures we can restore it if the switch fails
						int previousIndex = thisCfg.getSelectedBrokerIndex();
						Broker previousBroker = broker; // Save current broker reference
						logger.info("Previous broker index: " + previousIndex);
						logger.info("Previous broker name: " + (previousBroker != null ? previousBroker.name : "null"));
						
						try {
							logger.info("Calling switchBroker('" + selectedBrokerName + "')...");
							switchBroker(selectedBrokerName);
							logger.info("switchBroker() returned successfully");
							// If successful, the label should already be updated in switchBroker()
							logger.info("Broker switch completed successfully in action listener");
						} catch (BrokerException ex) {
							logger.error(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
							logger.error("BROKER EXCEPTION CAUGHT IN ACTION LISTENER");
							logger.error(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
							logger.error("Exception message: " + ex.getMessage());
							if (ex.getCause() != null) {
								logger.error("Exception cause: " + ex.getCause().getMessage());
							}
							logger.error("Broker switch failed: " + ex.getMessage(), ex);
							JOptionPane.showMessageDialog(frame, 
								"Failed to switch broker: " + ex.getMessage(),
								"Broker Switch Failed",
								JOptionPane.ERROR_MESSAGE);
							
							// Restore previous broker state
							logger.info("Attempting to restore previous broker state...");
							logger.info("  - previousIndex: " + previousIndex);
							logger.info("  - previousBroker: " + (previousBroker != null ? previousBroker.name : "null"));
							logger.info("  - greetingLine0: " + (greetingLine0 != null ? "not null" : "null"));
							logger.info("  - broker: " + (broker != null ? broker.name : "null"));
							
							try {
								// Restore config to previous broker
								logger.info("Restoring config to previous broker index: " + previousIndex);
								thisCfg.setSelectedBrokerIndex(previousIndex);
								broker = previousBroker; // Restore broker reference
								logger.info("Broker restored. broker.name: " + (broker != null ? broker.name : "null"));
								
								// Reset combo box to previous selection
								logger.info("Resetting combo box to index: " + previousIndex);
								cb.setSelectedIndex(previousIndex);
								
								// Update label to show restored broker info
								if (greetingLine0 != null && broker != null) {
									logger.info("Updating label with restored broker info...");
									String brokerFqdn = broker.fqdn();
									if (brokerFqdn == null || brokerFqdn.isEmpty()) {
										brokerFqdn = broker.sempHost;
									}
									String brokerInfo = "Broker: " + brokerFqdn + " | Service: " + broker.msgVpnName + " | SEMP User: " + broker.sempAdminUser + " | Client User: " + broker.messagingClientUsername;
									logger.info("Restored broker info text: " + brokerInfo);
									// Use HTML to enable text wrapping with two-line format
									String htmlBrokerInfo = formatBrokerInfoWithWrapping(
										brokerFqdn,
										broker.msgVpnName != null ? broker.msgVpnName : "",
										broker.sempAdminUser != null ? broker.sempAdminUser : "",
										broker.messagingClientUsername != null ? broker.messagingClientUsername : ""
									);
									greetingLine0.setText(htmlBrokerInfo);
									greetingLine0.setVisible(true);
									greetingLine0.setToolTipText(brokerInfo);
									logger.info("Label updated. Text: " + greetingLine0.getText() + ", Visible: " + greetingLine0.isVisible());
									SwingUtilities.invokeLater(() -> {
										greetingLine0.revalidate();
										greetingLine0.repaint();
										logger.info("Label revalidated and repainted");
									});
									logger.info("Successfully restored broker info display after switch failure");
								} else {
									logger.error("Cannot restore broker info - greetingLine0=" + greetingLine0 + ", broker=" + broker);
								}
							} catch (Exception restoreEx) {
								logger.error("Failed to restore broker info after switch failure: " + restoreEx.getMessage(), restoreEx);
								restoreEx.printStackTrace();
							}
							logger.info("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
						}
					}
				});
				topPanel.add(brokerComboBox);
			}
			
			// Create single line connection info: Broker: host | Service: VPN | SEMP User: user | Client User: user
			// Ensure broker is not null and has valid data
			System.out.println("========================================");
			System.out.println("CREATING INITIAL BROKER INFO LABEL");
			System.out.println("========================================");
			System.out.println("broker is null: " + (broker == null));
			System.out.println("thisCfg.brokers.size(): " + thisCfg.brokers.size());
			System.out.println("thisCfg.getSelectedBrokerIndex(): " + thisCfg.getSelectedBrokerIndex());
			
			if (broker == null) {
				System.out.println("ERROR: Broker is null during UI initialization!");
				logger.error("Broker is null during UI initialization!");
				if (thisCfg.brokers.size() > 0) {
					broker = thisCfg.brokers.get(0); // Fallback to first broker
					System.out.println("Using fallback broker: " + broker.name);
				} else {
					System.out.println("ERROR: No brokers available!");
					logger.error("No brokers available!");
					broker = new Broker(); // Create empty broker to prevent NPE
				}
			}
			
			// Create label with broker info - use helper method logic
			try {
				System.out.println("Broker details:");
				System.out.println("  - broker.name: " + broker.name);
				System.out.println("  - broker.sempHost: " + broker.sempHost);
				System.out.println("  - broker.msgVpnName: " + broker.msgVpnName);
				System.out.println("  - broker.sempAdminUser: " + broker.sempAdminUser);
				System.out.println("  - broker.messagingClientUsername: " + broker.messagingClientUsername);
				
				logger.info("Creating initial broker info label - broker.name=" + broker.name + 
					", sempHost=" + broker.sempHost + 
					", msgVpnName=" + broker.msgVpnName + 
					", sempAdminUser=" + broker.sempAdminUser + 
					", messagingClientUsername=" + broker.messagingClientUsername);
				
				String brokerFqdn = broker.fqdn();
				System.out.println("  - broker.fqdn(): " + brokerFqdn);
				if (brokerFqdn == null || brokerFqdn.isEmpty()) {
					System.out.println("WARNING: Broker FQDN is empty, using sempHost: " + broker.sempHost);
					logger.warn("Broker FQDN is empty, using sempHost: " + broker.sempHost);
					brokerFqdn = broker.sempHost != null ? broker.sempHost : "";
				}
				
				// Format broker info as two lines
				String brokerInfo = "Broker: " + brokerFqdn + 
					" | Service: " + (broker.msgVpnName != null ? broker.msgVpnName : "") + 
					" | SEMP User: " + (broker.sempAdminUser != null ? broker.sempAdminUser : "") + 
					" | Client User: " + (broker.messagingClientUsername != null ? broker.messagingClientUsername : "");
				
				System.out.println("Broker info text: " + brokerInfo);
				System.out.println("Creating JLabel with two-line format");
				
				// Use HTML to enable text wrapping with two-line format
				String htmlBrokerInfo = formatBrokerInfoWithWrapping(
					brokerFqdn,
					broker.msgVpnName != null ? broker.msgVpnName : "",
					broker.sempAdminUser != null ? broker.sempAdminUser : "",
					broker.messagingClientUsername != null ? broker.messagingClientUsername : ""
				);
				greetingLine0 = new JLabel(htmlBrokerInfo);
				greetingLine0.setFont(new Font(headerFontFamily, Font.PLAIN, 16));
				greetingLine0.setVisible(true);
				// Set tooltip to show full text on hover
				greetingLine0.setToolTipText(brokerInfo);
				
				System.out.println("Label created. Text: " + greetingLine0.getText());
				System.out.println("Label visible: " + greetingLine0.isVisible());
				System.out.println("Label parent: " + greetingLine0.getParent());
				
				logger.info("Initial broker info display created: " + brokerInfo);
			} catch (Exception e) {
				System.out.println("EXCEPTION creating initial broker info label: " + e.getMessage());
				e.printStackTrace();
				logger.error("Failed to create initial broker info label: " + e.getMessage(), e);
				// Create label with fallback info
				String fallbackInfo = "Broker: " + (broker.name != null ? broker.name : "Unknown");
				// Use HTML to enable text wrapping
				String htmlFallbackInfo = formatBrokerInfoWithWrapping(fallbackInfo);
				greetingLine0 = new JLabel(htmlFallbackInfo);
				greetingLine0.setFont(new Font(headerFontFamily, Font.PLAIN, 16));
				greetingLine0.setVisible(true);
				System.out.println("Created fallback label: " + fallbackInfo);
			}
			
			System.out.println("Adding label to topPanel...");
			topPanel.add(new JLabel("  ")); // Add some space before connection info
			topPanel.add(greetingLine0);
			System.out.println("Label added to topPanel. Label parent: " + greetingLine0.getParent());
			System.out.println("topPanel component count: " + topPanel.getComponentCount());
			System.out.println("========================================");
			
			// greetingLine1 is no longer needed, but keep it for switchBroker() method compatibility
			greetingLine1 = new JLabel(""); // Empty label for compatibility

			JPanel rightPanel = new JPanel();
			rightPanel.setLayout(new BorderLayout());
			rightPanel.add(textScrollPane, BorderLayout.CENTER);

			frame.add(topPanel, BorderLayout.NORTH);
			frame.add(listPanel, BorderLayout.WEST);
			frame.add(rightPanel, BorderLayout.CENTER);
			frame.add(buttonPanel, BorderLayout.SOUTH);

			frame.setVisible(true);
	}
	/**
	 * Helper method to format broker info text with HTML wrapping
	 * Formats broker info into two lines:
	 * Line 1: Broker and Service
	 * Line 2: Management User and Message User
	 * @param brokerFqdn The broker FQDN/hostname
	 * @param msgVpnName The message VPN name
	 * @param sempAdminUser The SEMP admin user
	 * @param messagingClientUsername The messaging client username
	 * @return HTML formatted string with two-line layout
	 */
	private String formatBrokerInfoWithWrapping(String brokerFqdn, String msgVpnName, String sempAdminUser, String messagingClientUsername) {
		// Escape HTML special characters to prevent rendering issues
		String escapedFqdn = (brokerFqdn != null ? brokerFqdn : "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		String escapedVpn = (msgVpnName != null ? msgVpnName : "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		String escapedSempUser = (sempAdminUser != null ? sempAdminUser : "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		String escapedMsgUser = (messagingClientUsername != null ? messagingClientUsername : "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		
		// Format as two lines
		String line1 = "Broker: " + escapedFqdn + " | Service: " + escapedVpn;
		String line2 = "Mgmt. User: " + escapedSempUser + " | Msg. User: " + escapedMsgUser;
		
		// Use HTML to enable text wrapping with a maximum width
		// The max-width will cause the text to wrap to multiple lines if needed
		return "<html><div style='max-width: 600px; word-wrap: break-word; white-space: normal;'>" + 
			line1 + "<br>" + line2 + 
			"</div></html>";
	}
	
	/**
	 * Legacy method for backward compatibility - formats single-line broker info
	 * @param brokerInfo The broker info text to format
	 * @return HTML formatted string with wrapping enabled
	 */
	private String formatBrokerInfoWithWrapping(String brokerInfo) {
		// Escape HTML special characters to prevent rendering issues
		String escaped = brokerInfo.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		// Use a reasonable max-width that will wrap on most screens (600px should work well)
		// The word-wrap ensures long words break if needed
		return "<html><div style='max-width: 600px; word-wrap: break-word; white-space: normal;'>" + 
			escaped + 
			"</div></html>";
	}
	
	/**
	 * Helper method to update the broker info label
	 * Must be called on EDT thread
	 */
	private void updateBrokerInfoLabel() {
		System.out.println("updateBrokerInfoLabel() called");
		System.out.println("  - greetingLine0 is null: " + (greetingLine0 == null));
		System.out.println("  - broker is null: " + (broker == null));
		
		if (greetingLine0 == null) {
			System.out.println("ERROR: greetingLine0 is null in updateBrokerInfoLabel!");
			logger.error("greetingLine0 is null in updateBrokerInfoLabel!");
			return;
		}
		if (broker == null) {
			System.out.println("ERROR: broker is null in updateBrokerInfoLabel!");
			logger.error("broker is null in updateBrokerInfoLabel!");
			return;
		}
		
		try {
			System.out.println("Broker details in updateBrokerInfoLabel:");
			System.out.println("  - broker.name: " + broker.name);
			System.out.println("  - broker.sempHost: " + broker.sempHost);
			System.out.println("  - broker.msgVpnName: " + broker.msgVpnName);
			System.out.println("  - broker.sempAdminUser: " + broker.sempAdminUser);
			System.out.println("  - broker.messagingClientUsername: " + broker.messagingClientUsername);
			
			logger.info("Updating broker info label - broker.name=" + broker.name + 
				", sempHost=" + broker.sempHost + 
				", msgVpnName=" + broker.msgVpnName + 
				", sempAdminUser=" + broker.sempAdminUser + 
				", messagingClientUsername=" + broker.messagingClientUsername);
			
			String brokerFqdn = broker.fqdn();
			System.out.println("  - broker.fqdn(): " + brokerFqdn);
			if (brokerFqdn == null || brokerFqdn.isEmpty()) {
				System.out.println("WARNING: Broker FQDN is empty, using sempHost: " + broker.sempHost);
				logger.warn("Broker FQDN is empty, using sempHost: " + broker.sempHost);
				brokerFqdn = broker.sempHost;
			}
			
			// Format broker info as two lines
			String brokerInfo = "Broker: " + brokerFqdn + 
				" | Service: " + (broker.msgVpnName != null ? broker.msgVpnName : "") + 
				" | SEMP User: " + (broker.sempAdminUser != null ? broker.sempAdminUser : "") + 
				" | Client User: " + (broker.messagingClientUsername != null ? broker.messagingClientUsername : "");
			
			System.out.println("Setting label text to two-line format");
			System.out.println("Label text before update: " + greetingLine0.getText());
			System.out.println("Label visible before update: " + greetingLine0.isVisible());
			System.out.println("Label parent before update: " + greetingLine0.getParent());
			
			// Use HTML to enable text wrapping with two-line format
			String htmlBrokerInfo = formatBrokerInfoWithWrapping(
				brokerFqdn,
				broker.msgVpnName != null ? broker.msgVpnName : "",
				broker.sempAdminUser != null ? broker.sempAdminUser : "",
				broker.messagingClientUsername != null ? broker.messagingClientUsername : ""
			);
			greetingLine0.setText(htmlBrokerInfo);
			greetingLine0.setVisible(true);
			// Set tooltip to show full text on hover
			greetingLine0.setToolTipText(brokerInfo);
			greetingLine0.revalidate();
			greetingLine0.repaint();
			
			System.out.println("Label text after update: " + greetingLine0.getText());
			System.out.println("Label visible after update: " + greetingLine0.isVisible());
			System.out.println("Label parent after update: " + greetingLine0.getParent());
			
			logger.info("Successfully updated broker info label: " + brokerInfo);
		} catch (Exception e) {
			System.out.println("EXCEPTION in updateBrokerInfoLabel: " + e.getMessage());
			e.printStackTrace();
			logger.error("Exception in updateBrokerInfoLabel: " + e.getMessage(), e);
			// Still try to show something
			try {
				String fallbackInfo = "Broker: " + (broker.name != null ? broker.name : "Unknown") + 
					" | Service: " + (broker.msgVpnName != null ? broker.msgVpnName : "");
				// Use HTML to enable text wrapping
				String htmlFallbackInfo = formatBrokerInfoWithWrapping(fallbackInfo);
				greetingLine0.setText(htmlFallbackInfo);
				greetingLine0.setVisible(true);
				System.out.println("Set fallback broker info: " + fallbackInfo);
				logger.info("Set fallback broker info: " + fallbackInfo);
			} catch (Exception e2) {
				System.out.println("EXCEPTION setting fallback: " + e2.getMessage());
				e2.printStackTrace();
				logger.error("Failed to set fallback broker info: " + e2.getMessage(), e2);
			}
		}
	}
	
	private void switchBroker(String brokerName) throws BrokerException {
		System.out.println("========================================");
		System.out.println("SWITCHING BROKER: " + brokerName);
		System.out.println("========================================");
		System.out.println("Current broker before switch: " + (broker != null ? broker.name : "null"));
		System.out.println("Current selectedBrokerIndex before switch: " + thisCfg.getSelectedBrokerIndex());
		logger.info("========================================");
		logger.info("SWITCHING BROKER: " + brokerName);
		logger.info("========================================");
		logger.info("Current broker before switch: " + (broker != null ? broker.name : "null"));
		logger.info("Current selectedBrokerIndex before switch: " + thisCfg.getSelectedBrokerIndex());
		
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
		// Update config to use the selected broker first
		try {
			System.out.println("Calling setSelectedBrokerByName('" + brokerName + "')...");
			logger.info("Calling setSelectedBrokerByName('" + brokerName + "')...");
			thisCfg.setSelectedBrokerByName(brokerName);
			broker = thisCfg.broker;
			System.out.println("Broker object set successfully. New broker details:");
			System.out.println("  - broker.name: " + (broker != null ? broker.name : "null"));
			System.out.println("  - broker.sempHost: " + (broker != null ? broker.sempHost : "null"));
			System.out.println("  - broker.msgVpnName: " + (broker != null ? broker.msgVpnName : "null"));
			System.out.println("  - broker.sempAdminUser: " + (broker != null ? broker.sempAdminUser : "null"));
			System.out.println("  - broker.messagingClientUsername: " + (broker != null ? broker.messagingClientUsername : "null"));
			System.out.println("  - broker.messagingHost: " + (broker != null ? broker.messagingHost : "null"));
			System.out.println("  - broker.fqdn(): " + (broker != null ? broker.fqdn() : "null"));
			System.out.println("New selectedBrokerIndex: " + thisCfg.getSelectedBrokerIndex());
			logger.info("Broker object set successfully. New broker details:");
			logger.info("  - broker.name: " + (broker != null ? broker.name : "null"));
			logger.info("  - broker.sempHost: " + (broker != null ? broker.sempHost : "null"));
			logger.info("  - broker.msgVpnName: " + (broker != null ? broker.msgVpnName : "null"));
			logger.info("  - broker.sempAdminUser: " + (broker != null ? broker.sempAdminUser : "null"));
			logger.info("  - broker.messagingClientUsername: " + (broker != null ? broker.messagingClientUsername : "null"));
			logger.info("  - broker.messagingHost: " + (broker != null ? broker.messagingHost : "null"));
			logger.info("  - broker.fqdn(): " + (broker != null ? broker.fqdn() : "null"));
			logger.info("New selectedBrokerIndex: " + thisCfg.getSelectedBrokerIndex());
		} catch (BrokerException e) {
			logger.error("Failed to set selected broker by name: " + e.getMessage(), e);
			frame.setCursor(Cursor.getDefaultCursor());
			throw e;
		}
		
		// Update UI labels immediately (before connection attempt)
		// This ensures broker info is displayed even if connection fails
		// Execute on EDT to ensure UI updates happen synchronously
		logger.info("Updating broker info label...");
		logger.info("  - greetingLine0 is null: " + (greetingLine0 == null));
		logger.info("  - broker is null: " + (broker == null));
		logger.info("  - Current thread is EDT: " + SwingUtilities.isEventDispatchThread());
		
		if (greetingLine0 == null) {
			logger.error("greetingLine0 is null! Cannot update broker info display.");
		} else if (broker == null) {
			logger.error("broker is null! Cannot update broker info display.");
		} else {
			// Update label synchronously on EDT
			if (SwingUtilities.isEventDispatchThread()) {
				logger.info("Updating label on EDT thread directly...");
				updateBrokerInfoLabel();
			} else {
				logger.info("Not on EDT thread, using invokeAndWait...");
				try {
					SwingUtilities.invokeAndWait(() -> {
						logger.info("Inside invokeAndWait callback, updating label...");
						updateBrokerInfoLabel();
					});
					logger.info("invokeAndWait completed successfully");
				} catch (Exception e) {
					logger.error("Failed to update broker info on EDT: " + e.getMessage(), e);
					// Fallback: try async update
					logger.info("Falling back to invokeLater...");
					SwingUtilities.invokeLater(() -> {
						logger.info("Inside invokeLater callback, updating label...");
						updateBrokerInfoLabel();
					});
				}
			}
		}
		
		try {
			// Reinitialize connections for the new broker
			logger.info("Starting broker connection initialization...");
			initializeBrokerConnections();
			logger.info("Broker connection initialization completed successfully");
			
			// Refresh queue list
			DefaultTableModel model = (DefaultTableModel) table.getModel();
			model.setRowCount(0); // Clears all existing rows
			
			Object[][] newData = getTableData(queues);
			for (Object[] rowData : newData) {
				model.addRow(rowData); // Add new rows
			}
			
			// Clear selection and disable buttons
			selectedQueue = "";
			selectedQueueMsgCount = 0;
			browseButton.setEnabled(false);
			copyAllButton.setEnabled(false);
			deleteAllButton.setEnabled(false);
			moveAllButton.setEnabled(false);
			
			// Reset details label
			String placeholderFontFamily = (thisCfg.fontFamily != null && !thisCfg.fontFamily.isEmpty()) ? thisCfg.fontFamily : Font.SANS_SERIF;
			detailsLabel.setText("<html>"
					+ "<div style='width: 280px; text-align: left; vertical-align:top; font-family: " + placeholderFontFamily + ";'>"
					+ "<p>Select a queue on the left to see details.</p>"
					+ "</div>"
					+ "</html>");
			qIconlabel.setVisible(false);
			
			logger.info("========================================");
			logger.info("BROKER SWITCH COMPLETED SUCCESSFULLY: " + brokerName);
			logger.info("========================================");
			
		} catch (BrokerException e) {
			logger.error("========================================");
			logger.error("BROKER SWITCH FAILED (BrokerException): " + brokerName);
			logger.error("Error: " + e.getMessage());
			if (e.getCause() != null) {
				logger.error("Cause: " + e.getCause().getMessage());
			}
			logger.error("Current broker after failure: " + (broker != null ? broker.name : "null"));
			logger.error("greetingLine0 is null: " + (greetingLine0 == null));
			if (greetingLine0 != null) {
				logger.error("greetingLine0 text: " + greetingLine0.getText());
				logger.error("greetingLine0 visible: " + greetingLine0.isVisible());
			}
			logger.error("========================================");
			frame.setCursor(Cursor.getDefaultCursor());
			// Label should already be updated, but ensure it's visible
			if (greetingLine0 != null) {
				greetingLine0.setVisible(true);
			}
			throw e;
		} catch (Exception e) {
			logger.error("========================================");
			logger.error("BROKER SWITCH FAILED (Exception): " + brokerName);
			logger.error("Error: " + e.getMessage());
			logger.error("Exception type: " + e.getClass().getName());
			if (e.getCause() != null) {
				logger.error("Cause: " + e.getCause().getMessage());
				logger.error("Cause type: " + e.getCause().getClass().getName());
			}
			logger.error("Current broker after failure: " + (broker != null ? broker.name : "null"));
			logger.error("greetingLine0 is null: " + (greetingLine0 == null));
			if (greetingLine0 != null) {
				logger.error("greetingLine0 text: " + greetingLine0.getText());
				logger.error("greetingLine0 visible: " + greetingLine0.isVisible());
			}
			logger.error("========================================");
			frame.setCursor(Cursor.getDefaultCursor());
			// Label should already be updated, but ensure it's visible
			if (greetingLine0 != null) {
				greetingLine0.setVisible(true);
			}
			String errorMsg = "Failed to switch broker: " + e.getMessage();
			if (e.getCause() != null) {
				errorMsg += " - Cause: " + e.getCause().getMessage();
			}
			throw new BrokerException(errorMsg);
		} finally {
			logger.info("switchBroker() finally block - ensuring label is visible");
			frame.setCursor(Cursor.getDefaultCursor());
			// Ensure label is visible in finally block
			if (greetingLine0 != null) {
				greetingLine0.setVisible(true);
				logger.info("greetingLine0 text in finally: " + greetingLine0.getText());
				logger.info("greetingLine0 visible in finally: " + greetingLine0.isVisible());
			} else {
				logger.error("greetingLine0 is null in finally block!");
			}
		}
	}
	
	private void onRefresh() {
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
		SwingUtilities.invokeLater(() -> {

			DefaultTableModel model = (DefaultTableModel) table.getModel();
			model.setRowCount(0); // Clears all existing rows

			try {
				queues = sempV2ConfigClient.getAllQueueNames(broker.msgVpnName);
				Collections.sort(queues);

				Object[][] newData = getTableData(queues);// new String[][] {};
				for (Object[] rowData : newData) {
				    model.addRow(rowData); // Add new rows
				}

			} catch (SempException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			frame.setCursor(Cursor.getDefaultCursor());
		});
	}
	
	@Override
	public void onDrop(int row, DroppableMessage msg) {
		String qDroppedOn = (String) table.getValueAt(row, 1);
		System.out.println("Dropped on " + qDroppedOn);
		
		try {
			sempV2ActionClient.copy(broker.msgVpnName, msg.queue, qDroppedOn, msg.replicationId);
			System.out.println("Message copied to " + qDroppedOn);
			
			// test the delete
			msg.targetQueue = qDroppedOn;
			msg.source.onMessageWasMoved(msg);
			
		} catch (SempException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void onSelectQueue(JTable table, int row) {
		// Ensure row is valid and selected
		if (row < 0 || row >= table.getRowCount()) {
			return;
		}
		
		// Ensure the row is actually selected in the table
		if (table.getSelectedRow() != row) {
			table.setRowSelectionInterval(row, row);
		}
		
		selectedQueue = getSelectedQueue();
		if (selectedQueue == null) {
			return;
		}
		
		browseButton.setEnabled(true);
		copyAllButton.setEnabled(true);
		deleteAllButton.setEnabled(true);
		moveAllButton.setEnabled(true);
		
		try {
			this.onQueueNameSelected(selectedQueue, detailsLabel, buttonPanel);
		} catch (SempException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private String getSelectedQueue() {
//		ListItem item = (ListItem) listBox.getSelectedValue();
//		return item.text.trim();
		return(String) table.getValueAt(table.getSelectedRow(), 1);
	}
	private void addButtons(JPanel buttonPanel) {
		// Create left panel for main buttons
		JPanel leftButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		leftButtonPanel.add(browseButton);
		leftButtonPanel.add(new JLabel("  |  "));
		leftButtonPanel.add(copyAllButton);
		leftButtonPanel.add(moveAllButton);
		leftButtonPanel.add(deleteAllButton);
		leftButtonPanel.add(new JLabel("  |  "));
		leftButtonPanel.add(refreshButton);
		
		// Create right panel for Exit button
		JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		rightButtonPanel.add(exitButton);
		
		// Add panels to buttonPanel using BorderLayout
		buttonPanel.add(leftButtonPanel, BorderLayout.WEST);
		buttonPanel.add(rightButtonPanel, BorderLayout.EAST);
	}

	private void onBrowse(String queueName, JFrame frame) throws SempException, JCSMPException {
		String[] otherQueues = getListOfQueuesExceptCurrentlySelectedOne(queueName);
		try {
			BrowserDialog d = new BrowserDialog(this.sempV2ActionClient, this.broker, queueName, frame,
					selectedQueueMsgCount, otherQueues, thisCfg.downloadFolder, thisCfg);
			d.run();
		} catch (SempException e) {
			// Show error dialog if SMF connection fails
			String errorMsg = "Failed to open queue browser:\n\n" + e.getMessage() + 
				"\n\nPlease check your messaging credentials and network connectivity.";
			JOptionPane.showMessageDialog(frame, 
				errorMsg,
				"SMF Connection Failed",
				JOptionPane.ERROR_MESSAGE);
			throw e; // Re-throw to allow caller to handle if needed
		} catch (JCSMPException e) {
			// Catch JCSMP exceptions directly
			String errorMsg = "Failed to open queue browser:\n\n" +
				"Error: " + e.getClass().getSimpleName() + "\n" +
				"Message: " + e.getMessage() + "\n";
			Throwable cause = e.getCause();
			if (cause != null) {
				errorMsg += "\nRoot Cause: " + cause.getClass().getSimpleName() + "\n";
				errorMsg += "Root Cause Message: " + cause.getMessage() + "\n";
			}
			errorMsg += "\nPlease check your messaging credentials and network connectivity.";
			JOptionPane.showMessageDialog(frame, 
				errorMsg,
				"SMF Connection Failed",
				JOptionPane.ERROR_MESSAGE);
			throw e; // Re-throw to allow caller to handle if needed
		} catch (Exception e) {
			// Catch any other exceptions
			String errorMsg = "Failed to open queue browser:\n\n" +
				"Error: " + e.getClass().getSimpleName() + "\n" +
				"Message: " + e.getMessage() + "\n";
			Throwable cause = e.getCause();
			if (cause != null) {
				errorMsg += "\nRoot Cause: " + cause.getClass().getSimpleName() + "\n";
				errorMsg += "Root Cause Message: " + cause.getMessage() + "\n";
			}
			errorMsg += "\nPlease check your messaging credentials and network connectivity.";
			JOptionPane.showMessageDialog(frame, 
				errorMsg,
				"Error Opening Queue Browser",
				JOptionPane.ERROR_MESSAGE);
			if (e instanceof SempException) {
				throw (SempException) e;
			} else if (e instanceof JCSMPException) {
				throw (JCSMPException) e;
			}
		}
	}

	private String[] getListOfQueuesExceptCurrentlySelectedOne(String selectedQueue) {
		List<String> tempList = new ArrayList<String>();
		for (String oneStr : queues) {
			if (oneStr.equals(selectedQueue) == false) {
				tempList.add(oneStr);	
			}
		}
		String[] options = (String[]) tempList.toArray(new String[0]);
		return options;
	}

	private String pickAQueue(JFrame frame) {
		QueueSelectorDialog dlg = new QueueSelectorDialog();
		String[] options = this.getListOfQueuesExceptCurrentlySelectedOne(selectedQueue);
		String destQueue = dlg.selectQueues(frame, options);
		return destQueue;
	}
	private void onCopyAll(String selectedQueue, JFrame frame) {
		String destQueue = pickAQueue(frame);
		QueueActionWindow cp;
		try {
			cp = new QueueActionWindow(frame, broker, eAction.eCopy, sempV2ActionClient, sempV2MonitorClient,
					broker.msgVpnName, selectedQueue, destQueue, thisCfg);
			cp.run();
		} catch (BrokerException e) {
			e.printStackTrace();
		}
	}

	private void onMoveAll(String selectedQueue, JFrame frame) {
		QueueActionWindow cp;
		try {
			String destQueue = pickAQueue(frame);

			cp = new QueueActionWindow(frame, broker, eAction.eMove, sempV2ActionClient, sempV2MonitorClient,
					broker.msgVpnName, selectedQueue, destQueue, thisCfg);
			cp.run();

			SwingUtilities.invokeLater(() -> {
				try {
					System.out.println("REFRESH");
					onQueueNameSelected(selectedQueue, detailsLabel, buttonPanel);
				} catch (SempException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		} catch (BrokerException e) {
			e.printStackTrace();
		}
	}

	private void onDeleteAll(String selectedQueue, JFrame frame) {
		int response = JOptionPane.showConfirmDialog(frame,
				"Are you sure you want to delete all messages in the '" + selectedQueue + "' queue? IMPORTANT: This action cannot be undone.", "Confirmation",
				JOptionPane.YES_NO_OPTION);

		if (response == JOptionPane.YES_OPTION) {
			QueueActionWindow cp;
			try {
				cp = new QueueActionWindow(frame, broker, eAction.eDelete, sempV2ActionClient, sempV2MonitorClient,
						broker.msgVpnName, selectedQueue, "", thisCfg);
				cp.run();

				SwingUtilities.invokeLater(() -> {
					try {
						System.out.println("REFRESH");
						onQueueNameSelected(selectedQueue, detailsLabel, buttonPanel);
					} catch (SempException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
			} catch (BrokerException e) {
				e.printStackTrace();
			}
		}
	}
	 public static String splitCamelCase(String str) {
	        // Use a regular expression to split camelCase
	        String[] words = str.split("(?=[A-Z])"); // Matches positions before uppercase letters

	        // Capitalize the first word
	        if (words.length > 0) {
	            words[0] = words[0].substring(0, 1).toUpperCase() + words[0].substring(1);
	        }

	        // Join the words with spaces
	        return String.join(" ", words);
	    }


	private void addRowToDisplay(QueueInfo info, StringBuilder sb, String fieldName, boolean big) {
         try {
             Field field = info.getClass().getDeclaredField(fieldName);
             field.setAccessible(true); // Allow access to private fields
             Object value = field.get(info);
             String strValue = "---";
             if (value != null) {
            	 strValue = value.toString();
            	 if (strValue.isEmpty()) {
            		 strValue = "---";
            	 }
             }
             
             if (fieldName.equals("partitionCount")) {
            	 int val = Integer.parseInt(value.toString());
            	 if (val > 0) {
            		 strValue += " - CANNOT browse"; 
            	 }
             }
             
             String style = " ";
             if (big) {
            	 style = " style='font-size: 20px;' ";
             }
             
             String fieldNameForDisply = splitCamelCase(fieldName);
             sb.append("<tr><td width='180px' align='right'>" + fieldNameForDisply + ":</td><td" + style + " align='left'>" + strValue + "</td></tr>");
             
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		} 
         
	}
	private void onQueueNameSelected(String queueName, JLabel textArea2, JPanel buttonPanel)
			throws SempException, IOException {
		
		QueueInfo info  = sempV2MonitorClient.getQueueInfo(broker.msgVpnName, queueName);
		selectedQueueMsgCount = info.msgCount;

		//String display = "Queue name: " + queueName + "\nCurrent Message Count: " + selectedQueueMsgCount;
		StringBuilder sb = new StringBuilder();
		// Use system default font family for better cross-platform appearance
		String htmlFontFamily = (thisCfg.fontFamily != null && !thisCfg.fontFamily.isEmpty()) ? thisCfg.fontFamily : Font.SANS_SERIF;
		sb.append("<html>");
		sb.append("<div style='width: 580px; text-align: left; vertical-align:top; font-family: " + htmlFontFamily + ";'>");

		sb.append("<div style='width: 580px; text-align: left; vertical-align:top; font-family: " + htmlFontFamily + ";'>");
		sb.append("<table>");
		addRowToDisplay(info, sb, "name", true);
		addRowToDisplay(info, sb, "msgCount", false);
		addRowToDisplay(info, sb, "accessType", false);
		addRowToDisplay(info, sb, "maxMsgSpoolUsage", false);
		addRowToDisplay(info, sb, "owner", false);
		addRowToDisplay(info, sb, "permission", false);
		addRowToDisplay(info, sb, "egressEnabled", false);
		addRowToDisplay(info, sb, "ingressEnabled", false);
		addRowToDisplay(info, sb, "partitionCount", false);

        sb.append("</table>");
        sb.append("</div>");
        sb.append("</html>");
		
        String display = sb.toString();
        
//		String display = "<html>"
//	        + "<div style='width: 280px; text-align: left; vertical-align:top'>"
//	        + "<table>"
//	        + "  <tr><th align='right'>Name:</th><th align='left'><b>{queueName}</th>"
//	        + "  <tr><th align='right'>Message Count:</th><th align='left'><b>{msgCount}</th>"
//	        + "  <tr><th align='right'>Access Type:</th><th align='left'><b>{accessType}</th>"
//	        + "  <tr><th align='right'>Owner:</th><th align='left'><b>{owner}</th>"
//	        + "  <tr><th align='right'>Permission:</th><th align='left'><b>{permission}</th>"
//	        + "  <tr><th align='right'>Max Spool:</th><th align='left'><b>{maxMsgSpoolUsage}</th>"
//	        + "</table>"
//	        + "</div>"
//	        + "</html>";
//		
//		display = display.replace("{queueName}", "" + info.name);
//		display = display.replace("{msgCount}", "" + info.msgCount);
//		display = display.replace("{accessType}", "" + info.accessType);
//		display = display.replace("{maxMsgSpoolUsage}", "" + info.maxMsgSpoolUsage);
//		display = display.replace("{owner}", "" + info.owner);
//		display = display.replace("{permission}", "" + info.permission);

		textArea2.setText(display);
		qIconlabel.setVisible(true);
		
		// Enable buttons before removing/re-adding (they will be re-enabled after)
		browseButton.setEnabled(true);
		copyAllButton.setEnabled(true);
		deleteAllButton.setEnabled(true);
		moveAllButton.setEnabled(true);
		
		buttonPanel.removeAll();
		this.addButtons(buttonPanel);
		
		// Ensure buttons remain enabled after being re-added
		// Do it immediately and also in invokeLater to be safe
		browseButton.setEnabled(true);
		copyAllButton.setEnabled(true);
		deleteAllButton.setEnabled(true);
		moveAllButton.setEnabled(true);
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				browseButton.setEnabled(true);
				copyAllButton.setEnabled(true);
				deleteAllButton.setEnabled(true);
				moveAllButton.setEnabled(true);
				buttonPanel.revalidate();
				buttonPanel.repaint();
			}
		});

//        // any spec on where this can move to?
//        List<String> destinations = getMovementDestinationsFor(queueName);
//        if (destinations.size() > 0) {
//        	for (String oneDest: destinations) {
//                JButton moveAllButton = new JButton("Move all to " + oneDest);
//                moveAllButton.setEnabled(true);
//                MoveActionListener listener = new MoveActionListener(queueName, oneDest);
//                moveAllButton.addActionListener(listener);
//                buttonPanel.add(moveAllButton);
//        	}
//            buttonPanel.revalidate();
//            buttonPanel.repaint();
//        }
	}
//	class MoveActionListener implements ActionListener {
//		
//		private String source;
//		private String target;
//
//		public MoveActionListener(String source, String target) {
//			this.source = source;
//			this.target = target;
//		}
//
//		@Override
//		public void actionPerformed(ActionEvent e) {
//			// TODO Auto-generated method stub
//			System.out.println("click " + source + " -> " + target);
//		}
//	}
//	

//	private List<String> getMovementDestinationsFor(String strQueue) {
//		List<String> rc = new ArrayList<String>();
//		
//		for (Map.Entry<String, MovementDestination> entry : thisCfg.movements.entrySet()) {
//            String key = entry.getKey();
//            MovementDestination value = entry.getValue();
//            
//            if (value.source.equals(strQueue) || value.source.equals("all")) {
//            	for (String oneDest : value.destinations) {
//                	rc.add(oneDest);
//            	}
//            }
//		}
//		return rc;
//	}

	public static void main(String[] args) throws BrokerException {
		// Initialize FlatLaf before any GUI components
		try {
			UIManager.setLookAndFeel(new FlatLightLaf());
		} catch (Exception ex) {
			System.err.println("Failed to initialize FlatLaf, using default look and feel: " + ex.getMessage());
		}

		CommandLineParser parser = new CommandLineParser();
		parser.parseArgs(args);
		
		// Load config to get version (without master password for now)
		Config tempCfg = new Config(parser.configFileProvided);
		try {
			// Check if encrypted passwords exist before loading
			boolean hasEncrypted = tempCfg.hasEncryptedPasswords();
			if (hasEncrypted && parser.masterPasswordProvided != null) {
				tempCfg.setMasterPassword(parser.masterPasswordProvided);
			}
			tempCfg.load();
		} catch (BrokerException e) {
			// If it's a master password error, we'll handle it in QueueBrowserMainWindow
			if (!e.getMessage().contains("master password")) {
				logger.error("Failed to load config for version info: " + e.getMessage());
			}
		}
		String versionStr = tempCfg.version;
		
		System.out.println("=================================================================");
		System.out.println("Starting Solace Queue Browser - Version: " + versionStr);
		System.out.println("=================================================================");

		logger.info("=================================================================");
		logger.info("Starting Solace Queue Browser - Version: " + versionStr);
		logger.info("=================================================================");
		logger.info("*** VERIFICATION: This is v2.0.2 with SMF error handling ***");
		logger.info("Configuration File: " + parser.configFileProvided);

		QueueBrowserMainWindow me = new QueueBrowserMainWindow(parser.configFileProvided, parser.masterPasswordProvided);
		me.run();
	}
}