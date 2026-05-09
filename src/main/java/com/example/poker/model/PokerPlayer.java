package com.example.poker.model;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class PokerPlayer {
    private final UUID uuid;
    private int chips; // キャッシュではなく、現在ゲームに持ち込んでいるチップ
    private List<Integer> hand;
    private int currentBet;
    private Status status;
    private boolean actedInPhase;

    public enum Status {
        WAITING, PLAYING, FOLDED, ALLIN
    }

    public PokerPlayer(UUID uuid) {
        this.uuid = uuid;
        this.hand = new ArrayList<>();
        this.status = Status.WAITING;
    }

    public UUID getUuid() { return uuid; }
    
    public int getChips() { return chips; }
    public void setChips(int chips) { this.chips = chips; }
    public void addChips(int amount) { this.chips += amount; }
    public void removeChips(int amount) { this.chips -= amount; }

    public List<Integer> getHand() { return hand; }
    public void setHand(List<Integer> hand) { this.hand = hand; }

    public int getCurrentBet() { return currentBet; }
    public void setCurrentBet(int currentBet) { this.currentBet = currentBet; }
    public void addBet(int amount) { this.currentBet += amount; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public boolean isActedInPhase() { return actedInPhase; }
    public void setActedInPhase(boolean actedInPhase) { this.actedInPhase = actedInPhase; }
}
