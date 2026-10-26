package com.ifba.prodscalpel4objects.implanter.services;

import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementação simplificada da interface ModelResolver do Maven.
 * Usada pelo MavenDependencyResolver para encontrar POMs pais durante a construção do modelo do projeto.
 */
class SimpleModelResolver implements ModelResolver {
    private final RepositorySystemSession session;
    private final RepositorySystem system;
    private final List<RemoteRepository> repositories = new ArrayList<>();

    SimpleModelResolver(RepositorySystemSession session, RepositorySystem system, RemoteRepository... initialRepositories) {
        this.session = session;
        this.system = system;
        Collections.addAll(this.repositories, initialRepositories);
    }

    private SimpleModelResolver(SimpleModelResolver original) {
        this.session = original.session;
        this.system = original.system;
        this.repositories.addAll(original.repositories);
    }

    /**
     * Resolve o modelo de um artefato a partir de suas coordenadas Maven.
     * @param groupId O groupId do artefato.
     * @param artifactId O artifactId do artefato.
     * @param version A versão do artefato.
     * @return um ModelSource apontando para o arquivo POM do artefato.
     * @throws UnresolvableModelException se o POM não puder ser encontrado.
     */
    @Override
    public ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new DefaultArtifact(groupId, artifactId, "pom", version));
        request.setRepositories(repositories);
        try {
            return new FileModelSource(system.resolveArtifact(session, request).getArtifact().getFile());
        } catch (ArtifactResolutionException e) {
            throw new UnresolvableModelException(e.getMessage(), groupId, artifactId, version, e);
        }
    }

    /**
     * (NOVO MÉTODO) Resolve o modelo de um POM pai.
     * Este método delega para a outra implementação de resolveModel.
     * @param parent O objeto Parent contendo as coordenadas do POM pai.
     * @return um ModelSource apontando para o arquivo POM do pai.
     * @throws UnresolvableModelException se o POM não puder ser encontrado.
     */
    @Override
    public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
        return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    /**
     * Adiciona um novo repositório à lista de repositórios a serem consultados.
     * @param repository O repositório a ser adicionado.
     * @throws InvalidRepositoryException se o repositório for inválido.
     */
    @Override
    public void addRepository(Repository repository) throws InvalidRepositoryException {
        this.repositories.add(new RemoteRepository.Builder(repository.getId(), "default", repository.getUrl()).build());
    }

    /**
     * (NOVO MÉTODO) Adiciona um novo repositório à lista, com uma flag opcional.
     * Para esta implementação simples, a flag é ignorada e o comportamento é o mesmo que o outro addRepository.
     * @param repository O repositório a ser adicionado.
     * @param replace Se o novo repositório deve substituir um existente (ignorado nesta implementação).
     * @throws InvalidRepositoryException se o repositório for inválido.
     */
    @Override
    public void addRepository(Repository repository, boolean replace) throws InvalidRepositoryException {
        addRepository(repository);
    }

    /**
     * Cria uma nova cópia deste resolvedor.
     * @return uma nova instância de SimpleModelResolver.
     */
    @Override
    public ModelResolver newCopy() {
        return new SimpleModelResolver(this);
    }
}