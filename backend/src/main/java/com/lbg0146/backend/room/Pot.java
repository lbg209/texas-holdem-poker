package com.lbg0146.backend.room;

import java.util.Set;

public record Pot(int amount, Set<String> eligiblePlayerIds) {
}
