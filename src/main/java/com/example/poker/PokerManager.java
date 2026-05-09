package com.example.poker;

import com.example.poker.model.PokerTable;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;

public class PokerManager {
    private final PokerPlugin plugin;
    private final Map<UUID, PokerTable> tables = new HashMap<>();
    public PokerManager(PokerPlugin plugin) {
        this.plugin = plugin;
    }

    public PokerPlugin getPlugin() {
        return plugin;
    }

    public PokerTable createTable(Location loc, BlockFace face) {
        // 座標補正
        double x = Math.floor(loc.getX()) + 0.5;
        double y = Math.floor(loc.getY());
        double z = Math.floor(loc.getZ()) + 0.5;
        Location baseLoc = new Location(loc.getWorld(), x, y, z);

        UUID tableId = UUID.randomUUID();
        PokerTable table = new PokerTable(tableId, baseLoc, face);
        table.spawnEntities();
        tables.put(tableId, table);
        return table;
    }

    public PokerTable getTable(UUID tableId) {
        return tables.get(tableId);
    }

    public Collection<PokerTable> getTables() {
        return tables.values();
    }

    public void removeTable(UUID tableId) {
        PokerTable table = tables.remove(tableId);
        if (table != null) {
            table.removeEntities();
        }
    }

    public enum InputType {
        BET_RAISE, SET_BB
    }

    public static class InputState {
        public UUID tableId;
        public InputType type;
        public InputState(UUID tableId, InputType type) {
            this.tableId = tableId;
            this.type = type;
        }
    }

    private final Map<UUID, InputState> inputWaitPlayers = new HashMap<>();

    public void setPlayerInputWait(UUID playerId, UUID tableId, InputType type) {
        inputWaitPlayers.put(playerId, new InputState(tableId, type));
    }

    public void removePlayerInputWait(UUID playerId) {
        inputWaitPlayers.remove(playerId);
    }

    public InputState getPlayerInputWait(UUID playerId) {
        return inputWaitPlayers.get(playerId);
    }
}
