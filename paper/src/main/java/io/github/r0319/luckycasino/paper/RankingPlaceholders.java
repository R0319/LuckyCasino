package io.github.r0319.luckycasino.paper;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI のプレースホルダー（ホログラムやスコアボードに収支ランキングを出す用）。
 * PlaceholderAPI が入っているときだけ読み込む。
 *
 * <pre>
 * %luckycasino_top_&lt;n&gt;_name%      全ゲーム合計で n 位の人の名前（いなければ ---）
 * %luckycasino_top_&lt;n&gt;_net%       その人の収支（+1,200 / -300 の形。いなければ空）
 * %luckycasino_top_&lt;n&gt;_net_raw%   その人の収支（数だけ）
 * %luckycasino_top_&lt;n&gt;_plays%     その人の遊んだ回数
 * %luckycasino_rank%               見ている人の順位（まだ遊んでいなければ -）
 * %luckycasino_net%  %luckycasino_net_raw%  %luckycasino_plays%   見ている人の収支・回数
 * 先頭にゲームの種類を付けるとそのゲームだけ: %luckycasino_blackjack_top_1_name%、%luckycasino_slot_rank% など
 * </pre>
 */
final class RankingPlaceholders extends PlaceholderExpansion {

    private final LuckyCasinoPaper plugin;

    RankingPlaceholders(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "luckycasino";
    }

    @Override
    public String getAuthor() {
        return "R0319";
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** /papi reload で消えないようにする（このプラグインが登録し直さないため）。 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer viewer, String params) {
        return plugin.stats().ranking().placeholder(viewer == null ? null : viewer.getUniqueId(), params);
    }
}
