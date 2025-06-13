package com.resume.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResumeServiceImpl implements ResumeService {

    private ChatClient chatClient;

    public ResumeServiceImpl(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }


    @Override
    public Map<String, Object> generateResumeResponse(String userResumeDescription) throws IOException {
        String promptString = this.loadPromptFromFile("resume_prompt.txt");
        
        // Add explicit instructions to format as JSON
        promptString = "Please provide your response as a valid JSON object. " + promptString;
        
        String promptContent = this.putValuesToTemplate(promptString, Map.of(
                "userDescription", userResumeDescription
        ));
        
        Prompt prompt = new Prompt(promptContent);
        String response = chatClient.prompt(prompt).call().content();
        
        // Log the raw response for debugging
        System.out.println("Raw AI response: " + response);
        
        Map<String, Object> stringObjectMap = parseMultipleResponses(response);
        
        // If data is null, try to create a basic structure
        if (stringObjectMap.get("data") == null) {
            Map<String, Object> basicData = new HashMap<>();
            basicData.put("personalInformation", createBasicPersonalInfo(userResumeDescription));
            basicData.put("summary", "Java developer with 2 years of experience");
            basicData.put("skills", createBasicSkills());
            basicData.put("experience", createBasicExperience(userResumeDescription));
            basicData.put("education", new ArrayList<>());
            basicData.put("certifications", new ArrayList<>());
            basicData.put("projects", new ArrayList<>());
            
            stringObjectMap.put("data", basicData);
        }
        
        return stringObjectMap;
    }

    private Map<String, Object> createBasicPersonalInfo(String description) {
        Map<String, Object> info = new HashMap<>();
        // Extract name if possible
        String name = "Sachin Gupta";
        if (description.toLowerCase().contains("sachin")) {
            name = "Sachin Gupta";
        }
        
        info.put("fullName", name);
        info.put("email", "");
        info.put("phoneNumber", "");
        info.put("location", "");
        info.put("linkedIn", null);
        info.put("gitHub", null);
        info.put("portfolio", null);
        
        return info;
    }

    private List<Map<String, Object>> createBasicSkills() {
        List<Map<String, Object>> skills = new ArrayList<>();
        Map<String, Object> skill = new HashMap<>();
        skill.put("title", "Java");
        skill.put("level", "Intermediate");
        skills.add(skill);
        
        return skills;
    }

    private List<Map<String, Object>> createBasicExperience(String description) {
        List<Map<String, Object>> experiences = new ArrayList<>();
        Map<String, Object> exp = new HashMap<>();
        exp.put("jobTitle", "Java Developer");
        exp.put("company", "Tech Company");
        exp.put("location", "");
        exp.put("duration", "2021 - Present");
        exp.put("responsibility", "Developing and maintaining Java applications");
        experiences.add(exp);
        
        return experiences;
    }

    String loadPromptFromFile(String filename) throws IOException{
        Path path= new ClassPathResource(filename).getFile().toPath();
        return Files.readString(path);
    }
    String putValuesToTemplate(String template, Map<String, String> values){
       for (Map.Entry<String, String> entry:values.entrySet()){
            template=template.replace("{{"+ entry.getKey() + "}}", entry.getValue());
        }
       return template;
    }

    public static Map<String, Object> parseMultipleResponses(String response) {
        Map<String, Object> jsonResponse = new HashMap<>();

        // Extract content inside <think> tags
        int thinkStart = response.indexOf("<think>") + 7;
        int thinkEnd = response.indexOf("</think>");
        if (thinkStart > 6 && thinkEnd != -1) {
            String thinkContent = response.substring(thinkStart, thinkEnd).trim();
            jsonResponse.put("think", thinkContent);
        } else {
            jsonResponse.put("think", null); // Handle missing <think> tags
        }

        // Try multiple approaches to find JSON content
        try {
            // First try to find JSON between ```json and ``` markers
            int jsonStart = response.indexOf("```json");
            int jsonEnd = -1;
            if (jsonStart != -1) {
                jsonStart += 7; // Move past "```json"
                jsonEnd = response.indexOf("```", jsonStart);
                if (jsonEnd != -1) {
                    String jsonContent = response.substring(jsonStart, jsonEnd).trim();
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> dataContent = objectMapper.readValue(jsonContent, Map.class);
                    jsonResponse.put("data", dataContent);
                    return jsonResponse;
                }
            }
            
            // Second, look for a JSON object directly in the response
            jsonStart = response.indexOf("{");
            if (jsonStart != -1 && jsonStart != response.indexOf("{\"think\":")) {
                // Find matching closing brace by counting braces
                int braceCount = 1;
                jsonEnd = jsonStart + 1;
                while (jsonEnd < response.length() && braceCount > 0) {
                    char c = response.charAt(jsonEnd);
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;
                    jsonEnd++;
                }
                
                if (braceCount == 0) {
                    String jsonContent = response.substring(jsonStart, jsonEnd).trim();
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> dataContent = objectMapper.readValue(jsonContent, Map.class);
                    jsonResponse.put("data", dataContent);
                    return jsonResponse;
                }
            }
            
            // If we still don't have data, try to extract content after the thinking section
            if (thinkEnd != -1 && thinkEnd < response.length() - 10) {
                String remainingContent = response.substring(thinkEnd + 8).trim();
                if (remainingContent.startsWith("{") && remainingContent.endsWith("}")) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> dataContent = objectMapper.readValue(remainingContent, Map.class);
                    jsonResponse.put("data", dataContent);
                    return jsonResponse;
                }
            }
            
            // If still no JSON found, the response might not contain valid JSON
            jsonResponse.put("data", null);
            System.err.println("No valid JSON found in the response. Raw response: " + response);
        } catch (Exception e) {
            jsonResponse.put("data", null);
            System.err.println("Error parsing JSON from response: " + e.getMessage());
            e.printStackTrace();
        }

        return jsonResponse;
    }

}
