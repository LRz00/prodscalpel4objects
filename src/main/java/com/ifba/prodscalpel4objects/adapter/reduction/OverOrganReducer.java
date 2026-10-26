package com.ifba.prodscalpel4objects.adapter.reduction;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.ifba.prodscalpel4objects.adapter.evaluation.DynamicCompiler;
import com.ifba.prodscalpel4objects.adapter.evaluation.DynamicTestRunner;
import com.ifba.prodscalpel4objects.adapter.model.ExtractedOrganInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Orquestrador da etapa de Redução de Sobrecarga de Código (Over-organ Reduction).
 *
 * <p>Recebe os artefatos depositados no IceBox pelo módulo Extractor, organiza
 * as árvores sintáticas (ASTs) das classes e suítes de teste, executa o motor
 * de Melhoria Genética (GI) e produz a versão minimalista do órgão.</p>
 *
 * @author Giovane Neves
 */
public class OverOrganReducer {

    private final DynamicCompiler compiler;
    private final DynamicTestRunner testRunner;
    private final AstPruner astPruner;
    private final GIReductionEngine reductionEngine;

    public OverOrganReducer() {
        this.compiler = new DynamicCompiler();
        this.testRunner = new DynamicTestRunner();
        this.astPruner = new AstPruner();
        ReductionFitnessCalculator calculator = new ReductionFitnessCalculator(compiler, testRunner, astPruner);
        this.reductionEngine = new GIReductionEngine(astPruner, calculator);
    }

    public OverOrganReducer(DynamicCompiler compiler, DynamicTestRunner testRunner, AstPruner astPruner, GIReductionEngine reductionEngine) {
        this.compiler = Objects.requireNonNull(compiler);
        this.testRunner = Objects.requireNonNull(testRunner);
        this.astPruner = Objects.requireNonNull(astPruner);
        this.reductionEngine = Objects.requireNonNull(reductionEngine);
    }

    /**
     * Executa a redução sobre um órgão extraído.
     *
     * @param organInfo Informações do sobre-órgão no IceBox.
     * @return {@link GIReductionEngine.ReductionResult} com o código podado e estatísticas.
     * @throws IOException Se houver falha na leitura dos arquivos do IceBox.
     */
    public GIReductionEngine.ReductionResult reduceOrgan(ExtractedOrganInfo organInfo) throws IOException {
        Objects.requireNonNull(organInfo, "organInfo não pode ser nulo");

        Path entryFile = organInfo.getEntryClassPath();
        if (entryFile == null || !Files.exists(entryFile)) {
            // Se não encontrou pelo nome exato, busca nos arquivos extraídos
            entryFile = organInfo.extractedClassFiles().stream()
                    .filter(f -> f.getFileName().toString().contains(organInfo.entryClassName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Arquivo da classe de entrada não encontrado: " + organInfo.entryClassName()));
        }

        CompilationUnit entryCu = StaticJavaParser.parse(entryFile);
        String packageName = entryCu.getPackageDeclaration().map(pd -> pd.getNameAsString() + ".").orElse("");
        String entryClassFullName = packageName + organInfo.entryClassName();

        // Carrega classes auxiliares
        Map<String, String> auxiliarySources = new HashMap<>();
        for (Path file : organInfo.extractedClassFiles()) {
            if (!file.equals(entryFile) && Files.exists(file)) {
                CompilationUnit auxCu = StaticJavaParser.parse(file);
                String auxPkg = auxCu.getPackageDeclaration().map(pd -> pd.getNameAsString() + ".").orElse("");
                String auxClassName = auxCu.findFirst(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                        .map(c -> auxPkg + c.getNameAsString())
                        .orElse(file.getFileName().toString().replace(".java", ""));
                auxiliarySources.put(auxClassName, auxCu.toString());
            }
        }

        // Carrega classes de teste
        List<String> testClassNames = new ArrayList<>();
        for (Path testFile : organInfo.testSuiteFiles()) {
            if (Files.exists(testFile)) {
                CompilationUnit testCu = StaticJavaParser.parse(testFile);
                String testPkg = testCu.getPackageDeclaration().map(pd -> pd.getNameAsString() + ".").orElse("");
                String testClassName = testCu.findFirst(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                        .map(c -> testPkg + c.getNameAsString())
                        .orElse(testFile.getFileName().toString().replace(".java", ""));
                auxiliarySources.put(testClassName, testCu.toString());
                testClassNames.add(testClassName);
            }
        }

        return reductionEngine.runReduction(
                entryCu,
                entryClassFullName,
                auxiliarySources,
                testClassNames
        );
    }

    public GIReductionEngine getReductionEngine() {
        return reductionEngine;
    }
}
