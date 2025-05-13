package PlayerShopWarp.playerShopWarp.models;

import org.bukkit.Location;

import java.util.UUID;

public class ShopWarp {
    
    private final String id;
    private final String name;
    private String description;
    private final UUID ownerId;
    private final String ownerName;
    private final Location location;
    private String category;
    
    public ShopWarp(String id, String name, String description, UUID ownerId, String ownerName, Location location) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.location = location;
        this.category = "Outros"; // Categoria padrão
    }
    
    public ShopWarp(String id, String name, String description, UUID ownerId, String ownerName, Location location, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.location = location;
        this.category = category;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public UUID getOwnerId() {
        return ownerId;
    }
    
    public String getOwnerName() {
        return ownerName;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
}