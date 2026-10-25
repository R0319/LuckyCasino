// 賭け場所を選んで賭ける卓（paper の SpotTable）のプレビュー。preview.js の後ろにつなげて読み込む。
// 位置・時間は paper の *Layout と *Table をそのまま移したもの。
"use strict";

/** 新しいゲームのタブを足す。 */
function addGame(id, label, game) {
  GAMES[id] = game;
  const b = document.createElement("button");
  b.type = "button";
  b.id = "tab-" + id;
  b.dataset.game = id;
  b.setAttribute("aria-pressed", "false");
  b.textContent = label;
  b.addEventListener("click", () => start(id));
  document.querySelector(".tabs").appendChild(b);
}

/**
 * SpotTable の共通の流れ（賭け → 締め切り → play → 精算 → 片付け）。
 * opts: { table, you, bettingSeconds, minBet, maxBet, maxTotal, chipSpot(spot, seat), croupier, play() → tick, payout(spot, amount),
 *         resultText() → [文字, 色], spotName(spot) }
 */
function spotFlow(opts) {
  const t = opts.table;
  let state = "IDLE", countdown = 0;
  const bets = {}, chipViews = {}, payoutViews = [];
  const limits = [["1 か所 " + opts.minBet + "〜" + opts.maxBet + "、合計 " + opts.maxTotal + " まで（練習用）", C.GRAY]];
  function status() {
    const head = {
      IDLE: ["ベットを待っています", C.AQUA],
      BETTING: ["ベット受付中（あと " + Math.max(countdown, 0) + " 秒）", C.AQUA],
      PLAYING: ["ノーモアベット", C.YELLOW],
      PAYING: ["払い戻し中", C.YELLOW],
    }[state];
    t.setStatus([[head], limits]);
  }
  status();

  function place(seat, spot, amount) {
    bets[seat] = bets[seat] || {};
    bets[seat][spot] = (bets[seat][spot] || 0) + amount;
    t.seats[seat].chips -= amount;
    chipViews[seat] = chipViews[seat] || {};
    const view = chipViews[seat][spot] = chipViews[seat][spot] || new ChipView();
    view.show(at(opts.chipSpot(spot, seat)), bets[seat][spot], false);
    if (seat === opts.you) chat([["（メニューで " + opts.spotName(spot) + " に " + amount + " 枚）", C.DARK_GRAY]]);
    if (state === "IDLE") {
      state = "BETTING";
      countdown = opts.bettingSeconds;
      t.broadcast([["ベットを受け付けています（あと " + countdown + " 秒）", C.AQUA]]);
    }
    status();
  }

  const tick = () => {
    if (state === "BETTING") {
      countdown--;
      if (countdown === 3) t.broadcast([["まもなく締め切ります", C.YELLOW]]);
      if (countdown <= 0) begin();
      status();
    }
    sim.later(20, tick);
  };
  sim.later(20, tick);

  function begin() {
    state = "PLAYING";
    t.broadcast([["ノーモアベット", C.AQUA]]);
    const ticks = opts.play();
    sim.later(ticks + 10, payout);
  }

  function payout() {
    state = "PAYING";
    const result = opts.resultText();
    for (const seat in bets) {
      let sum = 0, back = 0;
      for (const spot in bets[seat]) {
        const amount = bets[seat][spot];
        const pay = opts.payout(spot, amount);
        sum += amount;
        back += pay;
        t.seats[seat].chips += pay;
        const view = chipViews[seat][spot];
        if (pay === amount) continue;
        if (pay === 0) view.slideTo(at(opts.croupier));
        else {
          const w = new ChipView();
          w.show(at(opts.croupier), pay - amount, false);
          payoutViews.push(w);
          const p = opts.chipSpot(spot, Number(seat));
          sim.later(3, () => w.slideTo(at([p[0] + 0.05, p[1], p[2] - 0.05])));
        }
      }
      if (Number(seat) === opts.you) {
        const net = back - sum;
        showTitle(result, [net > 0 ? "+" + net + " 枚" : net === 0 ? "±0" : net + " 枚", net > 0 ? C.GREEN : net === 0 ? C.YELLOW : C.RED], 40);
        chat([["結果 ", C.WHITE], result, ["：払い戻し " + back + " 枚（手元 " + t.seats[seat].chips + " 枚）", C.GRAY]]);
      }
    }
    status();
    sim.later(80, finish);
  }

  function finish() {
    for (const s in chipViews) for (const k in chipViews[s]) chipViews[s][k].remove();
    payoutViews.forEach((v) => v.remove());
    if (opts.clearRound) opts.clearRound();
    state = "IDLE";
    status();
    t.broadcast([["次の回のベットを受け付けています", C.AQUA]]);
  }

  return { place };
}

// ================================================================ バカラ

const BC = {
  PLAYER_Z: 0.62, BANKER_Z: 0.30, TIE_Z: 0.03, PAIR_DX: 0.25,
  CARD_Z: -0.48, PLAYER_CARD_X: -0.75, BANKER_CARD_X: 0.35, CARD_STEP: 0.2,
  seatX: (i) => 1.6 - 0.8 * i,
  shape: { model: "table/baccarat", seatRise: RAISED_SEAT, dealer: [0, 0, -1.55], seats: [0, 1, 2, 3, 4].map((i) => [1.6 - 0.8 * i, 0, 1.55]) },
  shoe: [1.5, TOP + 0.05, -0.75],
  croupier: [-1.35, TOP + 0.001, -0.6],
  box(spot, seat) {
    const x = BC.seatX(seat);
    return {
      player: [x, TOP + 0.001, BC.PLAYER_Z],
      banker: [x, TOP + 0.001, BC.BANKER_Z],
      tie: [x, TOP + 0.001, BC.TIE_Z],
      player_pair: [x - BC.PAIR_DX, TOP + 0.001, BC.TIE_Z],
      banker_pair: [x + BC.PAIR_DX, TOP + 0.001, BC.TIE_Z],
    }[spot];
  },
  card(side, i) {
    const x = (side === "P" ? BC.PLAYER_CARD_X : BC.BANKER_CARD_X) + BC.CARD_STEP * i;
    return [x, TOP + 0.004 + 0.003 * i, BC.CARD_Z];
  },
  totalLabel(side) {
    const c = BC.card(side, 1);
    return [c[0] - BC.CARD_STEP / 2, TOP + 0.45, BC.CARD_Z];
  },
};
const BC_NAME = { player: "プレイヤー", banker: "バンカー", tie: "タイ", player_pair: "プレイヤーペア", banker_pair: "バンカーペア" };

function bacValue(c) { return (c.rank + 1 >= 10 ? 0 : c.rank + 1); }
function bacTotal(cards) { return cards.reduce((s, c) => s + bacValue(c), 0) % 10; }

function baccaratScenario() {
  const t = new Table(BC.shape);
  t.spawn();
  const YOU = 2;
  t.sit(0, "Bot1", 1000);
  t.sit(1, "Bot2", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(4, "Bot5", 1000);
  // 配る順（P, B, P, B, P3, B3）。P: 4+K=4 → 3 枚目の 3 で 7。B: 2+3=5 は、P の 3 枚目が 4〜7 のときだけ引くので止まる
  const deals = [["P", "H4"], ["B", "S2"], ["P", "CK"], ["B", "D3"], ["P", "S3"]];
  const player = [], banker = [];
  for (const [side, c] of deals) (side === "P" ? player : banker).push(card(c));
  const p = bacTotal(player), b = bacTotal(banker);
  const winner = p > b ? "player" : b > p ? "banker" : "tie";
  const views = [];
  let playerLabel = null, bankerLabel = null, resultLabel = null;

  function resultText() {
    const head = { player: ["プレイヤーの勝ち", C.AQUA], banker: ["バンカーの勝ち", C.RED], tie: ["タイ", C.GREEN] }[winner];
    return [head[0] + "（" + p + " 対 " + b + "）", head[1]];
  }
  function payout(spot, amount) {
    if (spot === "player") return winner === "player" ? amount * 2 : winner === "tie" ? amount : 0;
    if (spot === "banker") return winner === "banker" ? amount + Math.floor(amount * 95 / 100) : winner === "tie" ? amount : 0;
    if (spot === "tie") return winner === "tie" ? amount * 9 : 0;
    const cards = spot === "player_pair" ? player : banker;
    return cards[0].rank === cards[1].rank ? amount * 12 : 0;
  }

  function dealCard(i) {
    const [side] = deals[i];
    const n = deals.slice(0, i).filter(([s]) => s === side).length;
    const pos = BC.card(side, n);
    views.push(CardView.deal(at(pos), delta(BC.shoe, pos), card(deals[i][1]).code, false));
    t.swing();
  }
  function updateTotals(shown) {
    const pc = [], bc = [];
    for (let i = 0; i < shown; i++) (deals[i][0] === "P" ? pc : bc).push(card(deals[i][1]));
    if (shown >= 3) playerLabel.text([[["プレイヤー", C.AQUA]], [[String(bacTotal(pc)), C.WHITE]]]);
    if (shown >= 4) bankerLabel.text([[["バンカー", C.RED]], [[String(bacTotal(bc)), C.WHITE]]]);
  }

  // BaccaratTable.play と同じ順番・間合い
  function play() {
    playerLabel = t.label(BC.totalLabel("P"), 0.5);
    bankerLabel = t.label(BC.totalLabel("B"), 0.5);
    resultLabel = t.label([-0.2, TOP + 0.85, BC.CARD_Z], 0.9);
    for (let i = 0; i < 4; i++) sim.later(8 * i, () => dealCard(i));
    let tt = 8 * 4 + 8;
    const revealPair = (a, c, at0) => {
      sim.later(at0, () => views[a].reveal(card(deals[a][1]).code));
      sim.later(at0 + 8, () => { views[c].reveal(card(deals[c][1]).code); updateTotals(Math.max(a, c) + 1); });
      return at0 + 8 + 18;
    };
    tt = revealPair(0, 2, tt);
    tt = revealPair(1, 3, tt);
    for (let i = 4; i < deals.length; i++) {
      const side = deals[i][0];
      sim.later(tt, () => { t.broadcast([[(side === "P" ? "プレイヤー" : "バンカー") + "が 3 枚目を引きます", C.GRAY]]); dealCard(i); });
      sim.later(tt + 16, () => { views[i].reveal(card(deals[i][1]).code); updateTotals(i + 1); });
      tt += 28;
    }
    sim.later(tt, () => {
      resultLabel.text([[resultText()]]);
      sparkle(at([-0.2, TOP + 0.85, BC.CARD_Z]), 0xffffff);
    });
    return tt + 10;
  }

  const flow = spotFlow({
    table: t, you: YOU, bettingSeconds: 6, minBet: 10, maxBet: 1000, maxTotal: 2000,
    chipSpot: (spot, seat) => BC.box(spot, seat), croupier: BC.croupier, spotName: (s) => BC_NAME[s],
    play, payout, resultText,
    clearRound() {
      views.forEach((v) => v.remove());
      [playerLabel, bankerLabel, resultLabel].forEach((l) => l && l.remove());
    },
  });
  sim.later(14, () => flow.place(0, "banker", 50));
  sim.later(18, () => flow.place(1, "player", 100));
  sim.later(22, () => { flow.place(4, "banker", 20); flow.place(4, "tie", 10); });
  sim.later(30, () => flow.place(YOU, "player", 100));
  sim.later(44, () => flow.place(YOU, "player_pair", 10));
  return { you: YOU, table: t };
}

addGame("baccarat", "バカラ", { make: baccaratScenario, length: 480 });
