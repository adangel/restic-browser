package org.adangel.resticbrowser;

import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Map;

import org.adangel.resticbrowser.filesystem.ResticFileSystemProvider;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

public class WebServer {
    private final int port;
    private final HttpServer server;
    public WebServer(int port, Path resticRepoPath, char[] password) throws IOException {
        System.out.println("Starting server at port " + port + "...");

        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        FileSystem fileSystem = provider.newFileSystem(resticRepoPath, Map.of("RESTIC_PASSWORD", new String(password)));
        Path resticRootPath = fileSystem.getPath("/");

        this.port = port;
        server = SimpleFileServer.createFileServer(new InetSocketAddress(port), resticRootPath, SimpleFileServer.OutputLevel.INFO);
    }

    public void start() throws IOException {
        server.start();
        Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
    }

    public void stop() {
        server.stop(1);
    }

    public static void main(String[] args) throws IOException {
        WebServer webServer = new WebServer(8080, Path.of("src/test/resources/repos/repo1"), "test".toCharArray());
        webServer.start();
    }
}
