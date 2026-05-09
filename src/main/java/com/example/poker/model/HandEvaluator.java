package com.example.poker.model;

import java.util.*;

public class HandEvaluator {

    public enum HandRank {
        HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH
    }

    public static class HandResult implements Comparable<HandResult> {
        public HandRank rank;
        public List<Integer> kickers; // 判定用の数値リスト(降順)

        public HandResult(HandRank rank, List<Integer> kickers) {
            this.rank = rank;
            this.kickers = kickers;
        }

        @Override
        public int compareTo(HandResult other) {
            if (this.rank.ordinal() != other.rank.ordinal()) {
                return Integer.compare(this.rank.ordinal(), other.rank.ordinal());
            }
            for (int i = 0; i < Math.min(this.kickers.size(), other.kickers.size()); i++) {
                int cmp = Integer.compare(this.kickers.get(i), other.kickers.get(i));
                if (cmp != 0) return cmp;
            }
            return 0;
        }
    }

    public static HandResult evaluate(List<Integer> hand) {
        List<CardData> cards = new ArrayList<>();
        for (int id : hand) {
            cards.add(new CardData(id));
        }

        // 数値の出現回数と、スーツの出現回数をカウント
        Map<Integer, Integer> valueCounts = new HashMap<>();
        Map<Integer, List<CardData>> suitMap = new HashMap<>();
        
        for (CardData c : cards) {
            valueCounts.put(c.value, valueCounts.getOrDefault(c.value, 0) + 1);
            suitMap.computeIfAbsent(c.suit, k -> new ArrayList<>()).add(c);
        }

        // フラッシュ判定
        List<CardData> flushCards = null;
        for (List<CardData> sc : suitMap.values()) {
            if (sc.size() >= 5) {
                flushCards = new ArrayList<>(sc);
                flushCards.sort((a, b) -> Integer.compare(b.value, a.value));
                break;
            }
        }

        // ストレートフラッシュ判定
        if (flushCards != null) {
            List<Integer> sfKickers = checkStraight(flushCards);
            if (sfKickers != null) {
                return new HandResult(HandRank.STRAIGHT_FLUSH, sfKickers);
            }
        }

        // フォーカード判定
        for (Map.Entry<Integer, Integer> e : valueCounts.entrySet()) {
            if (e.getValue() == 4) {
                int quadValue = e.getKey();
                int kicker = getHighestExcluding(cards, Collections.singletonList(quadValue));
                return new HandResult(HandRank.FOUR_OF_A_KIND, Arrays.asList(quadValue, kicker));
            }
        }

        // フルハウス判定
        int threeValue = -1;
        int pairValue = -1;
        for (Map.Entry<Integer, Integer> e : valueCounts.entrySet()) {
            if (e.getValue() == 3) {
                if (threeValue == -1 || e.getKey() > threeValue) {
                    if (threeValue != -1) pairValue = Math.max(pairValue, threeValue); // 過去のスリーカードはペア候補に
                    threeValue = e.getKey();
                } else {
                    pairValue = Math.max(pairValue, e.getKey());
                }
            } else if (e.getValue() == 2) {
                pairValue = Math.max(pairValue, e.getKey());
            }
        }
        if (threeValue != -1 && pairValue != -1) {
            return new HandResult(HandRank.FULL_HOUSE, Arrays.asList(threeValue, pairValue));
        }

        // フラッシュ（役）判定
        if (flushCards != null) {
            List<Integer> kickers = new ArrayList<>();
            for (int i = 0; i < 5; i++) kickers.add(flushCards.get(i).value);
            return new HandResult(HandRank.FLUSH, kickers);
        }

        // ストレート判定
        List<Integer> straightKickers = checkStraight(cards);
        if (straightKickers != null) {
            return new HandResult(HandRank.STRAIGHT, straightKickers);
        }

        // スリーカード判定
        if (threeValue != -1) {
            List<Integer> kickers = getTopKickers(cards, Collections.singletonList(threeValue), 2);
            List<Integer> resultKickers = new ArrayList<>();
            resultKickers.add(threeValue);
            resultKickers.addAll(kickers);
            return new HandResult(HandRank.THREE_OF_A_KIND, resultKickers);
        }

        // ツーペア・ワンペア判定
        List<Integer> pairs = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : valueCounts.entrySet()) {
            if (e.getValue() == 2) pairs.add(e.getKey());
        }
        pairs.sort(Collections.reverseOrder());

        if (pairs.size() >= 2) {
            int p1 = pairs.get(0);
            int p2 = pairs.get(1);
            List<Integer> kickers = getTopKickers(cards, Arrays.asList(p1, p2), 1);
            return new HandResult(HandRank.TWO_PAIR, Arrays.asList(p1, p2, kickers.get(0)));
        } else if (pairs.size() == 1) {
            int p1 = pairs.get(0);
            List<Integer> kickers = getTopKickers(cards, Collections.singletonList(p1), 3);
            List<Integer> resultKickers = new ArrayList<>();
            resultKickers.add(p1);
            resultKickers.addAll(kickers);
            return new HandResult(HandRank.ONE_PAIR, resultKickers);
        }

        // ハイカード判定
        List<Integer> kickers = getTopKickers(cards, Collections.emptyList(), 5);
        return new HandResult(HandRank.HIGH_CARD, kickers);
    }

    private static List<Integer> checkStraight(List<CardData> cards) {
        List<Integer> uniqueValues = new ArrayList<>();
        for (CardData c : cards) {
            if (!uniqueValues.contains(c.value)) uniqueValues.add(c.value);
        }
        uniqueValues.sort(Collections.reverseOrder());

        // Aを1としても判定できるようにする（14なら1も追加）
        if (uniqueValues.contains(14)) {
            uniqueValues.add(1);
        }

        int consec = 1;
        for (int i = 0; i < uniqueValues.size() - 1; i++) {
            if (uniqueValues.get(i) - 1 == uniqueValues.get(i+1)) {
                consec++;
                if (consec == 5) {
                    return Collections.singletonList(uniqueValues.get(i - 3)); // 一番高い数値
                }
            } else {
                consec = 1;
            }
        }
        return null;
    }

    private static int getHighestExcluding(List<CardData> cards, List<Integer> excludeValues) {
        cards.sort((a, b) -> Integer.compare(b.value, a.value));
        for (CardData c : cards) {
            if (!excludeValues.contains(c.value)) return c.value;
        }
        return -1;
    }

    private static List<Integer> getTopKickers(List<CardData> cards, List<Integer> excludeValues, int count) {
        cards.sort((a, b) -> Integer.compare(b.value, a.value));
        List<Integer> kickers = new ArrayList<>();
        for (CardData c : cards) {
            if (!excludeValues.contains(c.value) && kickers.size() < count) {
                kickers.add(c.value);
            }
        }
        return kickers;
    }

    private static class CardData {
        int id;
        int value; // 2-14 (Ace is 14)
        int suit;

        public CardData(int id) {
            this.id = id;
            int num = Deck.getNumber(id);
            this.value = num == 1 ? 14 : num;
            this.suit = Deck.getSuit(id);
        }
    }
}
