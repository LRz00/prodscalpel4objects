package com.ifba.prodscalpel4objects.adapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe que representa um individuo.
 * @author Giovane Neves
 */
public class GPIndividual {

    /**
     * Individual's id
     */
    private int id;

    /**
     * The individual's fitness
     */
    private double fitness;
    private GPIndividual next;
    private List<Integer> selectedLOCs;

    public GPIndividual() {
        this.selectedLOCs = new ArrayList<>();
    }

    // Boilerplate code

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    public GPIndividual getNext() {
        return next;
    }

    public void setNext(GPIndividual next) {
        this.next = next;
    }

    public List<Integer> getSelectedLOCs() {
        return selectedLOCs;
    }

    public void setSelectedLOCs(List<Integer> selectedLOCs) {
        this.selectedLOCs = selectedLOCs;
    }
}
