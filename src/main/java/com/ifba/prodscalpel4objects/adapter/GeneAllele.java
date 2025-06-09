// ======================================= //
// ============= [ PACKAGE ] ============= //
// ======================================= //

package com.ifba.prodscalpel4objects.adapter;

// ======================================= //
// ========== [ PACKAGE - END ] ========== //
// ======================================= //


// ======================================= //
// ============= [ IMPORTS ] ============= //
// ======================================= //

import java.util.Objects;
import java.util.Optional;

// ======================================= //
// ========== [ IMPORTS - END ] ========== //
// ======================================= //

/**
 * Represents the allele value for a ProdScalpelGene.
 * Can represent either a method/field selection or a binding.
 * @author Giovane Neves
 */
public class GeneAllele {


    // ======================================= //
    // =========== [ ATTRIBUTES ] ============ //
    // ======================================= //

    // Ids or Fields
    private final Optional<String> methodOrFieldId; // Ex: "com.example.MyClass.myMethod(int)" ou "com.example.MyClass.myField"
    private final Optional<Boolean> isSelected; // 'true' if selected, 'false' otherwise

    // For binding infos
    private final Optional<String> hostVariableName;
    private final Optional<String> organParameterName;

    // ======================================= //
    // ======== [ ATTRIBUTES - END ] ========= //
    // ======================================= //

    // ======================================= //
    // =========== [ CONSTRUCTOR ] =========== //
    // ======================================= //

    // Constructor for medthod/field selection
    public GeneAllele(String methodOrFieldId, boolean isSelected) {
        this.methodOrFieldId = Optional.of(methodOrFieldId);
        this.isSelected = Optional.of(isSelected);
        this.hostVariableName = Optional.empty();
        this.organParameterName = Optional.empty();
    }

    // Constructor for binding
    public GeneAllele(String hostVariableName, String organParameterName) {
        this.methodOrFieldId = Optional.empty();
        this.isSelected = Optional.empty();
        this.hostVariableName = Optional.of(hostVariableName);
        this.organParameterName = Optional.of(organParameterName);
    }

    // ======================================= //
    // ======== [ CONSTRUCTOR - END ] ======== //
    // ======================================= //


    // ======================================= //
    // ========= [ GETTERS/SETTERS ] ========= //
    // ======================================= //

    public Optional<String> getMethodOrFieldId() {
        return methodOrFieldId;
    }

    public Optional<Boolean> isSelected() {
        return isSelected;
    }

    public Optional<String> getHostVariableName() {
        return hostVariableName;
    }

    public Optional<String> getOrganParameterName() {
        return organParameterName;
    }

    public boolean isSelectionAllele() {
        return methodOrFieldId.isPresent();
    }

    public boolean isBindingAllele() {
        return hostVariableName.isPresent();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GeneAllele that = (GeneAllele) o;
        return Objects.equals(methodOrFieldId, that.methodOrFieldId) &&
                Objects.equals(isSelected, that.isSelected) &&
                Objects.equals(hostVariableName, that.hostVariableName) &&
                Objects.equals(organParameterName, that.organParameterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodOrFieldId, isSelected, hostVariableName, organParameterName);
    }

    @Override
    public String toString() {
        if (isSelectionAllele()) {
            return String.format("SelectionGene[id=%s, selected=%b]", methodOrFieldId.get(), isSelected.get());
        } else if (isBindingAllele()) {
            return String.format("BindingGene[host=%s, organ=%s]", hostVariableName.get(), organParameterName.get());
        }
        return "EmptyGeneAllele";
    }

    // ======================================= //
    // ====== [ GETTERS/SETTERS - END ] ====== //
    // ======================================= //



}
