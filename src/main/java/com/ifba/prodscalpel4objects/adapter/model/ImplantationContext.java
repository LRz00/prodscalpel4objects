package com.ifba.prodscalpel4objects.adapter.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsula as informações do ponto de implantação no sistema hospedeiro (Host),
 * fornecidas pelo módulo Finder.
 *
 * @param hostRoot Diretório raiz do código-fonte do sistema hospedeiro.
 * @param targetFilePath Caminho do arquivo .java onde o enxerto será conectado.
 * @param targetClassName Nome da classe hospedeira de destino (ex: "UserService").
 * @param targetMethodSignature Assinatura do método hospedeiro que receberá a chamada do órgão.
 * @param insertionLineNumber Linha no código hospedeiro onde a chamada adaptada deve ser injetada.
 * @param hostVisibleVariables Mapa de variáveis disponíveis no escopo do ponto de enxerto (nome -> tipo totalmente qualificado).
 * @param hostAvailableBeans Lista de nomes ou tipos de Beans Spring disponíveis no contexto hospedeiro.
 * @param hostAvailableMethods Lista de métodos acessíveis no ponto de enxerto.
 *
 * @author Giovane Neves
 */
public record ImplantationContext(
        Path hostRoot,
        Path targetFilePath,
        String targetClassName,
        String targetMethodSignature,
        int insertionLineNumber,
        Map<String, String> hostVisibleVariables,
        List<String> hostAvailableBeans,
        List<String> hostAvailableMethods
) {

    public ImplantationContext {
        Objects.requireNonNull(hostRoot, "hostRoot não pode ser nulo");
        Objects.requireNonNull(targetFilePath, "targetFilePath não pode ser nulo");
        Objects.requireNonNull(targetClassName, "targetClassName não pode ser nulo");

        hostVisibleVariables = hostVisibleVariables != null
                ? Map.copyOf(hostVisibleVariables)
                : Collections.emptyMap();

        hostAvailableBeans = hostAvailableBeans != null
                ? List.copyOf(hostAvailableBeans)
                : Collections.emptyList();

        hostAvailableMethods = hostAvailableMethods != null
                ? List.copyOf(hostAvailableMethods)
                : Collections.emptyList();
    }
}
