package com.solace.psg.queueBrowser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solacesystems.jcsmp.*;
/**
 * This class is a simple wrapper around a Solace browser object, build to use with the Broker 
 * class and optimized for flexible pagination useful in GUIs  
 *
 * @author Mike O'Brien
 */
public class QueueBrowser {
	private static final Logger logger = LoggerFactory.getLogger(QueueBrowser.class.getName());
	private Broker broker;
	private String qToBrowse;
	private Browser solaceBrowserObject = null;
	private JCSMPSession solaceJcsmpSession = null;
	private BytesXMLMessage preFetchedNextMessage = null;
	private int paginationSize = 1;
	private String lastIdFetched = "";
	
	public QueueBrowser(Broker broker, String qToBrowse) {
		logger.info("QueueBrowser constructor called with queue: '" + qToBrowse + "'");
		this.broker = broker;
		this.qToBrowse = qToBrowse;
		logger.debug("QueueBrowser initialized - broker: " + (broker != null ? broker.messagingHost : "null") + ", queue: '" + qToBrowse + "'");
	}

	public QueueBrowser(Broker broker, String qToBrowse, int paginationSize) {
		logger.info("QueueBrowser constructor (with pagination) called with queue: '" + qToBrowse + "', pagination: " + paginationSize);
		this.broker = broker;
		this.qToBrowse = qToBrowse;
		this.paginationSize = paginationSize;
	}

	private void init() throws BrokerException {
		logger.info("QueueBrowser.init() called with queue: '" + this.qToBrowse + "'");
		System.out.println("QueueBrowser.init() called with queue: '" + this.qToBrowse + "'");
		
		// Validate queue name before proceeding
		if (this.qToBrowse == null || this.qToBrowse.trim().isEmpty()) {
			logger.error("Queue name validation failed - queue name is null or empty");
			throw new BrokerException("Queue name cannot be null or empty");
		}
		
		logger.info("Queue name validation passed for: '" + this.qToBrowse + "'");
		System.out.println("Queue name validation passed for: '" + this.qToBrowse + "'");
		
		try {
			// Define connection properties
			JCSMPProperties properties = new JCSMPProperties();
			properties.setProperty(JCSMPProperties.HOST, this.broker.messagingHost);
			properties.setProperty(JCSMPProperties.VPN_NAME, this.broker.msgVpnName);
			properties.setProperty(JCSMPProperties.USERNAME, this.broker.messagingClientUsername);
			properties.setProperty(JCSMPProperties.PASSWORD, this.broker.messagingPw);

			// Create a session
			logger.info("Creating JCSMP session for host: " + this.broker.messagingHost + ", VPN: " + this.broker.msgVpnName);
			System.out.println("Creating JCSMP session for host: " + this.broker.messagingHost + ", VPN: " + this.broker.msgVpnName);
			solaceJcsmpSession = JCSMPFactory.onlyInstance().createSession(properties);
			solaceJcsmpSession.connect();
			logger.info("Connected to Solace via JCSMP to " + this.broker.messagingHost);
			System.out.println("Connected to Solace via JCSMP to " + this.broker.messagingHost);
			
			// Define the queue object - ensure queue name is trimmed
			String queueNameToUse = this.qToBrowse.trim();
			logger.info("Creating queue object for: '" + queueNameToUse + "'");
			System.out.println("Creating queue object for: '" + queueNameToUse + "'");
			Queue queue = JCSMPFactory.onlyInstance().createQueue(queueNameToUse);

			// Create a jcsmp browser object
			BrowserProperties browserProps = new BrowserProperties();
			browserProps.setEndpoint(queue);

			// Set the window size for browsing. this allows the underlying API to pre-fetch
			// the specified # of msgs
			// If used from a GUI, this should be set to the number of messages on a page.
			browserProps.setTransportWindowSize(paginationSize);
			logger.info("Creating browser with transport window size: " + paginationSize);
			System.out.println("Creating browser with transport window size: " + paginationSize);
			solaceBrowserObject = solaceJcsmpSession.createBrowser(browserProps);
			logger.info("Created a queue browser object successfully on queue '" + this.qToBrowse + "'.");
			System.out.println("Created a queue browser object successfully on queue '" + this.qToBrowse + "'.");

		} catch (com.solacesystems.jcsmp.AccessDeniedException e) {
			String errorMsg = "Access denied to queue '" + this.qToBrowse + "'. " +
							 "Check with your Solace administrator to ensure your user account has 'browse' permission for this queue.";
			logger.error(errorMsg, e);
			System.err.println("ERROR: " + errorMsg);
			e.printStackTrace();
			throw new BrokerException(errorMsg);
		} catch (JCSMPException e) {
			String errorMsg = "JCSMP error while creating browser for queue '" + this.qToBrowse + "': " + e.getMessage();
			logger.error(errorMsg, e);
			System.err.println("ERROR: " + errorMsg);
			e.printStackTrace();
			throw new BrokerException(e);
		} catch (Exception e) {
			String errorMsg = "Unexpected error while creating browser for queue '" + this.qToBrowse + "': " + e.getMessage();
			logger.error(errorMsg, e);
			System.err.println("ERROR: " + errorMsg);
			e.printStackTrace();
			throw new BrokerException(errorMsg);
		}
	}

	private void lazyInit() throws BrokerException {
		if (solaceBrowserObject == null) {
			try {
				this.init();
			} catch (BrokerException e) {
				throw new BrokerException(e);
			}
		}
	}

	public boolean hasNext() throws BrokerException {
		lazyInit();
		try {
			logger.debug("QueueBrowser.hasNext() - calling getNext() on queue: '" + this.qToBrowse + "'");
			preFetchedNextMessage = solaceBrowserObject.getNext(1000);
			
			if (preFetchedNextMessage != null) {
				@SuppressWarnings("deprecation")
				String id = preFetchedNextMessage.getMessageId();
				logger.debug("Fetched a msg, id = " + id);
				this.lastIdFetched  = id;
			} else {
				logger.debug("No more messages available on queue: '" + this.qToBrowse + "'");
			}
		} catch (JCSMPException e) {
			logger.error("JCSMPException in hasNext() for queue '" + this.qToBrowse + "': " + e.getMessage(), e);
			throw new BrokerException(e);
		}
		return (preFetchedNextMessage != null);
	}

	public BytesXMLMessage next() throws BrokerException {
		if (preFetchedNextMessage == null) {
			throw new BrokerException("No message to fetch");
		}
		return preFetchedNextMessage;
	}

	@SuppressWarnings("unused")
	private void close() {
		solaceBrowserObject.close();
		solaceJcsmpSession.closeSession();
	}

	public boolean hasMoreAfterId(String id) {
		boolean bRc = true;
		if (this.lastIdFetched.equals(id)) {
			// its the last one that was fetched. since we prefetch a page at a time, if this is the last 
			// id fetched, it means this is the end of the queue
			bRc = false;
		}
		return bRc;
	}

	/* if you want to run a text based standalone, uncomment from here to the end of main()
	 * 
	public void run() throws BrokerException {
		lazyInit();

		// Browse messages
		System.out.println("Browsing messages...");
		PromptingClient prompter = new PromptingClient();
		while (hasNext()) {
			BytesXMLMessage message = next();
			if (message == null) {
				break;
			}

			System.out.println("MessageID: " + message.getMessageId());

			if (message instanceof TextMessage) {
				TextMessage txt = (TextMessage) message;
				System.out.println("MessageID: " + txt.getText());
			}
			String action;
			try {
				action = prompter.prompt("Enter the action you want:");
			} catch (IOException e) {
				throw new BrokerException(e);
			}
			if (action.toLowerCase().equals("n")) {
				// just continue top ofloop
			} else if (action.toLowerCase().equals("d")) {
				message.ackMessage();
			}
			// else if (action.toLowerCase().equals("m")) {
			// ReplicationGroupMessageId id = message.getReplicationGroupMessageId();
			// String strID = id.toString();
			// copy("testbroker", "TestBrokerQ1", "DestQueue", strID);
			// message.ackMessage();
			// next = true;
			// }
			else if (action.toLowerCase().equals("q")) {
				break;
			}
			System.out.println("----------");
		}

		// Close the browser and session
		this.close();
	}


	public static void main(String[] args) throws BrokerException  {
		Broker b = new Broker();
		b.sempHost = "tcps://mr-connection-XXXXX.messaging.solace.cloud:55443";
		b.msgVpnName = "testbroker";
		b.sempAdminUser = "solace-cloud-client";
		b.sempAdminPw = "XXXXX";
		b.name = "Test broker";

		QueueBrowser me = new QueueBrowser(b, "TestBrokerQ1");
		me.run();
	}
	*/
}
