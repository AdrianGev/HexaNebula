package com.galaxysim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;

public class Stars {
    
    /**
     * A shooting star that moves across the dark and endless abyss of outer space
     * (emo ahh description fr)
     * you know your vector crashout is bad when you start talking to yourself in commments lmao
     */
    public static class ShootingStar {
        private Vector3f direction;      // Direction of movement
        private float speed;            // Speed of movement
        private float lifetime;         // Total lifetime in seconds
        private float currentLifetime;  // Current lifetime
        private float trailLength;      // Length of the trail
        private int trailSegments;      // Number of segments in the trail
        private float[] trailWidths;    // Width of each trail segment
        private Vector4f headColor;     // Color of the shooting star head
        private Vector4f tailColor;     // Color at the end of the trail
        private float headSize;         // Size of the shooting star head
        
        private Vector3f position;      // Position of the shooting star
        
        public ShootingStar(Vector3f position, Vector3f direction, float speed, float lifetime, Random random) {
            this.position = position;
            this.direction = direction.normalize();
            this.speed = speed;
            this.lifetime = lifetime;
            this.currentLifetime = 0;
            this.trailLength = random.nextFloat() * 10.0f + 15.0f; // Longer trail (15-25 units)
            this.trailSegments = 20; // More segments for smoother trail
            this.trailWidths = new float[trailSegments];
            
            // Create varying widths for the trail segments
            for (int i = 0; i < trailSegments; i++) {
                float factor = (float)i / trailSegments;
                // Trail gets thinner toward the end
                this.trailWidths[i] = (1.0f - factor * 0.8f) * (random.nextFloat() * 0.5f + 1.5f);
            }
            
            // Choose a random color tint for the shooting star
            float colorType = random.nextFloat();
            if (colorType < 0.7f) {
                // 70% chance of white/blue
                this.headColor = new Vector4f(0.95f, 0.95f, 1.0f, 1.0f);
                this.tailColor = new Vector4f(0.6f, 0.7f, 1.0f, 0.0f);
            } else if (colorType < 0.85f) {
                // 15% chance of yellow/orange
                this.headColor = new Vector4f(1.0f, 0.9f, 0.7f, 1.0f);
                this.tailColor = new Vector4f(1.0f, 0.6f, 0.2f, 0.0f);
            } else {
                // 15% chance of green/blue
                this.headColor = new Vector4f(0.7f, 1.0f, 0.9f, 1.0f);
                this.tailColor = new Vector4f(0.2f, 0.5f, 0.8f, 0.0f);
            }
            
            // Size of the head
            this.headSize = random.nextFloat() * 0.1f + 0.15f;
        }
        
        public void update(float deltaTime) {
            // Update position based on direction and speed
            position.x += direction.x * speed * deltaTime;
            position.y += direction.y * speed * deltaTime;
            position.z += direction.z * speed * deltaTime;
            
            // Update lifetime
            currentLifetime += deltaTime;
        }
        
        public void render(Matrix4f view, Matrix4f projection) {
            // Don't render if lifetime is over
            if (currentLifetime >= lifetime) {
                return;
            }
            
            // Calculate alpha based on lifetime (fade in and out)
            float alpha;
            if (currentLifetime < lifetime * 0.1f) {
                // Faster fade in
                alpha = currentLifetime / (lifetime * 0.1f);
            } else if (currentLifetime > lifetime * 0.8f) {
                // Fade out
                alpha = 1.0f - (currentLifetime - lifetime * 0.8f) / (lifetime * 0.2f);
            } else {
                // Full brightness
                alpha = 1.0f;
            }
            
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
            
            // Enable blending for the trail effect - additive blending for glow
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            
            // Draw the trail as multiple segments for a more detailed effect
            for (int i = 0; i < trailSegments - 1; i++) {
                float startFactor = (float)i / trailSegments;
                float endFactor = (float)(i + 1) / trailSegments;
                
                // Calculate positions along the trail
                Vector3f startPos = new Vector3f(
                    position.x - direction.x * trailLength * startFactor,
                    position.y - direction.y * trailLength * startFactor,
                    position.z - direction.z * trailLength * startFactor
                );
                
                Vector3f endPos = new Vector3f(
                    position.x - direction.x * trailLength * endFactor,
                    position.y - direction.y * trailLength * endFactor,
                    position.z - direction.z * trailLength * endFactor
                );
                
                // Calculate colors and opacity along the trail
                float startAlpha = alpha * (1.0f - startFactor);
                float endAlpha = alpha * (1.0f - endFactor);
                
                // Interpolate between head and tail colors
                // (the head is brighter than the tail)
                Vector4f startColor = new Vector4f(
                    headColor.x * (1.0f - startFactor) + tailColor.x * startFactor,
                    headColor.y * (1.0f - startFactor) + tailColor.y * startFactor,
                    headColor.z * (1.0f - startFactor) + tailColor.z * startFactor,
                    startAlpha
                );
                
                Vector4f endColor = new Vector4f(
                    headColor.x * (1.0f - endFactor) + tailColor.x * endFactor,
                    headColor.y * (1.0f - endFactor) + tailColor.y * endFactor,
                    headColor.z * (1.0f - endFactor) + tailColor.z * endFactor,
                    endAlpha
                );
                
                // Set line width based on segment
                glLineWidth(trailWidths[i]);
                
                // Draw the trail segment
                glBegin(GL_LINES);
                glColor4f(startColor.x, startColor.y, startColor.z, startColor.w);
                glVertex3f(startPos.x, startPos.y, startPos.z);
                glColor4f(endColor.x, endColor.y, endColor.z, endColor.w);
                glVertex3f(endPos.x, endPos.y, endPos.z);
                glEnd();
            }
            
            // draw small particles along the trail for a sparkle effect
            glPointSize(2.0f);
            glBegin(GL_POINTS);
            for (int i = 0; i < 10; i++) {
                float factor = (float)i / 10.0f * 0.5f; // only along first half of trail
                float sparkleAlpha = alpha * (0.3f + (float)Math.random() * 0.7f); // random brightness
                
                glColor4f(headColor.x, headColor.y, headColor.z, sparkleAlpha);
                glVertex3f(
                    position.x - direction.x * trailLength * factor + (float)(Math.random() * 0.5f - 0.25f),
                    position.y - direction.y * trailLength * factor + (float)(Math.random() * 0.5f - 0.25f),
                    position.z - direction.z * trailLength * factor + (float)(Math.random() * 0.5f - 0.25f)
                );
            }
            glEnd();
            
            // draw the head of the shooting star with a glow
            // first draw a larger, more transparent glow
            glPointSize(headSize * 25);
            glColor4f(headColor.x, headColor.y, headColor.z, alpha * 0.3f);
            glBegin(GL_POINTS);
            glVertex3f(position.x, position.y, position.z);
            glEnd();
            
            // then draw the core
            glPointSize(headSize * 15);
            glColor4f(headColor.x, headColor.y, headColor.z, alpha);
            glBegin(GL_POINTS);
            glVertex3f(position.x, position.y, position.z);
            glEnd();
            
            // restore blend mode
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            
            // restore the previous matrix state
            glPopMatrix();
        }
        
        /**
         * check if this shooting star has completed its lifetime
         */
        public boolean isDead() {
            return currentLifetime >= lifetime;
        }
    }
    
    /**
     * create a shooting star at a random position with random direction
     */
    public static ShootingStar createShootingStar(Vector3f cameraPos, float distance, Random random) {
        // generate a random position around the camera
        float theta = random.nextFloat() * 2.0f * (float)Math.PI;
        float phi = (float)Math.acos(2.0f * random.nextFloat() - 1.0f);
        
        float x = distance * (float)Math.sin(phi) * (float)Math.cos(theta);
        float y = distance * (float)Math.sin(phi) * (float)Math.sin(theta);
        float z = distance * (float)Math.cos(phi);
        
        Vector3f position = new Vector3f(
            cameraPos.x + x,
            cameraPos.y + y,
            cameraPos.z + z
        );
        
        // generate a random direction that's not directly toward or away from the camera
        Vector3f toCamera = new Vector3f(cameraPos).sub(position).normalize();
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f right = new Vector3f(toCamera).cross(up).normalize();
        up = new Vector3f(right).cross(toCamera).normalize();
        
        float dirX = random.nextFloat() * 2.0f - 1.0f;
        float dirY = random.nextFloat() * 2.0f - 1.0f;
        
        Vector3f direction = new Vector3f(
            toCamera.x * 0.2f + right.x * dirX + up.x * dirY,
            toCamera.y * 0.2f + right.y * dirX + up.y * dirY,
            toCamera.z * 0.2f + right.z * dirX + up.z * dirY
        ).normalize();
        
        // speed between 100-300 units per second
        float speed = random.nextFloat() * 200.0f + 100.0f;
        
        // lifetime between 5-10 seconds
        float lifetime = random.nextFloat() * 5.0f + 5.0f;
        
        return new ShootingStar(position, direction, speed, lifetime, random);
    }
}
