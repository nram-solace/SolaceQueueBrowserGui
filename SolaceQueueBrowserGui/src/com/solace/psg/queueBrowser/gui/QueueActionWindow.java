package com.solace.psg.queueBrowser.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solace.psg.brokers.semp.SempClient;
import com.solace.psg.brokers.semp.SempClient.QueueInfo;
import com.solace.psg.brokers.semp.SempException;
import com.solace.psg.queueBrowser.QueueBrowser;
import com.solace.psg.util.CommandLog;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ReplicationGroupMessageId;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class QueueActionWindow extends JPanel {
	private static final long serialVersionUID = 1L;

	public enum eAction {eCopy, eMove, eDelete};
	private eAction eActionSelected;
	private String srcQName;
	private SwingWorker<Void, Integer> worker;
	private boolean cancelled = false;
	private SempClient sempV2ActionClient;
	private SempClient sempV2MonitorClient;
	private int totalMsgCount;
	private int msgsProcessed;
	private String msgVpnName;
	private JFrame parentFrame;
	private QueueBrowser solaceBrowserObject;
	private Broker broker;
	private String windowTitle;
	private String srcQlabelTitle;
	private String tarQName;
	private Config config;
	private Exception encounteredError = null;  // Track any exceptions during processing
	
	public QueueActionWindow(JFrame parentFrame, Broker broker, eAction action, SempClient sempV2ActionClient, SempClient sempV2MonitorClient, 
			String msgVpnName, String queueName, String destQnameName, Config config) throws BrokerException {
		this.config = config != null ? config : new Config(""); // Fallback if null
		this.sempV2ActionClient = sempV2ActionClient;
		this.sempV2MonitorClient = sempV2MonitorClient;
		this.srcQName = queueName;
		this.tarQName = destQnameName;
		this.msgVpnName = msgVpnName;
		this.parentFrame = parentFrame;
		this.broker = broker;
		this.eActionSelected = action;
		
		if (action == eAction.eCopy) {
			windowTitle = "Copy All in progress";
			srcQlabelTitle = "Copying all messages from:";
		}
		else if (action == eAction.eMove) {
			windowTitle = "Move All in progress";
			srcQlabelTitle = "Moving all messages from:";
		}
		else if (action == eAction.eDelete) {
			windowTitle = "Delete All in progress";
			srcQlabelTitle = "Deleting all messages from:";
		}
		
		if ((action == eAction.eCopy) || (action == eAction.eMove)) {
			if ((destQnameName == null) || (destQnameName.isEmpty())) {
				throw new BrokerException("Must suppy a destQueue name to copy or move messages");
			}
		}
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

	// Removed paintComponent - no graphics needed

	public void run() {
		try {
			QueueInfo info = sempV2MonitorClient.getQueueInfo(msgVpnName, srcQName);
			totalMsgCount = info.msgCount;
		} catch (SempException e1) {
			e1.printStackTrace();
		}
		msgsProcessed = 0;
		cancelled = false;

		JDialog frame = new JDialog (parentFrame, windowTitle, true);
		frame.setLocationRelativeTo(parentFrame);
		// Center the dialog on screen
		frame.setIconImage(parentFrame.getIconImage());

		Container contentPane = frame.getContentPane();
		contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS)); // Set BoxLayout on content pane

		JPanel verticalPanel = new JPanel();
		verticalPanel.setLayout(new BoxLayout(verticalPanel, BoxLayout.Y_AXIS));
		verticalPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Add padding

		// Create status message based on action type
		String statusMessage = "";
		if (eActionSelected == eAction.eCopy) {
			statusMessage = "Copying " + totalMsgCount + " messages from Queue " + srcQName + " to " + tarQName;
		} else if (eActionSelected == eAction.eMove) {
			statusMessage = "Moving " + totalMsgCount + " messages from Queue " + srcQName + " to " + tarQName;
		} else if (eActionSelected == eAction.eDelete) {
			statusMessage = "Deleting all messages from Queue " + srcQName;
		}

		JLabel labelTop = new JLabel(statusMessage);
		int headerFontSize = config != null ? config.headerFontSize : 16;
		labelTop.setFont(getFont(headerFontSize, Font.PLAIN));
		labelTop.setBorder(new EmptyBorder(10, 10, 10, 10)); // Add padding
		verticalPanel.add(labelTop);

		frame.add(verticalPanel);

		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				cancelled = true;
				if (worker != null) {
					worker.cancel(true);
				}
				frame.dispose();
			}
		});
		
		// Handle window close event (X button)
		frame.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				cancelled = true;
				if (worker != null) {
					worker.cancel(true);
				}
			}
		});
		
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttonPanel.setBorder(new EmptyBorder(1, 4, 1, 4)); // Top, Left, Bottom, Right
		buttonPanel.add(cancelButton);
		frame.add(buttonPanel);

		frame.setSize(600, 200);

		// Initialize browser object before showing dialog
		solaceBrowserObject = new QueueBrowser(broker, this.srcQName, 50);

		// Background task using SwingWorker
		worker = new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (int i = 0; i <= totalMsgCount; i++) {
                    if (isCancelled() || cancelled) {
                        break; // Exit task if canceled
                    }
                	try {
						if (solaceBrowserObject.hasNext()) {
							BytesXMLMessage msg = solaceBrowserObject.next();
			    			
							boolean axeIt = false;
							if (eActionSelected == eAction.eDelete) {
								// Delete operation
				    			String logMsg = "MessageId " + msg.getMessageId() + " deleted from '" + srcQName + "' queue.";
				    			CommandLog.instance().log(logMsg);
			    				axeIt = true;
							} else {
								// copy or move.. 
								ReplicationGroupMessageId replicationId = msg.getReplicationGroupMessageId();
				    			sempV2ActionClient.copy(msgVpnName, srcQName, tarQName, replicationId.toString());
				    			
				    			String action = "moved";
				    			if (eActionSelected == eAction.eCopy) {
				    				action = "copied";
				    			}
				    			String logMsg = "MessageId " + msg.getMessageId() + " (replication id='" + replicationId.toString() + "') " + action + 
				    					" from '" + srcQName + "' queue to '" + tarQName + "'.";
				    			CommandLog.instance().log(logMsg);
				    			if (eActionSelected == eAction.eMove) {
				    				axeIt = true;
				    			}
							}
							
							if (axeIt) {
								msg.ackMessage();
							}

			    			msgsProcessed++;
						    publish(i); // Send progress updates
						}
						else {
							// all done
							break;
						}
					} catch (SempException e) {
						// SEMP authorization or other API errors
						System.err.println("SEMP Exception during " + eActionSelected + " operation: " + e.getMessage());
						e.printStackTrace();
						encounteredError = e;
						cancelled = true;  // Stop processing on SEMP errors
						break;  // Exit the loop immediately
					} catch (BrokerException e) {
						// Other broker exceptions (JCSMP errors like AccessDeniedException)
						System.err.println("Broker Exception during " + eActionSelected + " operation: " + e.getMessage());
						e.printStackTrace();
						encounteredError = e;
						cancelled = true;  // Stop processing on broker errors
						break;  // Exit the loop immediately
					} catch (Exception e) {
						// Catch any other exceptions (e.g., JCSMPException from msg.ackMessage())
						System.err.println("Exception during " + eActionSelected + " operation: " + e.getMessage());
						e.printStackTrace();
						encounteredError = e;
						cancelled = true;  // Stop processing on any error
						break;  // Exit the loop immediately
					}
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                // No progress bar updates needed
            }

            @Override
            protected void done() {
                frame.dispose();
                
                if (encounteredError != null) {
                    // Show error dialog for exceptions
                    showErrorDialog(encounteredError);
                } else if (isCancelled() || cancelled) {
                    System.out.println("Task was canceled.");
                } else {
                    // Show completion status popup only if no errors
                    showCompletionStatus();
                }
            }
        };

        worker.execute(); // Start the task
		frame.setVisible(true);
	}
	
	private void showCompletionStatus() {
		String statusMessage = "";
		if (eActionSelected == eAction.eCopy) {
			statusMessage = msgsProcessed + " messages copied from Queue " + srcQName + " to " + tarQName;
		} else if (eActionSelected == eAction.eMove) {
			statusMessage = msgsProcessed + " messages moved from Queue " + srcQName + " to " + tarQName;
		} else if (eActionSelected == eAction.eDelete) {
			statusMessage = msgsProcessed + " messages deleted from Queue " + srcQName;
		}
		
		JOptionPane.showMessageDialog(parentFrame, 
			statusMessage,
			"Operation Complete",
			JOptionPane.INFORMATION_MESSAGE);
	}
	
	private void showErrorDialog(Exception error) {
		String actionName = "";
		if (eActionSelected == eAction.eCopy) {
			actionName = "Copy All";
		} else if (eActionSelected == eAction.eMove) {
			actionName = "Move All";
		} else if (eActionSelected == eAction.eDelete) {
			actionName = "Delete All";
		}
		
		String errorMessage = error.getMessage();
		
		// Extract more user-friendly message for authorization errors
		if (error instanceof SempException) {
			if (errorMessage.contains("UNAUTHORIZED") || errorMessage.contains("Authorization Access Level")) {
				errorMessage = "Authorization Error: The SEMP user does not have permission to perform this operation.\n\n" +
					"Details: " + errorMessage + "\n\n" +
					"Note: Copy and Move operations require write access via SEMP credentials.\n" +
					"Please use a SEMP management user with appropriate permissions.";
			}
		} else if (errorMessage != null && (
			errorMessage.contains("Access Denied") || 
			errorMessage.contains("no permission") ||
			errorMessage.contains("not authorized"))) {
			// Handle JCSMP permission errors (e.g., delete without consume permission)
			errorMessage = "Permission Error: The messaging client does not have permission to perform this operation.\n\n" +
				"Details: " + errorMessage + "\n\n";
		}
		
		String fullMessage = actionName + " operation failed after processing " + msgsProcessed + " of " + totalMsgCount + " messages.\n\n" + errorMessage;
		
		JOptionPane.showMessageDialog(parentFrame, 
			fullMessage,
			actionName + " Failed",
			JOptionPane.ERROR_MESSAGE);
	}

	private String getProgressLabelText() {
		String rc = "";
		String action = "";
		if (this.eActionSelected == eAction.eCopy) {
			action = "Copied";
		}
		else if (this.eActionSelected == eAction.eMove) {
			action = "Moved";
		}
		else if (this.eActionSelected == eAction.eDelete) {
			action = "Deleted";
		}
		rc = action + " " + this.msgsProcessed + " of " + this.totalMsgCount + " messages";
		return rc;
	}

//	public static void main(String[] args) {
//		CopyAllWindow me = new CopyAllWindow();
//		me.run();
//	}
}