package id.omniforge;

import java.util.List;

public record Ability(String id, Trigger trigger, double cooldown, String permission,
                      boolean consume, double manaCost, List<String> actions) {}
