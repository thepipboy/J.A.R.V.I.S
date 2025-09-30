package com.minecraftclone;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Complete Minecraft Clone in a Single Java File
 * Features: 3D World, Chunk System, Block Placement, First-Person Controls
 */
public class MinecraftClone {
    
    // ==================== ENUMS ====================
    
    public enum BlockType {
        AIR(0, false, false),
        GRASS(1, true, true),
        DIRT(2, true, true),
        STONE(3, true, true),
        SAND(4, true, true),
        WATER(5, false, false),
        WOOD(6, true, true),
        LEAVES(7, true, false),
        BEDROCK(8, true, true);
        
        private final int id;
        private final boolean solid;
        private final boolean opaque;
        
        BlockType(int id, boolean solid, boolean opaque) {
            this.id = id;
            this.solid = solid;
            this.opaque = opaque;
        }
        
        public int getId() { return id; }
        public boolean isSolid() { return solid; }
        public boolean isOpaque() { return opaque; }
        
        public static BlockType fromId(int id) {
            for (BlockType type : values()) {
                if (type.id == id) return type;
            }
            return AIR;
        }
    }
    
    public enum BlockFace {
        TOP, BOTTOM, NORTH, SOUTH, EAST, WEST
    }
    
    // ==================== BLOCK CLASS ====================
    
    public static class Block {
        private BlockType type;
        private int x, y, z;
        
        public Block(BlockType type, int x, int y, int z) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public BlockType getType() { return type; }
        public void setType(BlockType type) { this.type = type; }
        
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        
        public boolean isSolid() { return type.isSolid(); }
        public boolean isOpaque() { return type.isOpaque(); }
        
        public int[] getTextureCoords(BlockFace face) {
            return switch (type) {
                case GRASS -> face == BlockFace.TOP ? new int[]{0, 0} : 
                             face == BlockFace.BOTTOM ? new int[]{2, 0} : new int[]{3, 0};
                case DIRT -> new int[]{2, 0};
                case STONE -> new int[]{1, 0};
                case SAND -> new int[]{2, 1};
                case WATER -> new int[]{0, 1};
                case WOOD -> new int[]{4, 0};
                case LEAVES -> new int[]{4, 1};
                case BEDROCK -> new int[]{1, 1};
                default -> new int[]{0, 0};
            };
        }
    }
    
    // ==================== CHUNK CLASS ====================
    
    public static class Chunk {
        public static final int CHUNK_SIZE = 16;
        public static final int CHUNK_HEIGHT = 256;
        
        private int chunkX, chunkZ;
        private Block[][][] blocks;
        private boolean needsRebuild;
        private List<float[]> meshData;
        
        public Chunk(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blocks = new Block[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
            this.needsRebuild = true;
            this.meshData = new ArrayList<>();
            generateTerrain();
        }
        
        private void generateTerrain() {
            Random random = new Random(chunkX * 391279L + chunkZ * 918723L);
            
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    int worldX = chunkX * CHUNK_SIZE + x;
                    int worldZ = chunkZ * CHUNK_SIZE + z;
                    
                    double noise = (random.nextDouble() * 2 - 1) * 10;
                    int height = (int) (64 + noise);
                    
                    for (int y = 0; y < CHUNK_HEIGHT; y++) {
                        BlockType type = BlockType.AIR;
                        
                        if (y == 0) {
                            type = BlockType.BEDROCK;
                        } else if (y < height - 3) {
                            type = BlockType.STONE;
                        } else if (y < height) {
                            type = BlockType.DIRT;
                        } else if (y == height) {
                            type = BlockType.GRASS;
                        }
                        
                        blocks[x][y][z] = new Block(type, worldX, y, worldZ);
                    }
                }
            }
        }
        
        public Block getBlock(int x, int y, int z) {
            if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
                return null;
            }
            return blocks[x][y][z];
        }
        
        public void setBlock(int x, int y, int z, BlockType type) {
            if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
                blocks[x][y][z].setType(type);
                needsRebuild = true;
                
                if (x == 0) World.getInstance().markChunkForRebuild(chunkX - 1, chunkZ);
                if (x == CHUNK_SIZE - 1) World.getInstance().markChunkForRebuild(chunkX + 1, chunkZ);
                if (z == 0) World.getInstance().markChunkForRebuild(chunkX, chunkZ - 1);
                if (z == CHUNK_SIZE - 1) World.getInstance().markChunkForRebuild(chunkX, chunkZ + 1);
            }
        }
        
        public void rebuildMesh() {
            meshData.clear();
            
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_HEIGHT; y++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        Block block = blocks[x][y][z];
                        if (block.getType() != BlockType.AIR) {
                            addBlockToMesh(block, x, y, z);
                        }
                    }
                }
            }
            
            needsRebuild = false;
        }
        
        private void addBlockToMesh(Block block, int x, int y, int z) {
            for (BlockFace face : BlockFace.values()) {
                if (shouldRenderFace(block, face, x, y, z)) {
                    addFaceToMesh(block, face, x, y, z);
                }
            }
        }
        
        private boolean shouldRenderFace(Block block, BlockFace face, int x, int y, int z) {
            int adjX = x, adjY = y, adjZ = z;
            
            switch (face) {
                case TOP -> adjY++;
                case BOTTOM -> adjY--;
                case NORTH -> adjZ--;
                case SOUTH -> adjZ++;
                case EAST -> adjX++;
                case WEST -> adjX--;
            }
            
            Block adjacent = getBlock(adjX, adjY, adjZ);
            return adjacent == null || !adjacent.isOpaque();
        }
        
        private void addFaceToMesh(Block block, BlockFace face, int x, int y, int z) {
            int worldX = chunkX * CHUNK_SIZE + x;
            int worldZ = chunkZ * CHUNK_SIZE + z;
            
            float[][] vertices = getFaceVertices(face, worldX, y, worldZ);
            int[] textureCoords = block.getTextureCoords(face);
            
            for (float[] vertex : vertices) {
                meshData.add(new float[]{vertex[0], vertex[1], vertex[2]});
                meshData.add(new float[]{textureCoords[0], textureCoords[1]});
            }
        }
        
        private float[][] getFaceVertices(BlockFace face, float x, float y, float z) {
            return switch (face) {
                case TOP -> new float[][]{
                    {x, y + 1, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1},
                    {x, y + 1, z}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}
                };
                case BOTTOM -> new float[][]{
                    {x, y, z}, {x + 1, y, z + 1}, {x + 1, y, z},
                    {x, y, z}, {x, y, z + 1}, {x + 1, y, z + 1}
                };
                case NORTH -> new float[][]{
                    {x, y, z}, {x, y + 1, z}, {x + 1, y + 1, z},
                    {x, y, z}, {x + 1, y + 1, z}, {x + 1, y, z}
                };
                case SOUTH -> new float[][]{
                    {x, y, z + 1}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1},
                    {x, y, z + 1}, {x + 1, y, z + 1}, {x + 1, y + 1, z + 1}
                };
                case EAST -> new float[][]{
                    {x + 1, y, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1},
                    {x + 1, y, z}, {x + 1, y + 1, z + 1}, {x + 1, y, z + 1}
                };
                case WEST -> new float[][]{
                    {x, y, z}, {x, y + 1, z + 1}, {x, y + 1, z},
                    {x, y, z}, {x, y, z + 1}, {x, y + 1, z + 1}
                };
            };
        }
        
        public List<float[]> getMeshData() { return meshData; }
        public boolean needsRebuild() { return needsRebuild; }
        public int getChunkX() { return chunkX; }
        public int getChunkZ() { return chunkZ; }
    }
    
    // ==================== WORLD CLASS ====================
    
    public static class World {
        private static World instance;
        private Map<String, Chunk> chunks;
        private Set<String> chunksToRebuild;
        private ExecutorService chunkBuilder;
        
        private World() {
            chunks = new ConcurrentHashMap<>();
            chunksToRebuild = ConcurrentHashMap.newKeySet();
            chunkBuilder = Executors.newFixedThreadPool(2);
            startChunkBuilder();
        }
        
        public static World getInstance() {
            if (instance == null) {
                instance = new World();
            }
            return instance;
        }
        
        public Chunk getChunk(int chunkX, int chunkZ) {
            String key = chunkX + "," + chunkZ;
            return chunks.computeIfAbsent(key, k -> new Chunk(chunkX, chunkZ));
        }
        
        public Block getBlock(int worldX, int worldY, int worldZ) {
            if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
                return null;
            }
            
            int chunkX = worldX >> 4;
            int chunkZ = worldZ >> 4;
            int localX = worldX & 15;
            int localZ = worldZ & 15;
            
            Chunk chunk = getChunk(chunkX, chunkZ);
            return chunk.getBlock(localX, worldY, localZ);
        }
        
        public void setBlock(int worldX, int worldY, int worldZ, BlockType type) {
            if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
                return;
            }
            
            int chunkX = worldX >> 4;
            int chunkZ = worldZ >> 4;
            int localX = worldX & 15;
            int localZ = worldZ & 15;
            
            Chunk chunk = getChunk(chunkX, chunkZ);
            chunk.setBlock(localX, worldY, localZ, type);
            markChunkForRebuild(chunkX, chunkZ);
        }
        
        public void markChunkForRebuild(int chunkX, int chunkZ) {
            chunksToRebuild.add(chunkX + "," + chunkZ);
        }
        
        private void startChunkBuilder() {
            Thread builderThread = new Thread(() -> {
                while (true) {
                    try {
                        if (!chunksToRebuild.isEmpty()) {
                            String key = chunksToRebuild.iterator().next();
                            chunksToRebuild.remove(key);
                            
                            String[] parts = key.split(",");
                            int chunkX = Integer.parseInt(parts[0]);
                            int chunkZ = Integer.parseInt(parts[1]);
                            
                            Chunk chunk = getChunk(chunkX, chunkZ);
                            if (chunk.needsRebuild()) {
                                chunk.rebuildMesh();
                            }
                        }
                        Thread.sleep(16);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            builderThread.setDaemon(true);
            builderThread.start();
        }
        
        public void update() {
            // World update logic
        }
        
        public void cleanup() {
            chunkBuilder.shutdown();
        }
        
        public Map<String, Chunk> getChunks() {
            return chunks;
        }
    }
    
    // ==================== RAYCAST HIT ====================
    
    public static class RaycastHit {
        private Block block;
        private int blockX, blockY, blockZ;
        private Vector3f hitPoint;
        
        public RaycastHit(Block block, int x, int y, int z, Vector3f hitPoint) {
            this.block = block;
            this.blockX = x;
            this.blockY = y;
            this.blockZ = z;
            this.hitPoint = hitPoint;
        }
        
        public Block getBlock() { return block; }
        public int getBlockX() { return blockX; }
        public int getBlockY() { return blockY; }
        public int getBlockZ() { return blockZ; }
        public Vector3f getHitPoint() { return hitPoint; }
    }
    
    // ==================== PLAYER CLASS ====================
    
    public static class Player {
        private Vector3f position;
        private Vector3f rotation;
        private Vector3f velocity;
        
        private float moveSpeed = 5.0f;
        private float mouseSensitivity = 0.1f;
        private boolean onGround;
        
        private final float EYE_HEIGHT = 1.7f;
        private final float REACH_DISTANCE = 5.0f;
        
        public Player(World world) {
            position = new Vector3f(0, 70, 0);
            rotation = new Vector3f();
            velocity = new Vector3f();
            onGround = false;
        }
        
        public void update(InputHandler input) {
            handleMouseInput(input);
            handleKeyboardInput(input);
            applyPhysics();
        }
        
        private void handleMouseInput(InputHandler input) {
            if (input.isMouseLocked()) {
                rotation.x += (float) input.getMouseDY() * mouseSensitivity;
                rotation.y += (float) input.getMouseDX() * mouseSensitivity;
                
                rotation.x = Math.max(-90, Math.min(90, rotation.x));
            }
        }
        
        private void handleKeyboardInput(InputHandler input) {
            Vector3f moveDir = new Vector3f();
            
            if (input.isKeyPressed(GLFW_KEY_W)) {
                moveDir.z -= 1;
            }
            if (input.isKeyPressed(GLFW_KEY_S)) {
                moveDir.z += 1;
            }
            if (input.isKeyPressed(GLFW_KEY_A)) {
                moveDir.x -= 1;
            }
            if (input.isKeyPressed(GLFW_KEY_D)) {
                moveDir.x += 1;
            }
            if (input.isKeyPressed(GLFW_KEY_SPACE) && onGround) {
                velocity.y = 8.0f;
            }
            if (input.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
                moveSpeed = 8.0f;
            } else {
                moveSpeed = 5.0f;
            }
            
            if (moveDir.length() > 0) {
                moveDir.normalize();
                
                float yaw = (float) Math.toRadians(rotation.y);
                float x = moveDir.x * (float) Math.cos(yaw) - moveDir.z * (float) Math.sin(yaw);
                float z = moveDir.x * (float) Math.sin(yaw) + moveDir.z * (float) Math.cos(yaw);
                
                velocity.x = x * moveSpeed;
                velocity.z = z * moveSpeed;
            } else {
                velocity.x *= 0.8f;
                velocity.z *= 0.8f;
            }
        }
        
        private void applyPhysics() {
            if (!onGround) {
                velocity.y -= 0.3f;
            }
            
            position.add(velocity.mul(0.05f));
            
            checkCollisions();
            
            velocity.mul(0.9f);
        }
        
        private void checkCollisions() {
            onGround = false;
            
            Block feetBlock = World.getInstance().getBlock(
                (int) Math.floor(position.x),
                (int) Math.floor(position.y - 0.1f),
                (int) Math.floor(position.z)
            );
            
            if (feetBlock != null && feetBlock.isSolid()) {
                position.y = (float) Math.floor(position.y) + 1.1f;
                velocity.y = 0;
                onGround = true;
            }
            
            Block headBlock = World.getInstance().getBlock(
                (int) Math.floor(position.x),
                (int) Math.floor(position.y + EYE_HEIGHT + 0.1f),
                (int) Math.floor(position.z)
            );
            
            if (headBlock != null && headBlock.isSolid()) {
                position.y = (float) Math.floor(position.y + EYE_HEIGHT) - EYE_HEIGHT - 0.1f;
                velocity.y = Math.min(velocity.y, 0);
            }
        }
        
        public RaycastHit getRaycastHit() {
            Vector3f direction = getLookDirection();
            Vector3f start = new Vector3f(position).add(0, EYE_HEIGHT, 0);
            
            for (float t = 0; t < REACH_DISTANCE; t += 0.1f) {
                Vector3f point = new Vector3f(start).add(direction.mul(t));
                
                int blockX = (int) Math.floor(point.x);
                int blockY = (int) Math.floor(point.y);
                int blockZ = (int) Math.floor(point.z);
                
                Block block = World.getInstance().getBlock(blockX, blockY, blockZ);
                if (block != null && block.isSolid()) {
                    return new RaycastHit(block, blockX, blockY, blockZ, point);
                }
            }
            
            return null;
        }
        
        public Vector3f getLookDirection() {
            float yaw = (float) Math.toRadians(rotation.y);
            float pitch = (float) Math.toRadians(rotation.x);
            
            return new Vector3f(
                (float) (-Math.sin(yaw) * Math.cos(pitch)),
                (float) Math.sin(pitch),
                (float) (-Math.cos(yaw) * Math.cos(pitch))
            ).normalize();
        }
        
        public Vector3f getPosition() { return position; }
        public Vector3f getRotation() { return rotation; }
        public Vector3f getEyePosition() { return new Vector3f(position).add(0, EYE_HEIGHT, 0); }
        
        public int getSelectedBlock() {
            return 3; // Stone
        }
    }
    
    // ==================== INPUT HANDLER ====================
    
    public static class InputHandler {
        private boolean[] keys;
        private boolean[] mouseButtons;
        private double mouseX, mouseY;
        private double lastMouseX, lastMouseY;
        private double mouseDX, mouseDY;
        private boolean mouseLocked = false;
        
        private GLFWKeyCallback keyCallback;
        private GLFWMouseButtonCallback mouseButtonCallback;
        private GLFWCursorPosCallback cursorPosCallback;
        
        public InputHandler() {
            keys = new boolean[GLFW_KEY_LAST + 1];
            mouseButtons = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
            Arrays.fill(keys, false);
            Arrays.fill(mouseButtons, false);
        }
        
        public void init(long windowHandle) {
            keyCallback = glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
                if (key >= 0 && key < keys.length) {
                    keys[key] = action != GLFW_RELEASE;
                }
                
                if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                    mouseLocked = !mouseLocked;
                    glfwSetInputMode(window, GLFW_CURSOR, 
                        mouseLocked ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
                }
            });
            
            mouseButtonCallback = glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
                if (button >= 0 && button < mouseButtons.length) {
                    mouseButtons[button] = action != GLFW_RELEASE;
                }
            });
            
            cursorPosCallback = glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
                mouseX = xpos;
                mouseY = ypos;
            });
        }
        
        public void update() {
            mouseDX = mouseX - lastMouseX;
            mouseDY = mouseY - lastMouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        
        public boolean isKeyPressed(int keyCode) {
            return keyCode >= 0 && keyCode < keys.length && keys[keyCode];
        }
        
        public boolean isMouseButtonPressed(int button) {
            return button >= 0 && button < mouseButtons.length && mouseButtons[button];
        }
        
        public double getMouseX() { return mouseX; }
        public double getMouseY() { return mouseY; }
        public double getMouseDX() { return mouseDX; }
        public double getMouseDY() { return mouseDY; }
        public boolean isMouseLocked() { return mouseLocked; }
        
        public void cleanup() {
            if (keyCallback != null) keyCallback.free();
            if (mouseButtonCallback != null) mouseButtonCallback.free();
            if (cursorPosCallback != null) cursorPosCallback.free();
        }
    }
    
    // ==================== WINDOW CLASS ====================
    
    public static class Window {
        private long windowHandle;
        private String title;
        private int width, height;
        private boolean resized;
        
        public Window(String title, int width, int height) {
            this.title = title;
            this.width = width;
            this.height = height;
            this.resized = false;
        }
        
        public void init() {
            GLFWErrorCallback.createPrint(System.err).set();
            
            if (!glfwInit()) {
                throw new IllegalStateException("Unable to initialize GLFW");
            }
            
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
            
            windowHandle = glfwCreateWindow(width, height, title, NULL, NULL);
            if (windowHandle == NULL) {
                throw new RuntimeException("Failed to create GLFW window");
            }
            
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(windowHandle,
                (vidMode.width() - width) / 2,
                (vidMode.height() - height) / 2
            );
            
            glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> {
                this.width = w;
                this.height = h;
                this.resized = true;
                glViewport(0, 0, w, h);
            });
            
            glfwMakeContextCurrent(windowHandle);
            glfwSwapInterval(1);
            glfwShowWindow(windowHandle);
            
            GL.createCapabilities();
            
            glClearColor(0.6f, 0.8f, 1.0f, 1.0f);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        }
        
        public void update() {
            glfwSwapBuffers(windowHandle);
            glfwPollEvents();
        }
        
        public void cleanup() {
            glfwDestroyWindow(windowHandle);
            glfwTerminate();
            glfwSetErrorCallback(null).free();
        }
        
        public boolean shouldClose() {
            return glfwWindowShouldClose(windowHandle);
        }
        
        public long getWindowHandle() {
            return windowHandle;
        }
        
        public int getWidth() {
            return width;
        }
        
        public int getHeight() {
            return height;
        }
        
        public boolean isResized() {
            return resized;
        }
        
        public void setResized(boolean resized) {
            this.resized = resized;
        }
    }
    
    // ==================== RENDERER CLASS ====================
    
    public static class GameRenderer {
        private int shaderProgram;
        private int projectionMatrixLocation;
        private int viewMatrixLocation;
        private int modelMatrixLocation;
        
        public void init() throws Exception {
            shaderProgram = createShaderProgram();
            
            projectionMatrixLocation = glGetUniformLocation(shaderProgram, "projectionMatrix");
            viewMatrixLocation = glGetUniformLocation(shaderProgram, "viewMatrix");
            modelMatrixLocation = glGetUniformLocation(shaderProgram, "modelMatrix");
            
            glEnable(GL_DEPTH_TEST);
        }
        
        private int createShaderProgram() {
            String vertexShaderSource = """
                #version 330 core
                layout (location = 0) in vec3 position;
                layout (location = 1) in vec2 texCoord;
                
                uniform mat4 projectionMatrix;
                uniform mat4 viewMatrix;
                uniform mat4 modelMatrix;
                
                out vec2 fragTexCoord;
                
                void main() {
                    gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
                    fragTexCoord = texCoord;
                }
                """;
                
            String fragmentShaderSource = """
                #version 330 core
                in vec2 fragTexCoord;
                out vec4 fragColor;
                
                uniform sampler2D textureSampler;
                
                void main() {
                    fragColor = texture(textureSampler, fragTexCoord);
                    if (fragTexCoord.y > 0.8) fragColor *= 1.2;
                    else if (fragTexCoord.y < 0.2) fragColor *= 0.7;
                }
                """;
                
            int vertexShader = glCreateShader(GL_VERTEX_SHADER);
            glShaderSource(vertexShader, vertexShaderSource);
            glCompileShader(vertexShader);
            
            int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
            glShaderSource(fragmentShader, fragmentShaderSource);
            glCompileShader(fragmentShader);
            
            shaderProgram = glCreateProgram();
            glAttachShader(shaderProgram, vertexShader);
            glAttachShader(shaderProgram, fragmentShader);
            glLinkProgram(shaderProgram);
            
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            
            return shaderProgram;
        }
        
        public void clear() {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }
        
        public void render(World world, Player player) {
            glUseProgram(shaderProgram);
            
            float aspectRatio = 1200.0f / 800.0f;
            float fov = 70.0f;
            float near = 0.1f;
            float far = 1000.0f;
            
            float[] projectionMatrix = createProjectionMatrix(fov, aspectRatio, near, far);
            glUniformMatrix4fv(projectionMatrixLocation, false, projectionMatrix);
            
            float[] viewMatrix = createViewMatrix(player);
            glUniformMatrix4fv(viewMatrixLocation, false, viewMatrix);
            
            for (Chunk chunk : world.getChunks().values()) {
                renderChunk(chunk);
            }
        }
        
        private void renderChunk(Chunk chunk) {
            if (chunk.getMeshData().isEmpty()) return;
            
            int vao = glGenVertexArrays();
            int vbo = glGenBuffers();
            
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            
            List<float[]> meshData = chunk.getMeshData();
            FloatBuffer buffer = BufferUtils.createFloatBuffer(meshData.size() * 5);
            for (float[] data : meshData) {
                buffer.put(data);
            }
            buffer.flip();
            
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
            
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);
            
            float[] modelMatrix = createModelMatrix(chunk.getChunkX() * Chunk.CHUNK_SIZE, 0, chunk.getChunkZ() * Chunk.CHUNK_SIZE);
            glUniformMatrix4fv(modelMatrixLocation, false, modelMatrix);
            
            glDrawArrays(GL_TRIANGLES, 0, meshData.size() / 2);
            
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
        }
        
        private float[] createProjectionMatrix(float fov, float aspect, float near, float far) {
            float yScale = (float) (1.0f / Math.tan(Math.toRadians(fov / 2f)));
            float xScale = yScale / aspect;
            float frustumLength = far - near;
            
            return new float[]{
                xScale, 0, 0, 0,
                0, yScale, 0, 0,
                0, 0, -((far + near) / frustumLength), -1,
                0, 0, -((2 * near * far) / frustumLength), 0
            };
        }
        
        private float[] createViewMatrix(Player player) {
            Vector3f eyePos = player.getEyePosition();
            Vector3f rotation = player.getRotation();
            
            float pitch = (float) Math.toRadians(rotation.x);
            float yaw = (float) Math.toRadians(rotation.y);
            
            Vector3f xAxis = new Vector3f();
            Vector3f yAxis = new Vector3f();
            Vector3f zAxis = new Vector3f();
            
            zAxis.x = (float) (Math.cos(yaw) * Math.cos(pitch));
            zAxis.y = (float) Math.sin(pitch);
            zAxis.z = (float) (Math.sin(yaw) * Math.cos(pitch));
            zAxis.normalize();
            
            xAxis.x = (float) Math.cos(yaw - Math.PI / 2);
            xAxis.z = (float) Math.sin(yaw - Math.PI / 2);
            xAxis.normalize();
            
            yAxis = new Vector3f(zAxis).cross(xAxis);
            
            return new float[]{
                xAxis.x, yAxis.x, zAxis.x, 0,
                xAxis.y, yAxis.y, zAxis.y, 0,
                xAxis.z, yAxis.z, zAxis.z, 0,
                -xAxis.dot(eyePos), -yAxis.dot(eyePos), -zAxis.dot(eyePos), 1
            };
        }
        
        private float[] createModelMatrix(float x, float y, float z) {
            return new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                x, y, z, 1
            };
        }
        
        public void cleanup() {
            glDeleteProgram(shaderProgram);
        }
    }
    
    // ==================== MAIN GAME ENGINE ====================
    
    public static class GameEngine implements Runnable {
        private Thread gameThread;
        private boolean running = false;
        
        private Window window;
        private InputHandler input;
        private GameRenderer renderer;
        private World world;
        private Player player;
        
        private final int TARGET_FPS = 60;
        private final int TARGET_UPS = 20;
        
        public GameEngine() {
            window = new Window("Minecraft Clone", 1200, 800);
            input = new InputHandler();
            renderer = new GameRenderer();
        }
        
        public void start() {
            running = true;
            gameThread = new Thread(this, "GAME_LOOP");
            gameThread.start();
        }
        
        public void run() {
            try {
                init();
                gameLoop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }
        
        private void init() throws Exception {
            window.init();
            input.init(window.getWindowHandle());
            renderer.init();
            
            world = World.getInstance();
            player = new Player(world);
            
            System.out.println("Minecraft Clone initialized successfully!");
            System.out.println("Controls:");
            System.out.println("WASD - Move");
            System.out.println("Space - Jump");
            System.out.println("Shift - Sprint");
            System.out.println("Mouse - Look around");
            System.out.println("Left Click - Break block");
            System.out.println("Right Click - Place block");
            System.out.println("ESC - Toggle mouse lock");
        }
        
        private void gameLoop() {
            long initialTime = System.nanoTime();
            final double timeU = 1000000000.0 / TARGET_UPS;
            final double timeF = 1000000000.0 / TARGET_FPS;
            double deltaU = 0, deltaF = 0;
            int frames = 0, ticks = 0;
            long timer = System.currentTimeMillis();
            
            while (running && !window.shouldClose()) {
                long currentTime = System.nanoTime();
                deltaU += (currentTime - initialTime) / timeU;
                deltaF += (currentTime - initialTime) / timeF;
                initialTime = currentTime;
                
                if (deltaU >= 1) {
                    input.update();
                    update();
                    ticks++;
                    deltaU--;
                }
                
                if (deltaF >= 1) {
                    render();
                    frames++;
                    deltaF--;
                }
                
                if (System.currentTimeMillis() - timer > 1000) {
                    System.out.printf("FPS: %d, UPS: %d%n", frames, ticks);
                    frames = 0;
                    ticks = 0;
                    timer += 1000;
                }
            }
        }
        
        private void update() {
            player.update(input);
            world.update();
            
            if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT)) {
                RaycastHit hit = player.getRaycastHit();
                if (hit != null) {
                    world.setBlock(hit.getBlockX(), hit.getBlockY(), hit.getBlockZ(), BlockType.AIR);
                }
            }
            if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT)) {
                RaycastHit hit = player.getRaycastHit();
                if (hit != null) {
                    Vector3f hitPoint = hit.getHitPoint();
                    Vector3f normal = new Vector3f(hitPoint)
                        .sub(hit.getBlockX() + 0.5f, hit.getBlockY() + 0.5f, hit.getBlockZ() + 0.5f)
                        .normalize();
                    
                    int placeX = hit.getBlockX() + (int) normal.x;
                    int placeY = hit.getBlockY() + (int) normal.y;
                    int placeZ = hit.getBlockZ() + (int) normal.z;
                    
                    world.setBlock(placeX, placeY, placeZ, BlockType.fromId(player.getSelectedBlock()));
                }
            }
        }
        
        private void render() {
            renderer.clear();
            renderer.render(world, player);
            window.update();
        }
        
        private void cleanup() {
            renderer.cleanup();
            world.cleanup();
            input.cleanup();
            window.cleanup();
        }
        
        public void stop() {
            running = false;
        }
    }
    
    // ==================== MAIN METHOD ====================
    
    public static void main(String[] args) {
        System.out.println("Starting Minecraft Clone...");
        
        try {
            GameEngine game = new GameEngine();
            game.start();
            
            // Wait for game thread to finish
            try {
                game.gameThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            game.stop();
        } catch (Exception e) {
            System.err.println("Failed to start game:");
            e.printStackTrace();
            System.exit(-1);
        }
    }
}