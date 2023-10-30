package org.adangel.resticbrowser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

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
        repository.readFile(Path.of("config"));
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
}
