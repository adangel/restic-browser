package org.adangel.resticbrowser;

import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;

import org.adangel.resticbrowser.filesystem.ResticFileSystemProvider;

import com.sun.net.httpserver.SimpleFileServer;

public class WebServer {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        System.out.println("Starting server at port " + port + "...");
        var server = SimpleFileServer.createFileServer(new InetSocketAddress(port), getResticRootPath(), SimpleFileServer.OutputLevel.VERBOSE);
        server.start();

        Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
    }

    private static Path getRootPath() {
        return Path.of(".").toAbsolutePath();
    }

    private static Path getResticRootPath() throws IOException {
        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        FileSystem fileSystem = provider.newFileSystem(Path.of("src/test/resources/repos/repo1"), Map.of("RESTIC_PASSWORD", "test"));
        return fileSystem.getPath("/");
    }
}
