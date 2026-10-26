package com.ifba.prodscalpel4objects.adapter.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representa o artefato final gerado pelo módulo Adapter, pronto para ser
 * entregue ao módulo Implementer para implantação física no sistema hospedeiro.
 *
 * @param adaptedSourceRoot Diretório onde os arquivos adaptados e podados foram salvos.
 * @param wrapperClassName Nome da classe adaptadora gerada (Organ-Host Wrapper).
 * @param wrapperMethodSignature Assinatura do método sintetizado no Wrapper.
 * @param generatedAndCleanedFiles Lista de caminhos de todos os arquivos que compõem o órgão adaptado.
 * @param suggestedHookCode Trecho de código pronto sugerido para injeção no ponto de implantação do Host.
 * @param isCompilationValid Indica se o artefato adaptado compila sem erros.
 * @param areTestsPassing Indica se a suíte de testes original passou com sucesso sobre o órgão adaptado.
 * @param fitnessScore Nota de aptidão global obtida pelo indivíduo vencedor.
 * @param originalStatementsCount Quantidade de instruções (statements) do sobre-órgão antes da poda.
 * @param deletedStatementsCount Quantidade de instruções podadas pelo processo de redução.
 *
 * @author Giovane Neves
 */
public record AdaptedOrganArtifact(
        Path adaptedSourceRoot,
        String wrapperClassName,
        String wrapperMethodSignature,
        List<Path> generatedAndCleanedFiles,
        String suggestedHookCode,
        boolean isCompilationValid,
        boolean areTestsPassing,
        double fitnessScore,
        int originalStatementsCount,
        int deletedStatementsCount
) {

    public AdaptedOrganArtifact {
        Objects.requireNonNull(adaptedSourceRoot, "adaptedSourceRoot não pode ser nulo");
        Objects.requireNonNull(wrapperClassName, "wrapperClassName não pode ser nulo");

        generatedAndCleanedFiles = generatedAndCleanedFiles != null
                ? List.copyOf(generatedAndCleanedFiles)
                : Collections.emptyList();
    }

    /**
     * Calcula a taxa de redução de código supérfluo (Reduction Ratio).
     *
     * @return Percentual de instruções eliminadas (0.0 a 1.0).
     */
    public double getReductionRatio() {
        if (originalStatementsCount <= 0) return 0.0;
        return (double) deletedStatementsCount / originalStatementsCount;
    }
}
