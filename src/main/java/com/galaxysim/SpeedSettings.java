package com.galaxysim;

/**
 * Stores all speed settings for HexaNebula
 * These settings control movement speed in the simulation
 */
public class SpeedSettings {
    // Default movement speed
    private static float defaultSpeed = 2.0f;
    
    // Amplified speed multiplier (when control is pressed)
    private static float amplifiedSpeedMultiplier = 5.0f;
    
    // Getters and setters for all properties
    
    public static float getDefaultSpeed() {
        return defaultSpeed;
    }
    
    public static void setDefaultSpeed(float value) {
        defaultSpeed = Math.max(0.5f, Math.min(10.0f, value));
    }
    
    public static float getAmplifiedSpeedMultiplier() {
        return amplifiedSpeedMultiplier;
    }
    
    public static void setAmplifiedSpeedMultiplier(float value) {
        amplifiedSpeedMultiplier = Math.max(1.5f, Math.min(20.0f, value));
    }
    
    // Calculate the amplified speed
    public static float getAmplifiedSpeed() {
        return defaultSpeed * amplifiedSpeedMultiplier;
    }
}
