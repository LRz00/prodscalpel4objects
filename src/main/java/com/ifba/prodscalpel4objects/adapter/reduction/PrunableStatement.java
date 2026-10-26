package com.ifba.prodscalpel4objects.adapter.reduction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.Objects;

/**
 * Representa uma instrução ou nó da Árvore Sintática Abstrata (AST) elegível
 * para poda (deleção) no processo de Redução de Sobrecarga de Código.
 *
 * @param id Identificador numérico único do gene no indivíduo genético.
 * @param className Nome da classe onde a instrução se localiza.
 * @param methodName Nome do método onde a instrução reside (ou {@code "<field>"} se for atributo).
 * @param lineNumber Número da linha original no código fonte.
 * @param nodeType Tipo do nó na AST (ex: "ExpressionStmt", "FieldDeclaration", "IfStmt").
 * @param snippet Código fonte da instrução (para depuração e relatórios).
 * @param isField Indica se o nó representa a declaração de um campo de classe.
 * @param isReturn Indica se o nó é uma instrução de retorno (ReturnStmt).
 *
 * @author Giovane Neves
 */
public record PrunableStatement(
        int id,
        String className,
        String methodName,
        int lineNumber,
        String nodeType,
        String snippet,
        boolean isField,
        boolean isReturn
) {

    public PrunableStatement {
        Objects.requireNonNull(className, "className não pode ser nulo");
        Objects.requireNonNull(methodName, "methodName não pode ser nulo");
        Objects.requireNonNull(nodeType, "nodeType não pode ser nulo");
    }

    public static PrunableStatement fromStatement(int id, String className, String methodName, Statement stmt) {
        int line = stmt.getBegin().map(p -> p.line).orElse(-1);
        String type = stmt.getClass().getSimpleName();
        String snip = stmt.toString().trim();
        boolean isRet = stmt instanceof ReturnStmt;
        return new PrunableStatement(id, className, methodName, line, type, snip, false, isRet);
    }

    public static PrunableStatement fromField(int id, String className, FieldDeclaration field) {
        int line = field.getBegin().map(p -> p.line).orElse(-1);
        String type = field.getClass().getSimpleName();
        String snip = field.toString().trim();
        return new PrunableStatement(id, className, "<field>", line, type, snip, true, false);
    }
}
