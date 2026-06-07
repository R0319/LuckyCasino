package io.github.r0319.luckyCasino.blackjack.table;

/**
 * Persistent configuration data for one blackjack table.
 * Serialized to/from {@code tables/<tableId>.json} via Gson.
 */
public class BlackjackTable {

    private String tableId;
    private SerializableLocation dealerLocation;
    /** Index 0-3 maps to player slots 1-4. Null means the slot has no location set. */
    private SerializableLocation[] playerLocations = new SerializableLocation[4];
    private TableState state = TableState.WAITING;

    /** No-arg constructor required by Gson. */
    public BlackjackTable() {}

    public BlackjackTable(String tableId) {
        this.tableId = tableId;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getTableId() { return tableId; }

    public SerializableLocation getDealerLocation() { return dealerLocation; }

    public SerializableLocation[] getPlayerLocations() { return playerLocations; }

    public TableState getState() { return state; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setTableId(String tableId) { this.tableId = tableId; }

    public void setDealerLocation(SerializableLocation dealerLocation) {
        this.dealerLocation = dealerLocation;
    }

    /**
     * @param slot 0-indexed slot (0–3)
     * @param loc  location to assign, or null to clear
     */
    public void setPlayerLocation(int slot, SerializableLocation loc) {
        if (slot < 0 || slot > 3) throw new IllegalArgumentException("Slot must be 0-3");
        playerLocations[slot] = loc;
    }

    public void setState(TableState state) { this.state = state; }

    /** Returns the number of player slots that have a location configured. */
    public int configuredSlots() {
        int count = 0;
        for (SerializableLocation sl : playerLocations) {
            if (sl != null) count++;
        }
        return count;
    }
}
