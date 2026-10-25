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

// ================================================================ ビッグホイール

const BW_ORDER = ("J A B A E B A T A B A E A B W A B A A E T B A B A A B " +
  "L E A A B A B T E A A B A W B E A A B A T A B E A B A").split(" ");
const BW_SEG = {
  A: ["1", "#FFFF55", 1], B: ["2", "#5555FF", 2], E: ["5", "#FF55FF", 5], T: ["10", "#55FF55", 10],
  W: ["20", "#FFAA00", 20], J: ["ジョーカー", "#555555", 40], L: ["ロゴ", "#FF5555", 40],
};
const BW_SPOTS = { one: "A", two: "B", five: "E", ten: "T", twenty: "W", joker: "J", logo: "L" };
// BigWheelLayout と同じ数字（ステージ・ベット台・電球はモデル）
const BW_ANGLE = (i) => (0.5 - i / 4) * 1.2;
const BW = {
  shape: {
    model: "bigwheel/stage", modelAt: [0, 2.1, -1.1], modelScale: 2, seatRise: RAISED_SEAT,
    dealer: [1.55, 0, -1.0], status: [-2.5, 2.0, -0.9],
    seats: [0, 1, 2, 3, 4].map((i) => [3 * Math.sin(BW_ANGLE(i)), 0, -1.55 + 3 * Math.cos(BW_ANGLE(i))]),
    // 扇形の席はホイールの方を向く
    seatYaw: (i) => 180 - (BW_ANGLE(i) * 180) / Math.PI,
  },
  SCALE: 3,
  wheel: [0, 2.2, -1.55],
  pointerLabel: [0, 2.2 + 1.5 + 0.45, -1.45],
  stand: (seat) => [2.3 * Math.sin(BW_ANGLE(seat)), 1.001, -1.55 + 2.3 * Math.cos(BW_ANGLE(seat))],
  chip(seat, k) {
    const s = BW.stand(seat);
    const dx = k < 4 ? -0.165 + 0.11 * k : -0.11 + 0.11 * (k - 4);
    const dz = k < 4 ? -0.07 : 0.08;
    return [s[0] + dx, s[1] + seat * 0.0005, s[2] + dz];
  },
  croupier: [1.1, 0.201, -0.75],
};

function bigWheelScenario() {
  const t = new Table(BW.shape);
  t.spawn();
  const YOU = 2;
  t.sit(1, "Bot2", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(3, "Bot4", 1000);
  const SEG = 360 / 54;
  const SPIN_TICKS = 150, STEP = 2;
  const RESULT = 20; // マス 20 は 10
  const random = seeded(20261025);
  const bulbs = new ItemDisplay(at(BW.shape.modelAt), "bigwheel/bulbs_0");
  const bt = transformation([0, 0, 0], null, 0, 2);
  bulbs.tr = { from: bt, to: bt, start: 0, dur: 0 };
  let bulbPhase = false;
  const blink = () => { bulbPhase = !bulbPhase; bulbs.setItem(bulbPhase ? "bigwheel/bulbs_1" : "bigwheel/bulbs_0"); };
  let spinning = false;
  const slowBlink = () => { if (!spinning) blink(); sim.later(20, slowBlink); };
  sim.later(20, slowBlink);
  const wheel = new ItemDisplay(at(BW.wheel), "bigwheel/wheel");
  const wt = (deg) => transformation([0, 0, 0], [0, 0, 1], deg, BW.SCALE);
  wheel.tr = { from: wt(0), to: wt(0), start: 0, dur: 0 };
  const label = t.label(BW.pointerLabel, 0.8);
  const under = (deg) => ((Math.round(deg / SEG) % 54) + 54) % 54;
  let shown = null;
  const show = (seg) => { shown = seg; label.text([[[BW_SEG[seg][0], BW_SEG[seg][1]]]]); };
  show(BW_ORDER[0]);
  let wheelAngle = 0;

  // BigWheelTable.play と同じ計算
  function play() {
    const start = wheelAngle;
    const jitter = ((random.nextInt(1000) / 1000) - 0.5) * SEG * 0.7;
    const target = RESULT * SEG + jitter;
    const base = start + 2 * 360 + random.nextInt(360);
    const end = base + ((((target - base) % 360) + 360) % 360);
    const total = end - start;
    t.swing();
    spinning = true;
    for (let tt = 4; tt <= SPIN_TICKS + 40; tt += 4) sim.later(tt, blink);
    sim.later(SPIN_TICKS + 40, () => { spinning = false; });
    for (let tt = STEP; tt <= SPIN_TICKS; tt += STEP) {
      const p = tt / SPIN_TICKS;
      const angle = start + total * (1 - Math.pow(1 - p, 3));
      const seg = BW_ORDER[under(angle)];
      sim.later(tt, () => {
        wheel.interpolationDuration = STEP;
        wheel.setTransformation(wt(angle));
        if (seg !== shown) show(seg);
      });
    }
    wheelAngle = end % 360;
    sim.later(SPIN_TICKS + 2, () => { show(BW_ORDER[RESULT]); sparkle(at(BW.pointerLabel), 0xffffff); });
    return SPIN_TICKS + 2;
  }
  const seg = BW_ORDER[RESULT];
  const flow = spotFlow({
    table: t, you: YOU, bettingSeconds: 6, minBet: 10, maxBet: 500, maxTotal: 2000,
    chipSpot: (spot, seat) => BW.chip(seat, Object.keys(BW_SPOTS).indexOf(spot)),
    croupier: BW.croupier, spotName: (s) => BW_SEG[BW_SPOTS[s]][0],
    play,
    payout: (spot, amount) => (BW_SPOTS[spot] === seg ? amount * (BW_SEG[seg][2] + 1) : 0),
    resultText: () => [BW_SEG[seg][0] + "（" + BW_SEG[seg][2] + ":1）", BW_SEG[seg][1]],
  });
  sim.later(14, () => flow.place(1, "one", 50));
  sim.later(18, () => flow.place(3, "two", 30));
  sim.later(30, () => flow.place(YOU, "ten", 20));
  sim.later(42, () => flow.place(YOU, "one", 50));
  return {
    you: YOU, table: t,
    camera(kind) {
      if (kind === "seat") {
        const p = t.shape.seats[YOU];
        camera.position.set(p[0], 1.48 + RAISED_SEAT, p[2]);
        controls.target.set(0, 1.9, -1.55);
      } else if (kind === "high") {
        camera.position.set(2.6, 3.4, 4.6);
        controls.target.set(0, 1.5, -0.8);
      } else {
        camera.position.set(0, 2.2, 1.3);
        controls.target.set(0, 2.2, -1.55);
      }
    },
  };
}

addGame("bigwheel", "ビッグホイール", { make: bigWheelScenario, length: 400 });

// ================================================================ 大小（シックボー）

const SB = {
  shape: { model: "table/sicbo", seatRise: RAISED_SEAT, dealer: [0, 0, -1.55], seats: [0, 1, 2, 3, 4].map((i) => [1.6 - 0.8 * i, 0, 1.55]) },
  TRAY_SCALE: 0.56, DIE_SCALE: 0.13,
  tray: [0.75, TOP + 0.28, -0.68],
  dice: [0.75, TOP + 0.56 / 16 + 0.065, -0.68],
  resultLabel: [0.05, TOP + 0.45, -0.68],
  croupier: [-1.5, TOP + 0.001, -0.72],
  REST: [[-0.1, 0.06], [0.1, 0.07], [0, -0.1]],
  spot(id) {
    const front = { small: 0, odd: 1, even: 2, big: 3 };
    if (id in front) return [-1.44 + 0.96 * front[id], TOP + 0.001, 0.73];
    if (id === "any_triple") return [-1.74 + 0.29 * 6, TOP + 0.001, -0.26];
    const n = Number(id.slice(id.lastIndexOf("_") + 1));
    if (id.startsWith("total_")) return [-1.783 + 0.2743 * (n - 4), TOP + 0.001, 0.06];
    if (id.startsWith("single_")) return [-1.6 + 0.64 * (n - 1), TOP + 0.001, 0.38];
    return [-1.74 + 0.29 * (id.startsWith("double_") ? n - 1 : n + 6), TOP + 0.001, -0.26];
  },
};

function sbQuat(axis, rad) { return new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(...axis), rad); }
/** SicBoTable.faceUp と同じ。 */
function sbFaceUp(face) {
  const q = Math.PI / 2;
  return {
    1: new THREE.Quaternion(), 2: sbQuat([1, 0, 0], q), 3: sbQuat([0, 0, 1], q),
    4: sbQuat([0, 0, 1], -q), 5: sbQuat([1, 0, 0], -q), 6: sbQuat([1, 0, 0], 2 * q),
  }[face];
}
function sbPayout(dice, bet, amount) {
  const total = dice[0] + dice[1] + dice[2];
  const triple = dice[0] === dice[1] && dice[1] === dice[2];
  const count = (n) => dice.filter((d) => d === n).length;
  const pays = { 4: 60, 5: 30, 6: 17, 7: 12, 8: 8, 9: 6, 10: 6, 11: 6, 12: 6, 13: 8, 14: 12, 15: 17, 16: 30, 17: 60 };
  if (bet === "small") return !triple && total <= 10 ? amount * 2 : 0;
  if (bet === "big") return !triple && total >= 11 ? amount * 2 : 0;
  if (bet === "odd") return !triple && total % 2 === 1 ? amount * 2 : 0;
  if (bet === "even") return !triple && total % 2 === 0 ? amount * 2 : 0;
  if (bet === "any_triple") return triple ? amount * 31 : 0;
  const n = Number(bet.slice(bet.lastIndexOf("_") + 1));
  if (bet.startsWith("total_")) return total === n ? amount * (pays[n] + 1) : 0;
  if (bet.startsWith("triple_")) return count(n) === 3 ? amount * 181 : 0;
  if (bet.startsWith("double_")) return count(n) >= 2 ? amount * 11 : 0;
  return count(n) > 0 ? amount * (1 + count(n)) : 0;
}
function sbName(id) {
  const fixed = { small: "小", big: "大", odd: "奇数", even: "偶数", any_triple: "ゾロ目どれでも" };
  if (id in fixed) return fixed[id];
  const n = id.slice(id.lastIndexOf("_") + 1);
  return { total: "合計 ", single: "目 ", double: "ダブル ", triple: "ゾロ目 " }[id.slice(0, id.lastIndexOf("_"))] + n;
}

function sicBoScenario() {
  const t = new Table(SB.shape);
  t.spawn();
  const YOU = 2;
  t.sit(0, "Bot1", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(3, "Bot4", 1000);
  const DICE = [2, 5, 5];
  const random = seeded(4242);
  const rnd = () => random.nextInt(10000) / 10000;
  const SHAKE_TICKS = 42, SHAKE_STEP = 3, SETTLE = 6, LIFT_AT = 42 + 6 + 10, LIFT_TICKS = 14;
  const tr = (t3, q, s) => ({ t: t3, q, s: [s, s, s] });
  const still = (d) => { d.tr = { from: d.tr.to, to: d.tr.to, start: 0, dur: 0 }; };
  const base = new ItemDisplay(at(SB.tray), "sicbo/base");
  base.tr = { from: tr([0, 0, 0], new THREE.Quaternion(), SB.TRAY_SCALE), to: tr([0, 0, 0], new THREE.Quaternion(), SB.TRAY_SCALE), start: 0, dur: 0 };
  const dome = new ItemDisplay(at(SB.tray), "sicbo/dome");
  dome.tr = { ...base.tr };
  const dieAt = (i, face, yaw) => tr([SB.REST[i][0], 0, SB.REST[i][1]], sbQuat([0, 1, 0], yaw).multiply(sbFaceUp(face)), SB.DIE_SCALE);
  const dice = [4, 5, 6].map((face, i) => {
    const d = new ItemDisplay(at(SB.dice), "sicbo/die");
    d.tr = { from: dieAt(i, face, 0.4 * i), to: dieAt(i, face, 0.4 * i), start: 0, dur: 0 };
    return d;
  });
  const move = (e, to, ticks) => { e.interpolationDuration = ticks; e.setTransformation(to); };
  let resultLabel = null;
  const total = DICE[0] + DICE[1] + DICE[2];
  const resultText = () => [DICE.join("・") + " 合計 " + total + " " + (total <= 10 ? "小" : "大") + (total % 2 ? "・奇数" : "・偶数"), total <= 10 ? C.AQUA : C.RED];

  // SicBoTable.play と同じ順番・間合い
  function play() {
    resultLabel = t.label(SB.resultLabel, 0.8);
    t.swing();
    t.broadcast([["サイコロを振ります", C.GRAY]]);
    for (let tt = 0; tt <= SHAKE_TICKS; tt += SHAKE_STEP) {
      const hop = [0, 1, 2].map(() => {
        const e = new THREE.Euler(rnd() * 6.3, rnd() * 6.3, rnd() * 6.3, "XYZ");
        return tr([(rnd() - 0.5) * 0.3, rnd() * 0.2, (rnd() - 0.5) * 0.3], new THREE.Quaternion().setFromEuler(e), SB.DIE_SCALE);
      });
      const shake = tr([(rnd() - 0.5) * 0.02, 0, (rnd() - 0.5) * 0.02], new THREE.Quaternion(), SB.TRAY_SCALE);
      sim.later(Math.max(1, tt), () => { dice.forEach((d, i) => move(d, hop[i], SHAKE_STEP)); move(dome, shake, SHAKE_STEP); });
    }
    const rest = DICE.map((face, i) => dieAt(i, face, (rnd() - 0.5) * 1.2));
    sim.later(SHAKE_TICKS + SHAKE_STEP, () => {
      dice.forEach((d, i) => move(d, rest[i], SETTLE));
      move(dome, tr([0, 0, 0], new THREE.Quaternion(), SB.TRAY_SCALE), SETTLE);
    });
    sim.later(LIFT_AT, () => move(dome, tr([0, 0.5, -0.35], new THREE.Quaternion(), SB.TRAY_SCALE), LIFT_TICKS));
    const shown = LIFT_AT + LIFT_TICKS;
    sim.later(shown, () => { resultLabel.text([[resultText()]]); sparkle(at(SB.resultLabel), 0xffffff); });
    return shown + 2;
  }

  const flow = spotFlow({
    table: t, you: YOU, bettingSeconds: 6, minBet: 10, maxBet: 500, maxTotal: 2000,
    chipSpot: (spot, seat) => {
      const c = SB.spot(spot);
      const d = (seat - 2) * 0.008;
      return [c[0] + d, c[1] + seat * 0.0005, c[2] + d * 0.5];
    },
    croupier: SB.croupier, spotName: sbName,
    play, payout: (spot, amount) => sbPayout(DICE, spot, amount), resultText,
    clearRound() {
      move(dome, tr([0, 0, 0], new THREE.Quaternion(), SB.TRAY_SCALE), 10);
      if (resultLabel) resultLabel.remove();
    },
  });
  sim.later(14, () => flow.place(0, "small", 50));
  sim.later(18, () => { flow.place(3, "total_12", 10); flow.place(3, "triple_6", 10); });
  sim.later(30, () => flow.place(YOU, "big", 100));
  sim.later(42, () => flow.place(YOU, "single_5", 20));
  return {
    you: YOU, table: t,
    camera(kind) {
      if (kind === "seat") {
        camera.position.set(t.shape.seats[YOU][0], 1.48 + RAISED_SEAT, 1.5);
        controls.target.set(0.4, 0.95, -0.6);
      } else if (kind === "high") {
        camera.position.set(0.3, 3.4, 3.2);
        controls.target.set(0.1, 0.9, -0.1);
      } else {
        camera.position.set(0.75, 1.6, -0.35);
        controls.target.set(0.75, TOP, -0.68);
      }
    },
  };
}

addGame("sicbo", "大小", { make: sicBoScenario, length: 300 });

// ================================================================ ハイ＆ロー

const HL = {
  shape: { model: "table/highlow", seatRise: RAISED_SEAT, dealer: [0, 0, -1.55], seats: [0, 1, 2, 3, 4].map((i) => [1.6 - 0.8 * i, 0, 1.55]) },
  shoe: [1.5, TOP + 0.05, -0.75],
  base: [-0.3, TOP + 0.004, -0.4],
  next: [0.3, TOP + 0.004, -0.4],
  history: (i) => [-0.42 - 0.2 * i, TOP + 0.003, -0.8],
  cardLabel: (next) => [next ? 0.3 : -0.3, TOP + 0.3, -0.4],
  box: (bet) => [{ high: -1.15, same: 0, low: 1.15 }[bet], TOP + 0.001, 0.45],
  oddsLabel: (bet) => [{ high: -1.15, same: 0, low: 1.15 }[bet], TOP + 0.08, 0.25],
  croupier: [1.2, TOP + 0.001, -0.3],
};
const hlRank = (v) => ({ 1: "A", 11: "J", 12: "Q", 13: "K" })[v] || String(v);
const hlValue = (c) => c.rank + 1;
function hlPercent(bet, base) {
  const wins = bet === "high" ? 13 - base : bet === "low" ? base - 1 : 1;
  return wins === 0 ? 0 : Math.floor(1235 / wins);
}
const hlPercentText = (p) => Math.floor(p / 100) + "." + String(p % 100).padStart(2, "0") + " 倍";

function highLowScenario() {
  const t = new Table(HL.shape);
  t.spawn();
  const YOU = 2;
  t.sit(0, "Bot1", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(4, "Bot5", 1000);
  // 基準 5 → 9（ハイ）、次の回は基準 9 → 9（同じ）
  const draws = ["H9", "C9"];
  const history = ["S2", "DQ", "H6"].map((c, i) => CardView.deal(at(HL.history(i)), [0, 0, 0], card(c).code, true));
  let base = card("C5");
  let baseView = CardView.deal(at(HL.base), delta(HL.shoe, HL.base), base.code, true);
  let next = null, nextView = null;
  const baseLabel = t.label(HL.cardLabel(false), 0.5);
  const nextLabel = t.label(HL.cardLabel(true), 0.5);
  const odds = {};
  for (const bet of ["high", "same", "low"]) odds[bet] = t.label(HL.oddsLabel(bet), 0.45);
  function showBase() {
    const b = hlValue(base);
    baseLabel.text([[["基準", C.GRAY]], [[hlRank(b), C.WHITE]]]);
    nextLabel.text([[["次は？", C.YELLOW]]]);
    for (const bet in odds) {
      const p = hlPercent(bet, b);
      odds[bet].text([[p === 0 ? ["なし", C.DARK_GRAY] : [hlPercentText(p), C.GOLD]]]);
    }
  }
  showBase();
  const verdict = () => {
    const b = hlValue(base), n = hlValue(next);
    return [[hlRank(n) + " ", C.WHITE], n > b ? ["ハイ", C.GREEN] : n < b ? ["ロー", C.AQUA] : ["同じ！", C.GOLD]];
  };

  // HighLowTable.play と同じ間合い
  function play() {
    next = card(draws.shift());
    nextView = CardView.deal(at(HL.next), delta(HL.shoe, HL.next), next.code, false);
    t.swing();
    sim.later(14, () => t.broadcast([["めくります…", C.GRAY]]));
    const shown = next;
    sim.later(30, () => {
      nextView.reveal(shown.code);
      nextLabel.text([[["次", C.GRAY]], verdict()]);
      sparkle(at(HL.cardLabel(true)), 0xffffff);
    });
    return 36;
  }
  function clearRound() {
    history.unshift(baseView);
    while (history.length > 7) history.pop().remove();
    history.forEach((v, i) => v.moveTo(at(HL.history(i))));
    nextView.moveTo(at(HL.base));
    baseView = nextView;
    base = next;
    next = nextView = null;
    showBase();
  }
  const flow = spotFlow({
    table: t, you: YOU, bettingSeconds: 6, minBet: 10, maxBet: 500, maxTotal: 1500,
    chipSpot: (spot, seat) => {
      const c = HL.box(spot);
      const d = (seat - 2) * 0.014;
      return [c[0] + d, c[1] + seat * 0.0005, c[2] + d * 0.5];
    },
    croupier: HL.croupier,
    spotName: (s) => ({ high: "ハイ", same: "同じ", low: "ロー" })[s] + "（" + hlPercentText(hlPercent(s, hlValue(base))) + "）",
    play, clearRound,
    payout: (spot, amount) => {
      const b = hlValue(base), n = hlValue(next);
      const won = spot === "high" ? n > b : spot === "low" ? n < b : n === b;
      return won ? Math.floor(amount * hlPercent(spot, b) / 100) : 0;
    },
    resultText: () => [hlRank(hlValue(base)) + " → " + hlRank(hlValue(next)) + (hlValue(next) > hlValue(base) ? " ハイ" : hlValue(next) < hlValue(base) ? " ロー" : " 同じ！"), C.WHITE],
  });
  sim.later(14, () => flow.place(0, "low", 50));
  sim.later(20, () => flow.place(4, "high", 30));
  sim.later(34, () => flow.place(YOU, "high", 100));
  sim.later(272, () => flow.place(4, "low", 50));
  sim.later(280, () => flow.place(YOU, "same", 20));
  sim.later(290, () => flow.place(0, "high", 10));
  return {
    you: YOU, table: t,
    camera(kind) {
      if (kind === "seat") {
        camera.position.set(t.shape.seats[YOU][0], 1.48 + RAISED_SEAT, 1.5);
        controls.target.set(0, 0.95, -0.4);
      } else if (kind === "high") {
        camera.position.set(0, 3.4, 3.2);
        controls.target.set(0, 0.9, -0.1);
      } else {
        camera.position.set(-0.4, 1.9, 0.2);
        controls.target.set(-0.4, TOP, -0.5);
      }
    },
  };
}

addGame("highlow", "ハイ＆ロー", { make: highLowScenario, length: 540 });

// ================================================================ 競馬

const HR = {
  shape: { model: "table/horserace", seatRise: RAISED_SEAT, dealer: [0, 0, -1.55], seats: [0, 1, 2, 3, 4].map((i) => [1.6 - 0.8 * i, 0, 1.55]) },
  START_X: -1.62, FINISH_X: 1.55, SCALE: 0.32,
  laneZ: (h) => -0.88 + 0.14 * h,
  start: (h) => [-1.62, TOP + 0.16, -0.88 + 0.14 * h],
  box: (h) => [-1.6 + 0.64 * h, TOP + 0.001, 0.5],
  oddsLabel: (h) => [-1.6 + 0.64 * h, TOP + 0.08, 0.2],
  board: [0, TOP + 0.75, -0.5],
  croupier: [1.6, TOP + 0.001, 0.05],
};
const HR_COLORS = ["白", "黒", "赤", "青", "黄", "緑"];
const HR_INKS = [C.WHITE, C.DARK_GRAY, C.RED, C.BLUE, C.YELLOW, C.GREEN];
const hrName = (h) => [(h + 1) + " 番（" + HR_COLORS[h] + "）", HR_INKS[h]];
/** HorseRaceTable.progress と同じ。 */
const hrProgress = (u, pace, wave, phase) => u + pace * u * (1 - u) + wave * Math.sin(Math.PI * u) * Math.sin(2 * Math.PI * u + phase);

function horseRaceScenario() {
  const t = new Table(HR.shape);
  t.spawn();
  const YOU = 2;
  t.sit(0, "Bot1", 1000);
  t.sit(1, "Bot2", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(4, "Bot5", 1000);
  const random = seeded(777);
  const rnd = () => random.nextInt(100000) / 100000;
  // 出走表（勝つ確率）と着順。あなたは 4 番（青）を買い、3 番と競り合って差し切る
  const chance = [0.28, 0.08, 0.22, 0.12, 0.2, 0.1];
  const percent = chance.map((p) => Math.floor(90 / p));
  const order = [3, 2, 4, 0, 5, 1];
  const STEP = 2;
  const pose = (dx, dy) => ({ t: [dx, dy, 0], q: new THREE.Quaternion(), s: [HR.SCALE, HR.SCALE, HR.SCALE] });
  const horses = [0, 1, 2, 3, 4, 5].map((h) => {
    const d = new ItemDisplay(at(HR.start(h)), "horserace/horse_" + (h + 1));
    d.tr = { from: pose(0, 0), to: pose(0, 0), start: 0, dur: 0 };
    return d;
  });
  const hrPct = (p) => Math.floor(p / 100) + "." + String(p % 100).padStart(2, "0") + " 倍";
  for (let h = 0; h < 6; h++) t.label(HR.oddsLabel(h), 0.45).text([[[hrPct(percent[h]), C.GOLD]]]);
  const board = t.label(HR.board, 0.6);
  board.text([[["次のレースの単勝を受付中", C.AQUA]]]);
  const move = (e, to, ticks) => { e.interpolationDuration = ticks; e.setTransformation(to); };

  // HorseRaceTable.play と同じ計算
  function play() {
    const length = HR.FINISH_X - HR.START_X;
    const finish = [];
    let tt = 160 + random.nextInt(12);
    order.forEach((h, place) => { finish[h] = tt; tt += 2 + random.nextInt(place === 0 ? 3 : 8); });
    const last = tt;
    const pace = [], phase = [];
    for (let h = 0; h < 6; h++) { pace[h] = (rnd() - 0.5) * 0.6; phase[h] = rnd() * 2 * Math.PI; }
    // 見せ場のため、勝つ 4 番は追い込み（前半は遅め）、3 番は逃げ（前半は速め）
    pace[3] = -0.3; pace[2] = 0.3;
    t.swing();
    t.broadcast([["スタートしました！", C.GOLD]]);
    for (let tick = STEP; tick <= last + 10; tick += STEP) {
      const x = [], p = [];
      for (let h = 0; h < 6; h++) {
        const u = tick / finish[h];
        x[h] = u <= 1 ? length * hrProgress(u, pace[h], 0.05, phase[h]) : length + 0.15 * (1 - Math.exp(-(tick - finish[h]) / 8));
        const bob = u <= 1.1 && (tick / STEP + h) % 2 === 0 ? 0.025 : 0;
        p[h] = pose(x[h], bob);
      }
      let lead = 0;
      for (let h = 1; h < 6; h++) if (x[h] > x[lead]) lead = h;
      const now = tick;
      sim.later(tick, () => {
        horses.forEach((d, h) => move(d, p[h], STEP));
        if (now < finish[order[0]] && now % 10 === 0) board.text([[["先頭 ", C.GRAY], hrName(lead)]]);
      });
    }
    const winnerAt = finish[order[0]];
    sim.later(winnerAt - 30, () => t.broadcast([["最後の直線！", C.YELLOW]]));
    sim.later(winnerAt, () => {
      board.text([[["1 着 ", C.GOLD], hrName(order[0])]]);
      sparkle(at([HR.FINISH_X, TOP + 0.2, HR.laneZ(order[0])]), 0xffffff);
    });
    sim.later(last + 6, () => board.text([[["1 着 ", C.GOLD], hrName(order[0])], [["2 着 ", C.GRAY], hrName(order[1]), ["　3 着 ", C.GRAY], hrName(order[2])]]));
    return last + 8;
  }
  const flow = spotFlow({
    table: t, you: YOU, bettingSeconds: 6, minBet: 10, maxBet: 500, maxTotal: 1500,
    chipSpot: (spot, seat) => {
      const c = HR.box(Number(spot.slice(6)) - 1);
      const d = (seat - 2) * 0.014;
      return [c[0] + d, c[1] + seat * 0.0005, c[2] + d * 0.5];
    },
    croupier: HR.croupier,
    spotName: (s) => hrName(Number(s.slice(6)) - 1)[0],
    play,
    payout: (spot, amount) => (Number(spot.slice(6)) - 1 === order[0] ? Math.floor(amount * percent[order[0]] / 100) : 0),
    resultText: () => ["1 着 " + hrName(order[0])[0] + "（" + hrPct(percent[order[0]]) + "）", HR_INKS[order[0]]],
    clearRound() { horses.forEach((d) => move(d, pose(0, 0), 20)); },
  });
  sim.later(14, () => flow.place(0, "horse_1", 50));
  sim.later(20, () => flow.place(1, "horse_3", 30));
  sim.later(26, () => flow.place(4, "horse_5", 20));
  sim.later(36, () => flow.place(YOU, "horse_4", 50));
  return {
    you: YOU, table: t,
    camera(kind) {
      if (kind === "seat") {
        camera.position.set(t.shape.seats[YOU][0], 1.48 + RAISED_SEAT, 1.5);
        controls.target.set(0, 0.95, -0.5);
      } else if (kind === "high") {
        camera.position.set(0, 3.4, 3.2);
        controls.target.set(0, 0.9, -0.2);
      } else {
        camera.position.set(0, 2.6, 0.4);
        controls.target.set(0, TOP, -0.55);
      }
    },
  };
}

addGame("horserace", "競馬", { make: horseRaceScenario, length: 480 });
