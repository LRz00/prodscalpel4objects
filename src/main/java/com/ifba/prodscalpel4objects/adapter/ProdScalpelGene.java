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

import io.jenetics.Gene;

import java.util.List;
import java.util.Objects;
import java.util.Random;

// ======================================= //
// ========== [ IMPORTS - END ] ========== //
// ======================================= //

/**
 * Basic unit of the genetic material
 * Represents a gene in the ProdScalpel genetic algorithm.
 * The allele value is a GeneAllele, which can be a selection (method/field)
 * or a binding (host variable to organ parameter).
 * @author Giovane Neves
 */
public class ProdScalpelGene implements Gene<GeneAllele, ProdScalpelGene> {

    // ======================================= //
    // =========== [ ATTRIBUTES ] ============ //
    // ======================================= //

    private final GeneAllele allele;

    // Context Initialization for newInstance()
    private static List<String> availableMethodFieldIds;
    private static List<String> availableHostVariables;
    private static List<String> availableOrganParameters;
    private static final Random random = new Random();

    // ======================================= //
    // ======== [ ATTRIBUTES - END ] ========= //
    // ======================================= //


    public static void initializeContext(List<String> methodFieldIds, List<String> hostVars, List<String> organParams) {
        availableMethodFieldIds = methodFieldIds;
        availableHostVariables = hostVars;
        availableOrganParameters = organParams;
    }

    // ======================================= //
    // =========== [ CONSTRUCTOR ] =========== //
    // ======================================= //

    private ProdScalpelGene(final GeneAllele allele) {
        this.allele = Objects.requireNonNull(allele);
    }

    public static ProdScalpelGene of(final GeneAllele allele) {
        return new ProdScalpelGene(allele);
    }

    // ======================================= //
    // ======== [ CONSTRUCTOR - END ] ======== //
    // ======================================= //

    // ======================================= //
    // ========= [ GETTERS/SETTERS ] ========= //
    // ======================================= //

    @Override
    public GeneAllele allele() {
        return allele;
    }

    @Override
    public boolean isValid() {
        // TODO: Lógica de validação mais complexa aqui
        return true;
    }


    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final ProdScalpelGene other = (ProdScalpelGene) obj;
        return Objects.equals(allele, other.allele);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allele);
    }

    @Override
    public String toString() {
        return allele.toString();
    }

    // ======================================= //
    // ====== [ GETTERS/SETTERS - END ] ====== //
    // ======================================= //

    // ======================================= //
    // ============= [ METHODS ] ============= //
    // ======================================= //

    @Override
    public ProdScalpelGene newInstance(final GeneAllele allele) {
        return ProdScalpelGene.of(allele);
    }


    @Override
    public ProdScalpelGene newInstance() {
        if (availableMethodFieldIds == null || availableHostVariables == null || availableOrganParameters == null) {
            throw new IllegalStateException("ProdScalpelGene context not initialized. Call initializeContext() first with Organo's context.");
        }

        // Simula a criação de um gene de seleção ou de binding aleatoriamente
        if (random.nextBoolean() && !availableMethodFieldIds.isEmpty()) {
            String id = availableMethodFieldIds.get(random.nextInt(availableMethodFieldIds.size()));
            boolean selected = random.nextBoolean();
            return ProdScalpelGene.of(new GeneAllele(id, selected));
        } else if (!availableHostVariables.isEmpty() && !availableOrganParameters.isEmpty()){
            String hostVar = availableHostVariables.get(random.nextInt(availableHostVariables.size()));
            String organParam = availableOrganParameters.get(random.nextInt(availableOrganParameters.size()));
            return ProdScalpelGene.of(new GeneAllele(hostVar, organParam));
        }

        throw new IllegalStateException("Could not create a valid random ProdScalpelGene. Check context initialization or available options.");
    }

    // ======================================= //
    // =========== [ METHODS - END ] ========= //
    // ======================================= //



}
