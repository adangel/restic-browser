package org.adangel.resticbrowser.filesystem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.adangel.resticbrowser.Repository;
import org.adangel.resticbrowser.models.SnapshotWithId;
import org.adangel.resticbrowser.models.Tree;

class ResticFileSystem extends FileSystem {
    private static final Logger LOGGER = Logger.getLogger(ResticFileSystem.class.getName());
    private final ResticFileSystemProvider provider;
    private final Repository repository;
    private final ResticPath rootPath;

    ResticFileSystem(ResticFileSystemProvider provider, Path resticPath, Map<String, ?> env) throws IOException {
        this.provider = provider;
        this.repository = new Repository(resticPath, env.get("RESTIC_PASSWORD").toString());
        this.rootPath = new ResticPath(this, "/");
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return List.of(rootPath);
    }

    ResticPath getRootPath() {
        return rootPath;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path getPath(String first, String... more) {
        if (first.equals("/") && more.length == 0) {
            return rootPath;
        }
        String fullPath = first;
        if (more.length > 0) {
            fullPath = fullPath + "/" + String.join("/", more);
        }
        return new ResticPath(this, fullPath);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException();
    }

    URI getUri(ResticPath path) {
        try {
            return new URI(provider.getScheme(), null, path.toString(), "repoPath=" + repository.getPath().toString(), null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    DirectoryStream<Path> list(String path) throws IOException {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException();
        }

        String[] segments = path.substring(1).split("/");

        List<Path> paths = new ArrayList<>();
        if (path.equals("/")) {
            paths.add(new ResticPath(this, "/snapshots"));
            paths.add(new ResticPath(this, "/hosts"));
        } else if (segments[0].equals("snapshots")) {
            if (segments.length == 1) {
                repository.listSnapshots().forEach(snapshotWithId -> {
                    paths.add(new ResticPath(this, path + "/" + snapshotWithId.id()));
                });
            } else {
                SnapshotWithId snapshotById = findSnapshotById(segments[1]);
                String[] subPath = new String[segments.length - 2];
                System.arraycopy(segments, 2, subPath, 0, subPath.length);
                try {
                    String subPathJoined = String.join("/", subPath);
                    List<Tree.Node> files = repository.listFiles(snapshotById.id(), "/" + subPathJoined);
                    if (subPath.length > 0) {
                        files.forEach(file -> paths.add(getPath("snapshots", snapshotById.id(), subPathJoined, file.name()).toAbsolutePath()));
                    } else {
                        files.forEach(file -> paths.add(getPath("snapshots", snapshotById.id(), file.name()).toAbsolutePath()));
                    }
                } catch (InvalidAlgorithmParameterException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchPaddingException e) {
                    throw new RuntimeException(e);
                } catch (IllegalBlockSizeException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                } catch (BadPaddingException e) {
                    throw new RuntimeException(e);
                } catch (InvalidKeyException e) {
                    throw new RuntimeException(e);
                }
            }
        } else if (segments[0].equals("hosts")) {
            if (segments.length == 1) {
                repository.listSnapshots().stream().map(snapshotWithId -> snapshotWithId.snapshot().hostname())
                        .distinct()
                        .forEach(h -> paths.add(getPath("hosts", h).toAbsolutePath()));
            } else if (segments.length == 2) {
                repository.listSnapshots().stream().filter(s -> s.snapshot().hostname().equals(segments[1]))
                        .map(s -> s.snapshot().time().toString())
                        .forEach(tss -> paths.add(getPath("hosts", segments[1], tss).toAbsolutePath()));
            } else {
                SnapshotWithId snapshotWithId = findSnapshotByHostAndTime(segments[1], segments[2]);
                String[] subPath = new String[segments.length - 3];
                System.arraycopy(segments, 3, subPath, 0, subPath.length);
                try {
                    String subPathJoined = String.join("/", subPath);
                    List<Tree.Node> files = repository.listFiles(snapshotWithId.id(), "/" + subPathJoined);
                    if (subPath.length > 0) {
                        files.forEach(file -> paths.add(getPath("hosts", segments[1], segments[2], subPathJoined, file.name()).toAbsolutePath()));
                    } else {
                        files.forEach(file -> paths.add(getPath("hosts", segments[1], segments[2], file.name()).toAbsolutePath()));
                    }
                } catch (InvalidAlgorithmParameterException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchPaddingException e) {
                    throw new RuntimeException(e);
                } catch (IllegalBlockSizeException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                } catch (BadPaddingException e) {
                    throw new RuntimeException(e);
                } catch (InvalidKeyException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return new DirectoryStream<Path>() {
            @Override
            public Iterator<Path> iterator() {
                return paths.iterator();
            }

            @Override
            public void close() throws IOException {

            }
        };
    }

    private static boolean shouldFollowSymLinks(LinkOption... options) {
        boolean follow = true;
        for (LinkOption option : options) {
            if (option == LinkOption.NOFOLLOW_LINKS) {
                follow = false;
            }
        }
        return follow;
    }

    <A extends BasicFileAttributes> A readAttributes(String path, LinkOption... options) throws IOException {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must be absolute: " + path);
        }

        SnapshotWithId snapshotWithId = null;
        String[] segments = path.substring(1).split("/");

        if (path.equals("/")) {
            return ResticFileAttributes.forRoot(repository.getPath());
        }

        int firstIndex = -1;
        if (segments[0].equals("snapshots")) {
            if (segments.length == 1) {
                return ResticFileAttributes.forRoot(repository.getPath());
            } else {
                snapshotWithId = findSnapshotById(segments[1]);
                if (segments.length == 2) {
                    return ResticFileAttributes.forSnapshot(snapshotWithId);
                }
                firstIndex = 2;
            }
        } else if (segments[0].equals("hosts")) {
            if (segments.length == 1) {
                return ResticFileAttributes.forRoot(repository.getPath());
            } else if (segments.length == 2) {
                return ResticFileAttributes.forRoot(repository.getPath());
            } else {
                snapshotWithId = findSnapshotByHostAndTime(segments[1], segments[2]);
                if (segments.length == 3) {
                    return ResticFileAttributes.forSnapshot(snapshotWithId);
                }
                firstIndex = 3;
            }
        }

        if (firstIndex == -1) {
            throw new IllegalStateException();
        }

        String[] parentSubPath = new String[segments.length - firstIndex - 1];
        System.arraycopy(segments, firstIndex, parentSubPath, 0, parentSubPath.length);
        String parentSubPathJoined = String.join("/", parentSubPath);
        try {
            List<Tree.Node> parentFiles = repository.listFiles(snapshotWithId.id(), "/" + parentSubPathJoined);
            Tree.Node childNode = parentFiles.stream().filter(node -> node.name().equals(segments[segments.length - 1])).findFirst().get();
            if (childNode.type() == Tree.NodeType.SYMLINK && shouldFollowSymLinks(options)) {
                String[] pathToSnapshot = new String[firstIndex];
                System.arraycopy(segments, 0, pathToSnapshot, 0, pathToSnapshot.length);
                String pathToSnapshotJoined = String.join("/", pathToSnapshot);

                String linktarget = childNode.linktarget();
                if (linktarget.charAt(0) == '/') {
                    return readAttributes("/" + pathToSnapshotJoined + linktarget);
                }
                return readAttributes("/" + pathToSnapshotJoined + "/" + parentSubPathJoined + "/" + linktarget);
            } else {
                return ResticFileAttributes.fromNode(childNode);
            }
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private Tree.Node findNodeInTree(Tree tree, String name) {
        return tree.nodes().stream().filter(node -> node.name().equals(name)).findFirst().get();
    }

    private SnapshotWithId findSnapshotByHostAndTime(String hostname, String timestamp) {
        try {
            return repository.listSnapshots().stream().filter(s -> s.snapshot().hostname().equals(hostname) && s.snapshot().time().toString().equals(timestamp)).findFirst().get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SnapshotWithId findSnapshotById(String snapshotId) {
        try {
            return repository.listSnapshots().stream().filter(s -> s.id().equals(snapshotId)).findFirst().get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SeekableByteChannel createFromNode(Tree.Node node) throws IOException {
        return new SeekableByteChannel() {
            private InputStream in = repository.readNode(node);

            private ByteBuffer currentByteBuffer = null;
            private long position = 0L;
            private long size = node.size();
            @Override
            public int read(ByteBuffer dst) throws IOException {
                int count = 0;
                while (dst.hasRemaining()) {
                    int read = in.read();
                    if (read == -1) {
                        if (count == 0) {
                            return -1;
                        } else {
                            return count;
                        }
                    }
                    count++;
                    position++;
                    dst.put((byte) read);
                }
                return count;
            }

            @Override
            public int write(ByteBuffer src) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public long position() throws IOException {
                return position;
            }

            @Override
            public SeekableByteChannel position(long newPosition) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public long size() throws IOException {
                return size;
            }

            @Override
            public SeekableByteChannel truncate(long size) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() throws IOException {
                in.close();
            }
        };
    }

    SeekableByteChannel newByteChannel(String path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException();
        }
        String[] segments = path.substring(1).split("/");
        SnapshotWithId snapshotById = null;
        int firstIndex = -1;
        if (segments[0].equals("snapshots")) {
            snapshotById = findSnapshotById(segments[1]);
            firstIndex = 2;
        } else if (segments[0].equals("hosts")) {
            snapshotById = findSnapshotByHostAndTime(segments[1], segments[2]);
            firstIndex = 3;
        } else {
            throw new IllegalArgumentException("Unknown path: " + path);
        }
        String[] subPath = new String[segments.length - firstIndex];
        System.arraycopy(segments, firstIndex, subPath, 0, subPath.length);
        String subPathJoined = String.join("/", subPath);
        if (subPathJoined.isEmpty()) {
            throw new IllegalStateException(path + " is not a file");
        }
        String[] parentSubPath = new String[subPath.length - 1];
        System.arraycopy(subPath, 0, parentSubPath, 0, parentSubPath.length);
        String parentSubPathJoined = String.join("/", parentSubPath);
        try {
            List<Tree.Node> files = repository.listFiles(snapshotById.id(), "/" + parentSubPathJoined);
            Tree.Node node = files.stream().filter(n -> n.name().equals(subPath[subPath.length - 1])).findFirst().get();
            if (node.type() == Tree.NodeType.SYMLINK) {
                String[] pathToSnapshot = new String[firstIndex];
                System.arraycopy(segments, 0, pathToSnapshot, 0, pathToSnapshot.length);
                String pathToSnapshotJoined = String.join("/", pathToSnapshot);

                String linktarget = node.linktarget();
                if (linktarget.charAt(0) == '/') {
                    return newByteChannel("/" + pathToSnapshotJoined + linktarget, options, attrs);
                }
                return newByteChannel("/" + pathToSnapshotJoined + "/" + parentSubPathJoined + "/" + linktarget, options, attrs);
            }
            return createFromNode(node);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    Path readSymbolicLink(String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException();
        }
        String[] segments = path.substring(1).split("/");
        SnapshotWithId snapshotById = null;
        int firstIndex = -1;
        if (segments[0].equals("snapshots")) {
            snapshotById = findSnapshotById(segments[1]);
            firstIndex = 2;
        } else if (segments[0].equals("hosts")) {
            snapshotById = findSnapshotByHostAndTime(segments[1], segments[2]);
            firstIndex = 3;
        } else {
            throw new IllegalArgumentException("Unknown path: " + path);
        }
        String[] subPath = new String[segments.length - firstIndex];
        System.arraycopy(segments, firstIndex, subPath, 0, subPath.length);
        String subPathJoined = String.join("/", subPath);
        if (subPathJoined.isEmpty()) {
            throw new IllegalStateException(path + " is not a symlink");
        }
        String[] parentSubPath = new String[subPath.length - 1];
        System.arraycopy(subPath, 0, parentSubPath, 0, parentSubPath.length);
        String parentSubPathJoined = String.join("/", parentSubPath);
        try {
            List<Tree.Node> files = repository.listFiles(snapshotById.id(), "/" + parentSubPathJoined);
            Tree.Node node = files.stream().filter(n -> n.name().equals(subPath[subPath.length - 1])).findFirst().get();
            String linktarget = node.linktarget();
            if (linktarget.charAt(0) == '/') {
                String[] pathToSnapshotRoot = new String[firstIndex];
                System.arraycopy(segments, 0, pathToSnapshotRoot, 0, firstIndex);
                return getPath("/", String.join("/", pathToSnapshotRoot), linktarget.substring(1));
            } else {
                return getPath(node.linktarget());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
