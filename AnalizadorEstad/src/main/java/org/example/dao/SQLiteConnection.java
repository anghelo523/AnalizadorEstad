package org.example.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteConnection {
    // URL de conexión a la base de datos SQLite. Se creará si no existe.
    // Usamos `System.getProperty("user.home")` para que la base de datos se guarde
    // en la carpeta del usuario (home directory) y sea accesible en cualquier SO.
    // Esto es más robusto que una ruta fija.
    private static final String URL = "jdbc:sqlite:" + System.getProperty("user.home") + "/analizador_estadistico.db";

    /**
     * Establece una conexión con la base de datos SQLite.
     * @return Objeto Connection a la base de datos.
     * @throws SQLException Si ocurre un error de conexión a la base de datos.
     */
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}