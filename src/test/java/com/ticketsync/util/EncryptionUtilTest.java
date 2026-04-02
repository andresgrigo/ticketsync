package com.ticketsync.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionUtilTest {

    private static final String MASTER_KEY = "test-master-key-for-unit-tests";
    private static final String PLAINTEXT = "mySecretPassword";

    @Test
    void createEncryptor_returnsConfiguredEncryptor() {
        StandardPBEStringEncryptor encryptor = EncryptionUtil.createEncryptor(MASTER_KEY);
        assertNotNull(encryptor);
        // Verify it is functional by encrypting and decrypting a value
        String ciphertext = encryptor.encrypt(PLAINTEXT);
        assertNotNull(ciphertext);
        assertNotEquals(PLAINTEXT, ciphertext);
    }

    @Test
    void encrypt_returnsEncValueWrapped() {
        String result = EncryptionUtil.encrypt(PLAINTEXT, MASTER_KEY);
        assertNotNull(result);
        assertTrue(result.startsWith("ENC("), "Result should start with ENC(");
        assertTrue(result.endsWith(")"), "Result should end with )");
    }

    @Test
    void encrypt_ciphertextDiffersFromPlaintext() {
        String result = EncryptionUtil.encrypt(PLAINTEXT, MASTER_KEY);
        assertFalse(result.contains(PLAINTEXT), "Ciphertext must not contain the plaintext value");
    }

    @Test
    void encrypt_sameInputProducesDifferentCiphertexts() {
        // AES-256 with random IV: each call should produce a different ciphertext
        String first = EncryptionUtil.encrypt(PLAINTEXT, MASTER_KEY);
        String second = EncryptionUtil.encrypt(PLAINTEXT, MASTER_KEY);
        assertNotEquals(first, second, "Random IV should produce unique ciphertexts each time");
    }

    @Test
    void createEncryptor_canDecryptOwnCiphertext() {
        StandardPBEStringEncryptor encryptor = EncryptionUtil.createEncryptor(MASTER_KEY);
        String ciphertext = encryptor.encrypt(PLAINTEXT);
        String decrypted = encryptor.decrypt(ciphertext);
        assertEquals(PLAINTEXT, decrypted);
    }
}
