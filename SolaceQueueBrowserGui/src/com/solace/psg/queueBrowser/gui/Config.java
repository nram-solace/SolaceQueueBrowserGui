package com.solace.psg.queueBrowser.gui;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solace.psg.util.FileUtils;
import com.solace.psg.util.PasswordEncryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {
	private static final Logger logger = LoggerFactory.getLogger(Config.class.getName());
	
	private String configFile;
	public List<Broker> brokers = new ArrayList<Broker>();
	public Broker broker; // Currently selected broker (for backward compatibility)
	private int selectedBrokerIndex = 0;
	public String downloadFolder = "./downloads";
	
	// UI Configuration - OS-agnostic defaults
	public String fontFamily = null; // null means use FlatLaf default
	public String defaultFontFamilyFallback = "Serif"; // Fallback when fontFamily is null
	public int defaultFontSize = 14;
	public int headerFontSize = 16;
	public int labelFontSize = 16;
	public int buttonFontSize = 14;
	public int tableFontSize = 14;
	public int smallFontSize = 11;
	public int largeFontSize = 20;
	public int statusFontSize = 22;
	public String textAreaFontFamily = "Monospaced";
	public int textAreaFontSize = 12;
	public String version = "v2.1.3";
	public String selectedProfile = null; // Track which profile was loaded
	private String commandLineProfileOverride = null; // Command-line profile override
	public boolean buttonTextIcons = false; // Enable text icons in buttons (default false)
	
	// UI Colors - defaults matching current hardcoded values
	public Color rowEvenBackground = new Color(248, 248, 248);
	public Color rowOddBackground = Color.WHITE;
	public Color rowSelectedBackground = new Color(144, 238, 144);
	public Color rowForeground = Color.BLACK;
	public Color rowSelectedForeground = Color.BLACK;
	public Color textForeground = Color.BLACK;
	public Color gridColor = new Color(240, 240, 240);
	public Color buttonRefresh = new Color(220, 245, 255);
	public Color buttonRefreshForeground = Color.BLACK;
	public Color buttonFilter = new Color(240, 230, 255);
	public Color buttonFilterForeground = Color.BLACK;
	public Color buttonNavigation = new Color(230, 240, 255);
	public Color buttonNavigationForeground = Color.BLACK;
	public Color buttonDelete = new Color(255, 220, 220);
	public Color buttonDeleteForeground = Color.BLACK;
	public Color buttonCopy = new Color(220, 235, 255);
	public Color buttonCopyForeground = Color.BLACK;
	public Color buttonMove = new Color(255, 245, 220);
	public Color buttonMoveForeground = Color.BLACK;
	public Color buttonRestore = new Color(220, 255, 220);
	public Color buttonRestoreForeground = Color.BLACK;
	public Color buttonExit = new Color(220, 150, 150);
	public Color buttonExitForeground = Color.WHITE;

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
			throw new BrokerException("Failed to read user configuration file: " + e.getMessage());
		}
		JSONObject doc = null;
		try {
			doc = new JSONObject(fileContent);
		} catch (JSONException e) {
			throw new BrokerException("Failed to parse user configuration file: " + e.getMessage());
		}
		
		// Check eventBrokers array
		if (doc.has("eventBrokers")) {
			JSONArray eventBrokersArray = doc.getJSONArray("eventBrokers");
			for (int i = 0; i < eventBrokersArray.length(); i++) {
				JSONObject eventBroker = eventBrokersArray.getJSONObject(i);
				if (hasEncryptedPasswordInBroker(eventBroker)) {
					return true;
				}
			}
		} else {
			throw new BrokerException("User configuration must contain 'eventBrokers' array");
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
		// First, load system.json (mandatory)
		loadSystemConfig();
		
		// Then load user config file
		String fileContent = null;
		try {
			fileContent = FileUtils.loadFile(this.configFile);
		} catch (IOException e) {
			throw new BrokerException("Failed to read user configuration file: " + e.getMessage());
		}
		JSONObject doc = null;
		try {
			doc = new JSONObject(fileContent);
		} catch (JSONException e) {
			throw new BrokerException("Failed to parse user configuration file: " + e.getMessage());
		}
		
		// Load system properties from user config (allows override, though not recommended)
		try {
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
		} catch (JSONException e) {
			throw new BrokerException("Failed to process user configuration: " + e.getMessage());
		}

		// Load event brokers - must be array format
		try {
			if (doc.has("eventBrokers")) {
				JSONArray eventBrokersArray = doc.getJSONArray("eventBrokers");
				if (eventBrokersArray.length() == 0) {
					throw new BrokerException("Configuration must contain at least one event broker in 'eventBrokers' array");
				}
				for (int i = 0; i < eventBrokersArray.length(); i++) {
					JSONObject eventBroker = eventBrokersArray.getJSONObject(i);
					Broker b = createBrokerFromJson(eventBroker);
					brokers.add(b);
				}
				// Set first broker as default selected broker
				broker = brokers.get(0);
				selectedBrokerIndex = 0;
			} else {
				throw new BrokerException("User configuration must contain 'eventBrokers' array");
			}
		} catch (JSONException e) {
			throw new BrokerException("Failed to process eventBrokers configuration: " + e.getMessage());
		}
	}
	
	/**
	 * Load system configuration from config/system.json
	 * This file contains system/internal properties like download folder and UI settings
	 * This file is mandatory and the application will exit with an error if it doesn't exist or fails to load.
	 */
	private void loadSystemConfig() throws BrokerException {
		String systemConfigFile = "config/system.json";
		String fileContent = null;
		try {
			fileContent = FileUtils.loadFile(systemConfigFile);
		} catch (IOException e) {
			throw new BrokerException("Failed to read system configuration file '" + systemConfigFile + "': " + e.getMessage() + ". The system.json file is required.");
		}
		JSONObject systemDoc = null;
		try {
			systemDoc = new JSONObject(fileContent);
		} catch (JSONException e) {
			throw new BrokerException("Failed to parse system configuration file '" + systemConfigFile + "': " + e.getMessage() + ". The system.json file must be valid JSON.");
		}
		
		try {
			// Load version from top level
			if (systemDoc.has("version")) {
				version = systemDoc.getString("version");
			}
			
			// Load download folder
			if (systemDoc.has("downloadFolder")) {
				downloadFolder = systemDoc.getString("downloadFolder");
			}
			
			// Load UI profile
			loadUIProfile(systemDoc);
		} catch (JSONException e) {
			throw new BrokerException("Failed to process system configuration file '" + systemConfigFile + "': " + e.getMessage());
		}
        
        /* This code removed for V1 release
         * 
        if (doc.has("blackListedQueues") && doc.has("whiteListedQueues")) {
        	throw new BrokerException("Invalid configuration. You cannot specify both a blacklist and a whitelist");
        }
        blackListedQueues = loadList(doc, "blackListedQueues");
        whiteListedQueues = loadList(doc, "whiteListedQueues");
        
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
	
	/**
	 * Load a color from JSON array [R, G, B] format
	 * @param colorsConfig JSON object containing color configurations
	 * @param key The key for the color property
	 * @param defaultValue Default color if key is not found or invalid
	 * @return Color object loaded from config or default value
	 */
	private Color loadColor(JSONObject colorsConfig, String key, Color defaultValue) {
		try {
			if (colorsConfig.has(key)) {
				JSONArray rgbArray = colorsConfig.getJSONArray(key);
				if (rgbArray.length() >= 3) {
					int r = rgbArray.getInt(0);
					int g = rgbArray.getInt(1);
					int b = rgbArray.getInt(2);
					return new Color(r, g, b);
				}
			}
		} catch (JSONException e) {
			// If parsing fails, return default value
		}
		return defaultValue;
	}
	
	/**
	 * Load a UI profile from a separate JSON file
	 * @param profileName Name of the profile to load (e.g., "Modern", "Clean", "Dark")
	 * @return JSONObject containing the profile configuration
	 * @throws BrokerException if the profile file cannot be loaded or parsed
	 */
	private JSONObject loadProfileFromFile(String profileName) throws BrokerException {
		String profileFile = "config/ui/" + profileName + ".json";
		String fileContent = null;
		try {
			fileContent = FileUtils.loadFile(profileFile);
		} catch (IOException e) {
			throw new BrokerException("Failed to read UI profile file '" + profileFile + "': " + e.getMessage());
		}
		JSONObject profile = null;
		try {
			profile = new JSONObject(fileContent);
		} catch (JSONException e) {
			throw new BrokerException("Failed to parse UI profile file '" + profileFile + "': " + e.getMessage());
		}
		return profile;
	}
	
	/**
	 * Load UI profile from configuration
	 * Loads profile from separate JSON file based on uiProfile setting in system.json
	 * Falls back to default.json if the selected profile cannot be loaded
	 * @param systemDoc System configuration object from system.json
	 * @throws BrokerException if configuration is invalid
	 */
	private void loadUIProfile(JSONObject systemDoc) throws BrokerException {
		try {
			// Determine which profile to use
			String profileName = determineProfile(systemDoc);
			
			JSONObject profile = null;
			boolean loadedFromDefault = false;
			
			// Try to load the selected profile
			try {
				profile = loadProfileFromFile(profileName);
				selectedProfile = profileName;
			} catch (BrokerException e) {
				// If loading the selected profile fails, try to load default.json
				logger.warn("UI Profile: Failed to load profile '{}': {}. Attempting to load default profile.", profileName, e.getMessage());
				try {
					profile = loadProfileFromFile("default");
					selectedProfile = "default";
					loadedFromDefault = true;
				} catch (BrokerException defaultException) {
					// If default.json also fails, throw the original error
					throw new BrokerException("Failed to load UI profile '" + profileName + "' and default profile: " + defaultException.getMessage());
				}
			}
			
			// Log profile selection
			String description = profile.has("description") ? profile.getString("description") : "";
			String logMessage = "UI Profile selected: '" + selectedProfile + "'" + 
				(description.isEmpty() ? "" : " - " + description);
			if (loadedFromDefault) {
				logMessage += " (fallback from '" + profileName + "')";
			}
			if (commandLineProfileOverride != null) {
				logMessage += " (via command-line override)";
			}
			logger.info(logMessage);
			System.out.println("=================================================================");
			System.out.println(logMessage);
			System.out.println("=================================================================");
			
			// Load buttonTextIcons setting
			if (profile.has("buttonTextIcons")) {
				buttonTextIcons = profile.getBoolean("buttonTextIcons");
			}
			
			// Load font and colors from profile
			if (profile.has("font")) {
				loadFontConfig(profile.getJSONObject("font"));
			} else {
				logger.warn("UI Profile '{}': No 'font' section found, using defaults", selectedProfile);
			}
			if (profile.has("colors")) {
				loadColorConfig(profile.getJSONObject("colors"));
			} else {
				logger.warn("UI Profile '{}': No 'colors' section found, using defaults", selectedProfile);
			}
		} catch (JSONException e) {
			throw new BrokerException("Failed to load UI profile: " + e.getMessage());
		}
	}
	
	/**
	 * Set command-line profile override
	 * @param profileName Profile name from command line (overrides config file setting)
	 */
	public void setCommandLineProfileOverride(String profileName) {
		if (profileName != null && !profileName.trim().isEmpty()) {
			commandLineProfileOverride = profileName.trim();
			logger.info("UI Profile: Command-line override set to '{}'", commandLineProfileOverride);
		}
	}
	
	/**
	 * Determine which profile to use based on configuration
	 * Priority: 1. Command-line override, 2. Config file setting (uiProfile), 3. Default
	 * @param systemDoc System configuration object from system.json
	 * @return Profile name to use
	 */
	private String determineProfile(JSONObject systemDoc) {
		// Priority 1: Command-line override (highest priority)
		if (commandLineProfileOverride != null && !commandLineProfileOverride.isEmpty()) {
			logger.info("UI Profile: Using command-line override '{}'", commandLineProfileOverride);
			return commandLineProfileOverride;
		}
		
		// Priority 2: Config file setting (uiProfile at root level)
		try {
			if (systemDoc.has("uiProfile")) {
				String profile = systemDoc.getString("uiProfile");
				if (profile != null && !profile.isEmpty() && !profile.equals("auto")) {
					logger.debug("UI Profile: Explicitly set to '{}'", profile);
					return profile;
				} else if (profile != null && profile.equals("auto")) {
					logger.debug("UI Profile: Set to 'auto', using default profile");
				}
			}
		} catch (JSONException e) {
			// Ignore and use default
			logger.warn("UI Profile: Error reading uiProfile setting, using default: {}", e.getMessage());
		}
		
		// Priority 3: Default profile
		String defaultProfile = UIProfile.getDefaultProfile();
		logger.debug("UI Profile: Using default profile '{}'", defaultProfile);
		return defaultProfile;
	}
	
	/**
	 * Load font configuration from JSON
	 * @param fontConfig Font configuration object
	 */
	private void loadFontConfig(JSONObject fontConfig) {
		try {
			if (fontConfig.has("fontFamily") && !fontConfig.isNull("fontFamily")) {
				fontFamily = fontConfig.getString("fontFamily");
			}
			if (fontConfig.has("defaultFontFamilyFallback")) {
				defaultFontFamilyFallback = fontConfig.getString("defaultFontFamilyFallback");
			}
			if (fontConfig.has("defaultFontSize")) {
				defaultFontSize = fontConfig.getInt("defaultFontSize");
			}
			if (fontConfig.has("headerFontSize")) {
				headerFontSize = fontConfig.getInt("headerFontSize");
			}
			if (fontConfig.has("labelFontSize")) {
				labelFontSize = fontConfig.getInt("labelFontSize");
			}
			if (fontConfig.has("buttonFontSize")) {
				buttonFontSize = fontConfig.getInt("buttonFontSize");
			}
			if (fontConfig.has("tableFontSize")) {
				tableFontSize = fontConfig.getInt("tableFontSize");
			}
			if (fontConfig.has("smallFontSize")) {
				smallFontSize = fontConfig.getInt("smallFontSize");
			}
			if (fontConfig.has("largeFontSize")) {
				largeFontSize = fontConfig.getInt("largeFontSize");
			}
			if (fontConfig.has("statusFontSize")) {
				statusFontSize = fontConfig.getInt("statusFontSize");
			}
			if (fontConfig.has("textAreaFontFamily")) {
				textAreaFontFamily = fontConfig.getString("textAreaFontFamily");
			}
			if (fontConfig.has("textAreaFontSize")) {
				textAreaFontSize = fontConfig.getInt("textAreaFontSize");
			}
		} catch (JSONException e) {
			// If parsing fails, use defaults (already set in field declarations)
		}
	}
	
	/**
	 * Load color configuration from JSON
	 * @param colorsConfig Color configuration object
	 */
	private void loadColorConfig(JSONObject colorsConfig) {
		rowEvenBackground = loadColor(colorsConfig, "rowEvenBackground", rowEvenBackground);
		rowOddBackground = loadColor(colorsConfig, "rowOddBackground", rowOddBackground);
		rowSelectedBackground = loadColor(colorsConfig, "rowSelectedBackground", rowSelectedBackground);
		rowForeground = loadColor(colorsConfig, "rowForeground", rowForeground);
		rowSelectedForeground = loadColor(colorsConfig, "rowSelectedForeground", rowSelectedForeground);
		textForeground = loadColor(colorsConfig, "textForeground", textForeground);
		gridColor = loadColor(colorsConfig, "gridColor", gridColor);
		buttonRefresh = loadColor(colorsConfig, "buttonRefresh", buttonRefresh);
		buttonRefreshForeground = loadColor(colorsConfig, "buttonRefreshForeground", buttonRefreshForeground);
		buttonFilter = loadColor(colorsConfig, "buttonFilter", buttonFilter);
		buttonFilterForeground = loadColor(colorsConfig, "buttonFilterForeground", buttonFilterForeground);
		buttonNavigation = loadColor(colorsConfig, "buttonNavigation", buttonNavigation);
		buttonNavigationForeground = loadColor(colorsConfig, "buttonNavigationForeground", buttonNavigationForeground);
		buttonDelete = loadColor(colorsConfig, "buttonDelete", buttonDelete);
		buttonDeleteForeground = loadColor(colorsConfig, "buttonDeleteForeground", buttonDeleteForeground);
		buttonCopy = loadColor(colorsConfig, "buttonCopy", buttonCopy);
		buttonCopyForeground = loadColor(colorsConfig, "buttonCopyForeground", buttonCopyForeground);
		buttonMove = loadColor(colorsConfig, "buttonMove", buttonMove);
		buttonMoveForeground = loadColor(colorsConfig, "buttonMoveForeground", buttonMoveForeground);
		buttonRestore = loadColor(colorsConfig, "buttonRestore", buttonRestore);
		buttonRestoreForeground = loadColor(colorsConfig, "buttonRestoreForeground", buttonRestoreForeground);
		buttonExit = loadColor(colorsConfig, "buttonExit", buttonExit);
		buttonExitForeground = loadColor(colorsConfig, "buttonExitForeground", buttonExitForeground);
	}
	
	/**
	 * Format button text with icon if buttonTextIcons is enabled
	 * @param icon The Unicode icon character
	 * @param text The button text
	 * @return Formatted button text with icon prefix if enabled, otherwise just text
	 */
	public String formatButtonText(String icon, String text) {
		if (buttonTextIcons && icon != null && !icon.isEmpty()) {
			return icon + " " + text;
		}
		return text;
	}
}
