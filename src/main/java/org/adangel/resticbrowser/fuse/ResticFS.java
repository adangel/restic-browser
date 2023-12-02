package org.adangel.resticbrowser.fuse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.adangel.resticbrowser.filesystem.ResticFileSystemProvider;

import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

public class ResticFS extends FuseStubFS {
    private static final Logger LOGGER = Logger.getLogger(ResticFS.class.getName());
    private final FileSystem fileSystem;

    private final Map<Long, InputStream> openfiles = new HashMap<>();

    public ResticFS(Path repositoryPath, String password) throws IOException {
        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        this.fileSystem = provider.newFileSystem(repositoryPath, Map.of("RESTIC_PASSWORD", password));
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, long offset, FuseFileInfo fi) {
        filter.apply(buf, ".", null, 0);
        filter.apply(buf, "..", null, 0);

        Path resticPath = fileSystem.getPath(path);
        try (Stream<Path> dirlist = Files.list(resticPath)) {
            dirlist.forEach(p -> {
                filter.apply(buf, resticPath.relativize(p).toString(), null, 0);
            });
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading directory " + path, e);
            return -ErrorCodes.EIO();
        }
        return 0;
    }

    private long calcNanoSecondsPart(FileTime fileTime) {
        long nanosStartSecond = TimeUnit.NANOSECONDS.convert(fileTime.to(TimeUnit.SECONDS), TimeUnit.SECONDS);
        long nanos = fileTime.to(TimeUnit.NANOSECONDS);
        return nanos - nanosStartSecond;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        int res = 0;
        Path resticPath = fileSystem.getPath(path);
        try {
            if (Files.exists(resticPath)) {
                BasicFileAttributes attributes = Files.readAttributes(resticPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                stat.st_ctim.tv_sec.set(attributes.creationTime().to(TimeUnit.SECONDS));
                stat.st_ctim.tv_nsec.set(calcNanoSecondsPart(attributes.creationTime()));
                stat.st_mtim.tv_sec.set(attributes.lastModifiedTime().to(TimeUnit.SECONDS));
                stat.st_mtim.tv_nsec.set(calcNanoSecondsPart(attributes.lastModifiedTime()));
                stat.st_atim.tv_sec.set(attributes.lastAccessTime().to(TimeUnit.SECONDS));
                stat.st_atim.tv_nsec.set(calcNanoSecondsPart(attributes.lastAccessTime()));
                if (attributes.isDirectory()) {
                    stat.st_mode.set(FileStat.S_IFDIR | 0555);
                    stat.st_nlink.set(2);
                } else if (attributes.isRegularFile()) {
                    stat.st_mode.set(FileStat.S_IFREG | 0444);
                    stat.st_nlink.set(1);
                    stat.st_size.set(attributes.size());
                } else if (attributes.isSymbolicLink()) {
                    Path symlinkTarget = Files.readSymbolicLink(resticPath);
                    stat.st_mode.set(FileStat.S_IFLNK | 0444);
                    stat.st_nlink.set(1);
                    stat.st_size.set(symlinkTarget.toString().length());
                } else {
                    res = -ErrorCodes.ENOENT();
                }
            } else {
                res = -ErrorCodes.ENOENT();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading attributes for " + path, e);
            res = -ErrorCodes.EIO();
        }
        return res;
    }

    @Override
    public int readlink(String path, Pointer buf, long size) {
        int res = 0;
        Path resticPath = fileSystem.getPath(path);
        try {
            Path linkTarget = Files.readSymbolicLink(resticPath);
            String target = linkTarget.toString();
            target = target.substring(0, Math.min(target.length(), (int) size));
            buf.putString(0, target, (int) size, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading symlink " + path, e);
            res = -ErrorCodes.EIO();
        }
        return res;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        Path resticPath = fileSystem.getPath(path);
        if (!Files.exists(resticPath)) {
            return -ErrorCodes.ENOENT();
        }

        try {
            long handle = ThreadLocalRandom.current().nextLong();
            InputStream channel = Files.newInputStream(resticPath);
            fi.fh.set(handle);
            openfiles.put(handle, channel);
            LOGGER.log(Level.INFO, "Opened file " + resticPath + " (handle=" + handle + ")");
            return 0;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "While opening file " + resticPath, e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int release(String path, FuseFileInfo fi) {
        LOGGER.log(Level.INFO, "Closing file with handle " + fi.fh.get());
        InputStream channel = openfiles.remove(fi.fh.get());
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "While closing file " + path, e);
                return -ErrorCodes.EIO();
            }
        } else {
            LOGGER.log(Level.WARNING, "There is no open file for handle " + fi.fh.get());
        }
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, long size, long offset, FuseFileInfo fi) {
        LOGGER.log(Level.INFO, Thread.currentThread().getName() + "|Reading from file " + path + " (handle " + fi.fh.get() + ") offset=" + offset + " size=" + size);
        Path resticPath = fileSystem.getPath(path);
        if (!Files.exists(resticPath)) {
            return -ErrorCodes.ENOENT();
        }

        if (offset > Integer.MAX_VALUE) {
            LOGGER.log(Level.SEVERE, "offset is too big");
            return -ErrorCodes.ENOMEM();
        }
        if (size > Integer.MAX_VALUE) {
            LOGGER.log(Level.SEVERE, "size is too big");
            return -ErrorCodes.ENOMEM();
        }

        try {
            InputStream channel = openfiles.get(fi.fh.get());
            if (channel == null) {
                LOGGER.log(Level.SEVERE, "No open channel found for file handle");
                return -ErrorCodes.EIO();
            }

            // synchronized (channel) {
                // if (channel.position() != offset) {
                //     LOGGER.log(Level.SEVERE, "file channel is at " + channel.position() + " which doesn't match requested offset");
                //     return -ErrorCodes.EIO();
                // }

                ByteBuffer buffer = ByteBuffer.allocate((int) size);
                int bytesRead = channel.read(buffer.array());

                if (bytesRead > 0) {
                    buf.put(0, buffer.array(), 0, bytesRead);
                    return bytesRead;
                } else {
                    LOGGER.log(Level.INFO, "Reached end-of-file");
                    return 0; // EOF
                }
            // }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error while reading file " + path, e);
            return -ErrorCodes.EIO();
        }
    }

    public static void main(String[] args) throws IOException {
        ResticFS fs = new ResticFS(Path.of("src/test/resources/repos/repo1"), "test");
        String path =
                switch (Platform.getNativePlatform().getOS()) {
                    case WINDOWS -> "J://";
                    default -> "/tmp/fuse-restic";
                };
        Path ppath = Paths.get(path);
        Files.createDirectories(ppath);
        try {
            fs.mount(ppath, true, true);
        } finally {
            fs.umount();
            Files.delete(ppath);
        }
    }
}
