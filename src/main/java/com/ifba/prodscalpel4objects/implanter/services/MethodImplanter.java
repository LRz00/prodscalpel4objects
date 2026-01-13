package com.ifba.prodscalpel4objects.implanter.services;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;
import java.util.*;

/**
 * Classe orquestradora que gerencia todo o processo de transplante de código.
 * Utiliza classes de serviço para executar tarefas específicas como escanear,
 * analisar, modificar e gerenciar dependências.
 */
public class MethodImplanter {

    // --- Serviços ---
    private final ProjectScanner scanner = new ProjectScanner();
    private final CodeParser parser = new CodeParser();
    private final CodeModifier modifier = new CodeModifier();
    private final PomManager pomManager = new PomManager();
    private final Scanner consoleScanner = new Scanner(System.in);

    // --- Estado ---
    private final String hostRootPath;
    private final Set<String> rootPathOfReceptors = new HashSet<>();
    private final List<String> pathOfFileNames = new ArrayList<>();
    private final List<String> pathOfFileNamesDonor = new ArrayList<>();
    private String donorPomPath;
    private final Set<String> pomPathOfReceptors = new HashSet<>();

    /**
     * Construtor da classe.
     *
     * @param hostRootPath O caminho para o diretório fonte do projeto doador.
     */
    public MethodImplanter(String hostRootPath) {
        this.hostRootPath = hostRootPath;
    }

    /**
     * Adiciona o caminho de um projeto receptor à lista de alvos.
     *
     * @param path O caminho para o diretório do projeto receptor.
     */
    public void addReceiverPath(String path) {
        this.rootPathOfReceptors.add(path);
        addReceiverPomPath(scanner.findPomPath(path));
    }

    /**
     * Adiciona o caminho de um arquivo pom.xml de um receptor.
     *
     * @param path O caminho completo para o arquivo pom.xml.
     */
    public void addReceiverPomPath(String path) {
        if (path != null) this.pomPathOfReceptors.add(path);
    }

    /**
     * Método principal que executa o processo de implantação em três etapas:
     * 1. Cópia/Merge de arquivos, 2. Modificação de pacotes/imports, 3. Implantação de dependências.
     */
    public void implant() {
        copy();
        modifyFiles();
        implantDependencies();
    }

    /**
     * Orquestra a busca de arquivos no projeto doador e o processamento de cada arquivo para cada receptor.
     */
    public void copy() {
        pathOfFileNames.clear();
        pathOfFileNamesDonor.clear();
        List<File> donorFiles = scanner.findJavaFilesInProject(hostRootPath);
        for (File donorFile : donorFiles) {
            pathOfFileNamesDonor.add(donorFile.getAbsolutePath());
            for (String receptorRoot : rootPathOfReceptors) {
                processFileForReceptor(donorFile, receptorRoot);
            }
        }
    }

    /**
     * Processa um único arquivo doador para um receptor específico, decidindo se deve copiar ou mesclar.
     *
     * @param donorFile    O arquivo doador a ser processado.
     * @param receptorRoot O caminho raiz do projeto receptor.
     */
    private void processFileForReceptor(File donorFile, String receptorRoot) {
        String pkg = scanner.getPackageNameFromFile(donorFile);
        File receptorFile = buildReceptorFile(donorFile, receptorRoot, pkg);

        if (!receptorFile.exists()) {
            copyJavaFile(donorFile, pkg, receptorRoot);
        } else {
            handleExistingFile(donorFile, receptorFile, receptorRoot);
        }
    }

    /**
     * Copia um arquivo de origem para o destino, criando a estrutura de pacotes necessária.
     *
     * @param sourceFile          O arquivo de origem.
     * @param packageName         O caminho do pacote (com separadores de diretório).
     * @param destinationRootPath O diretório raiz do destino.
     */
    private void copyJavaFile(File sourceFile, String packageName, String destinationRootPath) {
        String destDirPath = destinationRootPath + File.separator + packageName;
        File destDir = new File(destDirPath);
        if (!destDir.exists()) destDir.mkdirs();

        File destFile = new File(destDir, sourceFile.getName());

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destFile)) {
            out.write(in.readAllBytes());
            System.out.println("Arquivo copiado com sucesso para: " + destFile.getAbsolutePath());
            pathOfFileNames.add(destFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Erro ao copiar o arquivo: " + e.getMessage());
        }
    }

    /**
     * Constrói e retorna um objeto `File` representando o caminho de destino de um arquivo no receptor.
     *
     * @param donorFile    O arquivo doador (usado para obter o nome do arquivo).
     * @param receptorRoot O caminho raiz do receptor.
     * @param pkg          O caminho do pacote (com separadores de diretório).
     * @return Um objeto `File` apontando para o local de destino.
     */
    private File buildReceptorFile(File donorFile, String receptorRoot, String pkg) {
        String destDirPath = receptorRoot + File.separator + pkg;
        File destDir = new File(destDirPath);
        if (!destDir.exists()) destDir.mkdirs();
        return new File(destDir, donorFile.getName());
    }

    /**
     * Gerencia a lógica de comparação e merge para um arquivo que já existe no doador e no receptor.
     *
     * @param donorFile    O arquivo doador.
     * @param receptorFile O arquivo receptor.
     * @param receptorRoot O caminho raiz do receptor (usado para logs).
     */
    private void handleExistingFile(File donorFile, File receptorFile, String receptorRoot) {
        try {
            Map<String, String> donorMap = parser.extractMethodsMap(donorFile);
            Map<String, String> receptorMap = parser.extractMethodsMap(receptorFile);
            Map<String, FieldDeclaration> donorFields = parser.extractFieldsMap(donorFile);
            Map<String, FieldDeclaration> receptorFields = parser.extractFieldsMap(receptorFile);

            processNewFields(donorFields, receptorFields, donorFile, receptorFile, receptorRoot);
            processNewMethods(donorMap.keySet(), receptorMap.keySet(), donorFile, receptorFile, receptorRoot);

            for (String sig : donorMap.keySet()) {
                if (receptorMap.containsKey(sig)) {
                    processPotentialMerge(sig, donorMap.get(sig), receptorMap.get(sig), donorFile, receptorFile, receptorRoot);
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao processar arquivo existente " + donorFile.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Lida com a lógica de interação com o usuário para adicionar ou substituir atributos (fields).
     *
     * @param donorFields    Mapa de atributos do doador.
     * @param receptorFields Mapa de atributos do receptor.
     * @param donorFile      Arquivo doador.
     * @param receptorFile   Arquivo receptor.
     * @param receptorRoot   Caminho raiz do receptor.
     */
    private void processNewFields(Map<String, FieldDeclaration> donorFields, Map<String, FieldDeclaration> receptorFields, File donorFile, File receptorFile, String receptorRoot) {
        for (Map.Entry<String, FieldDeclaration> entry : donorFields.entrySet()) {
            String name = entry.getKey();
            FieldDeclaration donorFd = entry.getValue();
            if (!receptorFields.containsKey(name)) {
                System.out.println("Atributo novo encontrado: '" + name + "'");
                System.out.print("Deseja adicionar este atributo ao receptor? (s/n): ");
                if (consoleScanner.nextLine().equalsIgnoreCase("s")) {
                    try {
                        FieldDeclaration fieldToCopy = parser.findFieldByName(donorFile, name);
                        modifier.addSingleField(fieldToCopy, receptorFile, name);
                        pathOfFileNames.add(receptorFile.getAbsolutePath());
                        System.out.println("Atributo '" + name + "' adicionado a " + receptorRoot);
                    } catch (IOException e) {
                        System.err.println("Falha ao adicionar atributo '" + name + "': " + e.getMessage());
                    }
                }
            } else {
                FieldDeclaration receptorFd = receptorFields.get(name);
                if (modifier.isFieldDifferent(donorFd, receptorFd)) {
                    System.out.println("Conflito de atributo para '" + name + "'. Doador: " + donorFd.toString().trim() + " | Receptor: " + receptorFd.toString().trim());
                    System.out.print("Deseja substituir o atributo existente? (s/n): ");
                    if (consoleScanner.nextLine().equalsIgnoreCase("s")) {
                        try {
                            FieldDeclaration fieldToCopy = parser.findFieldByName(donorFile, name);
                            modifier.replaceSingleField(fieldToCopy, receptorFile, name);
                            pathOfFileNames.add(receptorFile.getAbsolutePath());
                            System.out.println("Atributo '" + name + "' substituído em " + receptorRoot);
                        } catch (IOException e) {
                            System.err.println("Falha ao substituir atributo '" + name + "': " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Lida com a lógica de interação com o usuário para adicionar novos métodos.
     *
     * @param donorSigs    Conjunto de assinaturas de métodos do doador.
     * @param receptorSigs Conjunto de assinaturas de métodos do receptor.
     * @param donorFile    Arquivo doador.
     * @param receptorFile Arquivo receptor.
     * @param receptorRoot Caminho raiz do receptor.
     */
    private void processNewMethods(Set<String> donorSigs, Set<String> receptorSigs, File donorFile, File receptorFile, String receptorRoot) {
        for (String sig : donorSigs) {
            if (!receptorSigs.contains(sig)) {
                System.out.println("Método novo encontrado: '" + sig + "'");
                System.out.print("Deseja adicionar esse método ao receptor? (s/n): ");
                if (consoleScanner.nextLine().equalsIgnoreCase("s")) {
                    try {
                        MethodDeclaration methodToCopy = parser.findMethodBySignature(donorFile, sig);
                        modifier.addSingleMethod(methodToCopy, receptorFile);
                        pathOfFileNames.add(receptorFile.getAbsolutePath());
                        System.out.println("Método '" + sig + "' adicionado a " + receptorRoot);
                    } catch (IOException e) {
                        System.err.println("Falha ao adicionar método '" + sig + "': " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Verifica se há diferenças entre o corpo de dois métodos e pergunta ao usuário se deseja mesclá-los.
     *
     * @param sig          Assinatura do método.
     * @param donorBody    Corpo do método doador.
     * @param receptorBody Corpo do método receptor.
     * @param donorFile    Arquivo doador.
     * @param receptorFile Arquivo receptor.
     * @param receptorRoot Caminho raiz do receptor.
     */
    private void processPotentialMerge(String sig, String donorBody, String receptorBody, File donorFile, File receptorFile, String receptorRoot) {
        List<String> oldLines = Arrays.asList(receptorBody.split("\\R"));
        List<String> newLines = Arrays.asList(donorBody.split("\\R"));
        Patch<String> patch = DiffUtils.diff(oldLines, newLines);
        if (!patch.getDeltas().isEmpty()) {
            System.out.println("Método '" + sig + "' difere em " + receptorRoot);
            System.out.print("Deseja merge? (s/n): ");
            if (consoleScanner.nextLine().equalsIgnoreCase("s")) {
                mergeSingleMethod(sig, donorFile, receptorFile);
            }
        }
    }

    /**
     * Executa a ação de substituir o corpo de um método no receptor.
     *
     * @param sig          A assinatura do método a ser mesclado.
     * @param donorFile    O arquivo doador.
     * @param receptorFile O arquivo receptor.
     */
    private void mergeSingleMethod(String sig, File donorFile, File receptorFile) {
        try {
            MethodDeclaration donorMethod = parser.findMethodBySignature(donorFile, sig);
            modifier.replaceMethodInReceptor(receptorFile, donorMethod);
            pathOfFileNames.add(receptorFile.getAbsolutePath());
            System.out.println("Merge concluído para '" + sig + "'.");
        } catch (Exception e) {
            System.err.println("Falha no merge '" + sig + "': " + e.getMessage());
        }
    }

    /**
     * Itera sobre todos os arquivos que foram copiados/modificados e aplica as correções de pacote e imports.
     */
    public void modifyFiles() {
        Set<String> processedFiles = new HashSet<>();
        for (String donorPath : pathOfFileNamesDonor) {
            File donorFile = new File(donorPath);
            for (String receptorPath : pathOfFileNames) {
                File receptorFile = new File(receptorPath);
                if (donorFile.getName().equals(receptorFile.getName()) && !processedFiles.contains(receptorPath)) {
                    try {
                        modifier.modifyFile(donorFile, receptorFile);
                        processedFiles.add(receptorPath);
                        System.out.println("Arquivo atualizado com sucesso: " + receptorPath);
                    } catch (IOException e) {
                        System.err.println("Erro ao processar o arquivo " + receptorPath + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Orquestra o processo de identificação e adição de dependências nos poms dos receptores.
     * CORREÇÃO: No modo IceBox, busca o pom.xml diretamente na pasta alvo, evitando subir para o POM do sistema.
     */
    public void implantDependencies() {
        // 1. Definição do caminho do POM Doador
        // Se for IceBox, forçamos a busca local. Se for normal, usamos o scanner padrão.
        if (this.hostRootPath != null && this.hostRootPath.contains("IceBox")) {
            System.out.println("--- Modo IceBox Detectado ---");
            // Tenta pegar o pom.xml diretamente na pasta do IceBox informada
            File iceBoxPom = new File(this.hostRootPath, "pom.xml");

            if (iceBoxPom.exists()) {
                this.donorPomPath = iceBoxPom.getAbsolutePath();
                System.out.println("POM do IceBox localizado: " + this.donorPomPath);
            } else {
                System.err.println("ERRO: pom.xml não encontrado diretamente na pasta IceBox: " + this.hostRootPath);
                // Tenta procurar em subpastas IMEDIATAS do IceBox, caso a estrutura seja diferente
                // Mas evita chamar scanner.findPomPath() para não subir a árvore
                return;
            }
        } else {
            // Modo Normal: usa a lógica do scanner que procura a pasta 'src' e sobe se necessário
            this.donorPomPath = scanner.findPomPath(hostRootPath);
        }

        if (donorPomPath == null || pomPathOfReceptors.isEmpty()) {
            System.out.println("\nCaminhos do pom.xml não configurados ou POM do doador não encontrado. Pulando implantação de dependências.");
            return;
        }

        System.out.println("\nIniciando análise de dependências...");

        try {
            File donorPomFile = new File(donorPomPath);
            Model donorModel = pomManager.readPom(donorPomFile);
            Set<Dependency> dependenciesToImplant = new HashSet<>();

            // 2. Lógica de Seleção de Dependências
            if (this.hostRootPath != null && this.hostRootPath.contains("IceBox")) {
                System.out.println("Copiando TODAS as dependências do IceBox para os receptores...");

                // No IceBox, pegamos tudo o que está declarado no POM
                dependenciesToImplant.addAll(donorModel.getDependencies());
                System.out.println("Total de dependências encontradas no IceBox: " + dependenciesToImplant.size());

            } else {
                System.out.println("--- Modo Padrão Detectado ---");
                System.out.println("Analisando imports para filtrar dependências...");

                // Resolve a árvore para mapear pacotes
                pomManager.analyzeDonorDependencies(donorPomFile);

                // Coleta imports apenas dos arquivos modificados
                Set<String> allImports = parser.collectImportsFromModifiedFiles(pathOfFileNames);

                if (allImports.isEmpty()) {
                    System.out.println("Nenhum arquivo modificado requer dependências novas.");
                    return;
                }

                // Filtra o que é necessário
                dependenciesToImplant = pomManager.findRequiredDependencies(allImports);
            }

            // 3. Injeção nos Receptores (Código Comum)
            if (dependenciesToImplant.isEmpty()) {
                System.out.println("Nenhuma dependência listada para implantação.");
                return;
            }

            for (String receptorPomPath : pomPathOfReceptors) {
                try {
                    File receptorPomFile = new File(receptorPomPath);
                    Model receptorModel = pomManager.readPom(receptorPomFile);

                    // O pomManager.addMissingDependencies já verifica duplicatas
                    int count = pomManager.addMissingDependencies(receptorModel, dependenciesToImplant);

                    if (count > 0) {
                        pomManager.writePom(receptorPomFile, receptorModel);
                        System.out.println("Sucesso: " + count + " dependência(s) injetada(s) em: " + receptorPomPath);
                    } else {
                        System.out.println("Receptor já possui as dependências: " + receptorPomPath);
                    }

                } catch (Exception e) {
                    System.err.println("Erro ao processar o pom receptor (" + receptorPomPath + "): " + e.getMessage());
                }
            }

        } catch (IOException | XmlPullParserException e) {
            System.err.println("Erro Crítico ao ler o pom do doador: " + e.getMessage());
        }
    }
}