// =======================> START - PACKAGE <======================= //
package com.ifba.prodscalpel4objects.adapter;
// =======================> END - PACKAGE <======================= //

// =======================> START - IMPORTS <======================= //
import java.util.ArrayList;
import java.util.List;
// =======================> END - IMPORTS <======================= //

/**
 *  INFO: List of candidate mappings between donor symbols and possible host equivalents.
 *  INFO: Used to explore different combinations during evolution.
 *  SAMPLE: The donor variable 'counter' can be mapped to 'cont' or 'cnt' in the host.
 * <p>
 * @author Giovane Neves
 */
// =======================> START - CLASS <======================= //
public class MappingCandidate {
    private String donorSymbol;
    private List<String> possibleHostSymbols;

    public MappingCandidate(String donorSymbol, List<String> possibleHostSymbols) {
        this.donorSymbol = donorSymbol;
        this.possibleHostSymbols = new ArrayList<>(possibleHostSymbols);
    }
}
// =======================> END - CLASS <======================= //