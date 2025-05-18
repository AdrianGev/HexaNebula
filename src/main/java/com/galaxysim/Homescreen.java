package com.galaxysim;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
// GL11 is already imported via static import i think
import org.lwjgl.opengl.GL30;
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

public class Homescreen {
    private long window;
    private final int WIDTH = 1280;
    private final int HEIGHT = 720;
    
    // background stars
    private List<BackgroundStar> stars;
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
    
    // volume control properties
    private float volumeSliderX;
    private float volumeSliderY;
    private float volumeSliderWidth;
    private float volumeSliderHeight;
    private float volumeKnobX;
    private float volumeKnobSize;
    private boolean volumeKnobDragging = false;
    
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
    private static class BackgroundStar {
        float x, y;
        float size;
        float brightness;
        
        BackgroundStar(float x, float y, float size, float brightness) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.brightness = brightness;
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
        
        // setup mouse callbacks for button interaction and volume control
        glfwSetCursorPosCallback(window, (windowHandle, xpos, ypos) -> {
            // check if mouse is over play button
            playButtonHovered = xpos >= playButtonX && xpos <= playButtonX + playButtonWidth &&
                               ypos >= playButtonY && ypos <= playButtonY + playButtonHeight;
            
            // check if mouse is over options button
            optionsButtonHovered = xpos >= optionsButtonX && xpos <= optionsButtonX + optionsButtonWidth &&
                                  ypos >= optionsButtonY && ypos <= optionsButtonY + optionsButtonHeight;
            
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
        glfwSetMouseButtonCallback(window, (windowHandle, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                double[] xpos = new double[1];
                double[] ypos = new double[1];
                glfwGetCursorPos(window, xpos, ypos);
                
                if (action == GLFW_PRESS) {
                    // check if clicking on volume knob or slider
                    if (showOptionsMenu) {
                        float knobRadius = volumeKnobSize / 2;
                        float knobY = volumeSliderY + volumeSliderHeight / 2;
                        
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
        
        // initialize stars
        random = new Random();
        stars = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            stars.add(new BackgroundStar(
                random.nextFloat() * WIDTH,
                random.nextFloat() * HEIGHT,
                random.nextFloat() * 2.0f + 1.0f,
                random.nextFloat() * 0.5f + 0.5f
            ));
        }
        
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
        // We'll position the slider in drawVolumeControls() after calculating the text width
        volumeKnobSize = 20;
        // set initial knob position based on default volume (will be adjusted in drawVolumeControls)
        if (audioPlayer != null) {
            volumeKnobX = volumeSliderWidth * audioPlayer.getVolume();
        } else {
            volumeKnobX = volumeSliderWidth * 0.7f; // default 70%
        }
        
        // initialize back button properties
        backButtonWidth = 200;
        backButtonHeight = 50;
        backButtonX = (WIDTH - backButtonWidth) / 2;
        backButtonY = HEIGHT - 100; // near the bottom of the screen
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
                    } else {
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
                if (playButtonHovered) {
                    startGame = true;
                } else if (optionsButtonHovered && !showOptionsMenu) {
                    // show options menu
                    showOptionsMenu = true;
                    // add a small delay to prevent multiple toggles
                    try { Thread.sleep(200); } catch (InterruptedException e) {}
                } else if (backButtonHovered && showOptionsMenu) {
                    // hide options menu when back button is clicked
                    showOptionsMenu = false;
                    // add a small delay to prevent multiple toggles
                    try { Thread.sleep(200); } catch (InterruptedException e) {}
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
        glBegin(GL_POINTS);
        for (BackgroundStar star : stars) {
            // use the star size for point size
            glPointSize(star.size);
            glColor4f(star.brightness, star.brightness, star.brightness, 1.0f);
            glVertex2f(star.x, star.y);
        }
        glEnd();
    }
    
    private void drawTitle() {
        // draw "HexaNebula" title
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
