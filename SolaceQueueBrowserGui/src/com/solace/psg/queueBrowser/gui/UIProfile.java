package com.solace.psg.queueBrowser.gui;

/**
 * UI Profile utility - provides default profile name
 * Users can select any profile on any OS via config file
 */
public class UIProfile {
    
    /**
     * Get the default profile name
     * @return Default profile name ("Modern" as the default)
     */
    public static String getDefaultProfile() {
        return "Modern";
    }
}

