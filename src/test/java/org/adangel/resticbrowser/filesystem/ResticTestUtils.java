package org.adangel.resticbrowser.filesystem;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;

final class ResticTestUtils {
    private ResticTestUtils() {}

    static FileSystem openTestRepo() throws Exception {
        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        return provider.newFileSystem(Path.of("src/test/resources/repos/repo1"), Map.of("RESTIC_PASSWORD", "test"));
    }
}
