package org.example.gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import org.example.dao.DatasetDAO;
import org.example.model.Dataset;
import org.example.model.Observation;
import org.example.model.Variable;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class DataManagerController {

    @FXML public ComboBox<Dataset> datasetComboBox; // CAMBIADO A PUBLIC
    @FXML private TextField newDatasetNameField;
    @FXML private TableView<ObservableList<String>> observationsTable;
    @FXML private TableView<Variable> variablesTable;
    @FXML private TableColumn<Variable, String> varNameColumn;
    @FXML private TableColumn<Variable, String> varTypeColumn;

    private DatasetDAO datasetDAO;
    private Dataset currentDataset;
    private MainLayoutController mainLayoutController; // Referencia al controlador principal

    @FXML
    public void initialize() {
        datasetDAO = new DatasetDAO();
        loadDatasetsIntoComboBox();

        // Configurar la tabla de variables
        varNameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getName()));
        varTypeColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getType()));

        // Habilitar edición de tipo de variable
        varTypeColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        varTypeColumn.setOnEditCommit(event -> {
            Variable variable = event.getRowValue();
            String oldType = variable.getType();
            String newType = event.getNewValue();
            if (!isValidVariableType(newType)) { // Validación añadida
                showAlert(Alert.AlertType.ERROR, "Tipo de Variable Inválido", "El tipo '" + newType + "' no es válido. Tipos permitidos: NUMERIC, TEXT, QUALITATIVE, QUANTITATIVE, BOOLEAN.");
                variable.setType(oldType); // Revertir al tipo anterior
                variablesTable.refresh(); // Refrescar la tabla para mostrar el valor original
                return;
            }
            variable.setType(newType);
            // Opcional: Persistir el cambio de tipo inmediatamente o al guardar el dataset
        });

        // Configurar la tabla de observaciones (se construirá dinámicamente)
        observationsTable.setEditable(true);

        // Cuando cambia la selección del dataset, cargar sus datos
        datasetComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldDataset, newDataset) -> {
            if (newDataset != null) {
                loadDataset(newDataset);
                if (mainLayoutController != null) {
                    mainLayoutController.updateActiveDataset(newDataset); // Notificar al controlador principal
                }
            } else {
                // Si no hay dataset seleccionado (ej. después de borrar el último)
                if (mainLayoutController != null) {
                    mainLayoutController.updateActiveDataset(null);
                }
            }
        });

        // Permitir edición en la tabla de observaciones
        observationsTable.setEditable(true);

        // Inicializar o cargar el primer dataset al inicio
        if (datasetComboBox.getItems().isEmpty()) {
            handleNewDataset(); // Crea un nuevo dataset vacío si no hay ninguno
        } else {
            datasetComboBox.getSelectionModel().selectFirst();
        }
    }

    // Setter para el controlador principal
    public void setMainLayoutController(MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
        // Al configurar el controlador principal, notifica el dataset actual
        if (currentDataset != null) {
            mainLayoutController.updateActiveDataset(currentDataset);
        } else if (datasetComboBox.getSelectionModel().getSelectedItem() != null) {
            mainLayoutController.updateActiveDataset(datasetComboBox.getSelectionModel().getSelectedItem());
        } else {
            // Si no hay dataset, fuerza la creación de uno nuevo para que haya algo activo
            handleNewDataset(); // Esto ya notifica al MainLayoutController
        }
    }

    private void loadDatasetsIntoComboBox() {
        try {
            List<Dataset> datasets = datasetDAO.getAllDatasets();
            datasetComboBox.setItems(FXCollections.observableArrayList(datasets));
            datasetComboBox.setConverter(new StringConverter<Dataset>() {
                @Override
                public String toString(Dataset dataset) {
                    return dataset != null ? dataset.getName() : "";
                }

                @Override
                public Dataset fromString(String string) {
                    return null; // No necesitamos convertir de String a Dataset para ComboBox de solo lectura
                }
            });
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de BD", "No se pudieron cargar los datasets: " + e.getMessage());
        }
    }

    private void loadDataset(Dataset dataset) {
        try {
            // Cargar variables y observaciones completas del dataset
            this.currentDataset = datasetDAO.getDatasetById(dataset.getId());
            if (this.currentDataset == null) {
                showAlert(Alert.AlertType.ERROR, "Error de Carga", "El dataset seleccionado no pudo ser cargado.");
                return;
            }
            newDatasetNameField.setText(this.currentDataset.getName());

            // Limpiar y configurar columnas de variables
            variablesTable.setItems(FXCollections.observableArrayList(currentDataset.getVariables()));

            // Construir columnas dinámicamente para la tabla de observaciones
            buildObservationsTable(currentDataset);

            // Cargar datos en la tabla de observaciones
            populateObservationsTable(currentDataset);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de BD", "Error al cargar el dataset: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void buildObservationsTable(Dataset dataset) {
        observationsTable.getColumns().clear();

        // Columna para el índice de la observación (fila)
        TableColumn<ObservableList<String>, String> indexColumn = new TableColumn<>("Índice");
        indexColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().get(0)));
        indexColumn.setPrefWidth(60);
        observationsTable.getColumns().add(indexColumn);

        // Columnas para cada variable
        int i = 1; // Índice para los valores en la lista observable (0 es para el índice de observación)
        for (Variable var : dataset.getVariables()) {
            final int colIndex = i;
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(var.getName());
            column.setCellValueFactory(param -> {
                if (param.getValue() != null && colIndex < param.getValue().size()) {
                    return new ReadOnlyStringWrapper(param.getValue().get(colIndex));
                } else {
                    return new ReadOnlyStringWrapper("");
                }
            });

            // Habilitar edición de celda
            column.setCellFactory(TextFieldTableCell.forTableColumn());
            column.setOnEditCommit(event -> {
                int rowIndex = observationsTable.getItems().indexOf(event.getRowValue());
                if (rowIndex >= 0) {
                    // Obtener el índice real de la observación de la primera columna (el índice)
                    int obsId = Integer.parseInt(event.getRowValue().get(0));
                    String variableName = event.getTableColumn().getText(); // Nombre de la variable (columna)
                    String newStringValue = event.getNewValue();

                    // Intentar convertir el valor según el tipo de variable
                    Object newValue = convertValue(newStringValue, dataset.getVariableByName(variableName).getType());

                    if (newValue != null || newStringValue.isEmpty()) { // Permitir vacíos para limpiar
                        currentDataset.setValue(obsId, variableName, newValue);
                        // Actualizar la vista de la tabla
                        event.getRowValue().set(colIndex, newStringValue); // Mantener el string original
                    } else {
                        // Si la conversión falla, revertir la celda a su valor original
                        populateObservationsTable(currentDataset); // Más fácil repoblar la tabla completa
                    }
                }
            });
            observationsTable.getColumns().add(column);
            i++;
        }
    }

    private void populateObservationsTable(Dataset dataset) {
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
        List<Variable> variables = dataset.getVariables();

        // Ordenar observaciones por índice para asegurar el orden de las filas
        List<Map.Entry<Integer, Observation>> sortedObservations = new ArrayList<>(dataset.getObservations().entrySet());
        sortedObservations.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, Observation> entry : sortedObservations) {
            Integer obsIndex = entry.getKey();
            Observation observation = entry.getValue();
            ObservableList<String> row = FXCollections.observableArrayList();
            row.add(String.valueOf(obsIndex)); // Primer elemento es el índice de la observación

            for (Variable var : variables) {
                Object value = observation.getValue(var.getName());
                row.add(value != null ? value.toString() : "");
            }
            data.add(row);
        }
        observationsTable.setItems(data);
    }

    // --- Handlers de acciones de botones ---

    @FXML
    public void handleNewDataset() { // CAMBIADO A PUBLIC
        currentDataset = new Dataset("Nuevo Dataset " + (datasetComboBox.getItems().size() + 1));
        newDatasetNameField.setText(currentDataset.getName());
        variablesTable.setItems(FXCollections.observableArrayList(currentDataset.getVariables()));
        buildObservationsTable(currentDataset);
        populateObservationsTable(currentDataset);
        showAlert(Alert.AlertType.INFORMATION, "Dataset Creado", "Nuevo dataset '" + currentDataset.getName() + "' listo para edición.");

        // Notificar al MainLayoutController que hay un nuevo dataset activo
        if (mainLayoutController != null) {
            mainLayoutController.updateActiveDataset(currentDataset);
        }
    }

    @FXML
    public void handleSaveDataset() { // CAMBIADO A PUBLIC
        if (currentDataset == null) {
            showAlert(Alert.AlertType.WARNING, "Guardar Dataset", "No hay un dataset activo para guardar.");
            return;
        }
        String newName = newDatasetNameField.getText();
        if (newName == null || newName.trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Guardar Dataset", "El nombre del dataset no puede estar vacío.");
            return;
        }
        currentDataset.setName(newName);

        try {
            // Guardar el dataset y sus variables
            datasetDAO.saveDataset(currentDataset);

            // Guardar las observaciones
            // Esto es crucial: se elimina todo lo anterior y se guarda lo nuevo para evitar inconsistencias
            datasetDAO.deleteObservationsForDataset(currentDataset.getId());
            for (Map.Entry<Integer, Observation> entry : currentDataset.getObservations().entrySet()) {
                datasetDAO.saveObservation(currentDataset.getId(), entry.getKey(), entry.getValue());
            }

            showAlert(Alert.AlertType.INFORMATION, "Guardar Dataset", "Dataset '" + currentDataset.getName() + "' guardado exitosamente.");
            loadDatasetsIntoComboBox(); // Recargar la lista de datasets para reflejar cambios/nuevos
            datasetComboBox.getSelectionModel().select(currentDataset); // Seleccionar el dataset guardado

            // Notificar al MainLayoutController que el dataset activo ha cambiado/guardado
            if (mainLayoutController != null) {
                mainLayoutController.updateActiveDataset(currentDataset);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error de BD", "Error al guardar el dataset: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDeleteDataset() {
        Dataset selectedDataset = datasetComboBox.getSelectionModel().getSelectedItem();
        if (selectedDataset == null) {
            showAlert(Alert.AlertType.WARNING, "Eliminar Dataset", "Por favor, selecciona un dataset para eliminar.");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "¿Estás seguro de que quieres eliminar el dataset '" + selectedDataset.getName() + "'?", ButtonType.YES, ButtonType.NO);
        confirmAlert.setHeaderText("Confirmar eliminación");
        Optional<ButtonType> result = confirmAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                datasetDAO.deleteDataset(selectedDataset.getId());
                showAlert(Alert.AlertType.INFORMATION, "Eliminar Dataset", "Dataset '" + selectedDataset.getName() + "' eliminado exitosamente.");
                loadDatasetsIntoComboBox();
                if (datasetComboBox.getItems().isEmpty()) {
                    handleNewDataset(); // Crea un nuevo dataset vacío si no quedan
                    if (mainLayoutController != null) { // Notifica el nuevo dataset vacío
                        mainLayoutController.updateActiveDataset(currentDataset);
                    }
                } else {
                    datasetComboBox.getSelectionModel().selectFirst();
                    if (mainLayoutController != null) { // Notifica el nuevo dataset activo
                        mainLayoutController.updateActiveDataset(datasetComboBox.getSelectionModel().getSelectedItem());
                    }
                }
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Error de BD", "Error al eliminar el dataset: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleLoadDataset() {
        Dataset selectedDataset = datasetComboBox.getSelectionModel().getSelectedItem();
        if (selectedDataset != null) {
            loadDataset(selectedDataset);
            if (mainLayoutController != null) { // Notificar al controlador principal
                mainLayoutController.updateActiveDataset(currentDataset);
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Cargar Dataset", "Por favor, selecciona un dataset para cargar.");
        }
    }

    @FXML
    private void handleAddVariable() {
        if (currentDataset == null) {
            showAlert(Alert.AlertType.WARNING, "Añadir Variable", "Crea o carga un dataset primero.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("Var" + (currentDataset.getVariableCount() + 1));
        dialog.setTitle("Añadir Nueva Variable");
        dialog.setHeaderText("Nombre de la nueva variable:");
        dialog.setContentText("Nombre:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String varName = result.get().trim();
            // Verificar si el nombre ya existe
            if (currentDataset.getVariableByName(varName) != null) {
                showAlert(Alert.AlertType.ERROR, "Error", "Ya existe una variable con ese nombre.");
                return;
            }
            Variable newVar = new Variable(varName, "NUMERIC"); // Por defecto numérica
            currentDataset.addVariable(newVar);
            variablesTable.setItems(FXCollections.observableArrayList(currentDataset.getVariables()));
            buildObservationsTable(currentDataset); // Reconstruir tabla de observaciones
            populateObservationsTable(currentDataset); // Volver a poblar con la nueva columna
        }
    }

    @FXML
    private void handleRemoveVariable() {
        Variable selectedVariable = variablesTable.getSelectionModel().getSelectedItem();
        if (selectedVariable == null) {
            showAlert(Alert.AlertType.WARNING, "Eliminar Variable", "Por favor, selecciona una variable para eliminar.");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "¿Estás seguro de que quieres eliminar la variable '" + selectedVariable.getName() + "' y todos sus datos?", ButtonType.YES, ButtonType.NO);
        confirmAlert.setHeaderText("Confirmar eliminación");
        Optional<ButtonType> result = confirmAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            if (currentDataset != null) {
                currentDataset.removeVariable(selectedVariable.getName());
                variablesTable.setItems(FXCollections.observableArrayList(currentDataset.getVariables()));
                buildObservationsTable(currentDataset); // Reconstruir tabla de observaciones
                populateObservationsTable(currentDataset); // Volver a poblar
            }
        }
    }

    @FXML
    private void handleAddRow() {
        if (currentDataset == null) {
            showAlert(Alert.AlertType.WARNING, "Añadir Fila", "Crea o carga un dataset primero.");
            return;
        }
        Observation newObservation = new Observation();
        int newIndex = currentDataset.addObservation(newObservation); // El dataset asigna el índice

        // Añadir una nueva fila a la tabla visible
        ObservableList<String> newRow = FXCollections.observableArrayList();
        newRow.add(String.valueOf(newIndex)); // El índice de la observación
        for (int i = 0; i < currentDataset.getVariableCount(); i++) {
            newRow.add(""); // Valores vacíos para las nuevas celdas
        }
        observationsTable.getItems().add(newRow);
        observationsTable.scrollTo(newRow); // Desplazarse a la nueva fila
    }

    @FXML
    private void handleRemoveRow() {
        ObservableList<String> selectedRow = observationsTable.getSelectionModel().getSelectedItem();
        if (selectedRow == null || currentDataset == null) {
            showAlert(Alert.AlertType.WARNING, "Eliminar Fila", "Por favor, selecciona una fila para eliminar.");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "¿Estás seguro de que quieres eliminar la fila seleccionada?", ButtonType.YES, ButtonType.NO);
        confirmAlert.setHeaderText("Confirmar eliminación");
        Optional<ButtonType> result = confirmAlert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            int obsIndexToRemove = Integer.parseInt(selectedRow.get(0)); // Obtener el índice real de la observación
            currentDataset.removeObservation(obsIndexToRemove);
            populateObservationsTable(currentDataset); // Repoblar la tabla para reflejar los cambios
        }
    }

    // --- Métodos de Importación/Exportación (vacíos por ahora, necesitan implementación) ---
    @FXML
    private void handleImportCSV() {
        showAlert(Alert.AlertType.INFORMATION, "Importar CSV", "Funcionalidad no implementada aún.");
    }

    @FXML
    private void handleExportCSV() {
        showAlert(Alert.AlertType.INFORMATION, "Exportar CSV", "Funcionalidad no implementada aún.");
    }

    @FXML
    private void handleImportJSON() {
        showAlert(Alert.AlertType.INFORMATION, "Importar JSON", "Funcionalidad no implementada aún.");
    }

    @FXML
    private void handleExportJSON() {
        showAlert(Alert.AlertType.INFORMATION, "Exportar JSON", "Funcionalidad no implementada aún.");
    }

    // --- Utilidades ---
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Valida que el tipo de variable sea uno de los esperados
    private boolean isValidVariableType(String type) {
        return "NUMERIC".equalsIgnoreCase(type) ||
                "TEXT".equalsIgnoreCase(type) ||
                "QUALITATIVE".equalsIgnoreCase(type) ||
                "QUANTITATIVE".equalsIgnoreCase(type) ||
                "BOOLEAN".equalsIgnoreCase(type);
    }

    private Object convertValue(String value, String type) {
        if (value == null || value.trim().isEmpty()) {
            return null; // O dejar vacío si es tu política
        }
        switch (type.toUpperCase()) {
            case "NUMERIC":
            case "QUANTITATIVE":
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Error de Conversión", "El valor '" + value + "' no es un número válido para una variable " + type + ".");
                    return null; // Devuelve null si no se puede convertir
                }
            case "BOOLEAN":
                return Boolean.parseBoolean(value);
            case "TEXT":
            case "QUALITATIVE":
            default:
                return value;
        }
    }
}