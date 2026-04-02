package com.ticketsync.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;

/**
 * EncryptionUtil provides AES-256 PBE encryption utilities for TicketSync
 * configuration values using Jasypt.
 *
 * <p>Values encrypted with this utility are wrapped in {@code ENC(...)} so
 * that {@link org.jasypt.properties.EncryptableProperties} can transparently
 * decrypt them when the configuration file is loaded.</p>
 *
 * <p>The encryption algorithm used is {@code PBEWithHMACSHA512AndAES_256},
 * which requires Java 8 or later (built-in JCE) and satisfies NFR-SEC03.</p>
 *

 * @version 1.0
 * @since 1.0
 */
public final class EncryptionUtil {

    private static final String ALGORITHM = "PBEWithHMACSHA512AndAES_256";

    private EncryptionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates and returns a configured {@link StandardPBEStringEncryptor} using
     * the supplied master key and the {@code PBEWithHMACSHA512AndAES_256} algorithm.
     *
     * @param masterKey the password/key used for encryption and decryption; must not be null or blank
     * @return a fully configured, ready-to-use {@link StandardPBEStringEncryptor}
     */
    public static StandardPBEStringEncryptor createEncryptor(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("masterKey must not be null or blank");
        }
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(masterKey);
        encryptor.setAlgorithm(ALGORITHM);
        encryptor.setIvGenerator(new RandomIvGenerator());
        return encryptor;
    }

    /**
     * Encrypts the given plaintext value using the supplied master key and returns
     * the result wrapped in the Jasypt {@code ENC(...)} marker so it can be placed
     * directly in a {@code .properties} file.
     *
     * @param plaintext the value to encrypt; must not be null
     * @param masterKey the password/key used for encryption; must not be null or blank
     * @return the encrypted value in the form {@code ENC(encryptedValue)}
     */
    public static String encrypt(String plaintext, String masterKey) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        String encryptedValue = createEncryptor(masterKey).encrypt(plaintext);
        return "ENC(" + encryptedValue + ")";
    }

    /**
     * Standalone command-line tool for generating {@code ENC(...)} values to place
     * in {@code jdbc.properties}.
     *
     * <p>Usage:</p>
     * <pre>
     *   java com.ticketsync.util.EncryptionUtil &lt;plaintext&gt; &lt;masterKey&gt;
     * </pre>
     *
     * @param args two arguments: {@code args[0]} = plaintext value, {@code args[1]} = master key
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java com.ticketsync.util.EncryptionUtil <plaintext> <masterKey>");
            System.exit(1);
        }
        System.out.println(encrypt(args[0], args[1]));
    }
}
