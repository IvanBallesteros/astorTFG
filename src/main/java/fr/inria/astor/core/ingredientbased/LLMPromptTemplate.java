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
        // More detailed repair prompt with context
        predefinedTemplates.put(
            "BASIC_REPAIR",
                "The following Java code has a bug:\n\n{buggycode}\n\n" +
                "The test case that fails is:\n\n{testcode}\n\n" + 
                "Please provide a line fix that makes the test pass." +
                "Only include the corrected code in your response. Do not include explanations."
        );
        predefinedTemplates.put(
            "ADVANCED_REPAIR", 
                "There's a bug in the following Java code:\n\n{buggycode}\n\n" +
                "The failing test case is:\n\n{testcode}\n\n" +
                "Please provide a line fix that makes the test pass." +
                "Only include the corrected line code in your response. Do not include explanations." +
                "The code should be a valid Java code snippet. The code will be placed in the line I gave you."
        );
        
        // New template for multiple solutions
        predefinedTemplates.put(
            "MULTIPLE_SOLUTIONS",
                "There's a bug in the following Java code:\n\n{buggycode}\n\n" +
                "The failing test case is:\n\n{testcode}\n\n" +
                "Please provide 3 different possible fixes that might make the test pass.\n" +
                "Format your response as follows:\n" +
                "SOLUTION 1:\n[first solution code]\n" +
                "SOLUTION 2:\n[second solution code]\n" +
                "SOLUTION 3:\n[third solution code]\n\n" +
                "Each solution should be a single line of code that can replace the buggy line, do not write code in different lines." +
                "Do not include extra explanations, just the code solutions."
        );
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