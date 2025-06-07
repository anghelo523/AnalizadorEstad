package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dataset {
    private int id;
    private String name;
    private List<Variable> variables;
    private Map<Integer, Observation> observations; // Clave: índice de la observación (fila)

    public Dataset() {
        this.variables = new ArrayList<>();
        this.observations = new HashMap<>();
    }

    public Dataset(String name) {
        this();
        this.name = name;
    }

    public Dataset(int id, String name) {
        this();
        this.id = id;
        this.name = name;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Variable> getVariables() {
        return variables;
    }

    public void setVariables(List<Variable> variables) {
        this.variables = variables;
    }

    public Map<Integer, Observation> getObservations() {
        return observations;
    }

    public void setObservations(Map<Integer, Observation> observations) {
        this.observations = observations;
    }

    /**
     * Añade una variable al dataset.
     * @param variable La variable a añadir.
     */
    public void addVariable(Variable variable) {
        if (!this.variables.contains(variable)) { // Evitar duplicados por nombre si ya existe una lógica de equals
            this.variables.add(variable);
        }
    }

    /**
     * Elimina una variable del dataset por su nombre.
     * También elimina los valores asociados a esa variable de todas las observaciones.
     * @param variableName El nombre de la variable a eliminar.
     */
    public void removeVariable(String variableName) {
        variables.removeIf(v -> v.getName().equals(variableName));
        observations.values().forEach(obs -> obs.removeValue(variableName));
    }

    /**
     * Añade una observación (fila de datos) al dataset.
     * Asigna un nuevo índice de observación.
     * @param observation La observación a añadir.
     * @return El índice de la observación añadida.
     */
    public int addObservation(Observation observation) {
        int newIndex = getNextObservationIndex();
        observations.put(newIndex, observation);
        return newIndex;
    }

    /**
     * Elimina una observación por su índice.
     * @param index El índice de la observación a eliminar.
     */
    public void removeObservation(int index) {
        observations.remove(index);
        // Opcional: Reindexar observaciones si es necesario, pero es más complejo.
        // Por ahora, solo se elimina y el índice queda vacante.
    }

    /**
     * Obtiene una variable por su nombre.
     * @param name El nombre de la variable.
     * @return La Variable si se encuentra, null de lo contrario.
     */
    public Variable getVariableByName(String name) {
        for (Variable var : variables) {
            if (var.getName().equals(name)) {
                return var;
            }
        }
        return null;
    }

    /**
     * Obtiene el valor de una observación para una variable específica.
     * @param obsIndex El índice de la observación (fila).
     * @param variableName El nombre de la variable (columna).
     * @return El valor si existe, null de lo contrario.
     */
    public Object getValue(int obsIndex, String variableName) {
        Observation obs = observations.get(obsIndex);
        return obs != null ? obs.getValue(variableName) : null;
    }

    /**
     * Establece el valor de una observación para una variable específica.
     * Si la observación o la variable no existen, las crea si es necesario.
     * @param obsIndex El índice de la observación (fila).
     * @param variableName El nombre de la variable (columna).
     * @param value El valor a establecer.
     */
    public void setValue(int obsIndex, String variableName, Object value) {
        observations.computeIfAbsent(obsIndex, k -> new Observation()).addValue(variableName, value);
    }


    /**
     * Obtiene el siguiente índice disponible para una nueva observación.
     * @return El índice más alto actual + 1, o 0 si no hay observaciones.
     */
    public int getNextObservationIndex() {
        if (observations.isEmpty()) {
            return 0;
        }
        return Collections.max(observations.keySet()) + 1;
    }

    /**
     * Obtiene el número de observaciones (filas) en el dataset.
     * @return El número de observaciones.
     */
    public int getObservationCount() {
        return observations.size();
    }

    /**
     * Obtiene el número de variables (columnas) en el dataset.
     * @return El número de variables.
     */
    public int getVariableCount() {
        return variables.size();
    }
}