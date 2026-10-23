package com.nexora.finance.domain;

// Which way money moves on an account. Balance = sum of IN minus sum of OUT.
public enum Direction {
    IN,
    OUT;

    public Direction opposite() {
        return this == IN ? OUT : IN;
    }
}
