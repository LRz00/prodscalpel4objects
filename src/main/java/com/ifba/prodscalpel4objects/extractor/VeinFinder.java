package com.ifba.prodscalpel4objects.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Classe responsável por extrair o caminho de chamada reverso (callers) de um
 * método.
 */
public class VeinFinder {
    private final Path sourceRoot;
    private final JavaParser javaParser;
    private final CombinedTypeSolver typeSolver;

    public VeinFinder(String sourceRootPath) {
        this.sourceRoot = Paths.get(sourceRootPath);
        this.typeSolver = new CombinedTypeSolver();
        this.typeSolver.add(new ReflectionTypeSolver());
        this.typeSolver.add(new JavaParserTypeSolver(sourceRoot));

        this.javaParser = new JavaParser();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        this.javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    private Set<MethodDeclaration> findMethodCallers(String methodName, String className) throws IOException {
        Set<MethodDeclaration> callers = new HashSet<>();

        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        ParseResult<CompilationUnit> parseResult = javaParser.parse(path);
                        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                            CompilationUnit cu = parseResult.getResult().get();

                            // Encontrar todas as chamadas de método no arquivo
                            List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);

                            for (MethodCallExpr call : methodCalls) {
                                try {
                                    // Verifica se o nome do método corresponde
                                    if (call.getNameAsString().equals(methodName)) {
                                        // Tenta resolver a declaração do método
                                        System.out.println("Tentando resolver call: " + call + " na classe: " + path);
                                        ResolvedMethodDeclaration resolved = call.resolve();

                                        // Verifica se a classe declarante corresponde
                                        String declaringClass = resolved.getClassName();
                                        if (declaringClass.equals(className)) {
                                            // Encontrar o método que contém esta chamada
                                            Optional<MethodDeclaration> callerMethod = call
                                                    .findAncestor(MethodDeclaration.class);
                                            callerMethod.ifPresent(callers::add);
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignora métodos que não podem ser resolvidos
                                    System.err.println("Could not resolve method call: " + call + " in file: " + path);
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        return callers;
    }

    private void extractCallerContext(MethodDeclaration callerMethod, Path outputDir) throws IOException {
        // 1. Get the containing class
        ClassOrInterfaceDeclaration callerClass = callerMethod.findAncestor(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalStateException("Método não está em uma classe"));

        // 2. Get original CompilationUnit
        CompilationUnit originalCU = callerMethod.findCompilationUnit()
                .orElseThrow(() -> new IllegalStateException("Não foi possível obter a CompilationUnit"));

        // 3. Create new CompilationUnit with package and imports
        CompilationUnit newCU = new CompilationUnit();
        originalCU.getPackageDeclaration().ifPresent(newCU::setPackageDeclaration);
        originalCU.getImports().forEach(newCU::addImport);

        // 4. Create simplified version of caller class
        ClassOrInterfaceDeclaration newClass = newCU.addClass(callerClass.getNameAsString());

        // Copy class annotations (especially important for Spring controllers)
        callerClass.getAnnotations().forEach(newClass::addAnnotation);

        // Copy class modifiers
        newClass.setModifiers(callerClass.getModifiers());

        // 5. Add the caller method
        MethodDeclaration clonedMethod = callerMethod.clone();
        newClass.addMember(clonedMethod);

        // 6. Add fields needed by the method
        Set<String> fieldNames = callerMethod.findAll(NameExpr.class).stream()
                .map(NameExpr::getNameAsString)
                .collect(Collectors.toSet());

        callerClass.getFields().stream()
                .filter(field -> field.getVariables().stream()
                        .anyMatch(v -> fieldNames.contains(v.getNameAsString())))
                .forEach(newClass::addMember);

        // 7. Save the file - CORREÇÃO: usar estrutura Maven padrão
        String packagePath = originalCU.getPackageDeclaration()
                .map(p -> p.getNameAsString().replace(".", "/"))
                .orElse("");

        // CORREÇÃO: Criar estrutura src/main/java dentro do outputDir
        Path mavenOutputPath = outputDir.resolve("src/main/java");
        Path outputPath = mavenOutputPath.resolve(Paths.get(packagePath, callerClass.getNameAsString() + ".java"));
        Files.createDirectories(outputPath.getParent());

        if (!Files.exists(outputPath)) {
            Files.writeString(outputPath, newCU.toString());
            System.out.println("Caller context saved: " + outputPath);
        }
    }

    private void extractTargetClass(String className, Path outputDir) throws IOException {
        // Encontrar o arquivo da classe alvo
        Optional<Path> targetClassPath = Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(className + ".java"))
                .findFirst();

        if (targetClassPath.isPresent()) {
            // CORREÇÃO: Usar estrutura Maven padrão
            Path mavenOutputPath = outputDir.resolve("src/main/java");
            
            // Copiar o arquivo inteiro para o diretório de saída mantendo a estrutura de pacotes
            Path outputPath = mavenOutputPath.resolve(sourceRoot.relativize(targetClassPath.get()));
            Files.createDirectories(outputPath.getParent());
            
            if (!Files.exists(outputPath)) {
                Files.copy(targetClassPath.get(), outputPath);
                System.out.println("Target class saved: " + outputPath);
            } else {
                System.out.println("Arquivo já existe: " + outputPath + " - pulando cópia.");
            }
        }
    }

    public void extractFullCallPath(String targetMethodName, String targetClassName, Path outputDir)
            throws IOException {
        Set<MethodDeclaration> allCallers = new HashSet<>();
        Set<MethodDeclaration> currentLevelCallers = findMethodCallers(targetMethodName, targetClassName);

        // First, extract the target class
        extractTargetClass(targetClassName, outputDir);

        // Then process all callers
        while (!currentLevelCallers.isEmpty()) {
            Set<MethodDeclaration> nextLevel = new HashSet<>();

            for (MethodDeclaration caller : currentLevelCallers) {
                if (!allCallers.contains(caller)) {
                    allCallers.add(caller);
                    extractCallerContext(caller, outputDir);

                    // Find callers of this caller for next level
                    String callerClassName = caller.findAncestor(ClassOrInterfaceDeclaration.class)
                            .map(ClassOrInterfaceDeclaration::getNameAsString)
                            .orElse("");
                    nextLevel.addAll(findMethodCallers(caller.getNameAsString(), callerClassName));
                }
            }

            currentLevelCallers = nextLevel;
        }
    }

    // CORREÇÃO: Método adicional para garantir compatibilidade com MethodExtractorV1
    public void extractFullCallPath(String targetMethodName, String targetClassName, String sourceFilePath, Path outputDir)
            throws IOException {
        // Primeiro extrai a classe alvo baseada no sourceFilePath
        Path sourcePath = Paths.get(sourceFilePath);
        if (Files.exists(sourcePath)) {
            Path mavenOutputPath = outputDir.resolve("src/main/java");
            Files.createDirectories(mavenOutputPath);
            
            // Copia o arquivo fonte para a estrutura Maven
            Path outputPath = mavenOutputPath.resolve(sourcePath.getFileName());
            if (!Files.exists(outputPath)) {
                Files.copy(sourcePath, outputPath);
                System.out.println("Source class saved: " + outputPath);
            }
        }
        
        // Depois extrai o call path normalmente
        extractFullCallPath(targetMethodName, targetClassName, outputDir);
    }
}