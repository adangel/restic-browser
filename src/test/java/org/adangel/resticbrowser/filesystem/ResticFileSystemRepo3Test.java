package org.adangel.resticbrowser.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResticFileSystemRepo3Test {
    private static ResticFileSystem fileSystem;
    private static Path snapshotBasePath;

    @BeforeAll
    static void createFileSystem() throws IOException {
        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        fileSystem = provider.newFileSystem(Path.of("src/test/resources/repos/repo3"), Map.of("RESTIC_PASSWORD", "test"));
        snapshotBasePath = fileSystem.getPath("/snapshots/f9bd1daf0327779444c5dea5228dbb638bd7fcadc1fd120b11cebb75146206a6");
    }

    @Test
    void readSymlink() throws IOException {
        Path symlink = snapshotBasePath.resolve("data/symlink.txt");
        BasicFileAttributes attributes = Files.readAttributes(symlink, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        assertTrue(attributes.isSymbolicLink());
        Path symlinkTarget = Files.readSymbolicLink(symlink);
        assertEquals("regular.txt", symlinkTarget.toString());

        attributes = Files.readAttributes(symlink, BasicFileAttributes.class);
        assertFalse(attributes.isSymbolicLink());

        assertEquals("regular.txt\n", Files.readString(symlink));
        assertEquals("regular.txt\n", Files.readString(symlink.getParent().resolve(symlinkTarget)));
    }

    @Test
    void readSymlink2() throws IOException {
        Path symlink = snapshotBasePath.resolve("data/symlink2.txt");
        BasicFileAttributes attributes = Files.readAttributes(symlink, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        assertTrue(attributes.isSymbolicLink());
        Path symlinkTarget = Files.readSymbolicLink(symlink);
        assertEquals("dir/file.txt", symlinkTarget.toString());

        attributes = Files.readAttributes(symlink, BasicFileAttributes.class);
        assertFalse(attributes.isSymbolicLink());

        assertEquals("dir/file.txt\n", Files.readString(symlink));
        assertEquals("dir/file.txt\n", Files.readString(symlink.getParent().resolve(symlinkTarget)));
    }
}
