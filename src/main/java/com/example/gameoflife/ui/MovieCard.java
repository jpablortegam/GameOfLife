package com.example.gameoflife.ui;

import com.example.gameoflife.loader.ImageLoader;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

public class MovieCard extends StackPane {

    public static final double WIDTH = 192;
    public static final double HEIGHT = 288;
    private static final double CORNER_RADIUS = 18;

    private final Rectangle imageRect;
    private Timeline hoverInTimeline;
    private Timeline hoverOutTimeline;

    public MovieCard(
        String imageUrl,
        String title,
        String subtitle,
        String rating
    ) {
        setPrefSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setCursor(Cursor.HAND);

        // Ya NO usamos setClip(). Todo se renderiza con bordes nativos.

        // 1. Fondo base con bordes redondeados
        Rectangle background = new Rectangle(
            WIDTH,
            HEIGHT,
            Color.web("#1a1a1a")
        );
        background.setArcWidth(CORNER_RADIUS);
        background.setArcHeight(CORNER_RADIUS);

        // 2. Rectángulo de la imagen (reemplaza a ImageView)
        imageRect = new Rectangle(WIDTH, HEIGHT);
        imageRect.setArcWidth(CORNER_RADIUS);
        imageRect.setArcHeight(CORNER_RADIUS);
        imageRect.setFill(Color.TRANSPARENT); // Transparente hasta que cargue la imagen
        imageRect.setOpacity(0);

        // 3. Degradado superpuesto con bordes redondeados
        Rectangle overlay = new Rectangle(WIDTH, HEIGHT);
        overlay.setArcWidth(CORNER_RADIUS);
        overlay.setArcHeight(CORNER_RADIUS);
        overlay.setFill(
            new LinearGradient(
                0,
                1,
                0,
                0,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 0, 0, 0.95)),
                new Stop(0.5, Color.rgb(0, 0, 0, 0.3)),
                new Stop(1, Color.TRANSPARENT)
            )
        );

        StackPane skeleton = createSkeleton();
        VBox info = createInfo(title, subtitle, rating);

        getChildren().addAll(background, skeleton, imageRect, overlay, info);

        setupFastHover();
        loadImage(imageUrl, skeleton);
    }

    private void loadImage(String url, StackPane skeleton) {
        ImageLoader.load(
            url,
            WIDTH,
            HEIGHT,
            new ImageLoader.Callback() {
                @Override
                public void onSuccess(
                    javafx.scene.image.Image image,
                    boolean fromCache
                ) {
                    // TRUCO DE RENDIMIENTO: Pintar la imagen como textura de un rectángulo
                    imageRect.setFill(new ImagePattern(image));

                    // Fade in suave por propiedad
                    Timeline fade = new Timeline(
                        new KeyFrame(
                            Duration.millis(fromCache ? 80 : 300),
                            new KeyValue(
                                imageRect.opacityProperty(),
                                1.0,
                                Interpolator.EASE_IN
                            )
                        )
                    );
                    fade.play();

                    getChildren().remove(skeleton); // Liberar memoria
                }

                @Override
                public void onError(Throwable error) {
                    skeleton.getChildren().clear();
                    Label fail = new Label("✕");
                    fail.setStyle("-fx-text-fill:#ff6666; -fx-font-size:26;");
                    skeleton.getChildren().add(fail);
                }
            }
        );
    }

    private void setupFastHover() {
        // Duración ultra rápida para respuesta instantánea
        Duration duration = Duration.millis(100);

        // Pre-compilar las animaciones
        hoverInTimeline = new Timeline(
            new KeyFrame(
                duration,
                new KeyValue(scaleXProperty(), 1.05, Interpolator.EASE_OUT),
                new KeyValue(scaleYProperty(), 1.05, Interpolator.EASE_OUT)
            )
        );

        hoverOutTimeline = new Timeline(
            new KeyFrame(
                duration,
                new KeyValue(scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(scaleYProperty(), 1.0, Interpolator.EASE_OUT)
            )
        );

        setOnMouseEntered(e -> {
            hoverOutTimeline.stop();
            // Trae la tarjeta al frente para evitar glitches visuales con las tarjetas vecinas
            this.setViewOrder(-1);
            hoverInTimeline.playFromStart();
        });

        setOnMouseExited(e -> {
            hoverInTimeline.stop();
            // Devuelve la tarjeta a su capa normal
            this.setViewOrder(0);
            hoverOutTimeline.playFromStart();
        });
    }

    private StackPane createSkeleton() {
        StackPane pane = new StackPane();
        pane.setPrefSize(WIDTH, HEIGHT);
        Rectangle bg = new Rectangle(WIDTH, HEIGHT, Color.web("#252525"));
        bg.setArcWidth(CORNER_RADIUS);
        bg.setArcHeight(CORNER_RADIUS);
        pane.getChildren().add(bg);
        return pane;
    }

    private VBox createInfo(String title, String subtitle, String rating) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(0, 12, 14, 12));
        box.setAlignment(Pos.BOTTOM_LEFT);

        // Evitamos recalcular layouts limitando el click (mouse transparent)
        box.setMouseTransparent(true);

        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        titleLabel.setWrapText(false);
        titleLabel.setMaxWidth(WIDTH - 24);

        Label subLabel = new Label(subtitle);
        subLabel.setTextFill(Color.rgb(255, 255, 255, 0.7));
        subLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));

        Label ratingLabel = new Label("★ " + rating);
        ratingLabel.setTextFill(Color.web("#E5A93C"));
        ratingLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        box.getChildren().addAll(titleLabel, subLabel, ratingLabel);
        StackPane.setAlignment(box, Pos.BOTTOM_LEFT);

        return box;
    }
}
