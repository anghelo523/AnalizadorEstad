package org.example.model;

import java.util.HashMap;
import java.util.Map;

public class Observation {
    // No tiene un ID propio de la base de datos, su ID es el índice de la observación dentro del Dataset
    // La clave es el nombre de la variable, el valor es el dato de esa variable para esta observación
    private Map<String, Object> values;

    public Observation() {
        this.values = new HashMap<>();
    }

    // Getters y Setters
    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * Añade o actualiza un valor para una variable específica en esta observación.
     * @param variableName El nombre de la variable.
     * @param value El valor a almacenar.
     */
    public void addValue(String variableName, Object value) {
        this.values.put(variableName, value);
    }

    /**
     * Obtiene el valor para una variable específica en esta observación.
     * @param variableName El nombre de la variable.
     * @return El valor si existe, null de lo contrario.
     */
    public Object getValue(String variableName) {
        return values.get(variableName);
    }

    /**
     * Elimina el valor asociado a una variable específica en esta observación.
     * @param variableName El nombre de la variable a eliminar.
     */
    public void removeValue(String variableName) {
        values.remove(variableName);
    }
}