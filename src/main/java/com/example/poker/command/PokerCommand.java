package com.example.poker.command;

import com.example.poker.PokerManager;
import com.example.poker.model.PokerTable;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PokerCommand implements CommandExecutor {
    private final PokerManager manager;

    public PokerCommand(PokerManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("/poker create - テーブルを作成します");
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            BlockFace facing = normalizeFace(player.getFacing());
            PokerTable table = manager.createTable(player.getLocation(), facing);
            player.sendMessage("ポーカーテーブルを作成しました。(ID: " + table.getTableId().toString().substring(0, 8) + ", 向き: " + facing.name() + ")");
            return true;
        }

        return true;
    }

    private BlockFace normalizeFace(BlockFace face) {
        switch (face) {
            case NORTH: case NORTH_EAST: case NORTH_NORTH_EAST: case NORTH_WEST: case NORTH_NORTH_WEST:
                return BlockFace.NORTH;
            case SOUTH: case SOUTH_EAST: case SOUTH_SOUTH_EAST: case SOUTH_WEST: case SOUTH_SOUTH_WEST:
                return BlockFace.SOUTH;
            case WEST: case WEST_NORTH_WEST: case WEST_SOUTH_WEST:
                return BlockFace.WEST;
            case EAST: case EAST_NORTH_EAST: case EAST_SOUTH_EAST:
                return BlockFace.EAST;
            default:
                return BlockFace.EAST;
        }
    }
}
