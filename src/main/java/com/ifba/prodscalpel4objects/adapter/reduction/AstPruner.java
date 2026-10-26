package com.ifba.prodscalpel4objects.adapter.reduction;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.*;

/**
 * Responsável por indexar e podar de forma segura nós da Árvore Sintática Abstrata (AST)
 * via JavaParser.
 *
 * <p>Garante que a estrutura sintática da AST seja preservada durante as mutações
 * do algoritmo de Melhoria Genética (GI), manipulando remoção de {@link Statement} e
 * {@link FieldDeclaration} (inclusive atributos Spring `@Autowired` e `@Value`).</p>
 *
 * @author Giovane Neves
 */
public class AstPruner {

    /**
     * Indexa todas as instruções e declarações de campos podáveis em uma CompilationUnit.
     *
     * @param cu Unidade de compilação da classe analisada.
     * @return Lista indexada de {@link PrunableStatement}.
     */
    public List<PrunableStatement> indexPrunableNodes(CompilationUnit cu) {
        List<PrunableStatement> indexedNodes = new ArrayList<>();
        int currentId = 0;

        for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = classDecl.getNameAsString();

            // 1. Indexa campos de classe (FieldDeclarations)
            for (FieldDeclaration field : classDecl.getFields()) {
                indexedNodes.add(PrunableStatement.fromField(currentId++, className, field));
            }

            // 2. Indexa métodos e suas instruções internas
            for (MethodDeclaration method : classDecl.getMethods()) {
                String methodName = method.getNameAsString();
                if (method.getBody().isPresent()) {
                    BlockStmt body = method.getBody().get();
                    for (Statement stmt : body.getStatements()) {
                        indexedNodes.add(PrunableStatement.fromStatement(currentId++, className, methodName, stmt));
                    }
                }
            }
        }

        return indexedNodes;
    }

    /**
     * Aplica o conjunto de deleções especificadas por um indivíduo sobre uma cópia da AST.
     *
     * @param originalCu Unidade de compilação original.
     * @param indexedNodes Lista dos nós indexados previamente.
     * @param deletedIds Conjunto de IDs das instruções marcadas para remoção.
     * @return Nova instância de {@link CompilationUnit} podada.
     */
    public CompilationUnit applyPruning(
            CompilationUnit originalCu,
            List<PrunableStatement> indexedNodes,
            Set<Integer> deletedIds
    ) {
        CompilationUnit mutatedCu = originalCu.clone();
        if (deletedIds == null || deletedIds.isEmpty()) {
            return mutatedCu;
        }

        // Reindexa os nós na árvore clonada para obter as referências ativas dos nós
        List<Node> activeNodesInClone = collectNodesInOrder(mutatedCu);

        for (Integer id : deletedIds) {
            if (id >= 0 && id < activeNodesInClone.size()) {
                Node nodeToRemove = activeNodesInClone.get(id);

                // Proteção para não remover retornos obrigatórios de métodos não-void
                if (nodeToRemove instanceof ReturnStmt returnStmt) {
                    Optional<MethodDeclaration> parentMethod = returnStmt.findAncestor(MethodDeclaration.class);
                    if (parentMethod.isPresent() && !parentMethod.get().getType().isVoidType()) {
                        // Não remove o return obrigatório para evitar erro de sintaxe "missing return statement"
                        continue;
                    }
                }

                // Remoção segura da AST
                if (nodeToRemove.getParentNode().isPresent()) {
                    nodeToRemove.remove();
                }
            }
        }

        return mutatedCu;
    }

    /**
     * Coleta os nós da AST na mesma ordem determinística em que foram indexados.
     */
    private List<Node> collectNodesInOrder(CompilationUnit cu) {
        List<Node> nodes = new ArrayList<>();
        for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            // Campos
            nodes.addAll(classDecl.getFields());

            // Instruções dentro de métodos
            for (MethodDeclaration method : classDecl.getMethods()) {
                if (method.getBody().isPresent()) {
                    nodes.addAll(method.getBody().get().getStatements());
                }
            }
        }
        return nodes;
    }
}
