package com.ifba.prodscalpel4objects.adapter;

import com.ifba.prodscalpel4objects.adapter.model.AdaptedOrganArtifact;
import com.ifba.prodscalpel4objects.adapter.model.ExtractedOrganInfo;
import com.ifba.prodscalpel4objects.adapter.model.ImplantationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * Ponto de entrada utilitário para adaptação de métodos extraídos armazenados no IceBox.
 *
 * <p>Delega a execução para a fachada central {@link AdapterService}.</p>
 *
 * @author Giovane Neves
 */
public class MethodAdapter {

    private final Path iceBoxPath;
    private final AdapterService adapterService;

    public MethodAdapter(final String iceBoxPath) {
        this.iceBoxPath = Paths.get(iceBoxPath);
        this.adapterService = new AdapterService();
    }

    public MethodAdapter(final Path iceBoxPath) {
        this.iceBoxPath = iceBoxPath;
        this.adapterService = new AdapterService();
    }

    /**
     * Executa o fluxo de adaptação e redução para um método contido no IceBox.
     *
     * @param entryClassName Nome simples da classe no IceBox (ex: "ServiceClass").
     * @param entryMethodName Nome do método a ser adaptado (ex: "processData").
     * @param context Contexto de implantação no Host fornecido pelo Finder.
     * @param outputDir Diretório de saída para os artefatos adaptados.
     * @return {@link AdaptedOrganArtifact} resultante.
     * @throws IOException Se houver falha de leitura ou escrita.
     */
    public AdaptedOrganArtifact adapt(
            String entryClassName,
            String entryMethodName,
            ImplantationContext context,
            Path outputDir
    ) throws IOException {
        List<Path> extractedFiles = Collections.emptyList();
        if (Files.exists(iceBoxPath)) {
            try (var stream = Files.walk(iceBoxPath)) {
                extractedFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            }
        }

        ExtractedOrganInfo organInfo = new ExtractedOrganInfo(
                iceBoxPath,
                entryClassName,
                entryMethodName,
                entryMethodName,
                extractedFiles,
                Collections.emptyList()
        );

        return adapterService.adaptAndReduce(organInfo, context, outputDir);
    }

    public Path getIceBoxPath() {
        return iceBoxPath;
    }

    public AdapterService getAdapterService() {
        return adapterService;
    }
}
