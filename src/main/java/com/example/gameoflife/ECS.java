package com.example.gameoflife;

import javafx.scene.image.Image;

public class ECS {
    public static final int MAX = 10000;
    private int count = 0;

    private double[] x = new double[MAX];
    private double[] y = new double[MAX];
    private double[] w = new double[MAX];
    private double[] h = new double[MAX];
    private double[] scale = new double[MAX];
    private double[] targetScale = new double[MAX];
    private double[] scaleVel = new double[MAX];
    private double[] opacity = new double[MAX];
    private double[] targetOpacity = new double[MAX];
    private double[] opacityVel = new double[MAX];
    private double[] currentY = new double[MAX];
    private double[] targetY = new double[MAX];
    private double[] yVel = new double[MAX];
    private double[] delayTimer = new double[MAX];
    private double[] imageAlpha = new double[MAX];
    private int[] hoverState = new int[MAX];
    private boolean[] imageLoaded = new boolean[MAX];
    private Image[] images = new Image[MAX];
    private Image[] skeletonTex = new Image[MAX];
    private Image[] overlayTex = new Image[MAX];
    private Image[] modalTextTex = new Image[MAX];
    private boolean[] active = new boolean[MAX];

    public int create() {
        int e = count;
        count++;
        active[e] = true;
        return e;
    }

    public void remove(int e) {
        active[e] = false;
    }

    public boolean exists(int e) {
        return e >= 0 && e < MAX && active[e];
    }

    public int count() { return count; }

    public double getX(int e) { return x[e]; }
    public void setX(int e, double v) { x[e] = v; }
    public double getY(int e) { return y[e]; }
    public void setY(int e, double v) { y[e] = v; }
    public double getW(int e) { return w[e]; }
    public void setW(int e, double v) { w[e] = v; }
    public double getH(int e) { return h[e]; }
    public void setH(int e, double v) { h[e] = v; }

    public double getScale(int e) { return scale[e]; }
    public void setScale(int e, double v) { scale[e] = v; }
    public double getTargetScale(int e) { return targetScale[e]; }
    public void setTargetScale(int e, double v) { targetScale[e] = v; }
    public double getScaleVel(int e) { return scaleVel[e]; }
    public void setScaleVel(int e, double v) { scaleVel[e] = v; }

    public double getOpacity(int e) { return opacity[e]; }
    public void setOpacity(int e, double v) { opacity[e] = v; }
    public double getTargetOpacity(int e) { return targetOpacity[e]; }
    public void setTargetOpacity(int e, double v) { targetOpacity[e] = v; }
    public double getOpacityVel(int e) { return opacityVel[e]; }
    public void setOpacityVel(int e, double v) { opacityVel[e] = v; }

    public double getCurrentY(int e) { return currentY[e]; }
    public void setCurrentY(int e, double v) { currentY[e] = v; }
    public double getTargetY(int e) { return targetY[e]; }
    public void setTargetY(int e, double v) { targetY[e] = v; }
    public double getYVel(int e) { return yVel[e]; }
    public void setYVel(int e, double v) { yVel[e] = v; }

    public double getDelayTimer(int e) { return delayTimer[e]; }
    public void setDelayTimer(int e, double v) { delayTimer[e] = v; }
    public double getImageAlpha(int e) { return imageAlpha[e]; }
    public void setImageAlpha(int e, double v) { imageAlpha[e] = v; }
    public int getHoverState(int e) { return hoverState[e]; }
    public void setHoverState(int e, int v) { hoverState[e] = v; }
    public boolean isImageLoaded(int e) { return imageLoaded[e]; }
    public void setImageLoaded(int e, boolean v) { imageLoaded[e] = v; }

    public Image getImage(int e) { return images[e]; }
    public void setImage(int e, Image v) { images[e] = v; }
    public Image getSkeletonTex(int e) { return skeletonTex[e]; }
    public void setSkeletonTex(int e, Image v) { skeletonTex[e] = v; }
    public Image getOverlayTex(int e) { return overlayTex[e]; }
    public void setOverlayTex(int e, Image v) { overlayTex[e] = v; }
    public Image getModalTextTex(int e) { return modalTextTex[e]; }
    public void setModalTextTex(int e, Image v) { modalTextTex[e] = v; }
}
