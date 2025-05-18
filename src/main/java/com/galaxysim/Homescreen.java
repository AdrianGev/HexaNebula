package com.galaxysim;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
// GL11 is already imported via static import i think
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

// jeepers creepers this code is SO ORGANIZED like JINKIES dude, where's my trophy at fr

public class Homescreen {
    private long window;
    private final int WIDTH = 1280;
    private final int HEIGHT = 720;
    
    // background celestial objects
    private List<BackgroundStar> stars;
    private List<BackgroundStar> brightStars; // Separate list for brighter stars
    private List<BackgroundSun> suns;
    private Random random;
    
    // button properties
    private float playButtonX;
    private float playButtonY;
    private float playButtonWidth;
    private float playButtonHeight;
    private boolean playButtonHovered;
    
    // options button properties
    private float optionsButtonX;
    private float optionsButtonY;
    private float optionsButtonWidth;
    private float optionsButtonHeight;
    private boolean optionsButtonHovered;
    
    // options menu properties
    private boolean showOptionsMenu = false;
    
    // dispersion submenu properties
    private boolean showDispersionMenu = false;
    private float dispersionButtonX;
    private float dispersionButtonY;
    private float dispersionButtonWidth;
    private float dispersionButtonHeight;
    private boolean dispersionButtonHovered;
    
    // speed settings properties
    private boolean showSpeedMenu = false;
    private float speedButtonX;
    private float speedButtonY;
    private float speedButtonWidth;
    private float speedButtonHeight;
    private boolean speedButtonHovered;
    
    // Control settings menu
    private boolean showControlMenu = false;
    private float controlButtonX;
    private float controlButtonY;
    private float controlButtonWidth;
    private float controlButtonHeight;
    private boolean controlButtonHovered;
    private int currentRebindingKey = -1; // -1 means not rebinding any key
    
    // dispersion submenu tabs
    private enum DispersionTab { SUNS, STARS, SHOOTING_STARS, NEBULAE }
    private DispersionTab currentDispersionTab = DispersionTab.SUNS;
    
    // slider properties for dispersion settings
    private float dispersionSliderWidth = 200.0f;
    private float dispersionSliderHeight = 10.0f;
    private float[] dispersionSliderX = new float[10]; // x positions for multiple sliders
    private float[] dispersionSliderY = new float[10]; // y positions for multiple sliders
    private float[] dispersionKnobX = new float[10];   // knob positions for multiple sliders
    private boolean[] dispersionKnobDragging = new boolean[10]; // dragging state for each knob
    
    // volume control properties
    private float volumeSliderX;
    private float volumeSliderY;
    private float volumeSliderWidth;
    private float volumeSliderHeight;
    private float volumeKnobX;
    private float volumeKnobSize;
    private boolean volumeKnobDragging = false;
    
    // mouse state tracking
    private double mouseX;
    private double mouseY;
    private boolean mouseButtonDown = false;
    private boolean mouseButtonPressed = false;
    private long lastClickTime = 0;
    private boolean isDoubleClick = false;
    
    // text input for dispersion values
    private boolean isEditingValue = false;
    private int editingValueIndex = -1;
    private StringBuilder valueInputBuffer = new StringBuilder();
    private DispersionTab editingValueTab = null;
    
    // back button properties
    private float backButtonX;
    private float backButtonY;
    private float backButtonWidth;
    private float backButtonHeight;
    private boolean backButtonHovered = false;
    
    // font properties
    private int fontTexture;
    private STBTTBakedChar.Buffer charData;
    private final int BITMAP_WIDTH = 2048;  
    private final int BITMAP_HEIGHT = 2048; 
    private final int FIRST_CHAR = 32;
    private final int NUM_CHARS = 96;
    private float fontHeight = 42.0f;
    
    // audio properties
    private AudioPlayer audioPlayer;       
    
    // simple star for the background
    private class BackgroundStar {
        float x, y;
        float size;
        float brightness;
        boolean glimmers;
        boolean isGlimmering;
        float glimmerIntensity;
        long lastGlimmerTime;
        
        BackgroundStar(float x, float y, float size, float brightness) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.brightness = brightness;
            this.glimmers = random.nextFloat() < 0.3f; // 30% of stars can glimmer
            this.glimmerIntensity = random.nextFloat() * 0.5f + 1.5f; // 1.5x to 2.0x brighter when glimmering
            this.lastGlimmerTime = System.currentTimeMillis();
        }
        
        void update() {
            if (glimmers) {
                long currentTime = System.currentTimeMillis();
                // Randomly start glimmering
                if (!isGlimmering && random.nextFloat() < 0.005f && currentTime - lastGlimmerTime > 2000) {
                    isGlimmering = true;
                    lastGlimmerTime = currentTime;
                }
                // Stop glimmering after a short time
                else if (isGlimmering && currentTime - lastGlimmerTime > 300) {
                    isGlimmering = false;
                    lastGlimmerTime = currentTime;
                }
            }
        }
        
        float getCurrentBrightness() {
            return isGlimmering ? brightness * glimmerIntensity : brightness;
        }
    }
    
    private class BackgroundSun {
        float x, y;
        float size;
        float[] colors; // Array of colors for the gradient
        
        BackgroundSun(float x, float y, float size) {
            this.x = x;
            this.y = y;
            this.size = size;
            
            // Create a gradient of orange/red colors for the sun
            this.colors = new float[] {
                1.0f, 0.8f, 0.0f, // Bright yellow-orange
                1.0f, 0.6f, 0.0f, // Orange
                1.0f, 0.4f, 0.0f, // Dark orange
                0.9f, 0.2f, 0.0f  // Red-orange
            };
        }
    }
    

    
    public boolean show() {
        init();
        boolean startGame = loop();
        
        // clean up
        if (charData != null) {
            charData.free();
        }
        glDeleteTextures(fontTexture);
        
        // stop and clean up audio
        if (audioPlayer != null) {
            audioPlayer.cleanup();
        }
        
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        
        return startGame;
    }
    
    private void init() {
        // setup error callback
        GLFWErrorCallback.createPrint(System.err).set();
        
        // initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        
        // configure window
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        
        // create window
        window = glfwCreateWindow(WIDTH, HEIGHT, "HexaNebula", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }
        
        // center window on screen
        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidmode != null) {
            glfwSetWindowPos(
                window,
                (vidmode.width() - WIDTH) / 2,
                (vidmode.height() - HEIGHT) / 2
            );
        }
        
        // setup keyboard callbacks for value editing
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            handleKeyInput(key, scancode, action, mods);
        });
        
        // setup character callback for value editing
        glfwSetCharCallback(window, (window, codepoint) -> {
            handleCharInput(codepoint);
        });
        
        // setup mouse callbacks for button interaction and volume control
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            // Update mouse position
            mouseX = xpos;
            mouseY = ypos;
            
            // check if mouse is over play button
            playButtonHovered = xpos >= playButtonX && xpos <= playButtonX + playButtonWidth &&
                               ypos >= playButtonY && ypos <= playButtonY + playButtonHeight;
            
            // check if mouse is over options button
            optionsButtonHovered = xpos >= optionsButtonX && xpos <= optionsButtonX + optionsButtonWidth &&
                                  ypos >= optionsButtonY && ypos <= optionsButtonY + optionsButtonHeight;
            
            // check if mouse is over dispersion button when options menu is shown
            if (showOptionsMenu && !showDispersionMenu && !showSpeedMenu && !showControlMenu) {
                dispersionButtonHovered = xpos >= dispersionButtonX && xpos <= dispersionButtonX + dispersionButtonWidth &&
                                         ypos >= dispersionButtonY && ypos <= dispersionButtonY + dispersionButtonHeight;
                
                // check if mouse is over speed button
                speedButtonHovered = xpos >= speedButtonX && xpos <= speedButtonX + speedButtonWidth &&
                                     ypos >= speedButtonY && ypos <= speedButtonY + speedButtonHeight;
                
                // check if mouse is over control button
                controlButtonHovered = xpos >= controlButtonX && xpos <= controlButtonX + controlButtonWidth &&
                                      ypos >= controlButtonY && ypos <= controlButtonY + controlButtonHeight;
            }
            
            // check if mouse is over back button when options menu is shown
            if (showOptionsMenu) {
                backButtonHovered = xpos >= backButtonX && xpos <= backButtonX + backButtonWidth &&
                                   ypos >= backButtonY && ypos <= backButtonY + backButtonHeight;
            }
            
            // handle volume knob dragging
            if (volumeKnobDragging && showOptionsMenu) {
                // constrain knob position to slider bounds
                volumeKnobX = Math.max(volumeSliderX, Math.min(volumeSliderX + volumeSliderWidth, (float)xpos));
                
                // calculate and set volume based on knob position
                float volume = (volumeKnobX - volumeSliderX) / volumeSliderWidth;
                if (audioPlayer != null) {
                    audioPlayer.setVolume(volume);
                }
            }
        });
        
        // setup mouse button callback for clicking buttons and dragging volume knob
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    mouseButtonDown = true;
                    mouseButtonPressed = true;
                    
                    // Check for double click
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastClickTime < 300) { // 300ms threshold for double click
                        isDoubleClick = true;
                    } else {
                        isDoubleClick = false;
                    }
                    lastClickTime = currentTime;
                } else if (action == GLFW_RELEASE) {
                    mouseButtonDown = false;
                    mouseButtonPressed = false;
                }
                
                double[] xpos = new double[1];
                double[] ypos = new double[1];
                glfwGetCursorPos(window, xpos, ypos);
                
                if (action == GLFW_PRESS) {
                    // check if clicking on volume knob or slider
                    if (showOptionsMenu) {
                        float knobRadius = volumeKnobSize / 2;
                        float knobY = volumeSliderY + volumeSliderHeight / 2;
                        
                        // Check if we're in the control settings menu and handle key rebinding
                        if (showControlMenu) {
                            // Check for clicks on key binding buttons
                            float buttonHeight = 40;
                            float startY = HEIGHT / 3 - buttonHeight * 1.5f; // Shifted up by 1.5 button heights
                            float lineHeight = 60;
                            float buttonWidth = 300; // Increased from 200 to 300 to match the display width
                            float buttonX = WIDTH * 3 / 4 - buttonWidth / 2;
                            
                            // Check each key binding button
                            for (int i = 0; i < 7; i++) {
                                float buttonY = startY + lineHeight * i;
                                if (xpos[0] >= buttonX && xpos[0] <= buttonX + buttonWidth &&
                                    ypos[0] >= buttonY && ypos[0] <= buttonY + buttonHeight) {
                                    // Start rebinding this key
                                    currentRebindingKey = i;
                                    return; // Exit the callback to prevent other actions
                                }
                            }
                        }
                        
                        // Check if clicking directly on the knob
                        if (Math.abs(xpos[0] - volumeKnobX) <= knobRadius && 
                            Math.abs(ypos[0] - knobY) <= knobRadius) {
                            volumeKnobDragging = true;
                        }
                        // Check if clicking anywhere on the slider
                        else if (xpos[0] >= volumeSliderX && xpos[0] <= volumeSliderX + volumeSliderWidth &&
                                 Math.abs(ypos[0] - knobY) <= volumeSliderHeight * 2) {
                            // Move the knob to the clicked position
                            volumeKnobX = (float) xpos[0];
                            // Constrain to slider bounds
                            volumeKnobX = Math.max(volumeSliderX, Math.min(volumeSliderX + volumeSliderWidth, volumeKnobX));
                            // Update volume
                            if (audioPlayer != null) {
                                float volume = (volumeKnobX - volumeSliderX) / volumeSliderWidth;
                                audioPlayer.setVolume(volume);
                            }
                            // Start dragging
                            volumeKnobDragging = true;
                        }
                    }
                } else if (action == GLFW_RELEASE) {
                    // stop dragging volume knob
                    volumeKnobDragging = false;
                }
            }
        });
        
        // make opengl context current
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // enable v-sync
        glfwShowWindow(window);
        
        // initialize opengl
        GL.createCapabilities();
        
        // initialize font
        initFont();
        
        // initialize and play background music
        initAudio();
        
        // initialize random number generator
        random = new Random();
        
        // Initialize regular stars - make them more sparse
        stars = new ArrayList<>();
        for (int i = 0; i < 120; i++) { // Reduced from 200 to 120 stars
            stars.add(new BackgroundStar(
                random.nextFloat() * WIDTH,
                random.nextFloat() * HEIGHT,
                random.nextFloat() * 2.0f + 1.0f,
                random.nextFloat() * 0.5f + 0.5f
            ));
        }
        
        // Add scattered bright stars
        brightStars = new ArrayList<>();
        int numBrightStars = 30 + random.nextInt(20); // 30-50 bright stars
        for (int i = 0; i < numBrightStars; i++) {
            // Scattered randomly across the screen
            float x = random.nextFloat() * WIDTH;
            float y = random.nextFloat() * HEIGHT;
            
            // Larger size and higher brightness
            float size = random.nextFloat() * 3.0f + 2.0f; // 2.0-5.0 (larger than regular stars)
            float brightness = random.nextFloat() * 0.2f + 0.8f; // 0.8-1.0 (brighter than regular stars)
            
            brightStars.add(new BackgroundStar(x, y, size, brightness));
        }
        
        // Add suns positioned at the extreme edges of the screen
        suns = new ArrayList<>();
        
        // Always create exactly 2 suns
        
        // First sun - far left edge of screen
        float leftSunX = WIDTH * 0.1f + random.nextFloat() * WIDTH * 0.05f; // 10-15% from left
        float leftSunY = HEIGHT * 0.3f + random.nextFloat() * HEIGHT * 0.4f; // 30-70% from top
        float leftSunSize = random.nextFloat() * 60 + 100; // 100-160 pixel radius (slightly larger)
        suns.add(new BackgroundSun(leftSunX, leftSunY, leftSunSize));
        
        // Second sun - far right edge of screen
        float rightSunX = WIDTH * 0.9f - random.nextFloat() * WIDTH * 0.05f; // 85-90% from left
        float rightSunY = HEIGHT * 0.3f + random.nextFloat() * HEIGHT * 0.4f; // 30-70% from top
        float rightSunSize = random.nextFloat() * 60 + 100; // 100-160 pixel radius (slightly larger)
        suns.add(new BackgroundSun(rightSunX, rightSunY, rightSunSize));
        
        // initialize button position and size
        playButtonWidth = 300;
        playButtonHeight = 80;
        playButtonX = (WIDTH - playButtonWidth) / 2;
        playButtonY = HEIGHT / 2 + 50;
        
        // initialize options button position and size
        optionsButtonWidth = 300;
        optionsButtonHeight = 60;
        optionsButtonX = (WIDTH - optionsButtonWidth) / 2;
        optionsButtonY = playButtonY + playButtonHeight + 20; // position below play button
        
        // initialize volume slider properties
        volumeSliderWidth = 200;
        volumeSliderHeight = 10;
        // position the slider in drawVolumeControls() after calculating the text width
        volumeKnobSize = 20;
        // set initial knob position based on default volume (will be adjusted in drawVolumeControls)
        if (audioPlayer != null) {
            volumeKnobX = volumeSliderWidth * audioPlayer.getVolume();
        } else {
            volumeKnobX = volumeSliderWidth * 0.7f; // default 70%
        }
        
        // initialize back button position and size
        backButtonWidth = 150;
        backButtonHeight = 50;
        backButtonX = WIDTH - backButtonWidth - 20; // position in bottom right
        backButtonY = HEIGHT - backButtonHeight - 20;
        
        // initialize dispersion button position and size
        dispersionButtonWidth = 550; // Increased width to fit text better
        dispersionButtonHeight = 50;
        dispersionButtonX = (WIDTH - dispersionButtonWidth) / 2;
        dispersionButtonY = HEIGHT * 0.55f; // Position at 55% of screen height
        
        // initialize speed settings button position and size
        speedButtonWidth = 550; // Same width as dispersion button
        speedButtonHeight = 50;
        speedButtonX = (WIDTH - speedButtonWidth) / 2;
        speedButtonY = dispersionButtonY + dispersionButtonHeight + 20; // Position below dispersion button
        
        // initialize control settings button position and size
        controlButtonWidth = 550; // Same width as other buttons
        controlButtonHeight = 50;
        controlButtonX = (WIDTH - controlButtonWidth) / 2;
        controlButtonY = speedButtonY + speedButtonHeight + 20; // Position below speed button with more spacing
    }
    
    /**
     * initializes the audio player and starts playing the titlescreen music
     */
    private void initAudio() {
        // create and initialize audio player
        audioPlayer = new AudioPlayer();
        audioPlayer.init();
        
        // play the titlescreen music on loop
        String musicPath = "ost/HexaNebula_Titlescreen.wav";
        boolean success = audioPlayer.playWavFile(musicPath);
        
        if (!success) {
            System.err.println("failed to play titlescreen music: " + musicPath);
        }
    }
    
    private void initFont() {
        // load font file using relative path for portability
        ByteBuffer fontData = null;
        try {
            // Use a relative path that works regardless of where the application is run from
            Path fontPath = Paths.get("fonts/SpaceNova-6Rpd1.otf").toAbsolutePath();
            
            // If the font file doesn't exist at the relative path, try to find it in the classpath
            if (!Files.exists(fontPath)) {
                try {
                    // Try to load from classpath resources
                    URL fontUrl = Homescreen.class.getClassLoader().getResource("fonts/SpaceNova-6Rpd1.otf");
                    if (fontUrl != null) {
                        fontPath = Paths.get(fontUrl.toURI());
                    } else { // wow adrian is so good at error handling
                        System.err.println("Font file not found in path or resources: " + fontPath);
                        throw new IOException("Font file not found");
                    }
                } catch (URISyntaxException e) {
                    System.err.println("Invalid URI for font resource: " + e.getMessage());
                    throw new IOException("Font resource URI error", e);
                }
            }
            
            System.out.println("Loading font from: " + fontPath);
            FileChannel fc = FileChannel.open(fontPath, StandardOpenOption.READ);
            fontData = BufferUtils.createByteBuffer((int) fc.size());
            fc.read(fontData);
            fc.close();
            fontData.flip();
        } catch (IOException e) {
            System.err.println("Failed to load font file: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        fontTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        
        // use mipmapping and better filtering for smoother text and stuff
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        
        // enable anisotropic filtering if available
        // (big words are scary but make sense eventually)
        /*
         * Anisotropic filtering is a technique used to improve the quality
         * of textures applied to the surfaces of 3D objects when drawn at a sharp angle.
         * Enabling this option improves image quality at the expense of some performance.
         * - nvidia
         * 
         * high key accurate wording
         */
        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            // Using the correct constants for anisotropic filtering
            float maxAniso = glGetFloat(0x84FF); // GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
            glTexParameterf(GL_TEXTURE_2D, 0x84FE, maxAniso); // GL_TEXTURE_MAX_ANISOTROPY_EXT
        }
        
        // allocate texture bitmap
        ByteBuffer bitmap = BufferUtils.createByteBuffer(BITMAP_WIDTH * BITMAP_HEIGHT);
        
        // create STB font char data buffer
        charData = STBTTBakedChar.malloc(NUM_CHARS);
        
        // bake font to bitmap
        STBTruetype.stbtt_BakeFontBitmap(
            fontData,
            fontHeight,
            bitmap,
            BITMAP_WIDTH,
            BITMAP_HEIGHT,
            FIRST_CHAR,
            charData
        );
        
        // Upload bitmap to texture and generate mipmaps
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_WIDTH, BITMAP_HEIGHT, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
    }
    
    private boolean loop() {
        boolean startGame = false;
        
        // Set clear color to dark blue for space theme
        glClearColor(0.0f, 0.02f, 0.05f, 0.0f);
        
        while (!glfwWindowShouldClose(window) && !startGame) {
            glClear(GL_COLOR_BUFFER_BIT);
            
            // Set up orthographic projection for 2D rendering
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            
            // draw background stars
            drawStars();
            
            // if options menu is shown, draw it
            if (showOptionsMenu) {
                drawOptionsMenu();
            } else {
                // draw title and main menu buttons only on the main screen
                drawTitle();
                drawPlayButton();
                drawOptionsButton();
            }
            
            // check for button clicks
            if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
                // Handle options menu first if it's visible
                if (showOptionsMenu) {
                    if (backButtonHovered) {
                        // if in dispersion submenu, speed menu, or control menu, go back to main options menu
                        if (showDispersionMenu) {
                            showDispersionMenu = false;
                        } else if (showSpeedMenu) {
                            showSpeedMenu = false;
                        } else if (showControlMenu) {
                            showControlMenu = false;
                            currentRebindingKey = -1; // Cancel any ongoing rebinding
                        } else {
                            // hide options menu when back button is clicked
                            showOptionsMenu = false;
                        }
                        // add a small delay to prevent multiple toggles
                        try { Thread.sleep(200); } catch (InterruptedException e) {}
                    } else if (!showDispersionMenu && !showSpeedMenu && !showControlMenu) {
                        // Only check these buttons when in the main options menu
                        if (dispersionButtonHovered) {
                            // show dispersion submenu
                            showDispersionMenu = true;
                        } else if (speedButtonHovered) {
                            // show speed settings menu
                            showSpeedMenu = true;
                        } else if (controlButtonHovered) {
                            // show control settings menu
                            showControlMenu = true;
                        }
                    }
                } else {
                    // Main menu buttons
                    if (playButtonHovered) {
                        startGame = true;
                    } else if (optionsButtonHovered) {
                        // show options menu
                        showOptionsMenu = true;
                        // add a small delay to prevent multiple toggles
                        try { Thread.sleep(200); } catch (InterruptedException e) {}
                    }
                }
            }
            
            // check for ESC key to exit
            if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
                glfwSetWindowShouldClose(window, true);
            }
            
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        
        return startGame;
    }
    
    private void drawStars() {
        // Update stars (for glimmering effect)
        for (BackgroundStar star : stars) {
            star.update();
        }
        
        // Update bright stars (for glimmering effect)
        for (BackgroundStar star : brightStars) {
            star.update();
        }
        
        // Enable blending for the sun glow effects
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw suns
        for (BackgroundSun sun : suns) {
            drawSun(sun);
        }
        
        // Draw regular stars
        glEnable(GL_POINT_SMOOTH); // Make stars round
        glBegin(GL_POINTS);
        for (BackgroundStar star : stars) {
            // Use the star size for point size
            glPointSize(star.size);
            // Use current brightness (may be glimmering)
            float brightness = star.getCurrentBrightness();
            glColor4f(brightness, brightness, brightness, 1.0f);
            glVertex2f(star.x, star.y);
        }
        glEnd();
        
        // Draw bright stars (larger and brighter)
        glBegin(GL_POINTS);
        for (BackgroundStar star : brightStars) {
            // Use the star size for point size
            glPointSize(star.size);
            // Use current brightness (may be glimmering)
            float brightness = star.getCurrentBrightness();
            // Add a slight blue tint to bright stars
            glColor4f(brightness, brightness, Math.min(1.0f, brightness * 1.1f), 1.0f);
            glVertex2f(star.x, star.y);
        }
        glEnd();
        
        glDisable(GL_POINT_SMOOTH);
        glDisable(GL_BLEND);
    }
    

    
    private void drawSun(BackgroundSun sun) {
        // Draw the sun as a series of concentric circles with gradient colors
        int numRings = 8;
        float ringStep = sun.size / numRings;
        
        for (int i = numRings - 1; i >= 0; i--) {
            float radius = ringStep * (i + 1);
            float colorIndex = (float)i / (numRings - 1) * 3; // Map to color array indices
            int baseIndex = (int)colorIndex;
            float blend = colorIndex - baseIndex;
            
            // Blend between two colors in the gradient
            float r, g, b;
            if (baseIndex < 3) {
                r = sun.colors[baseIndex * 3] * (1 - blend) + sun.colors[(baseIndex + 1) * 3] * blend;
                g = sun.colors[baseIndex * 3 + 1] * (1 - blend) + sun.colors[(baseIndex + 1) * 3 + 1] * blend;
                b = sun.colors[baseIndex * 3 + 2] * (1 - blend) + sun.colors[(baseIndex + 1) * 3 + 2] * blend;
            } else {
                r = sun.colors[9];
                g = sun.colors[10];
                b = sun.colors[11];
            }
            
            // Add a slight pulsating effect to the outer rings
            float pulse = 1.0f;
            if (i > numRings / 2) {
                pulse = 0.8f + 0.2f * (float)Math.sin(System.currentTimeMillis() / 500.0 + i);
            }
            
            glColor4f(r * pulse, g * pulse, b * pulse, 0.7f);
            
            // Draw a filled circle
            glBegin(GL_TRIANGLE_FAN);
            glVertex2f(sun.x, sun.y); // Center
            int segments = 36;
            for (int j = 0; j <= segments; j++) {
                float angle = (float)(j * 2 * Math.PI / segments);
                float x = sun.x + (float)Math.cos(angle) * radius;
                float y = sun.y + (float)Math.sin(angle) * radius;
                glVertex2f(x, y);
            }
            glEnd();
        }
    }
    
    private void drawTitle() {
        // draw "HexaNebula" title
        // adding somewhat useless comments is better than no comments is my new philosophy
        String title = "HexaNebula";
        
        // calculate title width for centering
        float titleWidth = getTextWidth(title, fontHeight * 2.0f);
        
        // set title position
        float titleX = (WIDTH - titleWidth) / 2;
        float titleY = HEIGHT / 3;
        
        // draw with a blue glow effect
        // multiple glow layers for a better effect
        glColor4f(0.1f, 0.3f, 0.8f, 0.2f);
        renderTTFText(title, titleX + 4, titleY + 4, fontHeight * 2.0f);
        
        glColor4f(0.2f, 0.4f, 0.9f, 0.3f);
        renderTTFText(title, titleX + 3, titleY + 3, fontHeight * 2.0f);
        
        glColor4f(0.3f, 0.5f, 1.0f, 0.4f);
        renderTTFText(title, titleX + 2, titleY + 2, fontHeight * 2.0f);
        
        // draw white text with slight blue tint for a space theme
        glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
        renderTTFText(title, titleX, titleY, fontHeight * 2.0f);
    }
    
    private void drawPlayButton() {
        // draw button background
        if (playButtonHovered) {
            // bright blue when hovered
            glColor4f(0.2f, 0.6f, 0.9f, 0.8f);
        } else {
            // darker blue normally
            glColor4f(0.1f, 0.3f, 0.7f, 0.7f);
        }
        
        glBegin(GL_QUADS);
        glVertex2f(playButtonX, playButtonY);
        glVertex2f(playButtonX + playButtonWidth, playButtonY);
        glVertex2f(playButtonX + playButtonWidth, playButtonY + playButtonHeight);
        glVertex2f(playButtonX, playButtonY + playButtonHeight);
        glEnd();
        
        // draw button border
        glColor4f(0.4f, 0.7f, 1.0f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(playButtonX, playButtonY);
        glVertex2f(playButtonX + playButtonWidth, playButtonY);
        glVertex2f(playButtonX + playButtonWidth, playButtonY + playButtonHeight);
        glVertex2f(playButtonX, playButtonY + playButtonHeight);
        glEnd();
        
        // draw "PLAY" text
        String buttonText = "PLAY";
        float buttonFontSize = fontHeight * 1.2f; // smaller font size for button
        float textWidth = getTextWidth(buttonText, buttonFontSize);
        float textX = playButtonX + (playButtonWidth - textWidth) / 2;
        
        // adjust vertical position to center text properly
        // the y-coordinate in opengl starts from the top of the character
        // we need to account for the baseline offset
        float textY = playButtonY + (playButtonHeight - buttonFontSize) / 2 + buttonFontSize * 0.7f;
        
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        renderTTFText(buttonText, textX, textY, buttonFontSize);
    }
    
    /**
     * draws the options button
     */
    private void drawOptionsButton() {
        // draw button background
        if (optionsButtonHovered) {
            // bright blue when hovered
            glColor4f(0.2f, 0.5f, 0.8f, 0.8f);
        } else {
            // darker blue normally
            glColor4f(0.1f, 0.2f, 0.6f, 0.7f);
        }
        
        glBegin(GL_QUADS);
        glVertex2f(optionsButtonX, optionsButtonY);
        glVertex2f(optionsButtonX + optionsButtonWidth, optionsButtonY);
        glVertex2f(optionsButtonX + optionsButtonWidth, optionsButtonY + optionsButtonHeight);
        glVertex2f(optionsButtonX, optionsButtonY + optionsButtonHeight);
        glEnd();
        
        // draw button border
        glColor4f(0.3f, 0.6f, 0.9f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(optionsButtonX, optionsButtonY);
        glVertex2f(optionsButtonX + optionsButtonWidth, optionsButtonY);
        glVertex2f(optionsButtonX + optionsButtonWidth, optionsButtonY + optionsButtonHeight);
        glVertex2f(optionsButtonX, optionsButtonY + optionsButtonHeight);
        glEnd();
        
        // draw "options" text
        String buttonText = "OPTIONS";
        float buttonFontSize = fontHeight * 0.9f; // smaller font size for options button
        float textWidth = getTextWidth(buttonText, buttonFontSize);
        float textX = optionsButtonX + (optionsButtonWidth - textWidth) / 2;
        
        // adjust vertical position to center text properly
        float textY = optionsButtonY + (optionsButtonHeight - buttonFontSize) / 2 + buttonFontSize * 0.7f;
        
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        renderTTFText(buttonText, textX, textY, buttonFontSize);
    }
    
    /**
     * draws the options menu with volume controls and back button
     */
    private void drawOptionsMenu() {
        // draw semi-transparent background overlay that lets stars be visible
        glColor4f(0.0f, 0.05f, 0.1f, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(WIDTH, 0);
        glVertex2f(WIDTH, HEIGHT);
        glVertex2f(0, HEIGHT);
        glEnd();
        
        if (showDispersionMenu) {
            // Draw dispersion submenu
            drawDispersionMenu();
        } else if (showSpeedMenu) {
            // Draw speed settings menu
            drawSpeedSettingsMenu();
        } else if (showControlMenu) {
            // Draw control settings menu
            drawControlSettingsMenu();
        } else {
            // draw options title
            String optionsTitle = "OPTIONS";
            float titleFontSize = fontHeight * 1.5f;
            float titleWidth = getTextWidth(optionsTitle, titleFontSize);
            float titleX = (WIDTH - titleWidth) / 2;
            float titleY = HEIGHT / 6;
            
            glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
            renderTTFText(optionsTitle, titleX, titleY, titleFontSize);
            
            // draw volume controls
            drawVolumeControls();
            
            // draw dispersion button
            drawDispersionButton();
            
            // draw speed settings button
            drawSpeedButton();
            
            // draw control settings button
            drawControlButton();
        }
        
        // draw back button
        drawBackButton();
    }
    
    /**
     * draws the back button
     */
    private void drawBackButton() {
        // draw button background
        if (backButtonHovered) {
            // bright red when hovered
            glColor4f(0.8f, 0.3f, 0.3f, 0.8f);
        } else {
            // darker red normally
            glColor4f(0.6f, 0.2f, 0.2f, 0.7f);
        }
        
        glBegin(GL_QUADS);
        glVertex2f(backButtonX, backButtonY);
        glVertex2f(backButtonX + backButtonWidth, backButtonY);
        glVertex2f(backButtonX + backButtonWidth, backButtonY + backButtonHeight);
        glVertex2f(backButtonX, backButtonY + backButtonHeight);
        glEnd();
        
        // draw button border
        glColor4f(0.9f, 0.4f, 0.4f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(backButtonX, backButtonY);
        glVertex2f(backButtonX + backButtonWidth, backButtonY);
        glVertex2f(backButtonX + backButtonWidth, backButtonY + backButtonHeight);
        glVertex2f(backButtonX, backButtonY + backButtonHeight);
        glEnd();
        
        // draw "back" text
        String buttonText = "BACK";
        float buttonFontSize = fontHeight * 1.0f;
        float textWidth = getTextWidth(buttonText, buttonFontSize);
        float textX = backButtonX + (backButtonWidth - textWidth) / 2;
        float textY = backButtonY + (backButtonHeight - buttonFontSize) / 2 + buttonFontSize * 0.7f;
        
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        renderTTFText(buttonText, textX, textY, buttonFontSize);
    }
    
    /**
     * Draws the speed settings button
     */
    private void drawSpeedButton() {
        // draw button background
        if (speedButtonHovered) {
            // bright blue when hovered
            glColor4f(0.3f, 0.5f, 0.8f, 0.8f);
        } else {
            // darker blue normally
            glColor4f(0.2f, 0.3f, 0.6f, 0.7f);
        }
        
        glBegin(GL_QUADS);
        glVertex2f(speedButtonX, speedButtonY);
        glVertex2f(speedButtonX + speedButtonWidth, speedButtonY);
        glVertex2f(speedButtonX + speedButtonWidth, speedButtonY + speedButtonHeight);
        glVertex2f(speedButtonX, speedButtonY + speedButtonHeight);
        glEnd();
        
        // draw button border
        glColor4f(0.4f, 0.6f, 0.9f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(speedButtonX, speedButtonY);
        glVertex2f(speedButtonX + speedButtonWidth, speedButtonY);
        glVertex2f(speedButtonX + speedButtonWidth, speedButtonY + speedButtonHeight);
        glVertex2f(speedButtonX, speedButtonY + speedButtonHeight);
        glEnd();
        
        // draw "Speed Settings" text
        String buttonText = "SPEED SETTINGS";
        float buttonFontSize = fontHeight * 0.9f;
        float textWidth = getTextWidth(buttonText, buttonFontSize);
        float textX = speedButtonX + (speedButtonWidth - textWidth) / 2;
        float textY = speedButtonY + (speedButtonHeight - buttonFontSize) / 2 + buttonFontSize * 0.7f;
        
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        renderTTFText(buttonText, textX, textY, buttonFontSize);
    }
    
    /**
     * Draws the control settings button
     */
    private void drawControlButton() {
        // draw button background
        if (controlButtonHovered) {
            // bright green when hovered
            glColor4f(0.3f, 0.7f, 0.4f, 0.8f);
        } else {
            // darker green normally
            glColor4f(0.2f, 0.5f, 0.3f, 0.7f);
        }
        
        glBegin(GL_QUADS);
        glVertex2f(controlButtonX, controlButtonY);
        glVertex2f(controlButtonX + controlButtonWidth, controlButtonY);
        glVertex2f(controlButtonX + controlButtonWidth, controlButtonY + controlButtonHeight);
        glVertex2f(controlButtonX, controlButtonY + controlButtonHeight);
        glEnd();
        
        // draw button border
        glColor4f(0.5f, 0.9f, 0.6f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(controlButtonX, controlButtonY);
        glVertex2f(controlButtonX + controlButtonWidth, controlButtonY);
        glVertex2f(controlButtonX + controlButtonWidth, controlButtonY + controlButtonHeight);
        glVertex2f(controlButtonX, controlButtonY + controlButtonHeight);
        glEnd();
        
        // draw "Control Settings" text
        String buttonText = "CONTROL SETTINGS";
        float buttonFontSize = fontHeight * 1.0f;
        float textWidth = getTextWidth(buttonText, buttonFontSize);
        float textX = controlButtonX + (controlButtonWidth - textWidth) / 2;
        float textY = controlButtonY + (controlButtonHeight - buttonFontSize) / 2 + buttonFontSize * 0.7f;
        
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        renderTTFText(buttonText, textX, textY, buttonFontSize);
    }
    
    /**
     * Draws the control settings menu with key binding options
     */
    private void drawControlSettingsMenu() {
        // draw control settings title
        String title = "CONTROL SETTINGS";
        float titleFontSize = fontHeight * 1.5f;
        float titleWidth = getTextWidth(title, titleFontSize);
        float titleX = (WIDTH - titleWidth) / 2;
        float titleY = HEIGHT / 6;
        
        glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
        renderTTFText(title, titleX, titleY, titleFontSize);
        
        // Draw key binding options
        float buttonHeight = 40;
        float startY = HEIGHT / 3 - buttonHeight * 1.5f; // Shifted up by 1.5 button heights
        float lineHeight = 60;
        float fontSize = fontHeight * 0.9f;
        float buttonWidth = 300; // Increased from 200 to 300 for even better display of key names
        float labelX = WIDTH / 4;
        float buttonX = WIDTH * 3 / 4 - buttonWidth / 2;
        
        // Draw instructions at the bottom of the screen
        String instructions = "Click on a key to rebind it";
        if (currentRebindingKey >= 0) {
            instructions = "Press any key to set the binding...";
        }
        float instructionsWidth = getTextWidth(instructions, fontSize);
        float instructionsX = (WIDTH - instructionsWidth) / 2;
        float instructionsY = HEIGHT - 100; // Position near the bottom of the screen
        
        glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
        renderTTFText(instructions, instructionsX, instructionsY, fontSize);
        
        // Forward key
        drawKeyBindingOption("Move Forward", ControlSettings.getKeyName(ControlSettings.getMoveForwardKey()), 
                           labelX, buttonX, startY, buttonWidth, buttonHeight, fontSize, 0);
        
        // Backward key
        drawKeyBindingOption("Move Backward", ControlSettings.getKeyName(ControlSettings.getMoveBackwardKey()), 
                           labelX, buttonX, startY + lineHeight, buttonWidth, buttonHeight, fontSize, 1);
        
        // Left key
        drawKeyBindingOption("Move Left", ControlSettings.getKeyName(ControlSettings.getMoveLeftKey()), 
                           labelX, buttonX, startY + lineHeight * 2, buttonWidth, buttonHeight, fontSize, 2);
        
        // Right key
        drawKeyBindingOption("Move Right", ControlSettings.getKeyName(ControlSettings.getMoveRightKey()), 
                           labelX, buttonX, startY + lineHeight * 3, buttonWidth, buttonHeight, fontSize, 3);
        
        // Up key
        drawKeyBindingOption("Move Up", ControlSettings.getKeyName(ControlSettings.getMoveUpKey()), 
                           labelX, buttonX, startY + lineHeight * 4, buttonWidth, buttonHeight, fontSize, 4);
        
        // Down key
        drawKeyBindingOption("Move Down", ControlSettings.getKeyName(ControlSettings.getMoveDownKey()), 
                           labelX, buttonX, startY + lineHeight * 5, buttonWidth, buttonHeight, fontSize, 5);
        
        // Speed amplification key
        drawKeyBindingOption("Amplify Speed", ControlSettings.getKeyName(ControlSettings.getAmplifySpeedKey()), 
                           labelX, buttonX, startY + lineHeight * 6, buttonWidth, buttonHeight, fontSize, 6);
    }
    
    /**
     * Draws a key binding option with label and button
     */
    private void drawKeyBindingOption(String label, String keyName, float labelX, float buttonX, float y, 
                                    float buttonWidth, float buttonHeight, float fontSize, int keyIndex) {
        // Draw label - vertically centered with the button
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        float labelY = y + buttonHeight/2 + fontSize/3; // Adjusted to better align with button
        renderTTFText(label, labelX, labelY, fontSize);
        
        // Draw button background
        boolean isSelected = (currentRebindingKey == keyIndex);
        if (isSelected) {
            // Highlight when selected for rebinding
            glColor4f(0.8f, 0.8f, 0.3f, 0.8f);
        } else if (isMouseOverKeyBindingButton(buttonX, y, buttonWidth, buttonHeight)) {
            // Highlight when hovered
            glColor4f(0.4f, 0.6f, 0.8f, 0.8f);
        } else {
            // Normal color
            glColor4f(0.3f, 0.4f, 0.6f, 0.7f);
        }
        
        glBegin(GL_QUADS);
        glVertex2f(buttonX, y);
        glVertex2f(buttonX + buttonWidth, y);
        glVertex2f(buttonX + buttonWidth, y + buttonHeight);
        glVertex2f(buttonX, y + buttonHeight);
        glEnd();
        
        // Draw button border
        glColor4f(0.6f, 0.8f, 1.0f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(buttonX, y);
        glVertex2f(buttonX + buttonWidth, y);
        glVertex2f(buttonX + buttonWidth, y + buttonHeight);
        glVertex2f(buttonX, y + buttonHeight);
        glEnd();
        
        // Draw key name
        float keyNameWidth = getTextWidth(keyName, fontSize);
        float textX = buttonX + (buttonWidth - keyNameWidth) / 2;
        float textY = y + (buttonHeight - fontSize) / 2 + fontSize * 0.7f;
        
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        renderTTFText(keyName, textX, textY, fontSize);
    }
    
    /**
     * Check if mouse is over a key binding button
     */
    private boolean isMouseOverKeyBindingButton(float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    
    /**
     * Draws the speed settings menu with sliders for default and amplified speed
     */
    private void drawSpeedSettingsMenu() {
        // draw speed settings title
        String speedTitle = "SPEED SETTINGS";
        float titleFontSize = fontHeight * 1.5f;
        float titleWidth = getTextWidth(speedTitle, titleFontSize);
        float titleX = (WIDTH - titleWidth) / 2;
        float titleY = HEIGHT / 6;
        
        glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
        renderTTFText(speedTitle, titleX, titleY, titleFontSize);
        
        float startY = HEIGHT / 6 + 120;
        float spacing = 50;
        
        // Draw sliders for speed settings
        drawSpeedSlider(0, "Default Speed:", SpeedSettings.getDefaultSpeed(), 0.5f, 10.0f, startY);
        drawSpeedSlider(1, "Amplified Speed Multiplier:", SpeedSettings.getAmplifiedSpeedMultiplier(), 1.5f, 20.0f, startY + spacing);
        
        // Draw information about current speeds
        float infoY = startY + spacing * 3;
        float infoFontSize = fontHeight * 0.7f;
        
        glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
        String infoText = "Current Speeds:";
        renderTTFText(infoText, (WIDTH - getTextWidth(infoText, infoFontSize)) / 2, infoY, infoFontSize);
        
        // Show calculated speeds
        String defaultSpeedText = String.format("Default: %.2f units/frame", SpeedSettings.getDefaultSpeed());
        String amplifiedSpeedText = String.format("Amplified (CTRL): %.2f units/frame", SpeedSettings.getAmplifiedSpeed());
        
        infoY += spacing * 0.7f;
        renderTTFText(defaultSpeedText, (WIDTH - getTextWidth(defaultSpeedText, infoFontSize)) / 2, infoY, infoFontSize);
        
        infoY += spacing * 0.7f;
        renderTTFText(amplifiedSpeedText, (WIDTH - getTextWidth(amplifiedSpeedText, infoFontSize)) / 2, infoY, infoFontSize);
        
        // Add note about controls
        infoY += spacing * 1.2f;
        String controlsNote = "Hold CTRL while moving to use amplified speed";
        float noteSize = fontHeight * 0.6f;
        glColor4f(0.7f, 0.8f, 1.0f, 0.8f);
        renderTTFText(controlsNote, (WIDTH - getTextWidth(controlsNote, noteSize)) / 2, infoY, noteSize);
        
        // Instructions for value editing
        String instructions1 = "Double-click on a value to edit it directly.";
        float instructionsY1 = infoY + spacing * 1.2f;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions1, (WIDTH - getTextWidth(instructions1, fontHeight * 0.6f)) / 2, instructionsY1, fontHeight * 0.6f);
        
        // Enter/Return instruction
        String instructions2 = "Press Enter/Return to set the value.";
        float instructionsY2 = instructionsY1 + fontHeight * 0.8f;
        renderTTFText(instructions2, (WIDTH - getTextWidth(instructions2, fontHeight * 0.6f)) / 2, instructionsY2, fontHeight * 0.6f);
    }
    
    /**
     * Draws a slider for speed settings
     */
    private void drawSpeedSlider(int index, String label, float value, float min, float max, float y) {
        float labelFontSize = fontHeight * 0.7f;
        float labelWidth = getTextWidth(label, labelFontSize);
        float totalWidth = WIDTH * 0.7f;
        float startX = (WIDTH - totalWidth) / 2;
        
        // Store slider position for interaction
        dispersionSliderX[index] = startX + labelWidth + 20;
        dispersionSliderY[index] = y;
        
        // Draw label
        glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
        renderTTFText(label, startX, y + labelFontSize/2, labelFontSize);
        
        // Draw slider track
        glColor4f(0.3f, 0.3f, 0.3f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(dispersionSliderX[index], dispersionSliderY[index]);
        glVertex2f(dispersionSliderX[index] + dispersionSliderWidth, dispersionSliderY[index]);
        glVertex2f(dispersionSliderX[index] + dispersionSliderWidth, dispersionSliderY[index] + dispersionSliderHeight);
        glVertex2f(dispersionSliderX[index], dispersionSliderY[index] + dispersionSliderHeight);
        glEnd();
        
        // Calculate normalized value and knob position
        float normalizedValue = (value - min) / (max - min);
        dispersionKnobX[index] = dispersionSliderX[index] + normalizedValue * dispersionSliderWidth;
        
        // Draw knob
        float knobSize = 15.0f;
        // Calculate the vertical center of the slider for the knob
        float sliderCenterY = dispersionSliderY[index] + dispersionSliderHeight/2;
        
        glColor4f(0.7f, 0.8f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(dispersionKnobX[index] - knobSize/2, sliderCenterY - knobSize/2);
        glVertex2f(dispersionKnobX[index] + knobSize/2, sliderCenterY - knobSize/2);
        glVertex2f(dispersionKnobX[index] + knobSize/2, sliderCenterY + knobSize/2);
        glVertex2f(dispersionKnobX[index] - knobSize/2, sliderCenterY + knobSize/2);
        glEnd();
        
        // Draw value text or text input field
        float valueX = dispersionSliderX[index] + dispersionSliderWidth + 10;
        
        if (isEditingValue && editingValueIndex == index && showSpeedMenu) {
            // Draw text input field with cursor
            String inputText = valueInputBuffer.toString();
            
            // Draw input field background
            glColor4f(0.1f, 0.1f, 0.2f, 0.8f);
            float inputWidth = Math.max(80.0f, getTextWidth(inputText, labelFontSize) + 20);
            float inputHeight = labelFontSize * 1.2f; // Reduced height
            
            // Position the input box higher to better align with the value text
            float inputY = y - inputHeight/2;
            
            glBegin(GL_QUADS);
            glVertex2f(valueX, inputY);
            glVertex2f(valueX + inputWidth, inputY);
            glVertex2f(valueX + inputWidth, inputY + inputHeight);
            glVertex2f(valueX, inputY + inputHeight);
            glEnd();
            
            // Draw input text
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            renderTTFText(inputText, valueX + 5, y + labelFontSize/2, labelFontSize);
            
            // Draw cursor
            if ((System.currentTimeMillis() / 500) % 2 == 0) { // Blinking cursor
                float cursorX = valueX + 5 + getTextWidth(inputText, labelFontSize);
                glBegin(GL_LINES);
                glVertex2f(cursorX, inputY + 2);
                glVertex2f(cursorX, inputY + inputHeight - 2);
                glEnd();
            }
        } else {
            // Draw normal value text
            String valueText = String.format("%.2f", value);
            
            glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
            renderTTFText(valueText, valueX, y + labelFontSize/2, labelFontSize);
            
            // Check for double click on value text
            if (isDoubleClick && mouseButtonPressed) {
                float textWidth = getTextWidth(valueText, labelFontSize);
                float clickableHeight = labelFontSize * 1.2f;
                float clickableY = y - clickableHeight/2;
                
                if ((float)mouseX >= valueX && (float)mouseX <= valueX + textWidth &&
                    (float)mouseY >= clickableY && (float)mouseY <= clickableY + clickableHeight) {
                    // Start editing this value
                    isEditingValue = true;
                    editingValueIndex = index;
                    editingValueTab = null; // Not using the tab enum for speed settings
                    valueInputBuffer.setLength(0);
                    valueInputBuffer.append(valueText);
                    isDoubleClick = false; // Consume the double click
                }
            }
        }
        
        // Check for mouse interaction
        if (mouseButtonPressed) {
            // Use the same slider center Y calculation as in the drawing code
            if ((float)mouseX >= dispersionKnobX[index] - knobSize/2 && (float)mouseX <= dispersionKnobX[index] + knobSize/2 &&
                (float)mouseY >= sliderCenterY - knobSize/2 && (float)mouseY <= sliderCenterY + knobSize/2) {
                dispersionKnobDragging[index] = true;
            }
        } else if (!mouseButtonDown) {
            dispersionKnobDragging[index] = false;
        }
        
        if (dispersionKnobDragging[index]) {
            float newX = Math.max(dispersionSliderX[index], Math.min(dispersionSliderX[index] + dispersionSliderWidth, (float)mouseX));
            float newNormalizedValue = (newX - dispersionSliderX[index]) / dispersionSliderWidth;
            float newValue = min + newNormalizedValue * (max - min);
            
            // Update the appropriate setting based on the index
            updateSpeedSetting(index, newValue);
        }
    }
    
    /**
     * Updates the appropriate speed setting based on the slider index
     */
    private void updateSpeedSetting(int index, float value) {
        switch (index) {
            case 0: SpeedSettings.setDefaultSpeed(value); break;
            case 1: SpeedSettings.setAmplifiedSpeedMultiplier(value); break;
        }
    }
    
    /**
     * Draws the dispersion button
     */
    private void drawDispersionButton() {
        // draw button background
        if (dispersionButtonHovered) {
            // bright blue when hovered
            glColor4f(0.3f, 0.5f, 0.8f, 0.8f);
        } else {
            // darker blue normally
            glColor4f(0.2f, 0.3f, 0.6f, 0.7f);
        }
        
        glBegin(GL_QUADS);
        glVertex2f(dispersionButtonX, dispersionButtonY);
        glVertex2f(dispersionButtonX + dispersionButtonWidth, dispersionButtonY);
        glVertex2f(dispersionButtonX + dispersionButtonWidth, dispersionButtonY + dispersionButtonHeight);
        glVertex2f(dispersionButtonX, dispersionButtonY + dispersionButtonHeight);
        glEnd();
        
        // draw button border
        glColor4f(0.4f, 0.6f, 0.9f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(dispersionButtonX, dispersionButtonY);
        glVertex2f(dispersionButtonX + dispersionButtonWidth, dispersionButtonY);
        glVertex2f(dispersionButtonX + dispersionButtonWidth, dispersionButtonY + dispersionButtonHeight);
        glVertex2f(dispersionButtonX, dispersionButtonY + dispersionButtonHeight);
        glEnd();
        
        // draw "Dispersion Settings" text
        String buttonText = "DISPERSION SETTINGS";
        float buttonFontSize = fontHeight * 0.9f;
        float textWidth = getTextWidth(buttonText, buttonFontSize);
        float textX = dispersionButtonX + (dispersionButtonWidth - textWidth) / 2;
        float textY = dispersionButtonY + (dispersionButtonHeight - buttonFontSize) / 2 + buttonFontSize * 0.7f;
        
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        renderTTFText(buttonText, textX, textY, buttonFontSize);
    }
    
    /**
     * Draws the dispersion submenu with tabs for different celestial objects
     */
    private void drawDispersionMenu() {
        // draw dispersion title
        String dispersionTitle = "DISPERSION SETTINGS";
        float titleFontSize = fontHeight * 1.5f;
        float titleWidth = getTextWidth(dispersionTitle, titleFontSize);
        float titleX = (WIDTH - titleWidth) / 2;
        float titleY = HEIGHT / 6;
        
        glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
        renderTTFText(dispersionTitle, titleX, titleY, titleFontSize);
        
        // draw tabs
        drawDispersionTabs();
        
        // draw settings for current tab
        switch (currentDispersionTab) {
            case SUNS:
                drawSunSettings();
                break;
            case STARS:
                drawStarSettings();
                break;
            case SHOOTING_STARS:
                drawShootingStarSettings();
                break;
            case NEBULAE:
                drawNebulaSettings();
                break;
        }
    }
    
    /**
     * Draws the tabs for the dispersion submenu
     */
    private void drawDispersionTabs() {
        float tabWidth = WIDTH / 4; // Adjusted for 4 tabs
        float tabHeight = 40;
        float tabY = HEIGHT / 6 + 60;
        float tabFontSize = fontHeight * 0.8f;
        
        String[] tabNames = {"SUNS", "STARS", "SHOOTING STARS", "NEBULAE"};
        DispersionTab[] tabValues = {DispersionTab.SUNS, DispersionTab.STARS, DispersionTab.SHOOTING_STARS, DispersionTab.NEBULAE};
        
        for (int i = 0; i < tabNames.length; i++) {
            float tabX = i * tabWidth;
            
            // draw tab background
            if (currentDispersionTab == tabValues[i]) {
                // selected tab is brighter
                glColor4f(0.3f, 0.5f, 0.8f, 0.8f);
            } else {
                // unselected tabs are darker
                glColor4f(0.1f, 0.2f, 0.4f, 0.7f);
            }
            
            glBegin(GL_QUADS);
            glVertex2f(tabX, tabY);
            glVertex2f(tabX + tabWidth, tabY);
            glVertex2f(tabX + tabWidth, tabY + tabHeight);
            glVertex2f(tabX, tabY + tabHeight);
            glEnd();
            
            // draw tab border
            glColor4f(0.4f, 0.6f, 0.9f, 1.0f);
            glLineWidth(1.0f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(tabX, tabY);
            glVertex2f(tabX + tabWidth, tabY);
            glVertex2f(tabX + tabWidth, tabY + tabHeight);
            glVertex2f(tabX, tabY + tabHeight);
            glEnd();
            
            // draw tab text
            // Use smaller font size for "SHOOTING STARS" tab to fit better
            float actualTabFontSize = tabNames[i].equals("SHOOTING STARS") ? tabFontSize * 0.8f : tabFontSize;
            
            float textWidth = getTextWidth(tabNames[i], actualTabFontSize);
            float textX = tabX + (tabWidth - textWidth) / 2;
            float textY = tabY + (tabHeight - actualTabFontSize) / 2 + actualTabFontSize * 0.7f;
            
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            renderTTFText(tabNames[i], textX, textY, actualTabFontSize);
            
            // check if tab is clicked
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY && mouseY <= tabY + tabHeight && mouseButtonPressed) {
                currentDispersionTab = tabValues[i];
                mouseButtonPressed = false; // consume the click
            }
        }
    }
    
    /**
     * Draws a slider for dispersion settings
     */
    private void drawDispersionSlider(int index, String label, float value, float min, float max, float y) {
        float labelFontSize = fontHeight * 0.7f;
        float labelWidth = getTextWidth(label, labelFontSize);
        float totalWidth = WIDTH * 0.7f;
        float startX = (WIDTH - totalWidth) / 2;
        
        // Store slider position for interaction
        dispersionSliderX[index] = startX + labelWidth + 20;
        dispersionSliderY[index] = y;
        
        // Draw label
        glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
        renderTTFText(label, startX, y + labelFontSize/2, labelFontSize);
        
        // Draw slider track
        glColor4f(0.3f, 0.3f, 0.3f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(dispersionSliderX[index], dispersionSliderY[index]);
        glVertex2f(dispersionSliderX[index] + dispersionSliderWidth, dispersionSliderY[index]);
        glVertex2f(dispersionSliderX[index] + dispersionSliderWidth, dispersionSliderY[index] + dispersionSliderHeight);
        glVertex2f(dispersionSliderX[index], dispersionSliderY[index] + dispersionSliderHeight);
        glEnd();
        
        // Calculate normalized value and knob position
        float normalizedValue = (value - min) / (max - min);
        dispersionKnobX[index] = dispersionSliderX[index] + normalizedValue * dispersionSliderWidth;
        
        // Draw knob
        float knobSize = 15.0f;
        // Calculate the vertical center of the slider for the knob
        float sliderCenterY = dispersionSliderY[index] + dispersionSliderHeight/2;
        
        glColor4f(0.7f, 0.8f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(dispersionKnobX[index] - knobSize/2, sliderCenterY - knobSize/2);
        glVertex2f(dispersionKnobX[index] + knobSize/2, sliderCenterY - knobSize/2);
        glVertex2f(dispersionKnobX[index] + knobSize/2, sliderCenterY + knobSize/2);
        glVertex2f(dispersionKnobX[index] - knobSize/2, sliderCenterY + knobSize/2);
        glEnd();
        
        // Draw value text or text input field
        float valueX = dispersionSliderX[index] + dispersionSliderWidth + 10;
        
        if (isEditingValue && editingValueIndex == index && editingValueTab == currentDispersionTab) {
            // Draw text input field with cursor
            String inputText = valueInputBuffer.toString();
            
            // Draw input field background
            glColor4f(0.1f, 0.1f, 0.2f, 0.8f);
            float inputWidth = Math.max(80.0f, getTextWidth(inputText, labelFontSize) + 20);
            float inputHeight = labelFontSize * 1.2f; // Reduced height
            
            // Position the input box higher to better align with the value text
            float inputY = y - inputHeight/2;
            
            glBegin(GL_QUADS);
            glVertex2f(valueX, inputY);
            glVertex2f(valueX + inputWidth, inputY);
            glVertex2f(valueX + inputWidth, inputY + inputHeight);
            glVertex2f(valueX, inputY + inputHeight);
            glEnd();
            
            // Draw input text
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            renderTTFText(inputText, valueX + 5, y + labelFontSize/2, labelFontSize);
            
            // Draw cursor
            if ((System.currentTimeMillis() / 500) % 2 == 0) { // Blinking cursor
                float cursorX = valueX + 5 + getTextWidth(inputText, labelFontSize);
                glBegin(GL_LINES);
                glVertex2f(cursorX, inputY + 2);
                glVertex2f(cursorX, inputY + inputHeight - 2);
                glEnd();
            }
        } else {
            // Draw normal value text
            // Use more decimal places for very small values
            String valueText;
            if (value < 0.001f) {
                valueText = String.format("%.6f", value); // 6 decimal places for very small values
            } else {
                valueText = String.format("%.3f", value); // 3 decimal places for normal values
            }
            
            glColor4f(0.9f, 0.95f, 1.0f, 1.0f);
            renderTTFText(valueText, valueX, y + labelFontSize/2, labelFontSize);
            
            // Check for double click on value text
            if (isDoubleClick && mouseButtonPressed) {
                float textWidth = getTextWidth(valueText, labelFontSize);
                float clickableHeight = labelFontSize * 1.2f;
                float clickableY = y - clickableHeight/2;
                
                if ((float)mouseX >= valueX && (float)mouseX <= valueX + textWidth &&
                    (float)mouseY >= clickableY && (float)mouseY <= clickableY + clickableHeight) {
                    // Start editing this value
                    isEditingValue = true;
                    editingValueIndex = index;
                    editingValueTab = currentDispersionTab;
                    valueInputBuffer.setLength(0);
                    valueInputBuffer.append(valueText);
                    isDoubleClick = false; // Consume the double click
                }
            }
        }
        
        // Check for mouse interaction
        if (mouseButtonPressed) {
            // Use the same slider center Y calculation as in the drawing code
            if ((float)mouseX >= dispersionKnobX[index] - knobSize/2 && (float)mouseX <= dispersionKnobX[index] + knobSize/2 &&
                (float)mouseY >= sliderCenterY - knobSize/2 && (float)mouseY <= sliderCenterY + knobSize/2) {
                dispersionKnobDragging[index] = true;
            }
        } else if (!mouseButtonDown) {
            dispersionKnobDragging[index] = false;
        }
        
        if (dispersionKnobDragging[index]) {
            float newX = Math.max(dispersionSliderX[index], Math.min(dispersionSliderX[index] + dispersionSliderWidth, (float)mouseX));
            float newNormalizedValue = (newX - dispersionSliderX[index]) / dispersionSliderWidth;
            float newValue = min + newNormalizedValue * (max - min);
            
            // Update the appropriate setting based on the index
            updateDispersionSetting(index, newValue);
        }
    }
    
    /**
     * Handles keyboard input for value editing and key rebinding
     */
    private void handleKeyInput(int key, int scancode, int action, int mods) {
        if (action == GLFW_PRESS || action == GLFW_REPEAT) {
            // Handle key rebinding if we're in rebinding mode
            if (currentRebindingKey >= 0 && showControlMenu) {
                // Only accept the key if it's a valid key for binding
                if (key != GLFW_KEY_ESCAPE) { // Don't allow ESC to be rebound
                    switch (currentRebindingKey) {
                        case 0: // Forward
                            ControlSettings.setMoveForwardKey(key);
                            break;
                        case 1: // Backward
                            ControlSettings.setMoveBackwardKey(key);
                            break;
                        case 2: // Left
                            ControlSettings.setMoveLeftKey(key);
                            break;
                        case 3: // Right
                            ControlSettings.setMoveRightKey(key);
                            break;
                        case 4: // Up
                            ControlSettings.setMoveUpKey(key);
                            break;
                        case 5: // Down
                            ControlSettings.setMoveDownKey(key);
                            break;
                        case 6: // Amplify Speed
                            ControlSettings.setAmplifySpeedKey(key);
                            break;
                    }
                }
                // Exit rebinding mode
                currentRebindingKey = -1;
                return;
            }
            
            // Handle value editing
            if (isEditingValue) {
                if (key == GLFW_KEY_ESCAPE) {
                    // cancel editing
                    isEditingValue = false;
                    valueInputBuffer.setLength(0);
                } else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
                    // apply the new value
                    try {
                        float value = Float.parseFloat(valueInputBuffer.toString());
                        if (editingValueTab == null) {
                            updateSpeedSetting(editingValueIndex, value);
                        } else {
                            updateDispersionSetting(editingValueIndex, value);
                        }
                        isEditingValue = false;
                        valueInputBuffer.setLength(0);
                    } catch (NumberFormatException e) {
                        // invalid number, do nothing
                    }
                } else if (key == GLFW_KEY_BACKSPACE && valueInputBuffer.length() > 0) {
                    // remove last character
                    valueInputBuffer.deleteCharAt(valueInputBuffer.length() - 1);
                }
            }
        }
    }
    
    /**
     * Handles character input for value editing
     */
    private void handleCharInput(int codepoint) {
        if (!isEditingValue) return;
        
        char c = (char) codepoint;
        // Only allow digits, decimal point, and minus sign
        if (Character.isDigit(c) || c == '.' || c == '-') {
            valueInputBuffer.append(c);
        }
    }
    
    /**
     * Updates the appropriate dispersion setting based on the slider index
     */
    private void updateDispersionSetting(int index, float value) {
        switch (currentDispersionTab) {
            case SUNS:
                switch (index) {
                    case 0: DispersionSettings.setSunRarity(value); break;
                    case 1: DispersionSettings.setSunMinSize(value); break;
                    case 2: DispersionSettings.setSunMaxSize(value); break;
                    case 3: DispersionSettings.setSunMinDistance(value); break;
                    case 4: DispersionSettings.setSunMaxDistance(value); break;
                }
                break;
            case STARS:
                switch (index) {
                    case 0: DispersionSettings.setStarClusterDensity(value); break;
                    case 1: DispersionSettings.setScatteredStarDensity(value); break;
                    case 2: DispersionSettings.setStarGlimmerChance(value); break;
                    case 3: DispersionSettings.setStarGlimmerIntensity(value); break;
                }
                break;
            case SHOOTING_STARS:
                switch (index) {
                    case 0: DispersionSettings.setShootingStarChance(value); break;
                    case 1: DispersionSettings.setShootingStarSpeed(value); break;
                    case 2: DispersionSettings.setShootingStarSize(value); break;
                }
                break;
            case NEBULAE:
                switch (index) {
                    case 0: DispersionSettings.setNebulaRarity(value); break;
                    case 1: DispersionSettings.setNebulaMinSize(value); break;
                    case 2: DispersionSettings.setNebulaMaxSize(value); break;
                    case 3: DispersionSettings.setNebulaParticleDensity(value); break;
                }
                break;
        }
    }
    
    /**
     * Draws the sun settings in the dispersion submenu
     */
    private void drawSunSettings() {
        float startY = HEIGHT / 6 + 120;
        float spacing = 40;
        
        drawDispersionSlider(0, "Sun Rarity:", DispersionSettings.getSunRarity(), 0.0001f, 0.05f, startY);
        drawDispersionSlider(1, "Min Sun Size:", DispersionSettings.getSunMinSize(), 100.0f, 1000.0f, startY + spacing);
        drawDispersionSlider(2, "Max Sun Size:", DispersionSettings.getSunMaxSize(), 500.0f, 2000.0f, startY + spacing * 2);
        drawDispersionSlider(3, "Min Sun Distance:", DispersionSettings.getSunMinDistance(), 1000.0f, 10000.0f, startY + spacing * 3);
        drawDispersionSlider(4, "Max Sun Distance:", DispersionSettings.getSunMaxDistance(), 5000.0f, 20000.0f, startY + spacing * 4);
        
        // Instructions for value editing - split into separate lines
        String instructions1 = "Double-click on a value to edit it directly.";
        float instructionsY1 = startY + spacing * 5 + 20;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions1, (WIDTH - getTextWidth(instructions1, fontHeight * 0.6f)) / 2, instructionsY1, fontHeight * 0.6f);
        
        // Enter/Return instruction
        String instructions2 = "Press Enter/Return to set the value.";
        float instructionsY2 = instructionsY1 + fontHeight * 0.8f;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions2, (WIDTH - getTextWidth(instructions2, fontHeight * 0.6f)) / 2, instructionsY2, fontHeight * 0.6f);
        
        // Ready to play message
        String instructions3 = "Then, you're ready to play!";
        float instructionsY3 = instructionsY2 + fontHeight * 0.8f;
        glColor4f(0.8f, 0.9f, 1.0f, 0.9f);
        renderTTFText(instructions3, (WIDTH - getTextWidth(instructions3, fontHeight * 0.6f)) / 2, instructionsY3, fontHeight * 0.6f);
    }
    
    /**
     * Draws the star settings in the dispersion submenu
     */
    private void drawStarSettings() {
        float startY = HEIGHT / 6 + 120;
        float spacing = 40;
        
        drawDispersionSlider(0, "Star Cluster Density:", DispersionSettings.getStarClusterDensity(), 0.1f, 5.0f, startY);
        drawDispersionSlider(1, "Scattered Star Density:", DispersionSettings.getScatteredStarDensity(), 0.1f, 5.0f, startY + spacing);
        drawDispersionSlider(2, "Star Glimmer Chance:", DispersionSettings.getStarGlimmerChance(), 0.0f, 1.0f, startY + spacing * 2);
        drawDispersionSlider(3, "Star Glimmer Intensity:", DispersionSettings.getStarGlimmerIntensity(), 1.0f, 5.0f, startY + spacing * 3);
        
        // Instructions for value editing - split into separate lines
        String instructions1 = "Double-click on a value to edit it directly.";
        float instructionsY1 = startY + spacing * 5 + 20;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions1, (WIDTH - getTextWidth(instructions1, fontHeight * 0.6f)) / 2, instructionsY1, fontHeight * 0.6f);
        
        // Enter/Return instruction
        String instructions2 = "Press Enter/Return to set the value.";
        float instructionsY2 = instructionsY1 + fontHeight * 0.8f;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions2, (WIDTH - getTextWidth(instructions2, fontHeight * 0.6f)) / 2, instructionsY2, fontHeight * 0.6f);
        
        // Ready to play message
        String instructions3 = "Then, you're ready to play!";
        float instructionsY3 = instructionsY2 + fontHeight * 0.8f;
        glColor4f(0.8f, 0.9f, 1.0f, 0.9f);
        renderTTFText(instructions3, (WIDTH - getTextWidth(instructions3, fontHeight * 0.6f)) / 2, instructionsY3, fontHeight * 0.6f);
    }
    
    /**
     * Draws the shooting star settings in the dispersion submenu
     */
    private void drawShootingStarSettings() {
        float startY = HEIGHT / 6 + 120;
        float spacing = 40;
        
        drawDispersionSlider(0, "Shooting Star Chance:", DispersionSettings.getShootingStarChance(), 0.0f, 0.1f, startY);
        drawDispersionSlider(1, "Shooting Star Speed:", DispersionSettings.getShootingStarSpeed(), 0.1f, 5.0f, startY + spacing);
        drawDispersionSlider(2, "Shooting Star Size:", DispersionSettings.getShootingStarSize(), 0.1f, 3.0f, startY + spacing * 2);
        
        // Instructions for value editing - split into separate lines
        String instructions1 = "Double-click on a value to edit it directly.";
        float instructionsY1 = startY + spacing * 5 + 20;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions1, (WIDTH - getTextWidth(instructions1, fontHeight * 0.6f)) / 2, instructionsY1, fontHeight * 0.6f);
        
        // Enter/Return instruction
        String instructions2 = "Press Enter/Return to set the value.";
        float instructionsY2 = instructionsY1 + fontHeight * 0.8f;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions2, (WIDTH - getTextWidth(instructions2, fontHeight * 0.6f)) / 2, instructionsY2, fontHeight * 0.6f);
        
        // Ready to play message
        String instructions3 = "Then, you're ready to play!";
        float instructionsY3 = instructionsY2 + fontHeight * 0.8f;
        glColor4f(0.8f, 0.9f, 1.0f, 0.9f);
        renderTTFText(instructions3, (WIDTH - getTextWidth(instructions3, fontHeight * 0.6f)) / 2, instructionsY3, fontHeight * 0.6f);
    }
    
    /**
     * Draws the nebula settings in the dispersion submenu
     */
    private void drawNebulaSettings() {
        float startY = HEIGHT / 6 + 120;
        float spacing = 50;
        
        // Draw sliders for nebula settings
        drawDispersionSlider(0, "Nebula Rarity:", DispersionSettings.getNebulaRarity(), 0.001f, 0.2f, startY);
        drawDispersionSlider(1, "Min Size:", DispersionSettings.getNebulaMinSize(), 100.0f, 1000.0f, startY + spacing);
        drawDispersionSlider(2, "Max Size:", DispersionSettings.getNebulaMaxSize(), 500.0f, 3000.0f, startY + spacing * 2);
        drawDispersionSlider(3, "Particle Density:", DispersionSettings.getNebulaParticleDensity(), 0.1f, 3.0f, startY + spacing * 3);
        
        // Instructions for value editing - split into separate lines
        String instructions1 = "Double-click on a value to edit it directly.";
        float instructionsY1 = startY + spacing * 5 + 20;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions1, (WIDTH - getTextWidth(instructions1, fontHeight * 0.6f)) / 2, instructionsY1, fontHeight * 0.6f);
        
        // Enter/Return instruction
        String instructions2 = "Press Enter/Return to set the value.";
        float instructionsY2 = instructionsY1 + fontHeight * 0.8f;
        glColor4f(0.7f, 0.7f, 0.8f, 0.8f);
        renderTTFText(instructions2, (WIDTH - getTextWidth(instructions2, fontHeight * 0.6f)) / 2, instructionsY2, fontHeight * 0.6f);
        
        // Ready to play message
        String instructions3 = "Then, you're ready to play!";
        float instructionsY3 = instructionsY2 + fontHeight * 0.8f;
        glColor4f(0.8f, 0.9f, 1.0f, 0.9f);
        renderTTFText(instructions3, (WIDTH - getTextWidth(instructions3, fontHeight * 0.6f)) / 2, instructionsY3, fontHeight * 0.6f);
    }
    
    /**
     * draws the volume controls
     */
    private void drawVolumeControls() {
        // draw volume label
        String volumeLabel = "MUSIC VOLUME";
        float labelFontSize = fontHeight * 0.8f;
        float labelWidth = getTextWidth(volumeLabel, labelFontSize);
        
        // Center the entire control (text + slider) horizontally
        float totalWidth = labelWidth + 20 + volumeSliderWidth; // 20px spacing between text and slider
        float startX = (WIDTH - totalWidth) / 2;
        
        // Position text and slider vertically in the middle of the options menu
        float controlY = HEIGHT / 3;
        
        float labelX = startX;
        float labelY = controlY;
        
        // Calculate slider position (to the right of the text)
        volumeSliderX = labelX + labelWidth + 20; // 20px spacing
        // Position slider to be exactly aligned with the text baseline
        volumeSliderY = controlY + labelFontSize/2 - 37.5f; // Move down by 12.5 pixels (half of 25)
        
        // Adjust knob position based on the new slider position
        if (audioPlayer != null) {
            volumeKnobX = volumeSliderX + (volumeSliderWidth * audioPlayer.getVolume());
        } else {
            volumeKnobX = volumeSliderX + (volumeSliderWidth * 0.7f); // default 70%
        }
        
        // Draw volume label
        glColor4f(0.9f, 0.9f, 1.0f, 1.0f);
        renderTTFText(volumeLabel, labelX, labelY, labelFontSize);
        
        // draw slider background (track)
        glColor4f(0.2f, 0.2f, 0.3f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(volumeSliderX, volumeSliderY);
        glVertex2f(volumeSliderX + volumeSliderWidth, volumeSliderY);
        glVertex2f(volumeSliderX + volumeSliderWidth, volumeSliderY + volumeSliderHeight);
        glVertex2f(volumeSliderX, volumeSliderY + volumeSliderHeight);
        glEnd();
        
        // draw filled portion of slider
        glColor4f(0.3f, 0.6f, 0.9f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(volumeSliderX, volumeSliderY);
        glVertex2f(volumeKnobX, volumeSliderY);
        glVertex2f(volumeKnobX, volumeSliderY + volumeSliderHeight);
        glVertex2f(volumeSliderX, volumeSliderY + volumeSliderHeight);
        glEnd();
        
        // draw slider knob
        glColor4f(0.8f, 0.9f, 1.0f, 1.0f);
        float knobY = volumeSliderY + volumeSliderHeight / 2;
        float knobRadius = volumeKnobSize / 2;
        
        // draw circle for knob
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(volumeKnobX, knobY); // center
        for (int i = 0; i <= 20; i++) {
            float angle = (float) (i * 2.0f * Math.PI / 20);
            float x = volumeKnobX + (float) Math.cos(angle) * knobRadius;
            float y = knobY + (float) Math.sin(angle) * knobRadius;
            glVertex2f(x, y);
        }
        glEnd();
        
        // draw knob border
        glColor4f(0.4f, 0.7f, 1.0f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i <= 20; i++) {
            float angle = (float) (i * 2.0f * Math.PI / 20);
            float x = volumeKnobX + (float) Math.cos(angle) * knobRadius;
            float y = knobY + (float) Math.sin(angle) * knobRadius;
            glVertex2f(x, y);
        }
        glEnd();
        
        // draw volume percentage
        float volume = (volumeKnobX - volumeSliderX) / volumeSliderWidth * 100;
        String volumeText = String.format("%.0f%%", volume);
        float volumeTextFontSize = fontHeight * 0.7f;
        float volumeTextX = volumeSliderX + volumeSliderWidth + 10; // Position to the right of the slider
        float volumeTextY = volumeSliderY - volumeTextFontSize/4 + 20;
        
        glColor4f(0.8f, 0.8f, 1.0f, 1.0f);
        renderTTFText(volumeText, volumeTextX, volumeTextY, volumeTextFontSize);
    }
    
    // render text using the ttf font with goated quality
    private void renderTTFText(String text, float x, float y, float fontSize) {
        // enable texturing with anti-aliasing
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // enable anti-aliasing for smooth edges
        glEnable(GL_LINE_SMOOTH);
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
        glEnable(GL_POINT_SMOOTH);
        glHint(GL_POINT_SMOOTH_HINT, GL_NICEST);
        
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        
        // scale factor for font size
        float scale = fontSize / fontHeight;
        
        try (MemoryStack stack = stackPush()) {
            FloatBuffer xpos = stack.floats(x);
            FloatBuffer ypos = stack.floats(y);
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            
            // iterate through each character
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < FIRST_CHAR || c >= FIRST_CHAR + NUM_CHARS) continue;
                
                // get character quad
                stbtt_GetBakedQuad(charData, BITMAP_WIDTH, BITMAP_HEIGHT, c - FIRST_CHAR, xpos, ypos, q, true);
                
                // apply scale
                float x0 = x + (q.x0() - x) * scale;
                float x1 = x + (q.x1() - x) * scale;
                float y0 = y + (q.y0() - y) * scale;
                float y1 = y + (q.y1() - y) * scale;
                
                // draw character quad
                glBegin(GL_QUADS);
                glTexCoord2f(q.s0(), q.t0()); glVertex2f(x0, y0);
                glTexCoord2f(q.s1(), q.t0()); glVertex2f(x1, y0);
                glTexCoord2f(q.s1(), q.t1()); glVertex2f(x1, y1);
                glTexCoord2f(q.s0(), q.t1()); glVertex2f(x0, y1);
                glEnd();
            }
        }
        
        // disable texturing
        glDisable(GL_TEXTURE_2D);
    }
    
    // calculate the width of a text string
    private float getTextWidth(String text, float fontSize) {
        float scale = fontSize / fontHeight;
        float width = 0;
        
        try (MemoryStack stack = stackPush()) {
            FloatBuffer xpos = stack.floats(0);
            FloatBuffer ypos = stack.floats(0);
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < FIRST_CHAR || c >= FIRST_CHAR + NUM_CHARS) continue;
                
                stbtt_GetBakedQuad(charData, BITMAP_WIDTH, BITMAP_HEIGHT, c - FIRST_CHAR, xpos, ypos, q, true);
            }
            
            width = xpos.get(0) * scale;
        }
        
        return width;
    }
}
