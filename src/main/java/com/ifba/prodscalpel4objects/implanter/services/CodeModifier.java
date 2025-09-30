package com.ifba.prodscalpel4objects.implanter.services;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;

/**
 * Responsável por realizar modificações diretas nos arquivos de código-fonte,
 * como adicionar/substituir membros e corrigir pacotes e imports.
 */
public class CodeModifier {

    private final JavaParser javaParser = new JavaParser();
    private final ProjectScanner scanner = new ProjectScanner();

    /**
     * Adiciona um novo atributo (field) a uma classe em um arquivo.
     * @param donorFd O `FieldDeclaration` do doador a ser clonado e inserido.
     * @param receptorFile O arquivo receptor que será modificado.
     * @param name O nome específico da variável a ser mantida na declaração.
     * @throws IOException Se ocorrer um erro de leitura ou escrita.
     */
    public void addSingleField(FieldDeclaration donorFd, File receptorFile, String name) throws IOException {
        FieldDeclaration toInsert = donorFd.clone();
        toInsert.getVariables().removeIf(var -> !var.getNameAsString().equals(name));

        CompilationUnit cu = javaParser.parse(receptorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear receptor"));
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(clazz -> clazz.addMember(toInsert));

        try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
            fos.write(cu.toString().getBytes());
        }
    }

    /**
     * Substitui um atributo (field) existente em uma classe.
     * @param donorFd O `FieldDeclaration` do doador com a nova versão do atributo.
     * @param receptorFile O arquivo receptor que será modificado.
     * @param name O nome do atributo a ser substituído.
     * @throws IOException Se ocorrer um erro de leitura ou escrita.
     */
    public void replaceSingleField(FieldDeclaration donorFd, File receptorFile, String name) throws IOException {
        FieldDeclaration toInsert = donorFd.clone();
        toInsert.getVariables().removeIf(var -> !var.getNameAsString().equals(name));

        CompilationUnit cu = javaParser.parse(receptorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear receptor"));
        cu.findAll(FieldDeclaration.class).stream()
                .filter(fd -> fd.getVariables().stream().anyMatch(var -> var.getNameAsString().equals(name)))
                .findFirst()
                .ifPresent(existingFd -> {
                    if (existingFd.getVariables().size() > 1) {
                        existingFd.getVariables().removeIf(var -> var.getNameAsString().equals(name));
                        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(clazz -> clazz.addMember(toInsert));
                    } else {
                        existingFd.replace(toInsert);
                    }
                });
        try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
            fos.write(cu.toString().getBytes());
        }
    }

    /**
     * Encontra um método no arquivo receptor pela assinatura e substitui seu corpo pelo corpo do método doador.
     * @param receptorFile O arquivo .java a ser modificado.
     * @param donorMethod O `MethodDeclaration` do doador contendo o novo corpo.
     * @throws IOException Se ocorrer um erro de leitura ou escrita.
     */
    public void replaceMethodInReceptor(File receptorFile, MethodDeclaration donorMethod) throws IOException {
        CompilationUnit cu = javaParser.parse(receptorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear " + receptorFile.getName()));
        cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getDeclarationAsString(false, false, false).equals(donorMethod.getDeclarationAsString(false, false, false)))
                .findFirst()
                .ifPresent(target -> target.setBody(donorMethod.getBody().orElse(null)));
        try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
            fos.write(cu.toString().getBytes());
        }
    }

    /**
     * Adiciona um novo método ao final de uma classe em um arquivo.
     * @param donorMethod O `MethodDeclaration` do método a ser adicionado.
     * @param receptorFile O arquivo .java que será modificado.
     * @throws IOException Se ocorrer um erro de leitura ou escrita.
     */
    public void addSingleMethod(MethodDeclaration donorMethod, File receptorFile) throws IOException {
        CompilationUnit cu = javaParser.parse(receptorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear receptor"));
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(clazz -> clazz.addMember(donorMethod));
        try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
            fos.write(cu.toString().getBytes());
        }
    }

    /**
     * Orquestra a modificação de um arquivo receptor, ajustando a declaração de pacote e os imports.
     * @param donorFile O arquivo doador correspondente, usado como referência para os imports.
     * @param receptorFile O arquivo receptor a ser modificado.
     * @throws IOException Se ocorrer um erro de leitura ou escrita.
     */
    public void modifyFile(File donorFile, File receptorFile) throws IOException {
        CompilationUnit cu = javaParser.parse(receptorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao analisar o arquivo receptor: " + receptorFile.getPath()));
        modifyPackageDeclaration(cu, receptorFile);
        modifyImports(cu, receptorFile, donorFile);
        try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
            fos.write(cu.toString().getBytes());
        }
    }

    /**
     * Altera a declaração de `package` de um arquivo para corresponder à sua estrutura de diretórios.
     * @param cu A Unidade de Compilação do arquivo.
     * @param receptorFile O arquivo (`File`) cuja localização será usada para determinar o pacote correto.
     */
    private void modifyPackageDeclaration(CompilationUnit cu, File receptorFile) {
        String receptorPackage = scanner.getPackageNameFromJavaFolder(receptorFile);
        if (cu.getPackageDeclaration().isPresent()) {
            cu.getPackageDeclaration().get().setName(receptorPackage);
        } else {
            cu.setPackageDeclaration(receptorPackage);
        }
        System.out.println("Receptor package ajustado para: " + receptorPackage);
    }

    /**
     * Corrige as declarações de `import` que apontam para pacotes do projeto doador.
     * @param cu A Unidade de Compilação do arquivo a ser modificado.
     * @param receptorFile O arquivo receptor.
     * @param donorFile O arquivo doador, usado como referência.
     */
    private void modifyImports(CompilationUnit cu, File receptorFile, File donorFile) {
        String donorPackage = scanner.getPackageNameFromJavaFolder(donorFile);
        String receptorPackage = scanner.getPackageNameFromJavaFolder(receptorFile);
        String receptorName = scanner.findProjectName(receptorFile);
        String donorName = scanner.findProjectName(donorFile);

        String donorRoot = extractRootPackage(donorPackage, donorName);
        String receptorRoot = extractRootPackage(receptorPackage, receptorName);

        System.out.println("Donor root: " + donorRoot);
        System.out.println("Receptor root: " + receptorRoot);

        cu.getImports().forEach(impt -> {
            String importName = impt.getNameAsString();
            if (donorRoot != null && importName.startsWith(donorRoot)) {
                String rest = importName.substring(donorRoot.length());
                String newImport = receptorRoot + rest;
                System.out.println("Corrigindo import: " + importName + " -> " + newImport);
                impt.setName(newImport);
            }
        });
    }

    /**
     * Extrai a "raiz" de um nome de pacote, baseando-se na inclusão do nome do projeto.
     * @param fullPackageName O nome completo do pacote.
     * @param projectName O nome do projeto.
     * @return A parte inicial do pacote que corresponde à raiz do projeto.
     */
    private String extractRootPackage(String fullPackageName, String projectName) {
        if (fullPackageName == null || projectName == null) return null;
        int pos = fullPackageName.indexOf(projectName);
        if (pos != -1) {
            return fullPackageName.substring(0, pos + projectName.length());
        }
        return null;
    }

    /**
     * Compara dois `FieldDeclaration` para verificar se há diferenças de tipo, modificadores ou valor inicial.
     * @param donorFd O FieldDeclaration do doador.
     * @param receptorFd O FieldDeclaration do receptor.
     * @return `true` se houver diferenças, `false` caso contrário.
     */
    public boolean isFieldDifferent(FieldDeclaration donorFd, FieldDeclaration receptorFd) {
        VariableDeclarator donorVar = donorFd.getVariables().stream().findFirst().orElse(null);
        VariableDeclarator receptorVar = receptorFd.getVariables().stream().findFirst().orElse(null);
        if (donorVar == null || receptorVar == null) return true;
        if (!donorVar.getType().toString().equals(receptorVar.getType().toString())) return true;

        NodeList<Modifier> modDonor = donorFd.getModifiers();
        NodeList<Modifier> modRec = receptorFd.getModifiers();
        if (!new HashSet<>(modDonor).equals(new HashSet<>(modRec))) return true;

        if (donorVar.getInitializer().isPresent() ^ receptorVar.getInitializer().isPresent()) {
            return true;
        }
        if (donorVar.getInitializer().isPresent() && receptorVar.getInitializer().isPresent()) {
            if (!donorVar.getInitializer().get().toString().equals(receptorVar.getInitializer().get().toString())) return true;
        }
        return false;
    }
}