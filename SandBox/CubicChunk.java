// CUBIC CHUNKS MOD
/*
  CubicChunks mod - Implements cubic chunks instead of vertical columns
  Allows for infinite world height and depth
 */
public static class CubicChunksMod {
    private static final int CUBIC_CHUNK_SIZE = 16;
    private Map<String, CubicChunk> cubicChunks;
    private World world;
    
    public CubicChunksMod(World world) {
        this.world = world;
        this.cubicChunks = new ConcurrentHashMap<>();
    }
    
    public static class CubicChunk {
        private int chunkX, chunkY, chunkZ;
        private Block[][][] blocks;
        private boolean needsRebuild;
        private List<float[]> meshData;
        
        public CubicChunk(int chunkX, int chunkY, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.chunkZ = chunkZ;
            this.blocks = new Block[CUBIC_CHUNK_SIZE][CUBIC_CHUNK_SIZE][CUBIC_CHUNK_SIZE];
            this.needsRebuild = true;
            this.meshData = new ArrayList<>();
            generateCubicTerrain();
        }
        
        private void generateCubicTerrain() {
            Random random = new Random(chunkX * 391279L + chunkZ * 918723L + chunkY * 123456L);
            
            int worldBaseY = chunkY * CUBIC_CHUNK_SIZE;
            
            for (int x = 0; x < CUBIC_CHUNK_SIZE; x++) {
                for (int z = 0; z < CUBIC_CHUNK_SIZE; z++) {
                    int worldX = chunkX * CUBIC_CHUNK_SIZE + x;
                    int worldZ = chunkZ * CUBIC_CHUNK_SIZE + z;
                    
                    // 3D noise for more interesting terrain
                    double noise3D = (random.nextDouble() * 2 - 1) * 8;
                    double surfaceHeight = 64 + noise3D;
                    
                    for (int y = 0; y < CUBIC_CHUNK_SIZE; y++) {
                        int worldY = worldBaseY + y;
                        BlockType type = BlockType.AIR;
                        
                        if (worldY == -64) {
                            type = BlockType.BEDROCK;
                        } else if (worldY < surfaceHeight - 10) {
                            type = BlockType.STONE;
                        } else if (worldY < surfaceHeight - 3) {
                            type = BlockType.DIRT;
                        } else if (worldY < surfaceHeight) {
                            type = BlockType.DIRT;
                        } else if (worldY == (int)surfaceHeight) {
                            type = BlockType.GRASS;
                        } else if (worldY < surfaceHeight + 2 && random.nextDouble() < 0.3) {
                            // Generate some floating islands
                            type = BlockType.STONE;
                        }
                        
                        // Generate caves and overhangs
                        if (type != BlockType.AIR && type != BlockType.BEDROCK) {
                            double caveNoise = Math.sin(worldX * 0.1) * Math.cos(worldY * 0.1) * Math.sin(worldZ * 0.1);
                            if (caveNoise > 0.6 && worldY < surfaceHeight - 5) {
                                type = BlockType.AIR;
                            }
                        }
                        
                        blocks[x][y][z] = new Block(type, worldX, worldY, worldZ);
                    }
                }
            }
        }
        
        public Block getBlock(int x, int y, int z) {
            if (x < 0 || x >= CUBIC_CHUNK_SIZE || y < 0 || y >= CUBIC_CHUNK_SIZE || z < 0 || z >= CUBIC_CHUNK_SIZE) {
                return null;
            }
            return blocks[x][y][z];
        }
        
        public void setBlock(int x, int y, int z, BlockType type) {
            if (x >= 0 && x < CUBIC_CHUNK_SIZE && y >= 0 && y < CUBIC_CHUNK_SIZE && z >= 0 && z < CUBIC_CHUNK_SIZE) {
                blocks[x][y][z].setType(type);
                needsRebuild = true;
                
                // Mark neighboring chunks for rebuild
                if (x == 0) markNeighborForRebuild(chunkX - 1, chunkY, chunkZ);
                if (x == CUBIC_CHUNK_SIZE - 1) markNeighborForRebuild(chunkX + 1, chunkY, chunkZ);
                if (y == 0) markNeighborForRebuild(chunkX, chunkY - 1, chunkZ);
                if (y == CUBIC_CHUNK_SIZE - 1) markNeighborForRebuild(chunkX, chunkY + 1, chunkZ);
                if (z == 0) markNeighborForRebuild(chunkX, chunkY, chunkZ - 1);
                if (z == CUBIC_CHUNK_SIZE - 1) markNeighborForRebuild(chunkX, chunkY, chunkZ + 1);
            }
        }
        
        private void markNeighborForRebuild(int cx, int cy, int cz) {
            String key = cx + "," + cy + "," + cz;
            CubicChunk neighbor = cubicChunks.get(key);
            if (neighbor != null) {
                neighbor.needsRebuild = true;
            }
        }
        
        public void rebuildMesh() {
            meshData.clear();
            
            for (int x = 0; x < CUBIC_CHUNK_SIZE; x++) {
                for (int y = 0; y < CUBIC_CHUNK_SIZE; y++) {
                    for (int z = 0; z < CUBIC_CHUNK_SIZE; z++) {
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
            int worldX = chunkX * CUBIC_CHUNK_SIZE + x;
            int worldY = chunkY * CUBIC_CHUNK_SIZE + y;
            int worldZ = chunkZ * CUBIC_CHUNK_SIZE + z;
            
            float[][] vertices = getFaceVertices(face, worldX, worldY, worldZ);
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
        public int getChunkY() { return chunkY; }
        public int getChunkZ() { return chunkZ; }
    }
    
    public CubicChunk getCubicChunk(int chunkX, int chunkY, int chunkZ) {
        String key = chunkX + "," + chunkY + "," + chunkZ;
        return cubicChunks.computeIfAbsent(key, k -> new CubicChunk(chunkX, chunkY, chunkZ));
    }
    
    public Block getBlock(int worldX, int worldY, int worldZ) {
        int chunkX = worldX >> 4;
        int chunkY = worldY >> 4;
        int chunkZ = worldZ >> 4;
        int localX = worldX & 15;
        int localY = worldY & 15;
        int localZ = worldZ & 15;
        
        CubicChunk chunk = getCubicChunk(chunkX, chunkY, chunkZ);
        return chunk.getBlock(localX, localY, localZ);
    }
    
    public void setBlock(int worldX, int worldY, int worldZ, BlockType type) {
        int chunkX = worldX >> 4;
        int chunkY = worldY >> 4;
        int chunkZ = worldZ >> 4;
        int localX = worldX & 15;
        int localY = worldY & 15;
        int localZ = worldZ & 15;
        
        CubicChunk chunk = getCubicChunk(chunkX, chunkY, chunkZ);
        chunk.setBlock(localX, localY, localZ, type);
    }
}
