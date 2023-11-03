package org.adangel.resticbrowser.fuse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
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

    public ResticFS(Path repositoryPath, Map<String, ?> env) throws IOException {
        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        this.fileSystem = provider.newFileSystem(repositoryPath, env);
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
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, long size, long offset, FuseFileInfo fi) {
        Path resticPath = fileSystem.getPath(path);
        if (!Files.exists(resticPath)) {
            return -ErrorCodes.ENOENT();
        }

        try {
            byte[] bytes = Files.readAllBytes(resticPath);
            int length = bytes.length;
            if (offset < length) {
                if (offset + size > length) {
                    size = length - offset;
                }
                buf.put(0, bytes, 0, bytes.length);
            } else {
                size = 0;
            }
            return (int) size;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error while reading file " + path, e);
            return -ErrorCodes.EIO();
        }
    }

    public static void main(String[] args) throws IOException {
        ResticFS fs = new ResticFS(Path.of("src/test/resources/repos/repo1"), Map.of("RESTIC_PASSWORD", "test"));
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
