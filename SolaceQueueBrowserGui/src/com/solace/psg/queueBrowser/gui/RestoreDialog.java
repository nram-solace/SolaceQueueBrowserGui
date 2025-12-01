package com.solace.psg.queueBrowser.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solace.psg.queueBrowser.MessagePublisher;
import com.solace.psg.queueBrowser.util.MessageRestoreUtil;
import com.solace.psg.queueBrowser.util.MessageRestoreUtil.ParsedMessage;
import com.solace.psg.queueBrowser.util.MessageRestoreUtil.SourceMetadata;
import com.solace.psg.util.CommandLog;

/**
 * Dialog for restoring messages from downloaded ZIP files to a queue
 */
public class RestoreDialog {
	private static final Logger logger = LoggerFactory.getLogger(RestoreDialog.class.getName());
	
	private Broker broker;
	private String defaultQueueName;
	private JFrame parentFrame;
	private Config config;
	
	// UI Components
	private JDialog dialog;
	private JTable table;
	private DefaultTableModel tableModel;
	private JCheckBox selectAllCheckBox;
	private JButton previousPageButton;
	private JButton nextPageButton;
	private JButton restoreButton;
	private JButton cancelButton;
	private JLabel topLabel;
	private JLabel statusLabel;
	private JLabel sourceInfoLabel;
	private JButton selectDirectoryButton;
	
	// Data
	private List<ParsedMessage> allMessages;
	private List<ParsedMessage> currentPageMessages;
	private java.util.Set<String> selectedMessageKeys; // Track selected messages using "messageId|timestamp" as key
	private int nCurPage = 1;
	private int nItemsPerPage = 20;
	private int totalPages = 0;
	private SourceMetadata sourceMetadata;
	private String selectedDirectory;
	private boolean updatingSelectAllCheckBox = false;
	private boolean displayingPage = false; // Flag to prevent select-all checkbox updates during page display
	private ActionListener selectAllCheckBoxListener = null;
	private TableModelListener tableModelListener = null; // Store reference to table model listener
	
	private enum eSelectAllState {eIndeterminant, eSelectedAll, eSelectedNone};
	private eSelectAllState currentSelectAllState = eSelectAllState.eIndeterminant;
	
	private static final int nIdColumn = 2; // Column index for Message ID
	private static final int nTimestampColumn = 3; // Column index for Timestamp
	
	/**
	 * Generate a unique key for a message using messageId and timestamp
	 */
	private String getMessageKey(String messageId, String timestamp) {
		return messageId + "|" + timestamp;
	}
	
	public RestoreDialog(JFrame parentFrame, Broker broker, String defaultQueueName, Config config) {
		this.parentFrame = parentFrame;
		this.broker = broker;
		this.defaultQueueName = defaultQueueName;
		this.config = config != null ? config : new Config("");
		this.allMessages = new ArrayList<>();
		this.currentPageMessages = new ArrayList<>();
		this.selectedMessageKeys = new java.util.HashSet<>();
	}
	
	/**
	 * Get font from config or use default
	 */
	private Font getFont(int size, int style) {
		if (config != null && config.fontFamily != null && !config.fontFamily.isEmpty()) {
			return new Font(config.fontFamily, style, size);
		}
		return UIManager.getFont("Label.font").deriveFont(style, size);
	}
	
	/**
	 * Run the restore dialog
	 */
	public void run() {
		String versionStr = config != null ? config.version : "v2.1.3";
		dialog = new JDialog(parentFrame, "SolaceQueueBrowserGui 2.0 - Restore Messages - " + versionStr, true);
		dialog.setSize(1200, 800);
		dialog.setLayout(new BorderLayout());
		dialog.setModal(false);
		dialog.setLocationRelativeTo(parentFrame);
		
		// Create main panel
		JPanel mainPanel = new JPanel(new BorderLayout());
		String fontFamily = (config != null && config.fontFamily != null && !config.fontFamily.isEmpty()) ? config.fontFamily : (config != null ? config.defaultFontFamilyFallback : "Serif");
		int labelFontSize = config != null ? config.labelFontSize : 16;
		int buttonFontSize = config != null ? config.buttonFontSize : 14;
		
		// Top row: Queue info | Directory selection | Pagination (all in one row)
		JPanel topRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		topRowPanel.setBorder(new EmptyBorder(10, 20, 10, 20));
		
		// Queue info
		JLabel queueInfoLabel = new JLabel("Restore to Queue: " + defaultQueueName);
		queueInfoLabel.setFont(new Font(fontFamily, Font.BOLD, labelFontSize));
		topRowPanel.add(queueInfoLabel);
		
		// Separator
		topRowPanel.add(new JLabel(" | "));
		
		// Directory selection
		selectDirectoryButton = new JButton("Select Directory");
		selectDirectoryButton.setBackground(config != null ? config.buttonRefresh : new Color(220, 245, 255));
		selectDirectoryButton.setForeground(config != null ? config.buttonRefreshForeground : Color.BLACK);
		selectDirectoryButton.addActionListener(e -> onSelectDirectory());
		topRowPanel.add(selectDirectoryButton);
		
		sourceInfoLabel = new JLabel("No directory selected");
		sourceInfoLabel.setFont(new Font(fontFamily, Font.PLAIN, buttonFontSize));
		topRowPanel.add(sourceInfoLabel);
		
		// Separator
		topRowPanel.add(new JLabel(" | "));
		
		// Pagination controls
		previousPageButton = new JButton("<< Prev");
		previousPageButton.setEnabled(false);
		previousPageButton.setBackground(config != null ? config.buttonNavigation : new Color(230, 240, 255));
		previousPageButton.setForeground(config != null ? config.buttonNavigationForeground : Color.BLACK);
		previousPageButton.addActionListener(e -> onPreviousPage());
		topRowPanel.add(previousPageButton);
		
		topLabel = new JLabel("Page 1 of 1");
		topLabel.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(topLabel);
		
		nextPageButton = new JButton("Next >>");
		nextPageButton.setEnabled(false);
		nextPageButton.setBackground(config != null ? config.buttonNavigation : new Color(230, 240, 255));
		nextPageButton.setForeground(config != null ? config.buttonNavigationForeground : Color.BLACK);
		nextPageButton.addActionListener(e -> onNextPage());
		topRowPanel.add(nextPageButton);
		
		mainPanel.add(topRowPanel, BorderLayout.NORTH);
		
		// Create table
		String[] columnNames = {"", "", "Message ID", "Timestamp", "Original Queue"};
		tableModel = new DefaultTableModel(new Object[][]{}, columnNames) {
			@Override
			public Class<?> getColumnClass(int columnIndex) {
				if (columnIndex == 0) return Boolean.class;
				if (columnIndex == 1) return Icon.class;
				return String.class;
			}
			
			@Override
			public boolean isCellEditable(int row, int column) {
				return column == 0; // Only checkbox column is editable
			}
		};
		
		table = new JTable(tableModel);
		table.setRowHeight(33);
		table.setDefaultRenderer(Object.class, new AlternatingRowColorRenderer(config));
		table.getColumnModel().getColumn(0).setCellRenderer(new CheckboxTableCellRenderer(config));
		table.getColumnModel().getColumn(1).setCellRenderer(new IconicTableCellRenderer(config));
		
		// Set up select-all checkbox
		setupSelectAllCheckbox();
		
		// Set column widths
		table.getColumnModel().getColumn(0).setPreferredWidth(36);
		table.getColumnModel().getColumn(1).setPreferredWidth(36);
		table.getColumnModel().getColumn(2).setPreferredWidth(200);
		table.getColumnModel().getColumn(3).setPreferredWidth(200);
		table.getColumnModel().getColumn(4).setPreferredWidth(300);
		
		JScrollPane tableScrollPane = new JScrollPane(table);
		tableScrollPane.setPreferredSize(new Dimension(0, 400));
		mainPanel.add(tableScrollPane, BorderLayout.CENTER);
		
		// Bottom panel: Status | [Restore] [Cancel]
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.setBorder(new EmptyBorder(10, 20, 10, 20));
		
		// Status label on the left
		statusLabel = new JLabel("Select a directory containing message ZIP files");
		statusLabel.setFont(new Font(fontFamily, Font.PLAIN, buttonFontSize));
		bottomPanel.add(statusLabel, BorderLayout.WEST);
		
		// Buttons on the right
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		restoreButton = new JButton("Restore");
		restoreButton.setEnabled(false);
		restoreButton.setBackground(config != null ? config.buttonRestore : new Color(220, 255, 220));
		restoreButton.setForeground(config != null ? config.buttonRestoreForeground : Color.BLACK);
		restoreButton.addActionListener(e -> onRestore());
		buttonPanel.add(restoreButton);
		
		cancelButton = new JButton("Cancel");
		cancelButton.setBackground(config != null ? config.buttonDelete : new Color(255, 220, 220));
		cancelButton.setForeground(config != null ? config.buttonDeleteForeground : Color.BLACK);
		cancelButton.addActionListener(e -> dialog.dispose());
		buttonPanel.add(cancelButton);
		
		bottomPanel.add(buttonPanel, BorderLayout.EAST);
		
		dialog.add(mainPanel, BorderLayout.CENTER);
		dialog.add(bottomPanel, BorderLayout.SOUTH);
		
		dialog.setVisible(true);
	}
	
	/**
	 * Set up select-all checkbox in table header
	 */
	private void setupSelectAllCheckbox() {
		selectAllCheckBox = new JCheckBox();
		selectAllCheckBox.setToolTipText("Select/Deselect all messages");
		selectAllCheckBox.setHorizontalAlignment(SwingConstants.CENTER);
		
		Runnable toggleAllMessages = () -> {
			if (updatingSelectAllCheckBox) {
				logger.debug("toggleAllMessages: Skipping - updatingSelectAllCheckBox is true");
				return;
			}
			
			if (displayingPage) {
				logger.info("toggleAllMessages: Skipping - displayingPage is true (preventing duplicate adds during page display)");
				return;
			}
			
			boolean newValue = selectAllCheckBox.isSelected();
			logger.info("toggleAllMessages: Called with newValue={}, currentPageRows={}, currentSelectedCount={}", 
				newValue, table.getRowCount(), selectedMessageKeys.size());
			
			currentSelectAllState = newValue ? eSelectAllState.eSelectedAll : eSelectAllState.eSelectedNone;
			
			// Update current page selections - TableModelListener will handle selectedMessageKeys
			for (int row = 0; row < table.getRowCount(); row++) {
				table.setValueAt(newValue, row, 0);
			}
			
			logger.debug("toggleAllMessages: After updating rows, totalSelected={}", selectedMessageKeys.size());
			
			table.clearSelection();
			if (newValue) {
				table.setRowSelectionInterval(0, table.getRowCount() - 1);
				setStatus(selectedMessageKeys.size() + " messages selected");
			} else {
				setStatus("De-selected all messages");
			}
			table.repaint();
			updateRestoreButtonState();
		};
		
		selectAllCheckBoxListener = e -> toggleAllMessages.run();
		selectAllCheckBox.addActionListener(selectAllCheckBoxListener);
		
		TableCellRenderer headerCheckboxRenderer = new TableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				return selectAllCheckBox;
			}
		};
		
		table.getColumnModel().getColumn(0).setHeaderRenderer(headerCheckboxRenderer);
		
		JTableHeader header = table.getTableHeader();
		header.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int col = header.columnAtPoint(e.getPoint());
				if (col == 0 && selectAllCheckBox != null && table.getRowCount() > 0) {
					if (updatingSelectAllCheckBox) {
						return;
					}
					boolean newValue = !selectAllCheckBox.isSelected();
					updatingSelectAllCheckBox = true;
					selectAllCheckBox.setSelected(newValue);
					updatingSelectAllCheckBox = false;
					
					// Update all messages on current page - TableModelListener will handle selectedMessageIds
					for (int row = 0; row < table.getRowCount(); row++) {
						table.setValueAt(newValue, row, 0);
					}
					
					table.clearSelection();
					if (newValue) {
						table.setRowSelectionInterval(0, table.getRowCount() - 1);
						setStatus(selectedMessageKeys.size() + " messages selected");
					} else {
						setStatus("De-selected all messages");
					}
					table.repaint();
					updateRestoreButtonState();
				}
			}
		});
		
		tableModelListener = new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent e) {
				// Ignore INSERT and DELETE events (from addRow/removeRow) - only process UPDATE events
				if (e.getType() != TableModelEvent.UPDATE) {
					return;
				}
				
				// Skip processing during page display
				if (displayingPage) {
					System.out.println("TableModelListener: Skipping - displayingPage is true");
					return;
				}
				
				if (e.getColumn() == 0 || e.getColumn() == TableModelEvent.ALL_COLUMNS) {
					System.out.println("TableModelListener: column=" + e.getColumn() + ", type=" + e.getType() + 
						", firstRow=" + e.getFirstRow() + ", lastRow=" + e.getLastRow() + 
						", selectedMessageKeys.size()=" + selectedMessageKeys.size());
					
					// Only process if it's a single row change (not bulk operations)
					if (e.getFirstRow() == e.getLastRow()) {
						int row = e.getFirstRow();
						if (row >= 0 && row < table.getRowCount() && row < currentPageMessages.size()) {
							// Get the ParsedMessage from currentPageMessages to get the original timestamp
							ParsedMessage msg = currentPageMessages.get(row);
							String messageKey = getMessageKey(msg.messageId, msg.timestamp);
							Boolean checked = (Boolean) table.getValueAt(row, 0);
							boolean wasInSet = selectedMessageKeys.contains(messageKey);
							if (checked != null && checked) {
								selectedMessageKeys.add(messageKey);
								System.out.println("TableModelListener: ADDED messageId=" + msg.messageId + 
									", timestamp=" + msg.timestamp + ", key=" + messageKey + 
									", wasInSet=" + wasInSet + ", totalSelected=" + selectedMessageKeys.size());
							} else {
								selectedMessageKeys.remove(messageKey);
								System.out.println("TableModelListener: REMOVED messageId=" + msg.messageId + 
									", timestamp=" + msg.timestamp + ", key=" + messageKey + 
									", wasInSet=" + wasInSet + ", totalSelected=" + selectedMessageKeys.size());
							}
						}
					} else {
						// Bulk update - update all rows in the range
						System.out.println("TableModelListener: BULK UPDATE from row " + e.getFirstRow() + " to " + e.getLastRow());
						for (int row = e.getFirstRow(); row <= e.getLastRow() && row < table.getRowCount() && row < currentPageMessages.size(); row++) {
							// Get the ParsedMessage from currentPageMessages to get the original timestamp
							ParsedMessage msg = currentPageMessages.get(row);
							String messageKey = getMessageKey(msg.messageId, msg.timestamp);
							Boolean checked = (Boolean) table.getValueAt(row, 0);
							boolean wasInSet = selectedMessageKeys.contains(messageKey);
							if (checked != null && checked) {
								selectedMessageKeys.add(messageKey);
								System.out.println("TableModelListener: BULK ADDED messageId=" + msg.messageId + 
									", timestamp=" + msg.timestamp + ", key=" + messageKey + ", wasInSet=" + wasInSet);
							} else {
								selectedMessageKeys.remove(messageKey);
								System.out.println("TableModelListener: BULK REMOVED messageId=" + msg.messageId + 
									", timestamp=" + msg.timestamp + ", key=" + messageKey + ", wasInSet=" + wasInSet);
							}
						}
						System.out.println("TableModelListener: After bulk update, totalSelected=" + selectedMessageKeys.size());
					}
					updateSelectAllCheckBoxState();
					updateRestoreButtonState();
				}
			}
		};
		tableModel.addTableModelListener(tableModelListener);
	}
	
	/**
	 * Update select-all checkbox state based on current selections
	 */
	private void updateSelectAllCheckBoxState() {
		// Skip updating checkbox during page display to prevent triggering toggleAllMessages
		if (displayingPage) {
			logger.debug("updateSelectAllCheckBoxState: Skipping - displayingPage is true");
			return;
		}
		
		if (selectAllCheckBox == null || table.getRowCount() == 0) {
			if (selectAllCheckBox != null) {
				updatingSelectAllCheckBox = true;
				selectAllCheckBox.setSelected(false);
				updatingSelectAllCheckBox = false;
			}
			return;
		}
		
		boolean allSelected = true;
		int checkedCount = 0;
		for (int row = 0; row < table.getRowCount(); row++) {
			Boolean checked = (Boolean) table.getValueAt(row, 0);
			if (checked != null && checked) {
				checkedCount++;
			} else {
				allSelected = false;
			}
		}
		
		logger.debug("updateSelectAllCheckBoxState: table.getRowCount()={}, checkedCount={}, allSelected={}, currentCheckboxState={}, selectedMessageKeys.size()={}", 
			table.getRowCount(), checkedCount, allSelected, selectAllCheckBox.isSelected(), selectedMessageKeys.size());
		
		// Only update if state actually changed to prevent unnecessary events
		boolean checkboxCurrentlySelected = selectAllCheckBox.isSelected();
		if (checkboxCurrentlySelected != allSelected) {
			logger.info("updateSelectAllCheckBoxState: Changing checkbox from {} to {}, selectedMessageKeys.size()={}", 
				checkboxCurrentlySelected, allSelected, selectedMessageKeys.size());
			
			// Set flag BEFORE removing listener to prevent any race conditions
			updatingSelectAllCheckBox = true;
			
			// Temporarily remove listener to prevent triggering toggleAllMessages when updating programmatically
			if (selectAllCheckBoxListener != null) {
				ActionListener[] listeners = selectAllCheckBox.getActionListeners();
				logger.debug("updateSelectAllCheckBoxState: Current listeners count={}", listeners.length);
				for (ActionListener listener : listeners) {
					if (listener == selectAllCheckBoxListener) {
						selectAllCheckBox.removeActionListener(selectAllCheckBoxListener);
						logger.debug("updateSelectAllCheckBoxState: Removed action listener");
						break;
					}
				}
			}
			selectAllCheckBox.setSelected(allSelected);
			if (selectAllCheckBoxListener != null) {
				selectAllCheckBox.addActionListener(selectAllCheckBoxListener);
				logger.debug("updateSelectAllCheckBoxState: Re-added action listener");
			}
			
			// Clear flag AFTER re-adding listener
			updatingSelectAllCheckBox = false;
			
			logger.info("updateSelectAllCheckBoxState: After checkbox update, selectedMessageKeys.size()={}", selectedMessageKeys.size());
		} else {
			logger.debug("updateSelectAllCheckBoxState: Checkbox state unchanged, skipping update");
		}
		currentSelectAllState = allSelected ? eSelectAllState.eSelectedAll : eSelectAllState.eSelectedNone;
	}
	
	/**
	 * Update restore button enabled state
	 */
	private void updateRestoreButtonState() {
		restoreButton.setEnabled(!selectedMessageKeys.isEmpty() && !allMessages.isEmpty());
	}
	
	/**
	 * Handle directory selection
	 */
	private void onSelectDirectory() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fileChooser.setDialogTitle("Select Directory Containing Message ZIP Files");
		
		// Set root directory from config downloadFolder
		String downloadFolder = config != null && config.downloadFolder != null ? config.downloadFolder : "./downloads";
		File rootDir = new File(downloadFolder);
		
		// If root directory doesn't exist, try to create it
		if (!rootDir.exists()) {
			rootDir.mkdirs();
		}
		
		// Set current directory - use selectedDirectory if available, otherwise use rootDir
		if (selectedDirectory != null && new File(selectedDirectory).exists()) {
			fileChooser.setCurrentDirectory(new File(selectedDirectory));
		} else if (rootDir.exists()) {
			fileChooser.setCurrentDirectory(rootDir);
		}
		
		int result = fileChooser.showOpenDialog(dialog);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedDir = fileChooser.getSelectedFile();
			selectedDirectory = selectedDir.getAbsolutePath();
			
			// Extract source metadata
			sourceMetadata = MessageRestoreUtil.extractSourceMetadata(selectedDirectory);
			
			if (sourceMetadata == null) {
				JOptionPane.showMessageDialog(dialog,
					"Could not extract source metadata from directory path.\n" +
					"Expected format: .../host/vpn/queue-name/",
					"Invalid Directory",
					JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			// Load messages from directory (validation will happen when Restore is clicked)
			loadMessagesFromDirectory();
		}
	}
	
	/**
	 * Validate source metadata against target broker/vpn/queue
	 */
	private boolean validateSourceTarget() {
		if (sourceMetadata == null) {
			return true; // No source metadata, skip validation
		}
		
		String targetHost = broker.fqdn();
		if (targetHost == null || targetHost.isEmpty()) {
			targetHost = broker.name;
		}
		
		String sourceHost = sourceMetadata.host;
		String sourceVpn = sourceMetadata.vpn;
		String sourceQueue = sourceMetadata.queue;
		String targetVpn = broker.msgVpnName;
		String targetQueue = defaultQueueName;
		
		boolean hostDiff = !sourceHost.equalsIgnoreCase(targetHost);
		boolean vpnDiff = !sourceVpn.equalsIgnoreCase(targetVpn);
		boolean queueDiff = !sourceQueue.equalsIgnoreCase(targetQueue);
		
		if (hostDiff || vpnDiff || queueDiff) {
			StringBuilder message = new StringBuilder();
			message.append("Source and target differ:\n\n");
			if (hostDiff) {
				message.append("Host: ").append(sourceHost).append(" -> ").append(targetHost).append("\n");
			}
			if (vpnDiff) {
				message.append("VPN: ").append(sourceVpn).append(" -> ").append(targetVpn).append("\n");
			}
			if (queueDiff) {
				message.append("Queue: ").append(sourceQueue).append(" -> ").append(targetQueue).append("\n");
			}
			message.append("\nDo you want to proceed with restore?");
			
			int response = JOptionPane.showConfirmDialog(dialog,
				message.toString(),
				"Source/Target Mismatch",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
			
			return response == JOptionPane.YES_OPTION;
		}
		
		return true;
	}
	
	/**
	 * Load messages from selected directory
	 */
	private void loadMessagesFromDirectory() {
		dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				// Find all ZIP files
				List<File> zipFiles = MessageRestoreUtil.findMessageZipFiles(selectedDirectory);
				
				if (zipFiles.isEmpty()) {
					SwingUtilities.invokeLater(() -> {
						JOptionPane.showMessageDialog(dialog,
							"No message ZIP files found in directory:\n" + selectedDirectory,
							"No Files Found",
							JOptionPane.WARNING_MESSAGE);
					});
					return null;
				}
				
				// Parse ZIP files
				allMessages = MessageRestoreUtil.parseZipFiles(zipFiles);
				
				// Sort by message ID
				Collections.sort(allMessages, Comparator.comparing(m -> m.messageId));
				
				return null;
			}
			
			@Override
			protected void done() {
				dialog.setCursor(Cursor.getDefaultCursor());
				
				if (allMessages.isEmpty()) {
					setStatus("No valid messages found in directory");
					sourceInfoLabel.setText("No messages found");
					return;
				}
				
				// Update source info label
				String sourceInfo = String.format("Source: %s/%s/%s (%d messages)",
					sourceMetadata.host, sourceMetadata.vpn, sourceMetadata.queue, allMessages.size());
				sourceInfoLabel.setText(sourceInfo);
				
				// Calculate pages
				totalPages = (allMessages.size() + nItemsPerPage - 1) / nItemsPerPage;
				nCurPage = 1;
				
				// Clear previous selections
				selectedMessageKeys.clear();
				
				// Display first page
				displayPage(1);
				setStatus("Loaded " + allMessages.size() + " messages from directory");
			}
		};
		
		worker.execute();
	}
	
	/**
	 * Display a specific page of messages
	 */
	private void displayPage(int page) {
		logger.info("displayPage: Displaying page {}, selectedMessageKeys.size()={}", page, selectedMessageKeys.size());
		
		displayingPage = true; // Prevent select-all checkbox from triggering updates during page display
		
		nCurPage = page;
		int startIndex = (page - 1) * nItemsPerPage;
		int endIndex = Math.min(startIndex + nItemsPerPage, allMessages.size());
		
		currentPageMessages = allMessages.subList(startIndex, endIndex);
		
		logger.info("displayPage: Page {} has {} messages (indices {} to {})", page, currentPageMessages.size(), startIndex, endIndex-1);
		
		// Clear table
		tableModel.setRowCount(0);
		
		// Add rows, preserving selection state from selectedMessageKeys
		ImageIcon messageIcon = new ImageIcon("config/messageIcon32.png");
		for (ParsedMessage msg : currentPageMessages) {
			String messageKey = getMessageKey(msg.messageId, msg.timestamp);
			boolean isSelected = selectedMessageKeys.contains(messageKey);
			logger.debug("displayPage: Adding row for messageId={}, timestamp={}, key={}, isSelected={}", 
				msg.messageId, msg.timestamp, messageKey, isSelected);
			Object[] row = {
				isSelected, // checkbox - restore selection state
				messageIcon, // icon
				msg.messageId,
				MessageRestoreUtil.formatTimestamp(msg.timestamp),
				sourceMetadata.queue
			};
			tableModel.addRow(row);
		}
		
		logger.info("displayPage: After adding rows, selectedMessageKeys.size()={}", selectedMessageKeys.size());
		
		// Update pagination
		topLabel.setText("Page " + nCurPage + " of " + totalPages);
		previousPageButton.setEnabled(nCurPage > 1);
		nextPageButton.setEnabled(nCurPage < totalPages);
		
		// Update select-all checkbox state visually without triggering events
		// We skip updateSelectAllCheckBoxState during page display to prevent triggering toggleAllMessages
		// Instead, update the checkbox state directly after clearing the flag
		logger.info("displayPage: Before updating checkbox, selectedMessageKeys.size()={}", selectedMessageKeys.size());
		
		displayingPage = false; // Re-enable normal checkbox behavior BEFORE updating checkbox
		
		// Now update the checkbox state - this is safe because displayingPage is false
		// but we still need to prevent it from triggering during the update
		updatingSelectAllCheckBox = true;
		if (selectAllCheckBox != null && table.getRowCount() > 0) {
			boolean allSelected = true;
			for (int row = 0; row < table.getRowCount(); row++) {
				Boolean checked = (Boolean) table.getValueAt(row, 0);
				if (checked == null || !checked) {
					allSelected = false;
					break;
				}
			}
			// Temporarily remove listener
			if (selectAllCheckBoxListener != null) {
				ActionListener[] listeners = selectAllCheckBox.getActionListeners();
				for (ActionListener listener : listeners) {
					if (listener == selectAllCheckBoxListener) {
						selectAllCheckBox.removeActionListener(selectAllCheckBoxListener);
						break;
					}
				}
			}
			selectAllCheckBox.setSelected(allSelected);
			if (selectAllCheckBoxListener != null) {
				selectAllCheckBox.addActionListener(selectAllCheckBoxListener);
			}
			currentSelectAllState = allSelected ? eSelectAllState.eSelectedAll : eSelectAllState.eSelectedNone;
		}
		updatingSelectAllCheckBox = false;
		
		logger.info("displayPage: After updating checkbox, selectedMessageKeys.size()={}", selectedMessageKeys.size());
		
		updateRestoreButtonState();
	}
	
	/**
	 * Handle previous page
	 */
	private void onPreviousPage() {
		if (nCurPage > 1) {
			displayPage(nCurPage - 1);
		}
	}
	
	/**
	 * Handle next page
	 */
	private void onNextPage() {
		if (nCurPage < totalPages) {
			displayPage(nCurPage + 1);
		}
	}
	
	/**
	 * Handle restore action
	 */
	private void onRestore() {
		System.out.println("onRestore: Called, selectedMessageKeys.size()=" + selectedMessageKeys.size());
		System.out.println("onRestore: selectedMessageKeys=" + selectedMessageKeys);
		
		// Get selected messages
		List<ParsedMessage> selectedMessages = getSelectedMessages();
		
		System.out.println("onRestore: getSelectedMessages returned " + selectedMessages.size() + " messages");
		for (ParsedMessage msg : selectedMessages) {
			System.out.println("onRestore: Will restore messageId=" + msg.messageId);
		}
		
		if (selectedMessages.isEmpty()) {
			JOptionPane.showMessageDialog(dialog,
				"Please select at least one message to restore.",
				"No Selection",
				JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		// Validate source vs target (prompt user if different)
		if (sourceMetadata != null && !validateSourceTarget()) {
			return; // User cancelled
		}
		
		String targetQueue = defaultQueueName;
		
		// Validate queue name
		if (targetQueue == null || targetQueue.trim().isEmpty()) {
			JOptionPane.showMessageDialog(dialog,
				"Target queue name is not set. Please select a queue first.",
				"Invalid Queue Name",
				JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		// Perform restore (no confirmation prompt)
		performRestore(selectedMessages, targetQueue.trim());
	}
	
	/**
	 * Get list of selected messages (across all pages)
	 * Uses messageId + timestamp as unique key to handle duplicate messageIds
	 */
	private List<ParsedMessage> getSelectedMessages() {
		List<ParsedMessage> selected = new ArrayList<>();
		
		System.out.println("getSelectedMessages: selectedMessageKeys.size()=" + selectedMessageKeys.size() + 
			", allMessages.size()=" + allMessages.size());
		System.out.println("getSelectedMessages: selectedMessageKeys=" + selectedMessageKeys);
		
		// Get selected messages using messageId + timestamp as unique key
		for (ParsedMessage msg : allMessages) {
			String messageKey = getMessageKey(msg.messageId, msg.timestamp);
			if (selectedMessageKeys.contains(messageKey)) {
				selected.add(msg);
				System.out.println("getSelectedMessages: Added messageId=" + msg.messageId + 
					", timestamp=" + msg.timestamp + ", key=" + messageKey);
			}
		}
		
		System.out.println("getSelectedMessages: Returning " + selected.size() + " messages");
		return selected;
	}
	
	/**
	 * Perform the restore operation
	 */
	private void performRestore(List<ParsedMessage> messages, String targetQueue) {
		dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		restoreButton.setEnabled(false);
		
		SwingWorker<Integer, Integer> worker = new SwingWorker<Integer, Integer>() {
			private int successCount = 0;
			private int failCount = 0;
			private java.util.List<String> errorMessages = new java.util.ArrayList<>();
			
			@Override
			protected Integer doInBackground() throws Exception {
				MessagePublisher publisher = null;
				try {
					publisher = new MessagePublisher(broker);
					
					logger.info("performRestore: Starting restore of {} messages", messages.size());
					for (int i = 0; i < messages.size(); i++) {
						ParsedMessage msg = messages.get(i);
						logger.info("performRestore: Processing message {}/{}: messageId={}", i+1, messages.size(), msg.messageId);
						
						if (isCancelled()) {
							break;
						}
						
						try {
							publisher.publishMessage(targetQueue, msg);
							successCount++;
							String logMsg = "MessageId " + msg.messageId + " restored to queue '" + targetQueue + "'.";
							CommandLog.instance().log(logMsg);
							logger.info("performRestore: Successfully restored messageId={}", msg.messageId);
						} catch (BrokerException e) {
							failCount++;
							String errorMsg = e.getMessage();
							errorMessages.add(errorMsg);
							logger.error("Failed to restore message " + msg.messageId, e);
							CommandLog.instance().log("Failed to restore message " + msg.messageId + ": " + errorMsg);
						}
						
						publish(successCount + failCount);
					}
					logger.info("performRestore: Completed - successCount={}, failCount={}", successCount, failCount);
				} finally {
					if (publisher != null) {
						publisher.close();
					}
				}
				
				return successCount;
			}
			
			@Override
			protected void process(List<Integer> chunks) {
				if (!chunks.isEmpty()) {
					int processed = chunks.get(chunks.size() - 1);
					setStatus("Restored " + processed + " of " + messages.size() + " messages...");
				}
			}
			
			@Override
			protected void done() {
				dialog.setCursor(Cursor.getDefaultCursor());
				restoreButton.setEnabled(true);
				
				try {
					successCount = get();
					failCount = messages.size() - successCount;
					
					String dialogMessage;
					String statusMessage;
					
					if (failCount == 0) {
						dialogMessage = successCount + " message(s) successfully restored to queue '" + targetQueue + "'.";
						statusMessage = "Restore complete. " + successCount + " successful, " + failCount + " failed.";
						JOptionPane.showMessageDialog(dialog, dialogMessage,
							"Restore Complete",
							JOptionPane.INFORMATION_MESSAGE);
					} else {
						// Build detailed error message for dialog
						StringBuilder errorDetails = new StringBuilder();
						errorDetails.append(successCount).append(" message(s) restored successfully.\n");
						errorDetails.append(failCount).append(" message(s) failed.\n\n");
						
						// Check if all failures are due to queue quota
						boolean allQuotaErrors = true;
						for (String errorMsg : errorMessages) {
							if (errorMsg != null && !errorMsg.contains("Spool Over Quota") && !errorMsg.contains("queue is full")) {
								allQuotaErrors = false;
								break;
							}
						}
						
						if (allQuotaErrors && !errorMessages.isEmpty()) {
							// All errors are quota-related - show a clear message
							errorDetails.append("All failures are due to the target queue being full (Spool Over Quota).\n");
							errorDetails.append("The queue '").append(targetQueue).append("' has exceeded its message VPN limit.\n");
							errorDetails.append("Please free up space in the queue or increase the quota limit.");
						} else {
							// Mixed or other errors - show first few error details
							errorDetails.append("Error details:\n");
							int maxErrorsToShow = Math.min(3, errorMessages.size());
							for (int i = 0; i < maxErrorsToShow; i++) {
								String errorMsg = errorMessages.get(i);
								// Truncate long error messages
								if (errorMsg.length() > 150) {
									errorMsg = errorMsg.substring(0, 147) + "...";
								}
								errorDetails.append("• ").append(errorMsg).append("\n");
							}
							if (errorMessages.size() > maxErrorsToShow) {
								errorDetails.append("... and ").append(errorMessages.size() - maxErrorsToShow)
									.append(" more error(s).");
							}
						}
						
						dialogMessage = errorDetails.toString();
						statusMessage = "Restore complete. " + successCount + " successful, " + failCount + " failed.";
						JOptionPane.showMessageDialog(dialog, dialogMessage,
							"Restore Complete",
							JOptionPane.WARNING_MESSAGE);
					}
					
					// Clear message selections after restore (success or failure)
					selectedMessageKeys.clear();
					updateSelectAllCheckBoxState();
					updateRestoreButtonState();
					
					// Set short status message
					setStatus(statusMessage);
					
					// Refresh display
					displayPage(nCurPage);
				} catch (Exception e) {
					logger.error("Error during restore", e);
					JOptionPane.showMessageDialog(dialog,
						"Error during restore: " + e.getMessage(),
						"Restore Error",
						JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		
		worker.execute();
	}
	
	/**
	 * Set status message
	 */
	private void setStatus(String text) {
		SwingUtilities.invokeLater(() -> {
			statusLabel.setText(text);
		});
	}
}

