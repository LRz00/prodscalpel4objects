package com.ifba.prodscalpel4objects.extractor;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PomGenerator {

 private final Path iceBoxPath;
 private final Path originalPomPath;

 public PomGenerator(String originalPomPath) {
  this.iceBoxPath = Paths.get(System.getProperty("user.dir"), "IceBox");
  this.originalPomPath = Paths.get(originalPomPath);
 }

 /**
  * Gera um novo pom.xml no diretório IceBox com as dependências necessárias.
  *
  * @param imports Os imports das classes extraídas.
  */
 public void generatePomInIceBox(Set<String> imports) throws Exception {
  // Lê o pom.xml original
  Model originalModel = readPom(originalPomPath);

  // Filtra as dependências do pom.xml original com base nos imports
  Set<Dependency> requiredDependencies = findRequiredDependencies(imports, originalModel);

  // Cria um novo modelo para o pom.xml do IceBox
  Model iceBoxModel = new Model();
  iceBoxModel.setModelVersion("4.0.0");
  iceBoxModel.setGroupId("com.ifba.icebox");
  iceBoxModel.setArtifactId("icebox-project");
  iceBoxModel.setVersion("1.0.0");

  // Adiciona as dependências necessárias ao novo modelo
  requiredDependencies.forEach(iceBoxModel::addDependency);

  // Escreve o novo pom.xml no diretório IceBox
  MavenXpp3Writer writer = new MavenXpp3Writer();
  Path pomPath = iceBoxPath.resolve("pom.xml");
  writer.write(new FileWriter(pomPath.toFile()), iceBoxModel);

  System.out.println("Novo pom.xml gerado em: " + pomPath);
 }

 /**
  * Lê o pom.xml original e retorna o modelo.
  *
  * @param pomPath Caminho do pom.xml original.
  * @return O modelo do pom.xml.
  */
 private Model readPom(Path pomPath) throws Exception {
  MavenXpp3Reader reader = new MavenXpp3Reader();
  try (FileReader fileReader = new FileReader(pomPath.toFile())) {
   return reader.read(fileReader);
  }
 }

 /**
  * Filtra as dependências do pom.xml original com base nos imports.
  *
  * @param imports       Os imports das classes extraídas.
  * @param originalModel O modelo do pom.xml original.
  * @return Um conjunto de dependências necessárias.
  */
 private Set<Dependency> findRequiredDependencies(Set<String> imports, Model originalModel) {
  Set<Dependency> requiredDependencies = new HashSet<>();

  // Obtém a lista de dependências do pom.xml original
  List<Dependency> dependencies = originalModel.getDependencies();

  System.out.println("Dependências no pom.xml original:");
  dependencies.forEach(dep -> System.out.println(dep.getGroupId() + ":" + dep.getArtifactId()));

  // Para cada import, verifica se ele corresponde a uma dependência
  for (String importLine : imports) {
   // Extrai o grupo da importação (ex: "org.springframework.mail" de "org.springframework.mail.SimpleMailMessage")
   String importGroup = extractGroupFromImport(importLine);
   System.out.println("Import analisado: " + importLine + " -> Grupo: " + importGroup);

   // Divide o grupo do import em palavras
   String[] importWords = importGroup.split("\\.");

   // Procura a dependência correspondente no pom.xml original
   for (Dependency dependency : dependencies) {
    // Verifica se o groupId ou artifactId tem pelo menos 3 palavras em comum com o import
    if (hasAtLeastThreeCommonWords(importWords, dependency.getGroupId(), dependency.getArtifactId())) {
     System.out.println("Dependência correspondente encontrada: " + dependency.getGroupId() + ":" + dependency.getArtifactId());
     requiredDependencies.add(dependency);
    }
   }
  }

  return requiredDependencies;
 }

 private boolean hasAtLeastThreeCommonWords(String[] importWords, String groupId, String artifactId) {
  // Divide o groupId e o artifactId em palavras
  String[] groupIdWords = groupId.split("\\.");
  String[] artifactIdWords = artifactId.split("\\.");

  // Conta quantas palavras do import estão no groupId
  int commonInGroupId = countCommonWords(importWords, groupIdWords);

  // Conta quantas palavras do import estão no artifactId
  int commonInArtifactId = countCommonWords(importWords, artifactIdWords);

  // Retorna true se houver pelo menos 3 palavras em comum
  return (commonInGroupId >= 2) || (commonInArtifactId >= 1);
 }

 private int countCommonWords(String[] words1, String[] words2) {
  int commonCount = 0;
  for (String word1 : words1) {
   for (String word2 : words2) {
    if (word1.equals(word2)) {
     commonCount++;
     break; // Para evitar contar a mesma palavra mais de uma vez
    }
   }
  }
  return commonCount;
 }
 /**
  * Extrai o grupo de uma importação.
  *
  * @param importLine A linha de importação (ex: "org.apache.maven.model.Dependency").
  * @return O grupo da importação (ex: "org.apache.maven").
  */
 private String extractGroupFromImport(String importLine) {
  // Remove o nome da classe e mantém apenas o pacote
  int lastDotIndex = importLine.lastIndexOf('.');
  if (lastDotIndex != -1) {
   return importLine.substring(0, lastDotIndex);
  }
  return importLine; // Caso não haja ponto, retorna a importação completa
 }
}