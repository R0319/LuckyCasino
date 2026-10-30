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
        const pay = opts.payout(spot, amount, Number(seat));
        sum += amount;
        back += pay;
        t.seats[seat].chips += pay;
        const view = chipViews[seat][spot];
        if (pay === amount) continue;
        if (pay <= 0) view.slideTo(at(opts.croupier));
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
        chat([["結果 ", C.WHITE], result, [(back < 0 ? "：追加の支払い " + -back : "：払い戻し " + back) + " 枚（手元 " + t.seats[seat].chips + " 枚）", C.GRAY]]);
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
  const YOU = 1;
  t.sit(0, "Bot1", 1000);
  t.sit(2, "Bot3", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(3, "Bot4", 1000);
  t.sit(5, "Bot6", 1000);
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

// ================================================================ チンチロ（座敷）

// ChinchiroLayout と同じ数字（畳・座布団・どんぶりはモデル）
const CC_ANGLE = (seat) => ((120 - 60 * seat) * Math.PI) / 180;
const CC = {
  shape: {
    model: "chinchiro/zashiki", modelAt: [0, 0.5, 0], modelScale: 2, seatRise: 0.1 - 0.38,
    dealer: [0, 0, -1.55], dealerSeated: true, status: [0, 1.95, -1.8],
    seats: [0, 1, 2, 3, 4].map((i) => [1.55 * Math.sin(CC_ANGLE(i)), 0, 1.55 * Math.cos(CC_ANGLE(i))]),
    // 座布団の輪の真ん中（どんぶり）を向く
    seatYaw: (i) => 180 - (CC_ANGLE(i) * 180) / Math.PI,
  },
  DIE_SCALE: 0.12,
  bowl: [0, 0.1, 0],
  dice: [0, 0.1 + 0.04 + 0.06, 0],
  resultLabel: [0, 0.75, 0],
  croupier: [0, 0.101, -0.72],
  REST: [[-0.09, 0.05], [0.09, 0.06], [0, -0.09]],
  chip: (seat) => [0.7 * Math.sin(CC_ANGLE(seat)), 0.101 + seat * 0.0005, 0.7 * Math.cos(CC_ANGLE(seat))],
  hand(seat) {
    const a = seat < 0 ? Math.PI : CC_ANGLE(seat);
    return [Math.sin(a), 0.4, Math.cos(a)];
  },
  handLabel(seat) {
    const a = CC_ANGLE(seat);
    return [0.7 * Math.sin(a), 0.45, 0.7 * Math.cos(a)];
  },
};

function sbQuat(axis, rad) { return new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(...axis), rad); }
/** ChinchiroTable.faceUp と同じ。 */
function sbFaceUp(face) {
  const q = Math.PI / 2;
  return {
    1: new THREE.Quaternion(), 2: sbQuat([1, 0, 0], q), 3: sbQuat([0, 0, 1], q),
    4: sbQuat([0, 0, 1], -q), 5: sbQuat([1, 0, 0], -q), 6: sbQuat([1, 0, 0], 2 * q),
  }[face];
}

/** common の Chinchiro.hand と同じ。強さ rank（大きいほど強い）と倍率も返す。 */
function ccHand(d) {
  const s = [...d].sort((a, b) => a - b);
  if (s[0] === s[2]) return s[0] === 1 ? { label: "ピンゾロ", rank: 50, mult: 5, color: C.GOLD } : { label: s[0] + " のゾロ目", rank: 40 + s[0], mult: 3, color: C.GOLD };
  if (s.join() === "4,5,6") return { label: "シゴロ", rank: 30, mult: 2, color: C.LIGHT_PURPLE };
  if (s.join() === "1,2,3") return { label: "ヒフミ", rank: 0, mult: 2, color: C.RED, hifumi: true };
  if (s[0] === s[1]) return { label: s[2] + " の目", rank: 20 + s[2], mult: 1, color: C.WHITE };
  if (s[1] === s[2]) return { label: s[0] + " の目", rank: 20 + s[0], mult: 1, color: C.WHITE };
  return { label: "目なし", rank: 10, mult: 1, color: C.GRAY, none: true };
}
const ccVersus = (o, k) => (k.rank > o.rank ? k.mult : k.rank === o.rank ? 0 : -(k.hifumi ? 2 : o.mult));
const ccOutcome = (m) => (m > 0 ? ["勝ち +" + m + " 倍", C.GREEN] : m === 0 ? ["引き分け", C.YELLOW] : ["負け " + m + " 倍", C.RED]);

function chinchiroScenario() {
  const t = new Table(CC.shape);
  t.spawn();
  const YOU = 2;
  const names = ["Bot1", "Bot2", "あなた", "Bot4"];
  t.sit(0, "Bot1", 1000);
  t.sit(1, "Bot2", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(3, "Bot4", 1000);
  // 振る順と出目（親は目なしのあと 4 の目。子はそれぞれ勝ち・負け・シゴロ・ヒフミ）
  const SCRIPT = [
    [-1, [[1, 3, 6], [3, 4, 3]]],
    [0, [[5, 2, 2]]],
    [1, [[2, 4, 6], [1, 1, 3]]],
    [YOU, [[6, 4, 5]]],
    [3, [[2, 3, 1]]],
  ];
  const random = seeded(4242);
  const rnd = () => random.nextInt(10000) / 10000;
  const TOSS_TICKS = 10, TOSS_STEP = 2, BOUNCE_TICKS = 24, BOUNCE_STEP = 3, SETTLE = 6;
  const ROLL_TICKS = TOSS_TICKS + BOUNCE_TICKS + SETTLE + 6, PAUSE = 30, AUTO_DELAY = 20;
  const tr = (t3, q, s) => ({ t: t3, q, s: [s, s, s] });
  new ItemDisplay(at(CC.bowl), "chinchiro/bowl");
  const dieAt = (i, face, yaw) => tr([CC.REST[i][0], 0, CC.REST[i][1]], sbQuat([0, 1, 0], yaw).multiply(sbFaceUp(face)), CC.DIE_SCALE);
  const dice = [4, 5, 6].map((face, i) => {
    const d = new ItemDisplay(at(CC.dice), "chinchiro/die");
    d.tr = { from: dieAt(i, face, 0.4 * i), to: dieAt(i, face, 0.4 * i), start: 0, dur: 0 };
    return d;
  });
  const move = (e, to, ticks) => { e.interpolationDuration = ticks; e.setTransformation(to); };
  const spin = () => new THREE.Quaternion().setFromEuler(new THREE.Euler(rnd() * 6.3, rnd() * 6.3, rnd() * 6.3, "XYZ"));
  let resultLabel = null;
  const handLabels = [];
  let dealerHand = null;
  const results = {};

  // ChinchiroTable.animateRoll と同じ動き（at は振り始める tick）
  function animateRoll(start, seat, faces) {
    const h = CC.hand(seat);
    dice.forEach((d, i) => {
      const from = [h[0] + (i - 1) * 0.06, h[1], h[2]];
      const land = [(rnd() - 0.5) * 0.25, 0.08, (rnd() - 0.5) * 0.25];
      const first = tr(from, spin(), CC.DIE_SCALE);
      sim.later(start, () => move(d, first, 0));
      for (let tt = TOSS_STEP; tt <= TOSS_TICKS; tt += TOSS_STEP) {
        const u = tt / TOSS_TICKS;
        const p = [0, 1, 2].map((k) => lerp(from[k], land[k], u) + (k === 1 ? 0.35 * 4 * u * (1 - u) : 0));
        const pose = tr(p, spin(), CC.DIE_SCALE);
        sim.later(start + tt, () => move(d, pose, TOSS_STEP));
      }
    });
    for (let tt = TOSS_TICKS + BOUNCE_STEP; tt <= TOSS_TICKS + BOUNCE_TICKS; tt += BOUNCE_STEP) {
      const left = 1 - (tt - TOSS_TICKS) / BOUNCE_TICKS;
      const hop = [0, 1, 2].map(() => {
        const a = rnd() * 2 * Math.PI, r = 0.08 + rnd() * 0.12;
        return tr([Math.sin(a) * r, rnd() * 0.14 * left, Math.cos(a) * r], spin(), CC.DIE_SCALE);
      });
      sim.later(start + tt, () => dice.forEach((d, i) => move(d, hop[i], BOUNCE_STEP)));
    }
    const rest = faces.map((face, i) => dieAt(i, face, (rnd() - 0.5) * 1.2));
    sim.later(start + TOSS_TICKS + BOUNCE_TICKS + BOUNCE_STEP, () => dice.forEach((d, i) => move(d, rest[i], SETTLE)));
  }

  // ChinchiroTable の進行（親 → 子の順）を、決めた出目でなぞる。返すのは払い戻しまでの tick
  function play() {
    resultLabel = t.label(CC.resultLabel, 0.7);
    t.swing();
    t.broadcast([["親が振ります", C.GRAY]]);
    let now = 10;
    for (const [seat, rolls] of SCRIPT) {
      const who = seat < 0 ? "親" : names[seat];
      if (seat === YOU) sim.later(now, () => chat([["あなたの番です ", C.AQUA], ["[振る]", C.GOLD], [" かどんぶりをクリック", C.GRAY]]));
      rolls.forEach((faces, k) => {
        if (seat >= 0) now += AUTO_DELAY;
        animateRoll(now, seat, faces);
        now += ROLL_TICKS;
        const hand = ccHand(faces);
        sim.later(now, () => {
          resultLabel.text([[[who + " ", C.AQUA], [faces.join("・") + " ", C.GRAY], [hand.label, hand.color]]]);
          t.broadcast([[who + "（" + (k + 1) + " 回目）: ", C.GRAY], [faces.join("・") + " ", C.GRAY], [hand.label, hand.color]]);
          if (hand.rank >= 30) sparkle(at(CC.resultLabel), 0xffffff);
          if (k < rolls.length - 1) return;
          if (seat < 0) {
            dealerHand = hand;
            t.broadcast([["親は " + hand.label + "。子が順に振ります", C.YELLOW]]);
          } else {
            const m = ccVersus(dealerHand, hand);
            results[seat] = m;
            const l = t.label(CC.handLabel(seat), 0.45);
            l.text([[[hand.label + " ", hand.color], ccOutcome(m)]]);
            handLabels.push(l);
          }
        });
        if (k < rolls.length - 1) now += PAUSE / 2;
      });
      now += PAUSE;
    }
    return now;
  }

  const flow = spotFlow({
    table: t, you: YOU, bettingSeconds: 6, minBet: 10, maxBet: 200, maxTotal: 200,
    chipSpot: (spot, seat) => CC.chip(seat),
    croupier: CC.croupier, spotName: () => "子として張る",
    play,
    payout: (spot, amount, seat) => amount * (1 + (results[seat] ?? 0)),
    resultText: () => ["親 " + dealerHand.label, C.WHITE],
    clearRound() {
      if (resultLabel) resultLabel.remove();
      handLabels.forEach((l) => l.remove());
    },
  });
  sim.later(14, () => flow.place(0, "bet", 50));
  sim.later(18, () => flow.place(1, "bet", 30));
  sim.later(22, () => flow.place(3, "bet", 20));
  sim.later(30, () => flow.place(YOU, "bet", 100));
  return {
    you: YOU, table: t,
    camera(kind) {
      if (kind === "seat") {
        const p = t.shape.seats[YOU];
        camera.position.set(p[0], 1.48 + t.shape.seatRise, p[2]);
        controls.target.set(0, 0.2, 0);
      } else if (kind === "high") {
        camera.position.set(3.4, 2.6, 2.4);
        controls.target.set(0, 0.3, 0);
      } else {
        camera.position.set(0.5, 1.4, 0.7);
        controls.target.set(0, 0.15, 0);
      }
    },
  };
}

addGame("chinchiro", "チンチロ", { make: chinchiroScenario, length: 760 });

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
  const YOU = 1;
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

// HorseRaceLayout と同じ数字（コース・観客席・ゲートの扉はモデル）
const HR_SEAT_X = [-2.4, 0, 2.4];
const HR = {
  shape: {
    model: "horserace/course", modelAt: [0, 1, 0], modelScale: 3.5, seatRise: RAISED_SEAT,
    dealer: [4.4, 0, 1.0], status: [-4.3, 1.9, 0.7],
    // 席 0〜2 が下の段（右から）、3〜5 が上の段
    seats: [0, 1, 2, 3, 4, 5].map((i) => [HR_SEAT_X[2 - (i % 3)], i >= 3 ? 0.7 : 0, i >= 3 ? 1.8 : 0.9]),
  },
  START_X: -4.1, FINISH_X: 4.2, GATE_X: -3.8, SCALE: 0.6, DIRT: 0.06,
  laneZ: (h) => -1.75 + 0.3 * h,
  start: (h) => [-4.1, 0.06 + 0.3, -1.75 + 0.3 * h],
  gate: (h) => [-3.8, 0.06, -1.75 + 0.3 * h + 0.14],
  stand(seat) {
    const s = HR.shape.seats[seat];
    return [s[0], s[1] + 0.951, s[2] - 0.6];
  },
  chip(seat, h) {
    const s = HR.stand(seat);
    return [s[0] + ((h % 3) - 1) * 0.13, s[1] + seat * 0.0005, s[2] - 0.06 + Math.floor(h / 3) * 0.12];
  },
  odds: [-0.7, 0.65, -1.9],
  board: [1.05, 1.35, -1.9],
  croupier: [4.4, 0.951, 0.4],
};
const HR_COLORS = ["白", "黒", "赤", "青", "黄", "緑"];
const HR_INKS = [C.WHITE, C.DARK_GRAY, C.RED, C.BLUE, C.YELLOW, C.GREEN];
const hrName = (h) => [(h + 1) + " 番（" + HR_COLORS[h] + "）", HR_INKS[h]];
/** HorseRaceTable.progress と同じ。 */
const hrProgress = (u, pace, wave, phase) => u + pace * u * (1 - u) + wave * Math.sin(Math.PI * u) * Math.sin(2 * Math.PI * u + phase);

function horseRaceScenario() {
  const t = new Table(HR.shape);
  t.spawn();
  const YOU = 1;
  t.sit(0, "Bot1", 1000);
  t.sit(2, "Bot3", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(3, "Bot4", 1000);
  t.sit(5, "Bot6", 1000);
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
  t.label(HR.odds, 0.75).fixed().text([[["単勝オッズ", C.GOLD]], ...[0, 1, 2, 3, 4, 5].map((h) => [hrName(h), ["  " + hrPct(percent[h]), C.WHITE]])]);
  const board = t.label(HR.board, 0.7).fixed();
  board.text([[["次のレースの", C.AQUA]], [["単勝を受付中", C.AQUA]]]);
  // ゲートの扉（HorseRaceTable.gate と同じ: 蝶番を軸にコースの方へ 100 度）
  const gateTr = (open) => transformation([0, 0, 0], [0, 1, 0], open ? -100 : 0, 1);
  const gates = [0, 1, 2, 3, 4, 5].map((h) => {
    const d = new ItemDisplay(at(HR.gate(h)), "horserace/gate");
    d.tr = { from: gateTr(false), to: gateTr(false), start: 0, dur: 0 };
    return d;
  });
  const move = (e, to, ticks) => { e.interpolationDuration = ticks; e.setTransformation(to); };

  // HorseRaceTable.play と同じ計算
  function play() {
    const length = HR.FINISH_X - HR.START_X;
    const finish = [];
    let tt = 180 + random.nextInt(12);
    order.forEach((h, place) => { finish[h] = tt; tt += 2 + random.nextInt(place === 0 ? 3 : 8); });
    const last = tt;
    const pace = [], phase = [];
    for (let h = 0; h < 6; h++) { pace[h] = (rnd() - 0.5) * 0.6; phase[h] = rnd() * 2 * Math.PI; }
    // 見せ場のため、勝つ 4 番は追い込み（前半は遅め）、3 番は逃げ（前半は速め）
    pace[3] = -0.3; pace[2] = 0.3;
    t.swing();
    gates.forEach((g) => move(g, gateTr(true), 5));
    t.broadcast([["スタートしました！", C.GOLD]]);
    for (let tick = STEP; tick <= last + 10; tick += STEP) {
      const x = [], p = [];
      for (let h = 0; h < 6; h++) {
        const u = tick / finish[h];
        x[h] = u <= 1 ? length * hrProgress(u, pace[h], 0.05, phase[h]) : length + 0.4 * (1 - Math.exp(-(tick - finish[h]) / 8));
        const bob = u <= 1.1 && (tick / STEP + h) % 2 === 0 ? 0.05 : 0;
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
      sparkle(at([HR.FINISH_X, HR.DIRT + 0.6, HR.laneZ(order[0])]), 0xffffff);
    });
    sim.later(last + 6, () => board.text([[["1 着 ", C.GOLD], hrName(order[0])], [["2 着 ", C.GRAY], hrName(order[1])], [["3 着 ", C.GRAY], hrName(order[2])]]));
    return last + 8;
  }
  const flow = spotFlow({
    table: t, you: YOU, bettingSeconds: 6, minBet: 10, maxBet: 500, maxTotal: 1500,
    chipSpot: (spot, seat) => HR.chip(seat, Number(spot.slice(6)) - 1),
    croupier: HR.croupier,
    spotName: (s) => hrName(Number(s.slice(6)) - 1)[0],
    play,
    payout: (spot, amount) => (Number(spot.slice(6)) - 1 === order[0] ? Math.floor(amount * percent[order[0]] / 100) : 0),
    resultText: () => ["1 着 " + hrName(order[0])[0] + "（" + hrPct(percent[order[0]]) + "）", HR_INKS[order[0]]],
    clearRound() {
      horses.forEach((d) => move(d, pose(0, 0), 40));
      sim.later(42, () => gates.forEach((g) => move(g, gateTr(false), 5)));
    },
  });
  sim.later(14, () => flow.place(0, "horse_1", 50));
  sim.later(20, () => flow.place(2, "horse_3", 30));
  sim.later(24, () => flow.place(3, "horse_5", 20));
  sim.later(28, () => flow.place(5, "horse_3", 40));
  sim.later(36, () => flow.place(YOU, "horse_4", 50));
  return {
    you: YOU, table: t,
    camera(kind) {
      if (kind === "seat") {
        const p = t.shape.seats[YOU];
        camera.position.set(p[0], p[1] + 1.48 + RAISED_SEAT, p[2]);
        controls.target.set(0, 0.5, -1.0);
      } else if (kind === "high") {
        camera.position.set(9, 5, 4.5);
        controls.target.set(0, 0, -0.5);
      } else {
        camera.position.set(4.2, 1.2, 1.6);
        controls.target.set(2.5, 0.3, -1.0);
      }
    },
  };
}

addGame("horserace", "競馬", { make: horseRaceScenario, length: 480 });
