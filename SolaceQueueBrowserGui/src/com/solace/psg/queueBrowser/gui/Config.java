package com.solace.psg.queueBrowser.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.solace.psg.brokers.Broker;
import com.solace.psg.brokers.BrokerException;
import com.solace.psg.util.FileUtils;

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
	public String version = "v2.0.2";

	/* removed for V1 release
	public List<String> blackListedQueues = null;
	public List<String> whiteListedQueues = null;
	public HashMap<String, MovementDestination> movements = new HashMap<String, MovementDestination>(); 
	*/
	class MovementDestination {
		String source = "";
		List<String> destinations = new ArrayList<String>();
	}
	
	public Config(String file) {
		this.configFile = file;
	}
	public void load() throws BrokerException  {
	    String fileContent = null;
		try {
			fileContent = FileUtils.loadFile(this.configFile);
		} catch (IOException e) {
			throw new BrokerException(e);
		}
		JSONObject doc = new JSONObject(fileContent);
		if (doc.has("downloadFolder")) {
			downloadFolder = doc.getString("downloadFolder");
		}
		
		// Load UI configuration if present
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
		b.sempAdminPw = eventBroker.getString("sempAdminPw");
		b.name = eventBroker.getString("name");
		b.messagingHost = eventBroker.getString("messagingHost");
		b.messagingClientUsername = eventBroker.getString("messagingClientUsername");
		b.messagingPw = eventBroker.getString("messagingPw");
		return b;
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
