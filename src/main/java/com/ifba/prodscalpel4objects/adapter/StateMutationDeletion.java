// StatementDeletionMutation.java
package com.ifba.prodscalpel4objects.adapter;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.ifba.prodscalpel4objects.adapter.OrganChromosome;
import io.jenetics.Alterer;
import io.jenetics.Genotype;
import io.jenetics.Phenotype;
import io.jenetics.util.MSeq;

import java.util.List;
import java.util.Random;

/**
 * Operador de Mutação que remove um Statement aleatório da AST.
 * Este é o principal operador para a poda (pruning) do super-órgão.
 */
public class StatementDeletionMutation implements Alterer<OrganChromosome, Double> {
    private final double probability;

    public StatementDeletionMutation(double probability) {
        this.probability = probability;
    }

    @Override
    public int alter(MSeq<Phenotype<OrganChromosome, Double>> population, long generation) {
        int alterations = 0;
        final Random random = new Random();

        for (int i = 0; i < population.size(); i++) {
            if (random.nextDouble() < probability) {
                Phenotype<OrganChromosome, Double> p = population.get(i);
                CompilationUnit ast = p.genotype().chromosome().getOrganAST().clone();

                List<Statement> statements = ast.findAll(Statement.class);
                // Evitar remover o último statement de um bloco para não criar blocos vazios inválidos
                statements.removeIf(s -> s.getParentNode().isPresent() && s.getParentNode().get() instanceof BlockStmt && ((BlockStmt)s.getParentNode().get()).getStatements().size() == 1);


                if (!statements.isEmpty()) {
                    Statement toDelete = statements.get(random.nextInt(statements.size()));
                    if (toDelete.remove()) {
                        Genotype<OrganChromosome> newGenotype = Genotype.of(new OrganChromosome(ast, p.genotype().chromosome().getOriginalOverOrganAST()));
                        population.set(i, Phenotype.of(newGenotype, generation));
                        alterations++;
                    }
                }
            }
        }
        return alterations;
    }
}