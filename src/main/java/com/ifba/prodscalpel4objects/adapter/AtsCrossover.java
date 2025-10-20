// ASTCrossover.java
package com.ifba.prodscalpel4objects.adapter;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.Statement;
import com.ifba.prodscalpel4objects.adapter.OrganChromosome;
import io.jenetics.Alterer;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.util.MSeq;

import java.util.List;
import java.util.Random;

/**
 * Operador de Crossover que troca sub-árvores (nós) entre duas ASTs parentais.
 * Foca em trocar nós de tipo compatível (ex: Statement com Statement).
 */
public class ASTCrossover implements Alterer<OrganChromosome, Double> {
    private final double probability;

    public ASTCrossover(double probability) {
        this.probability = probability;
    }

    @Override
    public int alter(MSeq<Phenotype<OrganChromosome, Double>> population, long generation) {
        // Implementação simplificada de crossover
        // Uma implementação robusta usaria io.jenetics.Crossover
        int alterations = 0;
        final Random random = new Random();

        if (population.size() >= 2) {
            for (int i = 0; i < population.size() - 1; i += 2) {
                if (random.nextDouble() < probability) {
                    Phenotype<OrganChromosome, Double> p1 = population.get(i);
                    Phenotype<OrganChromosome, Double> p2 = population.get(i + 1);

                    // Clonar ASTs para evitar modificar os pais originais
                    CompilationUnit ast1 = p1.genotype().chromosome().getOrganAST().clone();
                    CompilationUnit ast2 = p2.genotype().chromosome().getOrganAST().clone();

                    // Encontrar todos os nós de um tipo específico (ex: Statements)
                    List<Statement> statements1 = ast1.findAll(Statement.class);
                    List<Statement> statements2 = ast2.findAll(Statement.class);

                    if (!statements1.isEmpty() &&!statements2.isEmpty()) {
                        // Selecionar pontos de crossover aleatórios
                        Statement crossPoint1 = statements1.get(random.nextInt(statements1.size()));
                        Statement crossPoint2 = statements2.get(random.nextInt(statements2.size()));

                        // Trocar os nós
                        Node parent1 = crossPoint1.getParentNode().orElse(null);
                        Node parent2 = crossPoint2.getParentNode().orElse(null);

                        if (parent1!= null && parent2!= null) {
                            parent1.replace(crossPoint1, crossPoint2.clone());
                            parent2.replace(crossPoint2, crossPoint1.clone());

                            // Criar novos filhos
                            Genotype<OrganChromosome> child1 = Genotype.of(new OrganChromosome(ast1, p1.genotype().chromosome().getOriginalOverOrganAST()));
                            Genotype<OrganChromosome> child2 = Genotype.of(new OrganChromosome(ast2, p2.genotype().chromosome().getOriginalOverOrganAST()));

                            population.set(i, Phenotype.of(child1, generation));
                            population.set(i + 1, Phenotype.of(child2, generation));
                            alterations += 2;
                        }
                    }
                }
            }
        }
        return alterations;
    }
}