package com.ifba.prodscalpel4objects.adapter.adaptation;

import com.ifba.prodscalpel4objects.adapter.model.MappingCandidate;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sintetizador do Organ-Host Wrapper (padrão de projeto Adapter/Facade).
 *
 * <p>Gera o código fonte da classe adaptadora em Java, devidamente anotada
 * para o ecossistema Spring Boot (@Component / @Service), servindo de ponte
 * semântica entre o ponto de implantação no Host e o Órgão transplantado.</p>
 *
 * @author Giovane Neves
 */
public class WrapperSynthesizer {

    /**
     * Sintetiza o código Java completo do Organ-Host Wrapper.
     *
     * @param targetPackage Pacote da classe onde o Wrapper residirá.
     * @param organPackage Pacote original da classe do órgão (para geração de import se diferente).
     * @param wrapperClassName Nome da classe Wrapper a ser criada (ex: "AdaptedServiceWrapper").
     * @param organClassName Nome simples da classe principal do Órgão (ex: "ServiceClass").
     * @param organMethodName Nome do método a ser invocado no Órgão (ex: "processData").
     * @param returnType Tipo de retorno do método (ex: "String" ou "void").
     * @param mappings Lista de mapeamentos de parâmetros (Host -> Órgão).
     * @return String contendo o código fonte completo do Wrapper em Java.
     */
    public String synthesizeWrapperCode(
            String targetPackage,
            String organPackage,
            String wrapperClassName,
            String organClassName,
            String organMethodName,
            String returnType,
            List<MappingCandidate> mappings
    ) {
        Objects.requireNonNull(wrapperClassName, "wrapperClassName não pode ser nulo");
        Objects.requireNonNull(organClassName, "organClassName não pode ser nulo");
        Objects.requireNonNull(organMethodName, "organMethodName não pode ser nulo");

        String pkg = (targetPackage != null && !targetPackage.trim().isEmpty())
                ? "package " + targetPackage + ";\n\n"
                : "";

        StringBuilder importsBuilder = new StringBuilder();
        importsBuilder.append("import org.springframework.stereotype.Component;\n");

        if (organPackage != null && !organPackage.trim().isEmpty() && !organPackage.equals(targetPackage)) {
            importsBuilder.append("import ").append(organPackage).append(".").append(organClassName).append(";\n");
        }
        importsBuilder.append("\n");

        String safeReturn = (returnType != null && !returnType.trim().isEmpty()) ? returnType : "void";
        boolean isVoid = "void".equalsIgnoreCase(safeReturn);

        // Parâmetros que o método do Wrapper aceita (variáveis do Host)
        String methodParams = mappings.stream()
                .map(m -> m.hostVariableType() + " " + m.hostVariableName())
                .collect(Collectors.joining(", "));

        // Argumentos passados para a chamada do método original do Órgão
        String callArgs = mappings.stream()
                .map(MappingCandidate::toExpression)
                .collect(Collectors.joining(", "));

        String returnPrefix = isVoid ? "" : "return ";

        return """
            %s%s/**
             * Organ-Host Wrapper sintetizado automaticamente pelo ProdScalpel4Objects.
             * Faz a ponte semântica entre o ponto de implantação do Host e o Órgão transplantado.
             */
            @Component
            public class %s {

                private final %s organInstance = new %s();

                /**
                 * Ponto de entrada adaptado para o sistema hospedeiro.
                 */
                public %s executeAdaptedFeature(%s) {
                    %sthis.organInstance.%s(%s);
                }

                public %s getOrganInstance() {
                    return this.organInstance;
                }
            }
            """.formatted(
                pkg,
                importsBuilder.toString(),
                wrapperClassName,
                organClassName, organClassName,
                safeReturn, methodParams,
                returnPrefix, organMethodName, callArgs,
                organClassName
        );
    }

    /**
     * Sobrecarga de conveniência sem organPackage explícito.
     */
    public String synthesizeWrapperCode(
            String targetPackage,
            String wrapperClassName,
            String organClassName,
            String organMethodName,
            String returnType,
            List<MappingCandidate> mappings
    ) {
        return synthesizeWrapperCode(targetPackage, "", wrapperClassName, organClassName, organMethodName, returnType, mappings);
    }

    /**
     * Gera o trecho de código pronto (hook) para que o Implementer insira no ponto de enxerto do Host.
     *
     * @param wrapperClassName Nome da classe do Wrapper.
     * @param returnType Tipo de retorno.
     * @param mappings Mapeamentos ativos com os nomes das variáveis do Host.
     * @return Instrução de código sugerida para o hook.
     */
    public String generateHookSnippet(String wrapperClassName, String returnType, List<MappingCandidate> mappings) {
        String varName = Character.toLowerCase(wrapperClassName.charAt(0)) + wrapperClassName.substring(1);
        String args = mappings.stream().map(MappingCandidate::hostVariableName).collect(Collectors.joining(", "));
        boolean isVoid = "void".equalsIgnoreCase(returnType);
        String call = "%s.executeAdaptedFeature(%s);".formatted(varName, args);

        if (!isVoid) {
            call = returnType + " organResult = " + call;
        }

        return "// [ProdScalpel4Objects Hook]:\n" + call;
    }
}
