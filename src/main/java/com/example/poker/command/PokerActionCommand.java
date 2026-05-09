package com.example.poker.command;

import com.example.poker.PokerManager;
import com.example.poker.model.PokerTable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PokerActionCommand implements CommandExecutor {
    private final PokerManager manager;

    public PokerActionCommand(PokerManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length < 2) return true;

        UUID tableId;
        try {
            tableId = UUID.fromString(args[0]);
        } catch (IllegalArgumentException e) {
            return true;
        }

        PokerTable table = manager.getTable(tableId);
        if (table == null) return true;

        String action = args[1].toUpperCase();

        if (action.equals("FOLD") || action.equals("CHECK") || action.equals("CALL") || action.equals("ALLIN")) {
            if (table.getGame() != null) {
                table.getGame().handleAction(player.getUniqueId(), action, 0);
            }
        } else if (action.equals("RAISE")) {
            // レイズ額の入力を求める
            manager.setPlayerInputWait(player.getUniqueId(), tableId, PokerManager.InputType.BET_RAISE);
            player.sendMessage("チャットにレイズするチップ額を数字で入力してください。");
        } else if (action.equals("START")) {
            if (table.getGame() == null) {
                table.setGame(new com.example.poker.model.PokerGame(table, manager));
            }
            table.getGame().startGame();
        } else if (action.equals("SET_BB")) {
            manager.setPlayerInputWait(player.getUniqueId(), tableId, PokerManager.InputType.SET_BB);
            player.sendMessage("チャットに新しいBBの額を数字で入力してください。");
        } else if (action.equals("CLOSE")) {
            manager.removeTable(tableId);
            player.sendMessage("テーブルを閉じました。");
        } else if (action.equals("MENU")) {
            net.kyori.adventure.text.Component startAction = net.kyori.adventure.text.Component.text(" [ゲームスタート] ")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/pokeraction " + table.getTableId() + " START"));
                    
            net.kyori.adventure.text.Component setBbAction = net.kyori.adventure.text.Component.text(" [BB設定] ")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/pokeraction " + table.getTableId() + " SET_BB"));
                    
            net.kyori.adventure.text.Component closeAction = net.kyori.adventure.text.Component.text(" [テーブルを閉じる] ")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/pokeraction " + table.getTableId() + " CLOSE"));

            player.sendMessage(net.kyori.adventure.text.Component.text("--- Poker Controller ---").color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
            player.sendMessage(startAction.append(setBbAction).append(closeAction));
        } else if (action.equals("LEAVE")) {
            for (com.example.poker.model.PokerSeat seat : table.getSeats()) {
                if (seat.getPlayerId() != null && seat.getPlayerId().equals(player.getUniqueId())) {
                    seat.setPlayerId(null);
                    seat.getDisplay().text(net.kyori.adventure.text.Component.text("Seat " + seat.getId() + "\n[右クリックで参加]"));
                    player.sendMessage("席を立ちました。");
                    break;
                }
            }
        }

        return true;
    }
}
