package org.adangel.resticbrowser.fuse;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
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
            return -ErrorCodes.EIO();
        }
        return 0;
    }

    @Override
    public int getattr(String path, FileStat stat) {
        int res = 0;
        Path resticPath = fileSystem.getPath(path);
        try {
            if (Files.exists(resticPath)) {
                BasicFileAttributes attributes = Files.readAttributes(resticPath, BasicFileAttributes.class);
                if (attributes.isDirectory()) {
                    stat.st_mode.set(FileStat.S_IFDIR | 0555);
                    stat.st_nlink.set(2);
                } else if (attributes.isRegularFile()) {
                    stat.st_mode.set(FileStat.S_IFREG | 0444);
                    stat.st_nlink.set(1);
                    stat.st_size.set(attributes.size());
                } else {
                    res = -ErrorCodes.ENOENT();
                }
            } else {
                res = -ErrorCodes.ENOENT();
            }
        } catch (IOException e) {
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
