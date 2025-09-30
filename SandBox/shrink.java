// ==================== SHRINK MOD ====================

/**
 * Shrink mod - Allows player to shrink/grow in size
 * When shrunk: can enter small spaces, see world from different perspective
 */
public static class ShrinkMod {
    private Player player;
    private float originalEyeHeight;
    private float originalReachDistance;
    private float originalMoveSpeed;
    
    private float currentScale = 1.0f;
    private final float MIN_SCALE = 0.1f;
    private final float MAX_SCALE = 5.0f;
    private boolean isShrunk = false;
    
    public ShrinkMod(Player player) {
        this.player = player;
        this.originalEyeHeight = 1.7f;
        this.originalReachDistance = 5.0f;
        this.originalMoveSpeed = 5.0f;
    }
    
    public void update(InputHandler input) {
        handleShrinkInput(input);
        applyScaleEffects();
    }
    
    private void handleShrinkInput(InputHandler input) {
        // Shrink with R key
        if (input.isKeyPressed(GLFW_KEY_R) && !isShrunk) {
            shrink();
        }
        // Grow back with F key
        if (input.isKeyPressed(GLFW_KEY_F) && isShrunk) {
            grow();
        }
        // Fine control with mouse wheel
        if (input.isKeyPressed(GLFW_KEY_LEFT_CONTROL)) {
            double scrollY = input.getMouseDY(); // Using mouse Y as scroll simulation
            if (scrollY != 0) {
                adjustScale((float) (scrollY * 0.01));
            }
        }
    }
    
    public void shrink() {
        currentScale = 0.25f;
        isShrunk = true;
        System.out.println("Shrunk to " + currentScale + " scale!");
        
        // Visual effects
        triggerShrinkParticles();
    }
    
    public void grow() {
        currentScale = 1.0f;
        isShrunk = false;
        System.out.println("Grew back to normal size!");
        
        // Visual effects
        triggerGrowParticles();
    }
    
    public void adjustScale(float delta) {
        currentScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, currentScale + delta));
        System.out.println("Scale adjusted to: " + currentScale);
        
        if (currentScale < 1.0f) {
            isShrunk = true;
        } else if (currentScale > 1.0f) {
            isShrunk = false;
        }
    }
    
    private void applyScaleEffects() {
        // Adjust player properties based on scale
        float eyeHeight = originalEyeHeight * currentScale;
        float reachDistance = originalReachDistance * currentScale;
        float moveSpeed = originalMoveSpeed * (isShrunk ? 1.5f : 1.0f); // Faster when small
        
        // In a full implementation, these would be set on the player
        // player.setEyeHeight(eyeHeight);
        // player.setReachDistance(reachDistance);
        // player.setMoveSpeed(moveSpeed);
        
        // Scale collision detection
        scaleCollisionDetection();
    }
    
    private void scaleCollisionDetection() {
        // When shrunk, player can fit through 1-block gaps
        // When grown, player might need 2-block high spaces
        // This would modify the player's collision box
    }
    
    public boolean canFitInSpace(int width, int height) {
        return width >= currentScale && height >= currentScale;
    }
    
    public float getCurrentScale() {
        return currentScale;
    }
    
    public boolean isShrunk() {
        return isShrunk;
    }
    
    private void triggerShrinkParticles() {
        // Spawn particle effects around player
        System.out.println("SHRINK! *poof*");
    }
    
    private void triggerGrowParticles() {
        // Spawn particle effects around player
        System.out.println("GROW! *flash*");
    }
    
    // Special abilities when shrunk
    public boolean canEnterSmallSpaces() {
        return currentScale < 0.5f;
    }
    
    public boolean canSeeMicroDetails() {
        return currentScale < 0.3f;
    }
    
    public float getMiningSpeedMultiplier() {
        return isShrunk ? 0.5f : 1.0f; // Mine slower when small
    }
}