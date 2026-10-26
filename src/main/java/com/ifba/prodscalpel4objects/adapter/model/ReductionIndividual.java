package com.ifba.prodscalpel4objects.adapter.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Representa um indivíduo cromossômico no algoritmo genético de Melhoria Genética (GI)
 * para Redução de Sobrecarga de Código (Over-organ Reduction).
 *
 * <p>Cada gene é um identificador único de uma instrução (Statement ou FieldDeclaration)
 * da AST que foi marcada para deleção (poda).</p>
 *
 * @author Giovane Neves
 */
public class ReductionIndividual implements Comparable<ReductionIndividual> {

    private final int id;
    private final Set<Integer> deletedStatementIds;
    private double fitness;
    private boolean compiles;
    private int passedTestsCount;
    private int totalTestsCount;
    private long executionTimeMillis;

    /**
     * Construtor padrão para um novo indivíduo.
     *
     * @param id Identificador único.
     */
    public ReductionIndividual(int id) {
        this.id = id;
        this.deletedStatementIds = new HashSet<>();
        this.fitness = 0.0;
        this.compiles = false;
        this.passedTestsCount = 0;
        this.totalTestsCount = 0;
        this.executionTimeMillis = 0;
    }

    /**
     * Construtor com conjunto de instruções marcadas para deleção.
     *
     * @param id Identificador único.
     * @param deletedStatementIds Conjunto de IDs de instruções a serem podadas.
     */
    public ReductionIndividual(int id, Set<Integer> deletedStatementIds) {
        this.id = id;
        this.deletedStatementIds = deletedStatementIds != null
                ? new HashSet<>(deletedStatementIds)
                : new HashSet<>();
        this.fitness = 0.0;
        this.compiles = false;
        this.passedTestsCount = 0;
        this.totalTestsCount = 0;
        this.executionTimeMillis = 0;
    }

    /**
     * Cria uma cópia profunda (clone) do indivíduo com um novo ID.
     *
     * @param newId Novo ID para o indivíduo clonado.
     * @return Nova instância de ReductionIndividual com os mesmos genes.
     */
    public ReductionIndividual copyWithNewId(int newId) {
        ReductionIndividual copy = new ReductionIndividual(newId, this.deletedStatementIds);
        copy.setFitness(this.fitness);
        copy.setCompiles(this.compiles);
        copy.setPassedTestsCount(this.passedTestsCount);
        copy.setTotalTestsCount(this.totalTestsCount);
        copy.setExecutionTimeMillis(this.executionTimeMillis);
        return copy;
    }

    /**
     * Marca uma instrução para deleção (mutação por adição de corte).
     *
     * @param statementId ID da instrução na AST.
     * @return true se foi adicionada, false se já estava na lista.
     */
    public boolean addDeletedStatement(int statementId) {
        return this.deletedStatementIds.add(statementId);
    }

    /**
     * Remove uma instrução da lista de deleção, restaurando-a (mutação por restauração).
     *
     * @param statementId ID da instrução na AST.
     * @return true se foi removida, false se não estava na lista.
     */
    public boolean removeDeletedStatement(int statementId) {
        return this.deletedStatementIds.remove(statementId);
    }

    /**
     * Verifica se uma instrução está marcada para deleção.
     *
     * @param statementId ID da instrução.
     * @return true se estiver marcada para ser podada.
     */
    public boolean isStatementDeleted(int statementId) {
        return this.deletedStatementIds.contains(statementId);
    }

    public int getId() {
        return id;
    }

    public Set<Integer> getDeletedStatementIds() {
        return Collections.unmodifiableSet(deletedStatementIds);
    }

    public int getDeletedCount() {
        return deletedStatementIds.size();
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    public boolean isCompiles() {
        return compiles;
    }

    public void setCompiles(boolean compiles) {
        this.compiles = compiles;
    }

    public int getPassedTestsCount() {
        return passedTestsCount;
    }

    public void setPassedTestsCount(int passedTestsCount) {
        this.passedTestsCount = passedTestsCount;
    }

    public int getTotalTestsCount() {
        return totalTestsCount;
    }

    public void setTotalTestsCount(int totalTestsCount) {
        this.totalTestsCount = totalTestsCount;
    }

    public long getExecutionTimeMillis() {
        return executionTimeMillis;
    }

    public void setExecutionTimeMillis(long executionTimeMillis) {
        this.executionTimeMillis = executionTimeMillis;
    }

    @Override
    public int compareTo(ReductionIndividual other) {
        // Ordena em ordem decrescente de fitness (maior fitness primeiro)
        return Double.compare(other.fitness, this.fitness);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReductionIndividual that = (ReductionIndividual) o;
        return Objects.equals(deletedStatementIds, that.deletedStatementIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deletedStatementIds);
    }

    @Override
    public String toString() {
        return "ReductionIndividual{" +
                "id=" + id +
                ", deletedCount=" + deletedStatementIds.size() +
                ", fitness=" + String.format("%.4f", fitness) +
                ", compiles=" + compiles +
                ", tests=" + passedTestsCount + "/" + totalTestsCount +
                '}';
    }
}
