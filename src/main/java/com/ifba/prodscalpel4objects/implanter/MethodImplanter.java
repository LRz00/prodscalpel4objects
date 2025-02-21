package com.ifba.prodscalpel4objects.implanter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Name;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Classe responsável por implementar o código no sistema receptor.
 *
 * @author Rafael Andrade
 */
public class MethodImplanter {

    private String hostRootPath;
    private String receiverRootPath;
    private List<String> pathOfFileNames = new ArrayList<>();

    public MethodImplanter(String hostRootPath, String receiverRootPath) {
        this.hostRootPath = hostRootPath;
        this.receiverRootPath = receiverRootPath;
    }

    /**
     * Método principal que inicia a cópia dos arquivos Java encontrados no projeto.
     */
    public void implant() {
        System.out.println("Começando copiar os arquivos");
        List<File> javaFiles = findJavaFilesInProject();
        String packageName;

        for(File javaFile : javaFiles){
            packageName = getPackageNameFromFile(javaFile);
            copyJavaFile(javaFile.toString(), packageName, javaFile.getName());
            System.out.println("Arquivo " + javaFile.getName() + " copiado.");
        }

        System.out.println("Todos os arquivos foram copiados.");

        for(String path : pathOfFileNames){
            modifyLinesOfTheFile(path);
        }

        System.out.println("Todos os arquivos foram modificados.");

    }

    /**
     * Copia um arquivo Java para o diretório de destino.
     *
     * @param sourceFilePath Caminho do arquivo de origem.
     * @param packageName Nome do pacote do arquivo.
     * @param fileName Nome do arquivo.
     */
    private void copyJavaFile(String sourceFilePath, String packageName, String fileName) {
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists()) {
            System.err.println("O arquivo de origem não existe.");
            return;
        }

        try {
            String destinationFilePath = "C:\\Users\\Micro\\IdeaProjects\\tcc\\receiverexample\\src\\main\\java\\org\\exemple\\receiverexample\\" + packageName;
            System.out.println("Caminho: " + destinationFilePath);

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

            try (FileInputStream fis = new FileInputStream(sourceFile);
                 FileOutputStream fos = new FileOutputStream(destinationFilePath)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }

                System.out.println("Arquivo copiado com sucesso para: " + destinationFilePath);
                pathOfFileNames.add(destinationFilePath);

            } catch (IOException e) {
                System.err.println("Erro ao copiar o arquivo: " + e.getMessage());
            }

        } catch (ParseProblemException e) {
            System.err.println("Erro ao analisar o arquivo: " + sourceFilePath + " - " + e.getMessage());
        }
    }

    /**
     * Obtém o nome do pacote de um arquivo Java com base em sua declaração de pacote ou diretório.
     *
     * @param file Arquivo Java.
     * @return Nome do pacote ou "Pasta Desconhecida" caso não seja possível determinar.
     */
    private String getPackageNameFromFile(File file) {
        if (file != null && file.exists()) {
            String projectName = findProjectName(file);
            File parent = file.getParentFile();
            List<String> packageParts = new ArrayList<>();

            while (parent != null && !parent.getName().equals(projectName) && !parent.getName().equals("IceBox")) {
                packageParts.add(0, parent.getName());
                parent = parent.getParentFile();
            }

            return packageParts.isEmpty() ? "" : String.join("\\", packageParts);
        }
        return "Pasta Desconhecida";
    }

    /**
     * Encontra todos os arquivos Java válidos no projeto.
     *
     * @return Lista de arquivos Java válidos.
     */
    public List<File> findJavaFilesInProject() {
        File directory = new File(hostRootPath);
        List<File> allJavaFiles = cleanProject(directory);
        JavaParser parser = new JavaParser();
        List<File> validJavaFiles = new ArrayList<>();

        for (File javaFile : allJavaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu != null) {
                    validJavaFiles.add(javaFile);
                }
            } catch (IOException e) {
                System.err.println("Erro ao ler o arquivo: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            } catch (ParseProblemException e) {
                System.err.println("Erro de parse no arquivo: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            }
        }

        return validJavaFiles;
    }

    /**
     * Remove o arquivo principal do projeto (com método main) da lista de arquivos encontrados.
     *
     * @param directory Diretório raiz do projeto.
     * @return Lista de arquivos Java sem o arquivo principal.
     */
    private List<File> cleanProject(File directory) {
        List<File> javaFiles = findJavaFiles(directory);
        JavaParser parser = new JavaParser();

        for (Iterator<File> iterator = javaFiles.iterator(); iterator.hasNext(); ) {
            File javaFile = iterator.next();
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);

                if (cu != null && containsMainMethod(cu)) {
                    System.out.println("Arquivo principal removido: " + javaFile.getName());
                    iterator.remove();
                }

            } catch (IOException e) {
                System.err.println("Erro ao ler o arquivo: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            } catch (ParseProblemException e) {
                System.err.println("Erro de parse no arquivo: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            }
        }

        return javaFiles;
    }

    /**
     * Verifica se a unidade de compilação contém um método 'public static void main(String[] args)'.
     *
     * @param cu Unidade de compilação do JavaParser.
     * @return true se o método main for encontrado; caso contrário, false.
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
     * Determina o nome do projeto com base na estrutura de diretórios e valida a presença de código Java válido.
     *
     * @param directory Diretório de análise.
     * @return Nome do projeto ou "Projeto Desconhecido" caso não seja identificado.
     */
    private String findProjectName(File directory) {
        JavaParser parser = new JavaParser();

        while (directory != null && directory.getParentFile() != null) {
            File srcFolder = new File(directory, "src");

            if (srcFolder.exists() && srcFolder.isDirectory()) {
                List<File> javaFiles = findJavaFiles(srcFolder);
                if (!javaFiles.isEmpty()) {
                    for (File file : javaFiles) {
                        try {
                            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
                            if (cu != null) {
                                String projectName = directory.getName()
                                        .toLowerCase()
                                        .replaceAll("[^a-z0-9]", "");
                                return projectName;
                            }
                        } catch (IOException e) {
                            System.err.println("Erro ao ler o arquivo: " + file.getAbsolutePath() + " - " + e.getMessage());
                        } catch (ParseProblemException e) {
                            System.err.println("Erro de parse no arquivo: " + file.getAbsolutePath() + " - " + e.getMessage());
                        }
                    }
                }
            }
            directory = directory.getParentFile();
        }

        System.out.println("Projeto não identificado.");
        return "Projeto Desconhecido";
    }

    /**
    * Busca recursivamente todos os arquivos Java válidos dentro de um diretório.
    *
    * @param directory Diretório raiz da busca.
    * @return Lista de arquivos Java válidos encontrados.
    */
    private List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        JavaParser parser = new JavaParser();

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        try {
                            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
                            if (cu != null) {
                                javaFiles.add(file);
                            }
                        } catch (IOException | ParseProblemException e) {
                            System.out.println("Erro ao analisar o arquivo: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
        return javaFiles;
    }

    /**
     * Adiciona ou modifica a linha package.
     *
     * @param filePath o caminho de cada arquivo a ser modificado.
     */
    public void modifyLinesOfTheFile(String filePath) {
        File file = new File(filePath);
        String packageName = getPackageNameFromJavaFolder(file);

        JavaParser javaParser = new JavaParser();
        try {
            CompilationUnit compilationUnit = javaParser.parse(file).getResult().orElseThrow(() ->
                    new IOException("Erro ao analisar o arquivo"));

            if (compilationUnit.getPackageDeclaration().isPresent()) {
                PackageDeclaration packageDeclaration = compilationUnit.getPackageDeclaration().get();
                packageDeclaration.setName(packageName);
            } else {
                compilationUnit.setPackageDeclaration(packageName);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(compilationUnit.toString().getBytes());
            }

            System.out.println("Arquivo atualizado com sucesso.");
        } catch (IOException e) {
            System.out.println("Erro ao processar arquivo: " + e.getMessage());
        }
    }

    /**
     * Pega o pacote de cada arquivo a partir da pasta java.
     *
     * @param file o arquivo que terá o pacote capturado.
     */
    private String getPackageNameFromJavaFolder(File file) {
        if (file != null && file.exists()) {
            File parent = file.getParentFile();
            StringBuilder packageParts = new StringBuilder();

            while (parent != null && !parent.getName().equals("java")) {
                if (packageParts.length() > 0) {
                    packageParts.insert(0, ".");
                }
                packageParts.insert(0, parent.getName());
                parent = parent.getParentFile();
            }

            return packageParts.toString();
        }
        return "Pasta Desconhecida";
    }
}