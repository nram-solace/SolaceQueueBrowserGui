package com.solace.psg.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for encrypting and decrypting passwords using AES-256-GCM.
 * Uses PBKDF2 for key derivation from a master password.
 * 
 * Format: ENC:AES256GCM:BASE64_ENCRYPTED_DATA:BASE64_IV:BASE64_SALT
 */
public class PasswordEncryption {
	private static final Logger logger = LoggerFactory.getLogger(PasswordEncryption.class.getName());
	
	private static final String ALGORITHM = "AES256GCM";
	private static final String ENCRYPTION_PREFIX = "ENC:";
	private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH = 128; // bits
	private static final int GCM_IV_LENGTH = 12; // bytes (96 bits recommended for GCM)
	private static final int SALT_LENGTH = 16; // bytes
	private static final int KEY_LENGTH = 256; // bits
	private static final int PBKDF2_ITERATIONS = 100000;
	private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
	
	/**
	 * Encrypts a password using AES-256-GCM with a master password.
	 * 
	 * @param password The plain text password to encrypt
	 * @param masterPassword The master password used for encryption
	 * @return Encrypted password in format: ENC:AES256GCM:base64data:iv:salt
	 * @throws RuntimeException if encryption fails
	 */
	public static String encrypt(String password, String masterPassword) {
		if (password == null || password.isEmpty()) {
			throw new IllegalArgumentException("Password cannot be null or empty");
		}
		if (masterPassword == null || masterPassword.isEmpty()) {
			throw new IllegalArgumentException("Master password cannot be null or empty");
		}
		
		try {
			// Generate random salt and IV
			SecureRandom random = new SecureRandom();
			byte[] salt = new byte[SALT_LENGTH];
			byte[] iv = new byte[GCM_IV_LENGTH];
			random.nextBytes(salt);
			random.nextBytes(iv);
			
			// Derive encryption key from master password using PBKDF2
			SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
			PBEKeySpec keySpec = new PBEKeySpec(masterPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
			SecretKey tmpKey = factory.generateSecret(keySpec);
			SecretKeySpec secretKey = new SecretKeySpec(tmpKey.getEncoded(), "AES");
			
			// Encrypt the password
			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
			
			byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
			byte[] encryptedData = cipher.doFinal(passwordBytes);
			
			// Encode components to Base64
			String base64Data = Base64.getEncoder().encodeToString(encryptedData);
			String base64IV = Base64.getEncoder().encodeToString(iv);
			String base64Salt = Base64.getEncoder().encodeToString(salt);
			
			// Clear sensitive data from memory
			clearArray(passwordBytes);
			clearArray(tmpKey.getEncoded());
			
			// Format: ENC:AES256GCM:base64data:iv:salt
			return ENCRYPTION_PREFIX + ALGORITHM + ":" + base64Data + ":" + base64IV + ":" + base64Salt;
			
		} catch (Exception e) {
			logger.error("Encryption failed", e);
			throw new RuntimeException("Failed to encrypt password: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Decrypts an encrypted password using the master password.
	 * 
	 * @param encryptedPassword The encrypted password in format: ENC:AES256GCM:base64data:iv:salt
	 * @param masterPassword The master password used for decryption
	 * @return The decrypted plain text password
	 * @throws RuntimeException if decryption fails (e.g., wrong master password)
	 */
	public static String decrypt(String encryptedPassword, String masterPassword) {
		if (encryptedPassword == null || encryptedPassword.isEmpty()) {
			throw new IllegalArgumentException("Encrypted password cannot be null or empty");
		}
		if (masterPassword == null || masterPassword.isEmpty()) {
			throw new IllegalArgumentException("Master password cannot be null or empty");
		}
		
		if (!isEncrypted(encryptedPassword)) {
			throw new IllegalArgumentException("Password is not in encrypted format. Expected format: ENC:AES256GCM:...");
		}
		
		try {
			// Parse the encrypted format: ENC:AES256GCM:base64data:iv:salt
			String[] parts = encryptedPassword.substring(ENCRYPTION_PREFIX.length()).split(":");
			if (parts.length != 4) {
				throw new IllegalArgumentException("Invalid encrypted password format. Expected: ENC:AES256GCM:data:iv:salt");
			}
			
			String algorithm = parts[0];
			if (!ALGORITHM.equals(algorithm)) {
				throw new IllegalArgumentException("Unsupported encryption algorithm: " + algorithm);
			}
			
			String base64Data = parts[1];
			String base64IV = parts[2];
			String base64Salt = parts[3];
			
			// Decode Base64 components
			byte[] encryptedData = Base64.getDecoder().decode(base64Data);
			byte[] iv = Base64.getDecoder().decode(base64IV);
			byte[] salt = Base64.getDecoder().decode(base64Salt);
			
			// Derive decryption key from master password using PBKDF2
			SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
			PBEKeySpec keySpec = new PBEKeySpec(masterPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
			SecretKey tmpKey = factory.generateSecret(keySpec);
			SecretKeySpec secretKey = new SecretKeySpec(tmpKey.getEncoded(), "AES");
			
			// Decrypt the password
			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
			
			byte[] decryptedBytes = cipher.doFinal(encryptedData);
			String decryptedPassword = new String(decryptedBytes, StandardCharsets.UTF_8);
			
			// Clear sensitive data from memory
			clearArray(decryptedBytes);
			clearArray(tmpKey.getEncoded());
			
			return decryptedPassword;
			
		} catch (javax.crypto.AEADBadTagException e) {
			logger.error("Decryption failed - likely wrong master password", e);
			throw new RuntimeException("Decryption failed. The master password may be incorrect.", e);
		} catch (Exception e) {
			logger.error("Decryption failed", e);
			throw new RuntimeException("Failed to decrypt password: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Checks if a password string is encrypted (starts with ENC: prefix).
	 * 
	 * @param password The password string to check
	 * @return true if the password appears to be encrypted, false otherwise
	 */
	public static boolean isEncrypted(String password) {
		return password != null && password.startsWith(ENCRYPTION_PREFIX);
	}
	
	/**
	 * Clears a byte array by overwriting it with zeros.
	 * This helps prevent sensitive data from remaining in memory.
	 * 
	 * @param array The array to clear
	 */
	private static void clearArray(byte[] array) {
		if (array != null) {
			java.util.Arrays.fill(array, (byte) 0);
		}
	}
}

