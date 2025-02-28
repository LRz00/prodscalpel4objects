// =======================> START - PACKAGE <======================= //
package com.ifba.prodscalpel4objects.adapter;
// =======================> END - PACKAGE <======================= //

// =======================> START - IMPORTS <======================= //
import java.util.ArrayList;
import java.util.List;
// =======================> END - IMPORTS <======================= //

/**
 * Class that represents an Individual.
 *
 * @author Giovane Neves
 */
// =======================> START - CLASS <======================= //
public class GPIndividual {

    // =======================> START - ATTRIBUTES <======================= //
    private int id; // Individual's unique indentifier
    private double fitness; // Individual's fitness
    private GPIndividual next; // The next Individual
    private List<Integer> selectedLOCs; // The selected lines of code
    private List<Mapping> abstractMappings; // The Abstract Symbol Tree

    // ========================================================================= //
    // TODO: Estudar essas 4 variáveis
    //List<String> mappedDeclarations;
    //DeclarationSymbolTable declarationSymbolTable;
    //List<MappingCandidate> candidateMappings
    //List<MappingCandidate> multiPossibleMappings
    // ========================================================================= //

    // =======================> END - ATTRIBUTES <======================= //


    // =======================> START - CONSTRUCTOR <======================= //
    public GPIndividual() {
        this.selectedLOCs = new ArrayList<>();
    }
    // =======================> END - CONSTRUCTOR <======================= //

    // ========================================================================== //
    // ========================================================================== //
    // =======================> START - BOILERPLATE CODE <======================= //
    // ========================================================================== //
    // ========================================================================== //

    public int getId() {
        return id;
    } // getId

    public void setId(int id) {
        this.id = id;
    } // setId

    public double getFitness() {
        return fitness;
    } // getFitness

    public void setFitness(double fitness) {
        this.fitness = fitness;
    } // setFitness

    public GPIndividual getNext() {
        return next;
    }

    public void setNext(GPIndividual next) {
        this.next = next;
    } // getNext

    public List<Integer> getSelectedLOCs() {
        return selectedLOCs;
    } // getSelectedLOCs

    public void setSelectedLOCs(List<Integer> selectedLOCs) {
        this.selectedLOCs = selectedLOCs;
    } // setSelectedLOCs

    public List<Mapping> getAbstractMappings() {
        return abstractMappings;
    } // getAbstractMappings

    public void setAbstractMappings(List<Mapping> abstractMappings) {
        this.abstractMappings = abstractMappings;
    } // setAbstractMappings

    // =======================> END - BOILERPLATE CODE <======================= //
}
// =======================> END - CLASS <======================= //
