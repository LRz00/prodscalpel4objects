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
import java.io.IOException;
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

    /**
     * Método principal para extração
     */
    public void extract(String sourceFilePath, String methodToBeExtracted) {
        try {
            File source = new File(sourceFilePath);
            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> sourceParseResult = javaParser.parse(source);

            Optional<CompilationUnit> cuOpt = sourceParseResult.getResult();
            if (cuOpt.isEmpty()) {
                System.out.println("Falha ao analisar o arquivo: " + sourceFilePath);
                return;
            }
            CompilationUnit cu = cuOpt.get();

            Optional<String> packageNameOpt = cu.getPackageDeclaration().map(pd -> pd.getNameAsString());
            String packagePath = packageNameOpt.map(pkg -> pkg.replace(".", "/")).orElse("");
            Path targetDirectory = Paths.get("IceBox", packagePath);
            Files.createDirectories(targetDirectory);

            Optional<ClassOrInterfaceDeclaration> sourceClassOpt = cu.findFirst(ClassOrInterfaceDeclaration.class);
            if (sourceClassOpt.isEmpty()) {
                System.out.println("Classe fonte não encontrada.");
                return;
            }
            ClassOrInterfaceDeclaration sourceClass = sourceClassOpt.get();

            MethodDeclaration method = sourceClass.findFirst(MethodDeclaration.class,
                    m -> m.getNameAsString().equals(methodToBeExtracted)).orElse(null);

            if (method == null) {
                System.out.println("Método não encontrado: " + methodToBeExtracted);
                return;
            }

            Set<MethodDeclaration> dependentMethods = findAllDependentMethods(method, sourceClass, cu);
            Set<FieldDeclaration> requiredFields = findRequiredFields(method, dependentMethods, sourceClass);
            saveClassFile(cu, sourceClass, targetDirectory);

            for (MethodDeclaration depMethod : dependentMethods) {
                saveExternalMethod(depMethod, cu);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveClassFile(CompilationUnit cu, ClassOrInterfaceDeclaration cls, Path targetDirectory) throws IOException {
        String classFileName = cls.getNameAsString() + ".java";
        Path classFilePath = targetDirectory.resolve(classFileName);
        Files.writeString(classFilePath, cu.toString());
    }

    private void saveExternalMethod(MethodDeclaration method, CompilationUnit sourceCU) throws IOException {
        Optional<ClassOrInterfaceDeclaration> parentClassOpt = method.findAncestor(ClassOrInterfaceDeclaration.class);
        if (parentClassOpt.isEmpty()) return;

        ClassOrInterfaceDeclaration parentClass = parentClassOpt.get();
        Optional<String> importPath = findImportPath(parentClass.getNameAsString(), sourceCU);
        if (importPath.isEmpty()) return;

        Path methodTargetDirectory = Paths.get("IceBox", importPath.get().replace(".", "/"));
        Files.createDirectories(methodTargetDirectory);

        CompilationUnit methodCU = new CompilationUnit();
        methodCU.setPackageDeclaration(importPath.get());
        ClassOrInterfaceDeclaration newClass = methodCU.addClass(parentClass.getNameAsString());
        newClass.addMember(method.clone());

        String methodFileName = parentClass.getNameAsString() + "_" + method.getNameAsString() + ".java";
        Files.writeString(methodTargetDirectory.resolve(methodFileName), methodCU.toString());
    }

    private Optional<String> findImportPath(String className, CompilationUnit sourceCU) {
        return sourceCU.getImports().stream()
                .map(importDecl -> importDecl.getName().toString())
                .filter(importedClass -> importedClass.endsWith(className))
                .findFirst();
    }

    private Set<MethodDeclaration> findAllDependentMethods(MethodDeclaration method,
                                                           ClassOrInterfaceDeclaration sourceClass,
                                                           CompilationUnit sourceCU) {
        Set<MethodDeclaration> allDependentMethods = new HashSet<>();
        Set<MethodDeclaration> processedMethods = new HashSet<>();
        Set<MethodDeclaration> methodsToProcess = new HashSet<>();
        methodsToProcess.add(method);

        while (!methodsToProcess.isEmpty()) {
            MethodDeclaration currentMethod = methodsToProcess.iterator().next();
            methodsToProcess.remove(currentMethod);

            if (processedMethods.contains(currentMethod)) continue;
            processedMethods.add(currentMethod);

            List<MethodCallExpr> methodCalls = currentMethod.findAll(MethodCallExpr.class);
            for (MethodCallExpr call : methodCalls) {
                Optional<MethodDeclaration> dependentMethodOpt = sourceClass.findFirst(MethodDeclaration.class,
                        m -> m.getNameAsString().equals(call.getNameAsString()));

                if (dependentMethodOpt.isPresent()) {
                    allDependentMethods.add(dependentMethodOpt.get());
                    methodsToProcess.add(dependentMethodOpt.get());
                } else {
                    dependentMethodOpt = findExternalMethod(call, sourceCU);
                    dependentMethodOpt.ifPresent(dependentMethod -> {
                        allDependentMethods.add(dependentMethod);
                        methodsToProcess.add(dependentMethod);
                    });
                }
            }
        }
        return allDependentMethods;
    }

    private Optional<MethodDeclaration> findExternalMethod(MethodCallExpr call, CompilationUnit sourceCU) {
        try {
            String scopeName = call.getScope().map(Object::toString).orElse("");
            if (scopeName.isEmpty()) return Optional.empty();

            Optional<String> className = sourceCU.findAll(FieldDeclaration.class).stream()
                    .filter(field -> field.getVariables().stream()
                            .anyMatch(variable -> variable.getNameAsString().equals(scopeName)))
                    .map(field -> field.getElementType().asString())
                    .findFirst();

            if (className.isEmpty()) return Optional.empty();
            Optional<String> importPath = findImportPath(className.get(), sourceCU);
            if (importPath.isEmpty()) return Optional.empty();

            Path classFilePath = sourceRoot.resolve(importPath.get().replace(".", "/") + ".java");
            if (!Files.exists(classFilePath)) return Optional.empty();

            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = javaParser.parse(classFilePath);
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
