package org.adangel.resticbrowser.fuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.LINUX)
class ResticFSTest {
    @TempDir
    private Path tempdir;

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
            fs.umount();
        }
    }

    @Test
    void readFile() throws IOException, NoSuchAlgorithmException {
        ResticFS fs = new ResticFS(Path.of("src/test/resources/repos/repo4"), "test");
        fs.mount(tempdir, false);

        try {
            Path fileRaw = tempdir.resolve("snapshots/a9e4ac2f30d86cf98d52648d00e9fe03313e2cc47e5533e7f1e5c35e2e52d364/file.raw");
            MessageDigest sha256 = MessageDigest.getInstance("SHA256");
            try (InputStream in = Files.newInputStream(fileRaw)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    sha256.update(buffer, 0, read);
                }
            }
            byte[] digest = sha256.digest();
            String digestString = HexFormat.of().formatHex(digest);
            assertEquals("4360fffcd35689d5ae2ca95b28b289bebf15905d3db33f4e918ae814f9140841", digestString);
        } finally {
            fs.umount();
        }
    }
}
