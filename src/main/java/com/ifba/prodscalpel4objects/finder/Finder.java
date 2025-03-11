package com.ifba.prodscalpel4objects.finder;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Finder {

    public FindReturn execute(String sourceDirectoryPath, String methodName) {

        // Diretório contendo os arquivos .java a serem analisados
        //String sourceDirectoryPath = "C:\\Users\\kaioe\\OneDrive\\Área de Trabalho\\soma_multiplicacao\\src\\main\\java\\com\\ifba";
        // Nome do método a ser localizado
        //String methodName = "soma";

        try {
            // Encontra todos os arquivos .java no diretório
            List<File> javaFiles = findJavaFiles(new File(sourceDirectoryPath));

            // Identifica a classe que contém o método
            File classWithMethod = findClassWithMethod(javaFiles, methodName);

            if (classWithMethod != null) {
                //System.out.println("Classe origem encontrada: " + classWithMethod.getName());
                //System.out.println("Caminho da classe origem: " + classWithMethod.getAbsolutePath());
                String classOriginPath = classWithMethod.getAbsolutePath();
                List<String> classCallPaths = new ArrayList<>();
                String className = classWithMethod.getName();

                // Procura chamadas ao método em outras classes
                for (File file : javaFiles) {
                    if (!file.equals(classWithMethod)) {
                        classCallPaths.add(findMethodCalls(file, methodName));
                    }
                }
                return new FindReturn(classOriginPath, className, classCallPaths);
            } else {
                System.out.println("Nenhuma classe contendo o método '" + methodName + "' foi encontrada.");
            }

        } catch (Exception e) {
            System.out.println("Erro ao processar os arquivos: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // Procura por todos os arquivos .java no diretório e subdiretórios
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }

    // Encontra a classe que contém o método especificado
    private static File findClassWithMethod(List<File> files, String methodName) {
        for (File file : files) {
            try {
                CompilationUnit compilationUnit = StaticJavaParser.parse(file);
                Optional<ClassOrInterfaceDeclaration> classDeclaration = compilationUnit
                        .findFirst(ClassOrInterfaceDeclaration.class);

                if (classDeclaration.isPresent()) {
                    Optional<MethodDeclaration> methodDeclaration = classDeclaration.get()
                            .getMethodsByName(methodName).stream().findFirst();

                    if (methodDeclaration.isPresent()) {
                        return file;
                    }
                }
            } catch (FileNotFoundException e) {
                System.out.println("Erro ao ler o arquivo: " + file.getAbsolutePath());
            }
        }
        return null;
    }

    // Encontra as chamadas ao método em um arquivo fornecido
    private static String findMethodCalls(File file, String methodName) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);

            Optional<MethodCallExpr> methodCall = compilationUnit.findFirst(MethodCallExpr.class,
                    call -> call.getNameAsString().equals(methodName));

            if (methodCall.isPresent()) {
                //System.out.println("Método '" + methodName + "' chamado em: " + file.getAbsolutePath());
                //System.out.println("Linha da chamada: " + methodCall.get().getBegin().get().line);
                return file.getAbsolutePath();
            }
        } catch (FileNotFoundException e) {
            System.out.println("Erro ao ler o arquivo: " + file.getAbsolutePath());
        }
        return null;
    }
}
