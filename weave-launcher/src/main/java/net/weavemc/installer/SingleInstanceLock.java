package net.weavemc.installer;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class SingleInstanceLock implements Closeable {
    private final RandomAccessFile lockFile;
    private final FileChannel channel;
    private final FileLock lock;

    private SingleInstanceLock(RandomAccessFile lockFile, FileChannel channel, FileLock lock) {
        this.lockFile = lockFile;
        this.channel = channel;
        this.lock = lock;
    }

    static SingleInstanceLock acquire() throws IOException {
        Path weaveDirectory = Paths.get(System.getProperty("user.home"), ".weave");
        Files.createDirectories(weaveDirectory);
        return acquire(weaveDirectory.resolve("fireball-fight-mods.lock"));
    }

    static SingleInstanceLock acquire(Path path) throws IOException {
        RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
        FileChannel fileChannel = file.getChannel();
        FileLock fileLock;
        try {
            fileLock = fileChannel.tryLock();
        } catch (OverlappingFileLockException ignored) {
            fileLock = null;
        }
        if (fileLock == null) {
            fileChannel.close();
            file.close();
            return null;
        }
        return new SingleInstanceLock(file, fileChannel, fileLock);
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            try {
                channel.close();
            } finally {
                lockFile.close();
            }
        }
    }
}
