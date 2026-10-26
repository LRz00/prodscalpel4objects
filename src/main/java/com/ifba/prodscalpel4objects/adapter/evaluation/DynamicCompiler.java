package com.ifba.prodscalpel4objects.adapter.evaluation;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compilador dinâmico em memória e diretório temporário para avaliação rápida
 * de variantes de código geradas pelos algoritmos genéticos (GI e GP).
 *
 * <p>Utiliza a API padrão {@link JavaCompiler} nativa do JDK, eliminando a
 * necessidade de invocar o Maven externamente a cada indivíduo da população.</p>
 *
 * @author Giovane Neves
 */
public class DynamicCompiler {

    private final JavaCompiler compiler;
    private final List<String> additionalClasspathEntries;

    /**
     * Construtor padrão que inicializa o compilador do sistema.
     */
    public DynamicCompiler() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new IllegalStateException("Nenhum JavaCompiler disponível no ambiente. Certifique-se de estar executando em um JDK (não apenas JRE).");
        }
        this.additionalClasspathEntries = new ArrayList<>();
    }

    /**
     * Adiciona uma entrada (pasta ou arquivo .jar) ao classpath da compilação dinâmica.
     *
     * @param path Caminho da pasta ou arquivo .jar.
     */
    public void addClasspathEntry(String path) {
        if (path != null && !path.trim().isEmpty()) {
            this.additionalClasspathEntries.add(path);
        }
    }

    /**
     * Compila uma lista de arquivos Java existentes em disco.
     *
     * @param sourceFiles Lista de arquivos .java a serem compilados.
     * @return {@link CompilationResult} com status, diretório de saída e diagnósticos.
     */
    public CompilationResult compileFiles(List<Path> sourceFiles) {
        long startTime = System.currentTimeMillis();

        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return CompilationResult.failure(Collections.emptyList(), 0);
        }

        try {
            Path outputDir = Files.createTempDirectory("prodscalpel_compile_");
            DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);

            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);

            List<String> options = buildCompilerOptions(outputDir);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnosticCollector,
                    options,
                    null,
                    compilationUnits
            );

            boolean success = Boolean.TRUE.equals(task.call());
            fileManager.close();

            long elapsed = System.currentTimeMillis() - startTime;
            List<CompilationDiagnostic> diagnostics = diagnosticCollector.getDiagnostics().stream()
                    .map(CompilationDiagnostic::fromJavaDiagnostic)
                    .collect(Collectors.toList());

            if (success) {
                List<String> compiledClasses = scanCompiledClasses(outputDir);
                return new CompilationResult(true, outputDir, diagnostics, compiledClasses, elapsed);
            } else {
                return CompilationResult.failure(diagnostics, elapsed);
            }

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            CompilationDiagnostic error = new CompilationDiagnostic(
                    Diagnostic.Kind.ERROR, 0, 0, "Falha de E/S na compilação: " + e.getMessage(), "compiler"
            );
            return CompilationResult.failure(List.of(error), elapsed);
        }
    }

    /**
     * Compila fontes fornecidos diretamente como Strings em memória.
     *
     * @param sourcesMap Mapa de NomeQualificadoDaClasse -> CódigoFonteJava (ex: "com.ifba.MyClass" -> "public class MyClass {...}").
     * @return {@link CompilationResult} com o resultado da compilação.
     */
    public CompilationResult compileSources(Map<String, String> sourcesMap) {
        long startTime = System.currentTimeMillis();

        if (sourcesMap == null || sourcesMap.isEmpty()) {
            return CompilationResult.failure(Collections.emptyList(), 0);
        }

        try {
            Path outputDir = Files.createTempDirectory("prodscalpel_compile_mem_");
            DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);

            List<JavaFileObject> compilationUnits = sourcesMap.entrySet().stream()
                    .map(entry -> new StringJavaFileObject(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            List<String> options = buildCompilerOptions(outputDir);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnosticCollector,
                    options,
                    null,
                    compilationUnits
            );

            boolean success = Boolean.TRUE.equals(task.call());
            fileManager.close();

            long elapsed = System.currentTimeMillis() - startTime;
            List<CompilationDiagnostic> diagnostics = diagnosticCollector.getDiagnostics().stream()
                    .map(CompilationDiagnostic::fromJavaDiagnostic)
                    .collect(Collectors.toList());

            if (success) {
                List<String> compiledClasses = scanCompiledClasses(outputDir);
                return new CompilationResult(true, outputDir, diagnostics, compiledClasses, elapsed);
            } else {
                return CompilationResult.failure(diagnostics, elapsed);
            }

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            CompilationDiagnostic error = new CompilationDiagnostic(
                    Diagnostic.Kind.ERROR, 0, 0, "Falha de E/S na compilação em memória: " + e.getMessage(), "compiler"
            );
            return CompilationResult.failure(List.of(error), elapsed);
        }
    }

    private List<String> buildCompilerOptions(Path outputDir) {
        List<String> options = new ArrayList<>();
        options.add("-d");
        options.add(outputDir.toAbsolutePath().toString());
        options.add("-proc:none"); // Desativa processadores de anotação pesados no loop genético

        String baseClasspath = System.getProperty("java.class.path");
        StringBuilder cpBuilder = new StringBuilder(baseClasspath);

        for (String extraCp : additionalClasspathEntries) {
            cpBuilder.append(File.pathSeparator).append(extraCp);
        }

        options.add("-cp");
        options.add(cpBuilder.toString());
        return options;
    }

    private List<String> scanCompiledClasses(Path outputDir) throws IOException {
        List<String> classes = new ArrayList<>();
        try (var stream = Files.walk(outputDir)) {
            stream.filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> {
                        Path rel = outputDir.relativize(p);
                        String className = rel.toString()
                                .replace(File.separatorChar, '.')
                                .replace(".class", "");
                        classes.add(className);
                    });
        }
        return classes;
    }

    /**
     * Representação em memória de um arquivo de código-fonte Java para o compilador.
     */
    private static class StringJavaFileObject extends SimpleJavaFileObject {
        private final String sourceCode;

        public StringJavaFileObject(String className, String sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = Objects.requireNonNull(sourceCode);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.sourceCode;
        }
    }
}
