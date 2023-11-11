package org.adangel.resticbrowser;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
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
        loadIndexFiles();
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

    private record IndexEntry(String packId, String type, int offset, int length, int uncompressed_length) {}
    private Map<String, IndexEntry> indexCache;
    private void loadIndexFiles() throws IOException {
        LOGGER.info("Loading indexes...");
        Map<String, Map<String, IndexEntry>> temporaryIndex = new HashMap<>();
        Set<String> supersedes = new HashSet<>();

        List<Path> indexFiles;
        try (Stream<Path> indexStream = Files.list(path.resolve("index"))) {
            indexFiles = indexStream.toList();
        }

        long fileNumber = 0;
        for (Path indexFile : indexFiles) {
            fileNumber++;
            String indexName = indexFile.getFileName().toString();
            Map<String, IndexEntry> currentCache = new HashMap<>();
            try {
                Index index = readFile(path.relativize(indexFile), Index.class);
                LOGGER.fine(String.format(" index %s loaded (%d of %d)", indexName, fileNumber, indexFiles.size()));

                if (index.supersedes() != null && !index.supersedes().isEmpty()) {
                    LOGGER.fine("Found superseded indexes...");
                    supersedes.addAll(index.supersedes());
                }

                for (Index.Pack pack : index.packs()) {
                    for (Index.Pack.Blob blob : pack.blobs()) {
                        IndexEntry previousEntry = currentCache.put(blob.id(), new IndexEntry(pack.id(), blob.type(), blob.offset(), blob.length(), blob.uncompressed_length()));
                        if (previousEntry != null) {
                            LOGGER.warning("replaced previous index entry " + previousEntry + " for blob " + blob.id());
                            throw new IllegalStateException();
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            temporaryIndex.put(indexName, currentCache);
        }

        LOGGER.info("Found %d superseded indexes".formatted(supersedes.size()));
        LOGGER.info("temporary index: size=%d".formatted(temporaryIndex.size()));
        for (String superseded : supersedes) {
            temporaryIndex.remove(superseded);
        }
        LOGGER.info("temporary index: size=%d (after removed old indexes)".formatted(temporaryIndex.size()));
        indexCache = new HashMap<>();
        for (Map<String, IndexEntry> entry : temporaryIndex.values()) {
            indexCache.putAll(entry);
        }

        LOGGER.info("IndexCache contains %d blobs".formatted(indexCache.size()));
    }

    private IndexEntry findBlob(String sha256) {
        if (!indexCache.containsKey(sha256)) {
            throw new IllegalStateException("Blob with id " + sha256 + " not found in index");
        }
        IndexEntry indexEntry = indexCache.get(sha256);
        LOGGER.fine("Found blob: " + indexEntry);
        return indexEntry;
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

    private Map<String, Tree> treeCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Tree> eldest) {
            return size() > 50;
        }
    };
    public Tree readTree(String tree) throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        if (treeCache.containsKey(tree)) {
            return treeCache.get(tree);
        }
        LOGGER.fine("Loading tree " + tree);
        Tree loadedTree = MAPPER.readValue(readContentAsStream(tree), Tree.class);
        treeCache.put(tree, loadedTree);
        return loadedTree;
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

    public byte[] readContent(String sha256) throws IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        LOGGER.fine("Reading content of blob " + sha256);
        IndexEntry indexEntry = findBlob(sha256);

        Path packFile = path.resolve("data").resolve(indexEntry.packId().substring(0, 2)).resolve(indexEntry.packId());
        try (RandomAccessFile raf = new RandomAccessFile(packFile.toFile(), "r")) {
            raf.seek(indexEntry.offset());
            byte[] encryptedBlob = new byte[indexEntry.length()];
            int readBytes = raf.read(encryptedBlob);
            if (readBytes != indexEntry.length()) {
                throw new IllegalStateException("Couldn't enough data");
            }

            return decryptBytes(encryptedBlob, indexEntry.uncompressed_length() != 0);
        }
    }

    public InputStream readContentAsStream(String sha256) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        LOGGER.fine("Reading content of blob " + sha256);
        IndexEntry indexEntry = findBlob(sha256);

        Path packFile = path.resolve("data").resolve(indexEntry.packId().substring(0, 2)).resolve(indexEntry.packId());

        InputStream encryptedStream = new InputStream() {
            private RandomAccessFile raf;
            private long bytesRead = 0;
            private final long length = indexEntry.length();

            {
                raf = new RandomAccessFile(packFile.toFile(), "r");
                raf.seek(indexEntry.offset());
            }

            @Override
            public int read() throws IOException {
                if (bytesRead >= length) {
                    return -1;
                }
                bytesRead++;
                return raf.read();
            }

            @Override
            public void close() throws IOException {
                raf.close();
            }
        };

        InputStream decryptedStream = new FilterInputStream(encryptedStream) {
            private Poly1305 mac;
            private Cipher cipher;
            private ByteBuffer encryptedBuffer = ByteBuffer.allocate(512);
            private ByteBuffer decryptedBuffer;
            private long bytesRead = 0;
            private final long encryptedLength = indexEntry.length() - /* MAC */ 16;

            {
                byte[] ivData = new byte[16];
                in.read(ivData);
                IvParameterSpec iv = new IvParameterSpec(ivData);
                cipher = Cipher.getInstance("AES/CTR/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, masterKeySpec, iv);

                decryptedBuffer = ByteBuffer.allocate(cipher.getOutputSize(encryptedBuffer.limit()));
                decryptedBuffer.position(decryptedBuffer.limit()); // no data in the buffer yet

                mac = new Poly1305(AESEngine.newInstance());
                mac.init(new ParametersWithIV(macParams, iv.getIV()));
            }


            @Override
            public int read() throws IOException {
                if (decryptedBuffer.hasRemaining()) {
                    return (int) decryptedBuffer.get() & 0xff;
                }

                if (bytesRead + 16 == encryptedLength) {
                    byte[] originalMac = new byte[16];
                    in.read(originalMac);

                    byte[] calculatedMac = new byte[mac.getMacSize()];
                    mac.doFinal(calculatedMac, 0);

                    if (!Arrays.equals(originalMac, calculatedMac)) {
                        LOGGER.severe("MAC doesn't match");
                        throw new RuntimeException("MAC Doesn't match");
                    }

                    return -1;
                }

                // read up to all, but not the last 16 bytes - the MAC
                int toRead = (int) Math.min(encryptedLength - 16 - bytesRead, (long) encryptedBuffer.capacity());

                int read = in.read(encryptedBuffer.array(), 0, toRead);
                bytesRead += read;
                encryptedBuffer.rewind();
                encryptedBuffer.limit(read);

                mac.update(encryptedBuffer.array(), 0, read);

                decryptedBuffer.rewind();
                try {
                    int out = cipher.update(encryptedBuffer, decryptedBuffer);
                    decryptedBuffer.rewind();
                    decryptedBuffer.limit(out);
                } catch (ShortBufferException e) {
                    throw new RuntimeException(e);
                }

                if (decryptedBuffer.hasRemaining()) {
                    return decryptedBuffer.get() & 0xff;
                }

                throw new IllegalStateException("no data decrypted??");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (decryptedBuffer.hasRemaining()) {
                    int available = Math.min(len, decryptedBuffer.remaining());
                    decryptedBuffer.get(b, off, available);
                    return available;
                }

                for (int i = 0; i < len; i++) {
                    int c = read();
                    if (c == -1) {
                        if (i == 0) {
                            return -1;
                        } else {
                            return i;
                        }
                    }
                    b[off + i] = (byte) c;
                }
                return len;
            }
        };

        boolean isCompressed = indexEntry.uncompressed_length() != 0;
        if (isCompressed) {
            InputStream decompressedStream = new FilterInputStream(new ZstdCompressorInputStream(decryptedStream)) {
                private long bytesRead = 0;
                private final long length = indexEntry.uncompressed_length();

                @Override
                public int read() throws IOException {
                    if (bytesRead >= length) {
                        return -1;
                    }
                    bytesRead++;
                    return super.read();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (bytesRead >= length) {
                        return -1;
                    }
                    if (len == 0) {
                        return 0;
                    }
                    if (length - bytesRead > len) {
                        int read = super.read(b, off, len);
                        bytesRead += read;
                        return read;
                    }
                    int available = (int) (length - bytesRead);
                    int read = super.read(b, off, available);
                    bytesRead += read;
                    return read;
                }
            };
            return decompressedStream;
        }

        return decryptedStream;
    }
}
