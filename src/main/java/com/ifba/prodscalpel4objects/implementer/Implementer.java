package com.ifba.prodscalpel4objects.implementer;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Classe responsável por implementar o código no sistema receptor.
 *
 * @author Rafael Andrade
 */
public class Implementer {

    private String hostRootPath;
    private String receiverRootPath;

    public Implementer(String hostRootPath, String receiverRootPath) {
        this.hostRootPath = hostRootPath;
        this.receiverRootPath = receiverRootPath;
    }

    /**
     * Método principal que inicia a cópia dos arquivos Java encontrados no projeto.
     */
    public void implement(){
        System.out.println("Começando copiar os arquivos");
        List<File> javaFiles = findJavaFilesInProject();
        String packageName;
        String fileNameWithPackage;

        for(File javaFile : javaFiles){
            packageName = getPackageNameFromFile(javaFile);
            copyJavaFile(javaFile.toString(), packageName, javaFile.getName());
            System.out.println("Arquivo " + javaFile.getName() + " copiado.");
        }

        System.out.println("Todos os arquivos foram copiados.");

    }

    /**
     * Copia um arquivo Java para o diretório de destino, criando o pacote caso necessário.
     *
     * @param sourceFilePath Caminho do arquivo de origem.
     * @param packageName Nome do pacote do arquivo.
     * @param fileName Nome do arquivo.
     */
    public  void copyJavaFile(String sourceFilePath, String packageName, String fileName) {
        String destinationFilePath = "C:\\Users\\Micro\\IdeaProjects\\tcc\\receiverexample\\src\\main\\java\\org\\exemple\\receiverexample\\" + packageName;
        System.out.println("Caminho: " + destinationFilePath);

        // Verifica se o pacote existe, se não, cria
        File folder = new File(destinationFilePath);
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                System.out.println("Pasta criada com sucesso: " + folder.getAbsolutePath());
            } else {
                System.err.println("Falha ao criar a pasta.");
                return;
            }
        }

        destinationFilePath = destinationFilePath.concat("\\" + fileName);

        // Realiza a cópia do arquivo
        try (FileInputStream fis = new FileInputStream(sourceFilePath);
             FileOutputStream fos = new FileOutputStream(destinationFilePath)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }

            System.out.println("Arquivo copiado com sucesso para: " + destinationFilePath);

        } catch (IOException e) {
            System.err.println("Erro ao copiar o arquivo: " + e.getMessage());
        }
    }

    /**
     * Obtém o nome do pacote de um arquivo Java com base em seu diretório.
     *
     * @param file Arquivo Java.
     * @return Nome do pacote ou "Pasta Desconhecida" caso não seja possível determinar.
     */
    private String getPackageNameFromFile(File file) {
        if (file != null && file.exists()) {
            String projectName = findProjectName(file); // Nome da pasta raiz do projeto
            File parent = file.getParentFile();
            List<String> packageParts = new ArrayList<>();

            // Percorre os diretórios superiores até encontrar a pasta do projeto
            while (parent != null && !parent.getName().equals(projectName)) {
                packageParts.add(0, parent.getName()); // Adiciona no início para manter a ordem correta
                parent = parent.getParentFile();
            }

            // Retorna o caminho do pacote no formato correto ou uma string vazia se estiver na raiz
            return packageParts.isEmpty() ? "" : String.join("\\", packageParts);
        }
        return "Pasta Desconhecida";
    }

    /**
     * Encontra todos os arquivos Java no projeto.
     *
     * @return Lista de arquivos Java.
     */
    public List<File> findJavaFilesInProject() {
        File directory = new File(hostRootPath);
        List<File> javaFiles = cleanProject(directory);

        return javaFiles;
    }

    /**
     * Remove o arquivo principal do projeto (Application.java) da lista de arquivos encontrados.
     *
     * @param directory Diretório raiz do projeto.
     * @return Lista de arquivos Java sem o arquivo principal.
     */
    private List<File> cleanProject(File directory){
        List<File> javaFiles = findJavaFiles(directory);
        String projectName = findProjectName(directory);

        projectName = projectName.concat("Application.java");
        projectName = capitalizeFirstLetter(projectName);

        for (Iterator<File> iterator = javaFiles.iterator(); iterator.hasNext();) {
            File javaFile = iterator.next();
            if (javaFile.getName().equals(projectName)) {
                iterator.remove();
            }
        }

        return  javaFiles;
    }

    /**
     * Determina o nome do projeto com base na estrutura de diretórios.
     *
     * @param directory Diretório de análise.
     * @return Nome do projeto ou "Projeto Desconhecido" caso não seja identificado.
     */
    private String findProjectName(File directory) {
        while (directory != null && directory.getParentFile() != null) {
            File srcFolder = new File(directory, "src");
            if (srcFolder.exists() && srcFolder.isDirectory()) {
                String projectName = directory.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
                System.out.println("Nome do projeto: " + projectName);
                return projectName;
            }
            directory = directory.getParentFile();
        }
        return "Projeto Desconhecido";
    }

    /**
     * Busca recursivamente todos os arquivos Java dentro de um diretório.
     *
     * @param directory Diretório raiz da busca.
     * @return Lista de arquivos Java encontrados.
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
     * Trasnforma a primeira letra de uma String em maiúscula.
     *
     * @param sentence String a ser transformada.
     * @return String com a primeira letra em maiúscula.
     */
    public String capitalizeFirstLetter(String sentence) {
        if (sentence == null || sentence.isEmpty()) {
            return sentence;
        }
        return sentence.substring(0, 1).toUpperCase() + sentence.substring(1);
    }
}