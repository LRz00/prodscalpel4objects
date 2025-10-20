// OrganChromosome.java
package com.ifba.prodscalpel4objects.adapter;

import com.github.javaparser.ast.CompilationUnit;
import io.jenetics.Chromosome;
import io.jenetics.Gene;
import io.jenetics.util.ISeq;

/**
 * Representa o cromossomo de um órgão, que é a própria AST (CompilationUnit).
 * Esta é a unidade fundamental de manipulação genética.
 */
public class OrganChromosome implements Chromosome<OrganGene>, Gene<Object, OrganChromosome> {

    private final CompilationUnit organAST;
    private final CompilationUnit originalOverOrganAST; // Mantém referência ao original para mutações

    public OrganChromosome(CompilationUnit ast, CompilationUnit originalAst) {
        this.organAST = ast;
        this.originalOverOrganAST = originalAst;
    }

    public CompilationUnit getOrganAST() {
        return organAST;
    }

    public CompilationUnit getOriginalOverOrganAST() {
        return originalOverOrganAST;
    }

    @Override
    public boolean isValid() {
        return organAST!= null;
    }

    @Override
    public Chromosome<OrganGene> newInstance() {
        // Cria uma nova instância com uma cópia da AST original (super-órgão)
        return new OrganChromosome(originalOverOrganAST.clone(), originalOverOrganAST.clone());
    }

    @Override
    public Chromosome<OrganGene> newInstance(ISeq<OrganGene> genes) {
        // Este método é mais complexo para ASTs. Simplificamos para retornar uma nova instância.
        // Uma implementação avançada poderia reconstruir a AST a partir de uma sequência de "genes" (nós).
        return new OrganChromosome(genes.get(0).getAstNode().findCompilationUnit().orElseThrow(), originalOverOrganAST);
    }

    @Override
    public OrganGene get(int index) {
        // Retorna a AST inteira como o único gene para simplicidade.
        if (index == 0) {
            return new OrganGene(organAST);
        }
        throw new IndexOutOfBoundsException("OrganChromosome has only one gene at index 0.");
    }

    @Override
    public int length() {
        return 1;
    }

    @Override
    public ISeq<OrganGene> toSeq() {
        return ISeq.of(new OrganGene(organAST));
    }
}

// OrganGene.java
package com.ifba.prodscalpel4objects.adapter.chromosome;

import com.github.javaparser.ast.Node;
import io.jenetics.Gene;

/**
 * Um "gene" neste contexto é um nó da AST.
 */
public class OrganGene implements Gene<Node, OrganGene> {
    private final Node astNode;

    public OrganGene(Node astNode) {
        this.astNode = astNode;
    }

    @Override
    public Node allele() {
        return astNode;
    }

    @Override
    public OrganGene newInstance() {
        return new OrganGene(astNode.clone());
    }

    @Override
    public OrganGene newInstance(Node value) {
        return new OrganGene(value);
    }

    public Node getAstNode() {
        return astNode;
    }
}