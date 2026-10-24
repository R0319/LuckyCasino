package io.github.r0319.luckycasino.paper;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;

/** チップとお金の両替。 */
interface Bank {

    /** お金を使わない練習用か。 */
    boolean practice();

    /** 今のお金で買えるチップの枚数。 */
    long affordable(OfflinePlayer player);

    /** chips 枚分のお金を引き出す。足りなければ false。 */
    boolean withdraw(OfflinePlayer player, long chips);

    /** chips 枚分のお金を戻す（オフラインのプレイヤーにも戻せる）。 */
    void deposit(OfflinePlayer player, long chips);

    /** 経済プラグインのお金で両替する。 */
    record Vault(Economy economy, double moneyPerChip) implements Bank {

        @Override
        public boolean practice() {
            return false;
        }

        @Override
        public long affordable(OfflinePlayer player) {
            return (long) Math.floor(economy.getBalance(player) / moneyPerChip);
        }

        @Override
        public boolean withdraw(OfflinePlayer player, long chips) {
            double money = chips * moneyPerChip;
            if (!economy.has(player, money)) {
                return false;
            }
            return economy.withdrawPlayer(player, money).transactionSuccess();
        }

        @Override
        public void deposit(OfflinePlayer player, long chips) {
            if (chips > 0) {
                economy.depositPlayer(player, chips * moneyPerChip);
            }
        }
    }

    /** 経済プラグインが無いサーバー用。チップは無料で配り、離席時に消える。 */
    record Practice(long chips) implements Bank {

        @Override
        public boolean practice() {
            return true;
        }

        @Override
        public long affordable(OfflinePlayer player) {
            return chips;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, long chips) {
            return true;
        }

        @Override
        public void deposit(OfflinePlayer player, long chips) {
        }
    }
}
