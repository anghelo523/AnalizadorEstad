package org.example.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.example.model.Dataset;
import org.example.dao.DatasetDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import javafx.scene.control.ButtonType;
// Importaciones de GSON (ahora sí deberían funcionar con el pom.xml corregido)
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import javafx.stage.FileChooser;


public class MainLayoutController {

    @FXML private TabPane mainTabPane;

    // Referencias a los controladores de las pestañas
    private DataManagerController dataManagerController;
    private RegressionAnalysisController regressionAnalysisController;
    private MediationModerationController mediationModerationController;

    private DatasetDAO datasetDAO;
    private Dataset currentActiveDataset; // El dataset que está activo globalmente

    @FXML
    public void initialize() {
        datasetDAO = new DatasetDAO();

        // Cargar los paneles FXML en las pestañas y obtener sus controladores
        try {
            // Pestaña Gestión de Datos
            FXMLLoader dataManagerLoader = new FXMLLoader(getClass().getResource("/org/example/gui/DataManagerPanel.fxml"));
            Tab dataManagerTab = mainTabPane.getTabs().get(0); // Suponemos que es la primera pestaña
            dataManagerTab.setContent(dataManagerLoader.load());
            dataManagerController = dataManagerLoader.getController();
            dataManagerController.setMainLayoutController(this); // Pasar referencia a sí mismo

            // Pestaña Análisis de Regresión
            FXMLLoader regressionLoader = new FXMLLoader(getClass().getResource("/org/example/gui/RegressionAnalysisPanel.fxml"));
            Tab regressionTab = mainTabPane.getTabs().get(1); // Suponemos que es la segunda pestaña
            regressionTab.setContent(regressionLoader.load());
            regressionAnalysisController = regressionLoader.getController();

            // Pestaña Mediación / Moderación
            FXMLLoader mediationModerationLoader = new FXMLLoader(getClass().getResource("/org/example/gui/MediationModerationPanel.fxml"));
            Tab mediationModerationTab = mainTabPane.getTabs().get(2); // Suponemos que es la tercera pestaña
            mediationModerationTab.setContent(mediationModerationLoader.load());
            mediationModerationController = mediationModerationLoader.getController();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Carga", "No se pudo cargar una o más paneles: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Error al cargar FXMLs: " + e.getMessage()); // Más detalles en consola
        }
    }

    // Este método será llamado por DataManagerController para actualizar el dataset activo global
    public void updateActiveDataset(Dataset dataset) {
        this.currentActiveDataset = dataset;
        // Notificar a otros controladores sobre el cambio de dataset activo
        if (regressionAnalysisController != null) {
            regressionAnalysisController.updateDataset(currentActiveDataset);
        }
        if (mediationModerationController != null) {
            mediationModerationController.updateDataset(currentActiveDataset);
        }
    }


    // --- Manejadores de eventos del menú (File, Edit, Help) ---

    @FXML
    private void handleNewProject() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Nuevo Proyecto");
        confirmAlert.setHeaderText("Crear un Nuevo Proyecto");
        confirmAlert.setContentText("¿Está seguro de que desea iniciar un nuevo proyecto? Esto eliminará todos los datasets de la base de datos.");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                datasetDAO.deleteAllDatasetsAndData(); // Llama al nuevo método en DatasetDAO
                // Después de borrar, inicializar un nuevo dataset vacío en DataManager
                if (dataManagerController != null) {
                    dataManagerController.handleNewDataset(); // Esto debería crear un dataset vacío y actualizar la vista
                }
                // El handleNewDataset() ya llama a updateActiveDataset(currentDataset)
                showAlert(Alert.AlertType.INFORMATION, "Nuevo Proyecto", "Nuevo proyecto iniciado. Base de datos limpia.");
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Error de BD", "Error al limpiar la base de datos para nuevo proyecto: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleOpenProject() {
        showAlert(Alert.AlertType.INFORMATION, "Abrir Proyecto", "Funcionalidad de abrir proyecto no implementada. Usa 'Cargar' en Gestión de Datos.");
    }

    @FXML
    private void handleSaveProject() {
        // Guarda el dataset actual si hay uno activo
        if (dataManagerController != null) {
            dataManagerController.handleSaveDataset(); // Llama al método de guardar del DataManager
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Guardar Proyecto", "No hay un dataset abierto o el gestor de datos no está listo.");
        }
    }

    @FXML
    private void handleSaveProjectAs() {
        showAlert(Alert.AlertType.INFORMATION, "Guardar Proyecto Como", "Funcionalidad no implementada. Puedes exportar datasets individualmente.");
    }

    @FXML
    private void handleExit() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Salir");
        confirmAlert.setHeaderText("Salir de la Aplicación");
        confirmAlert.setContentText("¿Está seguro de que desea salir? Asegúrese de haber guardado todos los cambios.");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            System.exit(0); // Cierra la aplicación
        }
    }

    @FXML
    private void handlePreferences() {
        showAlert(Alert.AlertType.INFORMATION, "Preferencias", "Funcionalidad no implementada.");
    }

    @FXML
    private void handleAbout() {
        showAlert(Alert.AlertType.INFORMATION, "Acerca de", "Analizador Estadístico Avanzado\nVersión 1.0\nDesarrollado por [Tu Nombre/Equipo]");
    }

    // --- Utilidades ---
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}