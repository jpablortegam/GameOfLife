package com.example.gameoflife;

import com.example.gameoflife.ui.HeroSection;
import com.example.gameoflife.ui.MovieCard;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

public class LazyLoadImageApp extends Application {

    // --- Constantes de Datos ---
    private static final String HERO_URL =
        "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?fm=jpg&q=85&w=1400";

    private static final String[][] MOVIES = {
        {
            "https://images.unsplash.com/photo-1536440136628-849c177e76a1?fm=jpg&q=85&w=400",
            "The Dark Knight",
            "Acción • 2008",
            "9.0",
        },
        {
            "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?fm=jpg&q=85&w=400",
            "Blade Runner 2049",
            "Neo-Noir • 2017",
            "8.0",
        },
        {
            "https://images.unsplash.com/photo-1542204165-65bf26472b9b?fm=jpg&q=85&w=400",
            "Dune",
            "Épica • 2021",
            "8.4",
        },
        {
            "https://images.unsplash.com/photo-1509347528160-9329f6b3c38a?fm=jpg&q=85&w=400",
            "Inception",
            "Thriller • 2010",
            "8.8",
        },
    };

    // --- Constantes de Layout ---
    private static final double APP_WIDTH = 1020;
    private static final double APP_HEIGHT = 810;
    private static final double SIDE_PADDING = 40;

    @Override
    public void start(Stage stage) {
        // Contenedor principal
        VBox root = new VBox();
        root.setStyle("-fx-background-color: #0f0f0f;");

        // Sección Hero
        HeroSection hero = new HeroSection(
            HERO_URL,
            "Interstellar",
            "Ciencia ficción • 2014 • 2h 49m",
            "9.2"
        );

        // Título de la sección
        Label sectionLabel = new Label("EN CARTELERA");
        sectionLabel.setTextFill(Color.rgb(255, 255, 255, 0.45));
        sectionLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        VBox.setMargin(sectionLabel, new Insets(32, 0, 16, SIDE_PADDING));

        // Fila de películas
        HBox movieRow = new HBox(18); // Espacio entre tarjetas
        movieRow.setPadding(new Insets(0, SIDE_PADDING, 40, SIDE_PADDING));
        buildMovieCards(movieRow);

        // Ensamblar la vista
        root.getChildren().addAll(hero, sectionLabel, movieRow);

        // Configurar la ventana
        Scene scene = new Scene(root, APP_WIDTH, APP_HEIGHT);
        scene.setFill(Color.web("#0f0f0f"));

        stage.setTitle("CineStream Pro");
        stage.setScene(scene);
        stage.show();
    }

    private void buildMovieCards(HBox row) {
        for (int i = 0; i < MOVIES.length; i++) {
            String[] movie = MOVIES[i];

            MovieCard card = new MovieCard(
                movie[0],
                movie[1],
                movie[2],
                movie[3]
            );

            // Añadir al contenedor antes de animar
            row.getChildren().add(card);

            // Iniciar animación en cascada
            animateCardEntrance(card, i);
        }
    }

    private void animateCardEntrance(MovieCard card, int index) {
        // Estado inicial
        card.setOpacity(0);
        card.setTranslateY(30); // Un poco más de desplazamiento para un efecto más claro

        Duration duration = Duration.millis(600);

        FadeTransition fade = new FadeTransition(duration, card);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(duration, card);
        slide.setToY(0);

        // Agrupar animaciones para mejor rendimiento de la GPU
        ParallelTransition entranceAnimation = new ParallelTransition(
            fade,
            slide
        );

        // EASE_OUT hace que empiece rápido y termine suavemente (efecto Premium)
        entranceAnimation.setInterpolator(Interpolator.EASE_OUT);

        // Retraso escalonado basado en el índice (Efecto cascada)
        entranceAnimation.setDelay(Duration.millis(150 + (index * 100L)));

        entranceAnimation.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
