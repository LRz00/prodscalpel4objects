package com.ifba.prodscalpel4objects.adapter.reduction;

import com.github.javaparser.ast.CompilationUnit;
import com.ifba.prodscalpel4objects.adapter.model.ReductionIndividual;

import java.util.*;

/**
 * Motor de Melhoria Genética (Genetic Improvement - GI) para Redução Automatizada de
 * Sobrecarga de Código (Over-organ Reduction).
 *
 * <p>Inspirado na técnica de redução de fatia observacional de μScalpel (Barr et al., 2015)
 * e prodScalpel (Souza et al., 2025), adaptado para manipulação orientada a objetos de nós da AST.</p>
 *
 * @author Giovane Neves
 */
public class GIReductionEngine {

    private final AstPruner astPruner;
    private final ReductionFitnessCalculator fitnessCalculator;
    private final Random random;

    private int populationSize = 20;
    private int maxGenerations = 15;
    private double crossoverRate = 0.5;
    private double mutationRate = 0.5;
    private int tournamentSize = 3;

    public GIReductionEngine(AstPruner astPruner, ReductionFitnessCalculator fitnessCalculator) {
        this.astPruner = Objects.requireNonNull(astPruner);
        this.fitnessCalculator = Objects.requireNonNull(fitnessCalculator);
        this.random = new Random();
    }

    public GIReductionEngine(AstPruner astPruner, ReductionFitnessCalculator fitnessCalculator, long seed) {
        this.astPruner = Objects.requireNonNull(astPruner);
        this.fitnessCalculator = Objects.requireNonNull(fitnessCalculator);
        this.random = new Random(seed);
    }

    /**
     * Executa o processo de redução genética sobre a classe alvo.
     *
     * @param originalCu A AST original da classe.
     * @param targetClassFullName Nome totalmente qualificado da classe (ex: "com.ifba.ServiceClass").
     * @param auxiliarySources Classes de suporte e testes necessárias para compilação.
     * @param testClassNames Lista das classes de teste unitário do IceBox.
     * @return O melhor indivíduo e a AST podada resultante.
     */
    public ReductionResult runReduction(
            CompilationUnit originalCu,
            String targetClassFullName,
            Map<String, String> auxiliarySources,
            List<String> testClassNames
    ) {
        List<PrunableStatement> prunableNodes = astPruner.indexPrunableNodes(originalCu);
        if (prunableNodes.isEmpty()) {
            ReductionIndividual emptyInd = new ReductionIndividual(0);
            emptyInd.setCompiles(true);
            emptyInd.setFitness(1.0);
            return new ReductionResult(emptyInd, originalCu, 0, 0);
        }

        // 1. População Inicial (1 instrução aleatória podada por indivíduo)
        List<ReductionIndividual> population = createInitialPopulation(
                prunableNodes, originalCu, targetClassFullName, auxiliarySources, testClassNames
        );

        Collections.sort(population);
        ReductionIndividual bestOverall = population.get(0).copyWithNewId(9999);

        // 2. Loop de Gerações Genéticas
        for (int gen = 0; gen < maxGenerations; gen++) {
            List<ReductionIndividual> nextGen = new ArrayList<>();

            // Elitismo: preserva top 10%
            int eliteCount = Math.max(1, populationSize / 10);
            for (int i = 0; i < eliteCount && i < population.size(); i++) {
                nextGen.add(population.get(i).copyWithNewId(nextGen.size()));
            }

            // Reprodução / Crossover / Mutação
            while (nextGen.size() < populationSize) {
                ReductionIndividual parent1 = tournamentSelect(population);
                ReductionIndividual parent2 = tournamentSelect(population);

                ReductionIndividual offspring;
                if (random.nextDouble() < crossoverRate) {
                    offspring = crossoverUnion(parent1, parent2, nextGen.size());
                } else {
                    offspring = parent1.copyWithNewId(nextGen.size());
                }

                if (random.nextDouble() < mutationRate) {
                    mutate(offspring, prunableNodes.size());
                }

                fitnessCalculator.evaluateFitness(
                        offspring, originalCu, targetClassFullName, prunableNodes, auxiliarySources, testClassNames
                );

                nextGen.add(offspring);
            }

            Collections.sort(nextGen);
            population = nextGen;

            if (population.get(0).getFitness() > bestOverall.getFitness()) {
                bestOverall = population.get(0).copyWithNewId(9999);
            }
        }

        // 3. Produz a AST final correspondente ao melhor indivíduo encontrado
        CompilationUnit bestPrunedCu = astPruner.applyPruning(
                originalCu, prunableNodes, bestOverall.getDeletedStatementIds()
        );

        return new ReductionResult(
                bestOverall,
                bestPrunedCu,
                prunableNodes.size(),
                bestOverall.getDeletedCount()
        );
    }

    private List<ReductionIndividual> createInitialPopulation(
            List<PrunableStatement> prunableNodes,
            CompilationUnit originalCu,
            String targetClassFullName,
            Map<String, String> auxiliarySources,
            List<String> testClassNames
    ) {
        List<ReductionIndividual> pop = new ArrayList<>();
        int totalNodes = prunableNodes.size();

        // Indivíduo zero: sem nenhuma deleção (baseline funcional)
        ReductionIndividual baseline = new ReductionIndividual(0);
        fitnessCalculator.evaluateFitness(
                baseline, originalCu, targetClassFullName, prunableNodes, auxiliarySources, testClassNames
        );
        pop.add(baseline);

        // Demais indivíduos: 1 deleção aleatória
        for (int i = 1; i < populationSize; i++) {
            ReductionIndividual ind = new ReductionIndividual(i);
            int randomStatementId = random.nextInt(totalNodes);
            ind.addDeletedStatement(randomStatementId);

            fitnessCalculator.evaluateFitness(
                    ind, originalCu, targetClassFullName, prunableNodes, auxiliarySources, testClassNames
            );
            pop.add(ind);
        }

        return pop;
    }

    private ReductionIndividual tournamentSelect(List<ReductionIndividual> pop) {
        ReductionIndividual best = null;
        for (int i = 0; i < tournamentSize; i++) {
            ReductionIndividual candidate = pop.get(random.nextInt(pop.size()));
            if (best == null || candidate.getFitness() > best.getFitness()) {
                best = candidate;
            }
        }
        return best != null ? best : pop.get(0);
    }

    private ReductionIndividual crossoverUnion(ReductionIndividual p1, ReductionIndividual p2, int newId) {
        Set<Integer> combinedDeletions = new HashSet<>(p1.getDeletedStatementIds());
        combinedDeletions.addAll(p2.getDeletedStatementIds());
        return new ReductionIndividual(newId, combinedDeletions);
    }

    private void mutate(ReductionIndividual ind, int totalNodes) {
        if (totalNodes <= 0) return;

        boolean addDeletion = random.nextBoolean() || ind.getDeletedCount() == 0;
        if (addDeletion) {
            // Mutação por corte: adiciona uma nova instrução à lista de poda
            int statementToPrune = random.nextInt(totalNodes);
            ind.addDeletedStatement(statementToPrune);
        } else {
            // Mutação por restauração: recupera uma instrução previamente deletada
            List<Integer> currentDeletions = new ArrayList<>(ind.getDeletedStatementIds());
            if (!currentDeletions.isEmpty()) {
                int toRestore = currentDeletions.get(random.nextInt(currentDeletions.size()));
                ind.removeDeletedStatement(toRestore);
            }
        }
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public void setMaxGenerations(int maxGenerations) {
        this.maxGenerations = maxGenerations;
    }

    public record ReductionResult(
            ReductionIndividual bestIndividual,
            CompilationUnit prunedCompilationUnit,
            int totalStatementsCount,
            int deletedStatementsCount
    ) {
        public double getReductionRatio() {
            if (totalStatementsCount <= 0) return 0.0;
            return (double) deletedStatementsCount / totalStatementsCount;
        }
    }
}
