package org.example.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import org.example.util.DbInitializer;

import java.io.IOException;

public class MainApp extends Application {

    private Stage primaryStage;
    private BorderPane rootLayout;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("Analizador Estadístico Avanzado");

        // Inicializa la base de datos al iniciar la aplicación
        DbInitializer.initializeDatabase();

        try {
            // Cargar el diseño principal desde MainLayout.fxml
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/org/example/gui/MainLayout.fxml"));
            rootLayout = loader.load();

            // Mostrar la escena que contiene el diseño principal
            Scene scene = new Scene(rootLayout);
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (IOException e) {
            System.err.println("Error al cargar el layout principal: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error de Inicio", "No se pudo cargar la interfaz principal. Consulta la consola para más detalles.");
        }
    }

    // Método de utilidad para mostrar alertas
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}