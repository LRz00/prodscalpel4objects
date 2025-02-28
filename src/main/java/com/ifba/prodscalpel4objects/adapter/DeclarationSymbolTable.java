// =======================> START - PACKAGE <======================= //
package com.ifba.prodscalpel4objects.adapter;
// =======================> END - PACKAGE <======================= //

// =======================> START - IMPORTS <======================= //
import java.util.HashMap;
import java.util.Map;
// =======================> END - IMPORTS <======================= //

/**
 * INFO: Symbol table that tracks donor declarations (variables, functions, types) and their correspondences in the host.
 * INFO: Used to resolve references during code adaptation.
 * SAMPLE: Maps function calculateMedia() (donor) to methodCalculateMedia() (host).
 * <p>
 * @author Giovane Neves
 */
// =======================> START - CLASS <======================= //
public class DeclarationSymbolTable {

    // =======================> START - ATTRIBUTES <======================= //
    private final Map<String, String> symbolMap = new HashMap<>();
    // =======================> END - ATTRIBUTES <======================= //

    public void addMapping(String donorSymbol, String hostSymbol) {
        symbolMap.put(donorSymbol, hostSymbol);
    }

    public String getHostSymbol(String donorSymbol) {
        return symbolMap.get(donorSymbol);
    }

}
// =======================> END - CLASS <======================= //