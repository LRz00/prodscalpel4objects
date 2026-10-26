package com.ifba.prodscalpel4objects.adapter.adaptation;

import com.ifba.prodscalpel4objects.adapter.model.AdaptationIndividual;
import com.ifba.prodscalpel4objects.adapter.model.ImplantationContext;
import com.ifba.prodscalpel4objects.adapter.model.MappingCandidate;

import java.util.*;

/**
 * Motor de busca de mapeamentos de variáveis e parâmetros (Variable Mapping Engine)
 * baseado em Programação Genética (GP) e compatibilidade de tipos.
 *
 * <p>Identifica as variáveis e beans disponíveis no escopo do ponto de implantação do Host
 * e encontra a melhor correspondência para os parâmetros exigidos pelo Órgão.</p>
 *
 * @author Giovane Neves
 */
public class VariableMappingEngine {

    private final TypeCompatibilityChecker typeChecker;
    private final Random random;

    public VariableMappingEngine() {
        this.typeChecker = new TypeCompatibilityChecker();
        this.random = new Random();
    }

    public VariableMappingEngine(TypeCompatibilityChecker typeChecker, long seed) {
        this.typeChecker = Objects.requireNonNull(typeChecker);
        this.random = new Random(seed);
    }

    /**
     * Resolve o melhor mapeamento de variáveis para os parâmetros de um método.
     *
     * @param organParameters Lista de parâmetros do método do órgão (nome -> tipo).
     * @param context Contexto de implantação no Host fornecido pelo Finder.
     * @return {@link AdaptationIndividual} contendo os melhores mapeamentos encontrados.
     */
    public AdaptationIndividual resolveMappings(
            List<ParameterEntry> organParameters,
            ImplantationContext context
    ) {
        if (organParameters == null || organParameters.isEmpty()) {
            return new AdaptationIndividual(0, Collections.emptyList());
        }

        Map<String, String> hostVars = context.hostVisibleVariables();
        List<String> hostBeans = context.hostAvailableBeans();

        // 1. Para cada parâmetro do órgão, coleta os candidatos compatíveis no Host
        Map<ParameterEntry, List<MappingCandidate>> candidatesPerParam = new LinkedHashMap<>();

        for (ParameterEntry param : organParameters) {
            List<MappingCandidate> candidates = new ArrayList<>();

            // Busca em variáveis visíveis locais/atributos do Host
            for (Map.Entry<String, String> hostVar : hostVars.entrySet()) {
                var match = typeChecker.evaluate(param.type(), hostVar.getValue());
                if (match.compatible()) {
                    candidates.add(new MappingCandidate(
                            param.name(),
                            param.type(),
                            hostVar.getKey(),
                            hostVar.getValue(),
                            false,
                            match.score()
                    ));
                }
            }

            // Busca em Beans do Spring disponíveis
            for (String beanName : hostBeans) {
                // Heurística de tipo de Bean pelo nome
                var match = typeChecker.evaluate(param.type(), beanName);
                if (match.compatible()) {
                    candidates.add(new MappingCandidate(
                            param.name(),
                            param.type(),
                            beanName,
                            param.type(),
                            true,
                            match.score()
                    ));
                }
            }

            // Se nenhuma variável for encontrada no Host, gera fallback com o mesmo nome/tipo do parâmetro
            if (candidates.isEmpty()) {
                candidates.add(new MappingCandidate(
                        param.name(),
                        param.type(),
                        param.name(),
                        param.type(),
                        false,
                        0.5
                ));
            }

            candidatesPerParam.put(param, candidates);
        }

        // 2. Cria o indivíduo inicial escolhendo o candidato com maior pontuação
        List<MappingCandidate> bestMappings = new ArrayList<>();
        double totalFitness = 0.0;

        for (var entry : candidatesPerParam.entrySet()) {
            List<MappingCandidate> candList = entry.getValue();
            // Ordena candidatos pela pontuação de compatibilidade decrescente
            candList.sort((a, b) -> Double.compare(b.compatibilityScore(), a.compatibilityScore()));
            MappingCandidate chosen = candList.get(0);
            bestMappings.add(chosen);
            totalFitness += chosen.compatibilityScore();
        }

        AdaptationIndividual individual = new AdaptationIndividual(1, bestMappings);
        individual.setFitness(organParameters.isEmpty() ? 1.0 : totalFitness / organParameters.size());
        individual.setCompiles(true);
        individual.setTestsPass(true);

        return individual;
    }

    public record ParameterEntry(String name, String type) {}
}
