package org.adangel.resticbrowser.filesystem;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResticFileSystemRepo2Test {
    private static ResticFileSystem fileSystem;

    @BeforeAll
    static void createFileSystem() throws IOException {
        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        fileSystem = provider.newFileSystem(Path.of("src/test/resources/repos/repo2"), Map.of("RESTIC_PASSWORD", "test"));
    }

    @Test
    void walkTree() throws IOException {
        List<String> files = new ArrayList<>();
        Files.walkFileTree(fileSystem.getPath("/"), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                files.add(dir.toString());
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files.add(file.toString());
                return super.visitFile(file, attrs);
            }
        });

        List<String> expectedFiles = Files.readAllLines(Path.of("src/test/resources/repos/repo2.filelist"));
        assertLinesMatch(expectedFiles, files);
    }
}
