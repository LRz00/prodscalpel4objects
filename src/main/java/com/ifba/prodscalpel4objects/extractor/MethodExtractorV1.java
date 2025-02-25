package com.ifba.prodscalpel4objects.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

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

// TODO: -Class annotations are not being preserved in extracted code.
public class MethodExtractorV1 {

    private final Path sourceRoot;

    /**
     * Construtor da classe MethodExtractorV1.
     *
     * @param sourceRootPath Caminho do diretório raiz do código-fonte.
     */
    public MethodExtractorV1(String sourceRootPath) {
        this.sourceRoot = Paths.get(sourceRootPath);
    }

    /**
     * Método principal para extração do método e suas dependências.
     *
     * @param sourceFilePath      Caminho do arquivo-fonte.
     * @param methodToBeExtracted Nome do método a ser extraído.
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

            Set<MethodDeclaration> dependentMethods = findAllDependentMethods(method, sourceClass, cu, Paths.get(sourceFilePath).getParent());
            Set<FieldDeclaration> requiredFields = findRequiredFields(method, dependentMethods, sourceClass);

            // Separa os métodos dependentes em duas categorias:
            // 1. Métodos da mesma classe (AnimalService).
            // 2. Métodos de outras classes.
            Set<MethodDeclaration> sameClassMethods = new HashSet<>();
            Set<MethodDeclaration> externalMethods = new HashSet<>();

            for (MethodDeclaration depMethod : dependentMethods) {
                Optional<ClassOrInterfaceDeclaration> parentClassOpt = depMethod.findAncestor(ClassOrInterfaceDeclaration.class);
                if (parentClassOpt.isPresent() && parentClassOpt.get().getNameAsString().equals(sourceClass.getNameAsString())) {
                    sameClassMethods.add(depMethod); // Método da mesma classe
                } else {
                    externalMethods.add(depMethod); // Método de outra classe
                }
            }

            // Salva a classe do método principal junto com os métodos da mesma classe
            saveClassFile(cu, sourceClass, targetDirectory, method, sameClassMethods, requiredFields);

            // Salva métodos externos (de outras classes)
            for (MethodDeclaration externalMethod : externalMethods) {
                saveExternalMethod(externalMethod, cu);
            }

            // Salva classes dependentes
            Set<String> requiredClasses = findRequiredClasses(method, sourceClass, cu);
// No método extract, substitua a chamada para saveClass:
            for (String className : requiredClasses) {
                // Verifica se a classe atual é a classe fonte
                if (className.equals(sourceClass.getNameAsString())) {
                    // Para a classe fonte, usamos os métodos e campos já identificados
                    saveClass(className, cu, sameClassMethods, requiredFields);
                } else {
                    // Para classes externas, precisamos encontrar os métodos e campos dependentes
                    Set<MethodDeclaration> classDependentMethods = new HashSet<>();
                    Set<FieldDeclaration> classRequiredFields = new HashSet<>();

                    // Encontra a classe externa no código-fonte
                    Optional<String> importPath = findImportPath(className, cu);
                    if (importPath.isPresent()) {
                        Path classFilePath = sourceRoot.resolve(importPath.get().replace(".", "/") + ".java");
                        if (Files.exists(classFilePath)) {
                            try {
                                // Analisa a classe externa
                                JavaParser exJavaParser = new JavaParser();
                                ParseResult<CompilationUnit> parseResult = exJavaParser.parse(classFilePath);
                                if (parseResult.getResult().isPresent()) {
                                    CompilationUnit classCU = parseResult.getResult().get();
                                    Optional<ClassOrInterfaceDeclaration> classOpt = classCU.findFirst(ClassOrInterfaceDeclaration.class);

                                    if (classOpt.isPresent()) {
                                        ClassOrInterfaceDeclaration externalClass = classOpt.get();

                                        // Encontra os métodos dependentes na classe externa
                                        for (MethodDeclaration externalMethod : externalMethods) {
                                            Optional<ClassOrInterfaceDeclaration> parentClassOpt = externalMethod.findAncestor(ClassOrInterfaceDeclaration.class);
                                            if (parentClassOpt.isPresent() && parentClassOpt.get().getNameAsString().equals(className)) {
                                                classDependentMethods.add(externalMethod);
                                            }
                                        }

                                        // Encontra os campos dependentes na classe externa
                                        classRequiredFields.addAll(findRequiredFieldsForExternalClass(externalClass, classDependentMethods));
                                    }
                                }
                            } catch (IOException e) {
                                System.err.println("Erro ao ler o arquivo da classe: " + classFilePath);
                            }
                        }
                    }

                    // Chama saveClass com os métodos e campos dependentes da classe externa
                    saveClass(className, cu, classDependentMethods, classRequiredFields);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Salva o arquivo da classe extraída, contendo o método principal e os métodos dependentes da mesma classe.
     *
     * @param cu               A unidade de compilação da classe.
     * @param cls              A classe a ser salva.
     * @param targetDirectory  O diretório de destino para o arquivo da classe.
     * @param mainMethod       O método principal que está sendo extraído.
     * @param dependentMethods Os métodos dependentes da mesma classe.
     * @param requiredFields   Os campos necessários para os métodos.
     * @throws IOException Caso ocorra um erro ao escrever o arquivo.
     */
    private void saveClassFile(CompilationUnit cu, ClassOrInterfaceDeclaration cls, Path targetDirectory,
                               MethodDeclaration mainMethod, Set<MethodDeclaration> dependentMethods,
                               Set<FieldDeclaration> requiredFields) throws IOException {
        // Cria uma nova CompilationUnit com o mesmo pacote e imports
        CompilationUnit newCU = new CompilationUnit();
        cu.getPackageDeclaration().ifPresent(newCU::setPackageDeclaration);
        cu.getImports().forEach(newCU::addImport);

        // Cria uma nova classe com o mesmo nome
        ClassOrInterfaceDeclaration newClass = newCU.addClass(cls.getNameAsString());

        // Copia as anotações da classe original
        cls.getAnnotations().forEach(newClass::addAnnotation);

        // Copia os campos necessários
        requiredFields.forEach(newClass::addMember);

        // Copia o método principal
        newClass.addMember(mainMethod.clone());

        // Copia os métodos dependentes da mesma classe
        for (MethodDeclaration depMethod : dependentMethods) {
            newClass.addMember(depMethod.clone());
        }

        // Salva a nova CompilationUnit no diretório de destino
        String classFileName = cls.getNameAsString() + ".java";
        Path classFilePath = targetDirectory.resolve(classFileName);
        Files.writeString(classFilePath, newCU.toString());
        System.out.println("Classe salva em: " + classFilePath);
    }

    /**
     * Salva o método externo em um arquivo se ele ainda não existir.
     *
     * @param method   O método a ser salvo.
     * @param sourceCU A unidade de compilação do código-fonte.
     * @throws IOException Caso ocorra um erro ao escrever o arquivo.
     */
    private void saveExternalMethod(MethodDeclaration method, CompilationUnit sourceCU) throws IOException {
        Optional<ClassOrInterfaceDeclaration> parentClassOpt = method.findAncestor(ClassOrInterfaceDeclaration.class);
        if (parentClassOpt.isEmpty()) return;

        ClassOrInterfaceDeclaration parentClass = parentClassOpt.get();
        Optional<String> importPath = findImportPath(parentClass.getNameAsString(), sourceCU);
        if (importPath.isEmpty()) return;

        // Remove o nome da classe do importPath (corta até o último ponto final)
        String packagePath = importPath.get();
        int lastDotIndex = packagePath.lastIndexOf('.');
        if (lastDotIndex != -1) {
            packagePath = packagePath.substring(0, lastDotIndex); // Corta até o último ponto
        }

        // Cria o diretório de destino no IceBox com a estrutura de pacotes original
        Path methodTargetDirectory = Paths.get("IceBox", packagePath.replace(".", "/"));
        Files.createDirectories(methodTargetDirectory);

        // Define o caminho do arquivo da classe (sem criar subdiretórios adicionais)
        Path classFilePath = methodTargetDirectory.resolve(parentClass.getNameAsString() + ".java");

        CompilationUnit methodCU;
        ClassOrInterfaceDeclaration newClass;

        if (Files.exists(classFilePath)) {
            // Se o arquivo já existe, carrega o conteúdo existente
            String existingCode = Files.readString(classFilePath);
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(existingCode);

            if (parseResult.getResult().isPresent()) {
                methodCU = parseResult.getResult().get();
                Optional<ClassOrInterfaceDeclaration> existingClassOpt = methodCU.findFirst(ClassOrInterfaceDeclaration.class);

                if (existingClassOpt.isPresent()) {
                    newClass = existingClassOpt.get();
                } else {
                    newClass = methodCU.addClass(parentClass.getNameAsString());
                }
            } else {
                return; // Erro ao carregar o arquivo existente
            }
        } else {
            // Cria um novo arquivo se ele não existir
            methodCU = new CompilationUnit();
            methodCU.setPackageDeclaration(packagePath); // Usa o packagePath sem o nome da classe
            newClass = methodCU.addClass(parentClass.getNameAsString());
        }

        // Adiciona os imports necessários
        sourceCU.getImports().forEach(methodCU::addImport);

        // Verifica se o método já existe no arquivo
        boolean methodExists = newClass.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals(method.getNameAsString()));

        if (!methodExists) {
            // Adiciona o método ao arquivo
            newClass.addMember(method.clone());
            Files.writeString(classFilePath, methodCU.toString());
            System.out.println("Método salvo em: " + classFilePath);
        }
    }
    /**
     * Encontra o caminho de importação de uma classe, se ela existir no código.
     * Agora com suporte para importações com *.
     *
     * @param className O nome da classe a ser localizada.
     * @param sourceCU  A unidade de compilação do código-fonte.
     * @return O caminho de importação da classe, se encontrado.
     */
    private Optional<String> findImportPath(String className, CompilationUnit sourceCU) {
        // Limpa o nome da classe
        String sanitizedClassName = sanitizeClassName(className);

        // Verifica as importações explícitas
        Optional<String> explicitImport = sourceCU.getImports().stream()
                .map(importDecl -> importDecl.getName().toString())
                .filter(importedClass -> importedClass.endsWith(sanitizedClassName))
                .findFirst();

        if (explicitImport.isPresent()) {
            return explicitImport;
        }

        // Verifica importações com *
        Optional<String> wildcardImport = sourceCU.getImports().stream()
                .filter(importDecl -> importDecl.isAsterisk()) // Verifica se é uma importação com *
                .map(importDecl -> importDecl.getName().toString() + "." + sanitizedClassName) // Constrói o caminho completo
                .filter(importedClass -> {
                    // Verifica se o arquivo da classe existe no pacote
                    Path classFilePath = sourceRoot.resolve(importedClass.replace(".", "/") + ".java");
                    return Files.exists(classFilePath);
                })
                .findFirst();

        if (wildcardImport.isPresent()) {
            return wildcardImport;
        }

        // Verifica se a classe está no mesmo pacote
        Optional<String> packageName = sourceCU.getPackageDeclaration().map(pd -> pd.getNameAsString());
        if (packageName.isPresent()) {
            String samePackageClass = packageName.get() + "." + sanitizedClassName;
            Path samePackagePath = sourceRoot.resolve(packageName.get().replace(".", "/") + "/" + sanitizedClassName + ".java");
            if (Files.exists(samePackagePath)) {
                return Optional.of(samePackageClass);
            }
        }

        // Verifica se a classe está no pacote padrão (sem pacote)
        Path defaultPackagePath = sourceRoot.resolve(sanitizedClassName + ".java");
        if (Files.exists(defaultPackagePath)) {
            return Optional.of(sanitizedClassName);
        }

        return Optional.empty();
    }

    /**
     * Encontra todos os métodos dependentes do método fornecido.
     *
     * @param method      O método principal para o qual as dependências devem ser encontradas.
     * @param sourceClass A classe onde os métodos são definidos.
     * @param sourceCU    A unidade de compilação do código-fonte.
     * @param sourceRoot  O diretório raiz do código-fonte.
     * @return Um conjunto de métodos dependentes.
     */
    private Set<MethodDeclaration> findAllDependentMethods(MethodDeclaration method,
                                                           ClassOrInterfaceDeclaration sourceClass,
                                                           CompilationUnit sourceCU,
                                                           Path sourceRoot) {
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
                    MethodDeclaration dependentMethod = dependentMethodOpt.get();
                    if (!processedMethods.contains(dependentMethod)) {
                        allDependentMethods.add(dependentMethod);
                        methodsToProcess.add(dependentMethod);
                    }
                } else {
                    dependentMethodOpt = findExternalMethod(call, sourceCU);
                    dependentMethodOpt.ifPresent(dependentMethod -> {
                        if (!processedMethods.contains(dependentMethod)) {
                            allDependentMethods.add(dependentMethod);
                            methodsToProcess.add(dependentMethod);
                        }
                    });
                }
            }

            Set<String> instantiatedClasses = findInstantiatedClasses(currentMethod, sourceCU);
            for (String className : instantiatedClasses) {
                Optional<String> importPath = findImportPath(className, sourceCU);
                if (importPath.isPresent()) {
                    Path classFilePath = sourceRoot.resolve(importPath.get().replace(".", "/") + ".java");
                    if (Files.exists(classFilePath)) {
                        try {
                            JavaParser javaParser = new JavaParser();
                            ParseResult<CompilationUnit> parseResult = javaParser.parse(classFilePath);
                            if (parseResult.getResult().isPresent()) {
                                CompilationUnit classCU = parseResult.getResult().get();
                                classCU.findFirst(ClassOrInterfaceDeclaration.class)
                                        .ifPresent(classDecl -> {
                                            System.out.println("Classe dependente encontrada: " + classDecl.getNameAsString());
                                        });
                            }
                        } catch (IOException e) {
                            System.err.println("Erro ao ler o arquivo da classe: " + classFilePath);
                        }
                    }
                }
            }
        }
        return allDependentMethods;
    }

    /**
     * Encontra métodos externos chamados por um método, se eles existirem.
     *
     * @param call     A expressão de chamada do método.
     * @param sourceCU A unidade de compilação do código-fonte.
     * @return O método dependente externo, se encontrado.
     */
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
     *
     * @param mainMethod       O método principal.
     * @param dependentMethods Métodos dependentes.
     * @param sourceClass      A classe onde os campos são definidos.
     * @return Um conjunto de campos necessários.
     */
    private Set<FieldDeclaration> findRequiredFields(MethodDeclaration mainMethod, Set<MethodDeclaration> dependentMethods,
                                                     ClassOrInterfaceDeclaration sourceClass) {
        Set<FieldDeclaration> requiredFields = new HashSet<>();

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

    /**
     * Encontra classes instanciadas dentro do método.
     *
     * @param method   O método analisado.
     * @param sourceCU A unidade de compilação do código-fonte.
     * @return Um conjunto de classes instanciadas dentro do método.
     */
    private Set<String> findInstantiatedClasses(MethodDeclaration method, CompilationUnit sourceCU) {
        Set<String> instantiatedClasses = new HashSet<>();

        method.findAll(ObjectCreationExpr.class).forEach(objCreation -> {
            String className = objCreation.getTypeAsString();
            instantiatedClasses.add(className);
        });

        return instantiatedClasses;
    }

    /**
     * Encontra classes necessárias para a execução do método, incluindo tipos de retorno, parâmetros, campos e dependências injetadas.
     *
     * @param method      O método analisado.
     * @param sourceClass A classe onde o método está definido.
     * @param sourceCU    A unidade de compilação do código-fonte.
     * @return Um conjunto de classes necessárias.
     */
    private Set<String> findRequiredClasses(MethodDeclaration method, ClassOrInterfaceDeclaration sourceClass, CompilationUnit sourceCU) {
        Set<String> requiredClasses = new HashSet<>();

        // Adiciona classes usadas como tipos de retorno e parâmetros
        Type type = method.getType();
        if (type instanceof ClassOrInterfaceType) {
            requiredClasses.add(((ClassOrInterfaceType) type).getNameAsString());
        }

        method.getParameters().forEach(parameter -> {
            if (parameter.getType() instanceof ClassOrInterfaceType) {
                requiredClasses.add(((ClassOrInterfaceType) parameter.getType()).getNameAsString());
            }
        });

        // Adiciona classes usadas como tipos de campos
        sourceClass.getFields().forEach(field -> {
            if (field.getElementType() instanceof ClassOrInterfaceType) {
                requiredClasses.add(((ClassOrInterfaceType) field.getElementType()).getNameAsString());
            }
        });

        // Adiciona classes injetadas via Spring (campos com @Autowired ou @RequiredArgsConstructor)
        sourceClass.getFields().forEach(field -> {
            if (field.isAnnotationPresent("Autowired") || field.isAnnotationPresent("RequiredArgsConstructor")) {
                if (field.getElementType() instanceof ClassOrInterfaceType) {
                    requiredClasses.add(((ClassOrInterfaceType) field.getElementType()).getNameAsString());
                }
            }
        });

        // Adiciona classes declaradas dentro do método (variáveis locais)
        method.findAll(VariableDeclarationExpr.class).forEach(variableDeclaration -> {
            variableDeclaration.getVariables().forEach(variable -> {
                if (variable.getType() instanceof ClassOrInterfaceType) {
                    requiredClasses.add(((ClassOrInterfaceType) variable.getType()).getNameAsString());
                }
            });
        });

        return requiredClasses;
    }

    private void saveClass(String className, CompilationUnit sourceCU, Set<MethodDeclaration> dependentMethods, Set<FieldDeclaration> requiredFields) throws IOException {
        // Limpa o nome da classe
        String sanitizedClassName = sanitizeClassName(className);

        Optional<String> importPath = findImportPath(sanitizedClassName, sourceCU);
        if (importPath.isEmpty()) {
            System.out.println("Caminho de importação não encontrado para a classe: " + sanitizedClassName);
            return;
        }

        // Converte o caminho de importação para o formato de diretório
        String packagePath = importPath.get().replace(".", "/");
        Path classFilePath = sourceRoot.resolve(packagePath + ".java");

        if (!Files.exists(classFilePath)) {
            System.out.println("Arquivo da classe não encontrado: " + classFilePath);
            return;
        }

        // Cria o diretório de destino no IceBox com a estrutura de pacotes original
        Path targetDirectory = Paths.get("IceBox", packagePath).getParent();
        if (targetDirectory == null) {
            System.out.println("Não foi possível determinar o diretório de destino para a classe: " + sanitizedClassName);
            return;
        }
        Files.createDirectories(targetDirectory);

        // Salva a classe no diretório correto
        Path targetClassFilePath = Paths.get("IceBox", packagePath + ".java");
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(classFilePath);

        if (parseResult.getResult().isPresent()) {
            CompilationUnit classCU = parseResult.getResult().get();
            Optional<ClassOrInterfaceDeclaration> classOpt = classCU.findFirst(ClassOrInterfaceDeclaration.class);

            if (classOpt.isPresent()) {
                ClassOrInterfaceDeclaration originalClass = classOpt.get();
                CompilationUnit newCU = new CompilationUnit();
                classCU.getPackageDeclaration().ifPresent(newCU::setPackageDeclaration);
                classCU.getImports().forEach(newCU::addImport);

                // Cria uma nova classe com o mesmo nome, extends, implements e anotações
                ClassOrInterfaceDeclaration newClass = newCU.addClass(originalClass.getNameAsString());

                // Copia as anotações da classe original
                originalClass.getAnnotations().forEach(newClass::addAnnotation);

                // Copia extends
                if (originalClass.getExtendedTypes().isNonEmpty()) {
                    originalClass.getExtendedTypes().forEach(newClass::addExtendedType);
                }

                // Copia implements
                if (originalClass.getImplementedTypes().isNonEmpty()) {
                    originalClass.getImplementedTypes().forEach(newClass::addImplementedType);
                }

                // Copia todos os campos da classe original, incluindo anotações e modificadores
                originalClass.getFields().forEach(field -> {
                    FieldDeclaration newField = new FieldDeclaration();

                    // Copia as anotações do campo
                    field.getAnnotations().forEach(newField::addAnnotation);

                    // Copia os modificadores do campo
                    newField.setModifiers(field.getModifiers()); // Usa setModifiers

                    // Copia as variáveis do campo
                    field.getVariables().forEach(variable -> newField.addVariable(variable.clone()));

                    // Adiciona o campo à nova classe
                    newClass.addMember(newField);
                });

                // Copia os métodos dependentes, incluindo anotações e modificadores
                for (MethodDeclaration method : dependentMethods) {
                    MethodDeclaration newMethod = method.clone();

                    // Copia as anotações do método
                    method.getAnnotations().forEach(newMethod::addAnnotation);

                    // Copia os modificadores do método
                    newMethod.setModifiers(method.getModifiers()); // Usa setModifiers

                    // Adiciona o método à nova classe
                    newClass.addMember(newMethod);
                }

                // Salva a nova CompilationUnit no diretório de destino
                Files.writeString(targetClassFilePath, newCU.toString());
                System.out.println("Classe salva em: " + targetClassFilePath);
            }
        }
    }

    /**
     * Remove caracteres inválidos do nome da classe para criar um caminho de arquivo válido.
     *
     * @param className O nome da classe.
     * @return O nome da classe sem caracteres inválidos.
     */
    private String sanitizeClassName(String className) {
        // Remove caracteres inválidos, como < e >
        return className.replaceAll("[<>]", "");
    }

    /**
     * Encontra os campos necessários para os métodos dependentes em uma classe externa.
     *
     * @param externalClass      A classe externa a ser analisada.
     * @param dependentMethods   Os métodos dependentes na classe externa.
     * @return Um conjunto de campos necessários.
     */
    private Set<FieldDeclaration> findRequiredFieldsForExternalClass(ClassOrInterfaceDeclaration externalClass,
                                                                     Set<MethodDeclaration> dependentMethods) {
        Set<FieldDeclaration> requiredFields = new HashSet<>();

        for (MethodDeclaration method : dependentMethods) {
            method.findAll(NameExpr.class).forEach(nameExpr -> {
                String fieldName = nameExpr.getNameAsString();
                externalClass.getFields().stream()
                        .filter(field -> field.getVariables().stream()
                                .anyMatch(variable -> variable.getNameAsString().equals(fieldName)))
                        .forEach(requiredFields::add);
            });
        }

        return requiredFields;
    }
}