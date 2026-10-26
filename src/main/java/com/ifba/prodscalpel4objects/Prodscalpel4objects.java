package com.ifba.prodscalpel4objects;

import com.ifba.prodscalpel4objects.adapter.AdapterService;
import com.ifba.prodscalpel4objects.adapter.model.AdaptedOrganArtifact;
import com.ifba.prodscalpel4objects.adapter.model.ExtractedOrganInfo;
import com.ifba.prodscalpel4objects.adapter.model.ImplantationContext;
import com.ifba.prodscalpel4objects.implanter.services.MethodImplanter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Ponto de entrada do ProdScalpel4Objects (Versão Orientada a Objetos / Spring Boot).
 *
 * <p>Demonstração e execução do pipeline modular de Transplante Automatizado de Software:</p>
 * <pre>
 * [Extractor] -> [IceBox] -> [Finder] -> [Adapter (GI/GP)] -> [Implementer]
 * </pre>
 *
 * @author Equipe ProdScalpel4Objects (IFBA)
 */
public class Prodscalpel4objects {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("  PRODSCALPEL4OBJECTS: Automated Software Transplantation for Java / Spring");
        System.out.println("==========================================================================");

        try {
            Path iceBoxDir = Paths.get("IceBox");
            if (!Files.exists(iceBoxDir)) {
                iceBoxDir = Paths.get("codigo-fonte", "IceBox");
            }

            System.out.println("\n[PIPELINE] Verificando artefatos no IceBox: " + iceBoxDir.toAbsolutePath());

            // 1. Contexto do Host
            Path hostDir = Paths.get("target", "demo_host");
            Files.createDirectories(hostDir);

            // Cria um teste unitário para o órgão demonstrando o ciclo de validação in-situ
            Path testFile = hostDir.resolve("ServiceClassTest.java");
            Files.writeString(testFile, """
                public class ServiceClassTest {
                    public void testProcessData() {
                        ServiceClass service = new ServiceClass();
                        String result = service.processData("hello");
                        if (!"OLLEH".equals(result)) {
                            throw new AssertionError("Resultado inesperado: " + result);
                        }
                    }
                }
            """);

            // 2. Informações do sobre-órgão no IceBox (provenientes do Extractor)
            List<Path> extractedFiles = Files.exists(iceBoxDir)
                    ? Files.walk(iceBoxDir).filter(p -> p.toString().endsWith(".java")).toList()
                    : List.of();

            ExtractedOrganInfo organInfo = new ExtractedOrganInfo(
                    iceBoxDir,
                    "ServiceClass",
                    "processData",
                    "String processData(String input)",
                    extractedFiles,
                    List.of(testFile)
            );

            // 3. Contexto do Ponto de Implantação no Host (proveniente do Finder)
            Path hostFile = hostDir.resolve("UserService.java");
            Files.writeString(hostFile, """
                package com.ifba.receptor;
                public class UserService {
                    public void handleUserRequest() {
                        String rawInputText = "hello world";
                    }
                }
            """);

            ImplantationContext hostContext = new ImplantationContext(
                    hostDir,
                    hostFile,
                    "UserService",
                    "void handleUserRequest()",
                    5,
                    Map.of("rawInputText", "java.lang.String"),
                    List.of("UserServiceBean"),
                    List.of("handleUserRequest")
            );

            // 3. Execução do Módulo ADAPTER (Fases 1 a 4)
            AdapterService adapterService = new AdapterService();
            Path adaptedOutputDir = Paths.get("target", "adapted_output");

            AdaptedOrganArtifact adaptedArtifact = adapterService.adaptAndReduce(
                    organInfo,
                    hostContext,
                    adaptedOutputDir
            );

            // 4. Execução do Módulo IMPLEMENTER (Pedro et al.)
            System.out.println("[PIPELINE - IMPLEMENTER] Recebendo artefato do Adapter para implantação no Host...");
            MethodImplanter implanter = new MethodImplanter(adaptedOutputDir.toString());
            implanter.addReceiverPath(hostDir.toString());
            implanter.addHook("UserService.java", "void handleUserRequest()", adaptedArtifact.suggestedHookCode());

            System.out.println("  -> Hook configurado no receptor:");
            System.out.println("     " + adaptedArtifact.suggestedHookCode());
            System.out.println("\n[PIPELINE] Transplante de software concluído com sucesso de ponta a ponta!");

        } catch (Exception e) {
            System.err.println("Erro na execução do pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
