package com.example.gameoflife;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import javafx.scene.image.Image;

public class ImageCache {

    private final int maxSize;

    // Almacenamiento principal: Lecturas concurrentes sin bloqueos (O(1) real)
    private final ConcurrentHashMap<String, Image> cache;

    // Cola concurrente para registrar el orden de inserción sin bloquear el mapa
    private final ConcurrentLinkedQueue<String> evictionQueue;

    public ImageCache() {
        this(150);
    }

    public ImageCache(int maxSize) {
        this.maxSize = maxSize;
        // Inicializamos con el tamaño máximo y un loadFactor de 1.0f
        // para evitar el overhead de re-hashing dinámico en memoria.
        this.cache = new ConcurrentHashMap<>(maxSize + 1, 1.0f);
        this.evictionQueue = new ConcurrentLinkedQueue<>();
    }

    public Image get(String url) {
        // LECTURA PURA: No hay locks, no hay mutación de punteros, no hay GC.
        // Volátil y directo a la memoria principal/caché del procesador.
        return cache.get(url);
    }

    public Image computeIfAbsent(String url, Function<String, Image> loader) {
        // Usamos la resolución atómica nativa del ConcurrentHashMap
        return cache.computeIfAbsent(url, k -> {
            // 1. Cargar la imagen (esto puede tomar tiempo, pero gracias a
            // ConcurrentHashMap, los demás hilos pueden seguir leyendo otras imágenes).
            Image newImage = loader.apply(k);

            // 2. Registrar la inserción en la cola FIFO
            evictionQueue.offer(k);

            // 3. Mantener el límite de memoria evaporando los más antiguos
            if (cache.size() > maxSize) {
                String eldest = evictionQueue.poll();
                if (eldest != null) {
                    cache.remove(eldest);
                }
            }

            return newImage;
        });
    }

    public void clear() {
        cache.clear();
        evictionQueue.clear();
    }

    public int size() {
        return cache.size();
    }
}
