package com.ifba.prodscalpel4objects.implanter.services;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class SpringContextModifier {

    private final JavaParser javaParser = new JavaParser();

    private static final Map<String, String> STEREOTYPE_IMPORTS = new LinkedHashMap<>();
    private static final Map<String, String> INJECTION_IMPORTS = new LinkedHashMap<>();

    static {
        STEREOTYPE_IMPORTS.put("Service",          "org.springframework.stereotype.Service");
        STEREOTYPE_IMPORTS.put("Component",         "org.springframework.stereotype.Component");
        STEREOTYPE_IMPORTS.put("Repository",        "org.springframework.stereotype.Repository");
        STEREOTYPE_IMPORTS.put("RestController",    "org.springframework.web.bind.annotation.RestController");
        STEREOTYPE_IMPORTS.put("Controller",        "org.springframework.stereotype.Controller");
        STEREOTYPE_IMPORTS.put("Configuration",     "org.springframework.context.annotation.Configuration");

        INJECTION_IMPORTS.put("Autowired",  "org.springframework.beans.factory.annotation.Autowired");
        INJECTION_IMPORTS.put("Value",      "org.springframework.beans.factory.annotation.Value");
        INJECTION_IMPORTS.put("Qualifier",  "org.springframework.beans.factory.annotation.Qualifier");
        INJECTION_IMPORTS.put("Lazy",       "org.springframework.context.annotation.Lazy");
    }

    private static final List<String> SENSITIVE_PROPERTY_KEYWORDS = Arrays.asList(
            "cors", "origin", "port", "url", "password", "secret",
            "token", "key", "client-id", "client-secret", "uri", "host"
    );

    public void propagateClassStereotypes(File donorFile, File receptorFile) throws IOException {
        CompilationUnit donorCu = javaParser.parse(donorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear doador: " + donorFile.getName()));
        CompilationUnit receptorCu = javaParser.parse(receptorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear receptor: " + receptorFile.getName()));

        ClassOrInterfaceDeclaration donorClass = donorCu.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalStateException("Nenhuma classe encontrada no doador: " + donorFile.getName()));
        ClassOrInterfaceDeclaration receptorClass = receptorCu.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalStateException("Nenhuma classe encontrada no receptor: " + receptorFile.getName()));

        Set<String> receptorAnnotationNames = receptorClass.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .collect(Collectors.toSet());

        boolean modified = false;
        for (AnnotationExpr donorAnnotation : donorClass.getAnnotations()) {
            String annotationName = donorAnnotation.getNameAsString();
            if (STEREOTYPE_IMPORTS.containsKey(annotationName) && !receptorAnnotationNames.contains(annotationName)) {
                receptorClass.addAnnotation(donorAnnotation.clone());
                addImportIfAbsent(receptorCu, STEREOTYPE_IMPORTS.get(annotationName));
                System.out.println("Anotação @" + annotationName + " propagada para " + receptorFile.getName());
                modified = true;
            }
        }

        if (modified) {
            salvarCompilationUnit(receptorCu, receptorFile);
        }
    }

    public void propagateFieldInjectionAnnotations(File donorFile, File receptorFile, String fieldName) throws IOException {
        CompilationUnit donorCu = javaParser.parse(donorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear doador: " + donorFile.getName()));
        CompilationUnit receptorCu = javaParser.parse(receptorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear receptor: " + receptorFile.getName()));

        Optional<FieldDeclaration> donorFieldOpt = donorCu.findAll(FieldDeclaration.class).stream()
                .filter(fd -> fd.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(fieldName)))
                .findFirst();

        if (donorFieldOpt.isEmpty()) {
            return;
        }

        FieldDeclaration donorField = donorFieldOpt.get();
        NodeList<AnnotationExpr> donorAnnotations = donorField.getAnnotations();

        List<AnnotationExpr> injectionsToPropagate = donorAnnotations.stream()
                .filter(a -> INJECTION_IMPORTS.containsKey(a.getNameAsString()))
                .collect(Collectors.toList());

        if (injectionsToPropagate.isEmpty()) {
            return;
        }

        Optional<FieldDeclaration> receptorFieldOpt = receptorCu.findAll(FieldDeclaration.class).stream()
                .filter(fd -> fd.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(fieldName)))
                .findFirst();

        if (receptorFieldOpt.isEmpty()) {
            return;
        }

        FieldDeclaration receptorField = receptorFieldOpt.get();
        Set<String> receptorFieldAnnotationNames = receptorField.getAnnotations().stream()
                .map(AnnotationExpr::getNameAsString)
                .collect(Collectors.toSet());

        boolean modified = false;
        for (AnnotationExpr annotation : injectionsToPropagate) {
            if (!receptorFieldAnnotationNames.contains(annotation.getNameAsString())) {
                receptorField.addAnnotation(annotation.clone());
                addImportIfAbsent(receptorCu, INJECTION_IMPORTS.get(annotation.getNameAsString()));
                System.out.println("Anotação @" + annotation.getNameAsString() + " injetada no atributo '" + fieldName + "'.");
                modified = true;
            }
        }

        if (modified) {
            salvarCompilationUnit(receptorCu, receptorFile);
        }
    }

    public void mergeProperties(File donorProperties, File receptorProperties, Set<String> usedProperties) throws IOException {
        String donorName = donorProperties.getName();

        if (donorName.endsWith(".yml") || donorName.endsWith(".yaml")) {
            mergeYamlProperties(donorProperties, receptorProperties, usedProperties);
            return;
        }

        if (!donorName.endsWith(".properties")) {
            throw new IllegalArgumentException("Formato de arquivo não suportado para merge: " + donorName);
        }

        Properties donorProps = carregarProperties(donorProperties);
        Properties receptorProps = carregarProperties(receptorProperties);

        int addedCount = 0;
        List<String> novasLinhas = new ArrayList<>();

        for (String chave : donorProps.stringPropertyNames()) {
            if (usedProperties.contains(chave)) {
                // Se o receptor NÃO tem a chave, nós a adicionamos
                if (!receptorProps.containsKey(chave)) {

                    // NOVA LÓGICA: Aplica o placeholder se for sensível/ambiente
                    String finalValue;
                    if (isEnvironmentOrSensitiveProperty(chave)) {
                        finalValue = "coloque aqui o valor das variaveis";
                        System.out.println("Propriedade de ambiente rastreada. Valor original ocultado para: " + chave);
                    } else {
                        finalValue = donorProps.getProperty(chave);
                    }

                    receptorProps.setProperty(chave, finalValue);
                    novasLinhas.add(chave + "=" + finalValue);
                    addedCount++;
                    System.out.println("Nova propriedade adicionada ao receptor: " + chave);
                } else {
                    System.out.println("Chave já existente, preservando valor do receptor: " + chave);
                }
            }
        }

        if (addedCount > 0) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(receptorProperties, true))) {
                writer.newLine();
                writer.write("# --- Propriedades adicionadas pelo prodScalpel4Objects ---");
                writer.newLine();
                for (String linha : novasLinhas) {
                    writer.write(linha);
                    writer.newLine();
                }
            }
        }
    }

    private void mergeYamlProperties(File donorFile, File receptorFile, Set<String> usedProperties) throws IOException {
        List<String> donorLines = Files.readAllLines(donorFile.toPath());
        String receptorContent = Files.readString(receptorFile.toPath());

        List<String> linesToAdd = new ArrayList<>();
        int addedCount = 0;

        for (String line : donorLines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            String[] parts = line.split(":", 2);
            if (parts.length > 0) {
                String keyWithIndentation = parts[0]; // Mantém os espaços iniciais
                String key = keyWithIndentation.trim();

                boolean isKeyUsed = usedProperties.stream().anyMatch(prop -> prop.contains(key));

                if (isKeyUsed) {
                    // Se o receptor já não possuir a chave
                    if (!receptorContent.contains(key + ":")) {

                        // NOVA LÓGICA: Verifica se é sensível E se a linha original realmente tinha um valor
                        // (parts.length > 1 e o valor não é vazio). Isso evita quebrar nós pais no YAML.
                        if (isEnvironmentOrSensitiveProperty(key) && parts.length > 1 && !parts[1].trim().isEmpty()) {
                            linesToAdd.add(keyWithIndentation + ": \"coloque aqui o valor das variaveis\"");
                            System.out.println("Propriedade YAML de ambiente rastreada. Valor original ocultado para: " + key);
                        } else {
                            // Se for nó pai (ex: "cors:") ou variável comum, mantém a linha original
                            linesToAdd.add(line);
                        }

                        addedCount++;
                        System.out.println("Nova propriedade YAML identificada: " + key);
                    } else {
                        System.out.println("Chave YAML já existente, preservada: " + key);
                    }
                }
            }
        }

        if (addedCount > 0) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(receptorFile, true))) {
                writer.newLine();
                writer.write("# --- Propriedades YAML adicionadas pelo prodScalpel4Objects ---");
                writer.newLine();
                for (String line : linesToAdd) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            System.out.println("Merge YAML concluído. " + addedCount + " nova(s) linha(s) adicionada(s).");
        } else {
            System.out.println("Merge YAML concluído. Nenhuma propriedade nova necessária.");
        }
    }

    private void addImportIfAbsent(CompilationUnit cu, String importName) {
        if (importName == null) return;
        boolean jaExiste = cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().equals(importName));
        if (!jaExiste) {
            cu.addImport(importName);
        }
    }

    private void salvarCompilationUnit(CompilationUnit cu, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(cu.toString().getBytes());
        }
    }

    private Properties carregarProperties(File file) throws IOException {
        Properties props = new Properties();
        if (file.exists()) {
            try (InputStream is = Files.newInputStream(file.toPath())) {
                props.load(is);
            }
        }
        return props;
    }

    /**
     * Lê os arquivos Java transplantados e extrai todas as chaves de propriedades
     * utilizadas dentro da anotação @Value.
     */
    public Set<String> extractUsedProperties(List<String> pathsOfFiles) {
        Set<String> usedProperties = new HashSet<>();
        for (String filePath : pathsOfFiles) {
            try {
                File file = new File(filePath);
                if (!file.exists()) continue;

                CompilationUnit cu = javaParser.parse(file).getResult().orElse(null);
                if (cu != null) {
                    // Busca por anotações no código
                    cu.findAll(AnnotationExpr.class).forEach(annotation -> {
                        // Verifica se é a anotação @Value do Spring e possui um único membro
                        if (annotation.getNameAsString().equals("Value") && annotation.isSingleMemberAnnotationExpr()) {
                            String value = annotation.asSingleMemberAnnotationExpr().getMemberValue().toString();

                            // Limpa a string: tira aspas e a sintaxe ${...}
                            String cleanValue = value.replaceAll("^\"|\"$", "");
                            if (cleanValue.startsWith("${") && cleanValue.endsWith("}")) {
                                String propKey = cleanValue.substring(2, cleanValue.length() - 1);

                                // Remove valor default caso exista (ex: ${api.url:http://localhost})
                                if (propKey.contains(":")) {
                                    propKey = propKey.substring(0, propKey.indexOf(':'));
                                }
                                usedProperties.add(propKey);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                System.err.println("Erro ao extrair propriedades do arquivo: " + filePath + " - " + e.getMessage());
            }
        }
        return usedProperties;
    }

    /**
     * Verifica se a chave da propriedade contém termos de ambiente ou sensíveis.
     */
    private boolean isEnvironmentOrSensitiveProperty(String key) {
        String lowerKey = key.toLowerCase();
        return SENSITIVE_PROPERTY_KEYWORDS.stream().anyMatch(lowerKey::contains);
    }
}