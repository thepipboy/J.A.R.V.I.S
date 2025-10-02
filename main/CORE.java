package com.starkindustries.jarvis.core;

public interface JARVISSystem {
    // Core system operations
    void initializeSystem();
    void shutdownSystem();
    void emergencyProtocol();
    
    // AI capabilities
    void processVoiceCommand(String command);
    void analyzeSituation();
    void learnFromExperience(String experience);
    
    // System monitoring
    void systemDiagnostics();
    void performanceReport();
    
    // Tony-specific features
    void suitIntegration();
    void starkIndustriesNetworkAccess();
}
