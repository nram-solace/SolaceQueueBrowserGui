package com.solace.psg.queueBrowser.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solace.psg.brokers.semp.SempClient;
import com.solace.psg.brokers.semp.SempException;
import com.solace.psg.queueBrowser.PaginatedCachingBrowser;
import com.solace.psg.queueBrowser.gui.dragAndDrop.DroppableMessage;
import com.solace.psg.queueBrowser.gui.dragAndDrop.IDragDropInstigator;
import com.solace.psg.queueBrowser.gui.dragAndDrop.QueueMessageTransferInstigatorHandler;
import com.solace.psg.util.CommandLog;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.ReplicationGroupMessageId;
import com.solacesystems.jcsmp.SDTException;
import com.solacesystems.jcsmp.SDTMap;

public class BrowserDialog implements IDragDropInstigator {
	private Broker broker;
	private PaginatedCachingBrowser browser;
	private String queue;
	private String[] otherQueues;
	private JFrame parentFrame;
	private int nCurPage = 1;

	private int nItemsPerPage = 20;
	private static final int nIdColumn = 2;
	
	private int estimatedPageCount = 0;

	private JLabel topLabel;
	private JLabel filterStatusLabel;
	private JTextArea textArea;
	//private JTextArea propsArea;
	private JTable table;
	private DefaultTableModel tableModel; 
	private JTable headersTable;
	private DefaultTableModel headersTableModel; 
	private JTable propsTable;
	private DefaultTableModel propsTableModel; 
	private JButton nextPageButton;
	private JButton nextMsgButton;
	private JButton delButton;
	private JButton prevMsgButton;
	private JButton moveMessageMsgButton;
	private JButton copyMessageMsgButton;
	private JButton downloadMessageMsgButton;
	private JLabel statusLabel;
	private JComboBox<String> comboBox;
	JDialog dialog; 
	private Semaphore semaphore = new Semaphore(1);
	private int selectedRow;
	private IconicTableCellRenderer iconCellRenderer;
	private ImageIcon messageIcon;
	private SempClient sempV2ActionClient;
	private JComboBox<String> cboMsgsPerPage;
	private Config config; // For UI settings

	public Point mousePressPoint;
	private FilterSpecification spec = new FilterSpecification();
	private String lastIdAdded = "";
	int numberOfMessagesOnTheCurrentPage = -1;
	private String downloadFolder = "";
	
	private String headerFields[] = {"Destination","Delivery Mode", "Reply-To Destination", "Time-To-Live (TTL)",
			"DMQ Eligible", "Immediate Acknowledgement", "Redelivery Flag",
			"Deliver-To-One", "Class of Service (CoS)", "Eliding Eligible",
			"Message ID", "Correlation ID", "Message Type", "Encoding"}; //, "Compression"

	private enum eSelectAllState {eIndeterminant, eSelectedAll, eSelectedNone};
	private eSelectAllState currentSelectAllState = eSelectAllState.eIndeterminant; 
	
	public BrowserDialog(SempClient sempV2ActionClient, Broker b, String queue, JFrame frame, int nEstimatedMessageCount, String[] otherQueues, String downloadFolder, Config config) throws SempException {
		this.queue = queue;
		this.otherQueues = otherQueues;
		this.parentFrame = frame;
		this.broker = b;
		this.estimatedPageCount = (nEstimatedMessageCount / nItemsPerPage) + 1;
		this.iconCellRenderer = new IconicTableCellRenderer();
		this.messageIcon = new ImageIcon("config/messageIcon32.png");
		this.sempV2ActionClient = sempV2ActionClient;
		this.downloadFolder = downloadFolder;
		this.config = config != null ? config : new Config(""); // Fallback if null
		//spec.bodyValue = "the text you seek";
		
		// Initialize browser - this will throw SempException if SMF connection fails
		try {
			this.initialize();
		} catch (SempException e) {
			// Re-throw to let caller handle and show error dialog
			this.browser = null; // Mark as failed
			throw e;
		}
	}

	private void initialize() throws SempException {
		try {
			this.browser = new PaginatedCachingBrowser(broker, this.queue, nItemsPerPage);
			this.browser.setFilter(spec);
			
			// Test the connection immediately by trying to get the first page
			// This will fail fast if SMF connection doesn't work
			try {
				java.util.ArrayList<com.solacesystems.jcsmp.BytesXMLMessage> testPage = this.browser.getPage(0);
			} catch (Exception e) {
				// Connection test failed - this means SMF is not working
				String errorMsg = buildDetailedErrorMessage("SMF (Messaging) connection test failed", e);
				throw new SempException(errorMsg);
			}
		} catch (SempException e) {
			// Re-throw SempException as-is
			throw e;
		} catch (Exception e) {
			// SMF connection failed or any other error during browser creation
			String errorMsg = buildDetailedErrorMessage("SMF (Messaging) connection failed", e);
			throw new SempException(errorMsg);
		}
	}
	
	/**
	 * Build a detailed error message from an exception, including root cause
	 */
	private String buildDetailedErrorMessage(String baseMessage, Exception e) {
		StringBuilder sb = new StringBuilder();
		sb.append(baseMessage).append(":\n\n");
		sb.append("Error: ").append(e.getClass().getSimpleName()).append("\n");
		sb.append("Message: ").append(e.getMessage()).append("\n\n");
		
		// Include root cause if available
		Throwable cause = e.getCause();
		if (cause != null) {
			sb.append("Root Cause: ").append(cause.getClass().getSimpleName()).append("\n");
			sb.append("Root Cause Message: ").append(cause.getMessage()).append("\n\n");
		}
		
		sb.append("Cannot browse messages. Please check:\n");
		sb.append("- Your messaging credentials (username/password)\n");
		sb.append("- Network connectivity to the broker\n");
		sb.append("- Broker host and port settings\n");
		sb.append("- VPN name configuration");
		
		return sb.toString();
	}
	
	/**
	 * Get font from config or use FlatLaf default
	 * @param size Font size
	 * @param style Font style (Font.PLAIN, Font.BOLD, etc.)
	 * @return Font instance
	 */
	private Font getFont(int size, int style) {
		if (config != null && config.fontFamily != null && !config.fontFamily.isEmpty()) {
			return new Font(config.fontFamily, style, size);
		}
		// Use FlatLaf default font (OS-agnostic)
		return UIManager.getFont("Label.font").deriveFont(style, size);
	}

	@SuppressWarnings("serial")
	void run() throws JCSMPException {
		// Check if browser was successfully initialized
		if (this.browser == null) {
			String errorMsg = "SMF (Messaging) connection failed. Cannot browse messages.\n\n" +
				"Please check your messaging credentials and network connectivity.";
			JOptionPane.showMessageDialog(parentFrame, 
				errorMsg,
				"SMF Connection Failed",
				JOptionPane.ERROR_MESSAGE);
			return; // Don't show empty dialog
		}
		
		int totalTableWidth = 1480;
		// Create the dialog
		String versionStr = config != null ? config.version : "v2.0.2";
		dialog = new JDialog(parentFrame, "Solace Queue Browser - " + this.queue + " [" + versionStr + "]", true);
		dialog.setSize(1600, 1200);
		dialog.setLayout(new BorderLayout());
		dialog.setModal(false);

		// Create the top panel
		JPanel topPanel = new JPanel(new BorderLayout());

		JButton refreshTopButton = new JButton("Refresh");
        refreshTopButton.setBackground(new Color(220, 245, 255)); // Soft cyan background
        refreshTopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onRefresh();
            }
        });
        

		// Create filter button for top row
		JButton filterTopButton = new JButton("Filter");
		filterTopButton.setEnabled(true);
		filterTopButton.setBackground(new Color(240, 230, 255)); // Soft purple background
		filterTopButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onClickFilter(dialog, tableModel, filterTopButton);
			}
		});

		
		cboMsgsPerPage = new JComboBox<>(getPageSizes());
		cboMsgsPerPage.setSelectedItem ("" + nItemsPerPage);
		cboMsgsPerPage.setEditable(true);
		cboMsgsPerPage.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
		    public void keyReleased(KeyEvent e) {
		        String input = ((JTextField) cboMsgsPerPage.getEditor().getEditorComponent()).getText();
		        for (int i = 0; i < cboMsgsPerPage.getItemCount(); i++) {
		            if (cboMsgsPerPage.getItemAt(i).toString().startsWith(input)) {
		                cboMsgsPerPage.setSelectedIndex(i);
		                break;
		            }
		        }
		    }
		});
		cboMsgsPerPage.addActionListener(e -> {
		    Object selected = cboMsgsPerPage.getSelectedItem();
		    int selectedValue = Integer.parseInt(selected.toString()); 
		    if (selectedValue != nItemsPerPage) {
		    	nItemsPerPage = selectedValue; 
			    //System.out.println("Selected: " + nItemsPerPage);
		    	onRefresh();
		    }
		});

		JButton previousPageButton = new JButton("<< Prev");
		previousPageButton.setEnabled(false);
		previousPageButton.setBackground(new Color(230, 240, 255)); // Soft light blue background
		previousPageButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onPreviousPage(dialog, tableModel, previousPageButton);
			}
		});

		nextPageButton = new JButton("Next >>");
		nextPageButton.setEnabled(false);
		nextPageButton.setBackground(new Color(230, 240, 255)); // Soft light blue background
		nextPageButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onNextPage(dialog, tableModel, previousPageButton);
			}
		});

		// Use improved topRowPanel layout from nram-dev but with Serif fonts
		// Use Serif font like the queue details panel for consistency
		String fontFamily = (config != null && config.fontFamily != null && !config.fontFamily.isEmpty()) ? config.fontFamily : "Serif";
		
		// New top row layout: Browsing QUEUE_NAME | [<< Prev] Page N of ~ M [Next >>] | Page Size S | [Filter] (Status) | [Refresh]
		String filterStatus = spec.isEmpty() ? "OFF" : "ON";
		JPanel topRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		
		JLabel browsingLabel = new JLabel("Browsing: " + this.queue);
		browsingLabel.setFont(new Font(fontFamily, Font.PLAIN, 16));
		topRowPanel.add(browsingLabel);
		
		JLabel separator1 = new JLabel("|");
		separator1.setFont(new Font(fontFamily, Font.PLAIN, 16));
		topRowPanel.add(separator1);
		
		topRowPanel.add(previousPageButton);
		
		topLabel = new JLabel("Page " + nCurPage + " of ~ " + estimatedPageCount);
		topLabel.setFont(new Font(fontFamily, Font.PLAIN, 16));
		topRowPanel.add(topLabel);
		
		topRowPanel.add(nextPageButton);
		
		JLabel separator2 = new JLabel("|");
		separator2.setFont(new Font(fontFamily, Font.PLAIN, 16));
		topRowPanel.add(separator2);
		
		JLabel pageSizeLabel = new JLabel("Page Size");
		pageSizeLabel.setFont(new Font(fontFamily, Font.PLAIN, 16));
		topRowPanel.add(pageSizeLabel);
		topRowPanel.add(cboMsgsPerPage);
		
		JLabel separator3 = new JLabel("|");
		separator3.setFont(new Font(fontFamily, Font.PLAIN, 16));
		topRowPanel.add(separator3);
		
		topRowPanel.add(filterTopButton);
		
		filterStatusLabel = new JLabel("(" + filterStatus + ")");
		filterStatusLabel.setFont(new Font(fontFamily, Font.PLAIN, 16));
		topRowPanel.add(filterStatusLabel);
		
		JLabel separator4 = new JLabel("|");
		separator4.setFont(new Font(fontFamily, Font.PLAIN, 16));
		topRowPanel.add(separator4);
		
		topRowPanel.add(refreshTopButton);
		topRowPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

		JPanel headerLabel = new JPanel(new BorderLayout());
		headerLabel.add(topRowPanel, BorderLayout.CENTER);
		
		topPanel.add(headerLabel, BorderLayout.NORTH);
		
		String[][] data = new String[][] {};
		String[] columnNames = { "Select", "", "Message Id", "Size", "Redelivered?" };

		// Create the table model
		tableModel = new DefaultTableModel(data, columnNames) {
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

		// Create the table with the table model
		table = new JTable(tableModel);
		table.setRowHeight(33);
        table.setDragEnabled(true);

		// Set a custom cell renderer to alternate row colors
		table.setDefaultRenderer(Object.class, new AlternatingRowColorRenderer());
		table.getColumnModel().getColumn(1).setCellRenderer(iconCellRenderer);
		
		table.getColumnModel().getColumn(0).setPreferredWidth(36);
		table.getColumnModel().getColumn(1).setPreferredWidth(36);
		int remainindWidth = totalTableWidth - 72;
        table.getColumnModel().getColumn(2).setPreferredWidth(remainindWidth/3);
        table.getColumnModel().getColumn(3).setPreferredWidth(remainindWidth/3);
        table.getColumnModel().getColumn(4).setPreferredWidth(remainindWidth/3);
        
		// Enable gridlines with very light color
		table.setShowGrid(true);
		table.setGridColor(new Color(240, 240, 240)); // Very light gray, almost invisible
		table.addMouseListener(new TableMouseListener(table, this));
		table.addMouseMotionListener(new TableMouseMotionListener(table));
		table.setTransferHandler(new QueueMessageTransferInstigatorHandler(this, "source"));

		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("UP"), "upArrow");
		table.getActionMap().put("upArrow", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				if (row > 0) {
					selectedRow = row - 1;
					table.setRowSelectionInterval(selectedRow, selectedRow);
					table.scrollRectToVisible(table.getCellRect(table.getSelectedRow(), 0, true));
					onSelectMessage(table, selectedRow);
				}
			}
		});

		// DOWN arrow key binding
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("DOWN"),"downArrow");
		table.getActionMap().put("downArrow", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				if (row < (numberOfMessagesOnTheCurrentPage - 1)) {
					selectedRow = row + 1;
					table.setRowSelectionInterval(selectedRow, selectedRow);
					table.scrollRectToVisible(table.getCellRect(table.getSelectedRow(), 0, true));
					onSelectMessage(table, selectedRow);
				}
			}
		});
		
		JTableHeader header = table.getTableHeader();
		header.addMouseListener(new MouseAdapter() {
		    @Override
		    public void mouseClicked(MouseEvent e) {
		        int col = header.columnAtPoint(e.getPoint());
		        if (col == 0) { 
		        	boolean newValue = true; 
		        	if (currentSelectAllState == eSelectAllState.eSelectedAll) {
		        		newValue = false;
		        		currentSelectAllState = eSelectAllState.eSelectedNone;
		        		setStatus("De-selected all messages");
		        	}
		        	else {
		        		currentSelectAllState = eSelectAllState.eSelectedAll;
		        		setStatus(table.getRowCount() + " messages selected");
		        	}
		            for (int row = 0; row < table.getRowCount(); row++) {
		                table.setValueAt(newValue, row, col);
		            }
		            table.clearSelection();
		            if (newValue) {
		            	table.setRowSelectionInterval(0, table.getRowCount() - 1);
		            	JOptionPane.showMessageDialog(dialog, "Multi-message selection is in beta. Some features may not work as expected!", "Notice", JOptionPane.INFORMATION_MESSAGE);
		            }
		            table.repaint();
		        }
		    }
		});

		JScrollPane listScrollPane = new JScrollPane(table);
		listScrollPane.setPreferredSize(new Dimension(380, 400));
		topPanel.add(listScrollPane, BorderLayout.CENTER);

		delButton = new JButton("Delete");
		delButton.setEnabled(false);
		delButton.setBackground(new Color(255, 220, 220)); // Soft red background
		delButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onDeleteMessage(table, dialog);
			}
		});

		nextMsgButton = new JButton("Next>");
		nextMsgButton.setEnabled(false);
		nextMsgButton.setBackground(new Color(230, 240, 255)); // Soft light blue background
		nextMsgButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onNextMessage();
			}
		});
		prevMsgButton = new JButton("<Prev");
		prevMsgButton.setEnabled(false);
		prevMsgButton.setBackground(new Color(230, 240, 255)); // Soft light blue background
		prevMsgButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onPreviousMessage();
			}
		});
		copyMessageMsgButton = new JButton("Copy");
		copyMessageMsgButton.setEnabled(false);
		copyMessageMsgButton.setBackground(new Color(220, 235, 255)); // Soft blue background
		copyMessageMsgButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onCopyMessage();
			}
		});

		moveMessageMsgButton = new JButton("Move");
		moveMessageMsgButton.setEnabled(false);
		moveMessageMsgButton.setBackground(new Color(255, 245, 220)); // Soft yellow background
		moveMessageMsgButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onMoveMessage();
			}
		});
        comboBox = new JComboBox<>(otherQueues);
        Dimension preferredSize = comboBox.getPreferredSize();
        preferredSize.width = 400;
        comboBox.setPreferredSize(preferredSize);

        downloadMessageMsgButton = new JButton("Download");
        downloadMessageMsgButton.setEnabled(false);
        downloadMessageMsgButton.setBackground(new Color(220, 255, 220)); // Soft green background
        downloadMessageMsgButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onDownloadMessage();
			}
		});

		JPanel buttonLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buttonLeftPanel.add(prevMsgButton);
		buttonLeftPanel.add(nextMsgButton);
		buttonLeftPanel.add(moveMessageMsgButton);
		buttonLeftPanel.add(copyMessageMsgButton);
		
		// Add "Target Queue" label before comboBox
		// Use the fontFamily already defined earlier in the method
		JLabel targetQueueLabel = new JLabel("Target Queue");
		targetQueueLabel.setFont(new Font(fontFamily, Font.PLAIN, 16));
		buttonLeftPanel.add(targetQueueLabel);
		buttonLeftPanel.add(comboBox);
		buttonLeftPanel.add(downloadMessageMsgButton);
		buttonLeftPanel.add(delButton);

		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(buttonLeftPanel, BorderLayout.WEST);

		// buttonPanel.add(new JButton("Button 2"));
		topPanel.add(buttonPanel, BorderLayout.SOUTH);

		// Create the bottom panel
		JPanel bottomPanel = new JPanel(new BorderLayout());

		// Add a label at the top of the bottom panel
		//JLabel bottomLabel = new JLabel("Payload");
		//bottomPanel.add(bottomLabel, BorderLayout.NORTH);

		// Add a large text area to the bottom panel
		JPanel payloadPanel = new JPanel();
		payloadPanel.setLayout(new BoxLayout(payloadPanel, BoxLayout.Y_AXIS));

		JLabel payloadLabel = new JLabel("Payload");
		payloadLabel.setFont(new Font(fontFamily, Font.PLAIN, 16));
		payloadPanel.add(payloadLabel);
		textArea = new JTextArea(10, 40);
		payloadPanel.add(textArea);
		JScrollPane textAreaScrollPane = new JScrollPane(payloadPanel);

		String[][] headerData = new String[][] {};
		String[] headerColumnNames = {"Property", "Value" };
		headersTableModel = new DefaultTableModel(headerData, headerColumnNames) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false; // Disable editing for all cells
			}
		};
		// Create the table with the table model
		headersTable = new JTable(headersTableModel);
		headersTable.setRowHeight(24);

		String[][] propsData = new String[][] {};
		String[] propColumnNames = {"Property", "Value" };
		propsTableModel = new DefaultTableModel(propsData, propColumnNames) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false; // Disable editing for all cells
			}
		};
		// Create the table with the table model
		propsTable = new JTable(propsTableModel);
		propsTable.setRowHeight(24);
				
		
		JPanel propsTablesPanel = new JPanel();
		propsTablesPanel.setLayout(new BoxLayout(propsTablesPanel, BoxLayout.Y_AXIS));

		// Add a label at the top of the bottom panel
		JLabel headersLabel = new JLabel("Headers");
		headersLabel.setFont(new Font(fontFamily, Font.PLAIN, 16));
		propsTablesPanel.add(headersLabel);
		propsTablesPanel.add(headersTable);
		JLabel userPropsLabel = new JLabel("User Properties");
		userPropsLabel.setFont(new Font(fontFamily, Font.PLAIN, 16));
		propsTablesPanel.add(userPropsLabel);
		propsTablesPanel.add(propsTable);
		

		JScrollPane propsAreaScrollPane = new JScrollPane(propsTablesPanel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, propsAreaScrollPane, textAreaScrollPane);
        splitPane.setDividerLocation(350); // Initial divider position
        splitPane.setOneTouchExpandable(true); // Adds little arrows to collapse/expand
		
		bottomPanel.add(splitPane, BorderLayout.CENTER);

		statusLabel = new JLabel("Browsing " + this.queue);
		// Use Serif font like the queue details panel for consistency
		String statusFontFamily = (config != null && config.fontFamily != null && !config.fontFamily.isEmpty()) ? config.fontFamily : "Serif";
		int statusFontSize = config != null ? config.statusFontSize : 16;
		statusLabel.setFont(new Font(statusFontFamily, Font.PLAIN, statusFontSize));
		bottomPanel.add(statusLabel, BorderLayout.SOUTH);

		
		// Add the top and bottom panels to the dialog
		dialog.add(topPanel, BorderLayout.NORTH);
		dialog.add(bottomPanel, BorderLayout.CENTER);

		// Center the dialog on the screen
		dialog.setLocationRelativeTo(parentFrame);
		dialog.setLocation(parentFrame.getLocation().x + 10, parentFrame.getLocation().y + 10);

		// Make the dialog visible
		// dialog.setVisible(true);

		// Show dialog first, then try to load messages
		dialog.setVisible(true);
		
		// Try to load messages - if this fails, show error dialog
		SwingUtilities.invokeLater(() -> {
			try {
				dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				
				// Test connection by trying to get first page
				nCurPage = 0;
				Object[][] testData = null;
				try {
					testData = this.getMessages();
				} catch (BrokerException e) {
					// SMF connection failed - show error and close dialog
					String errorMsg = buildDetailedErrorMessage("SMF (Messaging) connection failed", e);
					// Show error dialog immediately - we're already on EDT from invokeLater
					JOptionPane.showMessageDialog(dialog, 
						errorMsg,
						"SMF Connection Failed",
						JOptionPane.ERROR_MESSAGE);
					dialog.dispose(); // Close the empty dialog
					dialog.setCursor(Cursor.getDefaultCursor());
					return; // Don't continue
				} catch (Exception e) {
					// Any other exception
					String errorMsg = buildDetailedErrorMessage("Failed to load messages", e);
					// Show error dialog immediately - we're already on EDT from invokeLater
					JOptionPane.showMessageDialog(dialog, 
						errorMsg,
						"Error Loading Messages",
						JOptionPane.ERROR_MESSAGE);
					dialog.dispose(); // Close the empty dialog
					dialog.setCursor(Cursor.getDefaultCursor());
					return; // Don't continue
				}
				
				// If we got here, messages loaded successfully
				display(tableModel, testData);
				rowCount = testData != null ? testData.length : 0;
				
				preFetch();
				onPageChange();
				
				if (rowCount > 0) {
					autoSelectFirstRow();
				}
			} catch (Exception e) {
				// Handle any other errors during initialization
				String errorMsg = buildDetailedErrorMessage("Failed to initialize message browser", e);
				JOptionPane.showMessageDialog(dialog, 
					errorMsg,
					"Error Loading Messages",
					JOptionPane.ERROR_MESSAGE);
				dialog.dispose(); // Close the empty dialog
			} finally {
				dialog.setCursor(Cursor.getDefaultCursor());
			}
		});
	}
	private String[] getPageSizes() {
		return new String[] {"10", "20", "100", "200"};
	}
	
	private void onRefresh() {
		dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		try {
			initialize();
		} catch (SempException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		SwingUtilities.invokeLater(() -> {
			restartAfterFilter("Refreshing");
		});
	}
	private void onClickFilter(JDialog dialog, DefaultTableModel tableModel, JButton filterButton) {
		
		FilterDialog filterD = new FilterDialog(dialog, this.spec);
		filterD.run();
		
		if (filterD.cancelled == false) {
	
			try {
				initialize();
			} catch (SempException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	//		dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	 
			SwingUtilities.invokeLater(() -> {
				restartAfterFilter("Applying Filter");
			});
		}
		
	}
	private void restartAfterFilter(String title) {

		SpinnerDialog spinner = new SpinnerDialog(dialog, title);

		// Update filter status label - just show ON or OFF
		String filterStatus = spec.isEmpty() ? "OFF" : "ON";
		if (filterStatusLabel != null) {
			filterStatusLabel.setText("(" + filterStatus + ")");
		}

		tableModel.setRowCount(0);
		preFetch();
		nCurPage = 0;
		onNextPage(dialog, tableModel, nextPageButton);

		spinner.setVisible(false);
	}

	private void autoSelectFirstRow() {
		table.setRowSelectionInterval(0, 0);
		onSelectMessage(table, 0);
	}
	
	private void onMoveMessage() {
		moveOrCopy(true);
	}
	private void onCopyMessage() {
		moveOrCopy(false);
	}
	
	private void moveOrCopy(boolean deleteFromSource) {
		ArrayList<String> ids = getAllSelectedMessageIds();
		if (ids.size() > 1) {
			for (String id : ids) {
				moveOrCopyMessage(id, deleteFromSource, false);
			}
			String action = "copied";
			if (deleteFromSource == true) {
				action = "moved";
			}
			String selectedTargetQueue = (String) comboBox.getSelectedItem();
			setStatus(ids.size() + " messages were " + action+ " to " + selectedTargetQueue);

			// Update the display to remove all moved messages
			if (deleteFromSource) {
				updateDisplayAfterMultipleDeletes(ids);
			}
		}
		else {
			String id = ids.get(0);
			moveOrCopyMessage(id, deleteFromSource, true);
		}
	}
	private void moveOrCopyMessage(String id, boolean deleteFromSource, boolean showStatus) {
		String selectedTargetQueue = (String) comboBox.getSelectedItem();

		BytesXMLMessage msg = browser.get(id);
		ReplicationGroupMessageId replicationId = msg.getReplicationGroupMessageId();
		try {
			sempV2ActionClient.copy(broker.msgVpnName, queue, selectedTargetQueue, replicationId.toString());

			String action = "copied";
			if (deleteFromSource == true) {
				action = "moved";
			}
			String logMsg = "MessageId " + id + " (replication id='" + replicationId.toString() + "') was " + action +
					" from the '" + this.queue + "' queue to the '" + selectedTargetQueue + "'.";
			CommandLog.instance().log(logMsg);

			if (showStatus) {
				setStatus (logMsg);
			}

		} catch (SempException e1) {
			e1.printStackTrace();
		}
		if (deleteFromSource) {
			browser.delete(id);
			// For single message moves, remove the row immediately
			if (showStatus) {
				int selectedRow = table.getSelectedRow();
				if (selectedRow != -1) {
					tableModel.removeRow(selectedRow);
					numberOfMessagesOnTheCurrentPage--;
				}
			}
		}
	}

	private void updateDisplayAfterMultipleDeletes(ArrayList<String> ids) {
		// Remove all rows corresponding to the deleted message IDs from the display
		boolean finished = false;
		while (!finished) {
			boolean deletedAnyThisRun = false;
			for (int rowIter = 0; rowIter < table.getRowCount(); rowIter++) {
				String id = (String) table.getValueAt(rowIter, nIdColumn);

				// Was this message deleted?
				for (String oneDeletedId : ids) {
					if (id.equals(oneDeletedId)) {
						tableModel.removeRow(rowIter);
						numberOfMessagesOnTheCurrentPage--;

						// Now the model is different, rows are offset, restart
						deletedAnyThisRun = true;
						break;
					}
				}
				if (deletedAnyThisRun) {
					// Found a match, restart the row iteration
					if (table.getRowCount() == 0) {
						finished = true;
					}
					break;
				}
			}
			// If we went through all rows without finding any matches, we're done
			if (!deletedAnyThisRun) {
				finished = true;
			}
		}

		// Reset selection state
		currentSelectAllState = eSelectAllState.eIndeterminant;

		// Auto-select the first row if there are messages remaining
		if (table.getRowCount() > 0) {
			autoSelectFirstRow();
		} else {
			clearMessageSelection();
		}
	}
	
	private String propsAsString(String[][] props) {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i < props.length; i++) {
			String[] row = props[i];
			String name = row[0];
			String value = row[1];
			sb.append(name + ": " + value + "\n");
		}
		return sb.toString();
	}
	
	private void makeDirIfRequired(String path) throws IOException {
		File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs(); // Creates directory and any necessary parent dirs
            if (!created) {
            	throw new IOException("Failed to create directory '" + path + "'.");
            }
        }
	}
	private void writeStringToFile(String fileName, String payload) throws IOException {
        @SuppressWarnings("resource")
		FileWriter writer = new FileWriter(fileName);
        writer.write(payload);
        writer.close();
	}
	private void deleteFile(String filePath) throws IOException {
	    File file = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
            	throw new IOException("Failed to deletre file " + filePath);
            }
        }
	}

	private void onDownloadMessage() {
		ArrayList<String> ids = getAllSelectedMessageIds();
		if (ids.size() > 1) {
			for (String id : ids) {
				downloadMessage(id, false);
			}
			setStatus(ids.size() + " messages were downloaded to " + this.downloadFolder);
		}
		else {
			String id = ids.get(0);
			downloadMessage(id, true);
		}
	}
	
	private void downloadMessage(String id, boolean showStatus) {
		try {
			//String id = getMessageIdOfSelectedRow();
			BytesXMLMessage message = this.browser.get(id);
			String payload = browser.getPayload(message);
			String[][] headers = getMessageHeadersData(message);
			String[][] userProps = getMessageUserPropsData(message);
			
			String folder = this.downloadFolder;
			makeDirIfRequired(folder);
			
			String payloadFile = folder + "/payload.txt"; 
			writeStringToFile(payloadFile, payload);
			
			payload = propsAsString(headers);
			String headersFile = folder + "/headers.txt"; 
			writeStringToFile(headersFile, payload);

			payload = propsAsString(userProps);
			String userPropsFile = folder + "/userProps.txt"; 
			writeStringToFile(userPropsFile, payload);

			//StringBuilder sb = new StringBuilder();
			String context = this.broker.name + "-" + this.broker.msgVpnName;
			Instant when = Instant.now();
			long lWhen = when.toEpochMilli();
			
			String zipFileName = folder + "/" + context + "-" + "msg-" + id + "-" + lWhen + ".zip";

	        FileOutputStream fos = new FileOutputStream(zipFileName);
            ZipOutputStream zipOut = new ZipOutputStream(fos);
            addToZip(zipOut, payloadFile);
            addToZip(zipOut, headersFile);
            addToZip(zipOut, userPropsFile);
            zipOut.close();
            fos.close();

            deleteFile(payloadFile);
            deleteFile(headersFile);
            deleteFile(userPropsFile);
            
            if (showStatus) {
            	setStatus("Downloaded message " + id + " to " + zipFileName) ;
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void addToZip(ZipOutputStream zipOut, String srcFile) throws IOException {
        File fileToZip = new File(srcFile);
        FileInputStream fis = new FileInputStream(fileToZip); 
        ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
        zipOut.putNextEntry(zipEntry);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = fis.read(buffer)) >= 0) {
            zipOut.write(buffer, 0, length);
        }
        fis.close();
	}
	
	private ArrayList<String> getAllSelectedMessageIds() {
		ArrayList<Integer> allSelectedRowNumbers = getAllSelectedRows();
		ArrayList<String> ids = new ArrayList<String>();
    	for (Integer i : allSelectedRowNumbers) {
    		String id = (String) table.getValueAt(i, nIdColumn);
    		ids.add(id);
    	}

    	// Fallback: if no checkboxes are checked but a row is selected,
    	// use the row selection (supports single-message operations)
    	if (ids.isEmpty()) {
    		int selectedRow = table.getSelectedRow();
    		if (selectedRow >= 0 && selectedRow < table.getRowCount()) {
    			String id = (String) table.getValueAt(selectedRow, nIdColumn);
    			ids.add(id);
    		}
    	}

    	return ids;
	}
	
	private void onDeleteMessage(JTable table, Component dialog) {
		ArrayList<Integer> allSelectedRowNumbers = getAllSelectedRows();
		String prompt = "";
		if (allSelectedRowNumbers.size() == 1) {
			String id = getMessageIdOfSelectedRow();
			prompt = "Are you sure you want to delete message (" + id + ")?"; 
		}
		else {
			prompt = "Are you sure you want to delete all " + allSelectedRowNumbers.size() + " rows?"; 
		}
		
		int response = JOptionPane.showConfirmDialog(dialog, prompt, "Confirmation", JOptionPane.YES_NO_OPTION);
        if (response == JOptionPane.YES_OPTION) {
    		ArrayList<String> ids = new ArrayList<String>();
        	for (Integer i : allSelectedRowNumbers) {
        		String id = (String) table.getValueAt(i, nIdColumn);
        		ids.add(id);
            	this.browser.delete(id);
            	String logMsg = "MessageId " + id + " was deleted from the '" + this.queue + "' queue.";
    			CommandLog.instance().log(logMsg);
        	}
        	
        	// now update the GUI
        	boolean finished = false;
        	while (! finished) {
        		boolean deletedAnyThisRun = false;
	    		for (int rowIter = 0; rowIter < table.getRowCount() ; rowIter++) {
	        		String id = (String) table.getValueAt(rowIter, nIdColumn);
	        		
	        		// was this message deleted?
	        		for (String oneDeletedId : ids) {
	        			if (id.equals(oneDeletedId)) {
	        				tableModel.removeRow(rowIter);
	                		numberOfMessagesOnTheCurrentPage--;
	                		
	                		// now the model is different, rows are offset, just restart and keep doing 
	                		// that until none get deleted
	                		deletedAnyThisRun = true;
	                		break;
	        			}
	        		}
	        		if (deletedAnyThisRun) {
	        			if (table.getRowCount() == 0) {
	        				finished = true;
	        			}
	        			break;
	        		}
	        		else {
	        			finished = true;
	        		}
	    		}
        	}
        	
//        	for (Integer i : all) {
//            	//doDelete(id);
//
//        		// now axe the row that was deleted
//        		if (selectedRow != -1) {
//        			tableModel.removeRow(i);
//        		}
//
//        	}
//    		if (all.size() == 1) {
//    			setStatus (logMsg);
//    		} 
//    		else {
//            	String logMsg = "";
//    			setStatus (all.size() + " messages were deleted.");
//    		}
        } 
	}
	private void doDelete(String id) {
		this.browser.delete(id);
		int selectedRow = table.getSelectedRow();
		
		numberOfMessagesOnTheCurrentPage--;

		// now axe the row that was deleted
		if (selectedRow != -1) {
			tableModel.removeRow(selectedRow);
		}

		if (numberOfMessagesOnTheCurrentPage == 0) {
			// this is the only message, thing else to select
			clearMessageSelection();
		}
		else if (selectedRow == this.numberOfMessagesOnTheCurrentPage) {
			// this is the last row, move back one
			onPreviousMessage();
		}
		else {
			// skip ahead first so that the onSelect event handling properly shows the next message
			onNextMessage();
		}
		
		
	}

	private void onNextMessage() {
		int nRow = this.selectedRow + 1;
		
		int max = table.getRowCount();
		if ((nRow < 0) || (nRow > (max -1))) {
			nRow = nRow;
		}
		
		table.setRowSelectionInterval(nRow, nRow);
		onSelectMessage(table, nRow);
	}
	private void onPreviousMessage() {
		int nRow = this.selectedRow - 1;
		table.setRowSelectionInterval(nRow, nRow);
		onSelectMessage(table, nRow);
	}

	private String getMessageIdOfSelectedRow() {
		return(String) table.getValueAt(this.selectedRow, nIdColumn);
	}
	private ArrayList<Integer> getAllSelectedRows() {
		ArrayList<Integer> selectedIndices = new ArrayList<>();
		for (int row = 0; row < table.getRowCount(); row++) {
		    Boolean checked = (Boolean) table.getValueAt(row, 0);
		    if (checked != null && checked) {
		        selectedIndices.add(row);
		    }
		}
		return selectedIndices;
	}
	
	private String getHeaderValue(BytesXMLMessage message, String field) {
		String rc = "";
		if (field.equals("Destination")) {
			rc = message.getDestination().getName();
		}
		else if (field.equals("Delivery Mode")) {
			rc = message.getDeliveryMode().name();
		}
		else if (field.equals("Reply-To Destination")) {
			Destination dest = message.getReplyTo();
			if (dest != null) {
				rc = dest.getName();
			}
		}
		else if (field.equals("Time-To-Live (TTL)")) {
			rc = "" + message.getTimeToLive();
		}
		else if (field.equals("DMQ Eligible")) {
			rc = "" + message.isDMQEligible();
		}
		else if (field.equals("Immediate Acknowledgement")) {
			rc = "" + message.isAckImmediately();
		}
		else if (field.equals("Redelivery Flag")) {
			rc = "" + message.getRedelivered();
		}
		else if (field.equals("Deliver-To-One")) {
			rc = "" + message.getDeliverToOne();
		}
		else if (field.equals("Class of Service (CoS)")) {
			rc = "" + message.getCos().ordinal();
		}
		else if (field.equals("Eliding Eligible")) {
			rc = "" + message.isElidingEligible();
		}
		else if (field.equals("Message ID")) {
			rc = message.getMessageId();
		}
		else if (field.equals("Correlation ID")) {
			rc = message.getCorrelationId();
		}
		else if (field.equals("Message Type")) {
			rc = message.getMessageType().toString();
		}
		else if (field.equals("Encoding")) {
			rc = message.getHTTPContentEncoding();
		}
		return rc;
	}
	private String[][] getMessageHeadersData(BytesXMLMessage message) {
		String[][] data = new String[headerFields.length][];
		for (int i = 0; i < headerFields.length; i++) {
			String value = "?";
			data[i] = new String[2];
			data[i][0] = headerFields[i];
			data[i][1] = getHeaderValue(message, headerFields[i]);
		}
		return data;
	}
	
	private String[][] getMessageUserPropsData(BytesXMLMessage message) throws SDTException {
		SDTMap map = message.getProperties();
		String[][] data = null;
		if (map != null) {
			data = new String[map.size()][];
	
			Set<String> keys = map.keySet();
			int i = 0;
			for (String key : keys) {
		        Object value = map.get(key);
				data[i] = new String[2];
				data[i][0] = key;
				data[i][1] = value.toString();
				i++;
			}
		}
		else {
			data = new String[0][];
		}
		return data;
	}
	
	private void clearMessageSelection() {
		DefaultTableModel headersTableModel = (DefaultTableModel) headersTable.getModel();
		headersTableModel.setRowCount(0); // Clears all existing rows
		DefaultTableModel propsTableModel = (DefaultTableModel) propsTable.getModel();
		propsTableModel.setRowCount(0); // Clears all existing rows

		nextMsgButton.setEnabled(false);
		delButton.setEnabled(false);
		prevMsgButton.setEnabled(false);
		
		moveMessageMsgButton.setEnabled(false);
		copyMessageMsgButton.setEnabled(false);
		downloadMessageMsgButton.setEnabled(false);
		textArea.setText("");

		setStatus("Noting selected - selectedRow=" + selectedRow + ",total=" + this.numberOfMessagesOnTheCurrentPage ) ;
		
		this.selectedRow = -1;

	}
	private void onSelectMessage(JTable table, int row) {
		try {
			this.selectedRow = row;
			String id = getMessageIdOfSelectedRow();
			String payload = browser.getPayload(id);
			textArea.setText(payload);
			textArea.setCaretPosition(0);

			boolean moreRowsAvailable = false;
			if (row < (numberOfMessagesOnTheCurrentPage - 1)) {
				moreRowsAvailable = true;
			}
			
			// populate the headers and user proprties
			DefaultTableModel headersTableModel = (DefaultTableModel) headersTable.getModel();
			headersTableModel.setRowCount(0); // Clears all existing rows
			DefaultTableModel propsTableModel = (DefaultTableModel) propsTable.getModel();
			propsTableModel.setRowCount(0); // Clears all existing rows
			
			BytesXMLMessage message = this.browser.get(id);
			Object[][] newHeadersData = getMessageHeadersData(message);// new String[][] {};
			for (Object[] rowData : newHeadersData) {
				headersTableModel.addRow(rowData); // Add new rows
			}
			Object[][] newData = getMessageUserPropsData(message);// new String[][] {};
			for (Object[] rowData : newData) {
				propsTableModel.addRow(rowData); // Add new rows
			}

			
			nextMsgButton.setEnabled(moreRowsAvailable);
			delButton.setEnabled(true);
			prevMsgButton.setEnabled(row > 0);
			
			moveMessageMsgButton.setEnabled(true);
			copyMessageMsgButton.setEnabled(true);
			downloadMessageMsgButton.setEnabled(true);
			
			setStatus("Viewing message " + id + "; selectedRow=" + selectedRow + ",total=" + this.numberOfMessagesOnTheCurrentPage ) ;
		} catch (Throwable t) {
			System.out.println(t.getLocalizedMessage());
		}
	}

	boolean cantBrowseWarningIssuedAlready = false;
	boolean smfConnectionErrorShown = false; // Track if we've already shown SMF error
	private void preFetch() {
		try {
			semaphore.acquire();
			browser.prefetchNextPage();
		} catch (BrokerException e) {
			if (e.getMessage() != null && e.getMessage().contains("Browsing Not Supported on Partitioned Queue")) {
				if (! cantBrowseWarningIssuedAlready) {
					JOptionPane.showMessageDialog(this.dialog, "That queue is a partitioned queue. Browsing is not supported on Partitioned Queues");
					cantBrowseWarningIssuedAlready = true;
				}
			} else {
				// SMF connection error - show error dialog to user
				if (!smfConnectionErrorShown && this.dialog != null) {
					smfConnectionErrorShown = true;
					String errorMsg = buildDetailedErrorMessage("SMF (Messaging) connection failed during prefetch", e);
					// Show error dialog on EDT
					SwingUtilities.invokeLater(() -> {
						JOptionPane.showMessageDialog(this.dialog, 
							errorMsg,
							"SMF Connection Failed",
							JOptionPane.ERROR_MESSAGE);
					});
				}
			}
		} catch (InterruptedException e) {
			// Interrupted - ignore
		} catch (Exception e) {
			// Catch any other exceptions
			if (!smfConnectionErrorShown && this.dialog != null) {
				smfConnectionErrorShown = true;
				String errorMsg = buildDetailedErrorMessage("Unexpected error during prefetch", e);
				// Show error dialog on EDT
				SwingUtilities.invokeLater(() -> {
					JOptionPane.showMessageDialog(this.dialog, 
						errorMsg,
						"Error",
						JOptionPane.ERROR_MESSAGE);
				});
			}
		} finally {
			semaphore.release();
		}
	}

	private void onPageChange() {
		// Update topLabel with plain text (no HTML) using Serif font
		topLabel.setText("Page " + nCurPage + " of ~ " + estimatedPageCount);
		textArea.setText("");
	}

	private void display(DefaultTableModel tableModel, Object[][] dataUpdate) {
		tableModel.setRowCount(0);
		numberOfMessagesOnTheCurrentPage = 0;
		for (Object[] oneRow : dataUpdate) {
			tableModel.addRow(oneRow);
			lastIdAdded = (String) oneRow[nIdColumn];
			numberOfMessagesOnTheCurrentPage++;
		}
		
		if (dataUpdate.length > 0) {
			autoSelectFirstRow();
		}
	}

	private void onPreviousPage(JDialog dialog, DefaultTableModel tableModel, JButton backButton) {
		dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		nCurPage--;
		
		Object[][] dataUpdate = null;
		try {
			dataUpdate = this.getMessages();
		} catch (BrokerException e) {
			// Show error dialog if SMF connection fails
			String errorMsg = buildDetailedErrorMessage("SMF (Messaging) connection failed", e);
			JOptionPane.showMessageDialog(dialog, 
				errorMsg,
				"SMF Connection Failed",
				JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
			// Set empty data so dialog shows but with no messages
			dataUpdate = new Object[0][];
		} catch (Exception e) {
			// Catch any other exceptions
			String errorMsg = buildDetailedErrorMessage("Failed to load messages", e);
			JOptionPane.showMessageDialog(dialog, 
				errorMsg,
				"Error Loading Messages",
				JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
			dataUpdate = new Object[0][];
		} 
		display(tableModel, dataUpdate);

		if (nCurPage < 2) {
			backButton.setEnabled(false);
		}
		nextPageButton.setEnabled(shouldNextPageButtonBeActive());

		onPageChange();
		dialog.setCursor(Cursor.getDefaultCursor());
		SwingUtilities.invokeLater(() -> {
			autoSelectFirstRow();
		});
	}

	private boolean shouldNextPageButtonBeActive() {
		return browser.hasMoreAfterId(lastIdAdded);
	}
	int rowCount = 0;
	private void onNextPage(JDialog dialog, DefaultTableModel tableModel, JButton backButton) {
		dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		nCurPage++;
		Object[][] dataUpdate = null;
		
		try {
			dataUpdate = this.getMessages();
			rowCount = dataUpdate.length;
		} catch (BrokerException e) {
			// Show error dialog if SMF connection fails
			String errorMsg = buildDetailedErrorMessage("SMF (Messaging) connection failed", e);
			// Show error dialog immediately
			JOptionPane.showMessageDialog(dialog, 
				errorMsg,
				"SMF Connection Failed",
				JOptionPane.ERROR_MESSAGE);
			// Set empty data so dialog shows but with no messages
			dataUpdate = new Object[0][];
			rowCount = 0;
		} catch (Exception e) {
			// Catch any other exceptions
			String errorMsg = buildDetailedErrorMessage("Unexpected error loading messages", e);
			JOptionPane.showMessageDialog(dialog, 
				errorMsg,
				"Error Loading Messages",
				JOptionPane.ERROR_MESSAGE);
			dataUpdate = new Object[0][];
			rowCount = 0;
		}
		display(tableModel, dataUpdate);
		
		backButton.setEnabled(true);
		
		// see if the browser has any more messages after the last one onscreen
		nextPageButton.setEnabled(shouldNextPageButtonBeActive());
		onPageChange();
		dialog.setCursor(Cursor.getDefaultCursor());

		SwingUtilities.invokeLater(() -> {
			
			if (rowCount > 0) {
				autoSelectFirstRow();
			}
			preFetch();
		});
	}

	private Object[][] getMessages() throws BrokerException {
		// Create an ArrayList of ArrayList<String> to store the data
		ArrayList<ArrayList<String>> dynamicArray = new ArrayList<>();
		ArrayList<BytesXMLMessage> thisPage = browser.getPage(nCurPage - 1); // its star6 counting at 0

		for (BytesXMLMessage message : thisPage) {
			ArrayList<String> row = new ArrayList<>();
			@SuppressWarnings("deprecation")
			String id = message.getMessageId();
			row.add(id);

			String payload = this.browser.getPayload(message);
			int size = payload.length();
//			if (size == 0) {
//				size = message.getBinaryMetadataContentLength(size);
//			}
			row.add("" + size);

			String yN = "No";
			if (message.getRedelivered()) {
				yN = "Yes";
			}
			row.add(yN);

			//			String payload = null;
			//			if (message instanceof TextMessage) {
			//				TextMessage txt = (TextMessage) message;
			//				payload = txt.getText();
			//			} else {
			//				byte[] b = message.getBytes();
			//				payload = new String(b);
			//			}
			dynamicArray.add(row);
		}

		// Convert the ArrayList to a 2D array
		Object[][] data = new Object[dynamicArray.size()][];
		for (int i = 0; i < dynamicArray.size(); i++) {
			ArrayList<String> row = dynamicArray.get(i);
			data[i] = new Object[row.size() + 2];
			data[i][0] = false;
			data[i][1] = messageIcon;
			for (int y = 0; y < row.size(); y++) {
				data[i][y+2] = row.get(y);	
			}
		}
		return data;
	}
	
    private class TableMouseListener extends MouseAdapter {
        private final JTable table;
		BrowserDialog browserDialog;
        public TableMouseListener(JTable table, BrowserDialog browserDialog) {
            this.table = table;
            this.browserDialog = browserDialog; 
        }

        @Override
        public void mousePressed(MouseEvent e) {
            mousePressPoint = e.getPoint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            mousePressPoint = null;
            // Handle selection logic here if needed
            handleSelection(e);
        }

        private void handleSelection(MouseEvent e) {
            int row = table.rowAtPoint(e.getPoint());
            table.setRowSelectionInterval(row, row);
            
//			int row = table.rowAtPoint(e.getPoint());
            
    		SwingUtilities.invokeLater(() -> {
    	           browserDialog.onSelectMessage(table, row);
    		});

        }
    }
    private class TableMouseMotionListener extends MouseMotionAdapter {
        private final JTable table;

        public TableMouseMotionListener(JTable table) {
            this.table = table;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (mousePressPoint != null) {
                Point dragPoint = e.getPoint();
                int dragDistance = (int) mousePressPoint.distance(dragPoint);
                if (dragDistance > 5) { // Threshold distance to start drag
                    TransferHandler handler = table.getTransferHandler();
                    handler.exportAsDrag(table, e, TransferHandler.MOVE);
                }
            }
        }
    }

	@Override
	public DroppableMessage getMessageBeingDragged(int row) {
		String id = (String) table.getValueAt(row, nIdColumn);
		BytesXMLMessage msg = browser.get(id);
		ReplicationGroupMessageId replicationId = msg.getReplicationGroupMessageId();
		
		DroppableMessage dmsg = new DroppableMessage();
		dmsg.id = id;
		dmsg.queue = this.queue;
		dmsg.replicationId = replicationId.toString();
		dmsg.source = this;
		return dmsg;
	}

	private void setStatus(String txt) {
		SwingUtilities.invokeLater(() -> {
			statusLabel.setText(txt);
		});
	}
	@Override
	public void onMessageWasMoved(DroppableMessage msg) {
		doDelete(msg.id);
		setStatus("Message " + msg.id + " was moved from " + msg.queue + " to " + msg.targetQueue);
		
		
		String logMsg = "MessageId " + msg.id + " (replication id='" + msg.replicationId.toString() + "') was moved " +  
				" from the '" + msg.queue + "' queue to the '" + msg.targetQueue + "'.";
		CommandLog.instance().log(logMsg);
		
		//onNextMessage(this.table);		
	}
}