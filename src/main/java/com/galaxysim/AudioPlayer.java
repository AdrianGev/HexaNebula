package com.galaxysim;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * audio player for handling background music
 */
public class AudioPlayer {
    private long device;
    private long context;
    private int source;
    private int buffer;
    private boolean initialized = false;
    
    /**
     * initializes the audio player
     */
    public void init() {
        if (initialized) return;
        
        // initialize openal
        device = alcOpenDevice((ByteBuffer) null);
        if (device == NULL) {
            System.err.println("failed to open the default openal device");
            return;
        }
        
        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        context = alcCreateContext(device, (IntBuffer) null);
        if (context == NULL) {
            System.err.println("failed to create openal context");
            return;
        }
        
        alcMakeContextCurrent(context);
        ALCapabilities alCaps = AL.createCapabilities(deviceCaps);
        
        if (!alCaps.OpenAL10) {
            System.err.println("openal 1.0 is not supported");
            return;
        }
        
        // create source and buffer
        source = alGenSources();
        buffer = alGenBuffers();
        
        initialized = true;
    }
    
    // Default volume level (0.0f to 1.0f)
    private float volume = 0.7f;
    
    /**
     * loads and plays a wav file on loop
     * 
     * @param filePath path to the wav file
     * @return true if successful, false otherwise
     */
    public boolean playWavFile(String filePath) {
        if (!initialized) {
            System.err.println("audio player not initialized");
            return false;
        }
        
        try {
            // use a relative path that works regardless of where the application is run from
            Path audioPath = Paths.get(filePath).toAbsolutePath();
            
            // if the audio file doesn't exist at the relative path, try to find it in the classpath
            if (!Files.exists(audioPath)) {
                try {
                    // try to load from classpath resources
                    URL audioUrl = AudioPlayer.class.getClassLoader().getResource(filePath);
                    if (audioUrl != null) {
                        audioPath = Paths.get(audioUrl.toURI());
                    } else {
                        System.err.println("audio file not found in path or resources: " + filePath);
                        return false;
                    }
                } catch (URISyntaxException e) {
                    System.err.println("invalid uri for audio resource: " + e.getMessage());
                    return false;
                }
            }
            
            System.out.println("loading audio from: " + audioPath);
            
            // load wav file
            WavData wavData = loadWavFile(audioPath);
            if (wavData == null) {
                System.err.println("failed to load wav file: " + audioPath);
                return false;
            }
            
            // upload to openal
            alBufferData(buffer, wavData.format, wavData.data, wavData.sampleRate);
            
            // configure source
            alSourcei(source, AL_BUFFER, buffer);
            alSourcei(source, AL_LOOPING, AL_TRUE); // enable looping
            alSourcef(source, AL_GAIN, volume);     // set volume to current level
            
            // play the sound
            alSourcePlay(source);
            
            return true;
        } catch (Exception e) {
            System.err.println("error playing wav file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * stops the currently playing audio
     */
    public void stop() {
        if (initialized) {
            alSourceStop(source);
        }
    }
    
    /**
     * sets the volume of the audio
     * 
     * @param volume value between 0.0 (silent) and 1.0 (full volume)
     */
    public void setVolume(float volume) {
        // clamp volume between 0 and 1
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        
        // update source volume if initialized
        if (initialized) {
            alSourcef(source, AL_GAIN, this.volume);
        }
    }
    
    /**
     * gets the current volume level
     * 
     * @return volume level between 0.0 and 1.0
     */
    public float getVolume() {
        return volume;
    }
    
    /**
     * cleans up openal resources
     */
    public void cleanup() {
        if (initialized) {
            stop();
            alDeleteSources(source);
            alDeleteBuffers(buffer);
            alcDestroyContext(context);
            alcCloseDevice(device);
            initialized = false;
        }
    }
    
    /**
     * data structure for wav file data
     */
    private static class WavData {
        public ShortBuffer data;
        public int format;
        public int sampleRate;
    }
    
    /**
     * loads a wav file
     * 
     * @param path path to the wav file
     * @return wav data or null if loading failed
     */
    private WavData loadWavFile(Path path) {
        try {
            ByteBuffer wavData = readFileToByteBuffer(path);
            if (wavData == null) return null;
            
            // parse wav header
            int channels = wavData.getShort(22);
            int sampleRate = wavData.getInt(24);
            int bitsPerSample = wavData.getShort(34);
            
            // find data chunk
            int dataOffset = 44; // default for standard wav files
            int dataSize = wavData.getInt(40);
            
            // create short buffer for audio data
            ShortBuffer audioBuffer = BufferUtils.createShortBuffer(dataSize / 2);
            
            // copy audio data
            for (int i = 0; i < dataSize / 2; i++) {
                audioBuffer.put(wavData.getShort(dataOffset + i * 2));
            }
            audioBuffer.flip();
            
            // determine format
            int format;
            if (channels == 1) {
                format = bitsPerSample == 8 ? AL_FORMAT_MONO8 : AL_FORMAT_MONO16;
            } else {
                format = bitsPerSample == 8 ? AL_FORMAT_STEREO8 : AL_FORMAT_STEREO16;
            }
            
            WavData result = new WavData();
            result.data = audioBuffer;
            result.format = format;
            result.sampleRate = sampleRate;
            
            return result;
        } catch (Exception e) {
            System.err.println("error loading wav file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * reads a file into a byte buffer
     * 
     * @param path path to the file
     * @return byte buffer containing the file data
     */
    private ByteBuffer readFileToByteBuffer(Path path) {
        try {
            FileChannel fc = FileChannel.open(path, StandardOpenOption.READ);
            ByteBuffer buffer = BufferUtils.createByteBuffer((int) fc.size());
            fc.read(buffer);
            fc.close();
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            System.err.println("failed to read file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
