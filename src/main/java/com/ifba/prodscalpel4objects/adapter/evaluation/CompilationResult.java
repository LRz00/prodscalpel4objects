package com.ifba.prodscalpel4objects.adapter.evaluation;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Encapsula o resultado de uma operação de compilação dinâmica em memória.
 *
 * @param success Indica se a compilação foi bem-sucedida (sem erros de sintaxe ou tipos).
 * @param outputDirectory Diretório temporário contendo os arquivos .class compilados.
 * @param diagnostics Lista de mensagens de diagnóstico (erros, avisos, notas) geradas pelo compilador.
 * @param compiledClassNames Lista dos nomes totalmente qualificados das classes compiladas.
 * @param compilationTimeMillis Tempo gasto na compilação em milissegundos.
 *
 * @author Giovane Neves
 */
public record CompilationResult(
        boolean success,
        Path outputDirectory,
        List<CompilationDiagnostic> diagnostics,
        List<String> compiledClassNames,
        long compilationTimeMillis
) {

    public CompilationResult {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : Collections.emptyList();
        compiledClassNames = compiledClassNames != null ? List.copyOf(compiledClassNames) : Collections.emptyList();
    }

    public static CompilationResult success(Path outputDirectory, List<String> classNames, long timeMillis) {
        return new CompilationResult(true, outputDirectory, Collections.emptyList(), classNames, timeMillis);
    }

    public static CompilationResult failure(List<CompilationDiagnostic> diagnostics, long timeMillis) {
        return new CompilationResult(false, null, diagnostics, Collections.emptyList(), timeMillis);
    }

    /**
     * Retorna apenas os diagnósticos classificados como ERRO.
     */
    public List<CompilationDiagnostic> getErrors() {
        return diagnostics.stream()
                .filter(CompilationDiagnostic::isError)
                .collect(Collectors.toList());
    }

    /**
     * Retorna uma representação legível das mensagens de erro.
     */
    public String getFormattedErrors() {
        return getErrors().stream()
                .map(d -> String.format("[%s:%d:%d] %s", d.sourceName(), d.lineNumber(), d.columnNumber(), d.message()))
                .collect(Collectors.joining("\n"));
    }
}
