package com.ifba.prodscalpel4objects.implanter.services;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Classe responsável por implementar o código no sistema receptor.
 *
 * @author Rafael Andrade
 */
public class MethodImplanterDeprecated {

    private final String hostRootPath;
    private final String backupRootPath = "C:\\Users\\Micro\\IdeaProjects\\backupexample\\src\\main\\java\\org\\exemple\\backupexample";
    private final Set<String> rootPathOfReceptors = new HashSet<>();
    private final List<String> pathOfFileNames = new ArrayList<>();
    private final List<String> pathOfFileNamesDonor = new ArrayList<>();
    private final String donorPomPath;
    private final Set<String> pomPathOfReceptors = new HashSet<>();

    /**
     * Construtor da classe MethodImplanter.
     *
     * @param hostRootPath Caminho raiz do host.
     */
    public MethodImplanterDeprecated(String hostRootPath) {
        this.hostRootPath = hostRootPath;
        this.donorPomPath = findPomPath(hostRootPath);
    }

    public void addReceiverPath(String path) {
        this.rootPathOfReceptors.add(path);
        addReceiverPomPath(findPomPath(path));
    }

    public void addReceiverPomPath(String path) {
        this.pomPathOfReceptors.add(path);
    }

    /**
     * Método principal que inicia a cópia dos arquivos Java encontrados no projeto.
     */
    public void implant() {

        copy();
        modifyFile(pathOfFileNames, pathOfFileNamesDonor);
        implantDependencies();

//        copyFileToSp2();
//        copyFilesToReceiver();
//

//
//        System.out.println("Todos os arquivos foram modificados.");
    }

    /**
     * Copia métodos do sistema doador para os sistemas receptores,
     * comparando arquivo a arquivo e método a método. Se o arquivo receptor
     * não existir, copia tudo; se existir, só propõe merge para métodos iguais
     * na assinatura mas com diferenças no corpo.
     */
    public void copy() {
        pathOfFileNames.clear();
        List<File> donorFiles = findJavaFilesInProject(hostRootPath);
        for (File donorFile : donorFiles) {
            pathOfFileNamesDonor.add(donorFile.getAbsolutePath());
            for (String receptorRoot : rootPathOfReceptors) {
                processFileForReceptor(donorFile, receptorRoot);
            }
        }
    }

    private void handleExistingFile(File donorFile, File receptorFile, String receptorRoot) {
        Map<String, String> donorMap;
        Map<String, String> receptorMap;

        try {
            donorMap = extractMethodsMap(donorFile);
            receptorMap = extractMethodsMap(receptorFile);
        } catch (IOException e) {
            System.err.println("Erro ao extrair métodos de '"
                    + donorFile.getName() + "' ou '"
                    + receptorFile.getName() + "': " + e.getMessage());
            return;
        }

        // --- EXTRAIR FIELDS ---
        Map<String, FieldDeclaration> donorFields;
        Map<String, FieldDeclaration> receptorFields;
        try {
            donorFields = extractFieldsMap(donorFile);
            receptorFields = extractFieldsMap(receptorFile);
        } catch (IOException e) {
            System.err.println("Erro ao extrair atributos de '"
                    + donorFile.getName() + "' ou '"
                    + receptorFile.getName() + "': " + e.getMessage());
            // prossegue só com métodos
            donorFields = Collections.emptyMap();
            receptorFields = Collections.emptyMap();
        }

        // 0) Processa atributos novos ou conflitos
        processNewFields(donorFields, receptorFields, donorFile, receptorFile, receptorRoot);

        // 1) Trata métodos novos no receptor
        processNewMethods(donorMap.keySet(), receptorMap.keySet(), donorFile, receptorFile, receptorRoot);

        // 2) Continua com o fluxo de merge de métodos existentes
        for (String sig : donorMap.keySet()) {
            if (!receptorMap.containsKey(sig)) continue;
            processPotentialMerge(
                    sig,
                    donorMap.get(sig),
                    receptorMap.get(sig),
                    donorFile,
                    receptorFile,
                    receptorRoot
            );
        }
    }

    /**
     * Extrai todos os campos (FieldDeclaration) de um arquivo Java,
     * retornando um mapa nomeVariavel → FieldDeclaration original.
     * Se uma FieldDeclaration declara múltiplas variáveis, cada variável é mapeada separadamente.
     *
     * @param file Arquivo Java a ser analisado.
     * @return Map onde a chave é o nome da variável e o valor é o FieldDeclaration completo.
     * @throws IOException se ocorrer erro de leitura/parse.
     */
    private Map<String, FieldDeclaration> extractFieldsMap(File file) throws IOException {
        Map<String, FieldDeclaration> fieldsMap = new LinkedHashMap<>();
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult()
                .orElseThrow(() -> new IOException("Não foi possível parsear " + file.getName()));

        cu.findAll(FieldDeclaration.class).forEach(fd -> {
            for (VariableDeclarator var : fd.getVariables()) {
                String name = var.getNameAsString();
                // Em caso de múltiplas declarações no mesmo FieldDeclaration, mapeamos cada uma
                fieldsMap.put(name, fd);
            }
        });

        return fieldsMap;
    }

    /**
     * Identifica campos novos no doador e conflitos de campos (mesmo nome, mas tipo ou modificadores diferentes)
     * e pergunta ao usuário se deseja adicioná-los/substituí-los.
     *
     * @param donorFields    mapa nomeVariavel → FieldDeclaration no doador
     * @param receptorFields mapa nomeVariavel → FieldDeclaration no receptor
     * @param donorFile      arquivo doador
     * @param receptorFile   arquivo receptor
     * @param receptorRoot   raiz do sistema receptor (para exibir no output)
     */
    private void processNewFields(Map<String, FieldDeclaration> donorFields,
                                  Map<String, FieldDeclaration> receptorFields,
                                  File donorFile,
                                  File receptorFile,
                                  String receptorRoot) {
        Scanner sc = new Scanner(System.in);

        for (Map.Entry<String, FieldDeclaration> entry : donorFields.entrySet()) {
            String name = entry.getKey();
            FieldDeclaration donorFd = entry.getValue();

            if (!receptorFields.containsKey(name)) {
                // campo novo
                System.out.println("Atributo novo encontrado: '" + name + "'");
                System.out.print("Deseja adicionar este atributo ao receptor? (s/n): ");
                if (sc.nextLine().equalsIgnoreCase("s")) {
                    addSingleField(donorFile, receptorFile, name);
                    pathOfFileNames.add(receptorFile.getAbsolutePath());
                    System.out.println("Atributo '" + name + "' adicionado a " + receptorRoot);
                } else {
                    System.out.println("Adição cancelada para atributo '" + name + "'.");
                }
            } else {
                // já existe no receptor: checar tipo/modificadores/initializer diferentes?
                FieldDeclaration receptorFd = receptorFields.get(name);
                boolean diff = isFieldDifferent(donorFd, receptorFd);
                if (diff) {
                    System.out.println("Conflito de atributo para '" + name + "' em " + receptorRoot);
                    System.out.println("Doador:   " + donorFd.toString().trim());
                    System.out.println("Receptor: " + receptorFd.toString().trim());
                    System.out.print("Deseja substituir o atributo existente pelo do doador? (s/n): ");
                    if (sc.nextLine().equalsIgnoreCase("s")) {
                        replaceSingleField(donorFile, receptorFile, name);
                        pathOfFileNames.add(receptorFile.getAbsolutePath());
                        System.out.println("Atributo '" + name + "' substituído em " + receptorRoot);
                    } else {
                        System.out.println("Substituição cancelada para atributo '" + name + "'.");
                    }
                }
            }
        }
    }

    /**
     * Verifica se dois FieldDeclaration (para a mesma variável) diferem em tipo, modificadores ou inicializador.
     * Retorna true se houver diferença relevante.
     */
    private boolean isFieldDifferent(FieldDeclaration donorFd, FieldDeclaration receptorFd) {
        // Se o tipo ou modificadores ou inicializador diferem, consideramos diferente.
        // Para encontrar a VariableDeclarator correspondente (mesmo nome), buscamos em cada FD.
        VariableDeclarator donorVar = donorFd.getVariables().stream().findFirst().orElse(null);
        VariableDeclarator receptorVar = receptorFd.getVariables().stream().findFirst().orElse(null);
        if (donorVar == null || receptorVar == null) return true;

        // Tipo diferente?
        String donorType = donorVar.getType().toString();
        String receptorType = receptorVar.getType().toString();
        if (!donorType.equals(receptorType)) return true;

        // Modificadores diferentes?
        NodeList<Modifier> modDonor = donorFd.getModifiers();
        NodeList<Modifier> modRec = receptorFd.getModifiers();
        if (!new HashSet<>(modDonor).equals(new HashSet<>(modRec))) return true;

        // Inicializador: se um tem e outro não, ou ambos têm mas texto diferente
        if (donorVar.getInitializer().isPresent() ^ receptorVar.getInitializer().isPresent()) {
            return true;
        }
        if (donorVar.getInitializer().isPresent() && receptorVar.getInitializer().isPresent()) {
            String initDonor = donorVar.getInitializer().get().toString();
            String initRec = receptorVar.getInitializer().get().toString();
            if (!initDonor.equals(initRec)) return true;
        }

        return false; // se chegou aqui, consideramos iguais
    }

    /**
     * Encontra em um arquivo Java do doador o FieldDeclaration contendo a variável com o nome dado.
     *
     * @param donorFile arquivo do doador
     * @param name      nome da variável
     * @return FieldDeclaration original (pode declarar múltiplas variáveis)
     * @throws IOException em caso de falha no parse
     */
    private FieldDeclaration findFieldByName(File donorFile, String name) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(donorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear " + donorFile.getName()));

        for (FieldDeclaration fd : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator var : fd.getVariables()) {
                if (var.getNameAsString().equals(name)) {
                    return fd;
                }
            }
        }
        throw new RuntimeException("Atributo '" + name + "' não encontrado em " + donorFile.getName());
    }

    /**
     * Adiciona ao receptor o atributo (FieldDeclaration) do doador, preservando apenas a variável com o nome dado.
     *
     * @param donorFile    arquivo do doador
     * @param receptorFile arquivo do receptor
     * @param name         nome da variável a adicionar
     */
    private void addSingleField(File donorFile, File receptorFile, String name) {
        try {
            FieldDeclaration donorFd = findFieldByName(donorFile, name);
            // Clonar e remover variáveis extras, mantendo só a que desejamos
            FieldDeclaration toInsert = donorFd.clone();
            toInsert.getVariables().removeIf(var -> !var.getNameAsString().equals(name));

            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(receptorFile).getResult()
                    .orElseThrow(() -> new IOException("Erro ao parsear receptor"));

            cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(clazz -> {
                // Insere o campo. Pode-se ajustar posição conforme preferência,
                // aqui adiciona no final dos membros de classe.
                clazz.addMember(toInsert);
            });

            // Grava o receptor atualizado
            try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
                fos.write(cu.toString().getBytes());
            }
        } catch (IOException | ParseProblemException e) {
            System.err.println("Falha ao adicionar atributo '" + name + "': " + e.getMessage());
        }
    }

    /**
     * Substitui no receptor o atributo existente pelo doador: remove o FieldDeclaration atual e insere o novo.
     *
     * @param donorFile    arquivo do doador
     * @param receptorFile arquivo do receptor
     * @param name         nome da variável a substituir
     */
    private void replaceSingleField(File donorFile, File receptorFile, String name) {
        try {
            FieldDeclaration donorFd = findFieldByName(donorFile, name);
            FieldDeclaration toInsert = donorFd.clone();
            toInsert.getVariables().removeIf(var -> !var.getNameAsString().equals(name));

            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(receptorFile).getResult()
                    .orElseThrow(() -> new IOException("Erro ao parsear receptor"));

            cu.findAll(FieldDeclaration.class).stream()
                    .filter(fd -> fd.getVariables().stream()
                            .anyMatch(var -> var.getNameAsString().equals(name)))
                    .findFirst()
                    .ifPresent(existingFd -> {
                        // Se a declaração tiver múltiplas variáveis, removemos apenas a variável em questão
                        if (existingFd.getVariables().size() > 1) {
                            existingFd.getVariables().removeIf(var -> var.getNameAsString().equals(name));
                            // Em seguida, adicionamos o novo FieldDeclaration separado
                            cu.findFirst(ClassOrInterfaceDeclaration.class)
                                    .ifPresent(clazz -> clazz.addMember(toInsert));
                        } else {
                            // Se for única, substitui todo o FieldDeclaration
                            existingFd.replace(toInsert);
                        }
                    });

            // Grava o receptor atualizado
            try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
                fos.write(cu.toString().getBytes());
            }
        } catch (IOException | ParseProblemException e) {
            System.err.println("Falha ao substituir atributo '" + name + "': " + e.getMessage());
        }
    }

    /**
     * Para um arquivo do doador e um receptor específico,
     * decide se deve copiar o arquivo todo ou apenas processar merge de métodos.
     *
     * @param donorFile    Arquivo Java do doador.
     * @param receptorRoot Caminho raiz do sistema receptor.
     */
    private void processFileForReceptor(File donorFile, String receptorRoot) {
        String pkg = getPackageNameFromFile(donorFile);
        File receptorFile = buildReceptorFile(donorFile, receptorRoot, pkg);

        if (!receptorFile.exists()) {
            copyJavaFile(donorFile.toString(), pkg, donorFile.getName(), receptorRoot);
        } else {
            handleExistingFile(donorFile, receptorFile, receptorRoot);
        }
    }

    /**
     * Constrói o objeto File que representa o local de destino
     * dentro do receptor, criando diretórios se necessário.
     *
     * @param donorFile    Arquivo Java do doador.
     * @param receptorRoot Caminho raiz do sistema receptor.
     * @param pkg          Nome do pacote (com barras) do arquivo.
     * @return File apontando para o caminho de destino.
     */
    private File buildReceptorFile(File donorFile, String receptorRoot, String pkg) {
        String destDirPath = receptorRoot + File.separator + pkg;
        File destDir = new File(destDirPath);
        if (!destDir.exists()) destDir.mkdirs();
        return new File(destDir, donorFile.getName());
    }

    /**
     * Verifica se um método com mesma assinatura difere no corpo entre doador e receptor;
     * se diferir, pergunta ao usuário se deve mesclar e chama mergeSingleMethod.
     *
     * @param sig          Assinatura do método.
     * @param donorBody    Corpo do método no doador.
     * @param receptorBody Corpo do método no receptor.
     * @param donorFile    Arquivo Java do doador.
     * @param receptorFile Arquivo Java do receptor.
     * @param receptorRoot Caminho raiz do sistema receptor.
     */
    private void processPotentialMerge(String sig,
                                       String donorBody,
                                       String receptorBody,
                                       File donorFile,
                                       File receptorFile,
                                       String receptorRoot) {
        if (!hasDiff(receptorBody, donorBody)) return;

        System.out.println("Método '" + sig + "' difere em " + receptorRoot);
        System.out.print("Deseja merge? (s/n): ");
        if (new Scanner(System.in).nextLine().equalsIgnoreCase("s")) {
            mergeSingleMethod(sig, donorFile, receptorFile, receptorRoot);
        } else {
            System.out.println("Merge cancelado para '" + sig + "'.");
        }
    }

    /**
     * Realiza a mesclagem de um único método:
     * extrai o MethodDeclaration do doador, substitui o corpo no receptor,
     * grava o arquivo e registra o caminho para ajuste de pacote.
     *
     * @param sig          Assinatura do método.
     * @param donorFile    Arquivo Java do doador.
     * @param receptorFile Arquivo Java do receptor.
     * @param receptorRoot Caminho raiz do sistema receptor.
     */
    private void mergeSingleMethod(String sig,
                                   File donorFile,
                                   File receptorFile,
                                   String receptorRoot) {
        try {
            MethodDeclaration donorMethod = findMethodBySignature(donorFile, sig);
            replaceMethodInReceptor(receptorFile, donorMethod);
            pathOfFileNames.add(receptorFile.getAbsolutePath());
            System.out.println("Merge concluído para '" + sig + "'.");
        } catch (Exception e) {
            System.err.println("Falha no merge '" + sig + "': " + e.getMessage());
        }
    }

    /**
     * Extrai todos os métodos de um arquivo Java, retornando um mapa
     * de assinatura → corpo do método.
     *
     * @param file Arquivo Java a ser analisado.
     * @return Map onde a chave é a assinatura do método e o valor é seu corpo em texto.
     * @throws IOException se ocorrer erro de leitura do arquivo.
     */
    private Map<String, String> extractMethodsMap(File file) throws IOException {
        Map<String, String> methodsMap = new LinkedHashMap<>();
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult()
                .orElseThrow(() -> new IOException("Não foi possível parsear " + file.getName()));

        cu.findAll(MethodDeclaration.class).forEach(m -> {
            String sig = m.getDeclarationAsString(false, false, false);
            methodsMap.put(sig, m.toString());
        });

        return methodsMap;
    }

    /**
     * Compara duas versões de corpo de método e diz se há diferenças.
     *
     * @param oldBody Versão atual no receptor (texto completo do método).
     * @param newBody Versão do doador.
     * @return true se houver alguma diferença; false se forem idênticos.
     */
    private boolean hasDiff(String oldBody, String newBody) {
        List<String> oldLines = Arrays.asList(oldBody.split("\\R"));
        List<String> newLines = Arrays.asList(newBody.split("\\R"));
        Patch<String> patch = DiffUtils.diff(oldLines, newLines);
        return !patch.getDeltas().isEmpty();
    }

    /**
     * Encontra em um arquivo Java do doador o MethodDeclaration cuja assinatura
     * bate exatamente com a fornecida.
     *
     * @param donorFile Arquivo Java do doador.
     * @param signature Assinatura exata do método (declarationAsString).
     * @return MethodDeclaration correspondente.
     * @throws IOException se falhar a leitura/parse.
     */
    private MethodDeclaration findMethodBySignature(File donorFile, String signature) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(donorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear " + donorFile.getName()));

        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getDeclarationAsString(false, false, false).equals(signature))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Método '" + signature + "' não encontrado em " + donorFile.getName()));
    }

    /**
     * Substitui no arquivo receptor o corpo do método dado pelo MethodDeclaration
     * do doador, preservando o restante do arquivo.
     *
     * @param receptorFile Arquivo Java a ser modificado.
     * @param donorMethod  MethodDeclaration extraído do doador.
     * @throws IOException           se falhar leitura/escrita do arquivo.
     * @throws ParseProblemException se o JavaParser falhar ao parsear.
     */
    private void replaceMethodInReceptor(File receptorFile, MethodDeclaration donorMethod)
            throws IOException, ParseProblemException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(receptorFile).getResult()
                .orElseThrow(() -> new IOException("Erro ao parsear " + receptorFile.getName()));

        // Localiza e substitui o método por assinatura
        cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getDeclarationAsString(false, false, false)
                        .equals(donorMethod.getDeclarationAsString(false, false, false)))
                .findFirst()
                .ifPresent(target -> target.setBody(donorMethod.getBody().orElse(null)));

        // Grava o arquivo receptor atualizado
        try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
            fos.write(cu.toString().getBytes());
        }
    }

    /**
     * Identifica assinaturas que estão no doador mas não no receptor
     * e pergunta ao usuário se deseja adicioná-las.
     */
    private void processNewMethods(Set<String> donorSigs,
                                   Set<String> receptorSigs,
                                   File donorFile,
                                   File receptorFile,
                                   String receptorRoot) {
        Scanner sc = new Scanner(System.in);
        for (String sig : donorSigs) {
            if (receptorSigs.contains(sig)) continue;  // só novos
            System.out.println("Método novo encontrado: '" + sig + "'");
            System.out.print("Deseja adicionar esse método ao receptor? (s/n): ");
            if (sc.nextLine().equalsIgnoreCase("s")) {
                addSingleMethod(donorFile, receptorFile, sig);
                pathOfFileNames.add(receptorFile.getAbsolutePath());
                System.out.println("Método '" + sig + "' adicionado a " + receptorRoot);
            } else {
                System.out.println("Adição cancelada para '" + sig + "'.");
            }
        }
    }

    /**
     * Adiciona ao final da classe receptor o método do doador com a assinatura dada.
     *
     * @param donorFile    Arquivo Java do doador.
     * @param receptorFile Arquivo Java do receptor.
     * @param signature    Assinatura do método a ser inserido.
     */
    private void addSingleMethod(File donorFile, File receptorFile, String signature) {
        try {
            JavaParser parser = new JavaParser();
            // parseia doador para obter MethodDeclaration
            MethodDeclaration donorMethod = findMethodBySignature(donorFile, signature);

            // parseia receptor e insere o método
            CompilationUnit cu = parser.parse(receptorFile).getResult()
                    .orElseThrow(() -> new IOException("Erro ao parsear receptor"));

            cu.findFirst(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                    .ifPresent(clazz -> clazz.addMember(donorMethod));

            // grava o receptor atualizado
            try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
                fos.write(cu.toString().getBytes());
            }
        } catch (IOException | ParseProblemException e) {
            System.err.println("Falha ao adicionar método '" + signature + "': " + e.getMessage());
        }
    }

    /**
     * Copia os arquivos Java encontrados no host para o projeto auxiliar (backup).
     */
    private void copyFileToSp2() {
        System.out.println("Começando copiar os arquivos para SP2");
        List<File> javaFiles = findJavaFilesInProject(hostRootPath);
        String packageName;

        for (File javaFile : javaFiles) {
            packageName = getPackageNameFromFile(javaFile);
            System.out.println("Nome do pacote: " + packageName);
            copyJavaFile(javaFile.toString(), packageName, javaFile.getName(), backupRootPath);
            System.out.println("Arquivo " + javaFile.getName() + " copiado.");
        }

        System.out.println("Todos os arquivos foram copiados.");
    }

    /**
     * Copia os arquivos Java do projeto auxiliar (backup) para cada um dos sistemas receptores.
     * Antes de copiar, verifica se há alterações no arquivo auxiliar que não estão presentes no receptor.
     * Se houver, solicita ao usuário a confirmação para realizar o merge.
     */
    private void copyFilesToReceiver() {
        System.out.println("Começando copiar os arquivos para o sistema receptor");
        List<File> javaFiles = findJavaFilesInProject(backupRootPath);
        String packageName;

        pathOfFileNames.clear();

        for (File javaFile : javaFiles) {
            packageName = getPackageNameFromFile(javaFile);
            // Itera sobre cada receptor no conjunto de receptores (rootPathOfReceptors)
            for (String receptorRootPath : rootPathOfReceptors) {
                String destDirPath = receptorRootPath + "\\" + packageName;
                File destDir = new File(destDirPath);
                if (!destDir.exists()) {
                    if (destDir.mkdirs()) {
                        System.out.println("Pasta criada: " + destDirPath);
                    } else {
                        System.err.println("Falha ao criar a pasta: " + destDirPath);
                        continue;
                    }
                }
                String destFilePath = destDirPath + "\\" + javaFile.getName();
                File receptorFile = new File(destFilePath);

                if (receptorFile.exists()) {
                    try {
                        List<String> auxLines = Files.readAllLines(javaFile.toPath());
                        List<String> recLines = Files.readAllLines(receptorFile.toPath());

                        Patch<String> patch = DiffUtils.diff(recLines, auxLines);
                        if (!patch.getDeltas().isEmpty()) {
                            System.out.println("O arquivo " + javaFile.getName() + " possui alterações entre o auxiliar e o receptor (" + receptorRootPath + ").");
                            System.out.print("Deseja realizar merge? (s/n): ");
                            Scanner scanner = new Scanner(System.in);
                            String response = scanner.nextLine();
                            if (response.equalsIgnoreCase("s")) {
                                // Usa o método copyJavaFile para realizar a cópia (merge) se confirmado
                                copyJavaFile(javaFile.toString(), packageName, javaFile.getName(), receptorRootPath);
                                System.out.println("Merge realizado para o arquivo " + javaFile.getName() + " no receptor " + receptorRootPath);
                            } else {
                                System.out.println("Merge cancelado para o arquivo " + javaFile.getName() + " no receptor " + receptorRootPath);
                            }
                        } else {
                            System.out.println("Nenhuma alteração encontrada para " + javaFile.getName() + ". Copiando arquivo para o receptor " + receptorRootPath + ".");
                            copyJavaFile(javaFile.toString(), packageName, javaFile.getName(), receptorRootPath);
                        }
                    } catch (IOException e) {
                        System.err.println("Erro ao processar o arquivo " + javaFile.getName() + " no receptor " + receptorRootPath + ": " + e.getMessage());
                    }
                } else {
                    copyJavaFile(javaFile.toString(), packageName, javaFile.getName(), receptorRootPath);
                }
            }
        }

        System.out.println("Processo de cópia para receptor finalizado.");
    }

    /**
     * Copia um arquivo Java para o diretório de destino.
     *
     * @param sourceFilePath      Caminho do arquivo de origem.
     * @param packageName         Nome do pacote do arquivo.
     * @param fileName            Nome do arquivo.
     * @param destinationFilePath Caminho do diretório de destino.
     */
    private void copyJavaFile(String sourceFilePath, String packageName, String fileName, String destinationFilePath) {
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists()) {
            System.err.println("O arquivo de origem não existe.");
            return;
        }

        try {
            destinationFilePath = destinationFilePath + "\\" + packageName;
            System.out.println("Caminho: " + destinationFilePath);

            File folder = new File(destinationFilePath);
            if (!folder.exists()) {
                if (folder.mkdirs()) {
                    System.out.println("Pasta criada com sucesso: " + folder.getAbsolutePath());
                } else {
                    System.err.println("Falha ao criar a pasta.");
                    return;
                }
            }

            destinationFilePath = destinationFilePath.concat("\\" + fileName);

            try (FileInputStream fis = new FileInputStream(sourceFile);
                 FileOutputStream fos = new FileOutputStream(destinationFilePath)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }

                System.out.println("Arquivo copiado com sucesso para: " + destinationFilePath);
                pathOfFileNames.add(destinationFilePath);

            } catch (IOException e) {
                System.err.println("Erro ao copiar o arquivo: " + e.getMessage());
            }

        } catch (ParseProblemException e) {
            System.err.println("Erro ao analisar o arquivo: " + sourceFilePath + " - " + e.getMessage());
        }
    }

    /**
     * Obtém o nome do pacote de um arquivo Java com base em sua declaração de pacote ou diretório.
     *
     * @param file Arquivo Java.
     * @return Nome do pacote ou "Pasta Desconhecida" caso não seja possível determinar.
     */
    private String getPackageNameFromFile(File file) {
        if (file != null && file.exists()) {
            String projectName = findProjectName(file);
            File parent = file.getParentFile();
            List<String> packageParts = new ArrayList<>();

            while (parent != null && !parent.getName().equals(projectName) && !parent.getName().equals("IceBox")) {
                packageParts.addFirst(parent.getName());
                parent = parent.getParentFile();
            }

            return packageParts.isEmpty() ? "" : String.join("\\", packageParts);
        }
        return "Pasta Desconhecida";
    }

    /**
     * Encontra todos os arquivos Java válidos no projeto.
     *
     * @param sourcePath Caminho de origem onde os arquivos Java serão procurados.
     * @return Lista de arquivos Java válidos.
     */
    public List<File> findJavaFilesInProject(String sourcePath) {
        File directory = new File(sourcePath);
        List<File> allJavaFiles = cleanProject(directory);
        JavaParser parser = new JavaParser();
        List<File> validJavaFiles = new ArrayList<>();

        for (File javaFile : allJavaFiles) {
            try {
                parser.parse(javaFile).getResult().ifPresent(cu -> validJavaFiles.add(javaFile));
            } catch (IOException e) {
                System.err.println("Erro ao ler o arquivo: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            } catch (ParseProblemException e) {
                System.err.println("Erro de parse no arquivo: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            }
        }

        return validJavaFiles;
    }

    /**
     * Remove o arquivo principal do projeto (com método main) da lista de arquivos encontrados.
     *
     * @param directory Diretório raiz do projeto.
     * @return Lista de arquivos Java sem o arquivo principal.
     */
    private List<File> cleanProject(File directory) {
        List<File> javaFiles = findJavaFiles(directory);
        JavaParser parser = new JavaParser();

        for (Iterator<File> iterator = javaFiles.iterator(); iterator.hasNext(); ) {
            File javaFile = iterator.next();
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);

                if (cu != null && containsMainMethod(cu)) {
                    System.out.println("Arquivo principal removido: " + javaFile.getName());
                    iterator.remove();
                }

            } catch (IOException e) {
                System.err.println("Erro ao ler o arquivo: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            } catch (ParseProblemException e) {
                System.err.println("Erro de parse no arquivo: " + javaFile.getAbsolutePath() + " - " + e.getMessage());
            }
        }

        return javaFiles;
    }

    /**
     * Verifica se a unidade de compilação contém um método 'public static void main(String[] args)'.
     *
     * @param cu Unidade de compilação do JavaParser.
     * @return true se o método main for encontrado; caso contrário, false.
     */
    private boolean containsMainMethod(CompilationUnit cu) {
        return cu.findAll(MethodDeclaration.class).stream()
                .anyMatch(method -> method.isPublic() && method.isStatic()
                        && method.getType().asString().equals("void")
                        && method.getNameAsString().equals("main")
                        && method.getParameters().size() == 1
                        && method.getParameter(0).getType().asString().equals("String[]"));
    }

    /**
     * Determina o nome do projeto com base na estrutura de diretórios e valida a presença de código Java válido.
     *
     * @param file arquivo de análise.
     * @return Nome do projeto ou "Projeto Desconhecido" caso não seja identificado.
     */
    private String findProjectName(File file) {
        File current = file;

        while (current != null && current.getParentFile() != null) {
            if (current.getName().equals("src")) {
                // Quando encontrar a pasta 'src', retorna o nome do diretório que a contém
                File projectDir = current.getParentFile();
                return projectDir.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
            }

            current = current.getParentFile();
        }

        System.out.println("Projeto não identificado.");
        return "ProjetoDesconhecido";
    }

//    /**
//     * Determina o nome do projeto com base na estrutura de diretórios e valida a presença de código Java válido.
//     *
//     * @param directory Diretório de análise.
//     * @return Nome do projeto ou "Projeto Desconhecido" caso não seja identificado.
//     */
//    private String findProjectName(File directory) {
//        JavaParser parser = new JavaParser();
//
//        while (directory != null && directory.getParentFile() != null) {
//            File srcFolder = new File(directory, "src");
//
//            if (srcFolder.exists() && srcFolder.isDirectory()) {
//                List<File> javaFiles = findJavaFiles(srcFolder);
//                if (!javaFiles.isEmpty()) {
//                    for (File file : javaFiles) {
//                        try {
//                            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
//                            if (cu != null) {
//                                return directory.getName()
//                                        .toLowerCase()
//                                        .replaceAll("[^a-z0-9]", "");
//                            }
//                        } catch (IOException e) {
//                            System.err.println("Erro ao ler o arquivo: " + file.getAbsolutePath() + " - " + e.getMessage());
//                        } catch (ParseProblemException e) {
//                            System.err.println("Erro de parse no arquivo: " + file.getAbsolutePath() + " - " + e.getMessage());
//                        }
//                    }
//                }
//            }
//            directory = directory.getParentFile();
//        }
//
//        System.out.println("Projeto não identificado.");
//        return "Projeto Desconhecido";
//    }


    /**
     * Busca recursivamente todos os arquivos Java válidos dentro de um diretório.
     *
     * @param directory Diretório raiz da busca.
     * @return Lista de arquivos Java válidos encontrados.
     */
    private List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        JavaParser parser = new JavaParser();

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        try {
                            parser.parse(file).getResult().ifPresent(cu -> javaFiles.add(file));
                        } catch (IOException | ParseProblemException e) {
                            System.out.println("Erro ao analisar o arquivo: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
        return javaFiles;
    }

    /**
     * Modifica os arquivos Java receptores ajustando a declaração de package e corrigindo os imports
     * que ainda fazem referência ao projeto doador.
     *
     * @param pathOfFileNames      Lista de caminhos absolutos dos arquivos do projeto receptor.
     * @param pathOfFileNamesDonor Lista de caminhos absolutos dos arquivos do projeto doador.
     */
    public void modifyFile(List<String> pathOfFileNames, List<String> pathOfFileNamesDonor) {
        JavaParser javaParser = new JavaParser();

        for (String donorPath : pathOfFileNamesDonor) {
            File donorFile = new File(donorPath);
            String donorFileName = donorFile.getName(); // Ex: UserService.java

            // Para cada receptor que corresponde ao nome do doador
            for (String receptorPath : pathOfFileNames) {
                File receptorFile = new File(receptorPath);

                if (!receptorFile.getName().equals(donorFileName)) {
                    continue; // Pula se não for o mesmo nome de arquivo
                }

                try {
                    CompilationUnit cu = javaParser.parse(receptorFile)
                            .getResult()
                            .orElseThrow(() -> new IOException("Erro ao analisar o arquivo receptor: " + receptorPath));

                    modifyPackageDeclaration(cu, receptorFile);
                    modifyImports(cu, receptorFile, donorFile);

                    try (FileOutputStream fos = new FileOutputStream(receptorFile)) {
                        fos.write(cu.toString().getBytes());
                    }

                    System.out.println("Arquivo atualizado com sucesso: " + receptorPath);
                } catch (IOException e) {
                    System.err.println("Erro ao processar o arquivo " + receptorPath + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Modifica ou adiciona a declaração de package em uma unidade de compilação.
     * O nome do package é inferido com base na estrutura de diretórios do projeto receptor.
     *
     * @param cu           Unidade de compilação do arquivo Java receptor.
     * @param receptorFile Arquivo Java receptor.
     */
    private void modifyPackageDeclaration(CompilationUnit cu, File receptorFile) {
        String receptorPackage = getPackageNameFromJavaFolder(receptorFile); // Determina o package com base na estrutura de pastas

        // Substitui ou adiciona a declaração de package
        if (cu.getPackageDeclaration().isPresent()) {
            cu.getPackageDeclaration().get().setName(receptorPackage);
        } else {
            cu.setPackageDeclaration(receptorPackage);
        }

        System.out.println("Receptor package ajustado para: " + receptorPackage);
    }

    /**
     * Corrige as declarações de import do arquivo receptor que ainda apontam para pacotes do doador,
     * substituindo pelo pacote do receptor, com base nos nomes de projeto e estrutura de diretórios.
     *
     * @param cu           Unidade de compilação do arquivo receptor.
     * @param receptorFile Arquivo Java receptor.
     * @param donorFile    Arquivo Java doador correspondente.
     */
    private void modifyImports(CompilationUnit cu, File receptorFile, File donorFile) {
        String donorPackage = getPackageNameFromJavaFolder(donorFile);         // Pacote doador completo
        String receptorPackage = getPackageNameFromJavaFolder(receptorFile);   // Pacote receptor completo
        String receptorName = findProjectName(receptorFile);
        String donorName = findProjectName(donorFile);

        // Extrai a raiz dos pacotes com base no nome do projeto
        String donorRoot = extractRootPackage(donorPackage, donorName);
        String receptorRoot = extractRootPackage(receptorPackage, receptorName);

        System.out.println("Donor root: " + donorRoot);
        System.out.println("Receptor root: " + receptorRoot);

        // Corrige os imports
        cu.getImports().forEach(impt -> {
            String importName = impt.getNameAsString();
            if (importName.startsWith(donorRoot)) {
                String rest = importName.substring(donorRoot.length());
                String newImport = receptorRoot + rest;
                System.out.println("Corrigindo import: " + importName + " -> " + newImport);
                impt.setName(newImport);
            }
        });
    }

    /**
     * Extrai a raiz do pacote até incluir o nome do projeto.
     */
    private String extractRootPackage(String fullPackageName, String projectName) {
        int pos = fullPackageName.indexOf(projectName);
        if (pos != -1) {
            return fullPackageName.substring(0, pos + projectName.length());
        }
        return null;
    }

    /**
     * Obtém o nome do pacote de um arquivo Java a partir da estrutura de diretórios, baseado na pasta "java".
     *
     * @param file O arquivo para o qual o pacote será determinado.
     * @return O nome do pacote ou "Pasta Desconhecida" se não for possível determinar.
     */
    private String getPackageNameFromJavaFolder(File file) {
        if (file != null && file.exists()) {
            File parent = file.getParentFile();
            StringBuilder packageParts = new StringBuilder();

            while (parent != null && !parent.getName().equals("java")) {
                if (!packageParts.isEmpty()) {
                    packageParts.insert(0, ".");
                }
                packageParts.insert(0, parent.getName());
                parent = parent.getParentFile();
            }

            return packageParts.toString();
        }
        return "Pasta Desconhecida";
    }

    /**
     * Orquestra o processo de identificação e adição de dependências nos poms dos receptores.
     */
    public void implantDependencies() {
        if (donorPomPath == null || pomPathOfReceptors.isEmpty()) {
            System.out.println("Caminhos do pom.xml doador ou receptor não configurados. Pulando implantação de dependências.");
            return;
        }

        System.out.println("Iniciando a implantação de dependências...");
        Set<String> allImports = collectImportsFromModifiedFiles();
        if (allImports.isEmpty()) {
            System.out.println("Nenhum arquivo foi modificado. Nenhuma dependência a ser processada.");
            return;
        }

        try {
            Model donorModel = readPom(new File(donorPomPath));
            List<Dependency> donorDependencies = donorModel.getDependencies();

            Set<Dependency> requiredDependencies = findRequiredDependencies(allImports, donorDependencies);

            for (String receptorPomPath : pomPathOfReceptors) {
                try {
                    File receptorPomFile = new File(receptorPomPath);
                    Model receptorModel = readPom(receptorPomFile);

                    int count = addMissingDependencies(receptorModel, requiredDependencies);

                    if (count > 0) {
                        writePom(receptorPomFile, receptorModel);
                        System.out.println(count + " nova(s) dependência(s) adicionada(s) a: " + receptorPomPath);
                    } else {
                        System.out.println("Nenhuma dependência nova necessária para: " + receptorPomPath);
                    }

                } catch (IOException | XmlPullParserException e) {
                    System.err.println("Erro ao processar o pom receptor: " + receptorPomPath + " - " + e.getMessage());
                }
            }

        } catch (IOException | XmlPullParserException e) {
            System.err.println("Erro ao ler o pom doador: " + donorPomPath + " - " + e.getMessage());
        }
    }

    /**
     * Coleta todos os imports únicos dos arquivos que foram modificados no processo.
     */
    private Set<String> collectImportsFromModifiedFiles() {
        Set<String> allImports = new HashSet<>();
        JavaParser javaParser = new JavaParser();

        for (String filePath : new HashSet<>(pathOfFileNames)) { // Usa um Set para evitar arquivos duplicados
            try {
                CompilationUnit cu = javaParser.parse(new File(filePath)).getResult()
                        .orElseThrow(() -> new IOException("Não foi possível parsear " + filePath));
                cu.getImports().forEach(i -> allImports.add(i.getNameAsString()));
            } catch (IOException e) {
                System.err.println("Não foi possível coletar imports do arquivo: " + filePath + " - " + e.getMessage());
            }
        }
        return allImports;
    }

    /**
     * Adiciona ao modelo do receptor as dependências necessárias que ainda não existem.
     * Retorna o número de dependências adicionadas.
     */
    private int addMissingDependencies(Model receptorModel, Set<Dependency> requiredDependencies) {
        Set<String> existingDependencies = new HashSet<>();
        receptorModel.getDependencies().forEach(dep ->
                existingDependencies.add(dep.getGroupId() + ":" + dep.getArtifactId())
        );

        int addedCount = 0;
        for (Dependency requiredDep : requiredDependencies) {
            String dependencyCoord = requiredDep.getGroupId() + ":" + requiredDep.getArtifactId();
            if (!existingDependencies.contains(dependencyCoord)) {
                receptorModel.addDependency(requiredDep);
                addedCount++;
            }
        }
        return addedCount;
    }

    /**
     * Lê um arquivo pom.xml e o converte para um objeto Model do Maven.
     */
    private Model readPom(File pomFile) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (FileReader fileReader = new FileReader(pomFile)) {
            return reader.read(fileReader);
        }
    }

    /**
     * Escreve o objeto Model do Maven de volta para um arquivo pom.xml.
     */
    private void writePom(File pomFile, Model model) throws IOException {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            writer.write(fileWriter, model);
        }
    }

    /**
     * Filtra as dependências do pom.xml original com base nos imports.
     * Lógica adaptada da classe PomGenerator.
     */
    private Set<Dependency> findRequiredDependencies(Set<String> imports, List<Dependency> allDependencies) {
        Set<Dependency> requiredDependencies = new HashSet<>();

        for (String importLine : imports) {
            String importGroup = extractGroupFromImport(importLine);
            String[] importWords = importGroup.split("\\.");

            for (Dependency dependency : allDependencies) {
                if (hasWordsInCommon(importWords, dependency.getGroupId(), dependency.getArtifactId())) {
                    requiredDependencies.add(dependency);
                }
            }
        }
        return requiredDependencies;
    }

    /**
     * Extrai o nome do pacote de uma linha de import.
     */
    private String extractGroupFromImport(String importLine) {
        int lastDotIndex = importLine.lastIndexOf('.');
        if (lastDotIndex != -1) {
            return importLine.substring(0, lastDotIndex);
        }
        return importLine;
    }

    /**
     * Verifica se há palavras em comum entre o import e as coordenadas da dependência.
     */
    private boolean hasWordsInCommon(String[] importWords, String groupId, String artifactId) {
        long commonInGroupId = countCommonWords(importWords, groupId.split("\\."));
        long commonInArtifactId = countCommonWords(importWords, artifactId.split("-")); // ArtifactId usa hífens

        // Critério: Pelo menos 2 palavras em comum com o groupId ou 1 com o artifactId
        return (commonInGroupId >= 2) || (commonInArtifactId >= 1);
    }

    /**
     * Conta as palavras em comum entre dois arrays de strings.
     */
    private long countCommonWords(String[] words1, String[] words2) {
        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));
        set1.retainAll(set2);
        return set1.size();
    }

    /**
     * Encontra o caminho para o pom.xml subindo na estrutura de diretórios
     * até encontrar uma pasta que contenha o diretório 'src'.
     *
     * @param projectPath O caminho absoluto de partida (ex: C:\\...).
     * @return O caminho completo para o pom.xml, ou null se a pasta 'src' não for encontrada.
     */
    private String findPomPath(String projectPath) {
        // Cria um objeto File para facilitar a manipulação do caminho
        File currentDir = new File(projectPath);

        // Garante que estamos começando de um diretório.
        // Se o caminho inicial for um arquivo, pegamos a pasta que o contém.
        if (currentDir.isFile()) {
            currentDir = currentDir.getParentFile();
        }

        // Loop para "subir" uma pasta de cada vez
        while (currentDir != null) {
            // Cria um objeto File para a potencial pasta 'src' dentro do diretório atual
            File srcFolder = new File(currentDir, "src");

            // Verifica se a pasta 'src' existe no diretório atual
            if (srcFolder.exists() && srcFolder.isDirectory()) {
                // Se encontrou a pasta 'src', significa que 'currentDir' é a raiz do projeto.
                // Monta o caminho para o pom.xml e o retorna.
                String pomPath = Paths.get(currentDir.getAbsolutePath(), "pom.xml").toString();
                System.out.println("Caminho do pom: " + pomPath);
                return pomPath;
            }

            // Se não encontrou, sobe para a pasta pai para a próxima iteração
            currentDir = currentDir.getParentFile();
        }

        // Se o loop terminar, significa que a pasta 'src' não foi encontrada em nenhum nível
        return null;
    }

}