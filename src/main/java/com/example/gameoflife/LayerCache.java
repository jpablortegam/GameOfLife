package com.example.gameoflife;

import javafx.scene.image.Image;

public class LayerCache {

    public enum Layer {
        BACKGROUND(0),
        CARD(1),
        EFFECT(2),
        MODAL(3),
        DEBUG(4);

        final int index;
        Layer(int index) { this.index = index; }
    }

    private final Image[] cache = new Image[5];
    private final boolean[] valid = new boolean[5];

    public void cache(Layer layer, Image image) {
        cache[layer.index] = image;
        valid[layer.index] = true;
    }

    public Image get(Layer layer) {
        return valid[layer.index] ? cache[layer.index] : null;
    }

    public boolean isValid(Layer layer) {
        return valid[layer.index];
    }

    public void invalidate(Layer layer) {
        valid[layer.index] = false;
        cache[layer.index] = null;
    }

    public void invalidateAll() {
        for (int i = 0; i < cache.length; i++) {
            valid[i] = false;
            cache[i] = null;
        }
    }
}
