package com.ifba.prodscalpel4objects.implanter.services;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.*;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Resolve a árvore de dependências completa de um projeto Maven,
 * incluindo as dependências transitivas e considerando POMs pais.
 */
public class MavenDependencyResolver {

    private final RepositorySystem system;
    private final RemoteRepository central;

    public MavenDependencyResolver() {
        this.system = Booter.newRepositorySystem();
        this.central = new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build();
    }

    public List<ArtifactResult> resolveDependencies(File pomFile) throws Exception {
        RepositorySystemSession session = Booter.newRepositorySystemSession(system);

        ModelBuildingRequest modelRequest = new DefaultModelBuildingRequest();
        modelRequest.setPomFile(pomFile);
        modelRequest.setModelResolver(new SimpleModelResolver(session, system, central));
        modelRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        Properties systemProperties = new Properties();
        systemProperties.putAll(System.getProperties());
        modelRequest.setSystemProperties(systemProperties);

        ModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
        ModelBuildingResult modelResult = modelBuilder.build(modelRequest);

        Model effectiveModel = modelResult.getEffectiveModel();

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.addRepository(central);

        for (org.apache.maven.model.Dependency d : effectiveModel.getDependencies()) {
            if ("test".equalsIgnoreCase(d.getScope())) {
                continue;
            }
            Artifact artifact = new DefaultArtifact(d.getGroupId(), d.getArtifactId(), d.getClassifier(), d.getType(), d.getVersion());
            collectRequest.addDependency(new Dependency(artifact, d.getScope()));
        }

        CollectResult collectResult = system.collectDependencies(session, collectRequest);
        DependencyNode root = collectResult.getRoot();

        DependencyRequest dependencyRequest = new DependencyRequest(root, (node, parents) -> node.getDependency() != null && !"test".equalsIgnoreCase(node.getDependency().getScope()));

        system.resolveDependencies(session, dependencyRequest);

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        root.accept(nlg);

        return nlg.getArtifacts(false).stream()
                .map(a -> {
                    ArtifactRequest req = new ArtifactRequest(a, List.of(central), null);
                    try {
                        return system.resolveArtifact(session, req);
                    } catch (ArtifactResolutionException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}