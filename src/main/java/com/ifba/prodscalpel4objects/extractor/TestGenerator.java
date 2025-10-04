package com.ifba.prodscalpel4objects.extractor;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import org.json.JSONObject;
import org.json.JSONArray;

public class TestGenerator {

    private final Path iceBoxPath;
    private final String apiKey;
    private final HttpClient httpClient;

    public TestGenerator(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key não pode ser nula ou vazia");
        }

        this.iceBoxPath = Paths.get(System.getProperty("user.dir"), "IceBox");
        if (!Files.exists(iceBoxPath)) {
            throw new IllegalStateException("Diretório IceBox não encontrado em: " + iceBoxPath);
        }

        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void generateAndRunTests(String methodName, String sourceFilepath) throws IOException {
        generateTests(methodName, sourceFilepath);
        runTests();
    }

    private void generateTests(String methodName, String sourceFilepath) throws IOException {
        Path testsDir = iceBoxPath.resolve("tests");
        Files.createDirectories(testsDir);

        // Primeiro, encontre a classe que contém o método
        String targetClassName = Paths.get(sourceFilepath).getFileName().toString().replace(".java", "");

        System.out.println("🎯 Gerando testes para a classe: " + targetClassName + ", método: " + methodName);

        // Agora processa apenas a classe que contém o método
        Files.walk(iceBoxPath)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .filter(path -> path.getFileName().toString().replace(".java", "").equals(targetClassName))
                .forEach(path -> {
                    try {
                        String code = Files.readString(path);

                        // Construindo o JSON para a API OpenAI
                        JSONObject requestBody = new JSONObject();
                        requestBody.put("model", "gpt-4o-mini");

                        JSONArray messages = new JSONArray();
                        JSONObject message = new JSONObject();
                        message.put("role", "user");
                        message.put("content", "Você é um gerador de testes em Java usando JUnit 5 e Mockito."
                                + "Foque em testar APENAS o método chamado '" + methodName + "' da classe '" + targetClassName + "'. "
                                + "Não gere testes para outros métodos. Use as outras classes apenas como contexto se necessário. "
                                + "Use Mockito se necessário para mockar dependências. "
                                + "Não modifique o código original, apenas crie testes unitários.\n\n"
                                + "Em sua resposta não inclua nada além do código Java dos testes.\n\n"
                                + "Código da classe:\n" + code);

                        messages.put(message);

                        requestBody.put("messages", messages);
                        requestBody.put("max_completion_tokens", 1200);

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + apiKey)
                                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                                .build();

                        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
                        
                        // Debug
                        try (FileWriter writer = new FileWriter("api_responses.log", true)) {
                            writer.write("==== " + LocalDateTime.now() + " ====\n");
                            writer.write("Método alvo: " + methodName + "\n");
                            writer.write("Classe alvo: " + targetClassName + "\n");
                            writer.write(response.body() + "\n\n");
                        } catch (IOException e) {
                            System.err.println("Erro ao salvar log da API: " + e.getMessage());
                        }

                        // Parse da resposta
                        JSONObject responseJson = new JSONObject(response.body());
                        String testContent = responseJson.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        // Limpa possíveis markdown ou formatação extra
                        testContent = cleanTestContent(testContent);

                        String fileName = targetClassName + "Test.java";
                        Path testFile = testsDir.resolve(fileName);

                        if (Files.exists(testFile)) {
                            System.out.println("⚠️ Arquivo de teste já existe: " + testFile + ". Sobrescrevendo...");
                        }

                        Files.writeString(testFile, testContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        System.out.println("✅ Teste gerado: " + testFile);

                    } catch (Exception e) {
                        System.err.println("❌ Erro ao processar arquivo: " + path);
                        e.printStackTrace();
                    }
                });
    }

    /**
     * Limpa o conteúdo do teste removendo markdown e formatação extra
     */
    private String cleanTestContent(String testContent) {
        // Remove blocos de código markdown
        testContent = testContent.replaceAll("```java", "").replaceAll("```", "");
        
        // Remove possíveis textos explicativos no início/fim
        testContent = testContent.trim();
        
        // Garante que começa com package ou import
        if (!testContent.startsWith("package") && !testContent.startsWith("import")) {
            // Encontra a primeira linha que parece código Java
            String[] lines = testContent.split("\n");
            StringBuilder cleaned = new StringBuilder();
            boolean codeStarted = false;
            
            for (String line : lines) {
                if (line.trim().startsWith("package") || 
                    line.trim().startsWith("import") || 
                    line.trim().startsWith("@") ||
                    line.trim().startsWith("public class") ||
                    line.trim().startsWith("class")) {
                    codeStarted = true;
                }
                if (codeStarted) {
                    cleaned.append(line).append("\n");
                }
            }
            testContent = cleaned.toString();
        }
        
        return testContent.trim();
    }

    private void runTests() {
        try {
            // Verifica se o Maven está disponível
            ProcessBuilder pb = new ProcessBuilder("mvn", "-v");
            Process checkMaven = pb.start();
            if (checkMaven.waitFor() != 0) {
                throw new IllegalStateException("Maven não está instalado ou não está no PATH");
            }

            // Executa os testes
            pb = new ProcessBuilder("mvn", "test");
            pb.directory(iceBoxPath.toFile());
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("✅ Testes executados com sucesso!");
            } else {
                System.err.println("❌ Falha nos testes. Código de saída: " + exitCode);
            }
        } catch (Exception e) {
            System.err.println("Erro ao executar testes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}