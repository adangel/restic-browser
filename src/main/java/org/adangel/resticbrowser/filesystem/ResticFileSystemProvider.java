package org.adangel.resticbrowser.filesystem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * URI syntax: <pre>restic:{entry}?repoPath={repoPath}</pre>
 */
public class ResticFileSystemProvider extends FileSystemProvider {
    private final Map<Path, ResticFileSystem> filesystems = new HashMap<>();

    @Override
    public String getScheme() {
        return "restic";
    }

    private Path getResticPathFromUri(URI uri) {
        String s = uri.getRawSchemeSpecificPart();
        int sep = s.indexOf('!');
        if (sep != -1) {
            return Path.of(s.substring(0, sep));
        }
        return Path.of(s);
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        Path resticPath = getResticPathFromUri(uri);
        return newFileSystem(resticPath, env);
    }

    @Override
    public FileSystem newFileSystem(Path resticPath, Map<String, ?> env) throws IOException {
        synchronized (filesystems) {
            if (filesystems.containsKey(resticPath)) {
                throw new FileSystemAlreadyExistsException();
            }
            ResticFileSystem fileSystem = new ResticFileSystem(this, resticPath, env);
            filesystems.put(resticPath, fileSystem);
            return fileSystem;
        }
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        Path resticPath = getResticPathFromUri(uri);
        synchronized (filesystems) {
            ResticFileSystem fileSystem = filesystems.get(resticPath);
            if (fileSystem == null) {
                throw new FileSystemNotFoundException();
            }
            return fileSystem;
        }
    }

    @Override
    public Path getPath(URI uri) {
        String entry = uri.getSchemeSpecificPart();
        int sep = entry.indexOf('!');
        if (sep == -1) {
            throw new IllegalArgumentException();
        }
        return getFileSystem(uri).getPath(entry.substring(sep + 1));
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return ((ResticPath) path).newByteChannel(options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return ((ResticPath) dir).newDirectoryStream(filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Path path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return false;
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        if (path.toString().endsWith("index.html") || path.toString().endsWith("index.htm")) {
            throw new FileNotFoundException();
        }
        for (AccessMode mode : modes) {
            switch (mode) {
                case WRITE, EXECUTE:
                    throw new AccessDeniedException(path.toString());
                case READ:
                    continue;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        ResticPath resticPath = (ResticPath) path;
        return resticPath.readAttributes();
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }
}
