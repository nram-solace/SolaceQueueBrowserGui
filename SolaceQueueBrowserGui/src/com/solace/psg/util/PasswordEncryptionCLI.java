package com.solace.psg.util;

import java.io.Console;
import java.util.Arrays;

/**
 * Command-line utility for encrypting and decrypting passwords.
 * 
 * Usage:
 *   encrypt <password> <masterKey>  - Encrypt password (non-interactive)
 *   encrypt                          - Encrypt password (interactive, more secure)
 *   decrypt <encrypted> <masterKey>  - Decrypt password (non-interactive)
 *   decrypt                          - Decrypt password (interactive, more secure)
 *   --help or -h                     - Show usage information
 */
public class PasswordEncryptionCLI {
	
	public static void main(String[] args) {
		if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
			printUsage();
			return;
		}
		
		String command = args[0];
		
		try {
			if ("encrypt".equals(command)) {
				handleEncrypt(args);
			} else if ("decrypt".equals(command)) {
				handleDecrypt(args);
			} else {
				System.err.println("Unknown command: " + command);
				printUsage();
				System.exit(1);
			}
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			if (e.getCause() != null) {
				System.err.println("Cause: " + e.getCause().getMessage());
			}
			System.exit(1);
		}
	}
	
	private static void handleEncrypt(String[] args) {
		if (args.length == 3) {
			// Non-interactive mode: password and master key as arguments
			String password = args[1];
			String masterKey = args[2];
			String encrypted = PasswordEncryption.encrypt(password, masterKey);
			System.out.println(encrypted);
		} else if (args.length == 1) {
			// Interactive mode: prompt securely
			Console console = System.console();
			if (console == null) {
				System.err.println("Error: No console available. Use: encrypt <password> <masterKey>");
				System.err.println("       Or run from a terminal/command prompt (not from IDE)");
				System.exit(1);
			}
			
			char[] password = console.readPassword("Enter password to encrypt: ");
			if (password == null || password.length == 0) {
				System.err.println("Error: Password cannot be empty");
				System.exit(1);
			}
			
			char[] masterKey = console.readPassword("Enter master password: ");
			if (masterKey == null || masterKey.length == 0) {
				Arrays.fill(password, '\0');
				System.err.println("Error: Master password cannot be empty");
				System.exit(1);
			}
			
			try {
				String encrypted = PasswordEncryption.encrypt(
					new String(password), 
					new String(masterKey)
				);
				System.out.println(encrypted);
			} finally {
				// Clear arrays from memory
				Arrays.fill(password, '\0');
				Arrays.fill(masterKey, '\0');
			}
		} else {
			System.err.println("Error: Invalid number of arguments for encrypt command");
			System.err.println("Usage: encrypt [<password> <masterKey>]");
			System.exit(1);
		}
	}
	
	private static void handleDecrypt(String[] args) {
		if (args.length == 3) {
			// Non-interactive mode: encrypted password and master key as arguments
			String encrypted = args[1];
			String masterKey = args[2];
			String decrypted = PasswordEncryption.decrypt(encrypted, masterKey);
			System.out.println(decrypted);
		} else if (args.length == 1) {
			// Interactive mode: prompt securely
			Console console = System.console();
			if (console == null) {
				System.err.println("Error: No console available. Use: decrypt <encrypted> <masterKey>");
				System.err.println("       Or run from a terminal/command prompt (not from IDE)");
				System.exit(1);
			}
			
			String encrypted = console.readLine("Enter encrypted password: ");
			if (encrypted == null || encrypted.trim().isEmpty()) {
				System.err.println("Error: Encrypted password cannot be empty");
				System.exit(1);
			}
			encrypted = encrypted.trim();
			
			char[] masterKey = console.readPassword("Enter master password: ");
			if (masterKey == null || masterKey.length == 0) {
				System.err.println("Error: Master password cannot be empty");
				System.exit(1);
			}
			
			try {
				String decrypted = PasswordEncryption.decrypt(encrypted, new String(masterKey));
				System.out.println(decrypted);
			} finally {
				// Clear array from memory
				Arrays.fill(masterKey, '\0');
			}
		} else {
			System.err.println("Error: Invalid number of arguments for decrypt command");
			System.err.println("Usage: decrypt [<encrypted> <masterKey>]");
			System.exit(1);
		}
	}
	
	private static void printUsage() {
		System.out.println("Password Encryption CLI Tool");
		System.out.println("===========================");
		System.out.println();
		System.out.println("Usage:");
		System.out.println("  encrypt <password> <masterKey>  - Encrypt password (non-interactive)");
		System.out.println("  encrypt                          - Encrypt password (interactive, more secure)");
		System.out.println("  decrypt <encrypted> <masterKey>  - Decrypt password (non-interactive)");
		System.out.println("  decrypt                          - Decrypt password (interactive, more secure)");
		System.out.println("  --help or -h                     - Show this help message");
		System.out.println();
		System.out.println("Examples:");
		System.out.println("  # Interactive mode (recommended for security):");
		System.out.println("  java -cp SolaceQueueBrowserGui.jar com.solace.psg.util.PasswordEncryptionCLI encrypt");
		System.out.println();
		System.out.println("  # Non-interactive mode (for scripts):");
		System.out.println("  java -cp SolaceQueueBrowserGui.jar com.solace.psg.util.PasswordEncryptionCLI encrypt \"myPassword\" \"masterKey\"");
		System.out.println();
		System.out.println("Note: Interactive mode hides password input and is more secure.");
		System.out.println("      Non-interactive mode may expose passwords in process lists.");
	}
}

