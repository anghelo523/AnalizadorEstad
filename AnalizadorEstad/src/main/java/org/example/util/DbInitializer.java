package org.example.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DbInitializer {

    /**
     * Inicializa la base de datos, creando las tablas necesarias si no existen.
     */
    public static void initializeDatabase() {
        String createDatasetsTableSQL = "CREATE TABLE IF NOT EXISTS datasets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE" +
                ");";

        String createVariablesTableSQL = "CREATE TABLE IF NOT EXISTS variables (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "dataset_id INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE," +
                "UNIQUE(dataset_id, name)" + // No se permite el mismo nombre de variable en el mismo dataset
                ");";

        String createObservationsTableSQL = "CREATE TABLE IF NOT EXISTS observations (" +
                "id INTEGER NOT NULL," + // Index de la observación dentro del dataset
                "dataset_id INTEGER NOT NULL," +
                "PRIMARY KEY (id, dataset_id)," + // Clave primaria compuesta
                "FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE" +
                ");";

        String createObservationValuesTableSQL = "CREATE TABLE IF NOT EXISTS observation_values (" +
                "observation_id INTEGER NOT NULL," +
                "dataset_id INTEGER NOT NULL," +
                "variable_id INTEGER NOT NULL," +
                "value_numeric REAL," + // Para valores numéricos
                "value_text TEXT," +    // Para valores de texto/cualitativos
                "PRIMARY KEY (observation_id, dataset_id, variable_id)," +
                "FOREIGN KEY (observation_id, dataset_id) REFERENCES observations(id, dataset_id) ON DELETE CASCADE," +
                "FOREIGN KEY (variable_id) REFERENCES variables(id) ON DELETE CASCADE" +
                ");";

        try (Connection conn = SQLiteConnection.connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createDatasetsTableSQL);
            stmt.execute(createVariablesTableSQL);
            stmt.execute(createObservationsTableSQL);
            stmt.execute(createObservationValuesTableSQL);
            System.out.println("Tablas de base de datos inicializadas o ya existentes.");
        } catch (SQLException e) {
            System.err.println("Error al inicializar la base de datos: " + e.getMessage());
            e.printStackTrace();
        }
    }
}