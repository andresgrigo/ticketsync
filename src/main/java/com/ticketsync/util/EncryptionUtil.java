package com.ticketsync.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;

/**
 * EncryptionUtil proporciona utilidades de cifrado AES-256 PBE para los valores
 * de configuración de TicketSync usando Jasypt.
 *
 * <p>Los valores cifrados con esta utilidad se envuelven en {@code ENC(...)} para
 * que {@link org.jasypt.properties.EncryptableProperties} pueda descifrarlos
 * de forma transparente cuando se carga el archivo de configuración.</p>
 *
 * <p>El algoritmo de cifrado utilizado es {@code PBEWithHMACSHA512AndAES_256},
 * que requiere Java 8 o posterior (JCE integrado).</p>
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
     * Crea y devuelve un {@link StandardPBEStringEncryptor} configurado usando
     * la clave maestra proporcionada y el algoritmo {@code PBEWithHMACSHA512AndAES_256}.
     *
     * @param masterKey la contraseña/clave usada para cifrar y descifrar; no debe ser null ni estar en blanco
     * @return un {@link StandardPBEStringEncryptor} completamente configurado y listo para usar
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
     * Cifra el valor en texto plano dado usando la clave maestra proporcionada y devuelve
     * el resultado envuelto en el marcador {@code ENC(...)} de Jasypt para que pueda
     * colocarse directamente en un archivo {@code .properties}.
     *
     * @param plaintext el valor a cifrar; no debe ser null
     * @param masterKey la contraseña/clave usada para el cifrado; no debe ser null ni estar en blanco
     * @return el valor cifrado en la forma {@code ENC(valorCifrado)}
     */
    public static String encrypt(String plaintext, String masterKey) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        String encryptedValue = createEncryptor(masterKey).encrypt(plaintext);
        return "ENC(" + encryptedValue + ")";
    }

    /**
     * Herramienta de línea de comandos independiente para generar valores {@code ENC(...)} para
     * colocar en {@code jdbc.properties}.
     *
     * <p>Uso:</p>
     * <pre>
     *   java com.ticketsync.util.EncryptionUtil &lt;textoPlano&gt; &lt;claveMaestra&gt;
     * </pre>
     *
     * @param args dos argumentos: {@code args[0]} = valor en texto plano, {@code args[1]} = clave maestra
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java com.ticketsync.util.EncryptionUtil <plaintext> <masterKey>");
            System.exit(1);
        }
        System.out.println(encrypt(args[0], args[1]));
    }
}
