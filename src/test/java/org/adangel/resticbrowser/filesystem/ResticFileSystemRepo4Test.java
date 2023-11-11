package org.adangel.resticbrowser.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResticFileSystemRepo4Test {
    private static ResticFileSystem fileSystem;
    private static Path snapshotBasePath;

    @BeforeAll
    static void createFileSystem() throws IOException {
        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        fileSystem = provider.newFileSystem(Path.of("src/test/resources/repos/repo4"), Map.of("RESTIC_PASSWORD", "test"));
        snapshotBasePath = fileSystem.getPath("/snapshots/a9e4ac2f30d86cf98d52648d00e9fe03313e2cc47e5533e7f1e5c35e2e52d364");
    }

    @Test
    void readFile() throws Exception {
        // "file.raw" has two content chunks (blobs)
        Path path = snapshotBasePath.resolve("file.raw");
        MessageDigest sha256 = MessageDigest.getInstance("SHA256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                sha256.update(buffer, 0, read);
            }
        }
        byte[] digest = sha256.digest();
        String digestString = HexFormat.of().formatHex(digest);
        assertEquals("4360fffcd35689d5ae2ca95b28b289bebf15905d3db33f4e918ae814f9140841", digestString);
    }
}
