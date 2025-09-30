
// ==================== MODIFIED GAME ENGINE WITH MODS ====================

public static class ModdedGameEngine extends GameEngine {
    private CubicChunksMod cubicChunksMod;
    private ShrinkMod shrinkMod;
    private ContainMod containMod;
    private boolean useCubicChunks = true;
    
    @Override
    protected void init() throws Exception {
        super.init();
        
        // Initialize mods
        cubicChunksMod = new CubicChunksMod(world);
        shrinkMod = new ShrinkMod(player);
        containMod = new ContainMod(player);
        
        System.out.println("Mods loaded: CubicChunks, Shrink, Contain");
        System.out.println("Additional Controls:");
        System.out.println("R - Shrink | F - Grow | Ctrl + Mouse - Fine scale control");
        System.out.println("E - Open container | ESC - Close container");
    }
    
    @Override
    protected void update() {
        super.update();
        
        // Update mods
        shrinkMod.update(input);
        containMod.update(input, world);
        
        // Handle mod-specific block interactions
        handleModBlockInteractions();
    }
    
    private void handleModBlockInteractions() {
        // Container placement
        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_RIGHT)) {
            RaycastHit hit = player.getRaycastHit();
            if (hit != null && player.getSelectedBlock() == 8) { // Chest item
                Vector3f hitPoint = hit.getHitPoint();
                Vector3f normal = new Vector3f(hitPoint)
                    .sub(hit.getBlockX() + 0.5f, hit.getBlockY() + 0.5f, hit.getBlockZ() + 0.5f)
                    .normalize();
                
                int placeX = hit.getBlockX() + (int) normal.x;
                int placeY = hit.getBlockY() + (int) normal.y;
                int placeZ = hit.getBlockZ() + (int) normal.z;
                
                containMod.placeContainer(ContainMod.ContainerType.CHEST, placeX, placeY, placeZ);
            }
        }
    }
    
    // Override world access to use cubic chunks if enabled
    public Block getBlock(int worldX, int worldY, int worldZ) {
        if (useCubicChunks) {
            return cubicChunksMod.getBlock(worldX, worldY, worldZ);
        } else {
            return world.getBlock(worldX, worldY, worldZ);
        }
    }
    
    public void setBlock(int worldX, int worldY, int worldZ, BlockType type) {
        if (useCubicChunks) {
            cubicChunksMod.setBlock(worldX, worldY, worldZ, type);
        } else {
            world.setBlock(worldX, worldY, worldZ, type);
        }
    }
    
    // Mod accessors
    public CubicChunksMod getCubicChunksMod() { return cubicChunksMod; }
    public ShrinkMod getShrinkMod() { return shrinkMod; }
    public ContainMod getContainMod() { return containMod; }
}

// ==================== UPDATED MAIN METHOD ====================

public static void main(String[] args) {
    System.out.println("Starting Minecraft Clone with Mods...");
    
    try {
        ModdedGameEngine game = new ModdedGameEngine();
        game.start();
        
        try {
            game.gameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        game.stop();
    } catch (Exception e) {
        System.err.println("Failed to start modded game:");
        e.printStackTrace();
        System.exit(-1);
    }
}