package com.galaxysim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class GalaxySimulator {
    // basic window stuff we need for the game
    private long window;
    private final int WIDTH = 1280;
    private final int HEIGHT = 720;

    // camera stuff - this is how we move around in space
    private Vector3f cameraPos;      // where we are in space
    private CoordinatesFontRenderer coordinatesFontRenderer;
    private Vector3f cameraFront;    // which way we're looking
    private Vector3f cameraUp;       // which way is up (so you don't get disoriented)
    
    // mouse looking stuff - helps us look around smoothly
    private float yaw = -90.0f;      // looking left/right
    private float pitch = 0.0f;      // looking up/down
    private float lastX = WIDTH / 2.0f;  // last mouse x pos
    private float lastY = HEIGHT / 2.0f; // last mouse y pos

    // the actual space stuff
    private List<Star> stars;         // all stars
    private List<Stars.ShootingStar> shootingStars; // shooting stars with trails
    private List<Nebula> nebulae;     // colorful gas clouds
    private List<Bodies.Sun> suns;    // large stars (suns)
    private Random random;            
    private Set<String> generatedRegions;  // keeps track of where the game has already made stars
    private static final float REGION_SIZE = DispersionSettings.getRegionSize();  // how big each chunk of space is

    // this is what each star is made of
    // Changed from static to non-static class to access the random field
    private class Star {
        Vector3f position;    // where the star is in space
        float size;          // how chunky the star is
        float brightness;    // how bright and shiny it is
        
        // Flag to identify if this is a scattered star or a clustered star
        boolean isScatteredStar; // true if this is a scattered star (should glimmer)
        
        // Glimmering effect properties
        boolean canGlimmer;   // whether this star can glimmer
        float glimmerChance;  // chance of glimmering each frame
        float glimmerIntensity; // how bright the glimmer is
        boolean isGlimmering; // current glimmering state
        float currentBrightness; // current brightness with glimmer effect

        // Constructor with flag to identify scattered vs clustered stars
        Star(Vector3f position, float size, float brightness, boolean isScatteredStar) {
            this.position = position;
            this.size = size;
            this.brightness = brightness;
            this.currentBrightness = brightness;
            this.isScatteredStar = isScatteredStar;
            
            // Only scattered stars can glimmer
            if (isScatteredStar) {
                // High chance for scattered stars to glimmer
                this.canGlimmer = random.nextFloat() < 0.9f; // 90% of scattered stars can glimmer
                // How often the star glimmers - high chance for scattered stars
                this.glimmerChance = random.nextFloat() * 0.05f + 0.03f; // 3-8% chance per frame
                // How intense the glimmer is (1.8x to 3.0x normal brightness)
                this.glimmerIntensity = random.nextFloat() * 1.2f + 1.8f;
            } else {
                // Clustered stars don't glimmer
                this.canGlimmer = false;
                this.glimmerChance = 0;
                this.glimmerIntensity = 1.0f;
            }
            this.isGlimmering = false;
        }
        
        // Update the star's glimmering state
        void update() {
            // Only scattered stars can glimmer
            if (!isScatteredStar || !canGlimmer) {
                return; // This star doesn't glimmer
            }
            
            // Check if we should start glimmering
            if (!isGlimmering && random.nextFloat() < glimmerChance) {
                isGlimmering = true;
                currentBrightness = brightness * glimmerIntensity;
            } 
            // Check if we should stop glimmering (glimmers are brief)
            else if (isGlimmering && random.nextFloat() < 0.15f) { // 15% chance to stop each frame
                isGlimmering = false;
                currentBrightness = brightness;
            }
        }
    }

    public void run() {
        // Show homescreen first
        Homescreen homescreen = new Homescreen();
        boolean startGame = homescreen.show();
        
        // Only start the game if the user clicked play
        // (no duh)
        if (startGame) {
            init();
            loop();

            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }
        
        // clean up GLFW
        if (coordinatesFontRenderer != null) {
            coordinatesFontRenderer.cleanup();
        }
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // setup error handling first (in case it crashes out)
        GLFWErrorCallback.createPrint(System.err).set();

        // try to start up our window system
        if (!glfwInit()) {
            throw new IllegalStateException("uh oh, GLFW didn't want to start :(");
        }

        // put the player in the middle of nowhere
        cameraPos = new Vector3f(0.0f, 0.0f, 0.0f);
        cameraFront = new Vector3f(0.0f, 0.0f, -1.0f);  // looking forward
        cameraUp = new Vector3f(0.0f, 1.0f, 0.0f);      // this way is up
        yaw = -90.0f;   // start looking forward
        pitch = 0.0f;   // don't look up or down yet

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

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
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        
        // Disable cursor before showing the window
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        
        glfwShowWindow(window);

        // setup ESC key callback
        glfwSetKeyCallback(window, (windowHandle, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(windowHandle, true);
            }
        });

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);

        // Initialize font renderer for coordinates
        coordinatesFontRenderer = new CoordinatesFontRenderer(16.0f);
        coordinatesFontRenderer.init();

        // Initialize our collections
        stars = new ArrayList<>();
        shootingStars = new ArrayList<>();
        nebulae = new ArrayList<>();
        suns = new ArrayList<>();
        random = new Random();
        generatedRegions = new HashSet<>();

        // Make sure cursor is hidden and set initial position
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        
        // Force cursor to center of screen
        glfwSetCursorPos(window, WIDTH / 2.0f, HEIGHT / 2.0f);
        lastX = WIDTH / 2.0f;
        lastY = HEIGHT / 2.0f;

        // setup mouse callback
        glfwSetCursorPosCallback(window, (windowHandle, xpos, ypos) -> {
            float xOffset = (float) xpos - lastX;
            float yOffset = lastY - (float) ypos;
            lastX = (float) xpos;
            lastY = (float) ypos;

            float sensitivity = 0.1f;
            xOffset *= sensitivity;
            yOffset *= sensitivity;

            yaw += xOffset;
            pitch += yOffset;

            // constrain pitch
            if (pitch > 89.0f) pitch = 89.0f;
            if (pitch < -89.0f) pitch = -89.0f;

            // update camera front vector
            Vector3f direction = new Vector3f(
                (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))),
                (float) Math.sin(Math.toRadians(pitch)),
                (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)))
            );
            cameraFront = direction.normalize();
        });

        // Add a few distant suns
        createDistantSuns(1);
        
        // make our first few star clusters so it's not totally empty
        for (int i = 0; i < 5; i++) {
            // same sphere math as before - makes things look natural
            float theta = random.nextFloat() * 2.0f * (float)Math.PI;  // pick a random direction
            float phi = (float)Math.acos(2.0f * random.nextFloat() - 1.0f);  // and angle
            float r = random.nextFloat() * 100.0f + 50.0f;  // put them between 50-150 units away
            
            Vector3f center = new Vector3f(
                r * (float)(Math.sin(phi) * Math.cos(theta)),
                r * (float)(Math.sin(phi) * Math.sin(theta)),
                r * (float)Math.cos(phi)
            );
            createStarCluster(center, random.nextInt(100, 300), random.nextFloat() * 30.0f + 20.0f);
        }
        
        // Add scattered white stars in the starting area
        createScatteredStars(new Vector3f(0, 0, 0), 200, 200.0f);
    }

    private void createStarCluster(Vector3f center, int numStars, float radius) {
        // gonna make a bunch of stars in a nice ball shape
        for (int i = 0; i < numStars; i++) {
            // ok so this math looks scary but it just makes stars spread out in a sphere
            float theta = random.nextFloat() * 2.0f * (float)Math.PI;  // spin around in a circle
            float phi = (float)Math.acos(2.0f * random.nextFloat() - 1.0f);  // up/down angle
            float r = random.nextFloat() * radius;  // how far from the center
            
            // figure out where to put the star using trig
            float x = center.x + r * (float)(Math.sin(phi) * Math.cos(theta));
            float y = center.y + r * (float)(Math.sin(phi) * Math.sin(theta));
            float z = center.z + r * (float)Math.cos(phi);
            
            // stars closer to the middle of the cluster are bigger and brighter
            float distanceFromCenter = r / radius;  // 0 = center, 1 = edge
            float size = (1.0f - distanceFromCenter * 0.5f) * (random.nextFloat() * 0.2f + 0.1f);
            float brightness = 1.0f - distanceFromCenter * 0.5f;
            
            // add new star as a clustered star (doesn't glimmer)
            stars.add(new Star(new Vector3f(x, y, z), size, brightness, false));
        }
    }

    private String getRegionKey(float x, float y, float z) {
        // this is like minecraft chunks but in space
        // we split space into big cubes and give each one a name
        // so we know which areas we've already filled with stars
        int regionX = (int) Math.floor(x / REGION_SIZE);
        int regionY = (int) Math.floor(y / REGION_SIZE);
        int regionZ = (int) Math.floor(z / REGION_SIZE);
        return regionX + "," + regionY + "," + regionZ;  // like coordinates but in a string
    }

    private void generateNewClusters() {
        int checkRadius = 1;  // how far around the player to check for empty space
        float minClusterDist = 100.0f;  // how far apart clusters need to be
        
        // figure out which chunk of space the player is in right now
        int playerRegionX = (int) Math.floor(cameraPos.x / REGION_SIZE);
        int playerRegionY = (int) Math.floor(cameraPos.y / REGION_SIZE);
        int playerRegionZ = (int) Math.floor(cameraPos.z / REGION_SIZE);
        
        // check surrounding regions
        for (int dx = -checkRadius; dx <= checkRadius; dx++) {
            for (int dy = -checkRadius; dy <= checkRadius; dy++) {
                for (int dz = -checkRadius; dz <= checkRadius; dz++) {
                    int regionX = playerRegionX + dx;
                    int regionY = playerRegionY + dy;
                    int regionZ = playerRegionZ + dz;
                    String regionKey = getRegionKey(regionX * REGION_SIZE, regionY * REGION_SIZE, regionZ * REGION_SIZE);
                    
                    // if region hasn't been generated yet
                    if (!generatedRegions.contains(regionKey)) {
                        generatedRegions.add(regionKey);
                        
                        // calculate region center
                        Vector3f regionCenter = new Vector3f(
                            regionX * REGION_SIZE + REGION_SIZE/2,
                            regionY * REGION_SIZE + REGION_SIZE/2,
                            regionZ * REGION_SIZE + REGION_SIZE/2
                        );
                        
                        // Add scattered white stars to this region
                        createScatteredStars(regionCenter, 80, REGION_SIZE * 0.8f);
                        
                        // Chance to generate a sun in this region (very rare)
                        if (random.nextFloat() < DispersionSettings.getSunRarity()) {
                            // Place suns farther away but still visible
                            float minDist = DispersionSettings.getSunMinDistance();
                            float maxDist = DispersionSettings.getSunMaxDistance();
                            createSun(regionCenter, random.nextFloat() * (maxDist - minDist) + minDist);
                        }
                        
                        // Chance to generate a nebula in this region (rare)
                        if (random.nextFloat() < DispersionSettings.getNebulaRarity()) {
                            createNebula(regionCenter);
                        }
                        
                        // generate clusters for this region
                        int numClusters = random.nextInt(1, 3); // 1-2 clusters per region
                        for (int c = 0; c < numClusters; c++) {
                            Vector3f potentialCenter = new Vector3f(
                                regionCenter.x + (random.nextFloat() - 0.5f) * REGION_SIZE,
                                regionCenter.y + (random.nextFloat() - 0.5f) * REGION_SIZE,
                                regionCenter.z + (random.nextFloat() - 0.5f) * REGION_SIZE
                            );
                            
                            // check distance from existing stars
                            boolean tooClose = false;
                            for (Star star : stars) {
                                if (star.position.distance(potentialCenter) < minClusterDist) {
                                    tooClose = true;
                                    break;
                                }
                            }
                            
                            if (!tooClose) {
                                // calculate cluster size based on distance from origin
                                float distanceFromOrigin = potentialCenter.length();
                                boolean isFarCluster = distanceFromOrigin > 2000.0f;
                                
                                int clusterSize = isFarCluster ? 
                                    random.nextInt(50, 100) : // fewer stars in far clusters
                                    random.nextInt(20, 50);   // fewer stars in closer clusters
                                
                                float clusterRadius = isFarCluster ?
                                    random.nextFloat() * 100.0f + 50.0f : // larger radius for far clusters
                                    random.nextFloat() * 20.0f + 10.0f;   // normal radius for closer clusters
                                
                                createStarCluster(potentialCenter, clusterSize, clusterRadius);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleInput() {
        // figure out how fast we should move
        float currentSpeed = SpeedSettings.getDefaultSpeed();
        
        // Check if speed amplification key is pressed
        if (ControlSettings.isAmplifySpeedPressed(window)) {
            currentSpeed = SpeedSettings.getAmplifiedSpeed();  // speedy
        }
        
        // Forward movement
        if (glfwGetKey(window, ControlSettings.getMoveForwardKey()) == GLFW_PRESS) {
            cameraPos.add(new Vector3f(cameraFront).mul(currentSpeed));
        }
        
        // Backward movement
        if (glfwGetKey(window, ControlSettings.getMoveBackwardKey()) == GLFW_PRESS) {
            cameraPos.sub(new Vector3f(cameraFront).mul(currentSpeed));
        }
        
        // Left movement
        if (glfwGetKey(window, ControlSettings.getMoveLeftKey()) == GLFW_PRESS) {
            cameraPos.sub(new Vector3f(cameraFront).cross(cameraUp).normalize().mul(currentSpeed));
        }
        
        // Right movement
        if (glfwGetKey(window, ControlSettings.getMoveRightKey()) == GLFW_PRESS) {
            cameraPos.add(new Vector3f(cameraFront).cross(cameraUp).normalize().mul(currentSpeed));
        }
        
        // Up movement
        if (glfwGetKey(window, ControlSettings.getMoveUpKey()) == GLFW_PRESS) {
            cameraPos.y += currentSpeed;
        }
        
        // Down movement
        if (glfwGetKey(window, ControlSettings.getMoveDownKey()) == GLFW_PRESS) {
            cameraPos.y -= currentSpeed;
        }
    }

    private void loop() {
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            handleInput();

            // update perspective and camera
            float aspect = (float) WIDTH / HEIGHT;
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), aspect, 0.1f, 10000.0f); // Increased far plane for distant visibility
            Matrix4f view = new Matrix4f().lookAt(
                    cameraPos,
                    new Vector3f(cameraPos).add(cameraFront),
                    cameraUp
            );

            // update and render stars
            for (Star star : stars) {
                star.update(); // Update glimmering state
                renderStar(star, view, projection);
            }
            
            // update and render shooting stars
            updateShootingStars(0.016f); // Approximate time for 60fps
            for (Stars.ShootingStar shootingStar : shootingStars) {
                shootingStar.render(view, projection);
            }
            
            // Random chance to create a shooting star
            if (random.nextFloat() < DispersionSettings.getShootingStarChance()) {
                shootingStars.add(Stars.createShootingStar(cameraPos, 500.0f, random));
            }
            
            // render nebulae
            for (Nebula nebula : nebulae) {
                nebula.update(0.016f); // Approximate time for 60fps
                nebula.render(view, projection);
            }
            
            // render suns
            for (Bodies.Sun sun : suns) {
                sun.update(0.016f); // Approximate time for 60fps
                sun.render(view, projection);
            }

            // generate new star clusters
            generateNewClusters();

            // draw coordinates in top left corner
            renderCoordinates();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderCoordinates() {
        // save the current matrices and set up orthographic projection for 2D rendering 
        glPushMatrix();
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // disable depth testing for UI elements
        glDisable(GL_DEPTH_TEST);

        // draw coordinate text with our SpaceNova font
        glColor3f(0.8f, 0.9f, 1.0f); // Light blue color for coordinates
        String coords = String.format("X: %.1f Y: %.1f Z: %.1f", cameraPos.x, cameraPos.y, cameraPos.z);
        
        try {
            // Try to use the SpaceNova font first
            coordinatesFontRenderer.renderText(coords, 10, 20, 20.0f);
        } catch (Exception e) {
            // Fall back to the original text rendering if there's an issue
            System.out.println("Falling back to original text rendering: " + e.getMessage());
            glLoadIdentity();
            glTranslatef(10, 20, 0);
            renderText(coords);
        }

        // restore previous state
        glEnable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private void renderText(String text) {
        // save current states
        glPushAttrib(GL_LIST_BIT);
        glDisable(GL_TEXTURE_2D);
        
        // use points to render text
        glPointSize(2.0f);
        float baseCharWidth = 8.0f;
        float wideCharWidth = 10.0f;
        float charHeight = 15.0f;
        boolean afterDecimal = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            glPushMatrix();
            // Use wider spacing before decimal point for numbers
            float currentWidth = (Character.isDigit(c) && !afterDecimal) ? wideCharWidth : baseCharWidth;
            glTranslatef(i * currentWidth, 0, 0);
            
            // Track if we're after the decimal point
            if (c == '.') {
                afterDecimal = true;
            }
            
            // Render each character using points
            switch (c) {
                case 'X': renderX(baseCharWidth, charHeight); break;
                case 'Y': renderY(baseCharWidth, charHeight); break;
                case 'Z': renderZ(baseCharWidth, charHeight); break;
                case '.': renderDot(baseCharWidth, charHeight); break;
                case ':': renderColon(baseCharWidth, charHeight); break;
                case '-': renderMinus(baseCharWidth, charHeight); break;
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    renderDigit(c - '0', baseCharWidth, charHeight);
                    break;
                case ' ': break; // Space character - do nothing
            }
            glPopMatrix();
        }
        
        // Restore states
        glPopAttrib();
    }

    private void renderDigit(int digit, float w, float h) {
        glBegin(GL_POINTS);
        switch (digit) {
            case 0: // Render '0'
                for (float y = 0; y < h; y += 2) {
                    glVertex2f(0, y);
                    glVertex2f(w-1, y);
                }
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, 0);
                    glVertex2f(x, h-1);
                }
                break;
            case 1: // Render '1'
                for (float y = 0; y < h; y += 2) {
                    glVertex2f(w/2, y);
                }
                break;
            case 2: // Render '2'
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, 0);    // Bottom
                    glVertex2f(x, h/2);   // Middle
                    glVertex2f(x, h-1);   // Top
                }
                for (float y = 0; y < h/2; y += 2) {
                    glVertex2f(w-1, y);   // Bottom right
                }
                for (float y = h/2; y < h; y += 2) {
                    glVertex2f(0, y);     // Top left
                }
                break;
            case 3: // Render '3'
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, 0);    // Bottom
                    glVertex2f(x, h/2);   // Middle
                    glVertex2f(x, h-1);   // Top
                }
                for (float y = 0; y < h; y += 2) {
                    glVertex2f(w-1, y);   // Right side
                }
                break;
            case 4: // Render '4'
                for (float y = 0; y < h/2; y += 2) {
                    glVertex2f(0, y);     // Bottom left
                }
                for (float y = 0; y < h; y += 2) {
                    glVertex2f(w-1, y);   // Right side
                }
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, h/2);   // Middle
                }
                break;
            case 5: // Render '5'
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, 0);    // Bottom
                    glVertex2f(x, h/2);   // Middle
                    glVertex2f(x, h-1);   // Top
                }
                for (float y = 0; y < h/2; y += 2) {
                    glVertex2f(0, y);     // Bottom left
                }
                for (float y = h/2; y < h; y += 2) {
                    glVertex2f(w-1, y);   // Top right
                }
                break;
            case 6: // Render '6'
                for (float y = 0; y < h; y += 2) {
                    glVertex2f(0, y);     // Left side
                }
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, 0);    // Bottom
                    glVertex2f(x, h/2);   // Middle
                }
                for (float y = 0; y < h/2; y += 2) {
                    glVertex2f(w-1, y);   // Bottom right
                }
                break;
            case 7: // Render '7'
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, 0);   // Top
                }
                for (float y = 0; y < h; y += 2) {
                    glVertex2f(w-1, y);   // Right side
                }
                break;
            case 8: // Render '8'
                for (float y = 0; y < h; y += 2) {
                    glVertex2f(0, y);     // Left side
                    glVertex2f(w-1, y);   // Right side
                }
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, 0);    // Bottom
                    glVertex2f(x, h/2);   // Middle
                    glVertex2f(x, h-1);   // Top
                }
                break;
            case 9: // Render '9'
                for (float y = h/2; y < h; y += 2) {
                    glVertex2f(w - 1, y);   // Left side
                }
                for (float x = 0; x < w; x += 2) {
                    glVertex2f(x, h-1);   // Top
                    glVertex2f(x, h/2);   // Middle
                }
                for (float y = h/2; y < h; y += 2) {
                    glVertex2f(0, y);     // Top right
                }
                break;
        }
        glEnd();
    }

    private void renderX(float w, float h) {
        glBegin(GL_POINTS);
        for (float t = 0; t < 1; t += 0.1f) {
            glVertex2f(t * w, t * h);
            glVertex2f(t * w, (1-t) * h);
        }
        glEnd();
    }

    private void renderY(float w, float h) {
        glBegin(GL_POINTS);
        for (float t = 0; t < 0.5f; t += 0.1f) {
            float y = (1 - t) * h - 0.6f * h;
            glVertex2f((0.5f + t) * w, y);
            glVertex2f((0.5f - t) * w, y);
        }
        for (float t = 0.5f; t < 1; t += 0.1f) {
            glVertex2f(w/2, t * h);
        }
        glEnd();
    }

    private void renderZ(float w, float h) {
        glBegin(GL_POINTS);
        for (float x = 0; x < w; x += 2) {
            glVertex2f(x, 0);
            glVertex2f(x, h-1);
        }
        for (float t = 0; t < 1; t += 0.1f) {
            glVertex2f((1-t) * w, t * h);
        }
        glEnd();
    }

    private void renderDot(float w, float h) {
        glBegin(GL_POINTS);
        glVertex2f(w/2, h-2);
        glEnd();
    }

    private void renderColon(float w, float h) {
        glBegin(GL_POINTS);
        glVertex2f(w/2, h/3);
        glVertex2f(w/2, 2*h/3);
        glEnd();
    }

    private void renderMinus(float w, float h) {
        glBegin(GL_POINTS);
        for (float x = 0; x < w-2; x += 2) {
            glVertex2f(x, h/2);
        }
        glEnd();
    }

    /**
     * Creates scattered white stars in a region around a center point
     * @param center The center point of the region
     * @param numStars How many stars to create
     * @param radius The radius of the region to scatter stars in
     */
    private void createScatteredStars(Vector3f center, int numStars, float radius) {
        for (int i = 0; i < numStars; i++) {
            // Use spherical coordinates to distribute stars evenly in 3D space
            float theta = random.nextFloat() * 2.0f * (float)Math.PI;  // random angle around y-axis
            float phi = (float)Math.acos(2.0f * random.nextFloat() - 1.0f);  // random angle from y-axis
            float r = random.nextFloat() * radius;  // random distance from center
            
            // Convert spherical to Cartesian coordinates
            float x = center.x + r * (float)(Math.sin(phi) * Math.cos(theta));
            float y = center.y + r * (float)(Math.sin(phi) * Math.sin(theta));
            float z = center.z + r * (float)Math.cos(phi);
            
            // Create a more visible white star with better size variation
            float size = random.nextFloat() * 0.15f + 0.05f;  // Increased size for better visibility
            float brightness = 0.85f + random.nextFloat() * 0.15f;  // Varied brightness for more natural look
            
            // Add as a scattered star (can glimmer)
            stars.add(new Star(new Vector3f(x, y, z), size, brightness, true));
        }
    }
    
    /**
     * Creates a colorful nebula in the region
     * @param regionCenter The center of the region where the nebula will be placed
     */
    private void createNebula(Vector3f regionCenter) {
        // Pick a random position within the region
        float offsetX = (random.nextFloat() - 0.5f) * REGION_SIZE * 0.5f;
        float offsetY = (random.nextFloat() - 0.5f) * REGION_SIZE * 0.5f;
        float offsetZ = (random.nextFloat() - 0.5f) * REGION_SIZE * 0.5f;
        
        Vector3f position = new Vector3f(regionCenter).add(offsetX, offsetY, offsetZ);
        
        // Random size between 100-300
        float size = random.nextFloat() * 200.0f + 100.0f;
        
        // Random number of particles
        int particles = random.nextInt(2000, 5000);
        
        // Pick a random nebula type
        Nebula.NebulaType[] types = Nebula.NebulaType.values();
        Nebula.NebulaType type = types[random.nextInt(types.length)];
        
        // Create the nebula
        Nebula nebula = new Nebula(position, size, particles, type, random);
        
        // Log the creation
        System.out.println(String.format("Created a %s nebula at (%6.1f %6.1f %6.1f)",
                type.toString(), position.x, position.y, position.z));
        
        nebulae.add(nebula);
    }
    
    /**
     * Creates distant suns far away from the starting position
     * @param count Number of suns to create
     */
    private void createDistantSuns(int count) {
        for (int i = 0; i < count; i++) {
            // Create suns far away but still visible
            float minDist = DispersionSettings.getSunMinDistance();
            float maxDist = DispersionSettings.getSunMaxDistance();
            float distance = random.nextFloat() * (maxDist - minDist) + minDist; // Use settings for distance
            float angle = random.nextFloat() * (float)Math.PI * 2.0f;
            float elevation = random.nextFloat() * (float)Math.PI - (float)Math.PI/2;
            
            // Convert spherical coordinates to cartesian
            float x = distance * (float)Math.cos(elevation) * (float)Math.cos(angle);
            float y = distance * (float)Math.cos(elevation) * (float)Math.sin(angle);
            float z = distance * (float)Math.sin(elevation);
            
            Vector3f position = new Vector3f(x, y, z);
            createSun(position, distance);
        }
    }
    
    /**
     * Creates a sun at the specified position
     * @param position Position for the sun
     * @param distance Distance from origin (affects size)
     */
    private void createSun(Vector3f position, float distance) {
        // Create a sun with size based on distance
        Bodies.Sun sun = Bodies.createRandomSun(position, random);
        suns.add(sun);
    }
    
    /**
     * Checks if a point is within the view frustum
     * @param point The point to check
     * @param view The view matrix
     * @param projection The projection matrix
     * @return True if the point is within the view frustum
     */
    public static boolean isPointInFrustum(Vector3f point, Matrix4f view, Matrix4f projection) {
        // Create a combined view-projection matrix
        Matrix4f viewProj = new Matrix4f(projection).mul(view);
        
        // Transform the point to clip space
        org.joml.Vector4f pointVec = new org.joml.Vector4f(point.x, point.y, point.z, 1.0f);
        org.joml.Vector4f clipSpace = viewProj.transform(pointVec, new org.joml.Vector4f());
        
        // Check if the point is within the clip space boundaries
        float w = clipSpace.w;
        
        // Point is behind the camera
        if (w <= 0) {
            return false;
        }
        
        // Normalize by w
        float nx = clipSpace.x / w;
        float ny = clipSpace.y / w;
        float nz = clipSpace.z / w;
        
        // Check if the point is within the normalized device coordinates (-1 to 1 for all axes)
        return nx >= -1 && nx <= 1 && ny >= -1 && ny <= 1 && nz >= -1 && nz <= 1;
    }
    /**
     * Update all shooting stars and remove dead ones
     */
    private void updateShootingStars(float deltaTime) {
        // Update all shooting stars
        for (int i = shootingStars.size() - 1; i >= 0; i--) {
            Stars.ShootingStar star = shootingStars.get(i);
            star.update(deltaTime);
            
            // Remove dead shooting stars
            if (star.isDead()) {
                shootingStars.remove(i);
            }
        }
    }
    
    private void renderStar(Star star, Matrix4f view, Matrix4f projection) {
        // Save the current drawing state
        glPushMatrix();
        
        // Convert matrices to OpenGL format
        float[] viewMatrix = new float[16];
        float[] projMatrix = new float[16];
        view.get(viewMatrix);
        projection.get(projMatrix);
        
        // Set up the projection and modelview matrices
        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(projMatrix);
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrixf(viewMatrix);
        
        // Move to where we want to draw the star
        glTranslatef(star.position.x, star.position.y, star.position.z);
        
        // Enable point smoothing for better-looking stars
        glEnable(GL_POINT_SMOOTH);
        glHint(GL_POINT_SMOOTH_HINT, GL_NICEST);
        
        // Make it look like an actual star
        glPointSize(star.size * 10);
        glColor4f(star.currentBrightness, star.currentBrightness, star.currentBrightness, 1.0f); // pure white color with glimmer effect
        glBegin(GL_POINTS);
        glVertex3f(0.0f, 0.0f, 0.0f);  // just a single point in space
        glEnd();
        
        // Disable point smoothing after rendering
        glDisable(GL_POINT_SMOOTH);
        
        // Restore the previous matrix state
        glPopMatrix();
    }

    public static void main(String[] args) {
        new GalaxySimulator().run();
    }
}
