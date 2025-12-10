package utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;

@Slf4j
public class KeyUtil {
    public static void main(String[] args) throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        SecretKey secretKey = keyGen.generateKey();
        String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    public static void printLog() {
        System.out.println(EncryptDecryptUtil.decrypt("zGxvj++nOpkryewylR0gxuCA8Bbaj9msK9+4LCSTVlJWvNH2wVccnebDaMwDfipobmugpJ/T5KGYikBPMIiNjg=="));
    }
}

