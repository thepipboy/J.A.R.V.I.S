package com.starkindustries.jarvis.security;

import java.util.*;

public class SecuritySystem {
    private SecurityLevel currentLevel;
    private Set<String> authorizedUsers;
    private Map<String, Threat> activeThreats;
    private boolean lockdownActive;
    
    public SecuritySystem() {
        this.currentLevel = SecurityLevel.NORMAL;
        this.authorizedUsers = new HashSet<>();
        this.activeThreats = new HashMap<>();
        initializeSecurity();
    }
    
    private void initializeSecurity() {
        authorizedUsers.add("Tony Stark");
        authorizedUsers.add("Pepper Potts");
        authorizedUsers.add("James Rhodes");
        authorizedUsers.add("Avengers Protocol");
    }
    
    public boolean authenticateUser(UserProfile user) {
        boolean authenticated = authorizedUsers.contains(user.getName());
        if (authenticated) {
            log("User authenticated: " + user.getName());
        } else {
            log("SECURITY BREACH: Unauthorized access attempt by " + user.getName());
            elevateSecurityLevel();
        }
        return authenticated;
    }
    
    public ThreatAnalysis analyzeThreats() {
        ThreatAnalysis analysis = new ThreatAnalysis();
        analysis.setThreatLevel(currentLevel);
        analysis.setActiveThreats(new ArrayList<>(activeThreats.values()));
        analysis.setRecommendation(generateSecurityRecommendation());
        return analysis;
    }
    
    public void lockdownProtocol() {
        this.lockdownActive = true;
        this.currentLevel = SecurityLevel.LOCKDOWN;
        log("SYSTEM LOCKDOWN INITIATED! All external access denied.");
    }
    
    private void elevateSecurityLevel() {
        switch (currentLevel) {
            case NORMAL -> currentLevel = SecurityLevel.ELEVATED;
            case ELEVATED -> currentLevel = SecurityLevel.HIGH;
            case HIGH -> lockdownProtocol();
        }
    }
    
    private String generateSecurityRecommendation() {
        return switch (currentLevel) {
            case NORMAL -> "All security systems nominal.";
            case ELEVATED -> "Recommend increased surveillance.";
            case HIGH -> "Immediate action required! Activate countermeasures!";
            case LOCKDOWN -> "CRITICAL: Full system isolation in effect!";
        };
    }
    
    public String getSecurityLevel() {
        return currentLevel.toString();
    }
    
    private void log(String message) {
        System.out.println("SECURITY: " + message);
    }
    
    // Enums and supporting classes
    public enum SecurityLevel {
        NORMAL, ELEVATED, HIGH, LOCKDOWN
    }
    
    public static class Threat {
        private String type;
        private int severity;
        private String location;
        
        // Constructor, getters, setters
        public Threat(String type, int severity, String location) {
            this.type = type;
            this.severity = severity;
            this.location = location;
        }
        
        public String getType() { return type; }
        public int getSeverity() { return severity; }
        public String getLocation() { return location; }
    }
    
    public static class ThreatAnalysis {
        private SecurityLevel threatLevel;
        private List<Threat> activeThreats;
        private String recommendation;
        
        // Getters and setters
        public SecurityLevel getThreatLevel() { return threatLevel; }
        public void setThreatLevel(SecurityLevel threatLevel) { this.threatLevel = threatLevel; }
        
        public List<Threat> getActiveThreats() { return activeThreats; }
        public void setActiveThreats(List<Threat> activeThreats) { this.activeThreats = activeThreats; }
        
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    }
}