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
    private float cameraSpeed = 2.0f; // how fast we zoom around
    
    // mouse looking stuff - helps us look around smoothly
    private float yaw = -90.0f;      // looking left/right
    private float pitch = 0.0f;      // looking up/down
    private float lastX = WIDTH / 2.0f;  // last mouse x pos
    private float lastY = HEIGHT / 2.0f; // last mouse y pos

    // the actual space stuff
    private List<Star> stars;         // all our pretty stars
    private List<Stars.ShootingStar> shootingStars; // shooting stars with trails
    private List<Nebula> nebulae;     // colorful gas clouds
    private Random random;            // for making random stuff
    private Set<String> generatedRegions;  // keeps track of where the game has already made stars
    private static final float REGION_SIZE = 2000.0f;  // how big each chunk of space is
    private static final float NEBULA_RARITY = 0.05f;  // chance of a nebula spawning in a region (5% - very rare)
    private static final float SHOOTING_STAR_CHANCE = 0.02f; // 2% chance per frame to spawn a shooting star

    // this is what each star is made of
    private static class Star {
        Vector3f position;    // where the star is in space
        float size;          // how chunky the star is
        float brightness;    // how bright and shiny it is

        // when we make a new star, we gotta set all its properties
        Star(Vector3f position, float size, float brightness) {
            this.position = position;
            this.size = size;
            this.brightness = brightness;
        }
    }

    public void run() {
        // Show homescreen first
        Homescreen homescreen = new Homescreen();
        boolean startGame = homescreen.show();
        
        // Only start the game if the user clicked play
        if (startGame) {
            init();
            loop();

            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }
        
        // Clean up GLFW
        if (coordinatesFontRenderer != null) {
            coordinatesFontRenderer.cleanup();
        }
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // setup error handling first (in case stuff goes wrong)
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
        random = new Random();
        generatedRegions = new HashSet<>();

        // set cursor to center and hide it
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

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
        createScatteredStars(new Vector3f(0, 0, 0), 500, 200.0f);
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
            
            // add new star
            stars.add(new Star(new Vector3f(x, y, z), size, brightness));
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
                        createScatteredStars(regionCenter, 200, REGION_SIZE * 0.8f);
                        
                        // Chance to generate a nebula in this region (rare)
                        if (random.nextFloat() < NEBULA_RARITY) {
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
        float currentSpeed = cameraSpeed;
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || 
            glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS) {
            currentSpeed *= 5.0f;  // speedy
        }
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            cameraPos.add(new Vector3f(cameraFront).mul(currentSpeed));
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            cameraPos.sub(new Vector3f(cameraFront).mul(currentSpeed));
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            cameraPos.sub(new Vector3f(cameraFront).cross(cameraUp).normalize().mul(currentSpeed));
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            cameraPos.add(new Vector3f(cameraFront).cross(cameraUp).normalize().mul(currentSpeed));
        }
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            cameraPos.y += currentSpeed;
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
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

            // render stars
            for (Star star : stars) {
                renderStar(star, view, projection);
            }
            
            // update and render shooting stars
            updateShootingStars(0.016f); // Approximate time for 60fps
            for (Stars.ShootingStar shootingStar : shootingStars) {
                shootingStar.render(view, projection);
            }
            
            // Randomly spawn new shooting stars
            if (random.nextFloat() < SHOOTING_STAR_CHANCE) {
                shootingStars.add(Stars.createShootingStar(cameraPos, 500.0f, random));
            }
            
            // render nebulae
            for (Nebula nebula : nebulae) {
                nebula.update(0.016f); // Approximate time for 60fps
                nebula.render(view, projection);
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
            
            stars.add(new Star(new Vector3f(x, y, z), size, brightness));
        }
    }
    
    /**
     * Creates a colorful nebula in the region
     * @param regionCenter The center of the region where the nebula will be placed
     */
    private void createNebula(Vector3f regionCenter) {
        // Offset the nebula from the exact center of the region
        Vector3f nebulaCenter = new Vector3f(
            regionCenter.x + (random.nextFloat() - 0.5f) * REGION_SIZE * 0.5f,
            regionCenter.y + (random.nextFloat() - 0.5f) * REGION_SIZE * 0.5f,
            regionCenter.z + (random.nextFloat() - 0.5f) * REGION_SIZE * 0.5f
        );
        
        // Nebulae are extremely large - between 200% and 300% of a region's size
        float nebulaRadius = REGION_SIZE * (random.nextFloat() * 1.0f + 2.0f);
        
        // Number of particles depends on the size of the nebula
        int particleCount = (int)(nebulaRadius * 3.0f); // More particles for denser nebulae
        
        // Randomly select a nebula type
        Nebula.NebulaType[] nebulaTypes = Nebula.NebulaType.values();
        Nebula.NebulaType type = nebulaTypes[random.nextInt(nebulaTypes.length)];
        
        // Create the nebula and add it to our list
        nebulae.add(new Nebula(nebulaCenter, nebulaRadius, particleCount, type, random));
        
        System.out.println("Created a " + type + " nebula at " + nebulaCenter);
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
        glColor4f(star.brightness, star.brightness, star.brightness * 0.8f, 1.0f); // slight blue tint
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
