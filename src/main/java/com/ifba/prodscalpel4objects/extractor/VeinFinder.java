package com.ifba.prodscalpel4objects.extractor;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.JavaParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class VeinFinder {
    private final Path sourceRoot;
    private final String targetMethodName;
    private final List<String> targetParameterTypes;
    private final Set<MethodDeclaration> collectedMethods = new HashSet<>();
    private final Set<ClassOrInterfaceDeclaration> collectedClasses = new HashSet<>();
    private final Set<FieldDeclaration> collectedFields = new HashSet<>();

    public VeinFinder(Path sourceRoot, String targetMethodName, List<String> parameterTypes) {
        this.sourceRoot = sourceRoot;
        this.targetMethodName = targetMethodName;
        this.targetParameterTypes = parameterTypes != null ? parameterTypes : Collections.emptyList();
    }

    public void extractCallPaths() throws IOException {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot.toFile()));

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        // Configuração correta para versões recentes do JavaParser
        ParserConfiguration parserConfig = new ParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

        JavaParser javaParser = new JavaParser(parserConfig);

        // Processar todos os arquivos Java
        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> processFileForCalls(path, javaParser));

        // Extrair métodos e classes dependentes
        extractDependentCode();
    }

    private void processFileForCalls(Path filePath, JavaParser javaParser) {
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(filePath);
            if (!parseResult.isSuccessful()) {
                System.err.println("Erro ao analisar: " + filePath);
                return;
            }

            CompilationUnit cu = parseResult.getResult().orElseThrow();

            cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
                try {
                    ResolvedMethodDeclaration resolved = methodCall.resolve();

                    if (isTargetMethod(resolved)) {
                        methodCall.findAncestor(MethodDeclaration.class).ifPresent(callingMethod -> {
                            collectedMethods.add(callingMethod);
                            callingMethod.findAncestor(ClassOrInterfaceDeclaration.class)
                                    .ifPresent(collectedClasses::add);

                            // Coletar campos usados no método chamador
                            collectUsedFields(callingMethod);
                        });
                    }
                } catch (Exception e) {
                    // Ignorar métodos não resolvidos
                    System.err.println("Erro ao resolver método: " + methodCall + " - " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("Erro ao processar arquivo: " + filePath);
        }
    }

    private boolean isTargetMethod(ResolvedMethodDeclaration resolved) {
        if (!resolved.getName().equals(targetMethodName)) {
            return false;
        }

        if (targetParameterTypes.isEmpty()) {
            return true;
        }

        if (resolved.getNumberOfParams() != targetParameterTypes.size()) {
            return false;
        }

        for (int i = 0; i < targetParameterTypes.size(); i++) {
            String expected = targetParameterTypes.get(i);
            String actual = resolved.getParam(i).getType().describe();

            if (!actual.endsWith(expected)) {
                return false;
            }
        }

        return true;
    }

    private void extractDependentCode() {
        // Criar uma cópia para iterar enquanto modificamos
        Set<MethodDeclaration> methodsToProcess = new HashSet<>(collectedMethods);

        while (!methodsToProcess.isEmpty()) {
            MethodDeclaration current = methodsToProcess.iterator().next();
            methodsToProcess.remove(current);

            // Encontrar métodos chamados por este método
            Set<MethodDeclaration> calledMethods = findCalledMethods(current);

            for (MethodDeclaration called : calledMethods) {
                if (!collectedMethods.contains(called)) {
                    collectedMethods.add(called);
                    methodsToProcess.add(called);

                    // Coletar a classe do método chamado
                    called.findAncestor(ClassOrInterfaceDeclaration.class)
                            .ifPresent(collectedClasses::add);
                }
            }

            // Coletar campos usados por este método
            collectUsedFields(current);
        }
    }

    private Set<MethodDeclaration> findCalledMethods(MethodDeclaration method) {
        Set<MethodDeclaration> calledMethods = new HashSet<>();

        method.findAll(MethodCallExpr.class).forEach(call -> {
            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                call.findAncestor(CompilationUnit.class).ifPresent(cu -> {
                    cu.findFirst(MethodDeclaration.class, m ->
                            m.getNameAsString().equals(resolved.getName()) &&
                                    matchesParameters(m, resolved)
                    ).ifPresent(calledMethods::add);
                });
            } catch (Exception e) {
                // Ignorar métodos não resolvidos
            }
        });

        return calledMethods;
    }

    private boolean matchesParameters(MethodDeclaration method, ResolvedMethodDeclaration resolved) {
        if (method.getParameters().size() != resolved.getNumberOfParams()) {
            return false;
        }

        for (int i = 0; i < method.getParameters().size(); i++) {
            String methodParam = method.getParameter(i).getTypeAsString();
            String resolvedParam = resolved.getParam(i).getType().describe();

            if (!resolvedParam.endsWith(methodParam)) {
                return false;
            }
        }

        return true;
    }

    private void collectUsedFields(MethodDeclaration method) {
        method.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(parentClass -> {
            method.findAll(NameExpr.class).forEach(nameExpr -> {
                parentClass.getFields().stream()
                        .filter(field -> field.getVariables().stream()
                                .anyMatch(v -> v.getNameAsString().equals(nameExpr.getNameAsString())))
                        .forEach(collectedFields::add);
            });
        });
    }

    public void saveExtractedCode(Path outputDir) throws IOException {
        // Criar estrutura de pacotes
        Files.createDirectories(outputDir);

        // Salvar cada classe coletada
        for (ClassOrInterfaceDeclaration cls : collectedClasses) {
            saveClassWithDependencies(cls, outputDir);
        }
    }

    private void saveClassWithDependencies(ClassOrInterfaceDeclaration originalClass, Path outputDir) throws IOException {
        CompilationUnit newCU = new CompilationUnit();

        // Manter o pacote original
        originalClass.findCompilationUnit().ifPresent(cu -> {
            cu.getPackageDeclaration().ifPresent(newCU::setPackageDeclaration);
            cu.getImports().forEach(newCU::addImport);
        });

        // Criar nova classe com mesmo nome e modificadores
        ClassOrInterfaceDeclaration newClass = newCU.addClass(originalClass.getNameAsString());
        newClass.setModifiers(originalClass.getModifiers());

        // Adicionar campos necessários
        collectedFields.stream()
                .filter(field -> field.getParentNode().equals(Optional.of(originalClass)))
                .forEach(newClass::addMember);

        // Adicionar métodos coletados desta classe
        collectedMethods.stream()
                .filter(method -> method.getParentNode().equals(Optional.of(originalClass)))
                .forEach(newClass::addMember);

        // Salvar arquivo
        AtomicReference<Path> packagePath = new AtomicReference<>(outputDir);
        originalClass.findCompilationUnit().ifPresent(cu -> {
            cu.getPackageDeclaration().ifPresent(pkg -> {
                packagePath.set(outputDir.resolve(pkg.getNameAsString().replace(".", "/")));
            });
        });

        Files.createDirectories(packagePath.get());
        Path outputFile = packagePath.get().resolve(originalClass.getNameAsString() + ".java");
        Files.writeString(outputFile, newCU.toString());
    }
}
