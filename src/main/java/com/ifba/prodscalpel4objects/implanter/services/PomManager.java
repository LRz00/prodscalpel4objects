package com.ifba.prodscalpel4objects.implanter.services;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gerencia todas as operações relacionadas ao arquivo pom.xml,
 * como leitura, escrita e manipulação de dependências do Maven.
 */
public class PomManager {

    /**
     * Lê um arquivo pom.xml e o converte para um objeto Model do Maven.
     * @param pomFile O arquivo pom.xml a ser lido.
     * @return Um objeto `Model` representando o conteúdo do pom.
     * @throws IOException Se ocorrer um erro de leitura do arquivo.
     * @throws XmlPullParserException Se ocorrer um erro de parsing do XML.
     */
    public Model readPom(File pomFile) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (FileReader fileReader = new FileReader(pomFile)) {
            return reader.read(fileReader);
        }
    }

    /**
     * Escreve o conteúdo de um objeto Model do Maven de volta para um arquivo pom.xml.
     * @param pomFile O arquivo pom.xml de destino.
     * @param model O objeto `Model` a ser escrito.
     * @throws IOException Se ocorrer um erro de escrita no arquivo.
     */
    public void writePom(File pomFile, Model model) throws IOException {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            writer.write(fileWriter, model);
        }
    }

    /**
     * Adiciona um conjunto de dependências a um modelo Maven, evitando duplicatas.
     * @param receptorModel O modelo do pom.xml receptor a ser modificado.
     * @param requiredDependencies Um conjunto de dependências que devem ser adicionadas.
     * @return O número de novas dependências que foram efetivamente adicionadas.
     */
    public int addMissingDependencies(Model receptorModel, Set<Dependency> requiredDependencies) {
        Set<String> existingDependencies = new HashSet<>();
        receptorModel.getDependencies().forEach(dep ->
                existingDependencies.add(dep.getGroupId() + ":" + dep.getArtifactId())
        );
        int addedCount = 0;
        for (Dependency requiredDep : requiredDependencies) {
            String dependencyCoord = requiredDep.getGroupId() + ":" + requiredDep.getArtifactId();
            if (!existingDependencies.contains(dependencyCoord)) {
                receptorModel.addDependency(requiredDep);
                addedCount++;
            }
        }
        return addedCount;
    }

    /**
     * Filtra uma lista de dependências para encontrar aquelas que correspondem a um conjunto de imports.
     * @param imports Um conjunto de strings de import (ex: "java.util.List").
     * @param allDependencies A lista completa de dependências disponíveis (ex: do pom.xml doador).
     * @return Um conjunto de dependências (`Dependency`) consideradas necessárias pelos imports.
     */
    public Set<Dependency> findRequiredDependencies(Set<String> imports, List<Dependency> allDependencies) {
        Set<Dependency> requiredDependencies = new HashSet<>();
        for (String importLine : imports) {
            String importGroup = extractGroupFromImport(importLine);
            String[] importWords = importGroup.split("\\.");
            for (Dependency dependency : allDependencies) {
                if (hasWordsInCommon(importWords, dependency.getGroupId(), dependency.getArtifactId())) {
                    requiredDependencies.add(dependency);
                }
            }
        }
        return requiredDependencies;
    }

    /**
     * Extrai a parte do pacote de uma linha de import (remove o nome da classe).
     * @param importLine A string de import completa.
     * @return A string contendo apenas o pacote.
     */
    private String extractGroupFromImport(String importLine) {
        int lastDotIndex = importLine.lastIndexOf('.');
        if (lastDotIndex != -1) {
            return importLine.substring(0, lastDotIndex);
        }
        return importLine;
    }

    /**
     * Verifica se há palavras em comum entre as palavras de um import e o groupId/artifactId de uma dependência.
     * @param importWords Array de palavras do pacote do import.
     * @param groupId O groupId da dependência.
     * @param artifactId O artifactId da dependência.
     * @return `true` se houver correspondência suficiente, `false` caso contrário.
     */
    private boolean hasWordsInCommon(String[] importWords, String groupId, String artifactId) {
        long commonInGroupId = countCommonWords(importWords, groupId.split("\\."));
        long commonInArtifactId = countCommonWords(importWords, artifactId.split("-"));
        return (commonInGroupId >= 2) || (commonInArtifactId >= 1);
    }

    /**
     * Conta o número de strings em comum entre dois arrays de strings.
     * @param words1 Primeiro array de strings.
     * @param words2 Segundo array de strings.
     * @return A quantidade de strings em comum.
     */
    private long countCommonWords(String[] words1, String[] words2) {
        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));
        set1.retainAll(set2);
        return set1.size();
    }
}