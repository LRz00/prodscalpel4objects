package com.ifba.prodscalpel4objects.implanter.services;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Responsável por todas as interações com o sistema de arquivos, como
 * encontrar arquivos, analisar a estrutura de diretórios e extrair nomes de pacotes e projetos.
 */
public class ProjectScanner {

    /**
     * Orquestra a busca por arquivos .java em um diretório, removendo a classe principal do resultado.
     * @param sourcePath O caminho do diretório onde a busca deve começar.
     * @return Uma lista de arquivos Java (`File`) encontrados, excluindo a classe com o método `main`.
     */
    public List<File> findJavaFilesInProject(String sourcePath) {
        File directory = new File(sourcePath);
        List<File> allJavaFiles = findJavaFiles(directory);
        return cleanProject(allJavaFiles);
    }

    /**
     * Remove o arquivo principal do projeto (com método main) de uma lista de arquivos fornecida.
     * @param javaFiles A lista de arquivos Java a ser limpa.
     * @return A lista de arquivos Java modificada, sem o arquivo principal.
     */
    private List<File> cleanProject(List<File> javaFiles) {
        JavaParser parser = new JavaParser();

        for (Iterator<File> iterator = javaFiles.iterator(); iterator.hasNext(); ) {
            File javaFile = iterator.next();
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);

                if (cu != null && containsMainMethod(cu)) {
                    System.out.println("Arquivo principal removido: " + javaFile.getName());
                    iterator.remove();
                }

            } catch (IOException | ParseProblemException e) {
                System.err.println("Erro ao analisar o arquivo para limpeza: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            }
        }
        return javaFiles;
    }

    /**
     * Verifica se uma Unidade de Compilação (um arquivo .java) contém o método `public static void main(String[] args)`.
     * @param cu A Unidade de Compilação analisada pelo JavaParser.
     * @return `true` se o método `main` for encontrado, `false` caso contrário.
     */
    private boolean containsMainMethod(CompilationUnit cu) {
        return cu.findAll(MethodDeclaration.class).stream()
                .anyMatch(method -> method.isPublic() && method.isStatic()
                        && method.getType().asString().equals("void")
                        && method.getNameAsString().equals("main")
                        && method.getParameters().size() == 1
                        && method.getParameter(0).getType().asString().equals("String[]"));
    }

    /**
     * Busca recursivamente todos os arquivos com a extensão .java dentro de um diretório e seus subdiretórios.
     * @param directory O diretório inicial para a busca.
     * @return Uma lista de todos os arquivos (`File`) .java encontrados.
     */
    private List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
        return javaFiles;
    }

    /**
     * Gera um caminho de pacote relativo (com separadores de diretório) a partir da localização de um arquivo.
     * A busca sobe na árvore de diretórios até encontrar a pasta com o nome do projeto.
     * @param file O arquivo .java para o qual o caminho do pacote será determinado.
     * @return Uma string representando o caminho do pacote (ex: "br\edu\ifba\service").
     */
    public String getPackageNameFromFile(File file) {
        if (file != null && file.exists()) {
            String projectName = findProjectName(file);
            File parent = file.getParentFile();
            List<String> packageParts = new ArrayList<>();

            while (parent != null && !parent.getName().equals(projectName) && !parent.getName().equals("IceBox")) {
                packageParts.addFirst(parent.getName());
                parent = parent.getParentFile();
            }

            return String.join(File.separator, packageParts);
        }
        return "Pasta Desconhecida";
    }

    /**
     * Gera o nome do pacote Java (com pontos) a partir da localização de um arquivo.
     * A busca sobe na árvore de diretórios até encontrar a pasta "java".
     * @param file O arquivo .java para o qual o nome do pacote será determinado.
     * @return Uma string representando o nome do pacote (ex: "br.edu.ifba.service").
     */
    public String getPackageNameFromJavaFolder(File file) {
        if (file != null && file.exists()) {
            File parent = file.getParentFile();
            StringBuilder packageParts = new StringBuilder();

            while (parent != null && !parent.getName().equals("java")) {
                if (!packageParts.isEmpty()) {
                    packageParts.insert(0, ".");
                }
                packageParts.insert(0, parent.getName());
                parent = parent.getParentFile();
            }
            return packageParts.toString();
        }
        return "Pasta Desconhecida";
    }

    /**
     * Determina o nome do projeto subindo na árvore de diretórios a partir de um arquivo até encontrar a pasta "src".
     * O nome do diretório pai da pasta "src" é considerado o nome do projeto.
     * @param file Um arquivo qualquer dentro do projeto.
     * @return O nome do projeto normalizado (letras minúsculas e sem caracteres especiais).
     */
    public String findProjectName(File file) {
        File current = file;
        while (current != null && current.getParentFile() != null) {
            if (current.getName().equals("src")) {
                File projectDir = current.getParentFile();
                return projectDir.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
            }
            current = current.getParentFile();
        }
        System.out.println("Projeto não identificado.");
        return "ProjetoDesconhecido";
    }

    /**
     * Encontra o caminho absoluto para o arquivo pom.xml do projeto.
     * A busca sobe na árvore de diretórios a partir de um caminho inicial até encontrar uma pasta que contenha "src".
     * @param projectPath Um caminho qualquer dentro do projeto.
     * @return O caminho completo para o pom.xml, ou `null` se não for encontrado.
     */
    public String findPomPath(String projectPath) {
        File currentDir = new File(projectPath);
        if (currentDir.isFile()) {
            currentDir = currentDir.getParentFile();
        }
        while (currentDir != null) {
            File srcFolder = new File(currentDir, "src");
            if (srcFolder.exists() && srcFolder.isDirectory()) {
                String pomPath = Paths.get(currentDir.getAbsolutePath(), "pom.xml").toString();
                System.out.println("Caminho do pom: " + pomPath);
                return pomPath;
            }
            currentDir = currentDir.getParentFile();
        }
        return null;
    }
}