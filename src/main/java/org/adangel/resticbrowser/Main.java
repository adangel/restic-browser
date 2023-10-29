package org.adangel.resticbrowser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.util.encoders.Base64;
import org.json.JSONObject;

public class Main {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Path repoPath = Paths.get("src/test/resources/repos/repo1");

        JSONObject key = new JSONObject(Files.readString(repoPath.resolve("keys/5472afa3a6d2e4794d87dd72e833a14514312502e30b3454396faaec192af4f8")));
        System.out.println("key = " + key.toString(2));

        String password = "test";

        byte[] bytes = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), Base64.decode(key.getString("salt")),
                key.getInt("N"), key.getInt("r"), key.getInt("p"), 64);

        // AES-256
        byte[] aes256Key = new byte[32];
        System.arraycopy(bytes, 0, aes256Key, 0, 32);

        // Poly1305-AES
        byte[] authenticationKey = new byte[32];
        System.arraycopy(bytes, 32, authenticationKey, 0, 32);

        /*
         * In the first 16 bytes of each encrypted file the initialisation vector (IV) is stored. It is followed by
         * the encrypted data and completed by the 16 byte MAC. The format is: IV || CIPHERTEXT || MAC
         */
        byte[] data = Base64.decode(key.getString("data"));

        //Security.addProvider(new BouncyCastleProvider());

        // https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html

        IvParameterSpec iv = new IvParameterSpec(data, 0, 16);
        SecretKeySpec secretKeySpec = new SecretKeySpec(aes256Key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv);
        byte[] masterkey = cipher.doFinal(data, 16, data.length - 16 - 16);
        JSONObject masterkeyJson = new JSONObject(new String(masterkey, StandardCharsets.UTF_8));
        System.out.println("MasterKey: " + masterkeyJson.toString(2));

        Poly1305 mac = new Poly1305(AESEngine.newInstance());
        byte[] authenticationKeySwapped = new byte[32];
        System.arraycopy(authenticationKey, 16, authenticationKeySwapped, 0, 16); // r portion first for bouncycastle
        System.arraycopy(authenticationKey, 0, authenticationKeySwapped, 16, 16); // key last for bouncycastle
        CipherParameters polyparams = new KeyParameter(authenticationKeySwapped, 0, 32);
        CipherParameters ivparam = new ParametersWithIV(polyparams, data, 0, 16);
        mac.init(ivparam);
        mac.update(data, 16, data.length - 16 - 16);
        byte[] calculatedMac = new byte[mac.getMacSize()];
        mac.doFinal(calculatedMac, 0);

        byte[] originalMac = new byte[16];
        System.arraycopy(data, data.length - 16, originalMac, 0, 16);

        System.out.println("Mac equal? " + Arrays.equals(originalMac, calculatedMac));


        SecretKeySpec masterKeySpec = new SecretKeySpec(Base64.decode(masterkeyJson.getString("encrypt")), "AES");
        readFile(repoPath.resolve("config"), masterKeySpec);

        try (Stream<Path> stream = Files.list(repoPath.resolve("snapshots"))) {
            stream.forEach(p -> {
                try {
                    readFile(p, masterKeySpec);
                } catch (IOException | NoSuchPaddingException | NoSuchAlgorithmException |
                         InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException |
                         BadPaddingException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void readFile(Path file, SecretKeySpec masterKeySpec) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] encryptedData = Files.readAllBytes(file);
        IvParameterSpec iv = new IvParameterSpec(encryptedData, 0, 16);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKeySpec, iv);
        byte[] decrypted = cipher.doFinal(encryptedData, 16, encryptedData.length - 16 - 16);
        JSONObject json;
        if (decrypted[0] == '{' || decrypted[0] == '[') {
            json = new JSONObject(new String(decrypted, StandardCharsets.UTF_8));
        } else {
            ZstdCompressorInputStream decompressedStream = new ZstdCompressorInputStream(new ByteArrayInputStream(decrypted, 1, decrypted.length - 1));
            byte[] decompressed = decompressedStream.readAllBytes();
            json = new JSONObject(new String(decompressed, StandardCharsets.UTF_8));
        }
        System.out.println(file + ":\n" + json.toString(2));

    }
}
