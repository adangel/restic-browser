package org.adangel.resticbrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.adangel.resticbrowser.models.SnapshotWithId;
import org.adangel.resticbrowser.models.Tree;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RepositoryRepo2Test {
    private static Repository repo;

    @BeforeAll
    static void openRepository() throws Exception {
        repo = new Repository(Path.of("src/test/resources/repos/repo2"), "test");
    }

    @Test
    void checkSnapshots() throws Exception {
        List<SnapshotWithId> snapshotWithIds = repo.listSnapshots();
        assertEquals(1, snapshotWithIds.size());
        SnapshotWithId snapshotWithId = snapshotWithIds.getFirst();
        assertTrue(snapshotWithId.id().startsWith("2a9e949d"));
        assertEquals(List.of("/home/johndoe"), snapshotWithId.snapshot().paths());
        assertEquals("johndoe", snapshotWithId.snapshot().hostname());
    }

    @Test
    void listFiles() throws Exception {
        String snapshotId = repo.listSnapshots().getFirst().id();
        List<String> expectedFiles = Files.readAllLines(Path.of("src/test/resources/repos/repo2-2a9e949d.filelist"));

        List<String> files = repo.listFiles(snapshotId);
        assertLinesMatch(expectedFiles, files);
    }

    @Test
    void listSubPath() throws Exception {
        String snapshotId = repo.listSnapshots().getFirst().id();
        List<String> files = repo.listFiles(snapshotId, "/home/johndoe/subdir1/a").stream().map(Tree.Node::name).toList();
        assertLinesMatch(List.of("file1.txt", "file2.txt"), files);
    }
}
