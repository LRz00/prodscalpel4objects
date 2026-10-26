package com.ifba.prodscalpel4objects.adapter.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representa um indivíduo cromossômico no algoritmo de Programação Genética (GP)
 * para Adaptação de Métodos ao Contexto de Destino (Organ Adaptation).
 *
 * <p>O cromossomo contém o conjunto de mapeamentos entre parâmetros do órgão
 * e variáveis/beans do sistema hospedeiro, além do código sintetizado do Wrapper.</p>
 *
 * @author Giovane Neves
 */
public class AdaptationIndividual implements Comparable<AdaptationIndividual> {

    private final int id;
    private final List<MappingCandidate> mappings;
    private String synthesizedWrapperCode;
    private double fitness;
    private boolean compiles;
    private boolean testsPass;
    private int passedTestsCount;
    private int totalTestsCount;

    public AdaptationIndividual(int id) {
        this.id = id;
        this.mappings = new ArrayList<>();
        this.synthesizedWrapperCode = "";
        this.fitness = 0.0;
        this.compiles = false;
        this.testsPass = false;
        this.passedTestsCount = 0;
        this.totalTestsCount = 0;
    }

    public AdaptationIndividual(int id, List<MappingCandidate> mappings) {
        this.id = id;
        this.mappings = mappings != null ? new ArrayList<>(mappings) : new ArrayList<>();
        this.synthesizedWrapperCode = "";
        this.fitness = 0.0;
        this.compiles = false;
        this.testsPass = false;
        this.passedTestsCount = 0;
        this.totalTestsCount = 0;
    }

    public AdaptationIndividual copyWithNewId(int newId) {
        AdaptationIndividual copy = new AdaptationIndividual(newId, this.mappings);
        copy.setSynthesizedWrapperCode(this.synthesizedWrapperCode);
        copy.setFitness(this.fitness);
        copy.setCompiles(this.compiles);
        copy.setTestsPass(this.testsPass);
        copy.setPassedTestsCount(this.passedTestsCount);
        copy.setTotalTestsCount(this.totalTestsCount);
        return copy;
    }

    public void addMapping(MappingCandidate mapping) {
        if (mapping != null) {
            this.mappings.add(mapping);
        }
    }

    public int getId() {
        return id;
    }

    public List<MappingCandidate> getMappings() {
        return Collections.unmodifiableList(mappings);
    }

    public String getSynthesizedWrapperCode() {
        return synthesizedWrapperCode;
    }

    public void setSynthesizedWrapperCode(String synthesizedWrapperCode) {
        this.synthesizedWrapperCode = synthesizedWrapperCode;
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

    public boolean isTestsPass() {
        return testsPass;
    }

    public void setTestsPass(boolean testsPass) {
        this.testsPass = testsPass;
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

    @Override
    public int compareTo(AdaptationIndividual other) {
        return Double.compare(other.fitness, this.fitness);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AdaptationIndividual that = (AdaptationIndividual) o;
        return Objects.equals(mappings, that.mappings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mappings);
    }

    @Override
    public String toString() {
        return "AdaptationIndividual{" +
                "id=" + id +
                ", mappingsCount=" + mappings.size() +
                ", fitness=" + String.format("%.4f", fitness) +
                ", compiles=" + compiles +
                ", testsPass=" + testsPass +
                '}';
    }
}
