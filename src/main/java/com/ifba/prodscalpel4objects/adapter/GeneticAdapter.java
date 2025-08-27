package com.ifba.prodscalpel4objects.adapter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Módulo responsável por adaptar o "órgão" (código extraído) ao "hospedeiro"
 * utilizando técnicas de Algoritmo Genético.
 *
 * Esta classe implementa os operadores genéticos, como a mutação,
 * para modificar o código fonte de forma programática.
 */
public class GeneticAdapter {

    private final Random random = new Random();

    /**
     * Método principal que orquestra o processo de adaptação.
     * Por enquanto, ele irá carregar um arquivo e aplicar algumas mutações para demonstrar.
     *
     * @param organSourceFile O arquivo de código extraído que será adaptado.
     * @return A CompilationUnit (AST) com o código modificado.
     */
    public CompilationUnit adapt(File organSourceFile) throws IOException {
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(organSourceFile);

        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            throw new IOException("Falha ao fazer o parse do arquivo do órgão: " + organSourceFile.getName());
        }

        CompilationUnit ast = parseResult.getResult().get();

        // --- Demonstração dos Operadores de Mutação ---

        System.out.println("Código Original:\n" + ast.toString());

        // 1. Mutação de Deleção (Poda)
        deleteRandomStatement(ast);
        System.out.println("\nCódigo após Mutação de DELEÇÃO:\n" + ast.toString());

        // 2. Mutação de Inserção
        // Vamos criar uma linha de código (Statement) para inserir
        JavaParser parser = new JavaParser();
        ParseResult<Statement> newStatementResult = parser.parseStatement("System.out.println(\"Mutação de Inserção!\");");

        // Verificamos se o parse do novo statement foi bem-sucedido antes de usá-lo.
        if (newStatementResult.isSuccessful() && newStatementResult.getResult().isPresent()) {
            Statement newStatement = newStatementResult.getResult().get(); // Extraímos o Statement do ParseResult
            insertStatement(ast, newStatement); // Passamos o Statement desembrulhado
            System.out.println("\nCódigo após Mutação de INSERÇÃO:\n" + ast.toString());
        } else {
            System.err.println("Falha ao criar o statement para a mutação de inserção.");
        }


        return ast;
    }

    /**
     * Operador de Mutação: Deleção de Statement (Poda).
     * Encontra um método na AST e remove uma linha (Statement) aleatória de seu corpo.
     *
     * @param ast A unidade de compilação (árvore sintática) a ser modificada.
     */
    public void deleteRandomStatement(CompilationUnit ast) {
        Optional<MethodDeclaration> methodOpt = ast.findFirst(MethodDeclaration.class);

        if (methodOpt.isPresent()) {
            MethodDeclaration method = methodOpt.get();
            Optional<BlockStmt> body = method.getBody();

            if (body.isPresent()) {
                List<Statement> statements = body.get().getStatements();
                if (!statements.isEmpty()) {
                    int randomIndex = random.nextInt(statements.size());
                    Statement removedStatement = statements.remove(randomIndex);
                    System.out.println("-> [Mutação Deleção] Removeu: " + removedStatement.toString());
                }
            }
        }
    }

    /**
     * Operador de Mutação: Inserção de Statement.
     * Encontra um método na AST e insere um novo Statement em uma posição aleatória.
     *
     * @param ast A unidade de compilação a ser modificada.
     * @param statementToInsert O novo Statement a ser inserido.
     */
    // *** CORREÇÃO APLICADA AQUI ***
    // O método agora espera receber um 'Statement', não um 'ParseResult<Statement>'.
    public void insertStatement(CompilationUnit ast, Statement statementToInsert) {
        Optional<MethodDeclaration> methodOpt = ast.findFirst(MethodDeclaration.class);

        if (methodOpt.isPresent()) {
            MethodDeclaration method = methodOpt.get();
            Optional<BlockStmt> body = method.getBody();

            if (body.isPresent()) {
                List<Statement> statements = body.get().getStatements();

                int randomIndex = statements.isEmpty() ? 0 : random.nextInt(statements.size() + 1);

                statements.add(randomIndex, statementToInsert);
                System.out.println("-> [Mutação Inserção] Inseriu: " + statementToInsert.toString() + " na posição " + randomIndex);
            }
        }
    }
}