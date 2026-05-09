package com.example.poker.listener;

import com.example.poker.PokerManager;
import com.example.poker.model.PokerSeat;
import com.example.poker.model.PokerTable;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.UUID;

public class PokerListener implements Listener {
    private final PokerManager manager;

    public PokerListener(PokerManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Interaction)) return;
        
        Player player = event.getPlayer();

        for (PokerTable table : manager.getTables()) {
            // コントローラーのクリック判定
            if (table.getControllerInteraction() != null && table.getControllerInteraction().equals(clicked)) {
                player.sendMessage(Component.text("--- Poker Controller ---").color(NamedTextColor.GOLD));
                
                Component startAction = Component.text(" [ゲームスタート] ")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " START"));
                        
                Component setBbAction = Component.text(" [BB設定] ")
                        .color(NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " SET_BB"));
                        
                Component closeAction = Component.text(" [テーブルを閉じる] ")
                        .color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " CLOSE"));

                player.sendMessage(startAction.append(setBbAction).append(closeAction));
                return;
            }

            // 席のクリック判定
            for (PokerSeat seat : table.getSeats()) {
                if (seat.getInteraction() != null && seat.getInteraction().equals(clicked)) {
                    if (seat.isEmpty()) {
                        // 他の席にすでに座っていないかチェック
                        boolean alreadySeated = false;
                        for (PokerTable t : manager.getTables()) {
                            for (PokerSeat s : t.getSeats()) {
                                if (player.getUniqueId().equals(s.getPlayerId())) {
                                    alreadySeated = true;
                                    break;
                                }
                            }
                        }
                        if (alreadySeated) {
                            player.sendMessage(Component.text("あなたはすでに席に座っています。").color(NamedTextColor.RED));
                            return;
                        }

                        seat.setPlayerId(player.getUniqueId());
                        seat.getDisplay().text(Component.text("Seat " + seat.getId() + "\n[" + player.getName() + "]"));
                        player.sendMessage(Component.text("席 " + seat.getId() + " に着席しました。"));
                    } else if (seat.getPlayerId().equals(player.getUniqueId())) {
                        seat.setPlayerId(null);
                        seat.getDisplay().text(Component.text("Seat " + seat.getId() + "\n[右クリックで参加]"));
                        player.sendMessage(Component.text("席 " + seat.getId() + " から離れました。"));
                    } else {
                        player.sendMessage(Component.text("その席はすでに他の人が座っています。").color(NamedTextColor.RED));
                    }
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        PokerManager.InputState inputState = manager.getPlayerInputWait(playerId);
        if (inputState != null) {
            event.setCancelled(true);
            
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            int amount;
            try {
                amount = Integer.parseInt(message.trim());
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("数値を入力してください！").color(NamedTextColor.RED));
                return;
            }

            if (amount <= 0) {
                player.sendMessage(Component.text("0より大きい数値を入力してください！").color(NamedTextColor.RED));
                return;
            }

            PokerTable table = manager.getTable(inputState.tableId);
            if (table == null) {
                manager.removePlayerInputWait(playerId);
                return;
            }

            if (inputState.type == PokerManager.InputType.BET_RAISE) {
                if (table.getGame() != null) {
                    // 同期処理でゲームのアクションを実行
                    manager.getPlugin().getServer().getScheduler().runTask(manager.getPlugin(), () -> {
                        table.getGame().handleAction(playerId, "RAISE", amount);
                    });
                }
            } else if (inputState.type == PokerManager.InputType.SET_BB) {
                if (table.getGame() == null) {
                    table.setGame(new com.example.poker.model.PokerGame(table, manager));
                }
                table.getGame().setBbAmount(amount);
                player.sendMessage(Component.text("BBを " + amount + " に設定しました。"));
            }

            manager.removePlayerInputWait(playerId);
        }
    }
}
