package com.galaxysim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

/**
 * Represents a colorful nebula in space - a large, visually impressive cloud of gas and dust.
 * Nebulae are rare but spectacular features in the galaxy.
 */
public class Nebula {
    private Vector3f center;           // Center position of the nebula
    private float radius;              // Overall radius of the nebula
    private List<NebulaParticle> particles;  // Particles that make up the nebula
    private List<NebulaParticle> backgroundCloud; // Background cloud effect
    private Vector4f baseColor;        // Base color of the nebula (RGBA)
    private Vector4f secondaryColor;   // Secondary color for variation
    private Vector4f cloudColor;       // Color for the background cloud
    private float rotationSpeed;       // How fast the nebula rotates
    private float currentRotation;     // Current rotation angle
    
    /**
     * Represents a single particle in the nebula cloud
     */
    private static class NebulaParticle {
        Vector3f position;     // Position relative to nebula center
        float size;           // Size of this particle
        float opacity;        // Opacity/transparency
        Vector4f color;       // Color of this particle (RGBA)
        
        NebulaParticle(Vector3f position, float size, float opacity, Vector4f color) {
            this.position = position;
            this.size = size;
            this.opacity = opacity;
            this.color = color;
        }
    }
    
    /**
     * Creates a new nebula with specified parameters
     * 
     * @param center Center position of the nebula in space
     * @param radius Overall radius of the nebula
     * @param particleCount Number of particles to generate for this nebula
     * @param nebulaType Type of nebula (affects color and appearance)
     * @param random Random number generator for consistent generation
     */
    public Nebula(Vector3f center, float radius, int particleCount, NebulaType nebulaType, Random random) {
        this.center = center;
        this.radius = radius;
        this.particles = new ArrayList<>();
        this.backgroundCloud = new ArrayList<>();
        this.currentRotation = 0.0f;
        this.rotationSpeed = (random.nextFloat() * 0.01f + 0.005f) * (random.nextBoolean() ? 1 : -1); // Slower rotation
        
        // Set colors based on nebula type - more vibrant colors
        switch (nebulaType) {
            case EMISSION:
                // Red/orange emission nebula - more vibrant
                this.baseColor = new Vector4f(1.0f, 0.2f, 0.1f, 0.7f);
                this.secondaryColor = new Vector4f(1.0f, 0.7f, 0.0f, 0.6f);
                this.cloudColor = new Vector4f(0.9f, 0.2f, 0.0f, 0.4f); // Prominent red cloud
                break;
            case REFLECTION:
                // Blue reflection nebula - more vibrant
                this.baseColor = new Vector4f(0.1f, 0.5f, 1.0f, 0.6f);
                this.secondaryColor = new Vector4f(0.4f, 0.8f, 1.0f, 0.5f);
                this.cloudColor = new Vector4f(0.0f, 0.3f, 0.8f, 0.4f); // Prominent blue cloud
                break;
            case PLANETARY:
                // Green/teal planetary nebula - more vibrant
                this.baseColor = new Vector4f(0.0f, 1.0f, 0.7f, 0.6f);
                this.secondaryColor = new Vector4f(0.3f, 0.7f, 1.0f, 0.5f);
                this.cloudColor = new Vector4f(0.0f, 0.7f, 0.4f, 0.4f); // Prominent green cloud
                break;
            case DARK:
                // Purple/dark nebula - more vibrant
                this.baseColor = new Vector4f(0.7f, 0.0f, 0.7f, 0.6f);
                this.secondaryColor = new Vector4f(1.0f, 0.0f, 1.0f, 0.5f);
                this.cloudColor = new Vector4f(0.5f, 0.0f, 0.6f, 0.4f); // Prominent purple cloud
                break;
            default:
                // Default to a colorful mix - more vibrant
                this.baseColor = new Vector4f(0.7f, 0.3f, 1.0f, 0.6f);
                this.secondaryColor = new Vector4f(0.3f, 0.6f, 1.0f, 0.5f);
                this.cloudColor = new Vector4f(0.4f, 0.2f, 0.7f, 0.4f); // Prominent purple/blue cloud
        }
        
        // Generate particles that make up the nebula
        generateParticles(particleCount, random);
        
        // Generate background cloud effect (more particles, larger area, more transparent)
        generateBackgroundCloud(particleCount * 2, random);
    }
    
    /**
     * Generates the particles that make up the nebula
     */
    /**
     * Generates the background cloud effect that sits behind the main nebula
     * This creates a more diffuse, larger cloud effect
     */
    private void generateBackgroundCloud(int count, Random random) {
        float cloudRadius = radius * 2.0f; // Cloud extends much further beyond the main nebula
        
        for (int i = 0; i < count; i++) {
            // Use cylindrical coordinates to create cloud-like nebulae
            float theta = random.nextFloat() * 2.0f * (float)Math.PI;
            
            // Make the cloud taller in the Y direction
            float heightFactor = 3.0f; // Even more vertical stretching for the cloud
            
            // Use a distribution that creates a cloud-like shape
            float distFactor = (float)Math.pow(random.nextFloat(), 1.2f);
            float r = cloudRadius * distFactor * 0.9f; // Horizontal radius
            
            // Generate y with more range in vertical direction
            float y = (random.nextFloat() * 2.0f - 1.0f) * cloudRadius * heightFactor * distFactor;
            
            // Convert cylindrical to Cartesian coordinates for x and z
            float x = r * (float)Math.cos(theta);
            float z = r * (float)Math.sin(theta);
            
            // Create position with some noise to make it less perfectly shaped
            Vector3f position = new Vector3f(
                x + (random.nextFloat() - 0.5f) * cloudRadius * 0.3f,
                y + (random.nextFloat() - 0.5f) * cloudRadius * 0.3f,
                z + (random.nextFloat() - 0.5f) * cloudRadius * 0.3f
            );
            
            // Size is much larger for cloud particles
            float size = (1.0f - distFactor * 0.5f) * (random.nextFloat() * 40.0f + 25.0f);
            
            // Cloud has moderate transparency for visibility
            float opacity = (1.0f - distFactor * 0.6f) * (random.nextFloat() * 0.3f + 0.15f);
            
            // Use the cloud color with some variation
            float colorVar = random.nextFloat() * 0.2f - 0.1f; // +/- 10%
            Vector4f color = new Vector4f(
                Math.max(0, Math.min(1, cloudColor.x + colorVar)),
                Math.max(0, Math.min(1, cloudColor.y + colorVar)),
                Math.max(0, Math.min(1, cloudColor.z + colorVar)),
                cloudColor.w * opacity
            );
            
            backgroundCloud.add(new NebulaParticle(position, size, opacity, color));
        }
    }
    
    private void generateParticles(int count, Random random) {
        for (int i = 0; i < count; i++) {
            // Use cylindrical coordinates to create taller nebulae
            float theta = random.nextFloat() * 2.0f * (float)Math.PI;
            
            // Make the nebula taller in the Y direction
            float heightFactor = 2.5f; // Vertical stretching factor
            
            // Use a distribution that clusters more particles toward the center
            // but still has some reaching the outer edges
            float distFactor = (float)Math.pow(random.nextFloat(), 1.5f);
            float r = radius * distFactor * 0.7f; // Horizontal radius
            
            // Generate y with more range in vertical direction
            float y = (random.nextFloat() * 2.0f - 1.0f) * radius * heightFactor * distFactor;
            
            // Convert cylindrical to Cartesian coordinates for x and z
            float x = r * (float)Math.cos(theta);
            float z = r * (float)Math.sin(theta);
            
            // Create position with some noise to make it less perfectly spherical
            Vector3f position = new Vector3f(
                x + (random.nextFloat() - 0.5f) * radius * 0.2f,
                y + (random.nextFloat() - 0.5f) * radius * 0.2f,
                z + (random.nextFloat() - 0.5f) * radius * 0.2f
            );
            
            // Size is larger toward the center
            float size = (1.0f - distFactor * 0.7f) * (random.nextFloat() * 15.0f + 10.0f); // Larger particles
            
            // Opacity is higher toward the center
            float opacity = (1.0f - distFactor * 0.5f) * (random.nextFloat() * 0.5f + 0.3f); // More opaque
            
            // Mix the two colors with random weighting
            float colorMix = random.nextFloat();
            Vector4f color = new Vector4f(
                baseColor.x * colorMix + secondaryColor.x * (1.0f - colorMix),
                baseColor.y * colorMix + secondaryColor.y * (1.0f - colorMix),
                baseColor.z * colorMix + secondaryColor.z * (1.0f - colorMix),
                baseColor.w * opacity
            );
            
            particles.add(new NebulaParticle(position, size, opacity, color));
        }
    }
    
    /**
     * Update the nebula (rotation, animation, etc.)
     * @param deltaTime Time passed since last update in seconds
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
     * Render the nebula
     * @param view The view matrix
     * @param projection The projection matrix
     */
    public void render(Matrix4f view, Matrix4f projection) {
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
        
        // Move to the nebula's center position
        glTranslatef(center.x, center.y, center.z);
        
        // Apply rotation around Y axis
        glRotatef((float)Math.toDegrees(currentRotation), 0.0f, 1.0f, 0.0f);
        
        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Disable depth writing (but keep depth testing) for proper transparency
        glDepthMask(false);
        
        // First draw the background cloud with additive blending for a glowing effect
        glBlendFunc(GL_SRC_ALPHA, GL_ONE); // Additive blending for glow effect
        
        glBegin(GL_POINTS);
        for (NebulaParticle particle : backgroundCloud) {
            glColor4f(
                particle.color.x,
                particle.color.y,
                particle.color.z,
                particle.color.w * particle.opacity
            );
            glPointSize(particle.size);
            glVertex3f(
                particle.position.x,
                particle.position.y,
                particle.position.z
            );
        }
        glEnd();
        
        // Switch back to alpha blending for the main particles
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Then draw the main nebula particles
        glBegin(GL_POINTS);
        for (NebulaParticle particle : particles) {
            glColor4f(
                particle.color.x,
                particle.color.y,
                particle.color.z,
                particle.color.w * particle.opacity
            );
            glPointSize(particle.size);
            glVertex3f(
                particle.position.x,
                particle.position.y,
                particle.position.z
            );
        }
        glEnd();
        
        // Restore depth mask
        glDepthMask(true);
        
        // Disable blending
        glDisable(GL_BLEND);
        
        // Restore the previous matrix state
        glPopMatrix();
    }
    
    /**
     * Get the center position of this nebula
     */
    public Vector3f getCenter() {
        return center;
    }
    
    /**
     * Get the radius of this nebula
     */
    public float getRadius() {
        return radius;
    }
    
    /**
     * Different types of nebulae with different colors and characteristics
     */
    public enum NebulaType {
        EMISSION,   // Red/orange emission nebula
        REFLECTION, // Blue reflection nebula
        PLANETARY,  // Green/teal planetary nebula
        DARK        // Purple/dark nebula
    }
}
