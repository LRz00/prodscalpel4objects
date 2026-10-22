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

public class MethodImplanter {

    // --- Serviços ---
    private final ProjectScanner scanner = new ProjectScanner();
    private final CodeParser parser = new CodeParser();
    private final CodeModifier modifier = new CodeModifier();
    private final PomManager pomManager = new PomManager();
    private final SpringContextModifier springModifier = new SpringContextModifier();

    // --- Estado ---
    private final String hostRootPath;
    private final Set<String> rootPathOfReceptors = new HashSet<>();
    private final List<String> pathOfFileNames = new ArrayList<>();
    private final List<String> pathOfFileNamesDonor = new ArrayList<>();
    private String donorPomPath;
    private final Set<String> pomPathOfReceptors = new HashSet<>();

    // NOVO: Armazenamento automático de Hooks
    private final List<HookDefinition> hooksToInject = new ArrayList<>();

    // Classe auxiliar para armazenar os hooks
    public static class HookDefinition {
        public final String fileName;
        public final String methodSignature;
        public final String codeToInject;

        public HookDefinition(String fileName, String methodSignature, String codeToInject) {
            this.fileName = fileName;
            this.methodSignature = methodSignature;
            this.codeToInject = codeToInject;
        }
    }

    public MethodImplanter(String hostRootPath) {
        this.hostRootPath = hostRootPath;
    }

    public void addReceiverPath(String path) {
        this.rootPathOfReceptors.add(path);
        addReceiverPomPath(scanner.findPomPath(path));
    }

    public void addReceiverPomPath(String path) {
        if (path != null) this.pomPathOfReceptors.add(path);
    }

    /**
     * NOVO: Método para configurar a injeção de código automaticamente sem usar o Console.
     */
    public void addHook(String fileName, String methodSignature, String codeToInject) {
        hooksToInject.add(new HookDefinition(fileName, methodSignature, codeToInject));
    }

    public void implant() {
        copy();
        modifyFiles();
        propagateSpringContext();
        mergeApplicationProperties();
        injectHooks(); // Agora executa silenciosamente com base nos hooks adicionados
        implanterDependencies();
    }

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

    private void processFileForReceptor(File donorFile, String receptorRoot) {
        String pkg = scanner.getPackageNameFromFile(donorFile);
        File receptorFile = buildReceptorFile(donorFile, receptorRoot, pkg);

        if (!receptorFile.exists()) {
            copyJavaFile(donorFile, pkg, receptorRoot);
        } else {
            handleExistingFile(donorFile, receptorFile, receptorRoot);
        }
    }

    private void copyJavaFile(File sourceFile, String packageName, String destinationRootPath) {
        String destDirPath = destinationRootPath + File.separator + packageName;
        File destDir = new File(destDirPath);
        if (!destDir.exists()) destDir.mkdirs();

        File destFile = new File(destDir, sourceFile.getName());

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destFile)) {
            out.write(in.readAllBytes());
            pathOfFileNames.add(destFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Erro ao copiar o arquivo: " + e.getMessage());
        }
    }

    private File buildReceptorFile(File donorFile, String receptorRoot, String pkg) {
        String destDirPath = receptorRoot + File.separator + pkg;
        File destDir = new File(destDirPath);
        if (!destDir.exists()) destDir.mkdirs();
        return new File(destDir, donorFile.getName());
    }

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

    private void processNewFields(Map<String, FieldDeclaration> donorFields, Map<String, FieldDeclaration> receptorFields, File donorFile, File receptorFile, String receptorRoot) {
        Scanner localScanner = new Scanner(System.in);
        for (Map.Entry<String, FieldDeclaration> entry : donorFields.entrySet()) {
            String name = entry.getKey();
            FieldDeclaration donorFd = entry.getValue();
            if (!receptorFields.containsKey(name)) {
                System.out.println("Atributo novo encontrado: '" + name + "'");
                System.out.print("Deseja adicionar este atributo ao receptor? (s/n): ");
                if (localScanner.nextLine().equalsIgnoreCase("s")) {
                    try {
                        FieldDeclaration fieldToCopy = parser.findFieldByName(donorFile, name);
                        modifier.addSingleField(fieldToCopy, receptorFile, name);
                        springModifier.propagateFieldInjectionAnnotations(donorFile, receptorFile, name);
                        pathOfFileNames.add(receptorFile.getAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("Falha ao adicionar atributo '" + name + "': " + e.getMessage());
                    }
                }
            } else {
                FieldDeclaration receptorFd = receptorFields.get(name);
                if (modifier.isFieldDifferent(donorFd, receptorFd)) {
                    System.out.println("Conflito de atributo para '" + name + "'. Doador: " + donorFd.toString().trim() + " | Receptor: " + receptorFd.toString().trim());
                    System.out.print("Deseja substituir o atributo existente? (s/n): ");
                    if (localScanner.nextLine().equalsIgnoreCase("s")) {
                        try {
                            FieldDeclaration fieldToCopy = parser.findFieldByName(donorFile, name);
                            modifier.replaceSingleField(fieldToCopy, receptorFile, name);
                            springModifier.propagateFieldInjectionAnnotations(donorFile, receptorFile, name);
                            pathOfFileNames.add(receptorFile.getAbsolutePath());
                        } catch (IOException e) {
                            System.err.println("Falha ao substituir atributo '" + name + "': " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private void processNewMethods(Set<String> donorSigs, Set<String> receptorSigs, File donorFile, File receptorFile, String receptorRoot) {
        Scanner localScanner = new Scanner(System.in);
        for (String sig : donorSigs) {
            if (!receptorSigs.contains(sig)) {
                System.out.println("Método novo encontrado: '" + sig + "'");
                System.out.print("Deseja adicionar esse método ao receptor? (s/n): ");
                if (localScanner.nextLine().equalsIgnoreCase("s")) {
                    try {
                        MethodDeclaration methodToCopy = parser.findMethodBySignature(donorFile, sig);
                        modifier.addSingleMethod(methodToCopy, receptorFile);
                        pathOfFileNames.add(receptorFile.getAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("Falha ao adicionar método '" + sig + "': " + e.getMessage());
                    }
                }
            }
        }
    }

    private void processPotentialMerge(String sig, String donorBody, String receptorBody, File donorFile, File receptorFile, String receptorRoot) {
        Scanner localScanner = new Scanner(System.in);
        List<String> oldLines = Arrays.asList(receptorBody.split("\\R"));
        List<String> newLines = Arrays.asList(donorBody.split("\\R"));
        Patch<String> patch = DiffUtils.diff(oldLines, newLines);
        if (!patch.getDeltas().isEmpty()) {
            System.out.println("Método '" + sig + "' difere em " + receptorRoot);
            System.out.print("Deseja merge? (s/n): ");
            if (localScanner.nextLine().equalsIgnoreCase("s")) {
                mergeSingleMethod(sig, donorFile, receptorFile);
            }
        }
    }

    private void mergeSingleMethod(String sig, File donorFile, File receptorFile) {
        try {
            MethodDeclaration donorMethod = parser.findMethodBySignature(donorFile, sig);
            modifier.replaceMethodInReceptor(receptorFile, donorMethod);
            pathOfFileNames.add(receptorFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Falha no merge '" + sig + "': " + e.getMessage());
        }
    }

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
                    } catch (IOException e) {
                        System.err.println("Erro ao processar o arquivo " + receptorPath + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    public void propagateSpringContext() {
        Set<String> processedFiles = new HashSet<>();
        for (String donorPath : pathOfFileNamesDonor) {
            File donorFile = new File(donorPath);
            for (String receptorPath : pathOfFileNames) {
                File receptorFile = new File(receptorPath);
                if (donorFile.getName().equals(receptorFile.getName()) && !processedFiles.contains(receptorPath)) {
                    try {
                        springModifier.propagateClassStereotypes(donorFile, receptorFile);
                        processedFiles.add(receptorPath);
                    } catch (IOException e) {
                        System.err.println("Erro ao propagar estereótipos: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * NOVO: Verifica de forma genérica se o projeto usa .properties, .yml ou .yaml.
     */
    public void mergeApplicationProperties() {
        System.out.println("\nVerificando configurações (YML ou Properties)...");
        String donorPom = (this.hostRootPath != null && this.hostRootPath.contains("IceBox")) ?
                new File(this.hostRootPath, "pom.xml").getAbsolutePath() : scanner.findPomPath(hostRootPath);

        // NOVO: Levanta exatamente quais propriedades foram injetadas no receptor
        Set<String> propertiesUtilizadas = springModifier.extractUsedProperties(pathOfFileNames);

        if (donorPom != null) {
            File donorConfig = findConfigFile(new File(donorPom).getParentFile());
            if (donorConfig != null && donorConfig.exists()) {

                for (String receptorRoot : rootPathOfReceptors) {
                    String receptorPom = scanner.findPomPath(receptorRoot);
                    if (receptorPom != null) {
                        File receptorConfig = findConfigFile(new File(receptorPom).getParentFile());
                        if (receptorConfig != null && receptorConfig.exists()) {
                            try {
                                // NOVO: Passa a lista de propriedades permitidas para realizar o filtro
                                springModifier.mergeProperties(donorConfig, receptorConfig, propertiesUtilizadas);
                            } catch (IOException e) {
                                System.err.println("Erro ao realizar merge das configurações: " + e.getMessage());
                            }
                        }
                    }
                }
            } else {
                System.out.println("O doador não possui um arquivo de configuração (.yml ou .properties) padrão.");
            }
        }
    }

    /**
     * Auxiliar para encontrar o arquivo de configuração independente da extensão.
     */
    private File findConfigFile(File baseDir) {
        File resourcesDir = new File(baseDir, "src" + File.separator + "main" + File.separator + "resources");
        File prop = new File(resourcesDir, "application.properties");
        if (prop.exists()) return prop;
        File yml = new File(resourcesDir, "application.yml");
        if (yml.exists()) return yml;
        File yaml = new File(resourcesDir, "application.yaml");
        if (yaml.exists()) return yaml;
        return null; // Não encontrou nenhum
    }

    /**
     * NOVO: Execução silenciosa dos hooks.
     */
    public void injectHooks() {
        if (hooksToInject.isEmpty()) return;
        System.out.println("\nInjetando Hooks Automáticos configurados...");

        for (HookDefinition hook : hooksToInject) {
            for (String receptorPath : pathOfFileNames) {
                File receptorFile = new File(receptorPath);
                // Valida apenas pelo nome do arquivo para encontrar o alvo correto
                if (receptorFile.getName().equals(hook.fileName)) {
                    try {
                        modifier.injectMethodCallHook(receptorFile, hook.methodSignature, hook.codeToInject);
                    } catch (Exception e) {
                        System.err.println("Falha ao injetar hook no arquivo " + hook.fileName + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    public void implanterDependencies() {
        if (this.hostRootPath != null && this.hostRootPath.contains("IceBox")) {
            File iceBoxPom = new File(this.hostRootPath, "pom.xml");
            if (iceBoxPom.exists()) {
                this.donorPomPath = iceBoxPom.getAbsolutePath();
            } else {
                return;
            }
        } else {
            this.donorPomPath = scanner.findPomPath(hostRootPath);
        }

        if (donorPomPath == null || pomPathOfReceptors.isEmpty()) {
            return;
        }

        try {
            File donorPomFile = new File(donorPomPath);
            Model donorModel = pomManager.readPom(donorPomFile);
            Set<Dependency> dependenciesToImplant = new HashSet<>();

            if (this.hostRootPath != null && this.hostRootPath.contains("IceBox")) {
                dependenciesToImplant.addAll(donorModel.getDependencies());
            } else {
                pomManager.analyzeDonorDependencies(donorPomFile);
                Set<String> allImports = parser.collectImportsFromModifiedFiles(pathOfFileNames);

                if (allImports.isEmpty()) return;
                dependenciesToImplant = pomManager.findRequiredDependencies(allImports);
            }

            if (dependenciesToImplant.isEmpty()) return;

            for (String receptorPomPath : pomPathOfReceptors) {
                try {
                    File receptorPomFile = new File(receptorPomPath);
                    Model receptorModel = pomManager.readPom(receptorPomFile);

                    int count = pomManager.addMissingDependencies(receptorModel, dependenciesToImplant);
                    if (count > 0) {
                        pomManager.writePom(receptorPomFile, receptorModel);
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