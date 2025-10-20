// OrganGenotype.java
package com.ifba.prodscalpel4objects.adapter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import io.jenetics.Genotype;
import io.jenetics.util.Factory;
import io.jenetics.util.ISeq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Representa o genótipo de um órgão, que contém o cromossomo (a AST).
 * Também atua como uma Factory para criar a população inicial a partir dos arquivos do IceBox.
 */
public class OrganGenotype extends Genotype<OrganChromosome> {

    private OrganGenotype(ISeq<OrganChromosome> chromosomes) {
        super(chromosomes);
    }

    public static OrganGenotype of(OrganChromosome... chromosomes) {
        return new OrganGenotype(ISeq.of(chromosomes));
    }

    /**
     * Cria uma Factory que lê os arquivos.java do diretório IceBox para formar a população inicial.
     */
    public static Factory<Genotype<OrganChromosome>> of(Path iceBoxPath) throws IOException {
        JavaParser javaParser = new JavaParser();
        List<CompilationUnit> initialAsts;

        try (Stream<Path> paths = Files.walk(iceBoxPath)) {
            initialAsts = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return javaParser.parse(p);
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(pr -> pr!= null && pr.getResult().isPresent())
                    .map(ParseResult::getResult)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toList());
        }

        if (initialAsts.isEmpty()) {
            throw new IOException("Nenhum arquivo Java válido encontrado no diretório IceBox: " + iceBoxPath);
        }

        // Para simplificar, este esqueleto foca em evoluir um único arquivo/classe.
        // Uma implementação completa poderia ter um cromossomo para cada arquivo.
        CompilationUnit overOrgan = initialAsts.get(0);

        return () -> {
            // Cada novo genótipo é uma cópia do super-órgão original.
            OrganChromosome chromosome = new OrganChromosome(overOrgan.clone(), overOrgan.clone());
            return new OrganGenotype(ISeq.of(chromosome));
        };
    }
}