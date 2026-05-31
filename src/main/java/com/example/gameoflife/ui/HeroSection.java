package com.example.gameoflife.ui;

import com.example.gameoflife.loader.ImageLoader;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

public class HeroSection extends StackPane {

    // --- Constantes de Diseño ---
    public static final double HEIGHT = 470;
    private static final double MAX_WIDTH = 1400;
    private static final String FONT_FAMILY_PRIMARY = "Segoe UI";
    private static final String FONT_FAMILY_TITLE = "Georgia";

    private final ImageView imageView = new ImageView();

    public HeroSection(
        String imageUrl,
        String title,
        String subtitle,
        String rating
    ) {
        // Configuración del contenedor principal
        setPrefHeight(HEIGHT);
        setMinHeight(HEIGHT);
        setMaxHeight(HEIGHT);

        // --- SOLUCIÓN DEL BUG DE DESBORDAMIENTO ---
        // Esto recorta la imagen cuando la animación de escala la hace más grande que 470px
        Rectangle clipRect = new Rectangle();
        clipRect.widthProperty().bind(widthProperty());
        clipRect.setHeight(HEIGHT);
        this.setClip(clipRect);
        // ------------------------------------------

        setCache(true);
        setCacheHint(CacheHint.SPEED);

        // Fondo base (por si la imagen tarda en cargar)
        Rectangle background = new Rectangle();
        background.widthProperty().bind(widthProperty());
        background.setHeight(HEIGHT);
        background.setFill(Color.rgb(18, 18, 18));

        // Configuración de la imagen
        imageView.setPreserveRatio(false);
        imageView.setOpacity(0);
        imageView.setSmooth(true);
        imageView.setCache(true);
        imageView.setCacheHint(CacheHint.SPEED);

        // Degradados para mejorar la legibilidad del texto
        Rectangle leftGradient = createLeftGradient();
        Rectangle bottomGradient = createBottomGradient();

        // Contenido de texto y botones
        VBox content = createContent(title, subtitle, rating);
        content.setOpacity(0);
        content.setTranslateY(30);
        StackPane.setAlignment(content, Pos.BOTTOM_LEFT);

        getChildren().addAll(
            background,
            imageView,
            leftGradient,
            bottomGradient,
            content
        );

        loadHeroImage(imageUrl, content);
    }

    private void loadHeroImage(String url, VBox content) {
        ImageLoader.load(
            url,
            (int) MAX_WIDTH,
            (int) HEIGHT,
            new ImageLoader.Callback() {
                @Override
                public void onSuccess(Image image, boolean fromCache) {
                    imageView.setImage(image);
                    imageView.setFitWidth(
                        Math.max(MAX_WIDTH, image.getWidth())
                    );
                    imageView.setFitHeight(HEIGHT);

                    animateImage(fromCache);
                    animateContent(content, fromCache);
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println(
                        "[HeroSection] Error cargando imagen: " +
                            error.getMessage()
                    );
                }
            }
        );
    }

    private void animateImage(boolean fromCache) {
        // Efecto de aparición (Fade)
        FadeTransition fade = new FadeTransition(
            Duration.millis(fromCache ? 150 : 700),
            imageView
        );
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();

        // Efecto Ken Burns suave (Zoom y Paneo)
        ScaleTransition scale = new ScaleTransition(
            Duration.seconds(60),
            imageView
        );
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.04);
        scale.setToY(1.04);

        TranslateTransition move = new TranslateTransition(
            Duration.seconds(60),
            imageView
        );
        move.setFromX(0);
        move.setToX(-20);

        ParallelTransition kenBurnsEffect = new ParallelTransition(scale, move);
        kenBurnsEffect.setCycleCount(Animation.INDEFINITE);
        kenBurnsEffect.setAutoReverse(true);
        kenBurnsEffect.play();
    }

    private void animateContent(VBox content, boolean fromCache) {
        Duration duration = Duration.millis(fromCache ? 200 : 800);

        FadeTransition fade = new FadeTransition(duration, content);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(duration, content);
        slide.setFromY(30);
        slide.setToY(0);

        ParallelTransition contentAnimation = new ParallelTransition(
            fade,
            slide
        );
        contentAnimation.play();
    }

    private Rectangle createLeftGradient() {
        Rectangle rect = new Rectangle();
        rect.setHeight(HEIGHT);
        rect.widthProperty().bind(widthProperty());
        rect.setFill(
            new LinearGradient(
                0,
                0,
                1,
                0,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(15, 15, 15, 0.95)),
                new Stop(0.4, Color.rgb(15, 15, 15, 0.5)),
                new Stop(0.8, Color.TRANSPARENT)
            )
        );
        return rect;
    }

    private Rectangle createBottomGradient() {
        Rectangle rect = new Rectangle();
        rect.setHeight(HEIGHT);
        rect.widthProperty().bind(widthProperty());
        rect.setFill(
            new LinearGradient(
                0,
                1,
                0,
                0,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(15, 15, 15, 0.9)),
                new Stop(0.3, Color.rgb(15, 15, 15, 0.3)),
                new Stop(1, Color.TRANSPARENT)
            )
        );
        return rect;
    }

    private VBox createContent(String title, String subtitle, String rating) {
        VBox root = new VBox(12);
        root.setPadding(new Insets(0, 0, 50, 60));
        root.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.6))); // Sombra para legibilidad

        // Etiqueta de Destacado
        Label featured = new Label("◆ DESTACADO");
        featured.setTextFill(Color.web("#E5A93C")); // Dorado elegante
        featured.setFont(
            Font.font(FONT_FAMILY_PRIMARY, FontWeight.EXTRA_BOLD, 12)
        );
        featured.setEffect(new DropShadow(5, Color.BLACK));

        // Título principal
        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Font.font(FONT_FAMILY_TITLE, FontWeight.BOLD, 64));

        // Subtítulo / Descripción
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setTextFill(Color.rgb(230, 230, 230));
        subtitleLabel.setFont(
            Font.font(FONT_FAMILY_PRIMARY, FontWeight.NORMAL, 16)
        );
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMaxWidth(600); // Evitar texto infinito en pantallas anchas

        // Fila de Información (Calificación y HD)
        HBox infoRow = new HBox(15);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        Label ratingLabel = new Label("★ " + rating);
        ratingLabel.setTextFill(Color.web("#E5A93C"));
        ratingLabel.setFont(
            Font.font(FONT_FAMILY_PRIMARY, FontWeight.BOLD, 16)
        );

        Label hdLabel = new Label("HD");
        hdLabel.setFont(Font.font(FONT_FAMILY_PRIMARY, FontWeight.BOLD, 12));
        hdLabel.setStyle(
            "-fx-border-color: rgba(255,255,255,0.5);" +
                "-fx-border-radius: 4;" +
                "-fx-padding: 2 6 2 6;" +
                "-fx-text-fill: white;" +
                "-fx-background-color: rgba(0,0,0,0.3);" +
                "-fx-background-radius: 4;"
        );

        infoRow.getChildren().addAll(ratingLabel, hdLabel);

        // Fila de Botones (Acciones)
        HBox actionRow = new HBox(15);
        actionRow.setPadding(new Insets(10, 0, 0, 0));

        Button playButton = createButton(
            "▶ Reproducir",
            "-fx-background-color: white; -fx-text-fill: black;"
        );
        Button infoButton = createButton(
            "ⓘ Más información",
            "-fx-background-color: rgba(109, 109, 110, 0.7); -fx-text-fill: white;"
        );

        actionRow.getChildren().addAll(playButton, infoButton);

        // Ensamblar todo
        root.getChildren().addAll(
            featured,
            titleLabel,
            subtitleLabel,
            infoRow,
            actionRow
        );

        return root;
    }

    // Método auxiliar para crear botones limpios
    private Button createButton(String text, String baseStyle) {
        Button btn = new Button(text);
        btn.setFont(Font.font(FONT_FAMILY_PRIMARY, FontWeight.BOLD, 15));
        btn.setStyle(
            baseStyle + " -fx-background-radius: 4; -fx-padding: 10 24 10 24;"
        );
        btn.setCursor(Cursor.HAND);

        // Efecto hover simple
        btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));

        return btn;
    }
}
