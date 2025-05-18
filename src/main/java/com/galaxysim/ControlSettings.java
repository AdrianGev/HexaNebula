package com.galaxysim;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Stores all control settings for HexaNebula
 * These settings control key bindings for movement and other actions
 */
public class ControlSettings {
    // Movement keys
    private static int moveForwardKey = GLFW_KEY_W;
    private static int moveBackwardKey = GLFW_KEY_S;
    private static int moveLeftKey = GLFW_KEY_A;
    private static int moveRightKey = GLFW_KEY_D;
    private static int moveUpKey = GLFW_KEY_SPACE;
    private static int moveDownKey = GLFW_KEY_LEFT_SHIFT;
    
    // Speed amplification key
    private static int amplifySpeedKey = GLFW_KEY_LEFT_CONTROL;
    
    // Key name mapping for display purposes
    private static final String[] KEY_NAMES = new String[349]; // GLFW defines keys up to 348
    
    static {
        // Initialize key name mapping
        initKeyNames();
    }
    
    /**
     * Initialize the mapping between GLFW key codes and their display names
     */
    private static void initKeyNames() {
        // Letters
        for (int i = 0; i < 26; i++) {
            KEY_NAMES[GLFW_KEY_A + i] = String.valueOf((char)('A' + i));
        }
        
        // Numbers
        for (int i = 0; i < 10; i++) {
            KEY_NAMES[GLFW_KEY_0 + i] = String.valueOf(i);
        }
        
        // Function keys
        for (int i = 0; i < 25; i++) {
            KEY_NAMES[GLFW_KEY_F1 + i] = "F" + (i + 1);
        }
        
        // Special keys
        KEY_NAMES[GLFW_KEY_SPACE] = "SPACE";
        KEY_NAMES[GLFW_KEY_ESCAPE] = "ESC";
        KEY_NAMES[GLFW_KEY_ENTER] = "ENTER";
        KEY_NAMES[GLFW_KEY_TAB] = "TAB";
        KEY_NAMES[GLFW_KEY_BACKSPACE] = "BACKSPACE";
        KEY_NAMES[GLFW_KEY_INSERT] = "INSERT";
        KEY_NAMES[GLFW_KEY_DELETE] = "DELETE";
        KEY_NAMES[GLFW_KEY_RIGHT] = "RIGHT";
        KEY_NAMES[GLFW_KEY_LEFT] = "LEFT";
        KEY_NAMES[GLFW_KEY_DOWN] = "DOWN";
        KEY_NAMES[GLFW_KEY_UP] = "UP";
        KEY_NAMES[GLFW_KEY_PAGE_UP] = "PAGE UP";
        KEY_NAMES[GLFW_KEY_PAGE_DOWN] = "PAGE DOWN";
        KEY_NAMES[GLFW_KEY_HOME] = "HOME";
        KEY_NAMES[GLFW_KEY_END] = "END";
        KEY_NAMES[GLFW_KEY_CAPS_LOCK] = "CAPS LOCK";
        KEY_NAMES[GLFW_KEY_SCROLL_LOCK] = "SCROLL LOCK";
        KEY_NAMES[GLFW_KEY_NUM_LOCK] = "NUM LOCK";
        KEY_NAMES[GLFW_KEY_PRINT_SCREEN] = "PRINT SCREEN";
        KEY_NAMES[GLFW_KEY_PAUSE] = "PAUSE";
        KEY_NAMES[GLFW_KEY_LEFT_SHIFT] = "LEFT SHIFT";
        KEY_NAMES[GLFW_KEY_LEFT_CONTROL] = "LEFT CTRL";
        KEY_NAMES[GLFW_KEY_LEFT_ALT] = "LEFT ALT";
        KEY_NAMES[GLFW_KEY_LEFT_SUPER] = "LEFT SUPER";
        KEY_NAMES[GLFW_KEY_RIGHT_SHIFT] = "RIGHT SHIFT";
        KEY_NAMES[GLFW_KEY_RIGHT_CONTROL] = "RIGHT CTRL";
        KEY_NAMES[GLFW_KEY_RIGHT_ALT] = "RIGHT ALT";
        KEY_NAMES[GLFW_KEY_RIGHT_SUPER] = "RIGHT SUPER";
        KEY_NAMES[GLFW_KEY_MENU] = "MENU";
    }
    
    // Getters for movement keys
    public static int getMoveForwardKey() {
        return moveForwardKey;
    }
    
    public static int getMoveBackwardKey() {
        return moveBackwardKey;
    }
    
    public static int getMoveLeftKey() {
        return moveLeftKey;
    }
    
    public static int getMoveRightKey() {
        return moveRightKey;
    }
    
    public static int getMoveUpKey() {
        return moveUpKey;
    }
    
    public static int getMoveDownKey() {
        return moveDownKey;
    }
    
    public static int getAmplifySpeedKey() {
        return amplifySpeedKey;
    }
    
    // Setters for movement keys
    public static void setMoveForwardKey(int key) {
        moveForwardKey = key;
    }
    
    public static void setMoveBackwardKey(int key) {
        moveBackwardKey = key;
    }
    
    public static void setMoveLeftKey(int key) {
        moveLeftKey = key;
    }
    
    public static void setMoveRightKey(int key) {
        moveRightKey = key;
    }
    
    public static void setMoveUpKey(int key) {
        moveUpKey = key;
    }
    
    public static void setMoveDownKey(int key) {
        moveDownKey = key;
    }
    
    public static void setAmplifySpeedKey(int key) {
        amplifySpeedKey = key;
    }
    
    /**
     * Get the display name for a key
     * @param key The GLFW key code
     * @return The display name for the key
     */
    public static String getKeyName(int key) {
        if (key >= 0 && key < KEY_NAMES.length && KEY_NAMES[key] != null) {
            return KEY_NAMES[key];
        }
        return "UNKNOWN";
    }
    
    /**
     * Check if the amplify speed key is pressed
     * @param window The GLFW window handle
     * @return True if the amplify speed key is pressed
     */
    public static boolean isAmplifySpeedPressed(long window) {
        return glfwGetKey(window, amplifySpeedKey) == GLFW_PRESS;
    }
}
