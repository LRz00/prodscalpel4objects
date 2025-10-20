package com.ifba.prodscalpel4objects.adapter.service;

import com.ifba.prodscalpel4objects.adapter.chromosome.OrganGenotype;
import com.ifba.prodscalpel4objects.adapter.service.compiler.ByteClassLoader;
import com.ifba.prodscalpel4objects.adapter.service.compiler.InMemoryJavaCompiler;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Avalia a "qualidade" (fitness) de um candidato a órgão.
 * Implementa a avaliação de fitness em três camadas:
 * 1. Validade Estática (implícita na compilação)
 * 2. Compilação em Memória
 * 3. Validação Comportamental (execução de testes)
 */
public class FitnessEvaluator {

    private final Path hostProjectPath;
    private final Path testsPath;

    public FitnessEvaluator(Path hostProjectPath, Path testsPath) {
        this.hostProjectPath = hostProjectPath;
        this.testsPath = testsPath;
    }

    /**
     * Calcula o fitness de um genótipo de órgão.
     * @param genotype O genótipo a ser avaliado.
     * @return Um valor de fitness, onde valores maiores são melhores.
     */
    public double evaluate(final OrganGenotype genotype) {
        String sourceCode = genotype.chromosome().getOrganAST().toString();
        String className = genotype.chromosome().getOrganAST().getPrimaryTypeName().orElse("Unknown");

        // Camada 2: Compilação em Memória
        InMemoryJavaCompiler compiler = new InMemoryJavaCompiler();
        List<Diagnostic<? extends JavaFileObject>> diagnostics;
        Map<String, byte> byteCode;

        try {
            byteCode = compiler.compile(className + ".java", sourceCode);
        } catch (Exception e) {
            // Falha catastrófica na compilação
            return 0.0;
        }

        if (byteCode.isEmpty()) {
            // A compilação falhou. Fitness é inversamente proporcional ao número de erros.
            long errorCount = compiler.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
            return 1.0 / (1.0 + errorCount);
        }

        // Camada 3: Validação Comportamental
        return runIceBoxTests(byteCode);
    }

    /**
     * Executa os "ice-box tests" contra o bytecode compilado.
     * @param byteCode O mapa de nome da classe para seu bytecode.
     * @return O fitness final baseado na fórmula do µSCALPEL.
     */
    private double runIceBoxTests(Map<String, byte> byteCode) {
        // Implementação simplificada. Uma versão real carregaria classes de teste do 'testsPath'.
        // Aqui, assumimos que os testes estão em uma classe conhecida, por exemplo, "OrganTests".
        // Esta parte requer uma implementação robusta de carregamento e execução de testes (ex: usando JUnit Core).

        ByteClassLoader classLoader = new ByteClassLoader(byteCode);
        AtomicInteger testsExecuted = new AtomicInteger(0);
        AtomicInteger testsPassed = new AtomicInteger(0);

        try {
            // Carrega a classe de teste dinamicamente
            // String testClassName = "com.example.tests.OrganTests";
            // Class<?> testClass = classLoader.loadClass(testClassName);
            // Object testInstance = testClass.getDeclaredConstructor().newInstance();

            // Itera sobre os métodos de teste (ex: anotados com @Test)
            // for (Method method : testClass.getMethods()) {
            //     if (method.isAnnotationPresent(Test.class)) {
            //         testsExecuted.incrementAndGet();
            //         try {
            //             method.invoke(testInstance);
            //             testsPassed.incrementAndGet(); // Passou se não lançou exceção
            //         } catch (Exception e) {
            //             // Teste falhou
            //         }
            //     }
            // }

            // Simulação para fins de exemplo, já que não temos os testes reais
            testsExecuted.set(10); // Simula 10 testes
            testsPassed.set(7);    // Simula 7 passando

        } catch (Exception e) {
            // Erro ao carregar ou executar os testes
            return 0.0; // Penalidade máxima se a suíte de teste não puder ser executada
        }

        if (testsExecuted.get() == 0) {
            return 0.0; // Nenhum teste foi executado
        }

        // Fórmula de fitness do µSCALPEL: recompensa por estabilidade (TX) e correção (TP)
        double tx = testsExecuted.get();
        double tp = testsPassed.get();

        if (tp == 0) {
            return tx / (1.0 + Double.POSITIVE_INFINITY); // Evita divisão por zero, resulta em 0
        }

        return tx / (1.0 + (1.0 / tp));
    }
}