package net.tfminecraft.magic.command;

import java.util.List;
import java.util.Locale;

/** Shared numeric command semantics. Removal takes a nonnegative magnitude. */
enum ValueAction {
    GET, SET, ADD, REMOVE, RESET;

    static final List<String> NAMES = List.of("get", "set", "add", "remove", "reset");

    static ValueAction parse(String raw) {
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    boolean needsAmount() {
        return this == SET || this == ADD || this == REMOVE;
    }

    double apply(double current, double amount, double reset) {
        return switch (this) {
            case GET -> current;
            case SET -> amount;
            case ADD -> current + amount;
            case REMOVE -> current - amount;
            case RESET -> reset;
        };
    }

    static Double amount(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
