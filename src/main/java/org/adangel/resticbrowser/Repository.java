package org.adangel.resticbrowser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.adangel.resticbrowser.models.Config;
import org.adangel.resticbrowser.models.Index;
import org.adangel.resticbrowser.models.Key;
import org.adangel.resticbrowser.models.Masterkey;
import org.adangel.resticbrowser.models.Snapshot;
import org.adangel.resticbrowser.models.SnapshotWithId;
import org.adangel.resticbrowser.models.Tree;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.util.encoders.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Repository {
    private static final Logger LOGGER = Logger.getLogger(Repository.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

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
                    Key key = MAPPER.readValue(Files.readString(keyFile), Key.class);

                    byte[] bytes = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), Base64.decode(key.salt()),
                            key.N(), key.r(), key.p(), 64);

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
                    byte[] data = Base64.decode(key.data());

                    IvParameterSpec iv = new IvParameterSpec(data, 0, 16);
                    SecretKeySpec secretKeySpec = new SecretKeySpec(aes256Key, "AES");
                    Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv);
                    byte[] masterkeyBytes = cipher.doFinal(data, 16, data.length - 16 - 16);
                    Masterkey masterkey = MAPPER.readValue(masterkeyBytes, Masterkey.class);

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

                    masterKeySpec = new SecretKeySpec(Base64.decode(masterkey.encrypt()), "AES");

                    byte[] mac_r = Base64.decode(masterkey.mac().r());
                    if (mac_r.length != 16) {
                        throw new IllegalStateException("mac_r is not 16 bytes");
                    }
                    byte[] mac_k = Base64.decode(masterkey.mac().k());
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

    private byte[] decryptBytes(byte[] encryptedData, boolean uncompress) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        IvParameterSpec iv = new IvParameterSpec(encryptedData, 0, 16);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKeySpec, iv);
        byte[] decrypted = cipher.doFinal(encryptedData, 16, encryptedData.length - 16 - 16);

        if (uncompress) {
            ZstdCompressorInputStream decompressedStream = new ZstdCompressorInputStream(new ByteArrayInputStream(decrypted));
            decrypted = decompressedStream.readAllBytes();
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

        return decrypted;
    }

    public <T> T readFile(Path file, Class<T> clazz) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] encryptedData = Files.readAllBytes(path.resolve(file));
        byte[] decrypted = decryptBytes(encryptedData, false);
        T json;
        if (decrypted[0] == '{' || decrypted[0] == '[') {
            json = MAPPER.readValue(decrypted, clazz);
        } else {
            ZstdCompressorInputStream decompressedStream = new ZstdCompressorInputStream(new ByteArrayInputStream(decrypted, 1, decrypted.length - 1));
            byte[] decompressed = decompressedStream.readAllBytes();
            json = MAPPER.readValue(decompressed, clazz);
        }

        return json;
    }

    public String getId() {
        try {
            Config config = readFile(Path.of("config"), Config.class);
            return config.id();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Path getPath() {
        return path;
    }

    public List<SnapshotWithId> listSnapshots() throws IOException {
        try (Stream<Path> snapshotStream = Files.list(path.resolve("snapshots"))) {
            return snapshotStream.map(file -> {
                try {
                    Snapshot snapshot = readFile(path.relativize(file), Snapshot.class);
                    return new SnapshotWithId(file.getFileName().toString(), snapshot);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        }
    }

    public Tree readTree(String tree) throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        return MAPPER.readValue(readContent(tree), Tree.class);
    }

    public List<String> listFiles(String snapshotId) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, IOException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        Snapshot snapshot = readFile(Path.of("snapshots", snapshotId), Snapshot.class);
        List<String> files = new ArrayList<>();

        Tree tree = readTree(snapshot.tree());
        listFilesInTree(tree, files, Path.of("/"));

        return files;
    }

    private void listFilesInTree(Tree tree, List<String> files, Path current) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, IOException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        for (Tree.Node node : tree.nodes()) {
            Path subPath = current.resolve(node.name());
            files.add(subPath.toString());
            if (node.type() == Tree.NodeType.DIR) {
                Tree next = readTree(node.subtree());
                listFilesInTree(next, files, subPath);
            }
        }
    }

    public List<Tree.Node> listFiles(String snapshotId, String path) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, IOException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        if (path.charAt(0) != '/') {
            throw new IllegalArgumentException("path must be absolute");
        }
        Snapshot snapshot = readFile(Path.of("snapshots", snapshotId), Snapshot.class);
        Tree tree = readTree(snapshot.tree());
        String[] segments = path.substring(1).split("/");
        for (int i = 0; i < segments.length; i++) {
            String name = segments[i];
            if (!name.isEmpty()) {
                Tree.Node dir = tree.nodes().stream().filter(node -> node.name().equals(name)).findFirst().get();
                if (dir.type() != Tree.NodeType.DIR) {
                    throw new IllegalArgumentException(name + " is not a directory");
                }
                tree = readTree(dir.subtree());
            }
        }
        return tree.nodes();
    }

    record FoundBlob(Index.Pack pack, Index.Pack.Blob blob) {}

    private FoundBlob findBlob(String sha256) throws IOException {
        List<Index> indexes = new ArrayList<>();
        LOGGER.info("Loading indexes...");
        try (Stream<Path> indexStream = Files.list(path.resolve("index"))) {
            indexStream.map(file -> {
                try {
                    Index index = readFile(path.relativize(file), Index.class);
                    LOGGER.fine(" index " + file.getFileName().toString() + " loaded");
                    return index;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).forEach(indexes::add);
        }

        LOGGER.info("Searching for sha256 " + sha256);
        Index.Pack foundPack = null;
        Index.Pack.Blob foundBlob = null;
        for (Index index : indexes) {
            for (Index.Pack pack : index.packs()) {
                for (Index.Pack.Blob blob : pack.blobs()) {
                    if (blob.id().equals(sha256)) {
                        foundPack = pack;
                        foundBlob = blob;
                        LOGGER.info("Found blob in pack " + foundPack.id());
                        LOGGER.info("Blob: " + foundBlob);
                        break;
                    }
                }
                if (foundPack != null) break;
            }
            if (foundPack != null) break;
        }

        if (foundPack == null) {
            throw new IllegalStateException("Didn't find sha256 in index");
        }

        return new FoundBlob(foundPack, foundBlob);
    }

    public byte[] readContent(String sha256) throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        FoundBlob foundBlob = findBlob(sha256);

        Path packFile = path.resolve("data").resolve(foundBlob.pack().id().substring(0, 2)).resolve(foundBlob.pack().id());
        try (RandomAccessFile raf = new RandomAccessFile(packFile.toFile(), "r")) {
            raf.seek(foundBlob.blob().offset());
            byte[] encryptedBlob = new byte[foundBlob.blob().length()];
            int readBytes = raf.read(encryptedBlob);
            if (readBytes != foundBlob.blob().length()) {
                throw new IllegalStateException("Couldn't enough data");
            }

            return decryptBytes(encryptedBlob, foundBlob.blob().uncompressed_length() != 0);
        }
    }
}
