package fr.inria.astor.core.ingredientbased;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.json.JSONObject;

/**
 * Service to interact with various LLM providers.
 * Currently supports Ollama local models.
 */
public class LLMService {
    
    // Default URL for Ollama API
    private static final String OLLAMA_API_URL = "URL"; // http://localhost:11434/api/generate
    
    /**
     * Generate a response from Ollama
     * 
     * @param prompt The prompt to send to the model
     * @param model The model name (e.g., "llama2", "codellama", "mistral")
     * @return The model's response text
     */
    public static String generateFromOllama(String prompt, String model) {
        try {
            // Create connection
            URL url = new URL(OLLAMA_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
            // Create JSON request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);  // Get complete response, not streaming
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            StringBuilder response = new StringBuilder();
            try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name())) {
                while (scanner.hasNextLine()) {
                    response.append(scanner.nextLine());
                }
            }
            
            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getString("response");
            
        } catch (IOException e) {
            System.err.println("Error connecting to Ollama: " + e.getMessage());
            return "// Error connecting to Ollama: " + e.getMessage();
        }
    }
    
    /**
     * Generate code from an LLM based on configuration
     * 
     * @param prompt The complete prompt to send
     * @return The generated code
     */
    public static String generateCode(String prompt) {
        // Get LLM service from configuration
        String service = System.getProperty("llmservice", "ollama");
        String model = System.getProperty("llmmodel", "codellama");
        
        if ("ollama".equalsIgnoreCase(service)) {
            return generateFromOllama(prompt, model);
        } else {
            return "// Unsupported LLM service: " + service;
        }
    }
}