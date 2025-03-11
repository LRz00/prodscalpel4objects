package com.ifba.prodscalpel4objects.adapter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.Modifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Classe responsável por adaptar o código
 * dos métodos que foram extraídos para o IceBox.
 * 
 * @author Giovane Neves
 */
public class MethodAdapter {

    private final Path iceBoxPath;

    /**
    * Construtor da classe MethodAdapter.
    *
    * @param iceBoxPath Caminho do diretório od IceBox onde estão armazenados os metódos extraídos.
    */
    public MethodAdapter(final String iceBoxPath) {
        this.iceBoxPath = Paths.get(iceBoxPath);
    }
}

