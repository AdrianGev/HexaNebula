package com.galaxysim;

/**
 * Stores all dispersion settings for the galaxy simulator
 * These settings control spawn rates, colors, and other parameters for celestial bodies.
 * So it's like an open source universe in a sense?
 */
public class DispersionSettings {
    // Region settings
    private static float regionSize = 2000.0f;
    // Sun settings
    private static float sunRarity = 0.0002f;
    private static float sunMinSize = 500.0f;
    private static float sunMaxSize = 1000.0f;
    private static float sunMinDistance = 4000.0f;
    private static float sunMaxDistance = 10000.0f;
    
    // Star settings
    private static float starClusterDensity = 1.0f;
    private static float scatteredStarDensity = 1.0f;
    private static float starGlimmerChance = 0.05f;
    private static float starGlimmerIntensity = 2.5f;
    
    // Shooting star settings
    private static float shootingStarChance = 0.02f;
    private static float shootingStarSpeed = 1.0f;
    private static float shootingStarSize = 1.0f;
    
    // Nebula settings
    private static float nebulaRarity = 0.05f;
    private static float nebulaMinSize = 500.0f;
    private static float nebulaMaxSize = 1500.0f;
    private static float nebulaParticleDensity = 1.0f;
    
    // Getters and setters for all properties
    
    // Sun settings
    public static float getSunRarity() {
        return sunRarity;
    }
    
    public static void setSunRarity(float value) {
        sunRarity = Math.max(0.0001f, Math.min(0.05f, value));
    }
    
    public static float getSunMinSize() {
        return sunMinSize;
    }
    
    public static void setSunMinSize(float value) {
        sunMinSize = Math.max(100.0f, Math.min(sunMaxSize, value));
    }
    
    public static float getSunMaxSize() {
        return sunMaxSize;
    }
    
    public static void setSunMaxSize(float value) {
        sunMaxSize = Math.max(sunMinSize, Math.min(2000.0f, value));
    }
    
    public static float getSunMinDistance() {
        return sunMinDistance;
    }
    
    public static void setSunMinDistance(float value) {
        sunMinDistance = Math.max(1000.0f, Math.min(sunMaxDistance, value));
    }
    
    public static float getSunMaxDistance() {
        return sunMaxDistance;
    }
    
    public static void setSunMaxDistance(float value) {
        sunMaxDistance = Math.max(sunMinDistance, Math.min(20000.0f, value));
    }
    
    // Star settings
    public static float getStarClusterDensity() {
        return starClusterDensity;
    }
    
    public static void setStarClusterDensity(float value) {
        starClusterDensity = Math.max(0.1f, Math.min(5.0f, value));
    }
    
    public static float getScatteredStarDensity() {
        return scatteredStarDensity;
    }
    
    public static void setScatteredStarDensity(float value) {
        scatteredStarDensity = Math.max(0.1f, Math.min(5.0f, value));
    }
    
    public static float getStarGlimmerChance() {
        return starGlimmerChance;
    }
    
    public static void setStarGlimmerChance(float value) {
        starGlimmerChance = Math.max(0.0f, Math.min(1.0f, value));
    }
    
    public static float getStarGlimmerIntensity() {
        return starGlimmerIntensity;
    }
    
    public static void setStarGlimmerIntensity(float value) {
        starGlimmerIntensity = Math.max(1.0f, Math.min(5.0f, value));
    }
    
    // Shooting star settings
    public static float getShootingStarChance() {
        return shootingStarChance;
    }
    
    public static void setShootingStarChance(float value) {
        shootingStarChance = Math.max(0.0f, Math.min(0.1f, value));
    }
    
    public static float getShootingStarSpeed() {
        return shootingStarSpeed;
    }
    
    public static void setShootingStarSpeed(float value) {
        shootingStarSpeed = Math.max(0.1f, Math.min(5.0f, value));
    }
    
    public static float getShootingStarSize() {
        return shootingStarSize;
    }
    
    public static void setShootingStarSize(float value) {
        shootingStarSize = Math.max(0.1f, Math.min(3.0f, value));
    }
    
    // Nebula settings
    public static float getNebulaRarity() {
        return nebulaRarity;
    }
    
    public static void setNebulaRarity(float value) {
        nebulaRarity = Math.max(0.001f, Math.min(0.2f, value));
    }
    
    public static float getNebulaMinSize() {
        return nebulaMinSize;
    }
    
    public static void setNebulaMinSize(float value) {
        nebulaMinSize = Math.max(100.0f, Math.min(nebulaMaxSize, value));
    }
    
    public static float getNebulaMaxSize() {
        return nebulaMaxSize;
    }
    
    public static void setNebulaMaxSize(float value) {
        nebulaMaxSize = Math.max(nebulaMinSize, Math.min(3000.0f, value));
    }
    
    public static float getNebulaParticleDensity() {
        return nebulaParticleDensity;
    }
    
    public static void setNebulaParticleDensity(float value) {
        nebulaParticleDensity = Math.max(0.1f, Math.min(3.0f, value));
    }
    
    // Region settings
    public static float getRegionSize() {
        return regionSize;
    }
    
    public static void setRegionSize(float value) {
        regionSize = Math.max(500.0f, Math.min(5000.0f, value));
    }
}
