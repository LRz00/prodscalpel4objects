package com.ifba.prodscalpel4objects.adapter.evaluation;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Representa uma mensagem de diagnóstico gerada durante a compilação dinâmica
 * (erro, aviso, nota).
 *
 * @param kind Tipo do diagnóstico (ERROR, WARNING, NOTE, etc.).
 * @param lineNumber Número da linha do erro/aviso.
 * @param columnNumber Número da coluna do erro/aviso.
 * @param message Descrição textual do diagnóstico.
 * @param sourceName Nome do arquivo ou unidade de código fonte associada.
 *
 * @author Giovane Neves
 */
public record CompilationDiagnostic(
        Diagnostic.Kind kind,
        long lineNumber,
        long columnNumber,
        String message,
        String sourceName
) {

    public static CompilationDiagnostic fromJavaDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String source = diagnostic.getSource() != null ? diagnostic.getSource().getName() : "unknown";
        return new CompilationDiagnostic(
                diagnostic.getKind(),
                diagnostic.getLineNumber(),
                diagnostic.getColumnNumber(),
                diagnostic.getMessage(null),
                source
        );
    }

    public boolean isError() {
        return kind == Diagnostic.Kind.ERROR;
    }
}
