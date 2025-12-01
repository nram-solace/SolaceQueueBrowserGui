package com.solace.psg.queueBrowser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solace.psg.queueBrowser.util.MessageRestoreUtil.ParsedMessage;
import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.SDTException;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.SDTMap;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;

/**
 * Utility class for publishing messages to queues using JCSMP
 */
public class MessagePublisher {
	private static final Logger logger = LoggerFactory.getLogger(MessagePublisher.class.getName());
	private static final long PUBLISH_TIMEOUT_SECONDS = 30;
	
	private Broker broker;
	private JCSMPSession session;
	private XMLMessageProducer producer;
	
	// Map to track pending publish operations: key -> (latch, error holder)
	private final ConcurrentHashMap<String, PublishResult> pendingPublishes = new ConcurrentHashMap<>();
	// Track the most recent publish operation for handling null-key callbacks
	private final AtomicReference<PublishResult> currentPublish = new AtomicReference<>();
	
	/**
	 * Inner class to hold publish result (latch and error)
	 */
	private static class PublishResult {
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<JCSMPException> error = new AtomicReference<>();
		final String messageId; // For logging/debugging
		
		PublishResult(String messageId) {
			this.messageId = messageId;
		}
	}
	
	public MessagePublisher(Broker broker) throws BrokerException {
		this.broker = broker;
		initializeSession();
	}
	
	/**
	 * Initialize JCSMP session and producer
	 */
	private void initializeSession() throws BrokerException {
		try {
			JCSMPProperties properties = new JCSMPProperties();
			properties.setProperty(JCSMPProperties.HOST, broker.messagingHost);
			properties.setProperty(JCSMPProperties.VPN_NAME, broker.msgVpnName);
			properties.setProperty(JCSMPProperties.USERNAME, broker.messagingClientUsername);
			properties.setProperty(JCSMPProperties.PASSWORD, broker.messagingPw);
			
			session = JCSMPFactory.onlyInstance().createSession(properties);
			session.connect();
			
			// Create producer with event handler to catch async errors
			producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
				@Override
				public void responseReceivedEx(Object key) {
					// Handle publish confirmation - count down latch if we're waiting
					String keyStr = (key != null) ? String.valueOf(key) : null;
					logger.debug("Message published successfully: " + (keyStr != null ? keyStr : "null key"));
					
					PublishResult result = null;
					if (keyStr != null) {
						result = pendingPublishes.get(keyStr);
					}
					
					// If key is null or not found, try current publish
					if (result == null) {
						result = currentPublish.get();
					}
					
					if (result != null) {
						result.latch.countDown();
					}
				}
				
				@Override
				public void handleErrorEx(Object key, JCSMPException e, long timestamp) {
					// Handle publish errors - this is where async errors like 503 come through
					String keyStr = (key != null) ? String.valueOf(key) : null;
					logger.error("Error publishing message (key: " + (keyStr != null ? keyStr : "null") + "): " + e.getMessage(), e);
					
					PublishResult result = null;
					if (keyStr != null) {
						result = pendingPublishes.get(keyStr);
					}
					
					// If key is null or not found, use current publish (common case for queue sends)
					if (result == null) {
						result = currentPublish.get();
						if (result != null) {
							logger.debug("Applying error to current publish operation for message: " + result.messageId);
						}
					}
					
					if (result != null) {
						result.error.set(e);
						result.latch.countDown();
					} else {
						// Error for a message we're no longer tracking - log it
						logger.warn("Received publish error but no pending operation found (key: " + (keyStr != null ? keyStr : "null") + "), error: " + e.getMessage());
					}
				}
			});
			
			logger.info("MessagePublisher initialized successfully");
		} catch (JCSMPException e) {
			String errorMsg = "Failed to initialize JCSMP session for publishing: " + e.getMessage();
			logger.error(errorMsg, e);
			throw new BrokerException(errorMsg);
		}
	}
	
	/**
	 * Publish a single message to a queue
	 * @param queueName Target queue name
	 * @param parsedMessage Parsed message data
	 * @throws BrokerException If publishing fails
	 */
	public void publishMessage(String queueName, ParsedMessage parsedMessage) throws BrokerException {
		// Validate queue name
		if (queueName == null || queueName.trim().isEmpty()) {
			String errorMsg = "Failed to restore message " + parsedMessage.messageId + ": Queue name cannot be null or empty";
			logger.error(errorMsg);
			throw new BrokerException(errorMsg);
		}
		
		String publishKey = parsedMessage.messageId + "-" + System.currentTimeMillis();
		PublishResult result = new PublishResult(parsedMessage.messageId);
		pendingPublishes.put(publishKey, result);
		currentPublish.set(result); // Set as current for null-key callback handling
		
		try {
			BytesXMLMessage message = reconstructMessage(parsedMessage);
			Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName.trim());
			
			// Set application message ID as correlation key for tracking
			message.setApplicationMessageId(publishKey);
			
			// Send message (asynchronous) - catch synchronous exceptions
			try {
				producer.send(message, queue);
			} catch (JCSMPException e) {
				// Synchronous error during send
				pendingPublishes.remove(publishKey);
				currentPublish.compareAndSet(result, null); // Clear current
				String errorMsg = formatErrorMessage(e, parsedMessage.messageId, queueName);
				logger.error("Synchronous error sending message " + parsedMessage.messageId + " to queue " + queueName + ": " + errorMsg, e);
				throw new BrokerException(errorMsg);
			}
			
			// Wait briefly for async error callback (most errors come back quickly)
			// Use a short timeout - if no error callback, assume success
			boolean callbackReceived = result.latch.await(2, TimeUnit.SECONDS);
			
			// Clear current publish before checking for errors
			currentPublish.compareAndSet(result, null);
			
			if (callbackReceived) {
				// A callback was received (either success or error)
				JCSMPException error = result.error.get();
				if (error != null) {
					// Error was received via callback
					pendingPublishes.remove(publishKey);
					String errorMsg = formatErrorMessage(error, parsedMessage.messageId, queueName);
					logger.error("Asynchronous error publishing message " + parsedMessage.messageId + " to queue " + queueName + ": " + errorMsg, error);
					throw new BrokerException(errorMsg);
				} else {
					// Success callback received
					pendingPublishes.remove(publishKey);
					logger.info("Published message " + parsedMessage.messageId + " to queue " + queueName);
					return; // Success
				}
			}
			
			// Timeout - check if there was an error set (shouldn't happen, but be safe)
			JCSMPException error = result.error.get();
			if (error != null) {
				pendingPublishes.remove(publishKey);
				String errorMsg = formatErrorMessage(error, parsedMessage.messageId, queueName);
				logger.error("Error detected after timeout for message " + parsedMessage.messageId + " to queue " + queueName + ": " + errorMsg, error);
				throw new BrokerException(errorMsg);
			}
			
			// Timeout with no error - assume success (most errors come back quickly)
			pendingPublishes.remove(publishKey);
			logger.info("Published message " + parsedMessage.messageId + " to queue " + queueName + " (no error callback received)");
			
		} catch (InterruptedException e) {
			pendingPublishes.remove(publishKey);
			currentPublish.compareAndSet(result, null);
			Thread.currentThread().interrupt();
			String errorMsg = "Interrupted while waiting for publish confirmation for message " + parsedMessage.messageId + " to queue " + queueName;
			logger.error(errorMsg, e);
			throw new BrokerException(errorMsg);
		} catch (BrokerException e) {
			// Re-throw BrokerException as-is
			currentPublish.compareAndSet(result, null);
			throw e;
		} catch (Exception e) {
			pendingPublishes.remove(publishKey);
			currentPublish.compareAndSet(result, null);
			String errorMsg = "Unexpected error publishing message " + parsedMessage.messageId + " to queue " + queueName + ": " + e.getMessage();
			logger.error(errorMsg, e);
			throw new BrokerException(errorMsg);
		}
	}
	
	/**
	 * Format error message with user-friendly descriptions for common error codes
	 */
	private String formatErrorMessage(JCSMPException e, String messageId, String queueName) {
		if (e instanceof JCSMPErrorResponseException) {
			JCSMPErrorResponseException errorResp = (JCSMPErrorResponseException) e;
			int errorCode = errorResp.getSubcodeEx();
			String errorMessage = errorResp.getMessage();
			
			// Handle specific error codes with user-friendly messages
			if (errorCode == 503) {
				// Spool Over Quota
				return String.format(
					"Failed to restore message %s to queue '%s': Target queue is full (Spool Over Quota). " +
					"The queue has exceeded its message VPN limit. Please free up space in the queue or increase the quota limit.",
					messageId, queueName);
			} else if (errorCode == 404) {
				// Queue not found
				return String.format(
					"Failed to restore message %s to queue '%s': Queue not found. Please verify the queue name exists.",
					messageId, queueName);
			} else if (errorCode == 403) {
				// Permission denied
				return String.format(
					"Failed to restore message %s to queue '%s': Permission denied. Please check your messaging credentials.",
					messageId, queueName);
			} else {
				// Other error codes
				return String.format(
					"Failed to restore message %s to queue '%s': Error %d - %s",
					messageId, queueName, errorCode, errorMessage);
			}
		} else {
			// Non-error-response exceptions
			return String.format(
				"Failed to restore message %s to queue '%s': %s",
				messageId, queueName, e.getMessage());
		}
	}
	
	/**
	 * Publish multiple messages to a queue
	 * @param queueName Target queue name
	 * @param messages List of parsed messages
	 * @return Number of successfully published messages
	 * @throws BrokerException If all messages fail
	 */
	public int publishMessages(String queueName, List<ParsedMessage> messages) throws BrokerException {
		int successCount = 0;
		int failCount = 0;
		
		for (ParsedMessage parsedMessage : messages) {
			try {
				publishMessage(queueName, parsedMessage);
				successCount++;
			} catch (BrokerException e) {
				failCount++;
				logger.error("Failed to publish message " + parsedMessage.messageId, e);
			}
		}
		
		if (successCount == 0 && failCount > 0) {
			throw new BrokerException("All " + failCount + " messages failed to publish");
		}
		
		logger.info("Published " + successCount + " messages successfully, " + failCount + " failed");
		return successCount;
	}
	
	/**
	 * Reconstruct BytesXMLMessage from parsed message data
	 * @param parsedMessage Parsed message data
	 * @return BytesXMLMessage ready to publish
	 * @throws BrokerException If reconstruction fails
	 */
	private BytesXMLMessage reconstructMessage(ParsedMessage parsedMessage) throws BrokerException {
		try {
			BytesXMLMessage message;
			
			// Determine message type - prefer TextMessage for text payloads
			// Check if payload looks like text (contains printable characters)
			boolean isText = isTextPayload(parsedMessage.payload);
			
			if (isText) {
				TextMessage textMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
				textMsg.setText(parsedMessage.payload);
				message = textMsg;
			} else {
				BytesMessage bytesMsg = JCSMPFactory.onlyInstance().createMessage(BytesMessage.class);
				bytesMsg.setData(parsedMessage.payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				message = bytesMsg;
			}
			
			// Set headers from parsed headers
			setHeaders(message, parsedMessage.headers);
			
			// Set user properties
			setUserProperties(message, parsedMessage.userProps);
			
			// Set delivery mode to persistent by default
			message.setDeliveryMode(DeliveryMode.PERSISTENT);
			
			return message;
		} catch (Exception e) {
			throw new BrokerException("Failed to reconstruct message: " + e.getMessage());
		}
	}
	
	/**
	 * Check if payload is text (contains mostly printable characters)
	 */
	private boolean isTextPayload(String payload) {
		if (payload == null || payload.isEmpty()) {
			return true;
		}
		
		int printableCount = 0;
		int totalChars = Math.min(payload.length(), 1000); // Sample first 1000 chars
		
		for (int i = 0; i < totalChars; i++) {
			char c = payload.charAt(i);
			if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || 
				(c >= 32 && c <= 126)) { // Printable ASCII
				printableCount++;
			}
		}
		
		// Consider it text if >80% printable
		return (printableCount * 100 / totalChars) > 80;
	}
	
	/**
	 * Set message headers from parsed headers map
	 */
	private void setHeaders(BytesXMLMessage message, Map<String, String> headers) {
		if (headers == null) {
			return;
		}
		
		// Set TTL if available
		if (headers.containsKey("Time-To-Live (TTL)")) {
			try {
				long ttl = Long.parseLong(headers.get("Time-To-Live (TTL)"));
				message.setTimeToLive(ttl);
			} catch (NumberFormatException e) {
				logger.warn("Invalid TTL value: " + headers.get("Time-To-Live (TTL)"));
			}
		}
		
		// Set Correlation ID if available
		if (headers.containsKey("Correlation ID") && !headers.get("Correlation ID").isEmpty()) {
			message.setCorrelationId(headers.get("Correlation ID"));
		}
		
		// Set Reply-To Destination if available
		if (headers.containsKey("Reply-To Destination") && !headers.get("Reply-To Destination").isEmpty()) {
			try {
				String replyTo = headers.get("Reply-To Destination");
				Destination dest = JCSMPFactory.onlyInstance().createTopic(replyTo);
				message.setReplyTo(dest);
			} catch (Exception e) {
				logger.warn("Failed to set Reply-To destination: " + headers.get("Reply-To Destination"), e);
			}
		}
		
		// Note: Some headers like Message ID, Redelivery Flag, etc. are set by the broker
		// and cannot be set on new messages
	}
	
	/**
	 * Set user properties from parsed user properties map
	 */
	private void setUserProperties(BytesXMLMessage message, Map<String, String> userProps) {
		if (userProps == null || userProps.isEmpty()) {
			return;
		}
		
		try {
			SDTMap properties = JCSMPFactory.onlyInstance().createMap();
			for (Map.Entry<String, String> entry : userProps.entrySet()) {
				properties.putString(entry.getKey(), entry.getValue());
			}
			message.setProperties(properties);
		} catch (SDTException e) {
			logger.error("Failed to set user properties", e);
		}
	}
	
	/**
	 * Close the session and producer
	 */
	public void close() {
		try {
			if (producer != null) {
				producer.close();
			}
			if (session != null) {
				session.closeSession();
			}
			logger.info("MessagePublisher closed");
		} catch (Exception e) {
			logger.error("Error closing MessagePublisher", e);
		}
	}
}


