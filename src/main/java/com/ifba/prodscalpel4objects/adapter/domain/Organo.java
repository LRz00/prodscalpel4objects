// ======================================= //
// ============= [ PACKAGE ] ============= //
// ======================================= //

package com.ifba.prodscalpel4objects.adapter.domain;

// ======================================= //
// ========== [ PACKAGE - END ] ========== //
// ======================================= //

// ======================================= //
// ============= [ IMPORTS ] ============= //
// ======================================= //

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.List;

// ======================================= //
// ========== [ IMPORTS - END ] ========== //
// ======================================= //

/**
 * The extracted “over-organ” and its possible variations during evolution.
 * @author Giovane Neves
 */
public class Organo {

    // ======================================= //
    // =========== [ ATTRIBUTES ] ============ //
    // ======================================= //

    private CompilationUnit originalOverOrganCU; // The full AST of the over-organ
    private List<MethodDeclaration> candidateMethods; // Methods that can be selected
    private List<FieldDeclaration> candidateFields; // Fields that can be selected/referenced

    // ======================================= //
    // ======== [ ATTRIBUTES - END ] ========= //
    // ======================================= //

    // ======================================= //
    // =========== [ CONSTRUCTOR ] =========== //
    // ======================================= //

    public Organo(CompilationUnit originalCU, List<MethodDeclaration> methods, List<FieldDeclaration> fields) {
        this.originalOverOrganCU = originalCU;
        this.candidateMethods = methods;
        this.candidateFields = fields;
    }

    // ======================================= //
    // ======== [ CONSTRUCTOR - END ] ======== //
    // ======================================= //

    // ======================================= //
    // ========= [ GETTERS/SETTERS ] ========= //
    // ======================================= //

    public CompilationUnit getOriginalOverOrganCU() {
        return originalOverOrganCU;
    }

    public List<MethodDeclaration> getCandidateMethods() {
        return candidateMethods;
    }

    public List<FieldDeclaration> getCandidateFields() {
        return candidateFields;
    }

    // ======================================= //
    // ====== [ GETTERS/SETTERS - END ] ====== //
    // ======================================= //
}
