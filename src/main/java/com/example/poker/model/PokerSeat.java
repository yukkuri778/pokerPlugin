package com.example.poker.model;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import java.util.UUID;

public class PokerSeat {
    private final int id;
    private final Location location;
    private TextDisplay display;
    private Interaction interaction;
    private UUID playerId;

    public PokerSeat(int id, Location location) {
        this.id = id;
        this.location = location;
    }

    public int getId() { return id; }
    public Location getLocation() { return location; }
    
    public TextDisplay getDisplay() { return display; }
    public void setDisplay(TextDisplay display) { this.display = display; }
    
    public Interaction getInteraction() { return interaction; }
    public void setInteraction(Interaction interaction) { this.interaction = interaction; }

    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    
    public boolean isEmpty() { return playerId == null; }
}
