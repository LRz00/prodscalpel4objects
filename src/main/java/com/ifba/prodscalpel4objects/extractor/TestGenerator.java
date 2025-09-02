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

    public void generateAndRunTests(String methodName) throws IOException {
        generateTests(methodName);
        runTests();
    }

    private void generateTests(String methodName) throws IOException {
        Path testsDir = iceBoxPath.resolve("tests");
        Files.createDirectories(testsDir);

        Files.walk(iceBoxPath)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String code = Files.readString(path);

                        // Construindo o JSON para a API OpenAI
                        JSONObject requestBody = new JSONObject();
                        requestBody.put("model", "gpt-5");

                        JSONArray messages = new JSONArray();
                        JSONObject message = new JSONObject();
                        message.put("role", "user");
                        message.put("content", "Você é um gerador de testes em Java usando JUnit 5 e Mockito."
                                + "Foque em testar apenas o método chamado '" + methodName + "' e sua classe, nao teste mais nada. "
                                + "Use Mockito se necessário. Não modifique o código original, apenas crie testes.\n\n"
                                + "Em sua resposta nao inclua nada alem de código."
                                + code);

                        messages.put(message);

                        requestBody.put("messages", messages);

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + apiKey)
                                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                                .build();

                        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
                        
                        ///debug
                        try (FileWriter writer = new FileWriter("api_responses.log", true)) {
                            writer.write("==== " + LocalDateTime.now() + " ====\n");
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

                        String fileName = path.getFileName().toString().replace(".java", "Test.java");
                        Path testFile = testsDir.resolve(fileName);

                        if (Files.exists(testFile)) {
                            System.out.println("⚠️ Arquivo de teste já existe: " + testFile + ". Pulando...");
                            return;
                        }

                        Files.writeString(testFile, testContent);
                        System.out.println("Teste gerado: " + testFile);

                    } catch (Exception e) {
                        System.err.println("Erro ao processar arquivo: " + path);
                        e.printStackTrace();
                    }
                });
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