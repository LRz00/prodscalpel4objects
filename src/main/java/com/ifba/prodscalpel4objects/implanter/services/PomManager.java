package com.ifba.prodscalpel4objects.implanter.services;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PomManager {

    private final MavenDependencyResolver resolver = new MavenDependencyResolver();
    private List<ArtifactResult> resolvedDonorDependencies;
    private Map<String, org.apache.maven.model.Dependency> packageToDependencyMap;

    public void analyzeDonorDependencies(File donorPomFile) {
        System.out.println("Analisando grafo de dependências do projeto doador... Isso pode levar um momento.");
        try {
            this.resolvedDonorDependencies = resolver.resolveDependencies(donorPomFile);
            this.packageToDependencyMap = buildPackageMap(this.resolvedDonorDependencies);
            System.out.println("Análise de dependências concluída. " + packageToDependencyMap.size() + " pacotes mapeados.");
        } catch (Exception e) {
            System.err.println("ERRO CRÍTICO ao resolver dependências do doador: " + e.getMessage());
            this.resolvedDonorDependencies = new ArrayList<>();
            this.packageToDependencyMap = new HashMap<>();
        }
    }

    public Set<org.apache.maven.model.Dependency> findRequiredDependencies(Set<String> imports) {
        if (packageToDependencyMap == null || packageToDependencyMap.isEmpty()) {
            System.err.println("AVISO: O mapa de dependências está vazio. Nenhuma dependência será adicionada.");
            return new HashSet<>();
        }

        Set<org.apache.maven.model.Dependency> requiredDependencies = new HashSet<>();
        for (String importLine : imports) {
            int lastDot = importLine.lastIndexOf('.');
            if (lastDot > 0) {
                String packageName = importLine.substring(0, lastDot);
                if (packageToDependencyMap.containsKey(packageName)) {
                    requiredDependencies.add(packageToDependencyMap.get(packageName));
                }
            }
        }
        return requiredDependencies;
    }

    private Map<String, org.apache.maven.model.Dependency> buildPackageMap(List<ArtifactResult> artifactResults) {
        Map<String, org.apache.maven.model.Dependency> map = new HashMap<>();
        for (ArtifactResult result : artifactResults) {
            File jarFile = result.getArtifact().getFile();
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().endsWith(".class") && !entry.getName().contains("-")) {
                        String className = entry.getName().replace('/', '.').replaceAll("\\.class$", "");
                        int lastDot = className.lastIndexOf('.');
                        if (lastDot > 0) {
                            String packageName = className.substring(0, lastDot);
                            if (!map.containsKey(packageName)) {
                                map.put(packageName, convertToMavenDependency(result));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // Ignora JARs que não podem ser lidos
            }
        }
        return map;
    }

    private org.apache.maven.model.Dependency convertToMavenDependency(ArtifactResult artifactResult) {
        org.eclipse.aether.artifact.Artifact artifact = artifactResult.getArtifact();
        org.apache.maven.model.Dependency dependency = new org.apache.maven.model.Dependency();
        dependency.setGroupId(artifact.getGroupId());
        dependency.setArtifactId(artifact.getArtifactId());
        dependency.setVersion(artifact.getVersion());
        if(artifactResult.getRequest().getDependencyNode() != null && artifactResult.getRequest().getDependencyNode().getDependency() != null) {
            dependency.setScope(artifactResult.getRequest().getDependencyNode().getDependency().getScope());
        }
        return dependency;
    }

    public Model readPom(File pomFile) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (FileReader fileReader = new FileReader(pomFile)) {
            return reader.read(fileReader);
        }
    }

    public void writePom(File pomFile, Model model) throws IOException {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        try (FileWriter fileWriter = new FileWriter(pomFile)) {
            writer.write(fileWriter, model);
        }
    }

    public int addMissingDependencies(Model receptorModel, Set<org.apache.maven.model.Dependency> requiredDependencies) {
        Set<String> existingDependencies = new HashSet<>();
        receptorModel.getDependencies().forEach(dep ->
                existingDependencies.add(dep.getGroupId() + ":" + dep.getArtifactId())
        );
        int addedCount = 0;
        for (org.apache.maven.model.Dependency requiredDep : requiredDependencies) {
            String dependencyCoord = requiredDep.getGroupId() + ":" + requiredDep.getArtifactId();
            if (!existingDependencies.contains(dependencyCoord)) {
                receptorModel.addDependency(requiredDep);
                addedCount++;
            }
        }
        return addedCount;
    }
}