package com.ifba.prodscalpel4objects.adapter;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.ifba.prodscalpel4objects.adapter.adaptation.OrganMethodAdapter;
import com.ifba.prodscalpel4objects.adapter.model.AdaptedOrganArtifact;
import com.ifba.prodscalpel4objects.adapter.model.ExtractedOrganInfo;
import com.ifba.prodscalpel4objects.adapter.model.ImplantationContext;
import com.ifba.prodscalpel4objects.adapter.reduction.GIReductionEngine;
import com.ifba.prodscalpel4objects.adapter.reduction.OverOrganReducer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Fachada principal (Facade) do módulo Adapter do ProdScalpel4Objects.
 *
 * <p>Unifica em uma única interface os dois pilares centrais do transplante de software:</p>
 * <ol>
 *   <li><b>Redução de Sobrecarga (Over-organ Reduction):</b> Poda de nós e instruções supérfluas da AST
 *       via algoritmo genético de Melhoria Genética (GI).</li>
 *   <li><b>Adaptação ao Contexto (Organ Adaptation):</b> Mapeamento de variáveis/beans e síntese
 *       automatizada do Organ-Host Wrapper em Spring Boot (@Component).</li>
 * </ol>
 *
 * @author Giovane Neves
 */
public class AdapterService {

    private final OverOrganReducer reducer;
    private final OrganMethodAdapter adapter;

    /**
     * Construtor padrão que inicializa os motores de redução e adaptação.
     */
    public AdapterService() {
        this.reducer = new OverOrganReducer();
        this.adapter = new OrganMethodAdapter();
    }

    public AdapterService(OverOrganReducer reducer, OrganMethodAdapter adapter) {
        this.reducer = Objects.requireNonNull(reducer);
        this.adapter = Objects.requireNonNull(adapter);
    }

    /**
     * Executa o fluxo completo do Adapter: Redução de Sobrecarga (GI) seguida
     * de Adaptação Contextual e Síntese de Wrapper (Spring Boot).
     *
     * @param organInfo Informações do sobre-órgão no IceBox (provenientes do Extractor).
     * @param context Informações do ponto de enxerto no Host (provenientes do Finder).
     * @param outputDirectory Diretório onde os arquivos finais adaptados serão gravados.
     * @return {@link AdaptedOrganArtifact} pronto para entrega ao Implementer.
     * @throws IOException Se houver falha de leitura ou escrita.
     */
    public AdaptedOrganArtifact adaptAndReduce(
            ExtractedOrganInfo organInfo,
            ImplantationContext context,
            Path outputDirectory
    ) throws IOException {
        Objects.requireNonNull(organInfo, "organInfo não pode ser nulo");
        Objects.requireNonNull(context, "context não pode ser nulo");
        Objects.requireNonNull(outputDirectory, "outputDirectory não pode ser nulo");

        System.out.println("\n[PRODSCALPEL4OBJECTS - ADAPTER] Iniciando fluxo de Redução e Adaptação...");
        System.out.println("  -> Órgão Alvo: " + organInfo.entryClassName() + "::" + organInfo.entryMethodName());
        System.out.println("  -> Host Destino: " + context.targetClassName() + " (linha " + context.insertionLineNumber() + ")");

        // =========================================================================
        // ETAPA 1: Redução de Sobrecarga de Código (Over-organ Reduction via GI)
        // =========================================================================
        System.out.println("\n[ADAPTER - ETAPA 1/2] Executando Redução de Sobrecarga (Genetic Improvement)...");
        GIReductionEngine.ReductionResult reductionResult = reducer.reduceOrgan(organInfo);

        System.out.println("  -> Instruções originais: " + reductionResult.totalStatementsCount());
        System.out.println("  -> Instruções podadas: " + reductionResult.deletedStatementsCount());
        System.out.println("  -> Taxa de Redução (RR): " + String.format("%.2f%%", reductionResult.getReductionRatio() * 100));
        System.out.println("  -> Fitness de Redução: " + String.format("%.4f", reductionResult.bestIndividual().getFitness()));

        // =========================================================================
        // ETAPA 2: Adaptação ao Contexto e Síntese do Wrapper (Spring Boot)
        // =========================================================================
        System.out.println("\n[ADAPTER - ETAPA 2/2] Executando Adaptação de Contexto e Síntese de Wrapper...");
        AdaptedOrganArtifact artifact = adapter.adapt(
                organInfo,
                reductionResult.prunedCompilationUnit(),
                context,
                outputDirectory,
                reductionResult.totalStatementsCount(),
                reductionResult.deletedStatementsCount()
        );

        System.out.println("  -> Wrapper gerado: " + artifact.wrapperClassName());
        System.out.println("  -> Método sintetizado: " + artifact.wrapperMethodSignature());
        System.out.println("  -> Arquivos finais no pacote: " + artifact.generatedAndCleanedFiles().size());
        System.out.println("  -> Hook sugerido gerado com sucesso.");
        System.out.println("[PRODSCALPEL4OBJECTS - ADAPTER] Processamento concluído com sucesso!\n");

        return artifact;
    }

    /**
     * Sobrecarga de conveniência que gera um diretório de saída temporário.
     */
    public AdaptedOrganArtifact adaptAndReduce(
            ExtractedOrganInfo organInfo,
            ImplantationContext context
    ) throws IOException {
        Path tempOutput = Files.createTempDirectory("adapted_organ_final_");
        return adaptAndReduce(organInfo, context, tempOutput);
    }

    /**
     * Executa exclusivamente a etapa de Redução de Sobrecarga (GI).
     */
    public GIReductionEngine.ReductionResult reduceOnly(ExtractedOrganInfo organInfo) throws IOException {
        return reducer.reduceOrgan(organInfo);
    }

    public OverOrganReducer getReducer() {
        return reducer;
    }

    public OrganMethodAdapter getAdapter() {
        return adapter;
    }
}
