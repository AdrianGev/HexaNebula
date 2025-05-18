package com.galaxysim;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * Contains celestial bodies like large stars (suns)
 */
public class Bodies {
    
    /**
     * Represents a large star (sun) in the galaxy
     */
    public static class Sun {
        Vector3f position;
        float size;
        float rotationSpeed;
        float currentRotation;
        Vector3f color; // Vibrant orange color for the sun - used in renderEnhancedSun
        
        // Only orange suns as requested
        public enum SunType {
            ORANGE     // Orange sun
        }
        
        /**
         * Creates a new sun
         * @param position Position in 3D space
         * @param size Size of the sun
         * @param type Type of sun (determines color)
         * @param random Random number generator
         */
        public Sun(Vector3f position, float size, SunType type, Random random) {
            this.position = position;
            this.size = size;
            this.currentRotation = 0.0f;
            this.rotationSpeed = (random.nextFloat() * 0.005f + 0.001f) * (random.nextBoolean() ? 1 : -1);
            
            // Set color based on sun type (used for glow effects)
            // Only orange suns
            this.color = new Vector3f(1.0f, 0.6f, 0.2f); // Deep orange
        }
        
        /**
         * Update the sun (rotation, etc.)
         * @param deltaTime Time passed since last update
         */
        public void update(float deltaTime) {
            // Update rotation
            currentRotation += rotationSpeed * deltaTime;
            if (currentRotation > 2.0f * Math.PI) {
                currentRotation -= 2.0f * Math.PI;
            } else if (currentRotation < 0) {
                currentRotation += 2.0f * Math.PI;
            }
        }
        
        /**
         * Render the sun
         * @param view The view matrix
         * @param projection The projection matrix
         */
        public void render(Matrix4f view, Matrix4f projection) {
            // Calculate distance from camera to sun
            // Extract camera position from view matrix
            Vector3f cameraPosition = new Vector3f();
            view.getTranslation(cameraPosition);
            cameraPosition.negate(); // Camera position is negative of the translation
            
            // We don't need the center distance, only the backmost point distance
            
            // Calculate the distance from camera to the backmost point of the sun
            // We need to find the point on the sun's surface that's furthest from the camera
            Vector3f directionToSun = new Vector3f(position).sub(cameraPosition).normalize();
            Vector3f backPoint = new Vector3f(position).add(new Vector3f(directionToSun).mul(size));
            float backPointDistance = cameraPosition.distance(backPoint);
            
            // Calculate the minimum distance where the entire sun can be rendered
            // Based on user testing, the sun fully unrenders at 1507 units away
            // We'll start rendering 1507 units closer than before
            float minVisibleDistance = Math.max(0, size * 3.0f - 1507.0f); // Adjusted to render earlier
            
            // Only render the sun if the backmost point is beyond the minimum render distance
            if (backPointDistance >= minVisibleDistance) {
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
                
                // Move to the sun's position
                glTranslatef(position.x, position.y, position.z);
                
                // Apply rotation
                glRotatef((float)Math.toDegrees(currentRotation), 0.0f, 1.0f, 0.0f);
                
                // Enable blending for the glow effect
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE);
                
                // Disable depth writing (but keep depth testing) for proper transparency
                glDepthMask(false);
                
                // Draw the main sun with all its components at once
                renderEnhancedSun(size);
                
                // Re-enable depth writing
                glDepthMask(true);
                
                // Disable blending
                glDisable(GL_BLEND);
                
                // Restore the previous matrix state
                glPopMatrix();
            }
            // We don't render anything if we're too close
            // This prevents the glitchy effect of seeing parts of the sun render one at a time
        }
        
        /**
         * Render an enhanced sun with integrated corona effects on the 3D surface
         * @param radius Base radius of the sun
         */
        private void renderEnhancedSun(float radius) {
            // First render the main sun body with deep orange color
            renderSunCore(radius);
            
            // Then render the outer glow layers
            renderSunGlow(radius * 1.1f);
        }
        
        /**
         * Render the main body of the sun with deep orange color and surface details
         * @param radius Base radius of the sun
         */
        private void renderSunCore(float radius) {
            // Higher resolution for more detailed sun
            int segments = 64;
            
            // Create a texture-like pattern for the sun's surface
            float[][] colorPattern = new float[segments][segments];
            float[][] turbulencePattern = new float[segments][segments];
            
            // Use random to create consistent patterns
            Random rand = new Random(position.hashCode());
            
            // Generate base turbulence pattern for swirling effect
            generateTurbulencePattern(turbulencePattern, segments, rand);
            
            // Apply patterns to create final color map
            for (int i = 0; i < segments; i++) {
                for (int j = 0; j < segments; j++) {
                    // Base color intensity with turbulence
                    colorPattern[i][j] = 1.0f + (turbulencePattern[i][j] * 0.3f);
                    
                    // Add solar flares (bright spots)
                    if (rand.nextFloat() < 0.15f) { // 15% chance of a flare
                        float flareIntensity = rand.nextFloat() * 0.8f + 0.6f; // 0.6 to 1.4 brighter
                        colorPattern[i][j] += flareIntensity;
                        
                        // Extend flare to neighboring cells
                        spreadFlare(colorPattern, i, j, flareIntensity * 0.7f, segments, rand);
                    }
                    
                    // Add darker spots (sunspots)
                    if (rand.nextFloat() < 0.1f) {
                        float spotIntensity = -rand.nextFloat() * 0.3f - 0.1f; // 0.1 to 0.4 darker
                        colorPattern[i][j] += spotIntensity;
                    }
                    
                    // Ensure values are in valid range with high contrast
                    colorPattern[i][j] = Math.max(0.6f, Math.min(2.0f, colorPattern[i][j]));
                }
            }
            
            // Draw the main sun sphere with the deep orange pattern
            glBegin(GL_TRIANGLES);
            
            // Use the existing segments variable
            for (int i = 0; i < segments; i++) {
                float lat0 = (float) (Math.PI * (-0.5 + (double) i / segments));
                float z0 = (float) Math.sin(lat0);
                float zr0 = (float) Math.cos(lat0);
                
                float lat1 = (float) (Math.PI * (-0.5 + (double) (i + 1) / segments));
                float z1 = (float) Math.sin(lat1);
                float zr1 = (float) Math.cos(lat1);
                
                for (int j = 0; j < segments; j++) {
                    float lng0 = (float) (2 * Math.PI * (double) j / segments);
                    float lng1 = (float) (2 * Math.PI * (double) (j + 1) / segments);
                    
                    float x0 = (float) Math.cos(lng0);
                    float y0 = (float) Math.sin(lng0);
                    
                    float x1 = (float) Math.cos(lng1);
                    float y1 = (float) Math.sin(lng1);
                    
                    // Get color variations for each vertex
                    float c00 = colorPattern[i][j];
                    float c01 = colorPattern[i][(j+1) % segments];
                    float c10 = colorPattern[(i+1) % segments][j];
                    float c11 = colorPattern[(i+1) % segments][(j+1) % segments];
                    
                    // Triangle 1
                    setSolarCoreColor(c00);
                    glVertex3f(x0 * zr0 * radius, y0 * zr0 * radius, z0 * radius);
                    
                    setSolarCoreColor(c10);
                    glVertex3f(x0 * zr1 * radius, y0 * zr1 * radius, z1 * radius);
                    
                    setSolarCoreColor(c11);
                    glVertex3f(x1 * zr1 * radius, y1 * zr1 * radius, z1 * radius);
                    
                    // Triangle 2
                    setSolarCoreColor(c00);
                    glVertex3f(x0 * zr0 * radius, y0 * zr0 * radius, z0 * radius);
                    
                    setSolarCoreColor(c11);
                    glVertex3f(x1 * zr1 * radius, y1 * zr1 * radius, z1 * radius);
                    
                    setSolarCoreColor(c01);
                    glVertex3f(x1 * zr0 * radius, y1 * zr0 * radius, z0 * radius);
                }
            }
            
            glEnd();
        }
        

        
        /**
         * Render the sun's glow effect without affecting the core color
         * @param baseRadius Base radius from which to start the glow
         */
        private void renderSunGlow(float baseRadius) {
            // Create multiple transparent layers extending from the sun
            int layers = 5;
            int segments = 48;
            Random baseRand = new Random(position.hashCode() + 5000);
            
            // Generate a turbulence pattern for the glow
            float[][] turbulencePattern = new float[segments][segments];
            generateTurbulencePattern(turbulencePattern, segments, baseRand);
            
            for (int layer = 0; layer < layers; layer++) {
                float layerFactor = (float)(layer) / layers;
                float radius = baseRadius * (1.0f + layerFactor * 0.4f);
                
                // Alpha decreases with distance from sun
                float alpha = 0.35f * (1.0f - layerFactor * 0.8f);
                
                // Draw a transparent sphere for each glow layer
                glBegin(GL_TRIANGLES);
                
                for (int i = 0; i < segments; i++) {
                    float lat0 = (float) (Math.PI * (-0.5 + (double) i / segments));
                    float z0 = (float) Math.sin(lat0);
                    float zr0 = (float) Math.cos(lat0);
                    
                    float lat1 = (float) (Math.PI * (-0.5 + (double) (i + 1) / segments));
                    float z1 = (float) Math.sin(lat1);
                    float zr1 = (float) Math.cos(lat1);
                    
                    // Create a new random generator for this layer
                    Random rand = new Random(layer * 1000 + (int)(position.x * 100));
                    
                    for (int j = 0; j < segments; j++) {
                        float lng0 = (float) (2 * Math.PI * (double) j / segments);
                        float lng1 = (float) (2 * Math.PI * (double) (j + 1) / segments);
                        
                        float x0 = (float) Math.cos(lng0);
                        float y0 = (float) Math.sin(lng0);
                        
                        float x1 = (float) Math.cos(lng1);
                        float y1 = (float) Math.sin(lng1);
                        
                        // Get turbulence value for this position
                        float turbulence = 1.0f + turbulencePattern[i][j] * 0.3f;
                        
                        // Add some random variation to make it more interesting
                        float intensityVar = 1.0f + rand.nextFloat() * 0.5f;
                        
                        // Set color for each vertex with proper transparency
                        setSolarGlowColor(turbulence * intensityVar, alpha);
                        
                        // Triangle 1
                        glVertex3f(x0 * zr0 * radius, y0 * zr0 * radius, z0 * radius);
                        glVertex3f(x0 * zr1 * radius, y0 * zr1 * radius, z1 * radius);
                        glVertex3f(x1 * zr1 * radius, y1 * zr1 * radius, z1 * radius);
                        
                        // Triangle 2
                        glVertex3f(x0 * zr0 * radius, y0 * zr0 * radius, z0 * radius);
                        glVertex3f(x1 * zr1 * radius, y1 * zr1 * radius, z1 * radius);
                        glVertex3f(x1 * zr0 * radius, y1 * zr0 * radius, z0 * radius);
                    }
                }
                
                glEnd();
            }
            
            // Add some solar prominences (flares extending from the sun)
            renderSolarProminences(baseRadius);
        }
        
        /**
         * Render integrated surface features instead of separate prominences
         * @param baseRadius Base radius of the sun
         */
        private void renderSolarProminences(float baseRadius) {
            // Instead of rendering separate 2D prominences, we'll enhance the glow effect
            // with additional 3D surface detail that's integrated with the sun's sphere
            Random rand = new Random(position.hashCode() + 1000);
            int featureCount = 12 + rand.nextInt(8); // 12-20 surface features
            
            // Draw additional surface details as 3D elements on the sun's surface
            for (int i = 0; i < featureCount; i++) {
                // Random position on the sun's surface
                float phi = rand.nextFloat() * (float)Math.PI * 2.0f; // Longitude
                float theta = rand.nextFloat() * (float)Math.PI; // Latitude
                
                // Size of the feature
                float featureSize = baseRadius * (0.05f + rand.nextFloat() * 0.15f);
                
                // Calculate base position on sphere
                float sinTheta = (float)Math.sin(theta);
                float cosTheta = (float)Math.cos(theta);
                float sinPhi = (float)Math.sin(phi);
                float cosPhi = (float)Math.cos(phi);
                
                float baseX = baseRadius * sinTheta * cosPhi;
                float baseY = baseRadius * sinTheta * sinPhi;
                float baseZ = baseRadius * cosTheta;
                
                // Create a small 3D bump or depression on the surface
                boolean isBump = rand.nextBoolean();
                float bumpHeight = featureSize * (isBump ? 0.3f : -0.1f);
                
                // Draw the feature as a small 3D shape
                glBegin(GL_TRIANGLE_FAN);
                
                // Center point of the feature
                float centerX = baseX * (1.0f + (isBump ? 0.05f : -0.02f));
                float centerY = baseY * (1.0f + (isBump ? 0.05f : -0.02f));
                float centerZ = baseZ * (1.0f + (isBump ? 0.05f : -0.02f));
                
                // Color based on feature type
                if (isBump) {
                    // Bright spot - solar flare
                    glColor4f(1.0f, 0.9f, 0.5f, 0.8f);
                } else {
                    // Dark spot - sunspot
                    glColor4f(0.9f, 0.3f, 0.0f, 0.9f);
                }
                
                glVertex3f(centerX, centerY, centerZ); // Center of feature
                
                // Create points around the feature
                int points = 12;
                for (int j = 0; j <= points; j++) {
                    float angle = (float)(j * 2.0f * Math.PI / points);
                    
                    // Create a local coordinate system on the sphere surface
                    // We need a tangent plane at the feature center
                    float localX = (float)Math.cos(angle) * featureSize;
                    float localY = (float)Math.sin(angle) * featureSize;
                    
                    // Transform to global coordinates (this is a simplified approximation)
                    // We're creating a small perturbation from the base point on the sphere
                    float perturbX = localX * cosPhi - localY * sinPhi * cosTheta;
                    float perturbY = localX * sinPhi + localY * cosPhi * cosTheta;
                    float perturbZ = localY * sinTheta;
                    
                    float x = baseX + perturbX;
                    float y = baseY + perturbY;
                    float z = baseZ + perturbZ;
                    
                    // Normalize to keep on sphere surface (with slight bump/depression)
                    float length = (float)Math.sqrt(x*x + y*y + z*z);
                    float normalizedRadius = baseRadius + (j % 3 == 0 ? bumpHeight * 0.7f : bumpHeight);
                    
                    x = x / length * normalizedRadius;
                    y = y / length * normalizedRadius;
                    z = z / length * normalizedRadius;
                    
                    // Color varies slightly around the feature
                    if (isBump) {
                        float brightness = 0.8f + (j % 3) * 0.1f;
                        glColor4f(1.0f, brightness, brightness * 0.5f, 0.7f);
                    } else {
                        float darkness = 0.7f - (j % 3) * 0.1f;
                        glColor4f(darkness, darkness * 0.4f, 0.0f, 0.8f);
                    }
                    
                    glVertex3f(x, y, z);
                }
                
                glEnd();
            }
        }
        

        
        /**
         * Set the color for a point on the solar core surface - deep orange with variations
         * @param intensity Color intensity factor
         */
        private void setSolarCoreColor(float intensity) {
            // Apply the intensity to create realistic solar surface colors based on the sun's color
            if (intensity > 1.7f) {
                // Solar flares - bright yellow-orange (based on sun's color)
                glColor4f(1.0f, 0.9f, 0.4f, 1.0f);
            } else if (intensity > 1.4f) {
                // Very bright spots - yellow-orange (based on sun's color)
                glColor4f(1.0f, 0.8f, 0.3f, 1.0f);
            } else if (intensity > 1.1f) {
                // Bright spots - orange (based on sun's color)
                glColor4f(1.0f, 0.6f, 0.2f, 1.0f);
            } else if (intensity < 0.7f) {
                // Dark spots - deeper red-orange (sunspots) (based on sun's color)
                glColor4f(0.9f, 0.3f, 0.0f, 1.0f);
            } else {
                // Normal surface - fiery orange (based on sun's color)
                glColor4f(1.0f, 0.5f, 0.1f, 1.0f);
            }
        }
        
        /**
         * Set the color for a point on the solar glow - more transparent
         * @param intensity Color intensity factor
         * @param alpha Transparency value
         */
        private void setSolarGlowColor(float intensity, float alpha) {
            // Apply the intensity to create realistic glow colors
            if (intensity > 1.5f) {
                // Bright glow - yellowish
                glColor4f(1.0f, 0.9f, 0.5f, alpha);
            } else if (intensity > 1.2f) {
                // Medium glow - orange-yellow
                glColor4f(1.0f, 0.7f, 0.3f, alpha);
            } else {
                // Normal glow - orange-red
                glColor4f(1.0f, 0.4f, 0.1f, alpha);
            }
        }
        
        /**
         * Generate a turbulence pattern for swirling solar surface
         * @param pattern Pattern array to fill
         * @param size Size of the pattern
         * @param rand Random number generator
         */
        private void generateTurbulencePattern(float[][] pattern, int size, Random rand) {
            // Initialize with random noise
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    pattern[i][j] = rand.nextFloat() * 2.0f - 1.0f; // -1.0 to 1.0
                }
            }
            
            // Apply several passes of smoothing to create swirls
            for (int pass = 0; pass < 3; pass++) {
                float[][] temp = new float[size][size];
                
                // Copy pattern
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        temp[i][j] = pattern[i][j];
                    }
                }
                
                // Apply directional smoothing for swirl effect
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        // Create swirling effect by biasing the smoothing direction
                        float swirl = (float)Math.sin(i * 0.2f + j * 0.3f) * 0.5f;
                        
                        int i1 = (i + (int)(swirl * 3) + size) % size;
                        int j1 = (j + 1 + size) % size;
                        int i2 = (i - 1 + size) % size;
                        int j2 = (j + (int)(swirl * 2) + size) % size;
                        
                        pattern[i][j] = (temp[i][j] + temp[i1][j] + temp[i][j1] + temp[i2][j] + temp[i][j2]) / 5.0f;
                    }
                }
            }
        }
        
        /**
         * Spread a solar flare to neighboring cells
         * @param pattern Color pattern to modify
         * @param centerI Center row index
         * @param centerJ Center column index
         * @param intensity Intensity of the flare
         * @param size Size of the pattern
         * @param rand Random number generator
         */
        private void spreadFlare(float[][] pattern, int centerI, int centerJ, float intensity, int size, Random rand) {
            // Determine flare size
            int flareSize = rand.nextInt(3) + 2; // 2-4 cells radius
            
            // Spread flare with decreasing intensity
            for (int i = centerI - flareSize; i <= centerI + flareSize; i++) {
                for (int j = centerJ - flareSize; j <= centerJ + flareSize; j++) {
                    // Calculate distance from center
                    int di = Math.min(Math.abs(i - centerI), Math.min(i + size - centerI, centerI + size - i));
                    int dj = Math.min(Math.abs(j - centerJ), Math.min(j + size - centerJ, centerJ + size - j));
                    float distance = (float)Math.sqrt(di * di + dj * dj);
                    
                    // Apply flare with intensity falling off with distance
                    if (distance < flareSize) {
                        float falloff = 1.0f - (distance / flareSize);
                        int wrappedI = (i + size) % size;
                        int wrappedJ = (j + size) % size;
                        pattern[wrappedI][wrappedJ] += intensity * falloff * falloff;
                    }
                }
            }
        }
        
        /**
         * Get the position of this sun
         */
        public Vector3f getPosition() {
            return position;
        }
        
        /**
         * Get the size of this sun
         */
        public float getSize() {
            return size;
        }
    }
    
    /**
     * Creates a random sun
     * @param position Position for the sun
     * @param random Random number generator
     * @return A new sun with random properties
     */
    public static Sun createRandomSun(Vector3f position, Random random) {
        // Choose a random sun type
        Sun.SunType[] types = Sun.SunType.values();
        Sun.SunType type = types[random.nextInt(types.length)];
        
        // Create a massive sun (size between 500 and 1000) - 5x larger than before
        float size = random.nextFloat() * 500.0f + 500.0f;
        
        return new Sun(position, size, type, random);
    }
}
