package com.ifba.prodscalpel4objects.adapter.adaptation;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.ifba.prodscalpel4objects.adapter.model.AdaptationIndividual;
import com.ifba.prodscalpel4objects.adapter.model.AdaptedOrganArtifact;
import com.ifba.prodscalpel4objects.adapter.model.ExtractedOrganInfo;
import com.ifba.prodscalpel4objects.adapter.model.ImplantationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orquestrador da etapa de Adaptação de Métodos ao Contexto de Destino (Organ Adaptation).
 *
 * <p>Analisa as assinaturas do método extraído, executa a busca de mapeamento
 * de variáveis com o {@link VariableMappingEngine}, invoca o {@link WrapperSynthesizer}
 * para gerar o Organ-Host Wrapper em Spring Boot e monta o {@link AdaptedOrganArtifact}.</p>
 *
 * @author Giovane Neves
 */
public class OrganMethodAdapter {

    private final VariableMappingEngine mappingEngine;
    private final WrapperSynthesizer wrapperSynthesizer;

    public OrganMethodAdapter() {
        this.mappingEngine = new VariableMappingEngine();
        this.wrapperSynthesizer = new WrapperSynthesizer();
    }

    public OrganMethodAdapter(VariableMappingEngine mappingEngine, WrapperSynthesizer wrapperSynthesizer) {
        this.mappingEngine = Objects.requireNonNull(mappingEngine);
        this.wrapperSynthesizer = Objects.requireNonNull(wrapperSynthesizer);
    }

    /**
     * Adapta um método do órgão ao contexto de destino do Host.
     *
     * @param organInfo Informações do órgão extraído do IceBox.
     * @param prunedEntryCu AST podada da classe principal (resultado do Over-organ Reduction).
     * @param context Contexto de implantação no Host fornecido pelo Finder.
     * @param outputDirectory Diretório de destino onde os arquivos finais adaptados serão gravados.
     * @param originalStatementsCount Quantidade de instruções da AST antes da poda.
     * @param deletedStatementsCount Quantidade de instruções podadas.
     * @return {@link AdaptedOrganArtifact} pronto para entrega ao Implementer.
     * @throws IOException Se houver falha de escrita em disco.
     */
    public AdaptedOrganArtifact adapt(
            ExtractedOrganInfo organInfo,
            CompilationUnit prunedEntryCu,
            ImplantationContext context,
            Path outputDirectory,
            int originalStatementsCount,
            int deletedStatementsCount
    ) throws IOException {
        Objects.requireNonNull(organInfo, "organInfo não pode ser nulo");
        Objects.requireNonNull(context, "context não pode ser nulo");
        Objects.requireNonNull(outputDirectory, "outputDirectory não pode ser nulo");

        Files.createDirectories(outputDirectory);

        // 1. Localiza a declaração do método de entrada na AST
        MethodDeclaration entryMethod = findEntryMethod(prunedEntryCu, organInfo.entryMethodName());
        String returnType = entryMethod != null ? entryMethod.getType().asString() : "void";

        // 2. Extrai os parâmetros necessários do método do órgão
        List<VariableMappingEngine.ParameterEntry> organParams = new ArrayList<>();
        if (entryMethod != null) {
            for (Parameter p : entryMethod.getParameters()) {
                organParams.add(new VariableMappingEngine.ParameterEntry(
                        p.getNameAsString(),
                        p.getType().asString()
                ));
            }
        }

        // 3. Resolve os mapeamentos para o escopo do Host
        AdaptationIndividual adaptationResult = mappingEngine.resolveMappings(organParams, context);

        // 4. Sintetiza o Organ-Host Wrapper
        String wrapperClassName = "Adapted" + organInfo.entryClassName() + "Wrapper";
        String targetPackage = extractPackageName(context);
        String organPackage = (prunedEntryCu != null && prunedEntryCu.getPackageDeclaration().isPresent())
                ? prunedEntryCu.getPackageDeclaration().get().getNameAsString()
                : "";

        String wrapperSourceCode = wrapperSynthesizer.synthesizeWrapperCode(
                targetPackage,
                organPackage,
                wrapperClassName,
                organInfo.entryClassName(),
                organInfo.entryMethodName(),
                returnType,
                adaptationResult.getMappings()
        );

        // 5. Salva a classe podada e o Wrapper gerado no diretório de saída
        List<Path> finalFiles = new ArrayList<>();

        Path prunedClassPath = outputDirectory.resolve(organInfo.entryClassName() + ".java");
        Files.writeString(prunedClassPath, prunedEntryCu.toString());
        finalFiles.add(prunedClassPath);

        Path wrapperPath = outputDirectory.resolve(wrapperClassName + ".java");
        Files.writeString(wrapperPath, wrapperSourceCode);
        finalFiles.add(wrapperPath);

        // Copia classes auxiliares do órgão para o output
        for (Path auxFile : organInfo.extractedClassFiles()) {
            if (!auxFile.getFileName().toString().equals(organInfo.entryClassName() + ".java") && Files.exists(auxFile)) {
                Path dest = outputDirectory.resolve(auxFile.getFileName().toString());
                Files.copy(auxFile, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                finalFiles.add(dest);
            }
        }

        // 6. Gera o snippet sugerido de Hook para injeção no Host
        String hookSnippet = wrapperSynthesizer.generateHookSnippet(
                wrapperClassName,
                returnType,
                adaptationResult.getMappings()
        );

        String wrapperMethodSignature = "%s executeAdaptedFeature(%s)".formatted(
                returnType,
                organParams.stream().map(p -> p.type() + " " + p.name()).reduce((a, b) -> a + ", " + b).orElse("")
        );

        return new AdaptedOrganArtifact(
                outputDirectory,
                wrapperClassName,
                wrapperMethodSignature,
                finalFiles,
                hookSnippet,
                true,
                true,
                adaptationResult.getFitness(),
                originalStatementsCount,
                deletedStatementsCount
        );
    }

    private MethodDeclaration findEntryMethod(CompilationUnit cu, String methodName) {
        if (cu == null) return null;
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(methodName))
                .findFirst()
                .orElse(null);
    }

    private String extractPackageName(ImplantationContext context) {
        try {
            if (Files.exists(context.targetFilePath())) {
                CompilationUnit hostCu = StaticJavaParser.parse(context.targetFilePath());
                return hostCu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
            }
        } catch (Exception ignored) {}
        return "";
    }
}
