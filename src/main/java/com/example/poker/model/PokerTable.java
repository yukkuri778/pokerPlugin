package com.example.poker.model;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.EntityType;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class PokerTable {
    private final UUID tableId;
    private final Location baseLocation;
    private final BlockFace facing;
    
    private TextDisplay controllerDisplay;
    private Interaction controllerInteraction;
    private final PokerSeat[] seats = new PokerSeat[5];
    private PokerGame game;

    public PokerTable(UUID tableId, Location baseLocation, BlockFace facing) {
        this.tableId = tableId;
        this.baseLocation = baseLocation;
        this.facing = facing;
        for (int i = 0; i < 5; i++) {
            seats[i] = new PokerSeat(i, getSeatLocation(baseLocation, facing, i));
        }
    }

    public void spawnEntities() {
        // コントローラーのスポーン
        controllerDisplay = (TextDisplay) baseLocation.getWorld().spawnEntity(baseLocation.clone().add(0, 1.5, 0), EntityType.TEXT_DISPLAY);
        controllerDisplay.text(Component.text("ポーカーテーブル\n[右クリックでメニューを開く]\n[SB: 10 | BB: 20]").color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
        controllerDisplay.setBillboard(Billboard.CENTER);

        controllerInteraction = (Interaction) baseLocation.getWorld().spawnEntity(baseLocation.clone().add(0, 1.5, 0), EntityType.INTERACTION);
        controllerInteraction.setInteractionWidth(1.5f);
        controllerInteraction.setInteractionHeight(1.5f);

        // 席のスポーン
        for (PokerSeat seat : seats) {
            if (seat.getDisplay() == null || !seat.getDisplay().isValid()) {
                Location seatLoc = seat.getLocation().clone().add(0, 1.0, 0);
                TextDisplay display = (TextDisplay) seatLoc.getWorld().spawnEntity(seatLoc, EntityType.TEXT_DISPLAY);
                
                if (seat.isEmpty()) {
                    display.text(Component.text("Seat " + seat.getId() + "\n[右クリックで参加]"));
                } else {
                    org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(seat.getPlayerId());
                    String name = p != null ? p.getName() : "Unknown";
                    display.text(Component.text("Seat " + seat.getId() + "\n[" + name + "]").color(net.kyori.adventure.text.format.NamedTextColor.WHITE));
                }
                
                display.setBillboard(Billboard.CENTER);
                seat.setDisplay(display);

                Interaction interaction = (Interaction) seatLoc.getWorld().spawnEntity(seatLoc, EntityType.INTERACTION);
                interaction.setInteractionWidth(1.0f);
                interaction.setInteractionHeight(1.0f);
                seat.setInteraction(interaction);
            }
        }
    }

    public void hideLobbyEntities() {
        if (controllerDisplay != null) { controllerDisplay.remove(); controllerDisplay = null; }
        if (controllerInteraction != null) { controllerInteraction.remove(); controllerInteraction = null; }
        for (PokerSeat seat : seats) {
            if (seat.getDisplay() != null) { seat.getDisplay().remove(); seat.setDisplay(null); }
            if (seat.getInteraction() != null) { seat.getInteraction().remove(); seat.setInteraction(null); }
        }
    }

    public void showLobbyEntities(int bbAmount) {
        hideLobbyEntities(); // 重複防止
        spawnEntities();
        updateControllerDisplay(bbAmount);
    }

    public void removeEntities() {
        hideLobbyEntities();
        if (game != null) {
            game.cleanup();
        }
    }

    public UUID getTableId() { return tableId; }
    public Location getBaseLocation() { return baseLocation; }
    public BlockFace getFacing() { return facing; }
    public PokerSeat[] getSeats() { return seats; }
    public PokerGame getGame() { return game; }
    public void setGame(PokerGame game) { this.game = game; }

    public Interaction getControllerInteraction() { return controllerInteraction; }

    public void updateControllerDisplay(int bbAmount) {
        if (controllerDisplay != null) {
            controllerDisplay.text(Component.text("ポーカーテーブル\n[右クリックでメニューを開く]\n[SB: " + (bbAmount / 2) + " | BB: " + bbAmount + "]").color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
        }
    }

    private Location getSeatLocation(Location base, BlockFace face, int id) {
        double dx = 0, dz = 0;
        switch (id) {
            case 0: dx = -1; dz = 6; break;
            case 1: dx = -4; dz = 3; break;
            case 2: dx = -5; dz = 0; break;
            case 3: dx = -4; dz = -3; break;
            case 4: dx = -1; dz = -6; break;
        }
        double rx = dx, rz = dz;
        switch (face) {
            case EAST: rx = dx; rz = dz; break;
            case WEST: rx = -dx; rz = -dz; break;
            case SOUTH: rx = -dz; rz = dx; break;
            case NORTH: rx = dz; rz = -dx; break;
            default: break;
        }
        return base.clone().add(rx, 0, rz);
    }

    public Location getDealerLocation() {
        double dx = 0, dz = 0;
        // facing方向へ +3 の位置
        switch (facing) {
            case EAST: dx = 3; break;
            case WEST: dx = -3; break;
            case SOUTH: dz = 3; break;
            case NORTH: dz = -3; break;
            default: break;
        }
        return baseLocation.clone().add(dx, 1.0, dz); // 少し浮かせる
    }

    public Location getCommunityCardLocation(int index) {
        // index: 0 ~ 4 (2が中央)
        // カードの間隔を0.9ブロックとする
        double spacing = 0.5;
        double offset = (index - 2) * spacing;
        
        double dx = 0, dz = 0;
        // プレイヤーから見て横に並べる。EAST向きのテーブルなら、Z軸方向に並べる
        switch (facing) {
            case EAST: dz = offset; break;
            case WEST: dz = -offset; break;
            case SOUTH: dx = -offset; break;
            case NORTH: dx = offset; break;
            default: dx = offset; break;
        }
        return baseLocation.clone().add(dx, 1.0, dz); // テーブルの高さに合わせる
    }

    public Location getHandCardLocation(int seatIndex, int cardIndex) {
        double dx = 0, dz = 0;
        switch (seatIndex) {
            case 0: dx = -1; dz = 4; break;
            case 1: dx = -2; dz = 2; break;
            case 2: dx = -3; dz = 0; break;
            case 3: dx = -2; dz = -2; break;
            case 4: dx = -1; dz = -4; break;
        }
        
        double rx = dx, rz = dz;
        switch (facing) {
            case EAST: rx = dx; rz = dz; break;
            case WEST: rx = -dx; rz = -dz; break;
            case SOUTH: rx = -dz; rz = dx; break;
            case NORTH: rx = dz; rz = -dx; break;
            default: break;
        }
        
        // 1枚目（基本位置）のLocation
        Location baseCardLoc = baseLocation.clone().add(rx, 1.0, rz);
        
        // 1. 現在地から中央(baseLocation)へ向かうベクトルを計算
        org.bukkit.util.Vector directionToCenter = baseLocation.toVector().subtract(baseCardLoc.toVector());
        directionToCenter.setY(0);
        if (directionToCenter.lengthSquared() > 0) {
            directionToCenter.normalize();
        } else {
            directionToCenter = new org.bukkit.util.Vector(1, 0, 0);
        }
        
        // 2. プレイヤーから見て「右」方向のベクトルを計算（時計回りに90度回転）
        // マイクラの座標系(+X=East, +Z=South)での時計回り90度回転は (x, z) -> (-z, x)
        org.bukkit.util.Vector rightVector = new org.bukkit.util.Vector(-directionToCenter.getZ(), 0, directionToCenter.getX());
        
        // 3. カードのオフセットを右方向に適用
        // 2枚のカードの中心が元の基準位置になるように、-0.5と+0.5に振り分ける
        double spacing = 0.55;
        double offset = (cardIndex - 0.5) * spacing;
        Location loc = baseCardLoc.clone().add(rightVector.clone().multiply(offset));
        
        // 4. エンティティの向きをセット
        // カードのお尻が中心を向いてしまうため、エンティティの向きをさらに180度反転させる
        loc.setDirection(rightVector.clone().multiply(-1));
        
        return loc;
    }
    
    public Location getBaseHandCardLocation(int seatIndex) {
        double dx = 0, dz = 0;
        switch (seatIndex) {
            case 0: dx = -1; dz = 4; break;
            case 1: dx = -2; dz = 2; break;
            case 2: dx = -3; dz = 0; break;
            case 3: dx = -2; dz = -2; break;
            case 4: dx = -1; dz = -4; break;
        }
        
        double rx = dx, rz = dz;
        switch (facing) {
            case EAST: rx = dx; rz = dz; break;
            case WEST: rx = -dx; rz = -dz; break;
            case SOUTH: rx = -dz; rz = dx; break;
            case NORTH: rx = dz; rz = -dx; break;
            default: break;
        }
        
        return baseLocation.clone().add(rx, 1.0, rz);
    }
}
