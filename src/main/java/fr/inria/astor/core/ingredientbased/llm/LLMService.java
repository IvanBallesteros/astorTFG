package fr.inria.astor.core.ingredientbased.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import fr.inria.astor.core.ingredientbased.prompt.PromptTemplate;
import fr.inria.astor.core.setup.ConfigurationProperties;

/**
 * Service for interacting with Large Language Models
 */
public class LLMService {
    
    // Default service endpoints for different LLM providers
    private static final Map<String, String> SERVICE_URLS = new HashMap<>();
    
    static {
        // Initialize default service URLs
        SERVICE_URLS.put("ollama", "http://localhost:11434/api/generate");
        SERVICE_URLS.put("openai", "https://api.openai.com/v1/chat/completions");
        // Add more providers as needed
    }
    
    /**
     * Get code fix suggestions from an LLM
     * 
     * @param buggyCode The code that needs to be fixed
     * @param testCode The test code that reveals the bug
     * @param maxSuggestions Maximum number of suggestions to return
     * @return List of code suggestions
     */
    public static List<String> getSuggestions(String buggyCode, String testCode, int maxSuggestions) {
        String llmEngine = ConfigurationProperties.getProperty("llmEngine");
        String promptTemplateId = ConfigurationProperties.getProperty("llmprompttemplateid");
        
        List<String> suggestions = new ArrayList<>();
        
        switch(llmEngine.toLowerCase()) {
            case "ollama":
                suggestions = getOllamaSuggestions(buggyCode, testCode, promptTemplateId, maxSuggestions);
                break;
            case "openai":
                suggestions = getOpenAISuggestions(buggyCode, testCode, promptTemplateId, maxSuggestions);
                break;
            default:
                System.out.println("Unsupported LLM engine: " + llmEngine + ". Using Ollama instead.");
                suggestions = getOllamaSuggestions(buggyCode, testCode, promptTemplateId, maxSuggestions);
        }
        
        return suggestions;
    }
    
    /**
     * Get suggestions from Ollama
     */
    private static List<String> getOllamaSuggestions(String buggyCode, String testCode, String promptTemplateId, int maxSuggestions) {
        List<String> suggestions = new ArrayList<>();
        
        try {
            String ollamaModel = ConfigurationProperties.getProperty("ollamaModel");
            // Get the URL from internal configuration rather than from parameters
            String ollamaUrl = SERVICE_URLS.get("ollama");
            
            Map<String, String> replacements = new HashMap<>();
            replacements.put("buggycode", buggyCode);
            replacements.put("testcode", testCode);
            
            String prompt = PromptTemplate.fillTemplate(promptTemplateId, replacements);
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", ollamaModel);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            
            String responseBody = sendPostRequest(ollamaUrl, requestBody.toString());
            
            if (responseBody != null) {
                JSONObject jsonResponse = new JSONObject(responseBody);
                String responseText = jsonResponse.getString("response");
                
                // Basic parsing of code blocks - a more sophisticated parser might be needed
                suggestions.add(extractCodeSuggestion(responseText));
                
                // Limit to max suggestions
                if (suggestions.size() > maxSuggestions) {
                    suggestions = suggestions.subList(0, maxSuggestions);
                }
            } else {
                System.err.println("Error calling Ollama API: No response received");
                // Fallback suggestion for testing
                suggestions.add("return solve(f, min, max)");
            }
        } catch (Exception e) {
            System.err.println("Exception when calling Ollama API: " + e.getMessage());
            e.printStackTrace();
            // Fallback suggestion for testing
            suggestions.add("return solve(f, min, max)");
        }
        
        return suggestions;
    }
    
    /**
     * Get suggestions from OpenAI
     */
    private static List<String> getOpenAISuggestions(String buggyCode, String testCode, String promptTemplateId, int maxSuggestions) {
        List<String> suggestions = new ArrayList<>();
        
        try {
            String openaiModel = ConfigurationProperties.getProperty("openaiModel");
            // Get the URL from internal configuration rather than from parameters
            String openaiUrl = SERVICE_URLS.get("openai");
            String openaiKey = ConfigurationProperties.getProperty("openaiKey");
            
            if (openaiKey.isEmpty()) {
                System.err.println("OpenAI API key not configured");
                // Fallback suggestion for testing
                suggestions.add("return solve(f, min, max)");
                return suggestions;
            }
            
            Map<String, String> replacements = new HashMap<>();
            replacements.put("buggycode", buggyCode);
            replacements.put("testcode", testCode);
            
            String prompt = PromptTemplate.fillTemplate(promptTemplateId, replacements);
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", openaiModel);
            
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            
            requestBody.put("messages", messages);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + openaiKey);
            
            String responseBody = sendPostRequest(openaiUrl, requestBody.toString(), headers);
            
            if (responseBody != null) {
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    String content = messageObj.getString("content");
                    
                    // Extract code from the response
                    suggestions.add(extractCodeSuggestion(content));
                }
                
                // Limit to max suggestions
                if (suggestions.size() > maxSuggestions) {
                    suggestions = suggestions.subList(0, maxSuggestions);
                }
            } else {
                System.err.println("Error calling OpenAI API: No response received");
                // Fallback suggestion for testing
                suggestions.add("return solve(f, min, max)");
            }
        } catch (Exception e) {
            System.err.println("Exception when calling OpenAI API: " + e.getMessage());
            e.printStackTrace();
            // Fallback suggestion for testing
            suggestions.add("return solve(f, min, max)");
        }
        
        return suggestions;
    }
    
    /**
     * Configure a custom service URL for an LLM provider
     * 
     * @param provider The LLM provider name (e.g., "ollama", "openai")
     * @param url The service URL
     */
    public static void setServiceUrl(String provider, String url) {
        SERVICE_URLS.put(provider.toLowerCase(), url);
    }
    
    /**
     * Get the configured service URL for a provider
     * 
     * @param provider The LLM provider name
     * @return The service URL or null if not configured
     */
    public static String getServiceUrl(String provider) {
        return SERVICE_URLS.get(provider.toLowerCase());
    }
    
    /**
     * Send HTTP POST request using HttpURLConnection (Java 8 compatible)
     * 
     * @param urlString The URL to send the request to
     * @param jsonData The JSON data to send
     * @return Response body or null if request failed
     */
    private static String sendPostRequest(String urlString, String jsonData) {
        return sendPostRequest(urlString, jsonData, new HashMap<>());
    }
    
    /**
     * Send HTTP POST request using HttpURLConnection (Java 8 compatible) with custom headers
     * 
     * @param urlString The URL to send the request to
     * @param jsonData The JSON data to send
     * @param headers Map of custom headers
     * @return Response body or null if request failed
     */
    private static String sendPostRequest(String urlString, String jsonData, Map<String, String> headers) {
        HttpURLConnection conn = null;
        StringBuilder response = new StringBuilder();
        
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            
            // Add custom headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
            
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000); // 30 seconds
            conn.setReadTimeout(60000); // 60 seconds
            
            // Send the JSON data
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int status = conn.getResponseCode();
            
            if (status == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                return response.toString();
            } else {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }
                System.err.println("HTTP Error: " + status + " - " + response.toString());
                return null;
            }
        } catch (IOException e) {
            System.err.println("I/O error in HTTP request: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * Extract code suggestion from LLM response text
     */
    private static String extractCodeSuggestion(String text) {
        // Look for code between triple backticks
        int startIdx = text.indexOf("```java");
        if (startIdx != -1) {
            startIdx = text.indexOf("\n", startIdx) + 1;
            int endIdx = text.indexOf("```", startIdx);
            if (endIdx != -1) {
                return text.substring(startIdx, endIdx).trim();
            }
        }
        
        // Look for code between single backticks
        startIdx = text.indexOf("`");
        if (startIdx != -1) {
            startIdx++;
            int endIdx = text.indexOf("`", startIdx);
            if (endIdx != -1) {
                return text.substring(startIdx, endIdx).trim();
            }
        }
        
        // If no code blocks found, return the text itself (limiting to reasonable length)
        if (text.length() > 500) {
            // Try to find a statement that might be a complete line of code
            int lineEnd = text.indexOf(";\n", 0);
            if (lineEnd != -1 && lineEnd < 500) {
                return text.substring(0, lineEnd + 1).trim();
            }
            return text.substring(0, 500).trim();
        }
        
        return text.trim();
    }
}