/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ifba.prodscalpel4objects.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 *
 * @author lara
 */
public class MethodExtractorV1 {

    public MethodExtractorV1() {
    }

    public void extract() {
        try {
            JavaParser javaParser = new JavaParser();
            File source = new File("src/main/resources/SourceExemple.java");

            ParseResult<CompilationUnit> cU = javaParser.parse(source);

            MethodDeclaration method = cU.getResult().get()
                    .findFirst(MethodDeclaration.class,
                            (MethodDeclaration m) -> m.getNameAsString().equals("getSquare")).orElse(null);
            
            File targetFile = new File("IceBox.java");
            if(method != null){  
                if(!targetFile.exists()){
                    targetFile.createNewFile();
                    Files.write(Paths.get("IceBox.java"),"public class IceBox {}\n".getBytes());
                }
            }
            
            ParseResult<CompilationUnit> targetCU = javaParser.parse(targetFile);
            
            ClassOrInterfaceDeclaration targetClass = targetCU.getResult().get().findFirst(ClassOrInterfaceDeclaration.class)
                    .orElseGet(() -> targetCU.getResult().get().addClass("IceBox"));
            
            targetClass.addMember(method);
            Files.write(Paths.get("IceBox.java"), targetCU.getResult().get().toString().getBytes());

        } catch (Exception e) {
        }

    }
}
