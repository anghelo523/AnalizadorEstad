package org.example.dao;

import org.example.model.Dataset;
import org.example.model.Observation;
import org.example.model.Variable;
import org.example.util.SQLiteConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatasetDAO {

    public void saveDataset(Dataset dataset) throws SQLException {
        String sqlDataset = "INSERT OR REPLACE INTO datasets(id, name) VALUES(?, ?)";
        String sqlVariable = "INSERT OR REPLACE INTO variables(id, dataset_id, name, type) VALUES(?, ?, ?, ?)";
        String sqlObservationValue = "INSERT OR REPLACE INTO observation_values(observation_id, dataset_id, variable_id, value_numeric, value_text) VALUES(?, ?, ?, ?, ?)";

        try (Connection conn = SQLiteConnection.connect()) {
            conn.setAutoCommit(false); // Start transaction

            // 1. Save/Update Dataset
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDataset)) {
                if (dataset.getId() == 0) { // New dataset, generate ID
                    try (Statement stmt = conn.createStatement()) {
                        ResultSet rs = stmt.executeQuery("SELECT MAX(id) + 1 FROM datasets");
                        if (rs.next() && rs.getInt(1) > 0) { // Ensure ID is positive
                            dataset.setId(rs.getInt(1));
                        } else {
                            dataset.setId(1); // First dataset
                        }
                    }
                }
                pstmt.setInt(1, dataset.getId());
                pstmt.setString(2, dataset.getName());
                pstmt.executeUpdate();
            }

            // 2. Save/Update Variables
            // First, delete old variables for this dataset to avoid orphans/duplicates
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM variables WHERE dataset_id = ?")) {
                pstmt.setInt(1, dataset.getId());
                pstmt.executeUpdate();
            }
            // Then insert current variables
            for (Variable var : dataset.getVariables()) {
                try (PreparedStatement pstmt = conn.prepareStatement(sqlVariable)) {
                    if (var.getId() == 0) { // New variable, generate ID
                        try (Statement stmt = conn.createStatement()) {
                            ResultSet rs = stmt.executeQuery("SELECT MAX(id) + 1 FROM variables");
                            if (rs.next() && rs.getInt(1) > 0) { // Ensure ID is positive
                                var.setId(rs.getInt(1));
                            } else {
                                var.setId(1); // First variable
                            }
                        }
                    }
                    pstmt.setInt(1, var.getId());
                    pstmt.setInt(2, dataset.getId());
                    pstmt.setString(3, var.getName());
                    pstmt.setString(4, var.getType());
                    pstmt.executeUpdate();
                }
            }

            // 3. Save/Update Observation Values
            // Delete existing observations for this dataset to ensure fresh data
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM observation_values WHERE dataset_id = ?")) {
                pstmt.setInt(1, dataset.getId());
                pstmt.executeUpdate();
            }

            // Insert new observations and their values
            String sqlInsertObservation = "INSERT OR REPLACE INTO observations(id, dataset_id) VALUES(?, ?)";
            for (Map.Entry<Integer, Observation> obsEntry : dataset.getObservations().entrySet()) {
                Integer obsIndex = obsEntry.getKey();
                Observation observation = obsEntry.getValue();

                // Insert observation row (if not exists)
                try (PreparedStatement pstmt = conn.prepareStatement(sqlInsertObservation)) {
                    pstmt.setInt(1, obsIndex); // Use the observation index as its ID
                    pstmt.setInt(2, dataset.getId());
                    pstmt.executeUpdate();
                }

                // Insert values for this observation
                for (Map.Entry<String, Object> valueEntry : observation.getValues().entrySet()) {
                    String varName = valueEntry.getKey();
                    Object value = valueEntry.getValue();
                    Variable variable = dataset.getVariableByName(varName); // Get variable to find its ID

                    if (variable == null) {
                        System.err.println("Warning: Variable '" + varName + "' not found in dataset, skipping value.");
                        continue;
                    }

                    try (PreparedStatement pstmt = conn.prepareStatement(sqlObservationValue)) {
                        pstmt.setInt(1, obsIndex);
                        pstmt.setInt(2, dataset.getId());
                        pstmt.setInt(3, variable.getId());

                        if (value instanceof Number) {
                            pstmt.setDouble(4, ((Number) value).doubleValue());
                            pstmt.setNull(5, Types.VARCHAR);
                        } else if (value != null) {
                            pstmt.setNull(4, Types.DOUBLE);
                            pstmt.setString(5, value.toString());
                        } else {
                            pstmt.setNull(4, Types.DOUBLE);
                            pstmt.setNull(5, Types.VARCHAR);
                        }
                        pstmt.executeUpdate();
                    }
                }
            }
            conn.commit(); // Commit transaction
        } catch (SQLException e) {
            // If anything goes wrong, rollback
            System.err.println("Error saving dataset: " + e.getMessage());
            throw e;
        }
    }

    public Dataset getDatasetById(int datasetId) throws SQLException {
        Dataset dataset = null;
        String sqlDataset = "SELECT id, name FROM datasets WHERE id = ?";
        String sqlVariables = "SELECT id, name, type FROM variables WHERE dataset_id = ? ORDER BY id";
        String sqlObservationValues = "SELECT ov.observation_id, v.name AS var_name, ov.value_numeric, ov.value_text " +
                "FROM observation_values ov " +
                "JOIN variables v ON ov.variable_id = v.id " +
                "WHERE ov.dataset_id = ? ORDER BY ov.observation_id, v.id";

        try (Connection conn = SQLiteConnection.connect()) {
            // Get Dataset details
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDataset)) {
                pstmt.setInt(1, datasetId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    dataset = new Dataset(rs.getInt("id"), rs.getString("name"));
                } else {
                    return null; // Dataset not found
                }
            }

            // Get Variables
            if (dataset != null) {
                try (PreparedStatement pstmt = conn.prepareStatement(sqlVariables)) {
                    pstmt.setInt(1, datasetId);
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        dataset.addVariable(new Variable(rs.getInt("id"), rs.getString("name"), rs.getString("type")));
                    }
                }

                // Get Observation Values
                try (PreparedStatement pstmt = conn.prepareStatement(sqlObservationValues)) {
                    pstmt.setInt(1, datasetId);
                    ResultSet rs = pstmt.executeQuery();
                    Map<Integer, Observation> observations = new HashMap<>();
                    while (rs.next()) {
                        int obsId = rs.getInt("observation_id");
                        String varName = rs.getString("var_name");
                        Object value;
                        if (rs.getObject("value_numeric") != null) {
                            value = rs.getDouble("value_numeric");
                        } else {
                            value = rs.getString("value_text");
                        }

                        observations.computeIfAbsent(obsId, k -> new Observation()).addValue(varName, value);
                    }
                    dataset.setObservations(observations);
                }
            }
        }
        return dataset;
    }

    public List<Dataset> getAllDatasets() throws SQLException {
        List<Dataset> datasets = new ArrayList<>();
        String sql = "SELECT id, name FROM datasets";
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                datasets.add(new Dataset(rs.getInt("id"), rs.getString("name")));
            }
        }
        return datasets;
    }

    public void deleteDataset(int datasetId) throws SQLException {
        String sqlDeleteObservationValues = "DELETE FROM observation_values WHERE dataset_id = ?";
        String sqlDeleteObservations = "DELETE FROM observations WHERE dataset_id = ?";
        String sqlDeleteVariables = "DELETE FROM variables WHERE dataset_id = ?";
        String sqlDeleteDataset = "DELETE FROM datasets WHERE id = ?";

        try (Connection conn = SQLiteConnection.connect()) {
            conn.setAutoCommit(false); // Start transaction

            // Delete in correct order due to foreign key constraints
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDeleteObservationValues)) {
                pstmt.setInt(1, datasetId);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDeleteObservations)) {
                pstmt.setInt(1, datasetId);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDeleteVariables)) {
                pstmt.setInt(1, datasetId);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDeleteDataset)) {
                pstmt.setInt(1, datasetId);
                pstmt.executeUpdate();
            }
            conn.commit();
        }
    }

    public void saveObservation(int datasetId, int observationIndex, Observation observation) throws SQLException {
        String sqlObservation = "INSERT OR REPLACE INTO observations(id, dataset_id) VALUES(?, ?)";
        String sqlValue = "INSERT OR REPLACE INTO observation_values(observation_id, dataset_id, variable_id, value_numeric, value_text) VALUES(?, ?, ?, ?, ?)";
        String sqlDeleteExistingValues = "DELETE FROM observation_values WHERE observation_id = ? AND dataset_id = ?";

        try (Connection conn = SQLiteConnection.connect()) {
            conn.setAutoCommit(false);

            // Ensure the observation row exists
            try (PreparedStatement pstmt = conn.prepareStatement(sqlObservation)) {
                pstmt.setInt(1, observationIndex);
                pstmt.setInt(2, datasetId);
                pstmt.executeUpdate();
            }

            // Delete old values for this specific observation
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDeleteExistingValues)) {
                pstmt.setInt(1, observationIndex);
                pstmt.setInt(2, datasetId);
                pstmt.executeUpdate();
            }

            // Insert new values
            for (Map.Entry<String, Object> entry : observation.getValues().entrySet()) {
                String varName = entry.getKey();
                Object value = entry.getValue();

                // We need the variable ID; fetch it if not already known
                Variable var = getVariableByNameAndDatasetId(conn, varName, datasetId);
                if (var == null) {
                    System.err.println("Warning: Variable '" + varName + "' not found for dataset " + datasetId + ". Skipping value.");
                    continue;
                }

                try (PreparedStatement pstmt = conn.prepareStatement(sqlValue)) {
                    pstmt.setInt(1, observationIndex);
                    pstmt.setInt(2, datasetId);
                    pstmt.setInt(3, var.getId());

                    if (value instanceof Number) {
                        pstmt.setDouble(4, ((Number) value).doubleValue());
                        pstmt.setNull(5, Types.VARCHAR);
                    } else if (value != null) {
                        pstmt.setNull(4, Types.DOUBLE);
                        pstmt.setString(5, value.toString());
                    } else { // Handle null values
                        pstmt.setNull(4, Types.DOUBLE);
                        pstmt.setNull(5, Types.VARCHAR);
                    }
                    pstmt.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error saving observation: " + e.getMessage());
            throw e;
        }
    }

    // Helper method to get Variable by name and dataset ID within a transaction
    private Variable getVariableByNameAndDatasetId(Connection conn, String varName, int datasetId) throws SQLException {
        String sql = "SELECT id, name, type FROM variables WHERE dataset_id = ? AND name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, datasetId);
            pstmt.setString(2, varName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Variable(rs.getInt("id"), rs.getString("name"), rs.getString("type"));
            }
        }
        return null;
    }

    public void deleteObservationsForDataset(int datasetId) throws SQLException {
        String sqlDeleteObservationValues = "DELETE FROM observation_values WHERE dataset_id = ?";
        String sqlDeleteObservations = "DELETE FROM observations WHERE dataset_id = ?";

        try (Connection conn = SQLiteConnection.connect()) {
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement pstmt = conn.prepareStatement(sqlDeleteObservationValues)) {
                pstmt.setInt(1, datasetId);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDeleteObservations)) {
                pstmt.setInt(1, datasetId);
                pstmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error deleting observations for dataset: " + e.getMessage());
            throw e;
        }
    }

    // NEW METHOD FOR CLEANING ALL DATA
    public void deleteAllDatasetsAndData() throws SQLException {
        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false); // Iniciar transacción

            // Eliminar observaciones (primero, ya que tienen FK a variables)
            stmt.execute("DELETE FROM observation_values");
            stmt.execute("DELETE FROM observations");
            // Eliminar variables (segundo, ya que tienen FK a datasets)
            stmt.execute("DELETE FROM variables");
            // Eliminar datasets
            stmt.execute("DELETE FROM datasets");

            conn.commit(); // Confirmar la transacción
            System.out.println("Todos los datasets y sus datos han sido eliminados.");
        } catch (SQLException e) {
            // IMPORTANTE: Si hay un error, revertir la transacción.
            try (Connection conn = SQLiteConnection.connect()) {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException rollbackEx) {
                System.err.println("Error durante el rollback: " + rollbackEx.getMessage());
            }
            System.err.println("Error al eliminar todos los datasets y datos: " + e.getMessage());
            throw e; // Relanzar la excepción para que el llamador la maneje
        }
    }
}