package com.ifba.prodscalpel4objects.adapter.evaluation;

/**
 * Representa o resultado da execução de um método de teste unitário individual.
 *
 * @param testClassName Nome da classe de teste.
 * @param testMethodName Nome do método de teste executado.
 * @param passed Indica se o teste passou sem falha de asserção e sem exceções inesperadas.
 * @param crashed Indica se o teste abortou devido a uma exceção inesperada (diferente de falha de asserção).
 * @param executionTimeMillis Tempo de execução do teste em milissegundos.
 * @param failureMessage Mensagem de erro caso o teste tenha falhado ou abortado.
 *
 * @author Giovane Neves
 */
public record TestCaseResult(
        String testClassName,
        String testMethodName,
        boolean passed,
        boolean crashed,
        long executionTimeMillis,
        String failureMessage
) {

    public static TestCaseResult pass(String testClass, String testMethod, long timeMillis) {
        return new TestCaseResult(testClass, testMethod, true, false, timeMillis, null);
    }

    public static TestCaseResult assertionFailure(String testClass, String testMethod, String message, long timeMillis) {
        return new TestCaseResult(testClass, testMethod, false, false, timeMillis, message);
    }

    public static TestCaseResult crash(String testClass, String testMethod, Throwable exception, long timeMillis) {
        String msg = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return new TestCaseResult(testClass, testMethod, false, true, timeMillis, msg);
    }
}
