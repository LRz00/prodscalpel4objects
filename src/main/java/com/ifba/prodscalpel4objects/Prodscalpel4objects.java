/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.ifba.prodscalpel4objects;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.ifba.prodscalpel4objects.extractor.MethodExtractorV1;
import com.ifba.prodscalpel4objects.extractor.TestGenerator;

/**
 *
 * @author lara
 */
public class Prodscalpel4objects {

  static String API_KEY = "";
  public static void main(String[] args) throws IOException {
    String sourceRootPath = "/home/amp/Documentos/tcc/petresgate/src/main/java";

    // Caminho completo para o arquivo-fonte que contém o método
    String sourceFilePath = "/home/amp/Documentos/tcc/petresgate/src/main/java/br/com/ifba/petresgate/service/AnimalService.java";

    // Nome do método a ser extraído
    String methodName = "saveAnimal";

    // Caminho do Pom do projeto original
    String originalPomPath = "/home/amp/Documentos/tcc/petresgate/pom.xml";

    // Diretório de saída
    Path outputDir = Paths.get("IceBox");

    // Cria uma instância do extrator de métodos
    MethodExtractorV1 methodExtractor = new MethodExtractorV1(sourceRootPath, originalPomPath);

    // Extrai o método especificado para um novo arquivo
    methodExtractor.extract(sourceFilePath, methodName);

    // Extrai o caminho de chamadas até o método (call chain)
    methodExtractor.extractCallPath(sourceFilePath, methodName, outputDir);

    System.out.println("Extração concluída.");

    TestGenerator generator = new TestGenerator(API_KEY);
        generator.generateAndRunTests(methodName);

        System.out.println("Testes gerados e executados.");
}

}
