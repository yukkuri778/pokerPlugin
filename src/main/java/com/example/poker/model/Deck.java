package com.example.poker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Deck {
    private final List<Integer> cards;

    public Deck() {
        cards = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            cards.add(i);
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public int drawCard() {
        if (cards.isEmpty()) return -1;
        return cards.remove(cards.size() - 1); // Draw from top/end
    }

    public static int getNumber(int id) {
        return (id / 4) + 1; // 1 (Ace) to 13 (King)
    }

    public static int getSuit(int id) {
        return id % 4; // 0 to 3
    }

    public static Component getCardComponent(int id) {
        if (id == -1) return Component.text("[??]").color(NamedTextColor.GRAY);
        int number = getNumber(id);
        int suit = getSuit(id);
        String suitStr = "";
        NamedTextColor color = NamedTextColor.WHITE;
        switch (suit) {
            case 0: suitStr = "♠"; color = NamedTextColor.GRAY; break;
            case 1: suitStr = "♥"; color = NamedTextColor.RED; break;
            case 2: suitStr = "♦"; color = NamedTextColor.RED; break;
            case 3: suitStr = "♣"; color = NamedTextColor.GRAY; break;
        }
        String numStr = String.valueOf(number);
        if (number == 1) numStr = "A";
        else if (number == 11) numStr = "J";
        else if (number == 12) numStr = "Q";
        else if (number == 13) numStr = "K";

        return Component.text("[" + suitStr + numStr + "]").color(color);
    }
}
