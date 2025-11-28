package com.solace.psg.brokers;

import java.net.MalformedURLException;
import java.net.URL;


public class Broker {
	public String name;
	public String sempHost = "";
	public String sempAdminUser = "";
	public String sempAdminPw = "";
	public String msgVpnName = "";
	public String messagingClientUsername = "";
	public String messagingPw = "";
	public String messagingHost;
	
	public String fqdn() {
		try {
			// Use URL parsing for robust handling of http://, https://, tcp://, tcps:// URLs
			URL url = new URL(this.sempHost);
			String host = url.getHost();
			if (host != null && !host.isEmpty()) {
				return host;
			}
			// Fallback to old method if URL parsing fails or returns empty host
			String[] parts = this.sempHost.split(":");
			if (parts.length >= 2) {
				String rc = parts[1];
				rc = rc.replaceAll("/", "");
				return rc;
			}
			return this.sempHost;
		} catch (MalformedURLException e) {
			// Fallback to old method if URL is malformed
			String[] parts = this.sempHost.split(":");
			if (parts.length >= 2) {
				String rc = parts[1];
				rc = rc.replaceAll("/", "");
				return rc;
			}
			return this.sempHost;
		}
	}
}
