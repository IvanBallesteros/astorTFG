package fr.inria.astor.core.ingredientbased.prompt;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages prompt templates for LLM interactions
 */
public class PromptTemplate {
    
    private static final Map<String, String> TEMPLATES = new HashMap<>();
    
    static {
        // Default templates   
        TEMPLATES.put("default", 
                "You are a Java programming expert. Fix the following buggy code:\n" +
                "```java\n{buggycode}\n```\n\n" +
                "The failing test is:\n```java\n{testcode}\n```\n\n" +
                "Provide only the corrected code snippet without explanations.");
        TEMPLATES.put("with-test", "Fix this Java code:\n{buggycode}\nThe failing test is:\n{testcode}");
    }
    
    /**
     * Gets a prompt template by ID
     * 
     * @param templateId The ID of the template
     * @return The template string or the default if not found
     */
    public static String getTemplate(String templateId) {
        return TEMPLATES.getOrDefault(templateId, TEMPLATES.get("default"));
    }
    
    /**
     * Adds or updates a template
     * 
     * @param templateId The ID for the template
     * @param template The template string
     */
    public static void setTemplate(String templateId, String template) {
        TEMPLATES.put(templateId, template);
    }
    
    /**
     * Fills a template with provided values
     * 
     * @param templateId The template ID
     * @param replacements A map of placeholder keys to values
     * @return The filled template
     */
    public static String fillTemplate(String templateId, Map<String, String> replacements) {
        String template = getTemplate(templateId);
        
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return template;
    }
}