package org.adangel.resticbrowser.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Set;

class ResticPath implements Path {
    private final ResticFileSystem fileSystem;
    private final String[] pathComponents;

    ResticPath(ResticFileSystem fileSystem, String path) {
        this.fileSystem = fileSystem;
        this.pathComponents = path.split("/", -1);
    }

    @Override
    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return pathComponents.length > 0 && pathComponents[0].isEmpty();
    }

    @Override
    public Path getRoot() {
        if (this.isAbsolute()) {
            return fileSystem.getRootPath();
        } else {
            return null;
        }
    }

    @Override
    public Path getFileName() {
        if (pathComponents.length == 0) {
            return null;
        }
        String filename = pathComponents[pathComponents.length - 1];
        if (filename.isEmpty()) {
            return null;
        }
        return new ResticPath(fileSystem, filename);
    }

    @Override
    public Path getParent() {
        if (pathComponents.length < 2) {
            return null;
        }
        String[] parentComponents = new String[pathComponents.length - 1];
        System.arraycopy(pathComponents, 0, parentComponents, 0, parentComponents.length);
        String parentPath = String.join("/", parentComponents);
        if (parentPath.isEmpty()) {
            return null;
        }
        return new ResticPath(fileSystem, parentPath);
    }

    @Override
    public int getNameCount() {
        int count = pathComponents.length;
        if (this.isAbsolute()) {
            count--;
        }
        if (this.getFileName() == null) {
            count--;
        }
        return count;
    }

    @Override
    public Path getName(int index) {
        int count = getNameCount();
        if (index >= count) {
            throw new IllegalArgumentException();
        }
        int actualIndex = index;
        if (isAbsolute()) {
            actualIndex++;
        }
        return new ResticPath(fileSystem, pathComponents[actualIndex]);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        String[] subpath = new String[endIndex - beginIndex];
        int actualStart = isAbsolute() ? beginIndex + 1 : beginIndex;
        System.arraycopy(pathComponents, actualStart, subpath, 0, endIndex - beginIndex);
        return new ResticPath(fileSystem, String.join("/", subpath));
    }

    @Override
    public boolean startsWith(Path other) {
        return toString().startsWith(other.toString());
    }

    @Override
    public boolean endsWith(Path other) {
        return toString().endsWith(other.toString());
    }

    @Override
    public Path normalize() {
        return this;
    }

    @Override
    public Path resolve(Path other) {
        if (other.toString().isEmpty()) {
            return this;
        }
        if (other.isAbsolute()) {
            return other;
        }
        if (this.toString().endsWith("/")) {
            return new ResticPath(fileSystem, toString() + other.toString());
        }
        return new ResticPath(fileSystem, toString() + "/" + other.toString());
    }

    @Override
    public Path relativize(Path other) {
        if (this.isAbsolute() && !other.isAbsolute() || !this.isAbsolute() && other.isAbsolute()) {
            throw new IllegalArgumentException();
        }
        String thisPath = toString();
        String otherPath = other.toString();
        if (thisPath.equals(otherPath)) {
            return new ResticPath(fileSystem, "");
        }
        if (otherPath.startsWith(thisPath)) {
            String relative = otherPath.substring(thisPath.length());
            if (relative.charAt(0) == '/') {
                relative = relative.substring(1);
            }
            return new ResticPath(fileSystem, relative);
        } else if (thisPath.startsWith(otherPath)) {
            int diff = this.getNameCount() - other.getNameCount();
            String[] parts = new String[diff];
            for (int i = 0; i < diff; i++) {
                parts[i] = "..";
            }
            return new ResticPath(fileSystem, String.join("/", parts));
        } else {
            String[] parts = new String[this.getNameCount() + other.getNameCount()];
            for (int i = 0; i < this.getNameCount(); i++) {
                parts[i] = "..";
            }
            for (int i = this.getNameCount(), j = 0; i < parts.length; i++, j++) {
                parts[i] = other.getName(j).toString();
            }
            return new ResticPath(fileSystem, String.join("/", parts));
        }
    }

    @Override
    public URI toUri() {
        return fileSystem.getUri(this);
    }

    @Override
    public Path toAbsolutePath() {
        if (this.isAbsolute()) {
            return this;
        }
        return new ResticPath(fileSystem, "/" + toString());
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Path other) {
        return this.toString().compareTo(other.toString());
    }

    DirectoryStream<Path> newDirectoryStream(DirectoryStream.Filter<? super Path> filter) throws IOException {
        return fileSystem.list(this.toAbsolutePath().toString());
    }

    @Override
    public String toString() {
        return String.join("/", pathComponents);
    }

    <A extends BasicFileAttributes> A readAttributes(LinkOption... options) throws IOException {
        return fileSystem.readAttributes(this.toString(), options);
    }

    SeekableByteChannel newByteChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
        return fileSystem.newByteChannel(this.toString(), options, attrs);
    }

    public Path readSymbolicLink() {
        return fileSystem.readSymbolicLink(this.toString());
    }
}
