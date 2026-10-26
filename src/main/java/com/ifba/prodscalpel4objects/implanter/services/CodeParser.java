package com.ifba.prodscalpel4objects.implanter.services;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Utiliza a biblioteca JavaParser para analisar arquivos .java e extrair
 * informações estruturadas, como métodos, atributos (fields) e imports.
 */
public class CodeParser {

    private final JavaParser javaParser = new JavaParser();

    /**
     * Analisa (faz o parse) de um arquivo .java e retorna um mapa de seus métodos.
     * @param file O arquivo .java a ser analisado.
     * @return Um mapa onde a chave é a assinatura do método (String) e o valor é o corpo completo do método (String).
     * @throws IOException Se ocorrer um erro de leitura ou parsing do arquivo.
     */
    public Map<String, String> extractMethodsMap(File file) throws IOException {
        Map<String, String> methodsMap = new LinkedHashMap<>();
        CompilationUnit cu = javaParser.parse(file).getResult()
                .orElseThrow(() -> new IOException("Não foi possível parsear " + file.getName()));

        cu.findAll(MethodDeclaration.class).forEach(m -> {
            String sig = m.getDeclarationAsString(false, false, false);
            methodsMap.put(sig, m.toString());
        });

        return methodsMap;
    }

    /**
     * Analisa um arquivo .java e retorna um mapa de seus atributos (fields).
     * @param file O arquivo .java a ser analisado.
     * @return Um mapa onde a chave é o nome da variável (String) e o valor é o objeto `FieldDeclaration` completo.
     * @throws IOException Se ocorrer um erro de leitura ou parsing do arquivo.
     */
    public Map<String, FieldDeclaration> extractFieldsMap(File file) throws IOException {
        Map<String, FieldDeclaration> fieldsMap = new LinkedHashMap<>();
        CompilationUnit cu = javaParser.parse(file).getResult()
                .orElseThrow(() -> new IOException("Não foi possível parsear " + file.getName()));

        cu.findAll(FieldDeclaration.class).forEach(fd -> {
            for (VariableDeclarator var : fd.getVariables()) {
                String name = var.getNameAsString();
                fieldsMap.put(name, fd);
            }
        });

        return fieldsMap;
    }

    /**
     * Encontra e retorna um objeto `MethodDeclaration` específico de um arquivo, com base em sua assinatura.
     * @param donorFile O arquivo .java onde o método será procurado.
     * @param signature A assinatura exata do método a ser encontrado.
     * @return O objeto `MethodDeclaration` correspondente.
     * @throws IOException Se ocorrer um erro de leitura ou parsing do arquivo.
     * @throws RuntimeException Se o método com a assinatura especificada não for encontrado.
     */
    public MethodDeclaration findMethodBySignature(File donorFile, String signature) throws IOException {
        CompilationUnit cu = javaParser.parse(donorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear " + donorFile.getName()));

        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getDeclarationAsString(false, false, false).equals(signature))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Método '" + signature + "' não encontrado em " + donorFile.getName()));
    }

    /**
     * Encontra e retorna um objeto `FieldDeclaration` de um arquivo que contém uma variável com um nome específico.
     * @param donorFile O arquivo .java onde o atributo será procurado.
     * @param name O nome da variável do atributo a ser encontrado.
     * @return O objeto `FieldDeclaration` que contém a variável.
     * @throws IOException Se ocorrer um erro de leitura ou parsing do arquivo.
     * @throws RuntimeException Se o atributo com o nome especificado não for encontrado.
     */
    public FieldDeclaration findFieldByName(File donorFile, String name) throws IOException {
        CompilationUnit cu = javaParser.parse(donorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear " + donorFile.getName()));

        for (FieldDeclaration fd : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator var : fd.getVariables()) {
                if (var.getNameAsString().equals(name)) {
                    return fd;
                }
            }
        }
        throw new RuntimeException("Atributo '" + name + "' não encontrado em " + donorFile.getName());
    }

    /**
     * Coleta todas as declarações de import de uma lista de arquivos.
     * @param pathOfFileNames Uma lista de caminhos (String) para os arquivos .java.
     * @return Um conjunto (`Set`) com todas as strings de import únicas encontradas.
     */
    public Set<String> collectImportsFromModifiedFiles(List<String> pathOfFileNames) {
        Set<String> allImports = new HashSet<>();
        for (String filePath : new HashSet<>(pathOfFileNames)) {
            try {
                CompilationUnit cu = javaParser.parse(new File(filePath)).getResult()
                        .orElseThrow(() -> new IOException("Não foi possível parsear " + filePath));
                cu.getImports().forEach(i -> allImports.add(i.getNameAsString()));
            } catch (IOException e) {
                System.err.println("Não foi possível coletar imports do arquivo: " + filePath + " - " + e.getMessage());
            }
        }
        return allImports;
    }
}