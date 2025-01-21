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
 * Agora com suporte para dependências em classes de outros pacotes.
 *
 * @author lara
 */
public class MethodExtractorV1 {

    private final Path sourceRoot;

    public MethodExtractorV1(String sourceRootPath) {
        this.sourceRoot = Paths.get(sourceRootPath);
    }

    // Método principal para extração
    public void extract(String sourceFilePath, String methodToBeExtracted) {
        try {
            // Caminho para o arquivo-fonte
            File source = new File(sourceFilePath);

            // Cria uma instância do JavaParser para analisar o código
            JavaParser javaParser = new JavaParser();

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
            Set<MethodDeclaration> dependentMethods = findAllDependentMethods(method, sourceClass, sourceParseResult.getResult().orElseThrow());


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

            // Escreve o conteúdo atualizado no arquivo de destino
            Files.write(targetFile.toPath(), targetCU.toString().getBytes());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Encontra todos os métodos dependentes (em cadeia) chamados dentro de um método.
     */
    private Set<MethodDeclaration> findAllDependentMethods(MethodDeclaration method,
                                                           ClassOrInterfaceDeclaration sourceClass,
                                                           CompilationUnit sourceCompilationUnit) {
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
                    System.out.println("Método dependente encontrado: " + calledMethodName);
                    allDependentMethods.add(dependentMethodOpt.get());
                    methodsToProcess.add(dependentMethodOpt.get());
                } else {
                    System.out.println("Método dependente externo: " + calledMethodName);
                    dependentMethodOpt = findExternalMethod(call, sourceCompilationUnit); // Passa a CompilationUnit
                    dependentMethodOpt.ifPresent(dependentMethod -> {
                        allDependentMethods.add(dependentMethod);
                        methodsToProcess.add(dependentMethod);
                    });
                }
            }
        }

        return allDependentMethods;
    }

    /**
     * Tenta localizar um método em classes externas.
     */
    private Optional<MethodDeclaration> findExternalMethod(MethodCallExpr call, CompilationUnit sourceCompilationUnit) {
        try {
            // Obtém o escopo do método chamado (ex: "utilityClass")
            String scopeName = call.getScope().map(Object::toString).orElse("");
            if (scopeName.isEmpty()) {
                return Optional.empty();
            }

            // Encontra a declaração da variável correspondente ao escopo
            Optional<String> className = sourceCompilationUnit.findAll(FieldDeclaration.class).stream()
                    .filter(field -> field.getVariables().stream()
                            .anyMatch(variable -> variable.getNameAsString().equals(scopeName)))
                    .map(field -> field.getElementType().asString())
                    .findFirst();

            if (className.isEmpty()) {
                System.out.println("Classe não encontrada para o escopo: " + scopeName);
                return Optional.empty();
            }

            // Analisa os imports para encontrar o caminho completo da classe
            Optional<String> importPath = sourceCompilationUnit.getImports().stream()
                    .map(importDecl -> importDecl.getName().toString())
                    .filter(importedClass -> importedClass.endsWith(className.get()))
                    .findFirst();

            if (importPath.isEmpty()) {
                System.out.println("Import correspondente não encontrado para a classe: " + className.get());
                return Optional.empty();
            }

            // Constrói o caminho do arquivo baseado no import
            Path classFilePath = sourceRoot.resolve(importPath.get().replace(".", "/") + ".java");
            if (!Files.exists(classFilePath)) {
                System.out.println("Arquivo não encontrado para a classe importada: " + importPath.get());
                return Optional.empty();
            }

            // Faz o parse do arquivo encontrado
            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = javaParser.parse(classFilePath);

            // Busca o método chamado dentro da classe encontrada
            return parseResult.getResult()
                    .flatMap(cu -> cu.findFirst(ClassOrInterfaceDeclaration.class))
                    .flatMap(cls -> cls.findFirst(MethodDeclaration.class,
                            m -> m.getNameAsString().equals(call.getNameAsString())));

        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
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
