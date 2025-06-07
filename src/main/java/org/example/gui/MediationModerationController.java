package org.example.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import org.example.model.Dataset;
import org.example.model.Observation;
import org.example.model.Variable;
import org.example.service.EstadisticaService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MediationModerationController {

    @FXML private Label activeDatasetLabel;
    @FXML private ListView<String> availableVariablesListView;
    @FXML private ComboBox<String> predictorVariableComboBox; // X
    @FXML private ComboBox<String> mediatorVariableComboBox;    // M
    @FXML private ComboBox<String> moderatorVariableComboBox;   // W
    @FXML private ComboBox<String> outcomeVariableComboBox;     // Y
    @FXML private Label resultsLabel;

    private Dataset currentDataset;
    private EstadisticaService estadisticaService;

    @FXML
    public void initialize() {
        estadisticaService = new EstadisticaService();

        // Inicializar los ListView con selección múltiple
        availableVariablesListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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
            predictorVariableComboBox.getItems().clear();
            mediatorVariableComboBox.getItems().clear();
            moderatorVariableComboBox.getItems().clear();
            outcomeVariableComboBox.getItems().clear();
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
            predictorVariableComboBox.setItems(variableNames);
            mediatorVariableComboBox.setItems(variableNames);
            moderatorVariableComboBox.setItems(variableNames);
            outcomeVariableComboBox.setItems(variableNames);
        }
    }

    private void clearSelectionsAndResults() {
        predictorVariableComboBox.getSelectionModel().clearSelection();
        mediatorVariableComboBox.getSelectionModel().clearSelection();
        moderatorVariableComboBox.getSelectionModel().clearSelection();
        outcomeVariableComboBox.getSelectionModel().clearSelection();
        resultsLabel.setText("");
    }

    @FXML
    private void handleClearSelections() {
        clearSelectionsAndResults();
    }

    @FXML
    private void handlePerformMediation() {
        if (currentDataset == null) {
            showAlert(Alert.AlertType.ERROR, "Error de Datos", "No hay un dataset activo. Carga o crea uno en 'Gestión de Datos'.");
            return;
        }

        String xVar = predictorVariableComboBox.getSelectionModel().getSelectedItem();
        String mVar = mediatorVariableComboBox.getSelectionModel().getSelectedItem();
        String yVar = outcomeVariableComboBox.getSelectionModel().getSelectedItem();

        if (xVar == null || mVar == null || yVar == null || xVar.isEmpty() || mVar.isEmpty() || yVar.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Configuración Incompleta", "Por favor, selecciona las variables X (Predictora), M (Mediadora) y Y (Resultado).");
            return;
        }

        if (xVar.equals(mVar) || xVar.equals(yVar) || mVar.equals(yVar)) {
            showAlert(Alert.AlertType.ERROR, "Variables Duplicadas", "Las variables Predictora, Mediadora y Resultado deben ser diferentes.");
            return;
        }

        // Verificar que las variables seleccionadas existan en el dataset y sean numéricas
        if (currentDataset.getVariableByName(xVar) == null || !isNumeric(currentDataset.getVariableByName(xVar).getType()) ||
                currentDataset.getVariableByName(mVar) == null || !isNumeric(currentDataset.getVariableByName(mVar).getType()) ||
                currentDataset.getVariableByName(yVar) == null || !isNumeric(currentDataset.getVariableByName(yVar).getType())) {
            showAlert(Alert.AlertType.ERROR, "Error de Variables", "Asegúrate de que todas las variables seleccionadas existen y son numéricas.");
            return;
        }

        try {
            // Regresión 1: X -> M (a-path)
            Map<String, Object> resultsAM = estadisticaService.performMultipleLinearRegression(currentDataset, mVar, Arrays.asList(xVar));
            double aPath = (Double) ((List<Double>) resultsAM.get("Coefficients")).get(1); // Coeficiente de X (beta1)

            // Regresión 2: X, M -> Y (b-path y c'-path)
            Map<String, Object> resultsBMCM = estadisticaService.performMultipleLinearRegression(currentDataset, yVar, Arrays.asList(xVar, mVar));
            double bPath = (Double) ((List<Double>) resultsBMCM.get("Coefficients")).get(2); // Coeficiente de M (beta2)
            double cPrimePath = (Double) ((List<Double>) resultsBMCM.get("Coefficients")).get(1); // Coeficiente de X (beta1)

            // Regresión 3: X -> Y (c-path total effect)
            Map<String, Object> resultsCY = estadisticaService.performMultipleLinearRegression(currentDataset, yVar, Arrays.asList(xVar));
            double cPathTotal = (Double) ((List<Double>) resultsCY.get("Coefficients")).get(1); // Coeficiente de X (beta1)

            double indirectEffect = aPath * bPath;
            double totalEffectCheck = cPrimePath + indirectEffect; // Debería ser similar a cPathTotal

            StringBuilder sb = new StringBuilder();
            sb.append("Resultados del Análisis de Mediación\n\n");
            sb.append("Predictora (X): ").append(xVar).append("\n");
            sb.append("Mediadora (M): ").append(mVar).append("\n");
            sb.append("Resultado (Y): ").append(yVar).append("\n\n");

            sb.append("Efectos:\n");
            sb.append(String.format("Efecto Indirecto (a * b): %.4f (%.4f * %.4f)\n", indirectEffect, aPath, bPath));
            sb.append(String.format("Efecto Directo (c'): %.4f\n", cPrimePath));
            sb.append(String.format("Efecto Total (c): %.4f\n", cPathTotal));
            sb.append(String.format("Suma Directo + Indirecto (c' + a*b): %.4f\n\n", totalEffectCheck));

            sb.append("Regresiones Individuales:\n");
            sb.append("Regresión 1: M = Intercepto + a*X\n");
            appendRegressionSummary(sb, resultsAM, mVar, Arrays.asList(xVar));
            sb.append("\nRegresión 2: Y = Intercepto + c'*X + b*M\n");
            appendRegressionSummary(sb, resultsBMCM, yVar, Arrays.asList(xVar, mVar));
            sb.append("\nRegresión 3: Y = Intercepto + c*X (Efecto Total)\n");
            appendRegressionSummary(sb, resultsCY, yVar, Arrays.asList(xVar));

            resultsLabel.setText(sb.toString());

        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.ERROR, "Error en Mediación", e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            showAlert(Alert.AlertType.ERROR, "Error en Coeficientes", "Error al acceder a los coeficientes. Asegúrate de que las variables seleccionadas son válidas y hay suficientes datos numéricos. " + e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error inesperado al realizar el análisis de mediación: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handlePerformModeration() {
        if (currentDataset == null) {
            showAlert(Alert.AlertType.ERROR, "Error de Datos", "No hay un dataset activo. Carga o crea uno en 'Gestión de Datos'.");
            return;
        }

        String xVar = predictorVariableComboBox.getSelectionModel().getSelectedItem();
        String wVar = moderatorVariableComboBox.getSelectionModel().getSelectedItem();
        String yVar = outcomeVariableComboBox.getSelectionModel().getSelectedItem();

        if (xVar == null || wVar == null || yVar == null || xVar.isEmpty() || wVar.isEmpty() || yVar.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Configuración Incompleta", "Por favor, selecciona las variables X (Predictora), W (Moderadora) y Y (Resultado).");
            return;
        }

        if (xVar.equals(wVar) || xVar.equals(yVar) || wVar.equals(yVar)) {
            showAlert(Alert.AlertType.ERROR, "Variables Duplicadas", "Las variables Predictora, Moderadora y Resultado deben ser diferentes.");
            return;
        }

        // Verificar que las variables seleccionadas existan en el dataset y sean numéricas
        if (currentDataset.getVariableByName(xVar) == null || !isNumeric(currentDataset.getVariableByName(xVar).getType()) ||
                currentDataset.getVariableByName(wVar) == null || !isNumeric(currentDataset.getVariableByName(wVar).getType()) ||
                currentDataset.getVariableByName(yVar) == null || !isNumeric(currentDataset.getVariableByName(yVar).getType())) {
            showAlert(Alert.AlertType.ERROR, "Error de Variables", "Asegúrate de que todas las variables seleccionadas existen y son numéricas.");
            return;
        }

        try {
            // Construir la variable de interacción X*W
            String interactionVarName = xVar + "*" + wVar;
            Dataset tempDataset = cloneDatasetForAnalysis(currentDataset); // Clonar para no modificar el original
            
            // Calcular y añadir la variable de interacción si no existe
            if (tempDataset.getVariableByName(interactionVarName) == null) {
                Variable interactionVar = new Variable(interactionVarName, "NUMERIC");
                tempDataset.addVariable(interactionVar);
            }

            // Luego, calcular los valores para la variable de interacción
            for (Map.Entry<Integer, Observation> entry : tempDataset.getObservations().entrySet()) {
                Object xVal = entry.getValue().getValue(xVar);
                Object wVal = entry.getValue().getValue(wVar);
                if (xVal instanceof Number && wVal instanceof Number) {
                    double product = ((Number) xVal).doubleValue() * ((Number) wVal).doubleValue();
                    tempDataset.setValue(entry.getKey(), interactionVarName, product);
                } else {
                    tempDataset.setValue(entry.getKey(), interactionVarName, null);
                }
            }

            // Regresión: Y = Intercepto + b1*X + b2*W + b3*(X*W)
            Map<String, Object> results = estadisticaService.performMultipleLinearRegression(
                    tempDataset, yVar, Arrays.asList(xVar, wVar, interactionVarName));

            List<String> coefNames = (List<String>) results.get("CoefficientNames");
            List<Double> coefficients = (List<Double>) results.get("Coefficients");

            // Encontrar el coeficiente de interacción
            Double interactionCoef = null;
            int interactionIndex = -1;
            for(int i = 0; i < coefNames.size(); i++) {
                if (coefNames.get(i).equals(interactionVarName)) {
                    interactionIndex = i;
                    break;
                }
            }

            if (interactionIndex != -1) {
                interactionCoef = coefficients.get(interactionIndex);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Resultados del Análisis de Moderación\n\n");
            sb.append("Predictora (X): ").append(xVar).append("\n");
            sb.append("Moderadora (W): ").append(wVar).append("\n");
            sb.append("Resultado (Y): ").append(yVar).append("\n\n");

            sb.append("Regresión: Y = Intercepto + b1*X + b2*W + b3*(X*W)\n");
            appendRegressionSummary(sb, results, yVar, Arrays.asList(xVar, wVar, interactionVarName));
            sb.append("\n");

            if (interactionCoef != null) {
                sb.append(String.format("Coeficiente de Interacción (%s*%s): %.4f\n\n", xVar, wVar, interactionCoef));
                if (Math.abs(interactionCoef) > 0.001) {
                    sb.append("El efecto del predictor (X) sobre el resultado (Y) parece estar moderado por la variable moderadora (W).\n");
                    sb.append("Esto significa que la relación entre X y Y cambia en función de los niveles de W.\n");
                } else {
                    sb.append("El efecto de interacción no parece ser significativo (coeficiente muy cercano a cero).\n");
                    sb.append("Es probable que el efecto del predictor (X) sobre el resultado (Y) no esté moderado por la variable moderadora (W).\n");
                }
            } else {
                sb.append("No se pudo calcular el coeficiente de interacción para '" + interactionVarName + "'.\n");
            }

            resultsLabel.setText(sb.toString());

        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.ERROR, "Error en Moderación", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error Inesperado", "Ocurrió un error inesperado al realizar el análisis de moderación: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Métodos de Utilidad ---

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void appendRegressionSummary(StringBuilder sb, Map<String, Object> results, String dependentVar, List<String> independentVars) {
        sb.append("R-cuadrado: ").append(String.format("%.4f", (Double) results.get("R-Squared"))).append("\n");
        sb.append("Coeficientes:\n");
        List<String> coefNames = (List<String>) results.get("CoefficientNames");
        List<Double> coefficients = (List<Double>) results.get("Coefficients");
        for (int i = 0; i < coefNames.size(); i++) {
            sb.append(String.format("  %s: %.4f\n", coefNames.get(i), coefficients.get(i)));
        }
    }

    /**
     * Clona un dataset para no modificar el original al añadir variables de interacción.
     * Realiza una copia profunda de variables y observaciones.
     */
    private Dataset cloneDatasetForAnalysis(Dataset originalDataset) {
        // Clonar dataset
        Dataset clonedDataset = new Dataset(originalDataset.getId(), originalDataset.getName() + " (Clon)");

        // Clonar variables (creamos nuevas instancias de Variable)
        for (Variable var : originalDataset.getVariables()) {
            clonedDataset.addVariable(new Variable(var.getId(), var.getName(), var.getType()));
        }

        // Clonar observaciones con sus valores (copia profunda de los mapas de valores)
        Map<Integer, Observation> clonedObservations = new HashMap<>();
        for (Map.Entry<Integer, Observation> entry : originalDataset.getObservations().entrySet()) {
            Observation originalObs = entry.getValue();
            Observation clonedObs = new Observation();
            // Clonar el mapa de valores de la observación
            clonedObs.setValues(new HashMap<>(originalObs.getValues()));
            clonedObservations.put(entry.getKey(), clonedObs);
        }
        clonedDataset.setObservations(clonedObservations);

        return clonedDataset;
    }

    private boolean isNumeric(String type) {
        return "NUMERIC".equalsIgnoreCase(type) || "QUANTITATIVE".equalsIgnoreCase(type);
    }
}