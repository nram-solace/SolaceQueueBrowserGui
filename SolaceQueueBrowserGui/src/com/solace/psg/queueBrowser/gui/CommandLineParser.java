package com.solace.psg.queueBrowser.gui;

import org.apache.commons.cli.*;

import com.solace.psg.brokers.BrokerException;

public class CommandLineParser {
	private static final String configFile = "config";
	private static final String masterPassword = "master-password";
	private static final String uiProfile = "ui-profile";
	public String configFileProvided = "";
	public String masterPasswordProvided = null;
	public String uiProfileProvided = null;
	
    public void parseArgs(String[] args) throws BrokerException {
        Options options = new Options();
        Option configFileArg = Option.builder("c")
                                 .longOpt(configFile)
                                 .hasArg()
                                 .argName("CONFIG_FILE")
                                 .desc("Configuration file. A json file specifying the broker to poll and the ")
                                 .required(true)
                                 .build();
        Option masterPasswordArg = Option.builder("mp")
                                 .longOpt(masterPassword)
                                 .hasArg()
                                 .argName("MASTER_PASSWORD")
                                 .desc("Master password for decrypting encrypted passwords in config file")
                                 .required(false)
                                 .build();
        Option uiProfileArg = Option.builder("up")
                                 .longOpt(uiProfile)
                                 .hasArg()
                                 .argName("PROFILE_NAME")
                                 .desc("UI profile to use (Clean, Modern, Dark). Overrides profile setting in system.json")
                                 .required(false)
                                 .build();
        options.addOption(configFileArg);
        options.addOption(masterPasswordArg);
        options.addOption(uiProfileArg);
        DefaultParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            configFileProvided = cmd.getOptionValue(configFile);
            masterPasswordProvided = cmd.getOptionValue(masterPassword);
            uiProfileProvided = cmd.getOptionValue(uiProfile);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("SolaceQueueBrowserGui", options);
            throw new BrokerException(e);
        }
    }
}
