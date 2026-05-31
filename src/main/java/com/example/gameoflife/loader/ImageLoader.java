package com.example.gameoflife.loader;

import com.example.gameoflife.cache.ImageDiskCache;
import com.example.gameoflife.cache.ImageMemoryCache;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.image.Image;

public final class ImageLoader {

    public interface Callback {
        void onSuccess(Image image, boolean fromCache);

        void onError(Throwable error);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors())
    );

    private static final Map<String, CompletableFuture<Image>> IN_FLIGHT =
        new ConcurrentHashMap<>();

    private ImageLoader() {}

    public static void preload(String url, double width, double height) {
        load(
            url,
            width,
            height,
            new Callback() {
                @Override
                public void onSuccess(Image image, boolean fromCache) {}

                @Override
                public void onError(Throwable error) {}
            }
        );
    }

    public static void load(
        String url,
        double width,
        double height,
        Callback callback
    ) {
        Image cached = ImageMemoryCache.get(url);

        if (cached != null) {
            Platform.runLater(() -> callback.onSuccess(cached, true));

            return;
        }

        CompletableFuture<Image> existing = IN_FLIGHT.get(url);

        if (existing != null) {
            existing.whenComplete((image, error) -> {
                Platform.runLater(() -> {
                    if (error != null) {
                        callback.onError(error);
                    } else {
                        callback.onSuccess(image, true);
                    }
                });
            });

            return;
        }

        CompletableFuture<Image> future = CompletableFuture.supplyAsync(
            () -> download(url, width, height),
            POOL
        );

        IN_FLIGHT.put(url, future);

        future.whenComplete((image, error) -> {
            IN_FLIGHT.remove(url);

            Platform.runLater(() -> {
                if (error != null) {
                    callback.onError(error);
                } else {
                    callback.onSuccess(image, false);
                }
            });
        });
    }

    private static Image download(String url, double width, double height) {
        try {
            if (ImageDiskCache.exists(url)) {
                try (InputStream is = ImageDiskCache.read(url)) {
                    Image image = new Image(is, width, height, false, true);

                    ImageMemoryCache.put(url, image);

                    return image;
                }
            }

            HttpURLConnection con = (HttpURLConnection) new URL(
                url
            ).openConnection();

            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);

            con.setRequestProperty("User-Agent", "Mozilla/5.0");

            con.setRequestProperty("Accept", "image/jpeg,image/png,image/*");

            String type = con.getContentType();

            if (type == null || !type.startsWith("image/")) {
                throw new RuntimeException("Invalid image: " + type);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try (InputStream is = con.getInputStream()) {
                byte[] buffer = new byte[8192];

                int read;

                while ((read = is.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            byte[] data = out.toByteArray();

            ImageDiskCache.write(url, data);

            Image image = new Image(
                new ByteArrayInputStream(data),
                width,
                height,
                false,
                true
            );

            ImageMemoryCache.put(url, image);

            return image;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
