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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import javax.swing.JCheckBox;
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
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class BrowserDialog implements IDragDropInstigator {
	private static final Logger logger = LoggerFactory.getLogger(BrowserDialog.class.getName());
	
	// Helper method to log to both logger and stdout
	private void logBoth(String message) {
		logger.info(message);
		System.out.println(message);
	}
	
	// Helper method to log with thread information
	private void logBothWithThread(String message) {
		String threadInfo = " [Thread: " + Thread.currentThread().getName() + 
			(isEDT() ? ", EDT" : ", Non-EDT") + "]";
		logBoth(message + threadInfo);
	}
	
	// Check if we're on the Event Dispatch Thread
	private boolean isEDT() {
		return SwingUtilities.isEventDispatchThread();
	}
	private Broker broker;
	private PaginatedCachingBrowser browser;
	private String queue;
	private String[] otherQueues;
	private JFrame parentFrame;
	private int nCurPage = 1;

	private int nItemsPerPage = 20;
	private static final int nIdColumn = 2;
	
	private int estimatedPageCount = 0;
	private int estimatedTotalMessageCount = 0; // Store the original message count from main window

	private JLabel topLabel;
	private JLabel filterStatusLabel;
	private JTextArea textArea;
	//private JTextArea propsArea;
	private JTable table;
	private JCheckBox wrapTextCheckBox;
	private JComboBox<String> formatComboBox;
	private JScrollPane textAreaScrollPane;
	private String currentPayload; // Store raw payload for reformatting
	private DefaultTableModel tableModel; 
	private JTable headersTable;
	private DefaultTableModel headersTableModel; 
	private JTable propsTable;
	private DefaultTableModel propsTableModel; 
	private JButton nextPageButton;
	private JButton previousPageButton;
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
	private JCheckBox selectAllCheckBox; // Checkbox in top-left corner for select/deselect all

	public Point mousePressPoint;
	private FilterSpecification spec = new FilterSpecification();
	private String lastIdAdded = "";
	int numberOfMessagesOnTheCurrentPage = -1;
	private boolean updatingSelectAllCheckBox = false; // Flag to prevent recursion
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
		this.estimatedTotalMessageCount = nEstimatedMessageCount;
		this.estimatedPageCount = (nEstimatedMessageCount / nItemsPerPage) + 1;
		this.iconCellRenderer = new IconicTableCellRenderer(config);
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
			// Don't test connection here - let it fail during actual message loading
			// This prevents interfering with the first page retrieval
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
		logBothWithThread("*** FUNCTION CALL: run() - START ***");
		logBoth("*** run: Queue = " + this.queue + ", nCurPage = " + nCurPage + 
			", nItemsPerPage = " + nItemsPerPage + ", browser = " + (browser != null ? "not null" : "null") + " ***");
		// Check if browser was successfully initialized
		if (this.browser == null) {
			logBoth("*** run: browser is null - cannot proceed ***");
			logger.error("*** run: browser is null - cannot proceed ***");
			String errorMsg = "SMF (Messaging) connection failed. Cannot browse messages.\n\n" +
				"Please check your messaging credentials and network connectivity.";
			JOptionPane.showMessageDialog(parentFrame, 
				errorMsg,
				"SMF Connection Failed",
				JOptionPane.ERROR_MESSAGE);
			logBoth("*** run: Returning early due to null browser ***");
			return; // Don't show empty dialog
		}
		
		logBoth("*** run: Browser is not null, creating dialog ***");
		int totalTableWidth = 1480;
		// Create the dialog
		String versionStr = config != null ? config.version : "v2.1.3";
		dialog = new JDialog(parentFrame, "SolaceQueueBrowserGui 2.0 - " + this.queue, true);
		logBoth("*** run: Dialog created, about to set visible ***");
		dialog.setSize(1600, 1200);
		dialog.setLayout(new BorderLayout());
		dialog.setModal(false);

		// Create the top panel
		JPanel topPanel = new JPanel(new BorderLayout());

		JButton refreshTopButton = new JButton(config != null ? config.formatButtonText("↻", "Refresh") : "Refresh");
        refreshTopButton.setBackground(config != null ? config.buttonRefresh : new Color(220, 245, 255)); // Soft cyan background
        refreshTopButton.setForeground(config != null ? config.buttonRefreshForeground : Color.BLACK);
        refreshTopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                logBoth("*** BUTTON CLICK: Refresh button clicked ***");
                onRefresh();
            }
        });
        

		// Create filter button for top row
		JButton filterTopButton = new JButton(config != null ? config.formatButtonText("▼", "Filter") : "Filter");
		filterTopButton.setEnabled(true);
		filterTopButton.setBackground(config != null ? config.buttonFilter : new Color(240, 230, 255)); // Soft purple background
		filterTopButton.setForeground(config != null ? config.buttonFilterForeground : Color.BLACK);
		filterTopButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				logBoth("*** BUTTON CLICK: Filter button clicked ***");
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
		    	// Browser will be recreated in onRefresh() with new page size
		    	onRefresh();
		    }
		});

		previousPageButton = new JButton("<< Prev");
		previousPageButton.setEnabled(false);
		previousPageButton.setBackground(config != null ? config.buttonNavigation : new Color(230, 240, 255)); // Soft light blue background
		previousPageButton.setForeground(config != null ? config.buttonNavigationForeground : Color.BLACK);
		previousPageButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				logBoth("*** BUTTON CLICK: Previous Page button clicked ***");
				onPreviousPage(dialog, tableModel, previousPageButton);
			}
		});

		nextPageButton = new JButton("Next >>");
		nextPageButton.setEnabled(false);
		nextPageButton.setBackground(config != null ? config.buttonNavigation : new Color(230, 240, 255)); // Soft light blue background
		nextPageButton.setForeground(config != null ? config.buttonNavigationForeground : Color.BLACK);
		nextPageButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				logBoth("*** BUTTON CLICK: Next Page button clicked ***");
				onNextPage(dialog, tableModel, previousPageButton);
			}
		});

		// Use improved topRowPanel layout from nram-dev but with configured fonts
		// Use configured font family with fallback
		String fontFamily = (config != null && config.fontFamily != null && !config.fontFamily.isEmpty()) ? config.fontFamily : (config != null ? config.defaultFontFamilyFallback : "Serif");
		int labelFontSize = config != null ? config.labelFontSize : 16;
		
		// New top row layout: Browsing QUEUE_NAME | [<< Prev] Page N of ~ M [Next >>] | Page Size S | [Filter] (Status) | [Refresh]
		String filterStatus = spec.isEmpty() ? "OFF" : "ON";
		JPanel topRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		
		JLabel browsingLabel = new JLabel("Browsing: " + this.queue);
		browsingLabel.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(browsingLabel);
		
		JLabel separator1 = new JLabel("|");
		separator1.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(separator1);
		
		topRowPanel.add(previousPageButton);
		
		topLabel = new JLabel("Page " + nCurPage + " of ~ " + estimatedPageCount);
		topLabel.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(topLabel);
		
		topRowPanel.add(nextPageButton);
		
		JLabel separator2 = new JLabel("|");
		separator2.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(separator2);
		
		JLabel pageSizeLabel = new JLabel("Page Size");
		pageSizeLabel.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(pageSizeLabel);
		topRowPanel.add(cboMsgsPerPage);
		
		JLabel separator3 = new JLabel("|");
		separator3.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(separator3);
		
		topRowPanel.add(filterTopButton);
		
		filterStatusLabel = new JLabel("(" + filterStatus + ")");
		filterStatusLabel.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(filterStatusLabel);
		
		JLabel separator4 = new JLabel("|");
		separator4.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		topRowPanel.add(separator4);
		
		topRowPanel.add(refreshTopButton);
		topRowPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

		JPanel headerLabel = new JPanel(new BorderLayout());
		headerLabel.add(topRowPanel, BorderLayout.CENTER);
		
		topPanel.add(headerLabel, BorderLayout.NORTH);
		
		String[][] data = new String[][] {};
		String[] columnNames = { "", "", "Message Id", "Size", "Redelivered?" };

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
        
        // Add table model listener to update select-all checkbox when individual checkboxes change
        tableModel.addTableModelListener(new TableModelListener() {
			@Override
			public void tableChanged(TableModelEvent e) {
				// Only update if checkbox column (column 0) changed
				if (e.getColumn() == 0 || e.getColumn() == TableModelEvent.ALL_COLUMNS) {
					updateSelectAllCheckBoxState();
				}
			}
		});

		// Set a custom cell renderer to alternate row colors
		table.setDefaultRenderer(Object.class, new AlternatingRowColorRenderer(config));
		table.getColumnModel().getColumn(0).setCellRenderer(new CheckboxTableCellRenderer(config));
		table.getColumnModel().getColumn(1).setCellRenderer(iconCellRenderer);
		
		// Create select-all checkbox for header
		selectAllCheckBox = new JCheckBox();
		selectAllCheckBox.setToolTipText("Select/Deselect all messages");
		selectAllCheckBox.setHorizontalAlignment(SwingConstants.CENTER);
		
		// Method to handle checkbox toggle
		Runnable toggleAllMessages = new Runnable() {
			@Override
			public void run() {
				// Skip if we're updating programmatically
				if (updatingSelectAllCheckBox) {
					return;
				}
				
				boolean newValue = selectAllCheckBox.isSelected();
				currentSelectAllState = newValue ? eSelectAllState.eSelectedAll : eSelectAllState.eSelectedNone;
				
				// Update all row checkboxes
				for (int row = 0; row < table.getRowCount(); row++) {
					table.setValueAt(newValue, row, 0);
				}
				
				// Update row selection
				table.clearSelection();
				if (newValue) {
					table.setRowSelectionInterval(0, table.getRowCount() - 1);
					setStatus(table.getRowCount() + " messages selected");
				} else {
					setStatus("De-selected all messages");
				}
				table.repaint();
			}
		};
		
		// Add action listener for when checkbox is clicked directly
		selectAllCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toggleAllMessages.run();
			}
		});
		
		// Add mouse listener as backup (in case action listener doesn't fire)
		selectAllCheckBox.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				toggleAllMessages.run();
			}
		});
		
		// Create custom header renderer for column 0 that displays the checkbox
		// Note: The checkbox in the renderer won't be directly clickable, but we handle clicks via header mouse listener
		TableCellRenderer headerCheckboxRenderer = new TableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				// Return the checkbox component for rendering
				// The checkbox state will be updated programmatically via the header mouse listener
				return selectAllCheckBox;
			}
		};
		
		table.getColumnModel().getColumn(0).setHeaderRenderer(headerCheckboxRenderer);
		table.getColumnModel().getColumn(0).setPreferredWidth(36);
		table.getColumnModel().getColumn(1).setPreferredWidth(36);
		int remainindWidth = totalTableWidth - 72;
        table.getColumnModel().getColumn(2).setPreferredWidth(remainindWidth/3);
        table.getColumnModel().getColumn(3).setPreferredWidth(remainindWidth/3);
        table.getColumnModel().getColumn(4).setPreferredWidth(remainindWidth/3);
        
        // Add mouse listener to header to handle clicks on column 0 (checkbox column)
        // This handles clicks anywhere in the header column, including on the checkbox
        JTableHeader header = table.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (col == 0 && selectAllCheckBox != null && table.getRowCount() > 0) {
                    // Skip if we're updating programmatically
                    if (updatingSelectAllCheckBox) {
                        return;
                    }
                    
                    // Toggle the checkbox state
                    boolean newValue = !selectAllCheckBox.isSelected();
                    updatingSelectAllCheckBox = true;
                    selectAllCheckBox.setSelected(newValue);
                    updatingSelectAllCheckBox = false;
                    
                    // Update state and all row checkboxes
                    currentSelectAllState = newValue ? eSelectAllState.eSelectedAll : eSelectAllState.eSelectedNone;
                    
                    for (int row = 0; row < table.getRowCount(); row++) {
                        table.setValueAt(newValue, row, 0);
                    }
                    
                    // Update row selection
                    table.clearSelection();
                    if (newValue) {
                        table.setRowSelectionInterval(0, table.getRowCount() - 1);
                        setStatus(table.getRowCount() + " messages selected");
                    } else {
                        setStatus("De-selected all messages");
                    }
                    table.repaint();
                }
            }
        });
        
		// Enable gridlines with very light color
		table.setShowGrid(true);
		table.setGridColor(config != null ? config.gridColor : new Color(240, 240, 240)); // Very light gray, almost invisible
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
		
		JScrollPane listScrollPane = new JScrollPane(table);
		listScrollPane.setPreferredSize(new Dimension(380, 400));
		topPanel.add(listScrollPane, BorderLayout.CENTER);

		delButton = new JButton(config != null ? config.formatButtonText("✕", "Delete") : "Delete");
		delButton.setEnabled(false);
		delButton.setBackground(config != null ? config.buttonDelete : new Color(255, 220, 220)); // Soft red background
		delButton.setForeground(config != null ? config.buttonDeleteForeground : Color.BLACK);
		delButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onDeleteMessage(table, dialog);
			}
		});

		nextMsgButton = new JButton("Next>");
		nextMsgButton.setEnabled(false);
		nextMsgButton.setBackground(config != null ? config.buttonNavigation : new Color(230, 240, 255)); // Soft light blue background
		nextMsgButton.setForeground(config != null ? config.buttonNavigationForeground : Color.BLACK);
		nextMsgButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onNextMessage();
			}
		});
		prevMsgButton = new JButton("<Prev");
		prevMsgButton.setEnabled(false);
		prevMsgButton.setBackground(config != null ? config.buttonNavigation : new Color(230, 240, 255)); // Soft light blue background
		prevMsgButton.setForeground(config != null ? config.buttonNavigationForeground : Color.BLACK);
		prevMsgButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onPreviousMessage();
			}
		});
		copyMessageMsgButton = new JButton(config != null ? config.formatButtonText("⎘", "Copy") : "Copy");
		copyMessageMsgButton.setEnabled(false);
		copyMessageMsgButton.setBackground(config != null ? config.buttonCopy : new Color(220, 235, 255)); // Soft blue background
		copyMessageMsgButton.setForeground(config != null ? config.buttonCopyForeground : Color.BLACK);
		copyMessageMsgButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onCopyMessage();
			}
		});

		moveMessageMsgButton = new JButton(config != null ? config.formatButtonText("➜", "Move") : "Move");
		moveMessageMsgButton.setEnabled(false);
		moveMessageMsgButton.setBackground(config != null ? config.buttonMove : new Color(255, 245, 220)); // Soft yellow background
		moveMessageMsgButton.setForeground(config != null ? config.buttonMoveForeground : Color.BLACK);
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

        downloadMessageMsgButton = new JButton(config != null ? config.formatButtonText("⤓", "Download") : "Download");
        downloadMessageMsgButton.setEnabled(false);
        downloadMessageMsgButton.setBackground(config != null ? config.buttonRestore : new Color(220, 255, 220)); // Soft green background
        downloadMessageMsgButton.setForeground(config != null ? config.buttonRestoreForeground : Color.BLACK);
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
		targetQueueLabel.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
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

		// Payload header with controls
		JPanel payloadHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JLabel payloadLabel = new JLabel("Payload");
		payloadLabel.setFont(new Font(fontFamily, Font.PLAIN, labelFontSize));
		payloadHeaderPanel.add(payloadLabel);
		
		// Format selection
		int buttonFontSize = config != null ? config.buttonFontSize : 14;
		JLabel formatLabel = new JLabel("Format:");
		formatLabel.setFont(new Font(fontFamily, Font.PLAIN, buttonFontSize));
		payloadHeaderPanel.add(formatLabel);
		formatComboBox = new JComboBox<>(new String[]{"Plain", "JSON", "YAML", "CSV"});
		formatComboBox.setFont(new Font(fontFamily, Font.PLAIN, buttonFontSize));
		formatComboBox.setSelectedItem("Plain");
		formatComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (currentPayload != null) {
					updatePayloadDisplay();
				}
			}
		});
		payloadHeaderPanel.add(formatComboBox);
		
		// Wrap text checkbox
		wrapTextCheckBox = new JCheckBox("Wrap Text");
		int wrapTextFontSize = config != null ? config.buttonFontSize : 14;
		wrapTextCheckBox.setFont(new Font(fontFamily, Font.PLAIN, wrapTextFontSize));
		wrapTextCheckBox.setSelected(false);
		wrapTextCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				boolean wrap = wrapTextCheckBox.isSelected();
				textArea.setLineWrap(wrap);
				textArea.setWrapStyleWord(true);
				// Update the text area's scroll pane to show/hide horizontal scrollbar
				if (textAreaScrollPane != null) {
					textAreaScrollPane.setHorizontalScrollBarPolicy(
						wrap ? JScrollPane.HORIZONTAL_SCROLLBAR_NEVER : JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				}
				// Force repaint to update display
				textArea.revalidate();
				textArea.repaint();
			}
		});
		payloadHeaderPanel.add(wrapTextCheckBox);
		
		textArea = new JTextArea(4, 40); // Reduced to 4 rows for compact display
		textArea.setLineWrap(false);
		textArea.setWrapStyleWord(true);
		String textAreaFontFamily = config != null && config.textAreaFontFamily != null ? config.textAreaFontFamily : "Monospaced";
		int textAreaFontSize = config != null ? config.textAreaFontSize : 12;
		textArea.setFont(new java.awt.Font(textAreaFontFamily, Font.PLAIN, textAreaFontSize));
		
		// Wrap textArea in scroll pane for proper line wrapping
		textAreaScrollPane = new JScrollPane(textArea);
		textAreaScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		textAreaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		textAreaScrollPane.setPreferredSize(new Dimension(0, 100)); // Set preferred height - reduced
		textAreaScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120)); // Limit max height - reduced
		
		// Use BorderLayout for payload container to control sizing better
		JPanel payloadContainer = new JPanel(new BorderLayout());
		payloadContainer.add(payloadHeaderPanel, BorderLayout.NORTH);
		payloadContainer.add(textAreaScrollPane, BorderLayout.CENTER);
		payloadContainer.setPreferredSize(new Dimension(0, 130)); // Limit container height - reduced
		payloadContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150)); // Limit max height - reduced

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

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, propsAreaScrollPane, payloadContainer);
        splitPane.setDividerLocation(350); // Initial divider position
        splitPane.setOneTouchExpandable(true); // Adds little arrows to collapse/expand
		splitPane.setResizeWeight(0.5); // Allow both sides to resize
		
		bottomPanel.add(splitPane, BorderLayout.CENTER);
		bottomPanel.setPreferredSize(new Dimension(0, 200)); // Limit bottom panel height
		bottomPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220)); // Limit max height

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

		// Try to prefetch first page to check for partitioned queue error
		// This prevents opening the dialog window if browsing is not supported
		logBoth("*** run: Attempting initial prefetch to check for partitioned queue ***");
		try {
			semaphore.acquire();
			browser.prefetchNextPage(); // Try to prefetch the first page
			semaphore.release();
			logBoth("*** run: Successfully prefetched first page, queue is browsable ***");
		} catch (BrokerException e) {
			semaphore.release();
			if (e.getMessage() != null && e.getMessage().contains("Browsing Not Supported on Partitioned Queue")) {
				// Show error on parent frame and return without opening dialog
				logBoth("*** run: Partitioned queue detected, showing error on parent frame ***");
				JOptionPane.showMessageDialog(parentFrame, 
					"Browsing is not supported on Partitioned Queues",
					"Browsing Not Supported",
					JOptionPane.WARNING_MESSAGE);
				return; // Don't show the dialog
			} else {
				// Other broker errors - show error on parent frame and return
				logBoth("*** run: BrokerException during initial prefetch: " + e.getMessage() + " ***");
				String errorMsg = buildDetailedErrorMessage("Failed to browse queue", e);
				JOptionPane.showMessageDialog(parentFrame, 
					errorMsg,
					"Error Browsing Queue",
					JOptionPane.ERROR_MESSAGE);
				return; // Don't show the dialog
			}
		} catch (InterruptedException e) {
			semaphore.release();
			logBoth("*** run: InterruptedException during initial prefetch: " + e.getMessage() + " ***");
			String errorMsg = "Interrupted while browsing queue";
			JOptionPane.showMessageDialog(parentFrame, 
				errorMsg,
				"Error Browsing Queue",
				JOptionPane.ERROR_MESSAGE);
			return; // Don't show the dialog
		} catch (Exception e) {
			semaphore.release();
			logBoth("*** run: Exception during initial prefetch: " + e.getMessage() + " ***");
			String errorMsg = buildDetailedErrorMessage("Failed to browse queue", e);
			JOptionPane.showMessageDialog(parentFrame, 
				errorMsg,
				"Error Browsing Queue",
				JOptionPane.ERROR_MESSAGE);
			return; // Don't show the dialog
		}

		// Make the dialog visible
		// dialog.setVisible(true);

		// Show dialog first, then try to load messages
		logBoth("*** run: Setting dialog visible = true ***");
		dialog.setVisible(true);
		logBoth("*** run: Dialog is now visible, tableModel rowCount = " + tableModel.getRowCount() + " ***");
		
		// Try to load messages - if this fails, show error dialog
		// Use the same pattern as restartAfterFilter: set nCurPage to 0 and call onNextPage
		// This ensures consistent behavior with refresh
		SwingUtilities.invokeLater(() -> {
			try {
				dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				
				// Use the exact same pattern as restartAfterFilter
				tableModel.setRowCount(0);
				// Don't call preFetch() here since we already prefetched above for partitioned queue check
				nCurPage = 0;
				onNextPage(dialog, tableModel, nextPageButton); // Match restartAfterFilter exactly
				
			} catch (Exception e) {
				logBoth("*** INITIAL LOAD: Exception occurred: " + e.getMessage() + " ***");
				logger.error("*** INITIAL LOAD: Exception occurred: " + e.getMessage() + " ***", e);
				// Handle any errors during initialization
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
		logBothWithThread("*** FUNCTION CALL: onRefresh() - START ***");
		logBoth("*** onRefresh: Current nCurPage = " + nCurPage + 
			", tableModel rowCount = " + tableModel.getRowCount() + 
			", browser = " + (browser != null ? "not null" : "null") + " ***");
		dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		try {
			logBoth("*** onRefresh: Calling initialize() ***");
			initialize();
			logBoth("*** onRefresh: initialize() completed successfully ***");
		} catch (SempException e) {
			logBoth("*** onRefresh: initialize() failed with SempException: " + e.getMessage() + " ***");
			logger.error("*** onRefresh: initialize() failed with SempException: " + e.getMessage() + " ***", e);
			e.printStackTrace();
		}
		SwingUtilities.invokeLater(() -> {
			logBoth("*** onRefresh: Calling restartAfterFilter('Refreshing') ***");
			restartAfterFilter("Refreshing");
			logBoth("*** FUNCTION CALL: onRefresh() - END ***");
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
		
		// If filter is active, adjust page size using heuristic BEFORE preFetch/onNextPage
		// This ensures browser is reinitialized with correct page size before loading messages
		if (!spec.isEmpty() && browser != null) {
			adjustPageSizeForFilter();
		}
		
		preFetch();
		nCurPage = 0;
		
		onNextPage(dialog, tableModel, nextPageButton);
		
		// Recalculate page count after refresh/filter
		recalculateEstimatedPageCount();

		spinner.setVisible(false);
	}

	private void updateSelectAllCheckBoxState() {
		if (selectAllCheckBox == null || table.getRowCount() == 0) {
			if (selectAllCheckBox != null) {
				updatingSelectAllCheckBox = true;
				selectAllCheckBox.setSelected(false);
				updatingSelectAllCheckBox = false;
			}
			return;
		}
		
		// Check if all checkboxes are selected
		boolean allSelected = true;
		for (int row = 0; row < table.getRowCount(); row++) {
			Boolean checked = (Boolean) table.getValueAt(row, 0);
			if (checked == null || !checked) {
				allSelected = false;
				break;
			}
		}
		
		// Update checkbox state (use flag to prevent recursion)
		updatingSelectAllCheckBox = true;
		selectAllCheckBox.setSelected(allSelected);
		updatingSelectAllCheckBox = false;
		
		// Update state enum
		currentSelectAllState = allSelected ? eSelectAllState.eSelectedAll : eSelectAllState.eSelectedNone;
	}
	
	private void autoSelectFirstRow() {
		if (table.getRowCount() > 0) {
			table.setRowSelectionInterval(0, 0);
			onSelectMessage(table, 0);
		}
	}
	
	private void onMoveMessage() {
		moveOrCopy(true);
	}
	private void onCopyMessage() {
		moveOrCopy(false);
	}
	
	private void moveOrCopy(boolean deleteFromSource) {
		ArrayList<String> ids = getAllSelectedMessageIds();
		String action = deleteFromSource ? "move" : "copy";
		System.out.println("on" + (deleteFromSource ? "Move" : "Copy") + ": Called, selectedMessageIds.size()=" + ids.size());
		System.out.println("on" + (deleteFromSource ? "Move" : "Copy") + ": selectedMessageIds=" + ids);
		
		ArrayList<String> successfulIds = new ArrayList<String>();
		ArrayList<String> failedIds = new ArrayList<String>();
		
		if (ids.size() > 1) {
			System.out.println("on" + (deleteFromSource ? "Move" : "Copy") + ": Processing " + ids.size() + " messages");
			for (int i = 0; i < ids.size(); i++) {
				String id = ids.get(i);
				System.out.println("on" + (deleteFromSource ? "Move" : "Copy") + ": Processing message " + (i+1) + "/" + ids.size() + ": messageId=" + id);
				try {
					moveOrCopyMessage(id, deleteFromSource, false);
					successfulIds.add(id);
					System.out.println("on" + (deleteFromSource ? "Move" : "Copy") + ": Successfully " + action + "ed messageId=" + id);
				} catch (Exception e) {
					failedIds.add(id);
					System.out.println("on" + (deleteFromSource ? "Move" : "Copy") + ": Failed to " + action + " messageId=" + id + ", error=" + e.getMessage());
					logger.error("Failed to " + action + " message " + id, e);
				}
			}
			action = deleteFromSource ? "moved" : "copied";
			String selectedTargetQueue = (String) comboBox.getSelectedItem();
			
			// Show confirmation dialog
			String message;
			if (failedIds.isEmpty()) {
				message = successfulIds.size() + " message" + (successfulIds.size() == 1 ? "" : "s") + 
					" successfully " + action + " to queue '" + selectedTargetQueue + "'.";
			} else {
				message = successfulIds.size() + " message" + (successfulIds.size() == 1 ? "" : "s") + 
					" successfully " + action + " to queue '" + selectedTargetQueue + "'.\n" +
					failedIds.size() + " message" + (failedIds.size() == 1 ? "" : "s") + " failed.";
			}
			JOptionPane.showMessageDialog(dialog, message, 
				"Operation Complete", JOptionPane.INFORMATION_MESSAGE);
			
			setStatus(ids.size() + " messages were " + action+ " to " + selectedTargetQueue);

			// Update the display to remove all moved messages
			if (deleteFromSource) {
				// Update estimated total message count by subtracting moved messages
				estimatedTotalMessageCount = Math.max(0, estimatedTotalMessageCount - successfulIds.size());
				updateDisplayAfterMultipleDeletes(successfulIds);
				// Recalculation is done in updateDisplayAfterMultipleDeletes
			} else {
				// For copy operations, unselect the messages
				unselectMessages(ids);
			}
		}
		else {
			String id = ids.get(0);
			System.out.println("on" + (deleteFromSource ? "Move" : "Copy") + ": Processing single message: messageId=" + id);
			try {
				moveOrCopyMessage(id, deleteFromSource, true);
				action = deleteFromSource ? "moved" : "copied";
				String selectedTargetQueue = (String) comboBox.getSelectedItem();
				System.out.println("on" + (deleteFromSource ? "Move" : "Copy") + ": Successfully " + action + " messageId=" + id + " to queue=" + selectedTargetQueue);
				
				// Show confirmation dialog
				String message = "Message " + id + " successfully " + action + 
					" to queue '" + selectedTargetQueue + "'.";
				JOptionPane.showMessageDialog(dialog, message, 
					"Operation Complete", JOptionPane.INFORMATION_MESSAGE);
				
				// For copy operations, unselect the message
				if (!deleteFromSource) {
					unselectMessages(ids);
				}
			} catch (Exception e) {
				String actionVerb = deleteFromSource ? "move" : "copy";
				JOptionPane.showMessageDialog(dialog, 
					"Failed to " + actionVerb + " message " + id + ".\n" + e.getMessage(),
					"Operation Failed", JOptionPane.ERROR_MESSAGE);
				logger.error("Failed to " + actionVerb + " message " + id, e);
			}
		}
	}
	private void moveOrCopyMessage(String id, boolean deleteFromSource, boolean showStatus) throws SempException {
		String selectedTargetQueue = (String) comboBox.getSelectedItem();
		String actionVerb = deleteFromSource ? "move" : "copy";
		System.out.println("moveOrCopyMessage: Starting " + actionVerb + " for messageId=" + id + ", sourceQueue=" + queue + ", targetQueue=" + selectedTargetQueue);

		BytesXMLMessage msg = browser.get(id);
		ReplicationGroupMessageId replicationId = msg.getReplicationGroupMessageId();
		System.out.println("moveOrCopyMessage: Got message, replicationId=" + replicationId.toString());
		sempV2ActionClient.copy(broker.msgVpnName, queue, selectedTargetQueue, replicationId.toString());
		System.out.println("moveOrCopyMessage: SEMP copy operation completed for messageId=" + id);

		String actionPastTense = deleteFromSource ? "moved" : "copied";
		String logMsg = "MessageId " + id + " (replication id='" + replicationId.toString() + "') " + actionPastTense +
				" from '" + this.queue + "' queue to '" + selectedTargetQueue + "'.";
		CommandLog.instance().log(logMsg);

		if (showStatus) {
			setStatus (logMsg);
		}
		
		if (deleteFromSource) {
			System.out.println("moveOrCopyMessage: Deleting messageId=" + id + " from source queue (move operation)");
			browser.delete(id);
			System.out.println("moveOrCopyMessage: MessageId=" + id + " deleted from source queue");
			// For single message moves, remove the row immediately
			if (showStatus) {
				int selectedRow = table.getSelectedRow();
				if (selectedRow != -1) {
					tableModel.removeRow(selectedRow);
					numberOfMessagesOnTheCurrentPage--;
				}
				// Update estimated total message count by subtracting moved message
				estimatedTotalMessageCount = Math.max(0, estimatedTotalMessageCount - 1);
				// Recalculate page count after message move
				recalculateEstimatedPageCount();
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
		
		// Update estimated total message count by subtracting deleted messages
		estimatedTotalMessageCount = Math.max(0, estimatedTotalMessageCount - ids.size());
		
		// Recalculate page count after deletions
		recalculateEstimatedPageCount();
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
		System.out.println("onDownload: Called, selectedMessageIds.size()=" + ids.size());
		System.out.println("onDownload: selectedMessageIds=" + ids);
		
		ArrayList<String> successfulIds = new ArrayList<String>();
		ArrayList<String> failedIds = new ArrayList<String>();
		
		if (ids.size() > 1) {
			System.out.println("onDownload: Processing " + ids.size() + " messages");
			for (int i = 0; i < ids.size(); i++) {
				String id = ids.get(i);
				System.out.println("onDownload: Processing message " + (i+1) + "/" + ids.size() + ": messageId=" + id);
				try {
					downloadMessage(id, false);
					successfulIds.add(id);
					System.out.println("onDownload: Successfully downloaded messageId=" + id);
				} catch (Exception e) {
					failedIds.add(id);
					System.out.println("onDownload: Failed to download messageId=" + id + ", error=" + e.getMessage());
					logger.error("Failed to download message " + id, e);
				}
			}
			
			// Get broker hostname and sanitized VPN/queue names for status message
			String brokerHostname = this.broker.fqdn();
			if (brokerHostname == null || brokerHostname.isEmpty()) {
				brokerHostname = this.broker.name;
			}
			String sanitizedVpnName = this.broker.msgVpnName.replaceAll("/", "_").replaceAll(" ", "_");
			String sanitizedQueueName = this.queue.replaceAll("/", "_").replaceAll(" ", "_");
			String downloadPath = "./downloads/" + brokerHostname + "/" + sanitizedVpnName + "/" + sanitizedQueueName;
			
			// Show confirmation dialog
			String message;
			if (failedIds.isEmpty()) {
				message = successfulIds.size() + " message" + (successfulIds.size() == 1 ? "" : "s") + 
					" successfully downloaded to:\n" + downloadPath;
			} else {
				message = successfulIds.size() + " message" + (successfulIds.size() == 1 ? "" : "s") + 
					" successfully downloaded to:\n" + downloadPath + "\n\n" +
					failedIds.size() + " message" + (failedIds.size() == 1 ? "" : "s") + " failed.";
			}
			JOptionPane.showMessageDialog(dialog, message, 
				"Download Complete", JOptionPane.INFORMATION_MESSAGE);
			
			setStatus(ids.size() + " messages were downloaded to " + downloadPath);
			
			// Unselect the downloaded messages
			unselectMessages(ids);
		}
		else {
			String id = ids.get(0);
			System.out.println("onDownload: Processing single message: messageId=" + id);
			try {
				String zipFileName = downloadMessage(id, true);
				System.out.println("onDownload: Successfully downloaded messageId=" + id + " to file=" + zipFileName);
				
				// Show confirmation dialog
				String message = "Message " + id + " successfully downloaded to:\n" + zipFileName;
				JOptionPane.showMessageDialog(dialog, message, 
					"Download Complete", JOptionPane.INFORMATION_MESSAGE);
				
				// Unselect the downloaded message
				unselectMessages(ids);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(dialog, 
					"Failed to download message " + id + ".\n" + e.getMessage(),
					"Download Failed", JOptionPane.ERROR_MESSAGE);
				logger.error("Failed to download message " + id, e);
			}
		}
	}
	
	private String downloadMessage(String id, boolean showStatus) throws Exception {
		System.out.println("downloadMessage: Starting download for messageId=" + id);
		//String id = getMessageIdOfSelectedRow();
		BytesXMLMessage message = this.browser.get(id);
		System.out.println("downloadMessage: Retrieved message from browser for messageId=" + id);
		String payload = browser.getPayload(message);
		String[][] headers = getMessageHeadersData(message);
		String[][] userProps = getMessageUserPropsData(message);
		System.out.println("downloadMessage: Extracted payload, headers, and userProps for messageId=" + id);
		
		// Get broker hostname (use fqdn() if available, otherwise use name)
		String brokerHostname = this.broker.fqdn();
		if (brokerHostname == null || brokerHostname.isEmpty()) {
			brokerHostname = this.broker.name;
		}
		
		// Sanitize VPN name: replace "/" and spaces with "_"
		String sanitizedVpnName = this.broker.msgVpnName.replaceAll("/", "_").replaceAll(" ", "_");
		
		// Sanitize queue name: replace "/" and spaces with "_"
		String sanitizedQueueName = this.queue.replaceAll("/", "_").replaceAll(" ", "_");
		
		// Build folder path: ./downloads/hostname/vpn-name/queue-name/
		String baseFolder = "./downloads";
		String folder = baseFolder + "/" + brokerHostname + "/" + sanitizedVpnName + "/" + sanitizedQueueName;
		makeDirIfRequired(folder);
		
		String payloadFile = folder + "/payload.txt"; 
		writeStringToFile(payloadFile, payload);
		
		payload = propsAsString(headers);
		String headersFile = folder + "/headers.txt"; 
		writeStringToFile(headersFile, payload);

		payload = propsAsString(userProps);
		String userPropsFile = folder + "/userProps.txt"; 
		writeStringToFile(userPropsFile, payload);

		// Format timestamp as YYYYMMDDHHSS
		Instant when = Instant.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
			.withZone(ZoneId.systemDefault());
		String timestamp = formatter.format(when);
		
		String zipFileName = folder + "/msg-" + id + "-" + timestamp + ".zip";
		System.out.println("downloadMessage: Creating ZIP file=" + zipFileName + " for messageId=" + id);

        FileOutputStream fos = new FileOutputStream(zipFileName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        addToZip(zipOut, payloadFile);
        addToZip(zipOut, headersFile);
        addToZip(zipOut, userPropsFile);
        zipOut.close();
        fos.close();
        System.out.println("downloadMessage: ZIP file created successfully for messageId=" + id + ", file=" + zipFileName);

        deleteFile(payloadFile);
        deleteFile(headersFile);
        deleteFile(userPropsFile);
        
        // Log download to command.log
        String logMsg = "MessageId " + id + " downloaded from '" + this.queue + "' queue to '" + zipFileName + "'.";
        CommandLog.instance().log(logMsg);
        
        if (showStatus) {
        	setStatus("Downloaded message " + id + " to " + zipFileName) ;
        }
        
        return zipFileName;
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
	
	/**
	 * Unselects messages by clearing their checkboxes and row selection
	 * @param ids List of message IDs to unselect
	 */
	private void unselectMessages(ArrayList<String> ids) {
		// Clear checkboxes for the specified message IDs
		for (int row = 0; row < table.getRowCount(); row++) {
			String id = (String) table.getValueAt(row, nIdColumn);
			if (ids.contains(id)) {
				table.setValueAt(false, row, 0);
			}
		}
		
		// Clear row selection
		table.clearSelection();
		
		// Update select-all checkbox state
		updateSelectAllCheckBoxState();
		
		// Reset selection state
		currentSelectAllState = eSelectAllState.eIndeterminant;
	}
	
	private void onDeleteMessage(JTable table, Component dialog) {
		ArrayList<Integer> allSelectedRowNumbers = getAllSelectedRows();
		
		// If no checkboxes are selected, use the currently focused row
		if (allSelectedRowNumbers.isEmpty()) {
			int focusedRow = table.getSelectedRow();
			if (focusedRow >= 0 && focusedRow < table.getRowCount()) {
				allSelectedRowNumbers.add(focusedRow);
			} else {
				// No selection and no focus, can't delete anything
				setStatus("No message selected for deletion.");
				return;
			}
		}
		
		// Standardized prompt for all cases
		int messageCount = allSelectedRowNumbers.size();
		String prompt = "Are you sure you want to delete " + messageCount + " message" + (messageCount == 1 ? "?" : "s?");
		
		int response = JOptionPane.showConfirmDialog(dialog, prompt, "Confirmation", JOptionPane.YES_NO_OPTION);
        if (response == JOptionPane.YES_OPTION) {
    		ArrayList<String> ids = new ArrayList<String>();
    		ArrayList<String> successfulIds = new ArrayList<String>();
    		ArrayList<String> failedIds = new ArrayList<String>();
    		
        	for (Integer i : allSelectedRowNumbers) {
        		String id = (String) table.getValueAt(i, nIdColumn);
        		ids.add(id);
        		try {
        			this.browser.delete(id);
        			String logMsg = "MessageId " + id + " deleted from '" + this.queue + "' queue.";
        			CommandLog.instance().log(logMsg);
        			successfulIds.add(id);
        		} catch (Exception e) {
        			failedIds.add(id);
        			logger.error("Failed to delete message " + id, e);
        		}
        	}
        	
        	// now update the GUI
        	// Use the same pattern as updateDisplayAfterMultipleDeletes to ensure all rows are removed
        	boolean finished = false;
        	while (!finished) {
        		boolean deletedAnyThisRun = false;
	    		for (int rowIter = 0; rowIter < table.getRowCount(); rowIter++) {
	        		String id = (String) table.getValueAt(rowIter, nIdColumn);
	        		
	        		// was this message deleted?
	        		for (String oneDeletedId : successfulIds) {
	        			if (id.equals(oneDeletedId)) {
	        				tableModel.removeRow(rowIter);
	                		numberOfMessagesOnTheCurrentPage--;
	                		
	                		// now the model is different, rows are offset, restart and keep doing 
	                		// that until none get deleted
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
        	
        	// Reset selection state and update UI after all deletions
        	currentSelectAllState = eSelectAllState.eIndeterminant;
        	
        	// Auto-select the first row if there are messages remaining
        	if (table.getRowCount() > 0) {
        		autoSelectFirstRow();
        	} else {
        		clearMessageSelection();
        	}
        	
        	// Update estimated total message count by subtracting deleted messages
        	estimatedTotalMessageCount = Math.max(0, estimatedTotalMessageCount - successfulIds.size());
        	
        	// Recalculate page count after deletions
        	recalculateEstimatedPageCount();
        	
        	// Show confirmation dialog
        	String message;
        	if (failedIds.isEmpty()) {
        		if (successfulIds.size() == 1) {
        			message = "Message " + successfulIds.get(0) + " successfully deleted from queue '" + this.queue + "'.";
        		} else {
        			message = successfulIds.size() + " messages successfully deleted from queue '" + this.queue + "'.";
        		}
        		JOptionPane.showMessageDialog(dialog, message, 
        			"Deletion Complete", JOptionPane.INFORMATION_MESSAGE);
        	} else {
        		message = successfulIds.size() + " message" + (successfulIds.size() == 1 ? "" : "s") + 
        			" successfully deleted from queue '" + this.queue + "'.\n" +
        			failedIds.size() + " message" + (failedIds.size() == 1 ? "" : "s") + " failed to delete.";
        		JOptionPane.showMessageDialog(dialog, message, 
        			"Deletion Complete", JOptionPane.WARNING_MESSAGE);
        	}
        	
        	// Update status
        	if (successfulIds.size() == 1) {
        		setStatus("Message " + successfulIds.get(0) + " was deleted.");
        	} else {
        		setStatus(successfulIds.size() + " messages were deleted.");
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

		// Update estimated total message count by subtracting deleted message
		estimatedTotalMessageCount = Math.max(0, estimatedTotalMessageCount - 1);
		
		// Recalculate page count after deletion
		recalculateEstimatedPageCount();

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
			currentPayload = payload; // Store raw payload
			updatePayloadDisplay();
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
					JOptionPane.showMessageDialog(this.dialog, "Browsing is not supported on Partitioned Queues");
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
			logBoth("*** preFetch: Interrupted ***");
			logger.warn("*** preFetch: Interrupted ***");
			// Interrupted - ignore
		} catch (Exception e) {
			logBoth("*** preFetch: Exception occurred: " + e.getMessage() + " ***");
			logger.error("*** preFetch: Exception occurred: " + e.getMessage() + " ***", e);
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
		logBoth("*** FUNCTION CALL: onPageChange() - nCurPage = " + nCurPage + 
			", estimatedPageCount = " + estimatedPageCount + " ***");
		
		// Build page text based on filter status
		String pageText;
		if (!spec.isEmpty() && browser != null && lastIdAdded != null && !lastIdAdded.isEmpty()) {
			// Filter is active: check if there are more filtered messages
			boolean hasMore = browser.hasMoreAfterId(lastIdAdded);
			if (!hasMore) {
				// No more filtered messages: show exact page count
				pageText = "Page " + nCurPage + " of " + nCurPage;
			} else {
				// More filtered messages exist: show unknown
				pageText = "Page " + nCurPage + " of UNK";
			}
			logBoth("*** onPageChange: Filter active, hasMore=" + hasMore + ", pageText='" + pageText + "' ***");
		} else {
			// No filter: use estimated page count
			pageText = "Page " + nCurPage + " of ~ " + estimatedPageCount;
			logBoth("*** onPageChange: No filter, pageText='" + pageText + "' ***");
		}
		
		// Update topLabel with plain text (no HTML) using Serif font
		topLabel.setText(pageText);
		logBoth("*** onPageChange: topLabel set to '" + pageText + "' ***");
		textArea.setText("");
		currentPayload = null;
		logBoth("*** onPageChange: textArea cleared ***");
	}
	
	/**
	 * Update the payload display based on selected format
	 */
	private void updatePayloadDisplay() {
		if (currentPayload == null) {
			textArea.setText("");
			return;
		}
		
		String selectedFormat = (String) formatComboBox.getSelectedItem();
		String formattedPayload = currentPayload;
		
		try {
			switch (selectedFormat) {
				case "JSON":
					formattedPayload = formatAsJSON(currentPayload);
					break;
				case "YAML":
					formattedPayload = formatAsYAML(currentPayload);
					break;
				case "CSV":
					formattedPayload = formatAsCSV(currentPayload);
					break;
				case "Plain":
				default:
					formattedPayload = currentPayload;
					break;
			}
		} catch (Exception e) {
			// If formatting fails, show error message and use plain text
			formattedPayload = currentPayload + "\n\n[Formatting error: " + e.getMessage() + "]";
		}
		
		textArea.setText(formattedPayload);
	}
	
	/**
	 * Format payload as JSON with proper indentation
	 */
	private String formatAsJSON(String payload) {
		if (payload == null || payload.trim().isEmpty()) {
			return payload;
		}
		
		try {
			// Try to parse as JSON
			JsonElement jsonElement = JsonParser.parseString(payload);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			return gson.toJson(jsonElement);
		} catch (Exception e) {
			// If not valid JSON, try to find JSON-like content
			String trimmed = payload.trim();
			if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
				// Might be JSON but with extra content, try to extract
				int start = trimmed.indexOf('{');
				int end = trimmed.lastIndexOf('}');
				if (start >= 0 && end > start) {
					try {
						String jsonPart = trimmed.substring(start, end + 1);
						JsonElement jsonElement = JsonParser.parseString(jsonPart);
						Gson gson = new GsonBuilder().setPrettyPrinting().create();
						return trimmed.substring(0, start) + "\n" + gson.toJson(jsonElement) + "\n" + trimmed.substring(end + 1);
					} catch (Exception e2) {
						// Fall through to return original
					}
				}
			}
			// Not valid JSON, return original
			return payload;
		}
	}
	
	/**
	 * Format payload as YAML (basic implementation)
	 */
	private String formatAsYAML(String payload) {
		if (payload == null || payload.trim().isEmpty()) {
			return payload;
		}
		
		try {
			// First try to parse as JSON and convert to YAML
			JsonElement jsonElement = JsonParser.parseString(payload);
			return jsonToYaml(jsonElement, 0);
		} catch (Exception e) {
			// If not JSON, try to format as key-value pairs or return as-is
			return payload;
		}
	}
	
	/**
	 * Convert JSON element to YAML format
	 */
	private String jsonToYaml(JsonElement element, int indent) {
		String indentStr = "  ".repeat(indent);
		StringBuilder sb = new StringBuilder();
		
		if (element.isJsonObject()) {
			Set<java.util.Map.Entry<String, JsonElement>> entries = element.getAsJsonObject().entrySet();
			for (java.util.Map.Entry<String, JsonElement> entry : entries) {
				sb.append(indentStr).append(entry.getKey()).append(": ");
				JsonElement value = entry.getValue();
				if (value.isJsonPrimitive()) {
					if (value.getAsJsonPrimitive().isString()) {
						sb.append("\"").append(value.getAsString()).append("\"");
					} else {
						sb.append(value.getAsString());
					}
					sb.append("\n");
				} else if (value.isJsonObject() || value.isJsonArray()) {
					sb.append("\n");
					sb.append(jsonToYaml(value, indent + 1));
				} else {
					sb.append(value.getAsString()).append("\n");
				}
			}
		} else if (element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				sb.append(indentStr).append("- ");
				if (item.isJsonPrimitive()) {
					if (item.getAsJsonPrimitive().isString()) {
						sb.append("\"").append(item.getAsString()).append("\"");
					} else {
						sb.append(item.getAsString());
					}
					sb.append("\n");
				} else {
					sb.append("\n");
					sb.append(jsonToYaml(item, indent + 1));
				}
			}
		} else if (element.isJsonPrimitive()) {
			if (element.getAsJsonPrimitive().isString()) {
				sb.append("\"").append(element.getAsString()).append("\"");
			} else {
				sb.append(element.getAsString());
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * Format payload as CSV with proper alignment
	 */
	private String formatAsCSV(String payload) {
		if (payload == null || payload.trim().isEmpty()) {
			return payload;
		}
		
		String[] lines = payload.split("\n");
		if (lines.length == 0) {
			return payload;
		}
		
		// Check if it's already CSV-like (contains commas)
		boolean looksLikeCSV = false;
		for (String line : lines) {
			if (line.contains(",") && !line.trim().isEmpty()) {
				looksLikeCSV = true;
				break;
			}
		}
		
		if (!looksLikeCSV) {
			// Try to parse as JSON and convert to CSV
			try {
				JsonElement jsonElement = JsonParser.parseString(payload);
				return jsonToCSV(jsonElement);
			} catch (Exception e) {
				// Not JSON, return original
				return payload;
			}
		}
		
		// Format existing CSV with proper alignment
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			if (line.trim().isEmpty()) {
				sb.append("\n");
				continue;
			}
			String[] fields = line.split(",");
			for (int i = 0; i < fields.length; i++) {
				sb.append(fields[i].trim());
				if (i < fields.length - 1) {
					sb.append(", ");
				}
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	
	/**
	 * Convert JSON element to CSV format
	 */
	private String jsonToCSV(JsonElement element) {
		StringBuilder sb = new StringBuilder();
		
		if (element.isJsonObject()) {
			// For objects, create header row and data row
			Set<java.util.Map.Entry<String, JsonElement>> entries = element.getAsJsonObject().entrySet();
			java.util.List<String> headers = new ArrayList<>();
			java.util.List<String> values = new ArrayList<>();
			
			for (java.util.Map.Entry<String, JsonElement> entry : entries) {
				headers.add(entry.getKey());
				JsonElement value = entry.getValue();
				if (value.isJsonPrimitive()) {
					values.add(value.getAsString());
				} else {
					values.add(value.toString());
				}
			}
			
			// Write headers
			for (int i = 0; i < headers.size(); i++) {
				sb.append(headers.get(i));
				if (i < headers.size() - 1) {
					sb.append(", ");
				}
			}
			sb.append("\n");
			
			// Write values
			for (int i = 0; i < values.size(); i++) {
				sb.append(values.get(i));
				if (i < values.size() - 1) {
					sb.append(", ");
				}
			}
			sb.append("\n");
		} else if (element.isJsonArray()) {
			// For arrays, each element becomes a row
			boolean first = true;
			for (JsonElement item : element.getAsJsonArray()) {
				if (item.isJsonObject()) {
					if (first) {
						// Write headers from first object
						Set<String> headers = item.getAsJsonObject().keySet();
						java.util.List<String> headerList = new ArrayList<>(headers);
						for (int i = 0; i < headerList.size(); i++) {
							sb.append(headerList.get(i));
							if (i < headerList.size() - 1) {
								sb.append(", ");
							}
						}
						sb.append("\n");
						first = false;
					}
					// Write values
					Set<java.util.Map.Entry<String, JsonElement>> entries = item.getAsJsonObject().entrySet();
					java.util.List<String> values = new ArrayList<>();
					for (java.util.Map.Entry<String, JsonElement> entry : entries) {
						JsonElement value = entry.getValue();
						if (value.isJsonPrimitive()) {
							values.add(value.getAsString());
						} else {
							values.add(value.toString());
						}
					}
					for (int i = 0; i < values.size(); i++) {
						sb.append(values.get(i));
						if (i < values.size() - 1) {
							sb.append(", ");
						}
					}
					sb.append("\n");
				} else {
					// Simple array values
					if (item.isJsonPrimitive()) {
						sb.append(item.getAsString()).append("\n");
					} else {
						sb.append(item.toString()).append("\n");
					}
				}
			}
		} else {
			// Primitive value
			sb.append(element.getAsString()).append("\n");
		}
		
		return sb.toString();
	}
	
	/**
	 * Recalculate the estimated page count based on the message count from main window and current page size.
	 * Uses the original estimated message count and adjusts for deletions/moves.
	 * When a filter is active, page count is handled in onPageChange() based on hasMoreAfterId().
	 */
	private void recalculateEstimatedPageCount() {
		// Only recalculate if no filter is active
		// When filter is active, page count is determined dynamically in onPageChange()
		if (spec.isEmpty()) {
			// No filter: use the stored message count from main window (adjusted for deletions/moves)
			// Recalculate page count: (totalMessages / itemsPerPage) + 1
			// Ensure at least 1 page even if no messages
			if (estimatedTotalMessageCount > 0) {
				estimatedPageCount = (estimatedTotalMessageCount / nItemsPerPage) + 1;
			} else {
				estimatedPageCount = 1;
			}
			
		}
		
		// Update the UI label (will use filter-aware logic if filter is active)
		onPageChange();
	}
	
	/**
	 * Adjust page size when a filter is applied using a heuristic approach.
	 * Sets page size to a reasonable default (20) when filter is first applied,
	 * without scanning the entire queue to determine exact filtered count.
	 */
	private void adjustPageSizeForFilter() {
		// Only adjust if a filter is active
		if (spec.isEmpty() || browser == null) {
			return;
		}
		
		// Use heuristic: set page size to 20 when filter is applied
		// This is a reasonable default that works well for filtered results
		// without needing to scan the entire queue
		int heuristicPageSize = 20;
		
		// Always set to heuristic value when filter is applied
		// This ensures consistent page size for filtered results
		boolean pageSizeChanged = (nItemsPerPage != heuristicPageSize);
		
		if (pageSizeChanged) {
			// Update page size
			nItemsPerPage = heuristicPageSize;
			
			// Update the combo box without triggering refresh
			if (cboMsgsPerPage != null) {
				// Temporarily remove listener to avoid triggering refresh
				ActionListener[] listeners = cboMsgsPerPage.getActionListeners();
				for (ActionListener listener : listeners) {
					cboMsgsPerPage.removeActionListener(listener);
				}
				
				cboMsgsPerPage.setSelectedItem("" + heuristicPageSize);
				
				// Re-add listeners
				for (ActionListener listener : listeners) {
					cboMsgsPerPage.addActionListener(listener);
				}
			}
		}
		
		// Always reinitialize browser when filter is applied (even if page size unchanged)
		// This ensures browser has the correct filter and page size settings
		try {
			initialize(); // Recreates browser with current page size and filter
			// Recalculate page count with current size (will use cached count or heuristic for filtered results)
			recalculateEstimatedPageCount();
		} catch (SempException e) {
			logger.error("Failed to reinitialize browser with new page size", e);
		}
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
		
		// Update select-all checkbox state after table is populated
		updateSelectAllCheckBoxState();
		
		// If filter is active, recalculate page count after displaying messages
		// This updates the page count based on cached messages (more accurate than total queue count)
		if (!spec.isEmpty() && browser != null) {
			recalculateEstimatedPageCount();
		}
		
		// Update Next button state after displaying messages
		// This ensures Next button is disabled when no more filtered messages exist
		if (nextPageButton != null) {
			boolean shouldEnable = shouldNextPageButtonBeActive();
			nextPageButton.setEnabled(shouldEnable);
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
		
		// Recalculate page count after loading messages
		recalculateEstimatedPageCount();
		
		SwingUtilities.invokeLater(() -> {
			autoSelectFirstRow();
		});
	}

	private boolean shouldNextPageButtonBeActive() {
		if (browser == null) {
			return false;
		}
		
		// If no messages on current page, disable Next button
		if (numberOfMessagesOnTheCurrentPage == 0) {
			return false;
		}
		
		// Need lastIdAdded to check for more messages
		if (lastIdAdded == null || lastIdAdded.isEmpty()) {
			return false;
		}
		
		// If filter is active, check if there are more filtered messages
		if (!spec.isEmpty()) {
			// Check if current page has fewer messages than page size (partial page = end of filtered results)
			boolean partialPage = (numberOfMessagesOnTheCurrentPage > 0 && numberOfMessagesOnTheCurrentPage < nItemsPerPage);
			
			// Also check if there are more messages after the last ID
			boolean hasMore = browser.hasMoreAfterId(lastIdAdded);
			
			// If we have a partial page, we've reached the end of filtered messages
			// Even if hasMore is true (there might be unfiltered messages), we should disable Next
			if (partialPage) {
				return false;
			}
			
			// If no partial page but hasMore is false, also disable
			if (!hasMore) {
				return false;
			}
			
			// Page is full and hasMore is true, enable Next
			return true;
		} else {
			// No filter: use standard check
			boolean hasMore = browser.hasMoreAfterId(lastIdAdded);
			return hasMore;
		}
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
			logger.error("Failed to get messages", e);
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
			logger.error("Failed to get messages", e);
			String errorMsg = buildDetailedErrorMessage("Unexpected error loading messages", e);
			JOptionPane.showMessageDialog(dialog, 
				errorMsg,
				"Error Loading Messages",
				JOptionPane.ERROR_MESSAGE);
			dataUpdate = new Object[0][];
			rowCount = 0;
		}
		display(tableModel, dataUpdate);
		
		if (backButton != null) {
			backButton.setEnabled(true);
		}
		
		// see if the browser has any more messages after the last one onscreen
		// This now handles filtered messages correctly
		boolean hasMore = shouldNextPageButtonBeActive();
		if (nextPageButton != null) {
			nextPageButton.setEnabled(hasMore);
		}
		onPageChange();
		dialog.setCursor(Cursor.getDefaultCursor());
		
		// Recalculate page count after loading messages
		recalculateEstimatedPageCount();

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
		ArrayList<BytesXMLMessage> thisPage = null;
		try {
			thisPage = browser.getPage(nCurPage - 1); // its star6 counting at 0
		} catch (Exception e) {
			throw e;
		}

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
		
		
		String logMsg = "MessageId " + msg.id + " (replication id='" + msg.replicationId.toString() + "') moved " +  
				" from '" + msg.queue + "' queue to '" + msg.targetQueue + "'.";
		CommandLog.instance().log(logMsg);
		
		//onNextMessage(this.table);		
	}
}