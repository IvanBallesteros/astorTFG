package fr.inria.astor.core.ingredientbased;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to manage prompt templates for LLM-based repair.
 * This allows referencing templates by name and provides predefined templates.
 */
public class LLMPromptTemplate {
    
    // Static map of predefined templates
    private static final Map<String, String> predefinedTemplates = new HashMap<>();
    
    // Initialize predefined templates
    static {
        // Basic repair prompt
        predefinedTemplates.put("BASIC_REPAIR", 
                "Fix this Java code:\n{buggycode}\nThe failing test is:\n{testcode}");
        
        // More detailed repair prompt with context
        predefinedTemplates.put("DETAILED_REPAIR", 
                "There's a bug in the following Java code:\n\n{buggycode}\n\n" +
                "The failing test case is:\n\n{testcode}\n\n" +
                "Please provide a fix that makes the test pass. " +
                "Only include the corrected code in your response. Do not include explanations.");
        
        // Prompt that asks for explanation
        predefinedTemplates.put("EXPLANATORY_REPAIR", 
                "Fix this Java code:\n\n{buggycode}\n\n" +
                "The failing test is:\n\n{testcode}\n\n" +
                "First, explain what the bug is. " +
                "Then, provide the corrected code.");
    }
    
    /**
     * Get a template by name
     * 
     * @param templateName The name of the predefined template
     * @return The template string or null if not found
     */
    public static String getTemplate(String templateName) {
        return predefinedTemplates.get(templateName);
    }
    
    /**
     * Check if a template with the given name exists
     * 
     * @param templateName The name to check
     * @return true if the template exists, false otherwise
     */
    public static boolean hasTemplate(String templateName) {
        return predefinedTemplates.containsKey(templateName);
    }
    
    /**
     * Fill a template with the provided values
     * 
     * @param template The template string with placeholders
     * @param buggyCode The buggy code to insert
     * @param testCode The test code to insert
     * @return The filled template
     */
    public static String fillTemplate(String template, String buggyCode, String testCode) {
        return template
                .replace("{buggycode}", buggyCode)
                .replace("{testcode}", testCode);
    }
    
    /**
     * Get a filled template by name
     * 
     * @param templateName The name of the template
     * @param buggyCode The buggy code to insert
     * @param testCode The test code to insert
     * @return The filled template or null if template not found
     */
    public static String getFilledTemplate(String templateName, String buggyCode, String testCode) {
        String template = getTemplate(templateName);
        if (template == null) {
            return null;
        }
        return fillTemplate(template, buggyCode, testCode);
    }
}