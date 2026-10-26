package com.ifba.prodscalpel4objects.adapter.reduction;

import com.github.javaparser.ast.CompilationUnit;
import com.ifba.prodscalpel4objects.adapter.evaluation.CompilationResult;
import com.ifba.prodscalpel4objects.adapter.evaluation.DynamicCompiler;
import com.ifba.prodscalpel4objects.adapter.evaluation.DynamicTestRunner;
import com.ifba.prodscalpel4objects.adapter.evaluation.TestExecutionResult;
import com.ifba.prodscalpel4objects.adapter.model.ReductionIndividual;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Avaliador da função de aptidão (fitness) para o algoritmo de Melhoria Genética (GI).
 *
 * <p>Equilibra a estrita garantia de compilação ($I_C$) e preservação do comportamento
 * dos testes unitários ($T_P$) com o percentual de nós da AST podados (redução de sobrecarga).</p>
 *
 * <p>Fórmula de Fitness:</p>
 * <pre>
 * Se não compila: Fitness = 0.0
 * Se compila:
 *   Fitness = W_comp * 1.0 + W_test * (T_P / T) + W_red * (|Deleções| / |TotalNós|) [apenas se T_P == T]
 * </pre>
 *
 * @author Giovane Neves
 */
public class ReductionFitnessCalculator {

    private final DynamicCompiler compiler;
    private final DynamicTestRunner testRunner;
    private final AstPruner astPruner;

    private static final double WEIGHT_COMPILATION = 0.2;
    private static final double WEIGHT_TESTS = 0.5;
    private static final double WEIGHT_REDUCTION = 0.3;

    public ReductionFitnessCalculator(DynamicCompiler compiler, DynamicTestRunner testRunner, AstPruner astPruner) {
        this.compiler = Objects.requireNonNull(compiler);
        this.testRunner = Objects.requireNonNull(testRunner);
        this.astPruner = Objects.requireNonNull(astPruner);
    }

    /**
     * Avalia a aptidão de um indivíduo candidato a partir da poda da AST e execução de testes.
     *
     * @param individual O indivíduo cromossômico contendo os IDs de instrução deletados.
     * @param originalCu A AST original da classe alvo do órgão.
     * @param targetClassFullName Nome totalmente qualificado da classe alvo (ex: "com.ifba.ServiceClass").
     * @param indexedNodes Lista dos nós podáveis indexados.
     * @param auxiliarySources Mapa de classes auxiliares e arquivos de teste (Nome -> Código Fonte).
     * @param testClassNames Lista das classes de teste unitário a serem executadas.
     * @return Valor de aptidão (0.0 a 1.0).
     */
    public double evaluateFitness(
            ReductionIndividual individual,
            CompilationUnit originalCu,
            String targetClassFullName,
            List<PrunableStatement> indexedNodes,
            Map<String, String> auxiliarySources,
            List<String> testClassNames
    ) {
        if (individual == null || originalCu == null) {
            return 0.0;
        }

        // 1. Gera a AST podada conforme os genes do indivíduo
        CompilationUnit prunedCu = astPruner.applyPruning(
                originalCu,
                indexedNodes,
                individual.getDeletedStatementIds()
        );

        // 2. Prepara o conjunto de fontes para compilação (classe podada + auxiliares + testes)
        Map<String, String> sourcesToCompile = new HashMap<>();
        if (auxiliarySources != null) {
            sourcesToCompile.putAll(auxiliarySources);
        }
        sourcesToCompile.put(targetClassFullName, prunedCu.toString());

        // 3. Compilação dinâmica em memória
        CompilationResult compResult = compiler.compileSources(sourcesToCompile);
        if (!compResult.success()) {
            individual.setCompiles(false);
            individual.setFitness(0.0);
            individual.setPassedTestsCount(0);
            return 0.0;
        }

        individual.setCompiles(true);

        // Se não houver suíte de testes informada, a aptidão é baseada na compilação e redução
        if (testClassNames == null || testClassNames.isEmpty()) {
            double redScore = computeReductionScore(individual, indexedNodes.size());
            double fitness = WEIGHT_COMPILATION + (WEIGHT_REDUCTION * redScore);
            individual.setFitness(fitness);
            return fitness;
        }

        // 4. Execução dinâmica dos testes unitários
        TestExecutionResult testResult = testRunner.executeTests(
                compResult.outputDirectory(),
                testClassNames
        );

        individual.setTotalTestsCount(testResult.totalTests());
        individual.setPassedTestsCount(testResult.passedTests());
        individual.setExecutionTimeMillis(testResult.totalExecutionTimeMillis());

        double testRatio = testResult.getPassRate();

        // 5. Cálculo do Fitness
        double fitness = WEIGHT_COMPILATION + (WEIGHT_TESTS * testRatio);

        // O bônus de redução só é concedido se TODOS os testes continuarem passando (100%)
        if (testResult.allPassed()) {
            double redScore = computeReductionScore(individual, indexedNodes.size());
            fitness += (WEIGHT_REDUCTION * redScore);
        }

        individual.setFitness(fitness);
        return fitness;
    }

    private double computeReductionScore(ReductionIndividual individual, int totalNodes) {
        if (totalNodes <= 0) return 0.0;
        return (double) individual.getDeletedCount() / totalNodes;
    }
}
