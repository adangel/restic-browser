package org.adangel.resticbrowser.fuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.utils.MountUtils;

@EnabledIf("fuseIsAvailable")
class ResticFSTest {
    @TempDir
    private Path tempdir;

    @BeforeAll
    static void initLogging() throws IOException {
        LogManager.getLogManager().readConfiguration(ResticFSTest.class.getResourceAsStream("/logging.properties"));
    }

    static boolean fuseIsAvailable() {
        try {
            new FuseStubFS() {};
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    @Test
    void walkRepository() throws IOException {
        ResticFS fs = new ResticFS(Path.of("src/test/resources/repos/repo4"), "test");
        fs.mount(tempdir, false);

        try {
            List<String> files = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(tempdir)) {
                stream.map(path -> tempdir.relativize(path))
                        .map(Path::toString)
                        .filter(path -> !path.isEmpty())
                        .forEach(files::add);
            }
            List<String> expectedFiles = Files.readAllLines(Path.of("src/test/resources/repos/repo4.filelist"));
            assertLinesMatch(expectedFiles, files);
        } finally {
            umount(tempdir);
        }
    }

    @Test
    void readFileAsStream() throws IOException, NoSuchAlgorithmException {
        ResticFS fs = new ResticFS(Path.of("src/test/resources/repos/repo4"), "test");
        fs.mount(tempdir, false);

        try {
            Path fileRaw = tempdir.resolve("snapshots/a9e4ac2f30d86cf98d52648d00e9fe03313e2cc47e5533e7f1e5c35e2e52d364/file.raw");
            MessageDigest sha256 = MessageDigest.getInstance("SHA256");
            byte[] buffer = new byte[8192];
            try (InputStream in = Files.newInputStream(fileRaw)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    sha256.update(buffer, 0, read);
                }
            }
            byte[] digest = sha256.digest();
            String digestString = HexFormat.of().formatHex(digest);
            assertEquals("4360fffcd35689d5ae2ca95b28b289bebf15905d3db33f4e918ae814f9140841", digestString);
        } finally {
            umount(tempdir);
        }
    }

    @Test
    void readFileAsChannel() throws IOException, NoSuchAlgorithmException {
        ResticFS fs = new ResticFS(Path.of("src/test/resources/repos/repo4"), "test");
        fs.mount(tempdir, false);

        try {
            Path fileRaw = tempdir.resolve("snapshots/a9e4ac2f30d86cf98d52648d00e9fe03313e2cc47e5533e7f1e5c35e2e52d364/file.raw");
            MessageDigest sha256 = MessageDigest.getInstance("SHA256");
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            try (ByteChannel channel = Files.newByteChannel(fileRaw)) {
                while (channel.read(buffer) != -1) {
                    sha256.update(buffer.flip());
                    buffer.clear();
                }
            }
            byte[] digest = sha256.digest();
            String digestString = HexFormat.of().formatHex(digest);
            assertEquals("4360fffcd35689d5ae2ca95b28b289bebf15905d3db33f4e918ae814f9140841", digestString);
        } finally {
            umount(tempdir);
        }
    }

    /**
     * Reads the file externally with {@code sha256sum}, which will access the file via FUSE with a different
     * multi-threaded pattern.
     */
    @EnabledOnOs(OS.LINUX)
    @Test
    void readFileExternally() throws IOException, InterruptedException {
        ResticFS fs = new ResticFS(Path.of("src/test/resources/repos/repo4"), "test");
        fs.mount(tempdir, false);

        try {
            Path fileRaw = tempdir.resolve("snapshots/a9e4ac2f30d86cf98d52648d00e9fe03313e2cc47e5533e7f1e5c35e2e52d364/file.raw");
            ProcessBuilder processBuilder = new ProcessBuilder("sha256sum", fileRaw.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            StringWriter writer = new StringWriter();
            try (InputStreamReader reader = new InputStreamReader(process.getInputStream())) {
                reader.transferTo(writer);
            }
            int errorCode = process.waitFor();
            assertEquals(0, errorCode);
            assertEquals("4360fffcd35689d5ae2ca95b28b289bebf15905d3db33f4e918ae814f9140841  " + fileRaw + System.lineSeparator(),
                    writer.toString());
        } finally {
            umount(tempdir);
        }
    }

    private static void umount(Path mountPath) {
        try {
            // Note: MountUtils doesn't wait for fusermount to be finished...
            new ProcessBuilder("fusermount", "-u", "-z", mountPath.toAbsolutePath().toString()).start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (IOException e) {
            MountUtils.umount(mountPath);
        }
    }
}
