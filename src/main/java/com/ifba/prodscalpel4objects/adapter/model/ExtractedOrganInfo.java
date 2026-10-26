package com.ifba.prodscalpel4objects.adapter.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsula as informações sobre o sobre-órgão (over-organ) extraído
 * pelo módulo Extractor e armazenado no IceBox, juntamente com sua suíte de testes.
 *
 * @param iceBoxRoot Diretório raiz do IceBox onde os artefatos foram depositados.
 * @param entryClassName Nome simples da classe que contém o método principal extraído (ex: "ServiceClass").
 * @param entryMethodName Nome do método principal a ser transplantado (ex: "processData").
 * @param entryMethodSignature Assinatura completa do método de entrada.
 * @param extractedClassFiles Lista de caminhos para os arquivos .java pertencentes ao órgão.
 * @param testSuiteFiles Lista de caminhos para os arquivos de teste unitário associados ao órgão.
 *
 * @author Giovane Neves
 */
public record ExtractedOrganInfo(
        Path iceBoxRoot,
        String entryClassName,
        String entryMethodName,
        String entryMethodSignature,
        List<Path> extractedClassFiles,
        List<Path> testSuiteFiles
) {

    public ExtractedOrganInfo {
        Objects.requireNonNull(iceBoxRoot, "iceBoxRoot não pode ser nulo");
        Objects.requireNonNull(entryClassName, "entryClassName não pode ser nulo");
        Objects.requireNonNull(entryMethodName, "entryMethodName não pode ser nulo");

        extractedClassFiles = extractedClassFiles != null
                ? List.copyOf(extractedClassFiles)
                : Collections.emptyList();

        testSuiteFiles = testSuiteFiles != null
                ? List.copyOf(testSuiteFiles)
                : Collections.emptyList();
    }

    /**
     * Localiza o arquivo .java correspondente à classe de entrada entre os arquivos extraídos.
     *
     * @return Path do arquivo da classe de entrada, ou null se não localizado.
     */
    public Path getEntryClassPath() {
        String expectedFileName = entryClassName.endsWith(".java") ? entryClassName : entryClassName + ".java";
        return extractedClassFiles.stream()
                .filter(p -> p.getFileName().toString().equals(expectedFileName))
                .findFirst()
                .orElse(null);
    }
}
