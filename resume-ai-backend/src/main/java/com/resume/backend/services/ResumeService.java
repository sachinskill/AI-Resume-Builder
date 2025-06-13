package com.resume.backend.services;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

public interface ResumeService {

   public Map<String, Object> generateResumeResponse(String userResumeDescription) throws IOException;
}
