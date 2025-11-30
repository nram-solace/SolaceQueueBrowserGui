package com.solace.psg.queueBrowser.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solace.psg.util.FileUtils;
import com.solace.psg.util.PasswordEncryption;

public class Config {
	
	private String configFile;
	public List<Broker> brokers = new ArrayList<Broker>();
	public Broker broker; // Currently selected broker (for backward compatibility)
	private int selectedBrokerIndex = 0;
	public String downloadFolder = "./downloads";
	
	// UI Configuration - OS-agnostic defaults
	public String fontFamily = null; // null means use FlatLaf default
	public int defaultFontSize = 14;
	public int headerFontSize = 16;
	public int statusFontSize = 22;
	public String version = "v2.1.3";

	/* removed for V1 release
	public List<String> blackListedQueues = null;
	public List<String> whiteListedQueues = null;
	public HashMap<String, MovementDestination> movements = new HashMap<String, MovementDestination>(); 
	*/
	class MovementDestination {
		String source = "";
		List<String> destinations = new ArrayList<String>();
	}
	
	private char[] masterPassword = null; // Master password for decrypting encrypted passwords
	
	public Config(String file) {
		this.configFile = file;
	}
	
	/**
	 * Set the master password for decrypting encrypted passwords.
	 * @param masterPassword The master password as a char array (for security)
	 */
	public void setMasterPassword(char[] masterPassword) {
		// Clear old password from memory if exists
		if (this.masterPassword != null) {
			java.util.Arrays.fill(this.masterPassword, '\0');
		}
		// Copy the array to avoid issues if caller clears the original
		if (masterPassword != null) {
			this.masterPassword = new char[masterPassword.length];
			System.arraycopy(masterPassword, 0, this.masterPassword, 0, masterPassword.length);
		} else {
			this.masterPassword = null;
		}
	}
	
	/**
	 * Set the master password for decrypting encrypted passwords.
	 * @param masterPassword The master password as a String
	 */
	public void setMasterPassword(String masterPassword) {
		setMasterPassword(masterPassword != null ? masterPassword.toCharArray() : null);
	}
	
	/**
	 * Check if any passwords in the config file are encrypted.
	 * This method loads the config file and checks for encrypted passwords without decrypting them.
	 * @return true if any encrypted passwords are found, false otherwise
	 */
	public boolean hasEncryptedPasswords() throws BrokerException {
		String fileContent = null;
		try {
			fileContent = FileUtils.loadFile(this.configFile);
		} catch (IOException e) {
			throw new BrokerException(e);
		}
		JSONObject doc = new JSONObject(fileContent);
		
		// Check eventBrokers array
		if (doc.has("eventBrokers")) {
			JSONArray eventBrokersArray = doc.getJSONArray("eventBrokers");
			for (int i = 0; i < eventBrokersArray.length(); i++) {
				JSONObject eventBroker = eventBrokersArray.getJSONObject(i);
				if (hasEncryptedPasswordInBroker(eventBroker)) {
					return true;
				}
			}
		}
		
		// Check single eventBroker (backward compatibility)
		if (doc.has("eventBroker")) {
			JSONObject eventBroker = doc.getJSONObject("eventBroker");
			if (hasEncryptedPasswordInBroker(eventBroker)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Check if a broker JSON object contains encrypted passwords.
	 */
	private boolean hasEncryptedPasswordInBroker(JSONObject eventBroker) {
		if (eventBroker.has("sempAdminPw")) {
			String pw = eventBroker.getString("sempAdminPw");
			if (PasswordEncryption.isEncrypted(pw)) {
				return true;
			}
		}
		if (eventBroker.has("messagingPw")) {
			String pw = eventBroker.getString("messagingPw");
			if (PasswordEncryption.isEncrypted(pw)) {
				return true;
			}
		}
		return false;
	}
	
	public void load() throws BrokerException  {
		// First, load system.json if it exists
		loadSystemConfig();
		
		// Then load user config file
		String fileContent = null;
		try {
			fileContent = FileUtils.loadFile(this.configFile);
		} catch (IOException e) {
			throw new BrokerException(e);
		}
		JSONObject doc = new JSONObject(fileContent);
		
		// Load system properties from user config (allows override, though not recommended)
		if (doc.has("version")) {
			version = doc.getString("version");
		}
		if (doc.has("downloadFolder")) {
			downloadFolder = doc.getString("downloadFolder");
		}
		
		// Load UI configuration if present (allows override, though not recommended)
		if (doc.has("ui")) {
			JSONObject uiConfig = doc.getJSONObject("ui");
			if (uiConfig.has("fontFamily") && !uiConfig.isNull("fontFamily")) {
				fontFamily = uiConfig.getString("fontFamily");
			}
			if (uiConfig.has("defaultFontSize")) {
				defaultFontSize = uiConfig.getInt("defaultFontSize");
			}
			if (uiConfig.has("headerFontSize")) {
				headerFontSize = uiConfig.getInt("headerFontSize");
			}
			if (uiConfig.has("statusFontSize")) {
				statusFontSize = uiConfig.getInt("statusFontSize");
			}
			// Backward compatibility: also check for version in ui object
			if (uiConfig.has("version")) {
				version = uiConfig.getString("version");
			}
		}

		// Load event brokers - support both array and single object for backward compatibility
		if (doc.has("eventBrokers")) {
			// New format: array of brokers
			JSONArray eventBrokersArray = doc.getJSONArray("eventBrokers");
			if (eventBrokersArray.length() == 0) {
				throw new BrokerException("Configuration must contain at least one event broker");
			}
			for (int i = 0; i < eventBrokersArray.length(); i++) {
				JSONObject eventBroker = eventBrokersArray.getJSONObject(i);
				Broker b = createBrokerFromJson(eventBroker);
				brokers.add(b);
			}
			// Set first broker as default selected broker
			broker = brokers.get(0);
			selectedBrokerIndex = 0;
		} else if (doc.has("eventBroker")) {
			// Old format: single broker object (backward compatibility)
			JSONObject eventBroker = doc.getJSONObject("eventBroker");
			Broker b = createBrokerFromJson(eventBroker);
			brokers.add(b);
			broker = b;
			selectedBrokerIndex = 0;
		} else {
			throw new BrokerException("Configuration must contain either 'eventBroker' or 'eventBrokers'");
		}
	}
	
	/**
	 * Load system configuration from config/system.json
	 * This file contains system/internal properties like download folder and UI settings
	 */
	private void loadSystemConfig() throws BrokerException {
		String systemConfigFile = "config/system.json";
		try {
			String fileContent = FileUtils.loadFile(systemConfigFile);
			JSONObject systemDoc = new JSONObject(fileContent);
			
			// Load version from top level
			if (systemDoc.has("version")) {
				version = systemDoc.getString("version");
			}
			
			// Load download folder
			if (systemDoc.has("downloadFolder")) {
				downloadFolder = systemDoc.getString("downloadFolder");
			}
			
			// Load UI configuration
			if (systemDoc.has("ui")) {
				JSONObject uiConfig = systemDoc.getJSONObject("ui");
				if (uiConfig.has("fontFamily") && !uiConfig.isNull("fontFamily")) {
					fontFamily = uiConfig.getString("fontFamily");
				}
				if (uiConfig.has("defaultFontSize")) {
					defaultFontSize = uiConfig.getInt("defaultFontSize");
				}
				if (uiConfig.has("headerFontSize")) {
					headerFontSize = uiConfig.getInt("headerFontSize");
				}
				if (uiConfig.has("statusFontSize")) {
					statusFontSize = uiConfig.getInt("statusFontSize");
				}
			}
		} catch (IOException e) {
			// System config file is optional - if it doesn't exist, use defaults
			// This allows backward compatibility with existing configs
		}
        
        /* This code removed for V1 release
         * 
        if (doc.has("blackListedQueues") && doc.has("whiteListedQueues")) {
        	throw new BrokerException("Invalid configuration. You cannot specify both a blacklist and a whitelist");
        }
        blackListedQueues = loadList(doc, "blackListedQueues");
        whiteListedQueues = loadList(doc, "whiteListedQueues");
        
        if (doc.has("movementDestinations")) {
        	JSONArray arr = doc.getJSONArray("movementDestinations");
        	for (int i = 0; i < arr.length(); i++) {
        		JSONObject oneDestinationJson = arr.getJSONObject(i);
        		
        		MovementDestination oneMover = new MovementDestination();
        		oneMover.source = oneDestinationJson.getString("source");
        		
        		JSONArray targetsArr = oneDestinationJson.getJSONArray("targets");
        		
            	for (int i2 = 0; i2 < targetsArr.length(); i2++) {
                    String target = targetsArr.getString(i2);
                    oneMover.destinations.add(target);
            	}
            	if (movements.containsKey(oneMover.source)) {
            		throw new BrokerException("Invalid configuration. Multiple entries exist for movementDistinations for queue '" + oneMover.source + "'.");
            	}
            	this.movements.put(oneMover.source, oneMover);
        	}        	
        }
        */
	}
	/*
	private List<String> loadList(JSONObject doc, String field) {
		List<String> rc = null; 
        if (doc.has(field)) {
        	rc = new ArrayList<String>();
        	JSONArray arr = doc.getJSONArray(field);
        	for (int i = 0; i < arr.length(); i++) {
                String value = arr.getString(i);
                rc.add(value);
            }
        }
        return rc;
	}
	*/
	
	private Broker createBrokerFromJson(JSONObject eventBroker) throws BrokerException {
		Broker b = new Broker();
		b.sempHost = eventBroker.getString("sempHost");
		b.msgVpnName = eventBroker.getString("msgVpnName");
		b.sempAdminUser = eventBroker.getString("sempAdminUser");
		
		// Handle sempAdminPw - decrypt if encrypted
		String sempAdminPw = eventBroker.getString("sempAdminPw");
		b.sempAdminPw = decryptPasswordIfNeeded(sempAdminPw, "sempAdminPw");
		
		b.name = eventBroker.getString("name");
		b.messagingHost = eventBroker.getString("messagingHost");
		b.messagingClientUsername = eventBroker.getString("messagingClientUsername");
		
		// Handle messagingPw - decrypt if encrypted
		String messagingPw = eventBroker.getString("messagingPw");
		b.messagingPw = decryptPasswordIfNeeded(messagingPw, "messagingPw");
		
		return b;
	}
	
	/**
	 * Decrypts a password if it's encrypted, otherwise returns it as-is.
	 * @param password The password string (may be encrypted or plain text)
	 * @param fieldName The name of the field (for error messages)
	 * @return The decrypted or plain text password
	 * @throws BrokerException if password is encrypted but master password is not set or decryption fails
	 */
	private String decryptPasswordIfNeeded(String password, String fieldName) throws BrokerException {
		if (PasswordEncryption.isEncrypted(password)) {
			if (masterPassword == null || masterPassword.length == 0) {
				throw new BrokerException(
					"Encrypted password found for field '" + fieldName + "' but master password is not set. " +
					"Please provide the master password using --master-password option or via GUI prompt."
				);
			}
			try {
				String masterPwStr = new String(masterPassword);
				return PasswordEncryption.decrypt(password, masterPwStr);
			} catch (Exception e) {
				throw new BrokerException(
					"Failed to decrypt password for field '" + fieldName + "': " + e.getMessage() + 
					". The master password may be incorrect."
				);
			}
		}
		// Not encrypted, return as-is (backward compatibility)
		return password;
	}
	
	/**
	 * Get the list of all configured brokers
	 * @return List of Broker objects
	 */
	public List<Broker> getBrokers() {
		return brokers;
	}
	
	/**
	 * Get the currently selected broker index
	 * @return Index of selected broker
	 */
	public int getSelectedBrokerIndex() {
		return selectedBrokerIndex;
	}
	
	/**
	 * Set the selected broker by index
	 * @param index Index of broker to select
	 * @throws BrokerException if index is out of range
	 */
	public void setSelectedBrokerIndex(int index) throws BrokerException {
		if (index < 0 || index >= brokers.size()) {
			throw new BrokerException("Invalid broker index: " + index);
		}
		selectedBrokerIndex = index;
		broker = brokers.get(index);
	}
	
	/**
	 * Set the selected broker by name
	 * @param name Name of broker to select
	 * @throws BrokerException if broker with name is not found
	 */
	public void setSelectedBrokerByName(String name) throws BrokerException {
		for (int i = 0; i < brokers.size(); i++) {
			if (brokers.get(i).name.equals(name)) {
				setSelectedBrokerIndex(i);
				return;
			}
		}
		throw new BrokerException("Broker not found: " + name);
	}
}
