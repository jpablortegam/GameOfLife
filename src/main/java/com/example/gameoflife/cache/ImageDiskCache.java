package com.example.gameoflife.cache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.stream.Stream;

public final class ImageDiskCache {

    private static final Path CACHE_DIR = Paths.get(
        System.getProperty("user.home"),
        ".cinestream",
        "cache"
    );

    /*
     * 500 MB máximo
     */
    private static final long MAX_CACHE_SIZE = 500L * 1024L * 1024L;

    static {
        try {
            Files.createDirectories(CACHE_DIR);

            cleanup();
        } catch (Exception ignored) {}
    }

    private ImageDiskCache() {}

    private static String fileName(String url) {
        return Integer.toHexString(url.hashCode()) + ".img";
    }

    public static Path getFile(String url) {
        return CACHE_DIR.resolve(fileName(url));
    }

    public static boolean exists(String url) {
        return Files.exists(getFile(url));
    }

    public static InputStream read(String url) throws IOException {
        Path file = getFile(url);

        Files.setLastModifiedTime(
            file,
            FileTime.fromMillis(System.currentTimeMillis())
        );

        return Files.newInputStream(file);
    }

    public static void write(String url, byte[] data) throws IOException {
        Path file = getFile(url);

        Files.write(
            file,
            data,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        cleanup();
    }

    public static void remove(String url) throws IOException {
        Files.deleteIfExists(getFile(url));
    }

    public static void clear() throws IOException {
        try (Stream<Path> stream = Files.list(CACHE_DIR)) {
            stream.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {}
            });
        }
    }

    public static long size() throws IOException {
        try (Stream<Path> stream = Files.list(CACHE_DIR)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();
        }
    }

    private static void cleanup() throws IOException {
        long currentSize = size();

        if (currentSize <= MAX_CACHE_SIZE) {
            return;
        }

        try (Stream<Path> stream = Files.list(CACHE_DIR)) {
            Path[] files = stream
                .filter(Files::isRegularFile)
                .sorted(
                    Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return Long.MAX_VALUE;
                        }
                    })
                )
                .toArray(Path[]::new);

            for (Path file : files) {
                long fileSize = Files.size(file);

                Files.deleteIfExists(file);

                currentSize -= fileSize;

                if (currentSize <= MAX_CACHE_SIZE) {
                    break;
                }
            }
        }
    }
}
