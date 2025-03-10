package utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Slf4j
public class EncryptDecryptUtil {
    private static final String AES_KEY = "sB1+lkOiIqzMKO2yR/B91A==";

    private static final String PLAIN_TEXT = "";

    public static void main(String[] args) {
        try {
            String encryptedText = encrypt(PLAIN_TEXT, AES_KEY);
            System.out.println(encryptedText);
            String decryptedText = decrypt(encryptedText, AES_KEY);
            System.out.printf(decryptedText);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public static String encrypt(String plainText, String base64Key) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());

        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String encryptedText, String base64Key) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));

        return new String(decryptedBytes);
    }

    public static String decrypt(String encryptedText) {
        try {
            return decrypt(encryptedText, AES_KEY);
        } catch (Exception e) {
            log.error("decrypt异常！");
            return PLAIN_TEXT;
        }
    }

}
