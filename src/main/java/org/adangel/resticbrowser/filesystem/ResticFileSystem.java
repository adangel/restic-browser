package org.adangel.resticbrowser.filesystem;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
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

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.adangel.resticbrowser.Repository;
import org.adangel.resticbrowser.models.SnapshotWithId;
import org.adangel.resticbrowser.models.Tree;

class ResticFileSystem extends FileSystem {
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
            } else if (segments.length == 2) {
                SnapshotWithId snapshotById = findSnapshotById(segments[1]);
                try {
                    Tree tree = repository.readTree(snapshotById.snapshot().tree());
                    tree.nodes().forEach(node -> paths.add(getPath("snapshots", snapshotById.id(), node.name()).toAbsolutePath()));
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
            } else {
                // TODO
            }
        } else if (segments[0].equals("hosts")) {
            if (segments.length == 1) {
                repository.listSnapshots().stream().map(snapshotWithId -> snapshotWithId.snapshot().hostname())
                        .forEach(h -> paths.add(getPath("hosts", h).toAbsolutePath()));
            } else if (segments.length == 2) {
                repository.listSnapshots().stream().filter(s -> s.snapshot().hostname().equals(segments[1]))
                        .map(s -> s.snapshot().time().toString())
                        .forEach(tss -> paths.add(getPath("hosts", segments[1], tss).toAbsolutePath()));
            } else if (segments.length == 3) {
                SnapshotWithId snapshotByHostAndTime = findSnapshotByHostAndTime(segments[1], segments[2]);
                try {
                    Tree tree = repository.readTree(snapshotByHostAndTime.snapshot().tree());
                    tree.nodes().forEach(n -> paths.add(getPath("hosts", segments[1], segments[2], n.name()).toAbsolutePath()));
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
            } else {
                // TODO
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

    <A extends BasicFileAttributes> A readAttributes(String path) throws IOException {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException();
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
        if (snapshotWithId == null) {
            throw new IllegalStateException();
        }

        try {
            Tree tree = repository.readTree(snapshotWithId.snapshot().tree());
            Tree.Node node = findNodeInTree(tree, segments[firstIndex]);
            return ResticFileAttributes.fromNode(node);
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

    private SeekableByteChannel createFromNode(Tree.Node node) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(repository.readContent(node.content().getFirst()));
            return new SeekableByteChannel() {
                @Override
                public int read(ByteBuffer dst) throws IOException {
                    int count = 0;
                    while (dst.hasRemaining() && buffer.hasRemaining()) {
                        dst.put(buffer.get());
                        count++;
                    }
                    return count;
                }

                @Override
                public int write(ByteBuffer src) throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public long position() throws IOException {
                    return buffer.position();
                }

                @Override
                public SeekableByteChannel position(long newPosition) throws IOException {
                    buffer.position((int) newPosition);
                    return this;
                }

                @Override
                public long size() throws IOException {
                    return buffer.limit();
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

                }
            };
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
        }
        try {
            Tree tree = repository.readTree(snapshotById.snapshot().tree());
            Tree.Node node = findNodeInTree(tree, segments[firstIndex]);
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
}
