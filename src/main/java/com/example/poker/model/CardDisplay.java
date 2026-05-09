package com.example.poker.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class CardDisplay {
    private final BlockDisplay background;
    private final TextDisplay centerText;
    private final TextDisplay topLeftText;
    private final TextDisplay bottomRightText;

    private final Matrix4f baseMatrix = new Matrix4f();
    private final Location originLocation;

    private boolean isFaceUp = false;

    // 定数
    private static final float CARD_WIDTH = 0.43f;
    private static final float CARD_HEIGHT = 0.6f;
    private static final float CARD_THICKNESS = 0.03f;

    public CardDisplay(Location loc, int cardId) {
        this.originLocation = loc.clone();

        int number = Deck.getNumber(cardId);
        int suit = Deck.getSuit(cardId);

        String suitStr = getSuitString(suit);
        NamedTextColor color = getSuitColor(suit);

        String numStr = String.valueOf(number);
        if (number == 1) numStr = "A";
        else if (number == 11) numStr = "J";
        else if (number == 12) numStr = "Q";
        else if (number == 13) numStr = "K";

        // 背景生成 (白コンクリート)
        background = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        background.setBlock(Bukkit.createBlockData(Material.WHITE_CONCRETE));
        
        // テキスト生成
        centerText = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        centerText.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // 背景透明
        
        topLeftText = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        topLeftText.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
        
        bottomRightText = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        bottomRightText.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));

        // エース(A)とそれ以外でデザイン切り替え
        if (number == 1) {
            centerText.text(Component.text(suitStr).color(color));
            Component edgeComp = Component.text("A").color(color);
            topLeftText.text(edgeComp);
            bottomRightText.text(edgeComp);
        } else {
            centerText.text(Component.text(numStr).color(color));
            Component edgeComp = Component.text(suitStr).color(color);
            topLeftText.text(edgeComp);
            bottomRightText.text(edgeComp);
        }

        // 影やデフォルト背景を消す
        centerText.setDefaultBackground(false);
        topLeftText.setDefaultBackground(false);
        bottomRightText.setDefaultBackground(false);
        
        // ビルボードを固定に（プレイヤーの方を向かせない）
        centerText.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        topLeftText.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        bottomRightText.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);

        // 初期状態は裏面とする
        isFaceUp = false;
        // 行列をリセットして数値をセット
        baseMatrix.identity()
            // 1. 位置の微調整 (X, Y, Z) 
            // 例: ブロックの中心から少し上に浮かせたい場合など
            .translate(0.0f, -0.98f, 0.0f) 
            
            // 2. 向きの指定 (X軸, Y軸, Z軸の順に回転させるのが一般的)
            // カードをパタンと伏せる（X軸に90度）
            .rotateX((float) Math.toRadians(90))
            // 机の上で少し斜めに向ける（Y軸に30度）
            .rotateY((float) Math.toRadians(0))
            // カード自体を少し傾ける（Z軸）
            .rotateZ((float) Math.toRadians(90));
        updateTransformations(0);
    }

    private String getSuitString(int suit) {
        switch (suit) {
            case 0: return "♠";
            case 1: return "♥";
            case 2: return "♦";
            case 3: return "♣";
            default: return "";
        }
    }

    private NamedTextColor getSuitColor(int suit) {
        if (suit == 1 || suit == 2) return NamedTextColor.DARK_RED;
        return NamedTextColor.BLACK; // BLACK or DARK_GRAY
    }

    public void updateTransformations(int interpolationDuration) {
        if (interpolationDuration > 0) {
            background.setInterpolationDelay(0);
            background.setInterpolationDuration(interpolationDuration);
            centerText.setInterpolationDelay(0);
            centerText.setInterpolationDuration(interpolationDuration);
            topLeftText.setInterpolationDelay(0);
            topLeftText.setInterpolationDuration(interpolationDuration);
            bottomRightText.setInterpolationDelay(0);
            bottomRightText.setInterpolationDuration(interpolationDuration);
        }

        // 各パーツのローカル行列を計算し、baseMatrix を掛けて setTransformation

        // 1. Background
        Matrix4f bgLocal = new Matrix4f()
            .translate(-CARD_WIDTH / 2, -CARD_HEIGHT / 2, -CARD_THICKNESS / 2)
            .scale(CARD_WIDTH, CARD_HEIGHT, CARD_THICKNESS);
        background.setTransformation(matrixToTransformation(new Matrix4f(baseMatrix).mul(bgLocal)));

        // 2. Center Text
        Matrix4f centerLocal = new Matrix4f()
            .translate(0, -0.18f, CARD_THICKNESS / 2 + 0.001f)
            .scale(1.3f, 1.3f, 1.3f);
        centerText.setTransformation(matrixToTransformation(new Matrix4f(baseMatrix).mul(centerLocal)));

        // 3. Top Left Text
        Matrix4f tlLocal = new Matrix4f()
            .translate(-CARD_WIDTH / 2 + 0.06f, CARD_HEIGHT / 2 - 0.2f, CARD_THICKNESS / 2 + 0.001f)
            .scale(0.8f, 0.8f, 0.8f);
        topLeftText.setTransformation(matrixToTransformation(new Matrix4f(baseMatrix).mul(tlLocal)));

        // 4. Bottom Right Text (180度回転して右下に配置)
        Matrix4f brLocal = new Matrix4f()
            .translate(CARD_WIDTH / 2 - 0.06f, -CARD_HEIGHT / 2 + 0.2f, CARD_THICKNESS / 2 + 0.001f)
            .rotateZ((float) Math.PI)
            .scale(0.8f, 0.8f, 0.8f);
        bottomRightText.setTransformation(matrixToTransformation(new Matrix4f(baseMatrix).mul(brLocal)));
    }

    private Transformation matrixToTransformation(Matrix4f matrix) {
        Vector3f translation = new Vector3f();
        Quaternionf leftRot = new Quaternionf();
        Vector3f scale = new Vector3f();
        Quaternionf rightRot = new Quaternionf();
        
        matrix.getTranslation(translation);
        matrix.getUnnormalizedRotation(leftRot);
        matrix.getScale(scale);
        
        return new Transformation(translation, leftRot, scale, rightRot);
    }

    /**
     * 所定の位置へスライド移動する
     */
    public void moveTo(Plugin plugin, Location target, int durationTicks) {
        // 1. まず「移動にかかる時間」だけを先に設定する
        background.setTeleportDuration(durationTicks);
        centerText.setTeleportDuration(durationTicks);
        topLeftText.setTeleportDuration(durationTicks);
        bottomRightText.setTeleportDuration(durationTicks);

        // 2. 1ティック（0.05秒）だけ待ってから、テレポートを実行する
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            background.teleport(target);
            centerText.teleport(target);
            topLeftText.teleport(target);
            bottomRightText.teleport(target);
        }, 1L); // 1L = 1 tick
}

    /**
     * ホップして裏返す（または表にする）アニメーション
     */
    public void flip(Plugin plugin, boolean faceUp) {
        if (this.isFaceUp == faceUp) return;
        this.isFaceUp = faceUp;

        // 1. 少し上（ホップ）＋ 90度回転 (10 tick = 0.5s)
        baseMatrix.translate(0, 0, -0.5f); // Y軸方向にホップ
        baseMatrix.rotateY((float) Math.PI / 2); // 90度回転
        updateTransformations(10);

        // 2. 元の高さに戻る ＋ 残り90度回転 (10 tick = 0.5s)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            baseMatrix.translate(-0.5f, 0, 0);
            baseMatrix.rotateY((float) Math.PI / 2);
            updateTransformations(10);
        }, 10L);
    }

    public void remove() {
        if (background != null) background.remove();
        if (centerText != null) centerText.remove();
        if (topLeftText != null) topLeftText.remove();
        if (bottomRightText != null) bottomRightText.remove();
    }
}
