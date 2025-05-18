package com.galaxysim;

import org.lwjgl.BufferUtils;
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

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * font renderer for the coordinates display using the spacenova font
 */
public class CoordinatesFontRenderer {
    private int fontTexture;
    private STBTTBakedChar.Buffer charData;
    private final int BITMAP_WIDTH = 1024;
    private final int BITMAP_HEIGHT = 1024;
    private final int FIRST_CHAR = 32;
    private final int NUM_CHARS = 96;
    private float fontHeight;
    private boolean initialized = false;
    
    /**
     * Creates a new font renderer with the specified font height
     * 
     * @param fontHeight Base font height in pixels
     */
    public CoordinatesFontRenderer(float fontHeight) {
        this.fontHeight = fontHeight;
    }
    
    /**
     * initializes the font renderer with the spacenova font file
     */
    public void init() {
        if (initialized) return;
        
        // load font file using relative path for portability
        ByteBuffer fontData = null;
        try {
            // use a relative path that works regardless of where the application is run from
            Path fontPath = Paths.get("fonts/SpaceNova-6Rpd1.otf").toAbsolutePath();
            
            // if the font file doesn't exist at the relative path, try to find it in the classpath
            if (!Files.exists(fontPath)) {
                try {
                    // try to load from classpath resources
                    URL fontUrl = CoordinatesFontRenderer.class.getClassLoader().getResource("fonts/SpaceNova-6Rpd1.otf");
                    if (fontUrl != null) {
                        fontPath = Paths.get(fontUrl.toURI());
                    } else {
                        System.err.println("Font file not found in path or resources: " + fontPath);
                        throw new IOException("Font file not found");
                    } // wow these error messages are so helpful (ikr????)
                } catch (URISyntaxException e) {
                    System.err.println("Invalid URI for font resource: " + e.getMessage());
                    throw new IOException("Font resource URI error", e);
                }
            }
            
            System.out.println("Loading coordinates font from: " + fontPath);
            FileChannel fc = FileChannel.open(fontPath, StandardOpenOption.READ);
            fontData = BufferUtils.createByteBuffer((int) fc.size());
            fc.read(fontData);
            fc.close();
            fontData.flip();
        } catch (IOException e) {
            System.err.println("Failed to load SpaceNova font file: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        // create texture for font with goated filtering
        fontTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        
        // use mipmapping and better filtering for smoother text
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        
        // enable anisotropic filtering if available
        try {
            // using the correct constants for anisotropic filtering
            float maxAniso = glGetFloat(0x84FF); // GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
            glTexParameterf(GL_TEXTURE_2D, 0x84FE, maxAniso); // GL_TEXTURE_MAX_ANISOTROPY_EXT
        } catch (Exception e) {
            // anisotropic filtering not supported, IGNORE!!
        }
        
        // allocate texture bitmap
        ByteBuffer bitmap = BufferUtils.createByteBuffer(BITMAP_WIDTH * BITMAP_HEIGHT);
        
        // create STB font char data buffer
        charData = STBTTBakedChar.malloc(NUM_CHARS);
        
        // bake font to bitmap and wait for it to rise, then take it out of the oven
        STBTruetype.stbtt_BakeFontBitmap(
            fontData,
            fontHeight,
            bitmap,
            BITMAP_WIDTH,
            BITMAP_HEIGHT,
            FIRST_CHAR,
            charData
        );
        
        // upload bitmap to texture and generate mipmaps
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_WIDTH, BITMAP_HEIGHT, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
        
        // generate mipmaps if OpenGL 3.0+ is supported
        try {
            glGenerateMipmap(GL_TEXTURE_2D);
        } catch (Exception e) {
            // mipmapping not supported, IGNORE!!
            System.out.println("Mipmapping not supported, using basic texture filtering");
        }
        
        initialized = true;
    }
    
    /**
     * Renders text at the specified position with the specified font size
     * 
     * @param text Text to render
     * @param x X position
     * @param y Y position
     * @param fontSize Font size in pixels
     */

     // me when javadoc comments are annoying to write but otherwise my codebase is always lowk a mess
    public void renderText(String text, float x, float y, float fontSize) {
        if (!initialized) {
            init();
        }
        
        // enable texturing with anti-aliasing
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // enable anti-aliasing for smoother edges
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
                
                // Draw character quad
                glBegin(GL_QUADS);
                glTexCoord2f(q.s0(), q.t0()); glVertex2f(x0, y0);
                glTexCoord2f(q.s1(), q.t0()); glVertex2f(x1, y0);
                glTexCoord2f(q.s1(), q.t1()); glVertex2f(x1, y1);
                glTexCoord2f(q.s0(), q.t1()); glVertex2f(x0, y1);
                glEnd();
            }
        }
        
        // Disable texturing
        glDisable(GL_TEXTURE_2D);
    }
    
    /**
     * Calculates the width of a text string with the specified font size
     * 
     * @param text Text to measure
     * @param fontSize Font size in pixels
     * @return Width of the text in pixels
     */
    public float getTextWidth(String text, float fontSize) {
        if (!initialized) {
            init();
        }
        
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
    
    /**
     * Cleans up resources used by the font renderer
     */

     // wow much considerate memory management 
     // oblivion remake:
     
    public void cleanup() {
        if (charData != null) {
            charData.free();
        }
        glDeleteTextures(fontTexture);
        initialized = false;
    }
}
