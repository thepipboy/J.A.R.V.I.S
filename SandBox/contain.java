// CONTAIN MOD 

/*
  Contain mod - Adds containers, inventories, and storage systems
  Includes chests, barrels, and portable storage items
 */
public static class ContainMod {
    private Map<String, Container> containers;
    private Player player;
    private Container openContainer;
    
    public ContainMod(Player player) {
        this.player = player;
        this.containers = new ConcurrentHashMap<>();
    }
    
    public static class Container {
        private String containerId;
        private ContainerType type;
        private int size;
        private ItemStack[] items;
        private int x, y, z; // World position
        private boolean isOpen;
        
        public Container(ContainerType type, int x, int y, int z) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.containerId = x + "," + y + "," + z;
            this.size = type.getDefaultSize();
            this.items = new ItemStack[size];
            initializeEmptySlots();
        }
        
        private void initializeEmptySlots() {
            for (int i = 0; i < size; i++) {
                items[i] = new ItemStack(ItemType.EMPTY, 0);
            }
        }
        
        public boolean addItem(ItemType itemType, int quantity) {
            // First try to stack with existing items
            for (int i = 0; i < size; i++) {
                if (items[i].getItemType() == itemType && items[i].getQuantity() < items[i].getMaxStackSize()) {
                    int spaceLeft = items[i].getMaxStackSize() - items[i].getQuantity();
                    int toAdd = Math.min(quantity, spaceLeft);
                    items[i].setQuantity(items[i].getQuantity() + toAdd);
                    quantity -= toAdd;
                    
                    if (quantity <= 0) return true;
                }
            }
            
            // Then try empty slots
            for (int i = 0; i < size; i++) {
                if (items[i].getItemType() == ItemType.EMPTY) {
                    int toAdd = Math.min(quantity, itemType.getMaxStackSize());
                    items[i] = new ItemStack(itemType, toAdd);
                    quantity -= toAdd;
                    
                    if (quantity <= 0) return true;
                }
            }
            
            return quantity <= 0;
        }
        
        public ItemStack removeItem(int slot, int quantity) {
            if (slot < 0 || slot >= size || items[slot].getItemType() == ItemType.EMPTY) {
                return new ItemStack(ItemType.EMPTY, 0);
            }
            
            ItemStack stack = items[slot];
            int toRemove = Math.min(quantity, stack.getQuantity());
            ItemStack removed = new ItemStack(stack.getItemType(), toRemove);
            
            stack.setQuantity(stack.getQuantity() - toRemove);
            if (stack.getQuantity() <= 0) {
                items[slot] = new ItemStack(ItemType.EMPTY, 0);
            }
            
            return removed;
        }
        
        public void swapSlots(int slot1, int slot2) {
            if (slot1 >= 0 && slot1 < size && slot2 >= 0 && slot2 < size) {
                ItemStack temp = items[slot1];
                items[slot1] = items[slot2];
                items[slot2] = temp;
            }
        }
        
        // Getters
        public String getContainerId() { return containerId; }
        public ContainerType getType() { return type; }
        public int getSize() { return size; }
        public ItemStack[] getItems() { return items; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public boolean isOpen() { return isOpen; }
        public void setOpen(boolean open) { isOpen = open; }
    }
    
    public enum ContainerType {
        CHEST(27, "Chest"),
        BARREL(27, "Barrel"), 
        FURNACE(3, "Furnace"),
        CRAFTING_TABLE(9, "Crafting Table"),
        PLAYER_INVENTORY(36, "Inventory");
        
        private final int defaultSize;
        private final String displayName;
        
        ContainerType(int size, String name) {
            this.defaultSize = size;
            this.displayName = name;
        }
        
        public int getDefaultSize() { return defaultSize; }
        public String getDisplayName() { return displayName; }
    }
    
    public static class ItemStack {
        private ItemType itemType;
        private int quantity;
        
        public ItemStack(ItemType itemType, int quantity) {
            this.itemType = itemType;
            this.quantity = Math.min(quantity, itemType.getMaxStackSize());
        }
        
        public ItemType getItemType() { return itemType; }
        public int getQuantity() { return quantity; }
        public int getMaxStackSize() { return itemType.getMaxStackSize(); }
        public void setQuantity(int quantity) { 
            this.quantity = Math.min(quantity, getMaxStackSize()); 
        }
        
        public boolean isEmpty() {
            return itemType == ItemType.EMPTY || quantity <= 0;
        }
    }
    
    public enum ItemType {
        EMPTY(0, 0),
        STONE_BLOCK(1, 64),
        DIRT_BLOCK(2, 64),
        WOOD_BLOCK(3, 64),
        COBBLESTONE(4, 64),
        IRON_INGOT(5, 64),
        GOLD_INGOT(6, 64),
        DIAMOND(7, 64),
        CHEST_ITEM(8, 1),
        BARREL_ITEM(9, 1);
        
        private final int id;
        private final int maxStackSize;
        
        ItemType(int id, int maxStackSize) {
            this.id = id;
            this.maxStackSize = maxStackSize;
        }
        
        public int getId() { return id; }
        public int getMaxStackSize() { return maxStackSize; }
        
        public static ItemType fromId(int id) {
            for (ItemType type : values()) {
                if (type.id == id) return type;
            }
            return EMPTY;
        }
    }
    
    public void update(InputHandler input, World world) {
        handleContainerInteraction(input, world);
    }
    
    private void handleContainerInteraction(InputHandler input, World world) {
        // Open container with E key when looking at it
        if (input.isKeyPressed(GLFW_KEY_E)) {
            RaycastHit hit = player.getRaycastHit();
            if (hit != null) {
                Block block = hit.getBlock();
                if (isContainerBlock(block.getType())) {
                    openContainer(hit.getBlockX(), hit.getBlockY(), hit.getBlockZ());
                }
            }
        }
        
        // Close container with ESC
        if (input.isKeyPressed(GLFW_KEY_ESCAPE) && openContainer != null) {
            closeContainer();
        }
    }
    
    public void openContainer(int x, int y, int z) {
        String containerId = x + "," + y + "," + z;
        openContainer = containers.computeIfAbsent(containerId, 
            k -> new Container(ContainerType.CHEST, x, y, z));
        openContainer.setOpen(true);
        
        System.out.println("Opened " + openContainer.getType().getDisplayName() + " at " + x + "," + y + "," + z);
        displayContainerGUI(openContainer);
    }
    
    public void closeContainer() {
        if (openContainer != null) {
            openContainer.setOpen(false);
            System.out.println("Closed " + openContainer.getType().getDisplayName());
            openContainer = null;
        }
    }
    
    public void placeContainer(ContainerType type, int x, int y, int z) {
        String containerId = x + "," + y + "," + z;
        Container container = new Container(type, x, y, z);
        containers.put(containerId, container);
        
        // Convert block to container block
        BlockType blockType = getBlockTypeForContainer(type);
        World.getInstance().setBlock(x, y, z, blockType);
        
        System.out.println("Placed " + type.getDisplayName() + " at " + x + "," + y + "," + z);
    }
    
    private boolean isContainerBlock(BlockType blockType) {
        return blockType == BlockType.WOOD || blockType == BlockType.STONE; // Example container blocks
    }
    
    private BlockType getBlockTypeForContainer(ContainerType containerType) {
        return switch (containerType) {
            case CHEST, BARREL -> BlockType.WOOD;
            case FURNACE -> BlockType.STONE;
            default -> BlockType.STONE;
        };
    }
    
    private void displayContainerGUI(Container container) {
        // In a real implementation, this would open a GUI window
        System.out.println("=== " + container.getType().getDisplayName() + " Contents ===");
        for (int i = 0; i < container.getSize(); i++) {
            ItemStack stack = container.getItems()[i];
            if (!stack.isEmpty()) {
                System.out.println("Slot " + i + ": " + stack.getItemType() + " x" + stack.getQuantity());
            }
        }
    }
    
    // Utility methods
    public boolean hasOpenContainer() {
        return openContainer != null;
    }
    
    public Container getOpenContainer() {
        return openContainer;
    }
    
    public void transferItem(Container from, int fromSlot, Container to, int toSlot, int quantity) {
        ItemStack removed = from.removeItem(fromSlot, quantity);
        if (!removed.isEmpty()) {
            boolean added = to.addItem(removed.getItemType(), removed.getQuantity());
            if (!added) {
                // Couldn't add to destination, return to source
                from.addItem(removed.getItemType(), removed.getQuantity());
            }
        }
    }
}
