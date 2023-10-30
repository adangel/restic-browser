package org.adangel.resticbrowser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
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

public class Repository {
    private static final Logger LOGGER = Logger.getLogger(Repository.class.getName());

    private final Path path;
    private SecretKeySpec masterKeySpec;
    private CipherParameters macParams;

    public Repository(Path path, String password) throws IOException {
        this.path = path;
        initMasterKey(password);
    }

    private void initMasterKey(String password) throws IOException {
        LOGGER.fine("Searching for matching keys in " + path);
        try (Stream<Path> keyFileStream = Files.list(path.resolve("keys"))) {
            boolean foundMasterKey = keyFileStream.anyMatch(keyFile -> {
                try {
                    LOGGER.fine("Trying " + keyFile + " ...");
                    JSONObject key = new JSONObject(Files.readString(keyFile));
                    LOGGER.fine("key = " + key.toString(2));

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

                    IvParameterSpec iv = new IvParameterSpec(data, 0, 16);
                    SecretKeySpec secretKeySpec = new SecretKeySpec(aes256Key, "AES");
                    Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv);
                    byte[] masterkey = cipher.doFinal(data, 16, data.length - 16 - 16);
                    JSONObject masterkeyJson = new JSONObject(new String(masterkey, StandardCharsets.UTF_8));

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

                    if (!Arrays.equals(originalMac, calculatedMac)) {
                        LOGGER.severe("MAC doesn't match");
                        return false;
                    }

                    masterKeySpec = new SecretKeySpec(Base64.decode(masterkeyJson.getString("encrypt")), "AES");

                    byte[] mac_r = Base64.decode(masterkeyJson.getJSONObject("mac").getString("r"));
                    if (mac_r.length != 16) {
                        throw new IllegalStateException("mac_r is not 16 bytes");
                    }
                    byte[] mac_k = Base64.decode(masterkeyJson.getJSONObject("mac").getString("k"));
                    if (mac_k.length != 16) {
                        throw new IllegalStateException("mac_k is not 16 bytes");
                    }
                    byte[] authenticationKeyCombined = new byte[32];
                    System.arraycopy(mac_r, 0, authenticationKeyCombined, 0, 16);
                    System.arraycopy(mac_k, 0, authenticationKeyCombined, 16, 16);
                    macParams = new KeyParameter(authenticationKeyCombined);

                    LOGGER.info("Found master key in " + keyFile + ", password was valid");

                    return true;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error reading master key from " + keyFile, e);
                }
                return false;
            });
            if (!foundMasterKey) {
                throw new IllegalArgumentException("Wrong password");
            }
        }
    }

    public boolean hasMasterKey() {
        return masterKeySpec != null;
    }

    public JSONObject readFile(Path file) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] encryptedData = Files.readAllBytes(path.resolve(file));
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

        Poly1305 mac = new Poly1305(AESEngine.newInstance());
        CipherParameters macParams = new ParametersWithIV(this.macParams, iv.getIV());
        mac.init(macParams);
        mac.update(encryptedData, 16, encryptedData.length - 32);
        byte[] calculatedMac = new byte[mac.getMacSize()];
        mac.doFinal(calculatedMac, 0);

        byte[] originalMac = new byte[16];
        System.arraycopy(encryptedData, encryptedData.length - 16, originalMac, 0, 16);

        if (!Arrays.equals(originalMac, calculatedMac)) {
            LOGGER.severe("MAC doesn't match");
            throw new RuntimeException("MAC Doesn't match");
        }

        return json;
    }

    public String getId() {
        try {
            JSONObject config = readFile(Path.of("config"));
            return config.getString("id");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
