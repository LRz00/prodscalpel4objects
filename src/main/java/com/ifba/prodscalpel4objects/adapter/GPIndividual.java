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
    private int id; // Individual's unique identifier
    private double fitness; // Individual's fitness
    private GPIndividual next; // The next Individual
    private List<Integer> selectedLOCs; // The selected lines of code
    private List<Mapping> abstractMappings; // The Abstract Symbol Tree
    List<String> mappedDeclarations; // Stores mapped declarations. Ex: (“Interger data -> int data”)
    DeclarationSymbolTable declarationSymbolTable; // Trace donor declarations
    List<MappingCandidate> candidateMappings; // Explore different combinations during evolution. EX: changing the name of a variable.
    List<MappingCandidate> multiPossibleMappings; // Explores possibilities of using an equivalent variable on the host

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
