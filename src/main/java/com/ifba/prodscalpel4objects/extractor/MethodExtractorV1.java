/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ifba.prodscalpel4objects.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 *
 * @author lara
 */
public class MethodExtractorV1 {

    public MethodExtractorV1() {
    }

    //Metodo principal para extração
    public void extract() {
        try {

            //Caminho para o arquivo fonte(eventualmente evoluir para receber como parametro)
            String path = "src/main/resources/SourceExemple.java";

            //Nom do metodo a ser extraido(eventualmente evoluir para receber como parametro)
            String methodToBeExtracted = "threeSquared";

            //Cria uma instancia do javaparser para analisar o codigo
            JavaParser javaParser = new JavaParser();
            File source = new File(path);

            // Faz o parse do arquivo fonte
            ParseResult<CompilationUnit> sourceParseResult = javaParser.parse(source);

            // Encontra a classe principal no arquivo fonte
            Optional<ClassOrInterfaceDeclaration> sourceClassOpt = sourceParseResult.getResult()
                    .flatMap(cu -> cu.findFirst(ClassOrInterfaceDeclaration.class));

            // Verifica se a classe foi encontrada
            if (!sourceClassOpt.isPresent()) {
                System.out.println("Classe fonte não encontrada.");
                return;
            }

            // Obtém a classe encontrada
            ClassOrInterfaceDeclaration sourceClass = sourceClassOpt.get();

            // Encontra o método a ser extraído dentro da classe fonte
            MethodDeclaration method = sourceClass.findFirst(MethodDeclaration.class,
                    m -> m.getNameAsString().equals(methodToBeExtracted)).orElse(null);

            // Verifica se o método foi encontrado
            if (method == null) {
                System.out.println("Método não encontrado: " + methodToBeExtracted);
                return;
            }

            // Obtém todos os métodos dependentes do método a ser extraído
            Set<MethodDeclaration> dependentMethods = findDependentMethods(method, sourceClass);

            // Arquivo de destino onde o método será transferido
            File targetFile = new File("IceBox.java");

            // Cria o arquivo de destino caso ele não exista
            if (!targetFile.exists()) {
                targetFile.createNewFile();
                Files.write(Paths.get("IceBox.java"), "public class IceBox {}\n".getBytes());
            }

            // Faz o parse do arquivo de destino
            ParseResult<CompilationUnit> targetParseResult = javaParser.parse(targetFile);

            // Obtém ou cria a classe no arquivo de destino
            CompilationUnit targetCU = targetParseResult.getResult().get();
            ClassOrInterfaceDeclaration targetClass = targetCU.findFirst(ClassOrInterfaceDeclaration.class)
                    .orElseGet(() -> targetCU.addClass("IceBox"));

            // Adiciona o método principal ao arquivo de destino
            targetClass.addMember(method.clone());

            // Adiciona os métodos dependentes ao arquivo de destino, evitando "clones"
            for (MethodDeclaration depMethod : dependentMethods) {
                if (!targetClass.getMethodsByName(depMethod.getNameAsString()).isEmpty()) {
                    continue; // Ignora métodos já adicionados
                }
                targetClass.addMember(depMethod.clone());
            }

            // Escreve o conteúdo atualizado no arquivo de destino
            Files.write(Paths.get("IceBox.java"), targetCU.toString().getBytes());

        } catch (Exception e) {
        }

    }

    /**
     * Encontra todos os métodos dependentes chamados dentro de um método.
     * recebe o método principal a ser analisada, a classe onde o método está
     * definido e retorna um set de métodos dependentes.
     */
    private Set<MethodDeclaration> findDependentMethods(MethodDeclaration method, ClassOrInterfaceDeclaration sourceClass) {

        // Conjunto para armazenar os métodos dependentes
        Set<MethodDeclaration> dependentMethods = new HashSet<>();

        // Busca todas as chamadas de métodos dentro do método principal
        List<MethodCallExpr> methodCalls = method.findAll(MethodCallExpr.class);

        // Para cada chamada de método, verifica se o método existe na classe fonte
        for (MethodCallExpr call : methodCalls) {
            String calledMethodName = call.getNameAsString(); // Nome do método chamado

            // Procura o método chamado na classe fonte e adiciona ao conjunto de dependências
            sourceClass.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(calledMethodName))
                    .ifPresent(dependentMethods::add);
        }

        return dependentMethods;
    }
}
