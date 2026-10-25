package io.github.r0319.luckycasino.slot;

/** リールの絵柄。リソースパックの絵柄の並び（tools/gen_slot.py の SYMBOLS）と同じ順。 */
public enum Symbol {
    SEVEN("7"),
    BAR("BAR"),
    BELL("ベル"),
    MELON("スイカ"),
    LEMON("レモン"),
    CHERRY("チェリー");

    private final String displayName;

    Symbol(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
