package org.example.app;

import javafx.application.Application;

// Esta clase es solo para lanzar la aplicación JavaFX
// Es necesaria debido a las restricciones de módulo de JavaFX con algunos JDKs.
public class AppLauncher {
    public static void main(String[] args) {
        // Lanza la aplicación JavaFX llamando al método launch de MainApp
        Application.launch(MainApp.class, args);
    }
}