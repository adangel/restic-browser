package org.adangel.resticbrowser;

import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;

import com.sun.net.httpserver.SimpleFileServer;

public class WebServer {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        System.out.println("Starting server at port " + port + "...");
        var server = SimpleFileServer.createFileServer(new InetSocketAddress(port), Path.of(".").toAbsolutePath(), SimpleFileServer.OutputLevel.INFO);
        server.start();

        Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
    }
}
