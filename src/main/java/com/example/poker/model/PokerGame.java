package com.example.poker.model;

import com.example.poker.PokerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

public class PokerGame {
    private final PokerTable table;
    private final PokerManager manager;

    // --- 手札ポップアップの設定 ---
    private final float HAND_POPUP_HEIGHT = 0.2f;   // 手札が浮かび上がる高さ
    private final float HAND_POPUP_SCALE = 1.2f;    // 手札が拡大されるスケール
    private final int HAND_POPUP_SPEED = 5;         // 手札のアニメーション速度（tick）

    private int bbAmount = 20; // デフォルト
    private int pot = 0;
    private int currentHighestBet = 0;

    private List<UUID> activePlayers = new ArrayList<>();
    private Map<UUID, PokerPlayer> playersData = new HashMap<>();

    private Deck deck;
    private List<Integer> communityCards = new ArrayList<>();
    private List<CardDisplay> communityCardDisplays = new ArrayList<>();
    private Map<UUID, List<CardDisplay>> handCardDisplays = new HashMap<>();
    
    private Map<UUID, org.bukkit.entity.Interaction> handInteractions = new HashMap<>();
    private Map<UUID, org.bukkit.entity.TextDisplay> handInfoDisplays = new HashMap<>();
    private Map<UUID, Boolean> hoverStates = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask hoverTask;

    private int btnIndex = 0;
    private int currentPlayerIndex = -1;

    public enum GameState {
        WAITING, PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN
    }
    private GameState state = GameState.WAITING;
    private boolean handsRevealed = false;

    public PokerGame(PokerTable table, PokerManager manager) {
        this.table = table;
        this.manager = manager;
    }

    public void setBbAmount(int bb) {
        this.bbAmount = bb;
        table.updateControllerDisplay(bb);
        broadcast("BBが " + bb + " に設定されました。(SB: " + (bb / 2) + ")");
    }

    public void startGame() {
        if (state != GameState.WAITING) return;
        
        activePlayers.clear();
        playersData.clear();
        pot = 0;
        currentHighestBet = 0;
        communityCards.clear();
        cleanup();

        
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective chipsObj = sb.getObjective("chips");

        // 着席しているプレイヤーから参加者を選定
        for (PokerSeat seat : table.getSeats()) {
            if (!seat.isEmpty()) {
                UUID uuid = seat.getPlayerId();
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    int chips = 0;
                    if (chipsObj != null) {
                        chips = chipsObj.getScore(p.getName()).getScore();
                    }
                    if (chips >= bbAmount * 2) {
                        activePlayers.add(uuid);
                        PokerPlayer pp = new PokerPlayer(uuid);
                        pp.setChips(chips);
                        pp.setStatus(PokerPlayer.Status.PLAYING);
                        playersData.put(uuid, pp);
                    } else {
                        p.sendMessage(Component.text("チップが足りないためゲームに参加できません。(最低 2BB)").color(NamedTextColor.RED));
                    }
                }
            }
        }

        if (activePlayers.size() < 2) {
            broadcast("参加可能なプレイヤーが2人未満のため、ゲームを開始できません。");
            return;
        }

        // BTN決定 (ランダム)
        btnIndex = new Random().nextInt(activePlayers.size());
        
        // ゲーム中はテーブルのコントローラーや座席の文字を消す
        table.hideLobbyEntities();
        
        startRound();
    }

    private void startRound() {
        state = GameState.PRE_FLOP;
        handsRevealed = false;
        deck = new Deck();
        deck.shuffle();

        broadcast("=== ゲームスタート ===");
        broadcast("BTN: " + Bukkit.getPlayer(activePlayers.get(btnIndex)).getName());
        // ゲーム開始のファンファーレ音
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 1.2f);

        // ブラインド支払い
        int sbIndex = (btnIndex + 1) % activePlayers.size();
        int bbIndex = (btnIndex + 2) % activePlayers.size();

        if (activePlayers.size() == 2) {
            // ヘッズアップの場合、BTNがSB、もう一人がBB
            sbIndex = btnIndex;
            bbIndex = (btnIndex + 1) % 2;
        }

        payBet(activePlayers.get(sbIndex), bbAmount / 2, "SB");
        payBet(activePlayers.get(bbIndex), bbAmount, "BB");
        currentHighestBet = bbAmount;

        final int finalBbIndex = bbIndex;
        currentPlayerIndex = -1;
        manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
            // 手札のカードIDを決定し、actedInPhaseを初期化
            for (UUID uuid : activePlayers) {
                PokerPlayer p = playersData.get(uuid);
                p.setActedInPhase(false);
                p.setHand(Arrays.asList(deck.drawCard(), deck.drawCard()));
            }

            int dealDelay = 0;
            // 1枚目 -> 2枚目の順で全プレイヤーに配るアニメーション
            for (int cardIdx = 0; cardIdx < 2; cardIdx++) {
                for (UUID uuid : activePlayers) {
                    PokerPlayer p = playersData.get(uuid);
                    final int cardId = p.getHand().get(cardIdx);
                    final int fCardIdx = cardIdx;
                    final int seatId = getSeatIndex(uuid);
                    
                    manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                        org.bukkit.Location targetLoc = table.getHandCardLocation(seatId, fCardIdx);
                        CardDisplay display = new CardDisplay(table.getDealerLocation(), cardId);
                        
                        handCardDisplays.computeIfAbsent(uuid, k -> new ArrayList<>()).add(display);
                        
                        manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                            display.moveTo(manager.getPlugin(), targetLoc, 20); // 1秒かけて移動
                            playSoundAll(Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
                        }, 3L);
                    }, dealDelay * 8L); // 8tick間隔で配る
                    dealDelay++;
                }
            }

            // アニメーション完了後にテキスト表示とアクション開始
            long totalWaitTicks = dealDelay * 8L + 25L;
            
            manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                for (UUID uuid : activePlayers) {
                    PokerPlayer p = playersData.get(uuid);
                    Player bp = Bukkit.getPlayer(uuid);
                    
                    int seatId = getSeatIndex(uuid);
                    org.bukkit.Location baseHandLoc = table.getBaseHandCardLocation(seatId);
                    
                    // 当たり判定を下げるためにY座標を-1.0する
                    org.bukkit.Location interactionLoc = baseHandLoc.clone().subtract(0, 1.0, 0);
                    org.bukkit.entity.Interaction interaction = (org.bukkit.entity.Interaction) interactionLoc.getWorld().spawnEntity(interactionLoc, org.bukkit.entity.EntityType.INTERACTION);
                    interaction.setInteractionWidth(1.2f);
                    interaction.setInteractionHeight(0.2f);
                    handInteractions.put(uuid, interaction);
                    
                    org.bukkit.Location textLoc = baseHandLoc.clone().add(0, 0.2, 0); // 少し上に
                    org.bukkit.entity.TextDisplay textDisplay = (org.bukkit.entity.TextDisplay) textLoc.getWorld().spawnEntity(textLoc, org.bukkit.entity.EntityType.TEXT_DISPLAY);
                    textDisplay.text(Component.text(""));
                    textDisplay.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    textDisplay.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(),
                        new org.joml.AxisAngle4f(),
                        new org.joml.Vector3f(0, 0, 0), // 初期状態は非表示
                        new org.joml.AxisAngle4f()
                    ));
                    
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.getUniqueId().equals(uuid)) {
                            online.hideEntity(manager.getPlugin(), textDisplay);
                        }
                    }
                    handInfoDisplays.put(uuid, textDisplay);
                    hoverStates.put(uuid, false);
                    
                    if (bp != null) {
                        bp.sendMessage(Component.text("あなたの手札: ").color(NamedTextColor.GREEN)
                                .append(Deck.getCardComponent(p.getHand().get(0))).append(Component.text(" "))
                                .append(Deck.getCardComponent(p.getHand().get(1))));
                    }
                }
                
                startHoverTask();

                // プリフロップの最初の手番 (ヘッズアップ時はBTN(SB)、3人以上の場合はUTGから開始)
                currentPlayerIndex = getFirstPlayerIndexPreFlop();
                promptAction();
            }, totalWaitTicks);
        }, 30L); // 1.5秒ディレイ
    }

    private void startHoverTask() {
        if (hoverTask != null) {
            hoverTask.cancel();
        }
        hoverTask = manager.getPlugin().getServer().getScheduler().runTaskTimer(manager.getPlugin(), () -> {
            for (UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                
                org.bukkit.entity.Interaction interaction = handInteractions.get(uuid);
                org.bukkit.entity.TextDisplay display = handInfoDisplays.get(uuid);
                if (interaction == null || display == null) continue;
                
                if (!interaction.isValid() || !display.isValid()) {
                    // /kill 等で強制消去された場合、自爆してテーブルを閉じる
                    manager.getPlugin().getLogger().warning("Poker entities missing! Forcing cleanup for table " + table.getTableId());
                    cleanup();
                    return;
                }
                
                org.bukkit.entity.Entity target = player.getTargetEntity(5, false);
                boolean isLooking = (target != null && target.equals(interaction));
                boolean wasLooking = hoverStates.getOrDefault(uuid, false);
                
                if (isLooking && !wasLooking) {
                    PokerPlayer p = playersData.get(uuid);
                    if (p != null && !handsRevealed && p.getStatus() != PokerPlayer.Status.FOLDED) {
                        hoverStates.put(uuid, true); // 表示される条件を満たした時だけ状態をtrueにする
                        display.text(Component.text("").color(NamedTextColor.YELLOW)
                            .append(Deck.getCardComponent(p.getHand().get(0))).append(Component.text(" "))
                            .append(Deck.getCardComponent(p.getHand().get(1))));
                        
                        display.setTeleportDuration(HAND_POPUP_SPEED);
                        org.bukkit.Location baseHandLoc = table.getBaseHandCardLocation(getSeatIndex(uuid));
                        display.teleport(baseHandLoc.clone().add(0, HAND_POPUP_HEIGHT, 0));
                        display.setInterpolationDelay(0);
                        display.setInterpolationDuration(HAND_POPUP_SPEED);
                        display.setTransformation(new org.bukkit.util.Transformation(
                            new org.joml.Vector3f(),
                            new org.joml.AxisAngle4f(),
                            new org.joml.Vector3f(HAND_POPUP_SCALE, HAND_POPUP_SCALE, HAND_POPUP_SCALE),
                            new org.joml.AxisAngle4f()
                        ));
                    }
                } else if (!isLooking && wasLooking) {
                    hoverStates.put(uuid, false);
                    display.setTeleportDuration(HAND_POPUP_SPEED);
                    org.bukkit.Location baseHandLoc = table.getBaseHandCardLocation(getSeatIndex(uuid));
                    display.teleport(baseHandLoc.clone().add(0, 0.2, 0));
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(HAND_POPUP_SPEED);
                    display.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(),
                        new org.joml.AxisAngle4f(),
                        new org.joml.Vector3f(0, 0, 0),
                        new org.joml.AxisAngle4f()
                    ));
                }
            }
        }, 0L, 2L);
    }

    private void payBet(UUID uuid, int amount, String actionName) {
        PokerPlayer p = playersData.get(uuid);
        int actualAmount = Math.min(amount, p.getChips());
        p.removeChips(actualAmount);
        p.addBet(actualAmount);
        pot += actualAmount;
        updateScoreboard(uuid, -actualAmount);

        boolean isAllin = false;
        if (p.getChips() == 0) {
            p.setStatus(PokerPlayer.Status.ALLIN);
            isAllin = true;
            broadcastActionLog(uuid, "オールイン", p.getCurrentBet());
        } else {
            broadcastActionLog(uuid, actionName, p.getCurrentBet());
        }
        
        if (p.getCurrentBet() > currentHighestBet) {
            currentHighestBet = p.getCurrentBet();
        } else if (isAllin && p.getCurrentBet() < currentHighestBet) {
            // アーリーALLIN（少ない額でのALLIN）の処理：他プレイヤーの超過ベットを返還し、最大ベット額を下げる
            int capBet = p.getCurrentBet();
            for (UUID u : activePlayers) {
                PokerPlayer other = playersData.get(u);
                if (other.getCurrentBet() > capBet) {
                    int excess = other.getCurrentBet() - capBet;
                    other.setCurrentBet(capBet);
                    other.addChips(excess);
                    pot -= excess;
                    updateScoreboard(u, excess);
                    Player op = Bukkit.getPlayer(u);
                    if (op != null) {
                        op.sendMessage(Component.text("他のプレイヤーが少ない額でオールインしたため、超過分の " + excess + " チップが返還されました。").color(NamedTextColor.YELLOW));
                    }
                }
            }
            currentHighestBet = capBet;
            broadcast(">> 最大ベット額が " + capBet + " に制限されました。");
        }
    }

    private void promptAction() {
        if (checkRoundEnd()) return;

        PokerPlayer current = playersData.get(activePlayers.get(currentPlayerIndex));
        if (current.getStatus() == PokerPlayer.Status.FOLDED || current.getStatus() == PokerPlayer.Status.ALLIN) {
            currentPlayerIndex = (currentPlayerIndex + 1) % activePlayers.size();
            promptAction();
            return;
        }

        Player p = Bukkit.getPlayer(current.getUuid());
        if (p == null || !p.isOnline()) {
            // オフラインのプレイヤーは強制FOLD
            handleAction(current.getUuid(), "FOLD", 0);
            return;
        }

        int callAmount = currentHighestBet - current.getCurrentBet();
        
        broadcast("\n--------------------------------");
        broadcast(">>ターン：" + p.getName() + " (Call額: " + callAmount + ")");

        Component actions = Component.text("");
        actions = actions.append(Component.text("[あなたの手札: ").color(NamedTextColor.GREEN))
                .append(Deck.getCardComponent(current.getHand().get(0))).append(Component.text(" "))
                .append(Deck.getCardComponent(current.getHand().get(1)))
                .append(Component.text(" | 所持チップ: " + current.getChips() + "]\n").color(NamedTextColor.GREEN));
        actions = actions.append(Component.text("\nアクションを選択: "));
        
        actions = actions.append(Component.text(" [フォールド] ")
                .color(NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " FOLD")));

        boolean hasAllin = false;
        for (UUID u : activePlayers) {
            if (playersData.get(u).getStatus() == PokerPlayer.Status.ALLIN) {
                hasAllin = true;
                break;
            }
        }

        if (hasAllin) {
            if (callAmount > 0) {
                actions = actions.append(Component.text(" [コール] ")
                        .color(NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " CALL")));
            } else {
                actions = actions.append(Component.text(" [チェック] ")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " CHECK")));
            }
        } else {
            if (callAmount == 0) {
                actions = actions.append(Component.text(" [チェック] ")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " CHECK")));
            } else {
                actions = actions.append(Component.text(" [コール] ")
                        .color(NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " CALL")));
            }

            if (currentHighestBet == 0) {
                actions = actions.append(Component.text(" [ベット] ")
                        .color(NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " RAISE")));
            } else {
                actions = actions.append(Component.text(" [レイズ] ")
                        .color(NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " RAISE")));
            }
                    
            actions = actions.append(Component.text(" [オールイン] ")
                    .color(NamedTextColor.DARK_RED)
                    .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " ALLIN")));
        }

        p.sendMessage(actions);
    }

    public void handleAction(UUID playerId, String action, int amount) {
        if (currentPlayerIndex == -1 || !activePlayers.get(currentPlayerIndex).equals(playerId)) return;
        PokerPlayer p = playersData.get(playerId);
        p.setActedInPhase(true);

        if (action.equalsIgnoreCase("FOLD")) {
            p.setStatus(PokerPlayer.Status.FOLDED);
            broadcastActionLog(playerId, "フォールド", null);
            // FOLD: 重い打鍵音
            playSoundAll(Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 1.0f, 0.8f);
            
            // FOLD時にカードを削除
            List<CardDisplay> displays = handCardDisplays.remove(playerId);
            if (displays != null) {
                for (CardDisplay cd : displays) {
                    cd.remove();
                }
            }
        } else if (action.equalsIgnoreCase("CHECK")) {
            broadcastActionLog(playerId, "チェック", null);
            // CHECK: 軽いクリック音
            playSoundAll(Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } else if (action.equalsIgnoreCase("CALL")) {
            int callAmount = currentHighestBet - p.getCurrentBet();
            payBet(playerId, callAmount, "コール");
            // CALL: コイン音（XP取得音）
            playSoundAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        } else if (action.equalsIgnoreCase("RAISE")) {
            int totalBet = amount;
            if (totalBet <= currentHighestBet) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(Component.text("現在のベット額(" + currentHighestBet + ")以上を入力してください。").color(NamedTextColor.RED));
                }
                p.setActedInPhase(false);
                promptAction();
                return;
            }
            int addAmount = totalBet - p.getCurrentBet();
            String actionName = (currentHighestBet == 0) ? "ベット" : "レイズ";
            payBet(playerId, addAmount, actionName);
            // RAISE: 高めのコイン音
            playSoundAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.6f);
        } else if (action.equalsIgnoreCase("ALLIN")) {
            payBet(playerId, p.getChips(), "オールイン");
            // ALLIN: 雷鳴のような重い音
            playSoundAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
        }

        int nextIndex = (currentPlayerIndex + 1) % activePlayers.size();
        currentPlayerIndex = -1; // ディレイ中の連続クリック防止

        manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
            currentPlayerIndex = nextIndex;
            promptAction();
        }, 20L); // 1秒ディレイ
    }

    private boolean checkRoundEnd() {
        int activeCount = 0;
        int actionNeeded = 0;
        boolean allMatched = true;
        boolean allActed = true;

        for (UUID uuid : activePlayers) {
            PokerPlayer p = playersData.get(uuid);
            if (p.getStatus() != PokerPlayer.Status.FOLDED) {
                activeCount++;
                if (p.getStatus() != PokerPlayer.Status.ALLIN) {
                    actionNeeded++;
                    if (p.getCurrentBet() < currentHighestBet) {
                        allMatched = false;
                    }
                    if (!p.isActedInPhase()) {
                        allActed = false;
                    }
                }
            }
        }

        if (activeCount <= 1) {
            // 一人を除いて全員FOLD
            currentPlayerIndex = -1;
            manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                endGame(false);
            }, 20L);
            return true;
        }

        // 全員のベット額が揃い、かつアクションが必要な全員が行動済み、またはアクション可能なプレイヤーが1人以下になった場合
        if (allMatched && (allActed || actionNeeded <= 1)) {
           // ラウンド終了
           broadcast("\n>>> NEXT PHASE <<<");
           currentPlayerIndex = -1;
           manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
               nextPhase();
           }, 30L); // 1.5秒ディレイ
           return true;
        }

        return false;
    }

    /**
     * プリフロップにおける最初の手番のプレイヤーインデックスを取得します。
     * - ヘッズアップ(2人)の場合: SB(BTN)から開始
     * - 3人以上の場合: UTG(BBの左隣)から開始
     */
    private int getFirstPlayerIndexPreFlop() {
        if (activePlayers.size() == 2) {
            return btnIndex;
        } else {
            return (btnIndex + 3) % activePlayers.size();
        }
    }

    /**
     * フロップ以降のフェーズにおける最初の手番のプレイヤーインデックスを取得します。
     * - ヘッズアップ(2人)の場合: BBから開始 (BTNの次のプレイヤー)
     * - 3人以上の場合: SBから開始 (BTNの次のプレイヤー)
     * どちらの場合も、インデックスの計算式は共通になります。
     */
    private int getFirstPlayerIndexPostFlop() {
        return (btnIndex + 1) % activePlayers.size();
    }

    private void nextPhase() {
        // 全員の現在のBETをリセットし、アクション済みフラグをリセット
        for (UUID uuid : activePlayers) {
            PokerPlayer p = playersData.get(uuid);
            p.setCurrentBet(0);
            p.setActedInPhase(false);
        }
        currentHighestBet = 0;

        int actionNeeded = 0;
        for (UUID uuid : activePlayers) {
            if (playersData.get(uuid).getStatus() == PokerPlayer.Status.PLAYING) actionNeeded++;
        }
        final int finalActionNeeded = actionNeeded;

        if (finalActionNeeded <= 1 && !handsRevealed && state != GameState.RIVER && state != GameState.SHOWDOWN) {
            handsRevealed = true;
            broadcast("\n=== ALLIN成立: プレイヤーの手札公開 ===");
            for (UUID uuid : activePlayers) {
                PokerPlayer p = playersData.get(uuid);
                if (p.getStatus() != PokerPlayer.Status.FOLDED) {
                    List<CardDisplay> displays = handCardDisplays.get(uuid);
                    if (displays != null) {
                        for (CardDisplay cd : displays) {
                            cd.flip(manager.getPlugin(), true);
                        }
                    }
                    broadcastComponent(Component.text(Bukkit.getPlayer(uuid).getName() + ": ")
                            .append(Deck.getCardComponent(p.getHand().get(0))).append(Component.text(" "))
                            .append(Deck.getCardComponent(p.getHand().get(1))));
                }
            }
            playSoundAll(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);

            manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                executeNextPhase(finalActionNeeded);
            }, 40L); // 2秒ディレイ
            return;
        }

        executeNextPhase(finalActionNeeded);
    }

    private void spawnAndAnimateCommunityCards(int count) {
        // 今回配るカードIDを先にすべてドローし、communityCards に追加する
        int startIndex = communityCards.size();
        int[] drawnCards = new int[count];
        for (int i = 0; i < count; i++) {
            drawnCards[i] = deck.drawCard();
            communityCards.add(drawnCards[i]);
        }

        for (int i = 0; i < count; i++) {
            final int cardIndex = startIndex + i;
            final int cardId = drawnCards[i];

            // カードごとに遅延をつけてスポーン
            manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {

                org.bukkit.Location targetLoc = table.getCommunityCardLocation(cardIndex);

                // ディーラー位置にエンティティを生成（遅延後なので他カードと同時に出現しない）
                CardDisplay display = new CardDisplay(table.getDealerLocation(), cardId);
                communityCardDisplays.add(display);

                // teleportDuration を設定してから teleport を呼ぶことで補間移動が有効になる
                // ※スポーン直後(同じtick)に移動させると補間されないため、少し遅延を入れる
                manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                    display.moveTo(manager.getPlugin(), targetLoc, 20); // 20 ticks = 1秒かけて滑らかに移動

                    // 移動完了後（20tick後）にカードを表向きにフリップ
                    manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                        display.flip(manager.getPlugin(), true);
                        playSoundAll(Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    }, 20L);
                }, 3L); // スポーンから3tick待ってから移動開始

            }, (long) i * 12L); // 12tick（0.6秒）間隔で1枚ずつ配る
        }
    }

    private void executeNextPhase(int actionNeeded) {
        int dealCount = 0;
        if (state == GameState.PRE_FLOP) {
            state = GameState.FLOP;
            dealCount = 3;
        } else if (state == GameState.FLOP) {
            state = GameState.TURN;
            dealCount = 1;
        } else if (state == GameState.TURN) {
            state = GameState.RIVER;
            dealCount = 1;
        } else if (state == GameState.RIVER) {
            state = GameState.SHOWDOWN;
            endGame(true);
            return;
        }

        if (dealCount > 0) {
            spawnAndAnimateCommunityCards(dealCount);
            
            // アニメーション完了まで待ってから次へ
            int animWaitTicks = (dealCount - 1) * 10 + 20 + 20 + 20; 
            
            manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                broadcastCommunityCards();
                currentPlayerIndex = -1;
                
                if (actionNeeded <= 1) {
                    manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                        nextPhase();
                    }, 30L);
                } else {
                    manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                        currentPlayerIndex = getFirstPlayerIndexPostFlop();
                        promptAction();
                    }, 10L);
                }
            }, animWaitTicks);
        }
    }

    private void broadcastCommunityCards() {
        Component msg = Component.text("\n=== 共通カード (" + state.name() + ") ===\n");
        for (int id : communityCards) {
            msg = msg.append(Deck.getCardComponent(id)).append(Component.text(" "));
        }
        // カード表示の末尾にポット総額を追加
        msg = msg.append(Component.text(" | POT: " + pot).color(NamedTextColor.WHITE));
        broadcastComponent(msg);
        // 共通カードをめくる音
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
    }

    private void endGame(boolean isShowdown) {
        broadcast("\n=== ゲームセット ===");
        currentPlayerIndex = -1;

        // 勝者の判定
        List<UUID> remaining = new ArrayList<>();
        for (UUID uuid : activePlayers) {
            if (playersData.get(uuid).getStatus() != PokerPlayer.Status.FOLDED) {
                remaining.add(uuid);
            }
        }

        if (!isShowdown || remaining.size() == 1) {
            UUID winner = remaining.get(0);
            Component msg = Component.text("\n\n>>>  ")
                .append(Component.text(Bukkit.getPlayer(winner).getName()).color(NamedTextColor.GREEN))
                .append(Component.text("  WIN  >>>").color(NamedTextColor.GOLD));
            broadcastComponent(msg);
            awardPot(winner, pot);
            promptNextGame();
        } else {
            // ショーダウン
            showdownNextPlayer(remaining, 0, null, null);
        }
    }

    private void showdownNextPlayer(List<UUID> remaining, int index, UUID bestPlayer, HandEvaluator.HandResult bestHand) {
        if (index >= remaining.size()) {
        manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
                Component msg = Component.text("\n\n>>>  ")
                    .append(Component.text(Bukkit.getPlayer(bestPlayer).getName()).color(NamedTextColor.GREEN))
                    .append(Component.text("  WIN").color(NamedTextColor.GOLD))
                    .append(Component.text(" (" + bestHand.rank.name() + ")  >>>").color(NamedTextColor.WHITE));
                broadcastComponent(msg);
                awardPot(bestPlayer, pot);
                // 勝利のファンファーレ
                playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                promptNextGame();
            }, 20L);
            return;
        }

        UUID uuid = remaining.get(index);
        PokerPlayer p = playersData.get(uuid);
        
        List<CardDisplay> displays = handCardDisplays.get(uuid);
        if (displays != null) {
            for (CardDisplay cd : displays) {
                cd.flip(manager.getPlugin(), true);
            }
        }
        
        List<Integer> evalCards = new ArrayList<>(p.getHand());
        evalCards.addAll(communityCards);
        HandEvaluator.HandResult result = HandEvaluator.evaluate(evalCards);

        Component msg = Component.text(Bukkit.getPlayer(uuid).getName() + " : " + result.rank.name() + " (")
                .append(Deck.getCardComponent(p.getHand().get(0))).append(Component.text(" "))
                .append(Deck.getCardComponent(p.getHand().get(1))).append(Component.text(")"));
        broadcastComponent(msg);
        // 役公開音（ドラムロール的なベース音）
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);

        UUID nextBestPlayer = bestPlayer;
        HandEvaluator.HandResult nextBestHand = bestHand;
        if (bestHand == null || result.compareTo(bestHand) > 0) {
            nextBestHand = result;
            nextBestPlayer = uuid;
        }

        UUID finalNextBestPlayer = nextBestPlayer;
        HandEvaluator.HandResult finalNextBestHand = nextBestHand;

        manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
            showdownNextPlayer(remaining, index + 1, finalNextBestPlayer, finalNextBestHand);
        }, 30L);
    }

    private void promptNextGame() {
        state = GameState.WAITING;
        
        // ゲーム終了時にコントローラーや座席の文字を復活させる
        table.showLobbyEntities(bbAmount);

        
        manager.getPlugin().getServer().getScheduler().runTaskLater(manager.getPlugin(), () -> {
        Component prompt = Component.text("\n=== 次のゲーム ===\n").color(NamedTextColor.GOLD)
            .append(Component.text("ゲームを続ける場合はそのままお待ちください。\n"))
            .append(Component.text(" [席を立つ] ")
            .color(NamedTextColor.RED)
            .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " LEAVE")))
            .append(Component.text(" [コントローラーメニューを開く] ")
            .color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand("/pokeraction " + table.getTableId() + " MENU")));

        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(prompt);
        }   
        }, 20L);
    }

    private void awardPot(UUID winner, int amount) {
        broadcast(">>獲得：" + amount);
        updateScoreboard(winner, amount);
    }

    private void updateScoreboard(UUID playerId, int delta) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective chipsObj = sb.getObjective("chips");
        if (chipsObj != null) {
            int current = chipsObj.getScore(p.getName()).getScore();
            chipsObj.getScore(p.getName()).setScore(current + delta);
        }
    }

    public void broadcast(String message) {
        for (PokerSeat seat : table.getSeats()) {
            if (!seat.isEmpty()) {
                Player p = Bukkit.getPlayer(seat.getPlayerId());
                if (p != null) p.sendMessage(Component.text(message));
            }
        }
    }

    public void broadcastComponent(Component message) {
        for (PokerSeat seat : table.getSeats()) {
            if (!seat.isEmpty()) {
                Player p = Bukkit.getPlayer(seat.getPlayerId());
                if (p != null) p.sendMessage(message);
            }
        }
    }

    /**
     * アクションログをフォーマットして全員に送信します。
     * 形式: *<プレイヤー名>　<アクション名>：<ベット額>
     */
    private void broadcastActionLog(UUID playerId, String actionName, Integer betAmount) {
        NamedTextColor actionColor;
        switch (actionName) {
            case "フォールド": actionColor = NamedTextColor.RED; break;
            case "コール": actionColor = NamedTextColor.YELLOW; break;
            case "チェック": actionColor = NamedTextColor.GREEN; break;
            case "ベット":
            case "レイズ": actionColor = NamedTextColor.AQUA; break;
            case "オールイン": actionColor = NamedTextColor.DARK_RED; break;
            default: actionColor = NamedTextColor.WHITE; break;
        }

        Player player = Bukkit.getPlayer(playerId);
        String playerName = player != null ? player.getName() : "Unknown";
        
        Component msg = Component.text("*").color(NamedTextColor.WHITE)
            .append(Component.text(playerName).color(NamedTextColor.GREEN))
            .append(Component.text("　").color(NamedTextColor.WHITE))
            .append(Component.text(actionName).color(actionColor));
        
        if (betAmount != null) {
            msg = msg.append(Component.text("：").color(NamedTextColor.WHITE))
                     .append(Component.text(String.valueOf(betAmount)).color(NamedTextColor.WHITE));
        }
        
        broadcastComponent(msg);
    }

    // テーブルに着席している全プレイヤーに効果音を再生する
    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (PokerSeat seat : table.getSeats()) {
            if (!seat.isEmpty()) {
                Player p = Bukkit.getPlayer(seat.getPlayerId());
                if (p != null) {
                    p.playSound(p.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
                }
            }
        }
    }

    public void cleanup() {
        if (hoverTask != null) {
            hoverTask.cancel();
            hoverTask = null;
        }
        for (org.bukkit.entity.Interaction interaction : handInteractions.values()) {
            if (interaction != null && interaction.isValid()) interaction.remove();
        }
        handInteractions.clear();
        for (org.bukkit.entity.TextDisplay display : handInfoDisplays.values()) {
            if (display != null && display.isValid()) display.remove();
        }
        handInfoDisplays.clear();
        hoverStates.clear();

        if (communityCardDisplays != null) {
            for (CardDisplay cd : communityCardDisplays) {
                cd.remove();
            }
            communityCardDisplays.clear();
        }

        if (handCardDisplays != null) {
            for (List<CardDisplay> list : handCardDisplays.values()) {
                for (CardDisplay cd : list) {
                    cd.remove();
                }
            }
            handCardDisplays.clear();
        }
    }

    private int getSeatIndex(UUID uuid) {
        for (PokerSeat seat : table.getSeats()) {
            if (seat.getPlayerId() != null && seat.getPlayerId().equals(uuid)) {
                return seat.getId();
            }
        }
        return -1;
    }
}
