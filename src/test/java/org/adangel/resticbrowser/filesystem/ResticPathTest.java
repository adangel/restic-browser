package org.adangel.resticbrowser.filesystem;

import static org.adangel.resticbrowser.filesystem.ResticTestUtils.openTestRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ResticPathTest {

    @Test
    void testAbsolutePath() {
        Path path = new ResticPath(null, "/");
        assertTrue(path.isAbsolute());
        assertNull(path.getFileName());
        assertNull(path.getParent());
        assertEquals(0, path.getNameCount());
        assertThrows(IllegalArgumentException.class, () -> path.getName(0));
        assertEquals("/", path.toString());
    }

    @Test
    void testRelativePath() {
        Path path = new ResticPath(null, "a/b");
        assertFalse(path.isAbsolute());
        assertEquals("b", path.getFileName().toString());
        assertEquals("a", path.getParent().toString());
        assertEquals(2, path.getNameCount());
        assertEquals("a/b", path.toString());
    }

    @Test
    void testFileName() {
        Path path = new ResticPath(null, "/abc.txt");
        Path filename = path.getFileName();
        assertFalse(filename.isAbsolute());
        assertEquals("abc.txt", filename.toString());
        assertNull(filename.getParent());
        assertNull(filename.getRoot());
    }

    @Test
    void testParent() {
        Path path = new ResticPath(null, "/abc/def.txt");
        Path parent = path.getParent();
        assertTrue(parent.isAbsolute());
        assertEquals("/abc", parent.toString());
    }

    @Test
    void testSubPath() {
        Path path = new ResticPath(null, "/abc/def/ghi.txt");
        Path subpath = path.subpath(1, 2);
        assertFalse(subpath.isAbsolute());
        assertEquals("def", subpath.toString());

        subpath = path.subpath(1, 3);
        assertFalse(subpath.isAbsolute());
        assertEquals("def/ghi.txt", subpath.toString());

        subpath = path.subpath(0, 3);
        assertFalse(subpath.isAbsolute());
        assertEquals("abc/def/ghi.txt", subpath.toString());
    }

    @Test
    void testStartsWith() {
        Path path = new ResticPath(null, "/abc");
        Path other = new ResticPath(null, "/abc/def");
        Path other2 = new ResticPath(null, "/xyz/def");
        assertTrue(other.startsWith(path));
        assertFalse(other2.startsWith(path));
    }

    @Test
    void testResolve() {
        Path root = new ResticPath(null, "/");
        Path other = root.resolve(new ResticPath(null, "abc"));
        assertTrue(other.isAbsolute());
        assertEquals("/abc", other.toString());
    }

    @Test
    void testRelativize() {
        Path path1 = new ResticPath(null, "/a/b");
        Path path2 = new ResticPath(null, "/a/b/c/d");
        Path path3 = new ResticPath(null, "/x/y");
        Path path4 = new ResticPath(null, "foo");
        Path path5 = new ResticPath(null, "bar");
        Path path6 = new ResticPath(null, "/a/x");
        Path path7 = new ResticPath(null, "/");
        Path path8 = new ResticPath(null, "/a");
        assertEquals("", path1.relativize(path1).toString());
        assertEquals("", path4.relativize(path4).toString());
        assertEquals("c/d", path1.relativize(path2).toString());
        assertEquals("../..", path2.relativize(path1).toString());
        assertEquals("../../x/y", path1.relativize(path3).toString());
        assertThrows(IllegalArgumentException.class, () -> path1.relativize(path4).toString());
        assertThrows(IllegalArgumentException.class, () -> path4.relativize(path1).toString());
        assertEquals("../bar", path4.relativize(path5).toString());
        //TODO assertEquals("../../../x", path2.relativize(path6).toString());
        assertEquals("a", path7.relativize(path8).toString());
    }

    @Test
    void testToUri() throws Exception {
        try (FileSystem fileSystem = openTestRepo()) {
            Path path = fileSystem.getPath("/snapshots", "cc5dc9a1e093d1670edd2bd2385b1313f867d98a2e175285ecc1c303c3b0525d");
            URI uri = path.toUri();
            assertEquals("restic:/snapshots/cc5dc9a1e093d1670edd2bd2385b1313f867d98a2e175285ecc1c303c3b0525d?repoPath=src/test/resources/repos/repo1", uri.toString());
        }
    }

    @Test
    void listDirectories() throws Exception {
        try (FileSystem fileSystem = openTestRepo()) {
            Path path = fileSystem.getPath("/");
            Files.list(path).forEach(p -> {
                System.out.println("p = " + p);
                try {
                    Files.list(path.resolve(p)).forEach(p2 -> System.out.println("p2 = " + p2));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
