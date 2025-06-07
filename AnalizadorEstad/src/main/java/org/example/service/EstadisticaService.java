package org.example.service;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.example.model.Dataset;
import org.example.model.Observation;
import org.example.model.Variable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class EstadisticaService {

    /**
     * Realiza un análisis de regresión lineal múltiple.
     *
     * @param dataset El dataset que contiene los datos.
     * @param dependentVariableName El nombre de la variable dependiente.
     * @param independentVariableNames Una lista de nombres de variables independientes.
     * @return Un mapa con los resultados de la regresión (coeficientes, R-cuadrado, etc.).
     */
    public Map<String, Object> performMultipleLinearRegression(
            Dataset dataset, String dependentVariableName, List<String> independentVariableNames) {

        // Validar que las variables existan y sean numéricas
        Variable dependentVar = dataset.getVariableByName(dependentVariableName);
        if (dependentVar == null) {
            throw new IllegalArgumentException("Variable dependiente no encontrada: " + dependentVariableName);
        }
        if (!isNumeric(dependentVar.getType())) {
            throw new IllegalArgumentException("La variable dependiente debe ser numérica: " + dependentVariableName);
        }

        List<Variable> independentVars = independentVariableNames.stream()
                .map(dataset::getVariableByName)
                .peek(v -> {
                    if (v == null) {
                        throw new IllegalArgumentException("Variable independiente no encontrada: " + v.getName());
                    }
                    if (!isNumeric(v.getType())) {
                        throw new IllegalArgumentException("La variable independiente '" + v.getName() + "' debe ser numérica.");
                    }
                })
                .collect(Collectors.toList());

        // Preparar los datos para Apache Commons Math
        List<Observation> validObservations = dataset.getObservations().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Ordenar por índice de observación para consistencia
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        int numObservations = validObservations.size();
        if (numObservations <= independentVariableNames.size()) { // Se necesitan al menos N+1 observaciones para N variables + intercepto
            throw new IllegalArgumentException("No hay suficientes observaciones (" + numObservations + ") para la regresión con " + independentVariableNames.size() + " variables independientes. Se necesitan al menos " + (independentVariableNames.size() + 1) + " observaciones.");
        }
        if (independentVariableNames.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una variable independiente para la regresión.");
        }

        double[] y = new double[numObservations];
        double[][] x = new double[numObservations][independentVariableNames.size()];

        for (int currentRow = 0; currentRow < numObservations; currentRow++) {
            Observation obs = validObservations.get(currentRow);

            Object depValue = obs.getValue(dependentVariableName);
            if (depValue instanceof Number) {
                y[currentRow] = ((Number) depValue).doubleValue();
            } else {
                throw new IllegalArgumentException("Valor no numérico o nulo en variable dependiente '" + dependentVariableName + "' en la observación " + (currentRow + 1) + ".");
            }

            for (int i = 0; i < independentVariableNames.size(); i++) {
                String indepVarName = independentVariableNames.get(i);
                Object indepValue = obs.getValue(indepVarName);
                if (indepValue instanceof Number) {
                    x[currentRow][i] = ((Number) indepValue).doubleValue();
                } else {
                    throw new IllegalArgumentException("Valor no numérico o nulo en variable independiente '" + indepVarName + "' en la observación " + (currentRow + 1) + ".");
                }
            }
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        // Por defecto, OLSMultipleLinearRegression calcula un intercepto.
        // Si no quieres intercepto, descomenta la línea de abajo.
        // regression.setNoIntercept(true);

        try {
            regression.newSampleData(y, x); // Carga los datos (Y dependiente, X independientes)
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error al cargar datos para regresión: " + e.getMessage());
        }

        double[] beta = regression.estimateRegressionParameters(); // Coeficientes (incluye intercepto si aplica)
        double rSquared = regression.calculateRSquared(); // R-cuadrado
        double adjustedRSquared = regression.calculateAdjustedRSquared(); // R-cuadrado ajustado
        double sigma = regression.estimateRegressionStandardError(); // Error estándar de los residuales
        double sumOfSqResiduals = regression.estimateResidualSumOfSquares(); // Suma de cuadrados de los residuales (SSE)
        double sumOfRegressionSquares = regression.estimateRegressionSumOfSquares(); // Suma de cuadrados de la regresión (SSR)
        double totalSumOfSquares = regression.estimateTotalSumSquares(); // Suma total de cuadrados

        // Crear un mapa de resultados
        Map<String, Object> results = new HashMap<>();

        List<String> coefNames = new ArrayList<>();
        List<Double> coefficients = new ArrayList<>();

        if (!regression.isNoIntercept()) { // Si el modelo incluye intercepto
            coefNames.add("Intercepto");
            coefficients.add(beta[0]);
            for (int i = 0; i < independentVariableNames.size(); i++) {
                coefNames.add(independentVariableNames.get(i));
                coefficients.add(beta[i + 1]); // Los coeficientes de las variables independientes están después del intercepto
            }
        } else { // Si el modelo no incluye intercepto
            for (int i = 0; i < independentVariableNames.size(); i++) {
                coefNames.add(independentVariableNames.get(i));
                coefficients.add(beta[i]);
            }
        }

        results.put("CoefficientNames", coefNames);
        results.put("Coefficients", coefficients);

        results.put("R-Squared", rSquared);
        results.put("Adjusted R-Squared", adjustedRSquared);
        results.put("Regression Standard Error (Sigma)", sigma);
        results.put("Residual Sum of Squares (SSE)", sumOfSqResiduals);
        results.put("Regression Sum of Squares (SSR)", sumOfRegressionSquares);
        results.put("Total Sum of Squares (SST)", totalSumOfSquares);
        results.put("Num Observations", numObservations);
        results.put("Num Independent Variables", independentVariableNames.size());

        return results;
    }

    /**
     * Verifica si un tipo de variable es numérico.
     * @param type El tipo de la variable (e.g., "NUMERIC", "TEXT", "QUALITATIVE", "QUANTITATIVE").
     * @return true si es numérico, false de lo contrario.
     */
    private boolean isNumeric(String type) {
        return "NUMERIC".equalsIgnoreCase(type) || "QUANTITATIVE".equalsIgnoreCase(type);
    }
}