package org.adangel.resticbrowser.filesystem;

import static org.adangel.resticbrowser.filesystem.ResticTestUtils.openTestRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ResticFileSystemTest {
    @Test
    void testWalkTree() throws Exception {
        List<String> entries = new ArrayList<>();
        try (FileSystem fileSystem = openTestRepo()) {
            Path root = fileSystem.getPath("/");

            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    entries.add(dir.toString());
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    entries.add(file.toString() + " ( size = " + attrs.size() + ")");
                    return super.visitFile(file, attrs);
                }
            });
        }
        assertEquals(List.of("/",
                "/snapshots",
                "/snapshots/cc5dc9a1e093d1670edd2bd2385b1313f867d98a2e175285ecc1c303c3b0525d",
                "/snapshots/cc5dc9a1e093d1670edd2bd2385b1313f867d98a2e175285ecc1c303c3b0525d/test.txt ( size = 5)",
                "/hosts",
                "/hosts/adangel",
                "/hosts/adangel/2023-10-29T10:55:03.245143527Z",
                "/hosts/adangel/2023-10-29T10:55:03.245143527Z/test.txt ( size = 5)"),
                entries);
    }
}
