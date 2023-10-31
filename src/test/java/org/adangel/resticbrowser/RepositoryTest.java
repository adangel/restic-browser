package org.adangel.resticbrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.LogManager;

import org.adangel.resticbrowser.models.Snapshot;
import org.adangel.resticbrowser.models.SnapshotWithId;
import org.adangel.resticbrowser.models.Tree;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RepositoryTest {
    @BeforeAll
    static void initLogging() throws IOException {
        LogManager.getLogManager().readConfiguration(RepositoryTest.class.getResourceAsStream("/logging.properties"));
    }
    @Test
    void validPassword() throws Exception {
        Repository repository = new Repository(Path.of("src/test/resources/repos/repo1"), "test");
        assertTrue(repository.hasMasterKey());
        assertEquals("4d935de4a1a6ab1d4ad70f8021f5a36296dccc17ff33f1e955fa0237cd1c6d46", repository.getId());
    }

    @Test
    void listSnapshots() throws Exception {
        Repository repository = new Repository(Path.of("src/test/resources/repos/repo1"), "test");
        List<JSONObject> snapshots = repository.listSnapshots();
        assertEquals(1, snapshots.size());
        assertEquals("2023-10-29T11:55:03.245143527+01:00", snapshots.get(0).getString("time"));
        assertEquals("f21b8d90630e094791e9222661d7e53674467e36afba0a4c12b276f8c22c56ff", snapshots.get(0).getString("tree"));

        JSONObject index = repository.readFile(Path.of("index/cccab80cdb489d6d416e283dd9019d06086c3a82b63fc45714e88dea9073f4a0"));
        System.out.println("index.toString(2) = " + index.toString(2));
    }

    @Test
    void listSnapshots2() throws Exception {
        Repository repository = new Repository(Path.of("src/test/resources/repos/repo1"), "test");
        List<SnapshotWithId> snapshots = repository.listSnapshots2();
        assertEquals(1, snapshots.size());
        assertEquals("cc5dc9a1e093d1670edd2bd2385b1313f867d98a2e175285ecc1c303c3b0525d", snapshots.getFirst().id());
        assertEquals("adangel", snapshots.getFirst().snapshot().hostname());
    }

    @Test
    void validPassword2() throws IOException {
        Repository repository = new Repository(Path.of("src/test/resources/repos/repo1"), "password");
        assertTrue(repository.hasMasterKey());
    }

    @Test
    void invalidPassword() throws IOException {
        Exception exception = assertThrows(Exception.class, () -> new Repository(Path.of("src/test/resources/repos/repo1"), "wrong_password"));
        assertEquals("Wrong password", exception.getMessage());
    }

    @Test
    void readTree() throws Exception {
        Repository repository = new Repository(Path.of("src/test/resources/repos/repo1"), "test");
        Snapshot snapshot = repository.listSnapshots2().getFirst().snapshot();
        Tree tree = repository.readTree(snapshot.tree());
        assertEquals(1, tree.nodes().size());
        assertEquals("test.txt", tree.nodes().getFirst().name());
        assertEquals("c9d04c9565fc665c80681fb1d829938026871f66e14f501e08531df66938a789", tree.nodes().getFirst().content().getFirst());
    }

    @Test
    void readContent() throws Exception {
        Repository repository = new Repository(Path.of("src/test/resources/repos/repo1"), "test");
        byte[] data = repository.readContent("c9d04c9565fc665c80681fb1d829938026871f66e14f501e08531df66938a789");
        assertEquals("Test\n", new String(data, StandardCharsets.UTF_8));
    }
}
