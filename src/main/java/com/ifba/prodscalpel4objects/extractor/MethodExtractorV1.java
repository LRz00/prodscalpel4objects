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
 * Classe responsável por extrair um método e suas dependências para novos arquivos.
 *
 * @author lara
 */
public class MethodExtractorV1 {

    public MethodExtractorV1() {
    }

    public void extract() {
        try {
            // Caminho do diretório fonte
            String sourceDirPath = "/home/lara/Documentos/TCC-EXAMPLE/src/com/example/service/";
            String methodToBeExtracted = "processDataWithExtraLogic";

            // Diretório de destino
            Path targetDirectory = Paths.get("IceBox");
            if (!Files.exists(targetDirectory)) {
                Files.createDirectory(targetDirectory);
            }

            // Inicializa o JavaParser
            JavaParser javaParser = new JavaParser();

            // Analisa todos os arquivos no diretório fonte
            Map<String, CompilationUnit> sourceFiles = new HashMap<>();
            Files.walk(Paths.get(sourceDirPath))
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            ParseResult<CompilationUnit> parseResult = javaParser.parse(path.toFile());
                            parseResult.getResult().ifPresent(cu -> sourceFiles.put(path.getFileName().toString(), cu));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

            // Busca o método principal em todas as classes
            Optional<MethodDeclaration> mainMethodOpt = sourceFiles.values().stream()
                    .flatMap(cu -> cu.findAll(ClassOrInterfaceDeclaration.class).stream())
                    .flatMap(cls -> cls.findAll(MethodDeclaration.class).stream())
                    .filter(method -> method.getNameAsString().equals(methodToBeExtracted))
                    .findFirst();

            if (!mainMethodOpt.isPresent()) {
                System.out.println("Método principal não encontrado: " + methodToBeExtracted);
                return;
            }

            MethodDeclaration mainMethod = mainMethodOpt.get();
            ClassOrInterfaceDeclaration mainClass = (ClassOrInterfaceDeclaration) mainMethod.getParentNode().get();

            // Encontra dependências
            Map<ClassOrInterfaceDeclaration, Set<MethodDeclaration>> dependentMethods = findAllDependentMethods(mainMethod, mainClass, sourceFiles);
            Set<FieldDeclaration> requiredFields = findRequiredFields(mainMethod, dependentMethods, sourceFiles);

            // Escreve métodos e dependências em arquivos separados
            for (Map.Entry<ClassOrInterfaceDeclaration, Set<MethodDeclaration>> entry : dependentMethods.entrySet()) {
                ClassOrInterfaceDeclaration sourceClass = entry.getKey();
                Set<MethodDeclaration> methods = entry.getValue();

                // Nome do arquivo de destino
                String targetFileName = sourceClass.getNameAsString() + ".java";
                File targetFile = targetDirectory.resolve(targetFileName).toFile();

                CompilationUnit targetCU = new CompilationUnit();
                ClassOrInterfaceDeclaration targetClass = targetCU.addClass(sourceClass.getNameAsString());

                // Adiciona métodos
                for (MethodDeclaration method : methods) {
                    targetClass.addMember(method.clone());
                }

                // Adiciona campos se for a classe principal
                if (sourceClass.equals(mainClass)) {
                    for (FieldDeclaration field : requiredFields) {
                        if (targetClass.getFieldByName(field.getVariables().get(0).getNameAsString()).isEmpty()) {
                            targetClass.addMember(field.clone());
                        }
                    }
                }

                // Escreve o arquivo
                Files.write(targetFile.toPath(), targetCU.toString().getBytes());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<ClassOrInterfaceDeclaration, Set<MethodDeclaration>> findAllDependentMethods(
            MethodDeclaration method,
            ClassOrInterfaceDeclaration mainClass,
            Map<String, CompilationUnit> sourceFiles) {

        Map<ClassOrInterfaceDeclaration, Set<MethodDeclaration>> result = new HashMap<>();
        Queue<MethodDeclaration> queue = new LinkedList<>();
        queue.add(method);

        while (!queue.isEmpty()) {
            MethodDeclaration currentMethod = queue.poll();
            ClassOrInterfaceDeclaration currentClass = (ClassOrInterfaceDeclaration) currentMethod.getParentNode().get();

            result.putIfAbsent(currentClass, new HashSet<>());
            if (!result.get(currentClass).add(currentMethod)) {
                continue; // Método já processado
            }

            // Encontra chamadas de método
            List<MethodCallExpr> methodCalls = currentMethod.findAll(MethodCallExpr.class);
            for (MethodCallExpr call : methodCalls) {
                String calledMethodName = call.getNameAsString();

                // Procura o método na classe atual ou em outras classes
                sourceFiles.values().stream()
                        .flatMap(cu -> cu.findAll(ClassOrInterfaceDeclaration.class).stream())
                        .flatMap(cls -> cls.findAll(MethodDeclaration.class).stream())
                        .filter(m -> m.getNameAsString().equals(calledMethodName))
                        .forEach(queue::add);
            }
        }

        return result;
    }

    private Set<FieldDeclaration> findRequiredFields(
            MethodDeclaration mainMethod,
            Map<ClassOrInterfaceDeclaration, Set<MethodDeclaration>> dependentMethods,
            Map<String, CompilationUnit> sourceFiles) {

        Set<FieldDeclaration> requiredFields = new HashSet<>();
        Set<MethodDeclaration> allMethods = new HashSet<>(dependentMethods.values().stream().flatMap(Set::stream).toList());
        allMethods.add(mainMethod);

        for (MethodDeclaration method : allMethods) {
            method.findAll(NameExpr.class).forEach(nameExpr -> {
                String fieldName = nameExpr.getNameAsString();
                sourceFiles.values().stream()
                        .flatMap(cu -> cu.findAll(ClassOrInterfaceDeclaration.class).stream())
                        .flatMap(cls -> cls.getFields().stream())
                        .filter(field -> field.getVariables().stream()
                                .anyMatch(variable -> variable.getNameAsString().equals(fieldName)))
                        .forEach(requiredFields::add);
            });
        }

        return requiredFields;
    }
}

