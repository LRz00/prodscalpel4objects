package com.ifba.prodscalpel4objects.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Classe responsável por extrair um método e suas dependências para um novo arquivo.
 *
 * @author lara
 */
public class MethodExtractorV1 {

    public MethodExtractorV1() {
    }

    // Método principal para extração
    public void extract() {
        try {
            // Caminho para o arquivo fonte
            String path = "src/main/resources/SourceExemple.java";

            // Nome do método a ser extraído
            String methodToBeExtracted = "threeSquared";

            // Cria uma instância do JavaParser para analisar o código
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

            // Obtém todos os métodos dependentes (em cadeia)
            Set<MethodDeclaration> dependentMethods = findAllDependentMethods(method, sourceClass);

            // Obtém todos os atributos necessários pelos métodos (principal e dependentes)
            Set<FieldDeclaration> requiredFields = findRequiredFields(method, dependentMethods, sourceClass);

            // Diretório de destino
            Path targetDirectory = Paths.get("IceBox");
            if (!Files.exists(targetDirectory)) {
                Files.createDirectory(targetDirectory);
            }

            // Nome do arquivo de destino será o mesmo do arquivo original
            String originalFileName = source.getName();
            File targetFile = targetDirectory.resolve(originalFileName).toFile();

            // Cria ou substitui o arquivo de destino
            if (targetFile.exists()) {
                Files.delete(targetFile.toPath());
            }
            targetFile.createNewFile();

            // Faz o parse do arquivo de destino
            ParseResult<CompilationUnit> targetParseResult = javaParser.parse(targetFile);

            // Obtém ou cria a classe no arquivo de destino
            CompilationUnit targetCU = targetParseResult.getResult().orElse(new CompilationUnit());
            ClassOrInterfaceDeclaration targetClass = targetCU.findFirst(ClassOrInterfaceDeclaration.class)
                    .orElseGet(() -> targetCU.addClass(originalFileName.replace(".java", "")));

            // Adiciona o método principal ao arquivo de destino
            targetClass.addMember(method.clone());

            // Adiciona os métodos dependentes ao arquivo de destino, evitando duplicações
            for (MethodDeclaration depMethod : dependentMethods) {
                if (targetClass.getMethodsByName(depMethod.getNameAsString()).isEmpty()) {
                    targetClass.addMember(depMethod.clone());
                }
            }

            // Adiciona os atributos necessários ao arquivo de destino, evitando duplicações
            for (FieldDeclaration field : requiredFields) {
                if (targetClass.getFieldByName(field.getVariables().get(0).getNameAsString()).isEmpty()) {
                    targetClass.addMember(field.clone());
                }
            }

            // debugando
            //System.out.println("Conteúdo gerado para o arquivo de destino:");
            //System.out.println(targetCU.toString());

            // Escreve o conteúdo atualizado no arquivo de destino
            Files.write(targetFile.toPath(), targetCU.toString().getBytes());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Encontra todos os métodos dependentes (em cadeia) chamados dentro de um método.
     */
    private Set<MethodDeclaration> findAllDependentMethods(MethodDeclaration method, ClassOrInterfaceDeclaration sourceClass) {
        Set<MethodDeclaration> allDependentMethods = new HashSet<>();
        Set<MethodDeclaration> processedMethods = new HashSet<>();
        Set<MethodDeclaration> methodsToProcess = new HashSet<>();
        methodsToProcess.add(method);

        while (!methodsToProcess.isEmpty()) {
            MethodDeclaration currentMethod = methodsToProcess.iterator().next();
            methodsToProcess.remove(currentMethod);

            if (processedMethods.contains(currentMethod)) {
                continue;
            }
            processedMethods.add(currentMethod);

            List<MethodCallExpr> methodCalls = currentMethod.findAll(MethodCallExpr.class);

            for (MethodCallExpr call : methodCalls) {
                String calledMethodName = call.getNameAsString();
                Optional<MethodDeclaration> dependentMethodOpt = sourceClass.findFirst(MethodDeclaration.class,
                        m -> m.getNameAsString().equals(calledMethodName));

                if (dependentMethodOpt.isPresent()) {
                    MethodDeclaration dependentMethod = dependentMethodOpt.get();
                    allDependentMethods.add(dependentMethod);
                    methodsToProcess.add(dependentMethod);
                }
            }
        }

        return allDependentMethods;
    }

    /**
     * Encontra todos os atributos da classe que são usados diretamente pelos métodos fornecidos.
     */
    private Set<FieldDeclaration> findRequiredFields(MethodDeclaration mainMethod, Set<MethodDeclaration> dependentMethods,
                                                     ClassOrInterfaceDeclaration sourceClass) {
        Set<FieldDeclaration> requiredFields = new HashSet<>();

        // Coleta todos os campos diretamente usados pelos métodos
        List<MethodDeclaration> allMethods = new ArrayList<>(dependentMethods);
        allMethods.add(mainMethod);

        for (MethodDeclaration method : allMethods) {
            method.findAll(NameExpr.class).forEach(nameExpr -> {
                String fieldName = nameExpr.getNameAsString();
                sourceClass.getFields().stream()
                        .filter(field -> field.getVariables().stream()
                                .anyMatch(variable -> variable.getNameAsString().equals(fieldName)))
                        .forEach(requiredFields::add);
            });
        }

        return requiredFields;
    }
}
