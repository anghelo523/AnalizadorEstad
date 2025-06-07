package org.example.model;

import java.util.Objects;

public class Variable {
    private int id; // ID de la base de datos
    private String name;
    private String type; // e.g., "NUMERIC", "TEXT", "QUALITATIVE", "QUANTITATIVE", "BOOLEAN"

    public Variable(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Variable(int id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return name + " (" + type + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Variable variable = (Variable) o;
        return Objects.equals(name, variable.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}