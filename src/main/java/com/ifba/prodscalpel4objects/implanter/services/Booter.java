package com.ifba.prodscalpel4objects.implanter.services;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.File;

/**
 * Classe auxiliar para configurar e instanciar o sistema do Maven Resolver (Aether).
 * Contém o código de boilerplate para inicializar o serviço.
 */
class Booter {

    /**
     * Cria e retorna uma nova instância do sistema de repositório principal.
     * @return um objeto RepositorySystem configurado.
     */
    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    /**
     * Cria uma nova sessão do sistema de repositório, configurando o repositório local do Maven.
     * @param system O sistema de repositório a ser usado.
     * @return uma sessão DefaultRepositorySystemSession configurada.
     */
    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // Aponta para o repositório .m2 local do usuário
        String userHome = System.getProperty("user.home");
        LocalRepository localRepo = new LocalRepository(new File(userHome, ".m2/repository"));

        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }
}