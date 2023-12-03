package org.adangel.resticbrowser.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResticFileSystemRepo4Test {
    private static ResticFileSystem fileSystem;
    private static Path snapshotBasePath;

    @BeforeAll
    static void initLogging() throws IOException {
        LogManager.getLogManager().readConfiguration(ResticFileSystemRepo4Test.class.getResourceAsStream("/logging.properties"));
    }

    @BeforeAll
    static void createFileSystem() throws IOException {
        ResticFileSystemProvider provider = new ResticFileSystemProvider();
        fileSystem = provider.newFileSystem(Path.of("src/test/resources/repos/repo4"), Map.of("RESTIC_PASSWORD", "test"));
        snapshotBasePath = fileSystem.getPath("/snapshots/a9e4ac2f30d86cf98d52648d00e9fe03313e2cc47e5533e7f1e5c35e2e52d364");
    }

    @Test
    void readFile() throws Exception {
        // "file.raw" has two content chunks (blobs)
        Path path = snapshotBasePath.resolve("file.raw");
        MessageDigest sha256 = MessageDigest.getInstance("SHA256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                sha256.update(buffer, 0, read);
            }
        }
        byte[] digest = sha256.digest();
        String digestString = HexFormat.of().formatHex(digest);
        assertEquals("4360fffcd35689d5ae2ca95b28b289bebf15905d3db33f4e918ae814f9140841", digestString);
    }

    /**
     * This test tries to mimic the behavior of
     * sun.nio.fs.UnixFileSystemProvider#newByteChannel(java.nio.file.Path, java.util.Set, java.nio.file.attribute.FileAttribute[]).
     * This reads blocks of different sizes in multiple threads. See ResticFSTest#readFileExternally().
     *
     * <p>Read sequence is:
     * 1. read first 16384 bytes and wait for result
     * 2. read offset=16384 size=32768 and offset=49152 size=65536 in parallel and wait
     * 3. read offset=114688 size=131072 and offset=245760 size=131072 in parallel and wait
     * 4. and so on - always 2 threads in parallel reading the next 128k block
     *
     * @throws Exception
     */
    @Test
    void readFileMultiThreaded() throws Exception {
        // "file.raw" has two content chunks (blobs), it is 2_097_152 bytes in size
        Path path = snapshotBasePath.resolve("file.raw");
        long fileSize = Files.size(path);
        System.out.println("size = " + fileSize);

        MessageDigest sha256 = MessageDigest.getInstance("SHA256");

        final Semaphore lock = new Semaphore(1, true);
        final AtomicInteger currentTask = new AtomicInteger(-1);

        try (SeekableByteChannel channel = Files.newByteChannel(path);
             //ExecutorService executorService = Executors.newThreadPerTaskExecutor(Executors.defaultThreadFactory());
             ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
             ) {

            class ReadBlock implements Callable<ByteBuffer> {
                private static final Logger LOGGER = Logger.getLogger(ReadBlock.class.getName());
                private final long offset;
                private final int size;
                private final SeekableByteChannel channel;
                private final int task;
                ReadBlock(long offset, int size, SeekableByteChannel channel, int task) {
                    this.offset = offset;
                    this.size = size;
                    this.channel = channel;
                    this.task = task;
                }

                @Override
                public ByteBuffer call() throws Exception {
                    LOGGER.info("Starting Thread: " + Thread.currentThread().getName() + "|Reading offset=" + offset + " size=" + size);
                    //waitForCurrentPosition();
                    //waitForPreviousTask();
                    waitForPreviousTaskLock();
                    LOGGER.info("Finished waiting Thread: " + Thread.currentThread().getName() + "|Reading offset=" + offset + " size=" + size);

                    try {
                        LOGGER.info("Thread: " + Thread.currentThread().getName() + "|Reading offset=" + offset + " size=" + size);

                        //channel.position(offset);
                        if (channel.position() != offset) {
                            throw new IllegalStateException("Expected offset=" + offset + " but was " + channel.position());
                        }

                        ByteBuffer dst = ByteBuffer.allocate(size);
                        channel.read(dst);
                        dst.flip();
                        return dst;
                    } finally {
                        currentTask.set(task);
                        lock.release();
                    }
                }

                private void waitForPreviousTaskLock() throws InterruptedException {
                    while (currentTask.get() != task - 1) {
                        lock.acquire();
                        if (currentTask.get() != task - 1) {
                            lock.release();
                        }
                    }
                }

                private void waitForPreviousTask() {
                    while (currentTask.get() != task - 1) {
                        // busy wait
                        Thread.yield();
                    }
                }

                private void waitForCurrentPosition() throws IOException {
                    long position;
                    synchronized (channel) {
                        position = channel.position();
                    }
                    while (position != offset) {
                        synchronized (channel) {
                            position = channel.position();
                        }
                    }
                }
            }

            long offset = 0;
            List<Callable<ByteBuffer>> tasks = new ArrayList<>();
            //List<Future<ByteBuffer>> futures = new ArrayList<>();
            // blocks: 16384, 32768, 65536, 131072, 131072, 131072, ..., 16384
            int blockSize = 16384;
            long remaining = fileSize - offset;
            int taskCounter = 0;
            while (remaining > 0L) {
                tasks.add(new ReadBlock(offset, blockSize, channel, taskCounter++));
                //futures.add(executorService.submit(new ReadBlock(offset, blockSize, channel)));
                offset += blockSize;
                remaining = fileSize - offset;

                if (blockSize < 131072) {
                    blockSize *= 2;
                }
                if (blockSize > remaining) {
                    blockSize = (int) remaining;
                }
            }

            List<Future<ByteBuffer>> futures = executorService.invokeAll(tasks);

            for (Future<ByteBuffer> f : futures) {
                sha256.update(f.get());
            }
        }
        byte[] digest = sha256.digest();
        String digestString = HexFormat.of().formatHex(digest);
        assertEquals("4360fffcd35689d5ae2ca95b28b289bebf15905d3db33f4e918ae814f9140841", digestString);

    }
}
