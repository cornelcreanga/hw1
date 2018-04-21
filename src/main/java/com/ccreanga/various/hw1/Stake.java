package com.ccreanga.various.hw1;

public class Stake {
    private int stake;
    private int customerId;

    public Stake(int stake, int customerId) {
        this.stake = stake;
        this.customerId = customerId;
    }

    public int getStake() {
        return stake;
    }

    public int getCustomerId() {
        return customerId;
    }
}
