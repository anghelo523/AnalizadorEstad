package org.example.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode; // Asegurarse de importar SelectionMode
import org.example.model.Dataset;
import org.example.model.Variable;
import org.example.service.EstadisticaService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RegressionAnalysisController {

    @FXML private Label activeDatasetLabel;
    @FXML private ListView<String> availableVariablesListView;
    @FXML private ComboBox<String> dependentVariableComboBox;
    @FXML private ListView<String> independentVariablesListView;
    @FXML private Label resultsLabel;

    private Dataset currentDataset;
    private EstadisticaService estadisticaService;

    @FXML
    public void initialize() {
        estadisticaService = new EstadisticaService();

        // Inicializar los ListView con selección múltiple
        availableVariablesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        independentVariablesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    // Método para ser llamado desde MainLayoutController cuando el dataset cambia
    public void updateDataset(Dataset dataset) {
        this.currentDataset = dataset;
        if (currentDataset != null) {
            activeDatasetLabel.setText(currentDataset.getName());
            populateVariableLists();
        } else {
            activeDatasetLabel.setText("Ninguno");
            availableVariablesListView.getItems().clear();
            dependentVariableComboBox.getItems().clear();
            independentVariablesListView.getItems().clear();
        }
        clearSelectionsAndResults();
    }

    private void populateVariableLists() {
        if (currentDataset != null) {
            ObservableList<String> variableNames = FXCollections.observableArrayList(
                    currentDataset.getVariables().stream()
                            .map(Variable::getName)
                            .collect(Collectors.toList())
            );
            availableVariablesListView.setItems(variableNames);
            dependentVariableComboBox.setItems(variableNames);
        }
    }

    private void clearSelectionsAndResults() {
        dependentVariableComboBox.getSelectionModel().clearSelection();
        independentVariablesListView.getItems().clear();
        resultsLabel.setText("");
    }

    @FXML
    private void handleAddDependentVariable() {
        String selectedVar = availableVariablesListView.getSelectionModel().getSelectedItem();
        if (selectedVar != null) {
            // Solo se permite una variable dependiente
            dependentVariableComboBox.getSelectionModel().select(selectedVar);
        } else {
            showAlert(Alert.AlertType.WARNING, "Selección", "Por favor, selecciona una variable disponible.");
        }
    }

    @FXML
    private void handleRemoveDependentVariable() {
        dependentVariableComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleAddIndependentVariable() {
        ObservableList<String> selectedVars = availableVariablesListView.getSelectionModel().getSelectedItems();
        if (!selectedVars.isEmpty()) {
            // Añadir solo variables que no estén ya en la lista de independientes
            selectedVars.forEach(var -> {
                if (!independentVariablesListView.getItems().contains(var)) {
                    independentVariablesListView.getItems().add(var);
                }
            });
            // Reordenar alfabéticamente si se desea
            FXCollections.sort(independentVariablesListView.getItems());
        } else {
            showAlert(Alert.AlertType.WARNING, "Selección", "Por favor, selecciona al menos una variable disponible.");
        }
    }

    @FXML
    private void handleRemoveIndependentVariable() {
        ObservableList<String> selectedVarsToRemove = independentVariablesListView.getSelectionModel().getSelectedItems();
        if (!selectedVarsToRemove.isEmpty()) {
            independentVariablesListView.getItems().removeAll(selectedVarsToRemove);
        } else {
            showAlert(Alert.AlertType.WARNING, "Eliminar", "Por favor, selecciona una variable independiente para quitar.");
        }
    }

    @FXML
    private void handlePerformRegression() {
        if (currentDataset == null) {
            showAlert(Alert.AlertType.ERROR, "Error de Datos", "No hay un dataset activo. Carga o crea uno en 'Gestión de Datos'.");
            return;
        }

        String dependentVarName = dependentVariableComboBox.getSelectionModel().getSelectedItem();
        if (dependentVarName == null || dependentVarName.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error de Configuración", "Por favor, selecciona una variable dependiente.");
            return;
        }

        List<String> independentVarNames = independentVariablesListView.getItems();
        if (independentVarNames.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error de Configuración", "Por favor, selecciona al menos una variable independiente.");
            return;
        }

        // Validación de que la variable dependiente no es una de las independientes
        if (independentVarNames.contains(dependentVarName)) {
            showAlert(Alert.AlertType.ERROR, "Error de Configuración", "La variable dependiente no puede ser también una variable independiente.");
            return;
        }

        // Validación de que todas las variables seleccionadas son numéricas
        boolean allNumeric = independentVarNames.stream()
                .allMatch(varName -> isNumeric(currentDataset.getVariableByName(varName).getType()));
        if (!isNumeric(currentDataset.getVariableByName(dependentVarName).getType()) || !allNumeric) {
            showAlert(Alert.AlertType.ERROR, "Tipo de Variable Incorrecto", "Todas las variables seleccionadas (dependiente e independientes) deben ser numéricas para la regresión.");
            return;
        }


        try {
            Map<String, Object> results = estadisticaService.performMultipleLinearRegression(
                    currentDataset, dependentVarName, independentVarNames);

            displayRegressionResults(results);

        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.ERROR, "Error en Regresión", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error inesperado al realizar la regresión: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void displayRegressionResults(Map<String, Object> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>Resultados de la Regresión Lineal Múltiple</h2>");
        sb.append("<p><b>Dataset:</b> ").append(currentDataset.getName()).append("</p>");
        sb.append("<p><b>Variable Dependiente (Y):</b> ").append(dependentVariableComboBox.getSelectionModel().getSelectedItem()).append("</p>");
        sb.append("<p><b>Variables Independientes (X):</b> ").append(String.join(", ", independentVariablesListView.getItems())).append("</p>");
        sb.append("<p><b>Número de Observaciones:</b> ").append(results.get("Num Observations")).append("</p>");
        sb.append("<p><b>Número de Variables Independientes:</b> ").append(results.get("Num Independent Variables")).append("</p>");
        sb.append("<hr>");

        sb.append("<h3>Estadísticas del Modelo:</h3>");
        sb.append(String.format("<p><b>R-cuadrado (R²):</b> %.4f</p>", (Double) results.get("R-Squared")));
        sb.append(String.format("<p><b>R-cuadrado Ajustado:</b> %.4f</p>", (Double) results.get("Adjusted R-Squared")));
        sb.append(String.format("<p><b>Error Estándar de la Regresión (Sigma):</b> %.4f</p>", (Double) results.get("Regression Standard Error (Sigma)")));
        sb.append(String.format("<p><b>Suma de Cuadrados de la Regresión (SSR):</b> %.4f</p>", (Double) results.get("Regression Sum of Squares (SSR)")));
        sb.append(String.format("<p><b>Suma de Cuadrados de los Residuales (SSE):</b> %.4f</p>", (Double) results.get("Residual Sum of Squares (SSE)")));
        sb.append(String.format("<p><b>Suma Total de Cuadrados (SST):</b> %.4f</p>", (Double) results.get("Total Sum of Squares (SST)")));
        sb.append("<hr>");

        sb.append("<h3>Coeficientes:</h3>");
        List<String> coefNames = (List<String>) results.get("CoefficientNames");
        List<Double> coefficients = (List<Double>) results.get("Coefficients");

        for (int i = 0; i < coefNames.size(); i++) {
            sb.append(String.format("<p><b>%s:</b> %.4f</p>", coefNames.get(i), coefficients.get(i)));
        }
        sb.append("<hr>");

        resultsLabel.setText(sb.toString()); // Asegúrate de que resultsLabel sea un Label en el FXML
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean isNumeric(String type) {
        return "NUMERIC".equalsIgnoreCase(type) || "QUANTITATIVE".equalsIgnoreCase(type);
    }
}