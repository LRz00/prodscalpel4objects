package com.ifba.prodscalpel4objects.adapter.evaluation;

import java.util.Collections;
import java.util.List;

/**
 * Encapsula o resultado consolidado da execução de toda a suíte de testes unitários
 * do IceBox para um indivíduo candidato.
 *
 * @param totalTests Quantidade total de testes unitários executados.
 * @param passedTests Quantidade de testes que passaram com sucesso.
 * @param failedTests Quantidade de testes que falharam em asserções.
 * @param crashedTests Quantidade de testes que abortaram devido a exceções inesperadas (crashes).
 * @param totalExecutionTimeMillis Tempo total de execução da suíte em milissegundos.
 * @param testCaseResults Lista com os detalhes individuais de cada teste executado.
 *
 * @author Giovane Neves
 */
public record TestExecutionResult(
        int totalTests,
        int passedTests,
        int failedTests,
        int crashedTests,
        long totalExecutionTimeMillis,
        List<TestCaseResult> testCaseResults
) {

    public TestExecutionResult {
        testCaseResults = testCaseResults != null ? List.copyOf(testCaseResults) : Collections.emptyList();
    }

    public static TestExecutionResult empty() {
        return new TestExecutionResult(0, 0, 0, 0, 0, Collections.emptyList());
    }

    /**
     * Indica se todos os testes da suíte passaram com sucesso (100% de aprovação).
     */
    public boolean allPassed() {
        return totalTests > 0 && passedTests == totalTests && crashedTests == 0;
    }

    /**
     * Calcula a taxa de testes passados (Pass Rate) entre 0.0 e 1.0.
     */
    public double getPassRate() {
        if (totalTests <= 0) return 0.0;
        return (double) passedTests / totalTests;
    }

    /**
     * Calcula a taxa de testes não abortados (Non-Crashed Rate) entre 0.0 e 1.0.
     */
    public double getNonCrashedRate() {
        if (totalTests <= 0) return 0.0;
        return (double) (totalTests - crashedTests) / totalTests;
    }
}
