package id.omniforge;

public enum Trigger {
    RIGHT_CLICK, SHIFT_RIGHT_CLICK, LEFT_CLICK, SHIFT_LEFT_CLICK,
    HIT_ENTITY, KILL_ENTITY, WHEN_HIT, CONSUME;

    public static Trigger parse(String s) {
        if (s == null) return RIGHT_CLICK;
        try {
            return valueOf(s.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return RIGHT_CLICK;
        }
    }
}
