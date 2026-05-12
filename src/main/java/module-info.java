module com.example.gameoflife {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.base;
    requires java.desktop;
    opens com.example.gameoflife to javafx.graphics;
}
