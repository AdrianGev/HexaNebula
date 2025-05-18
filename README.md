# HexaNebula

A 3D galaxy simulator using GLFW and OpenGL, featuring a black void with randomly spawning stars that you can navigate through.

## Disclaimer

Please appreciate the fact that I actually wrote comments for every single class and method so it's easier to understand what's going on. If I'm being completely honest, I really hate writing comments and I'm trying to get better at it because it's a good practice. So that's that.

## Prerequisites

- Java JDK 17 or higher
- Maven
- LWJGL 3.3.2
- JOML 1.10.5
- STB Truetype
- STB Image
- FreeType
- The ability to bear with me

## Building and Running

To build and run the simulator, use the following commands in the project root directory:

```bash
# First, compile the project and copy dependencies
mvn clean compile dependency:copy-dependencies

# Then run it (macOS)
java -XstartOnFirstThread -cp "target/classes:target/dependency/*" com.galaxysim.GalaxySimulator
```

## Controls

### Keyboard
- W: Move forward in the direction you're looking
- S: Move backward
- A: Strafe left
- D: Strafe right
- Space: Move up
- Left Shift: Move down
- Control (hold): Move 5x faster

### Mouse
- Move mouse left/right: Look around horizontally
- Move mouse up/down: Look around vertically

Note: The mouse cursor is hidden and locked to the window for better control. Press Esc to exit.

## Features

- Black void environment
- 1000 initial randomly placed stars
- Continuous random star generation
- 3D navigation with WASD + Space/Shift controls
- Dynamic star rendering with varying sizes
- Being awesome
