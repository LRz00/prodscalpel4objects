package com.ifba.prodscalpel4objects.adapter.model;

import java.util.Objects;

/**
 * Representa uma associação candidata (mapping) entre um parâmetro/dependência
 * exigida pelo órgão e uma variável ou Bean Spring disponível no escopo do sistema hospedeiro.
 *
 * @param organParamName Nome do parâmetro ou atributo exigido no órgão original.
 * @param organParamType Tipo qualificado do parâmetro no órgão (ex: "java.lang.String").
 * @param hostVariableName Nome da variável ou Bean disponível no escopo do Host.
 * @param hostVariableType Tipo qualificado da variável no Host.
 * @param isSpringBean Indica se a variável do Host provém do ApplicationContext do Spring (injeção).
 * @param compatibilityScore Grau de compatibilidade de tipos (1.0 = exato, 0.8 = subtipo/polimorfismo).
 *
 * @author Giovane Neves
 */
public record MappingCandidate(
        String organParamName,
        String organParamType,
        String hostVariableName,
        String hostVariableType,
        boolean isSpringBean,
        double compatibilityScore
) {

    public MappingCandidate {
        Objects.requireNonNull(organParamName, "organParamName não pode ser nulo");
        Objects.requireNonNull(organParamType, "organParamType não pode ser nulo");
        Objects.requireNonNull(hostVariableName, "hostVariableName não pode ser nulo");
        Objects.requireNonNull(hostVariableType, "hostVariableType não pode ser nulo");
    }

    /**
     * Retorna a expressão de mapeamento em código Java.
     * Exemplo: {@code hostVar} ou {@code (TargetType) hostVar}.
     *
     * @return String contendo a expressão de conversão ou passagem do parâmetro.
     */
    public String toExpression() {
        if (organParamType.equals(hostVariableType)) {
            return hostVariableName;
        }
        return "(" + organParamType + ") " + hostVariableName;
    }
}
