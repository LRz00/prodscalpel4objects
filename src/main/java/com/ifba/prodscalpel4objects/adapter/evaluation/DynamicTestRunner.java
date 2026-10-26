package com.ifba.prodscalpel4objects.adapter.evaluation;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Executor programático e dinâmico de testes unitários para avaliação
 * de variantes de código nos algoritmos genéticos de GI e GP.
 *
 * <p>Carrega o bytecode recém-compilado pelo {@link DynamicCompiler} em um
 * {@link URLClassLoader} isolado, detecta métodos de teste (compatível com
 * JUnit 4, JUnit 5 e convenção xUnit) e captura de forma segura falhas de
 * asserção versus falhas de execução (crashes).</p>
 *
 * @author Giovane Neves
 */
public class DynamicTestRunner {

    /**
     * Executa a suíte de testes unitários sobre os arquivos de bytecode gerados.
     *
     * @param compiledClassesDirectory Diretório contendo os arquivos .class compilados.
     * @param testClassNames Lista de nomes totalmente qualificados das classes de teste (ex: "org.example.ServiceTest").
     * @return {@link TestExecutionResult} com o resumo e os detalhes de cada teste executado.
     */
    public TestExecutionResult executeTests(Path compiledClassesDirectory, List<String> testClassNames) {
        if (compiledClassesDirectory == null || testClassNames == null || testClassNames.isEmpty()) {
            return TestExecutionResult.empty();
        }

        long suiteStartTime = System.currentTimeMillis();
        List<TestCaseResult> caseResults = new ArrayList<>();

        URL classDirUrl;
        try {
            classDirUrl = compiledClassesDirectory.toUri().toURL();
        } catch (MalformedURLException e) {
            return TestExecutionResult.empty();
        }

        ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classDirUrl}, parentClassLoader)) {

            for (String testClassName : testClassNames) {
                runTestsForClass(classLoader, testClassName, caseResults);
            }

        } catch (IOException e) {
            System.err.println("Erro ao fechar ClassLoader de testes: " + e.getMessage());
        }

        long totalSuiteTime = System.currentTimeMillis() - suiteStartTime;

        int passed = 0;
        int failed = 0;
        int crashed = 0;

        for (TestCaseResult cr : caseResults) {
            if (cr.passed()) {
                passed++;
            } else if (cr.crashed()) {
                crashed++;
            } else {
                failed++;
            }
        }

        return new TestExecutionResult(
                caseResults.size(),
                passed,
                failed,
                crashed,
                totalSuiteTime,
                caseResults
        );
    }

    private void runTestsForClass(ClassLoader classLoader, String testClassName, List<TestCaseResult> results) {
        Class<?> testClass;
        try {
            testClass = classLoader.loadClass(testClassName);
        } catch (ClassNotFoundException e) {
            results.add(new TestCaseResult(testClassName, "loadClass", false, true, 0, "Classe de teste não encontrada: " + e.getMessage()));
            return;
        }

        List<Method> beforeMethods = findLifecycleMethods(testClass, "Before", "BeforeEach");
        List<Method> testMethods = findTestMethods(testClass);

        for (Method testMethod : testMethods) {
            long testStartTime = System.currentTimeMillis();
            try {
                // Instancia uma nova instância da classe de teste para cada teste (isolamento padrão do JUnit)
                Object testInstance = testClass.getDeclaredConstructor().newInstance();

                // Executa métodos de setup (@Before / @BeforeEach)
                for (Method beforeMethod : beforeMethods) {
                    beforeMethod.setAccessible(true);
                    beforeMethod.invoke(testInstance);
                }

                // Invoca o método de teste
                testMethod.setAccessible(true);
                testMethod.invoke(testInstance);

                long elapsed = System.currentTimeMillis() - testStartTime;
                results.add(TestCaseResult.pass(testClassName, testMethod.getName(), elapsed));

            } catch (InvocationTargetException ite) {
                long elapsed = System.currentTimeMillis() - testStartTime;
                Throwable cause = ite.getTargetException();

                if (isAssertionFailure(cause)) {
                    results.add(TestCaseResult.assertionFailure(testClassName, testMethod.getName(), cause.getMessage(), elapsed));
                } else {
                    results.add(TestCaseResult.crash(testClassName, testMethod.getName(), cause, elapsed));
                }

            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - testStartTime;
                results.add(TestCaseResult.crash(testClassName, testMethod.getName(), e, elapsed));
            }
        }
    }

    private List<Method> findTestMethods(Class<?> clazz) {
        List<Method> testMethods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (isTestMethod(method)) {
                testMethods.add(method);
            }
        }
        return testMethods;
    }

    private boolean isTestMethod(Method method) {
        // Verifica anotação cujo nome simples seja "Test" (JUnit 4 ou JUnit 5)
        for (Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if ("Test".equals(name)) {
                return true;
            }
        }
        // Convenção legada de métodos iniciando com "test..." sem parâmetros
        return method.getName().startsWith("test") && method.getParameterCount() == 0;
    }

    private List<Method> findLifecycleMethods(Class<?> clazz, String... annotationSimpleNames) {
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                String name = annotation.annotationType().getSimpleName();
                for (String expected : annotationSimpleNames) {
                    if (expected.equals(name)) {
                        methods.add(method);
                        break;
                    }
                }
            }
        }
        return methods;
    }

    private boolean isAssertionFailure(Throwable cause) {
        if (cause instanceof AssertionError) {
            return true;
        }
        String className = cause.getClass().getName();
        return className.contains("AssertionFailedError") || className.contains("AssertionError");
    }
}
