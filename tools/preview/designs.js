// 机以外で遊ぶ形のデザイン案（ビッグホイール・サイコロ・競馬を 3 案ずつ）。preview.js の後ろにつなげて読み込む。
// 実装前の見た目の比較用。ブロックは色の付いた箱、ホイール・サイコロ・馬・チップはリソースパックのモデル。
"use strict";

document.querySelector(".tabs").innerHTML = "";
document.querySelector("header h1").textContent = "LuckyCasino デザイン案";
document.querySelector("header p").textContent =
  "ビッグホイール・サイコロ・競馬を、机に座る形以外で遊ぶ案です。各 3 案。下のチャット欄に、置く大きさ・席・遊び方の違いを書いています。ドラッグで視点を回せます。";

// ---------------------------------------------------------------- 部品

const dMats = new Map();
/** 色の箱用の、少しざらついた 16 px のテクスチャ（ブロックっぽく）。 */
function dMat(color, opts = {}) {
  const key = color + JSON.stringify(opts);
  if (!dMats.has(key)) {
    const c = document.createElement("canvas");
    c.width = c.height = 16;
    const g = c.getContext("2d");
    const base = new THREE.Color(color);
    let seed = (color % 997) + 3;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const v = opts.flat ? 1 : 0.9 + rnd() * 0.16;
      g.fillStyle = `rgb(${Math.min(255, base.r * 255 * v)},${Math.min(255, base.g * 255 * v)},${Math.min(255, base.b * 255 * v)})`;
      g.fillRect(x, y, 1, 1);
    }
    const tex = new THREE.CanvasTexture(c);
    tex.magFilter = THREE.NearestFilter;
    tex.encoding = THREE.sRGBEncoding;
    const Mat = opts.glow ? THREE.MeshBasicMaterial : THREE.MeshLambertMaterial;
    dMats.set(key, new Mat({ map: tex, transparent: !!opts.opacity, opacity: opts.opacity ?? 1 }));
  }
  return dMats.get(key);
}

/** 静的な物の置き場（start のたびに作り直す）。 */
function dAdd(obj) {
  sim.scene.add(obj);
  return obj;
}
/** 底面の中心が (x, y, z) の箱。 */
function blk(w, h, d, color, x, y, z, opts) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), dMat(color, opts));
  m.position.set(x, y + h / 2, z);
  return dAdd(m);
}
function cyl(r, h, color, x, y, z, opts = {}) {
  const m = new THREE.Mesh(new THREE.CylinderGeometry(opts.top ?? r, r, h, opts.seg || 20, 1, !!opts.open), dMat(color, opts));
  m.position.set(x, y + h / 2, z);
  if (opts.side) m.material = m.material.clone(), m.material.side = THREE.DoubleSide;
  return dAdd(m);
}
/** リソースパックのモデル（中心が原点）。 */
function mdl(path, scale, x, y, z) {
  const outer = new THREE.Group();
  const g = itemContent(path);
  outer.add(g);
  outer.scale.setScalar(scale);
  outer.position.set(x, y, z);
  return dAdd(outer);
}
/** 毎フレーム呼ぶ動き。 */
class Anim {
  constructor(fn) { this.fn = fn; this.removed = false; sim.entities.add(this); }
  update(now) { this.fn(now); }
  remove() { this.removed = true; sim.entities.delete(this); }
}
function person(x, y, z, yaw, opts = {}) {
  const f = new Figure({ x, y, z, yaw }, { shirt: opts.shirt ?? 0x3c6fb0, pants: opts.pants ?? 0x2a2f55, sitting: !!opts.sitting, skin: opts.skin, hair: opts.hair });
  return f;
}
function dealerAt(x, z, yaw) {
  return person(x, 0, z, yaw, { shirt: 0x202024, pants: 0x18181c });
}
function sign(lines, x, y, z, scale = 0.6) {
  const t = new TextDisplay({ x, y, z }, scale);
  t.text(lines);
  return t;
}
/** 光る電球（点滅）。 */
function bulbs(points, period = 8) {
  const ms = points.map(([x, y, z], i) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(0.09, 0.09, 0.09), dMat(0xfff2a8, { glow: true }));
    m.position.set(x, y, z);
    dAdd(m);
    return m;
  });
  const on = dMat(0xfff2a8, { glow: true }), off = dMat(0x8a6a30, { glow: true });
  new Anim((now) => ms.forEach((m, i) => { m.material = (Math.floor(now / period) + i) % 2 ? on : off; }));
}
function stool(x, z, color = 0xa12722, h = 0.75) {
  blk(0.22, h - 0.1, 0.22, 0x42291a, x, 0, z);
  blk(0.5, 0.1, 0.5, color, x, h - 0.1, z);
}
function intro(title, lines) {
  chat([["【" + title + "】", C.GOLD]]);
  for (const l of lines) chat(l.map((p) => (typeof p === "string" ? [p, C.WHITE] : p)));
}
const sub = (s) => [s, C.GRAY];
/** カメラの位置: { seat: [pos, target], high: [...], top: [...] }。 */
function scene(cams, seat) {
  return {
    you: 0,
    table: { shape: { seats: [seat || [0, 0, 2]] }, youParts: [] },
    camera(kind) {
      const [p, t] = cams[kind] || cams.high;
      camera.position.set(...p);
      controls.target.set(...t);
    },
  };
}
function chipsOn(x, y, z, amount) {
  const v = new ChipView();
  v.show({ x, y, z }, amount, false, true);
  return v;
}
// 大小の目を上にする回転（ChinchiroTable.faceUp と同じ）
function dieQuat(face, yaw) {
  return new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), yaw).multiply(sbFaceUp(face));
}
function randQuat(r) {
  return new THREE.Quaternion().setFromEuler(new THREE.Euler(r() * 6.3, r() * 6.3, r() * 6.3));
}
function rng(seed) {
  let s = seed;
  return () => ((s = (s * 16807) % 2147483647) / 2147483647);
}

const WOOD = 0x6b4a2b, DARK_WOOD = 0x42291a, GOLDC = 0xd8b04a, RED_CURTAIN = 0x8a1c24, QUARTZ = 0xe8e2d6;

/** ビッグホイールを回す（中心 (x, y, z)、直径 d）。12 秒周期で回って止まる。 */
function spinningWheel(x, y, z, d, facing = 0) {
  const w = mdl("bigwheel/wheel", d, x, y, z);
  w.rotation.y = facing;
  const inner = w.children[0];
  let base = 0;
  new Anim((now) => {
    const cyc = now % 240;
    const p = Math.min(1, cyc / 150);
    const turn = Math.floor(now / 240);
    const start = turn * 517;
    inner.rotation.z = -(start + 900 * (1 - Math.pow(1 - p, 3))) * DEG + Math.PI;
  });
  return w;
}

// ================================================================ ビッグホイール

function wheelStage() {
  intro("ビッグホイール A：ステージ型", [
    ["奥に高さ 4 ブロックの舞台。直径 3 ブロックのホイールを立て、まわりに電球。手前にバースツールを 5 脚、扇形に並べます。"],
    [sub("置く大きさ 5 x 5 ブロック（高さ 4）／席 5／ホイールは今の 1.7 倍。席ごとの小さな台（ベット台）にチップが積まれ、メニューは台か椅子のクリック。")],
    [sub("いちばんの見せ場はホイールの大きさ。座ったまま見上げる形です。")],
  ]);
  blk(5, 0.3, 1.6, DARK_WOOD, 0, 0, -1.6);
  blk(5, 4, 0.2, RED_CURTAIN, 0, 0, -2.3);
  blk(5, 0.3, 0.3, GOLDC, 0, 4, -2.25);
  blk(0.25, 3.0, 0.25, DARK_WOOD, 0, 0.3, -1.95);
  spinningWheel(0, 2.2, -1.8, 3);
  blk(0.25, 0.35, 0.15, GOLDC, 0, 3.65, -1.7);
  const pts = [];
  for (let i = 0; i < 28; i++) {
    const a = (i / 28) * Math.PI * 2;
    pts.push([Math.sin(a) * 1.68, 2.2 + Math.cos(a) * 1.68, -1.86]);
  }
  bulbs(pts);
  sign([[["BIG WHEEL", C.GOLD]]], 0, 4.4, -2.1, 1.1);
  dealerAt(1.9, -1.4, 160);
  for (let i = 0; i < 5; i++) {
    const a = (-0.5 + i / 4) * 1.1;
    const x = Math.sin(a) * 3.2, z = 0.9 + (1 - Math.cos(a)) * 0.6 + 0.5;
    stool(x, z, 0xa12722, 0.95);
    const px = Math.sin(a) * 2.6, pz = z - 0.65;
    blk(0.5, 0.95, 0.4, DARK_WOOD, px, 0, pz);
    blk(0.56, 0.06, 0.46, 0x1f6b45, px, 0.95, pz);
    if (i !== 2) person(x, 0.4, z, 180 - a / DEG, { sitting: true, shirt: [0xc0504d, 0x4f81bd, 0, 0x9bbb59, 0x8064a2][i] });
    chipsOn(px, 1.02, pz, [50, 100, 20, 500, 10][i]);
  }
  return scene({
    seat: [[0, 1.95, 1.6], [0, 2.1, -1.8]],
    high: [[5.2, 3.4, 2.2], [0, 1.6, -0.9]],
    top: [[0, 8, 0.6], [0, 0, -0.4]],
  }, [0, 0, 1.4]);
}

function wheelShow() {
  intro("ビッグホイール B：ゲームショー型", [
    ["高さ 1 ブロックのステージに、金の枠と看板付きのホイール。プレイヤーは座らず、色ごとの「回答台」の後ろに立って遊びます。"],
    [sub("置く大きさ 7 x 5 ブロック（高さ 5）／席 5（立ち）／回答台を右クリックでメニュー。ディーラーが司会のようにホイールを回します。")],
    [sub("テレビのクイズ番組の雰囲気。立ったまま歩き回れるので、まわりで見ている人も参加しやすいです。")],
  ]);
  blk(7, 1, 2.2, 0x2c2c3a, 0, 0, -1.6);
  blk(7, 0.08, 2.2, 0x3a3a52, 0, 1, -1.6);
  blk(3.2, 3.6, 0.3, GOLDC, 0, 1, -2.2);
  blk(2.9, 3.3, 0.32, 0x2a1220, 0, 1.15, -2.2);
  spinningWheel(0, 2.75, -2.0, 2.6);
  blk(0.25, 0.35, 0.15, GOLDC, 0, 4.15, -1.9);
  blk(4.2, 0.8, 0.25, 0x1a1a2a, 0, 4.6, -2.2);
  sign([[["BIG WHEEL SHOW", C.GOLD]]], 0, 4.75, -2.0, 1.0);
  const pts = [];
  for (let i = 0; i < 17; i++) pts.push([-2.05 + i * 0.256, 5.35, -2.05], [-2.05 + i * 0.256, 4.65, -2.05]);
  bulbs(pts, 5);
  person(-2.3, 1.08, -1.3, 150, { shirt: 0xaa2233, pants: 0x18181c });
  const colors = [0xe6c440, 0x4a7ad8, 0x9b4ad0, 0x3aa65a, 0xe07a30];
  for (let i = 0; i < 5; i++) {
    const x = -2.6 + i * 1.3, z = 1.0;
    blk(0.8, 1.05, 0.6, colors[i], x, 0, z);
    blk(0.86, 0.08, 0.66, 0xf0f0f0, x, 1.05, z);
    blk(0.3, 0.06, 0.3, 0xff3030, x, 1.13, z, { glow: true });
    if (i !== 2) person(x, 0, z + 0.65, 180, { shirt: [0xc0504d, 0x4f81bd, 0, 0x9bbb59, 0x8064a2][i] });
    chipsOn(x + 0.22, 1.13, z + 0.12, [50, 100, 20, 500, 10][i]);
  }
  return scene({
    seat: [[0, 1.62, 1.7], [0, 2.4, -2]],
    high: [[6.0, 3.8, 2.6], [0, 1.8, -0.8]],
    top: [[0, 9, 0.5], [0, 0, -0.5]],
  });
}

function wheelTheater() {
  intro("ビッグホイール C：壁かけ＋床のベットマット", [
    ["ホイールは白い壁に掛け、床に大きなベットマット（7 色のマス）を敷きます。チップはマットのマスに置かれ、後ろの 2 段の観覧ベンチから見ます。"],
    [sub("置く大きさ 7 x 6 ブロック（壁の高さ 4）／席 6（ベンチ 2 段 x 3）／マスを右クリックでも、ベンチからメニューでも賭けられる。")],
    [sub("「マスにチップを置く」感覚が強く、ホイールはやや遠め。広い場所向け。")],
  ]);
  blk(7, 4, 0.3, QUARTZ, 0, 0, -2.6);
  blk(7, 0.3, 0.3, GOLDC, 0, 4, -2.55);
  spinningWheel(0, 2.3, -2.35, 2.2);
  blk(0.25, 0.35, 0.15, GOLDC, 0, 3.55, -2.25);
  dealerAt(-1.8, -2.0, 180);
  const colors = [0xe6c440, 0x4a7ad8, 0x9b4ad0, 0x3aa65a, 0xe07a30, 0x222228, 0xc41c3a];
  blk(6.4, 0.05, 1.6, 0x1f5a3a, 0, 0, -0.9);
  for (let i = 0; i < 7; i++) {
    const x = -2.7 + i * 0.9;
    blk(0.8, 0.07, 1.3, colors[i], x, 0, -0.9);
    if (i % 2 === 0) chipsOn(x, 0.07, -0.9, [50, 100, 20, 500][i / 2]);
  }
  sign([[["1", C.YELLOW], ["  2", "#5555FF"], ["  5", "#FF55FF"], ["  10", C.GREEN], ["  20", C.GOLD], ["  J", C.DARK_GRAY], ["  L", C.RED]]], 0, 0.5, -0.1, 0.7);
  for (let row = 0; row < 2; row++) {
    const z = 1.2 + row * 1.0, y = row * 0.5;
    blk(5, 0.5 + y, 0.9, 0x8a6a48, 0, 0, z);
    for (let i = 0; i < 3; i++) {
      const x = -1.6 + i * 1.6;
      if (!(row === 0 && i === 1)) person(x, 0.5 + y - 0.55, z, 180, { sitting: true, shirt: [0xc0504d, 0x4f81bd, 0x9bbb59, 0x8064a2, 0x4bacc6, 0xf79646][row * 3 + i] });
    }
  }
  return scene({
    seat: [[0, 2.0, 1.25], [0, 1.4, -2]],
    high: [[6.0, 4.0, 2.4], [0, 1.2, -0.6]],
    top: [[0, 9, 0.4], [0, 0, -0.4]],
  });
}

// ================================================================ サイコロ

/** 器の中のサイコロ 3 個を振る（中心 (x, y, z)）。周期 120 tick、振って止まって出目を見せる。 */
function rollingDice(cx, cy, cz, spread, scale, seed, hop = 0.25) {
  const r = rng(seed);
  const dice = [0, 1, 2].map(() => mdl("chinchiro/die", scale, cx, cy, cz));
  const rounds = [[2, 5, 5], [1, 1, 1], [3, 4, 6], [6, 2, 4]];
  const rest = [[-spread * 0.6, spread * 0.3], [spread * 0.6, spread * 0.4], [0, -spread * 0.6]];
  new Anim((now) => {
    const cyc = now % 120, k = Math.floor(now / 120);
    const faces = rounds[k % rounds.length];
    dice.forEach((d, i) => {
      if (cyc < 40) {
        const t = Math.floor(now / 3) * 3 + i * 7;
        const rr = rng(t + seed);
        d.position.set(cx + (rr() - 0.5) * spread * 1.4, cy + rr() * hop, cz + (rr() - 0.5) * spread * 1.4);
        d.children[0].quaternion.copy(randQuat(rr)).multiply(new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI));
      } else {
        d.position.set(cx + rest[i][0], cy, cz + rest[i][1]);
        d.children[0].quaternion.copy(dieQuat(faces[i], i * 0.5 + k)).multiply(new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI));
      }
    });
  });
  return dice;
}

function diceZashiki() {
  intro("サイコロ A：座敷のチンチロ", [
    ["4 x 4 の畳の上に、どんぶりを 1 つ。まわりに座布団を 5 枚、輪に並べ、床に座って遊びます。親（ディーラー）がどんぶりにサイコロを投げ込みます。"],
    [sub("置く大きさ 4 x 4 ブロック（高さ 1）／席 5（座布団）／チップは座布団の前に積む。賭け方は今の大小のままでも、本来のチンチロ（親と子で役の強さを比べる）にもできます。")],
    [sub("いちばん「和」の雰囲気。和風の建物や温泉宿のような場所に合います。")],
  ]);
  blk(4.2, 0.12, 4.2, 0x5a3c22, 0, 0, 0);
  for (let i = 0; i < 2; i++) for (let j = 0; j < 4; j++) {
    blk(1.95, 0.08, 0.95, 0xc8c47a, -1 + i * 2, 0.12, -1.5 + j * 1.0);
    blk(1.95, 0.085, 0.06, 0x2e4a2a, -1 + i * 2, 0.12, -1.5 + j * 1.0 + 0.45);
  }
  cyl(0.38, 0.32, 0xf2efe6, 0, 0.2, 0, { top: 0.48, side: true, open: true });
  cyl(0.36, 0.02, 0xf2efe6, 0, 0.2, 0);
  cyl(0.49, 0.04, 0x2a4ea0, 0, 0.5, 0, { open: true, side: true });
  rollingDice(0, 0.29, 0, 0.16, 0.1, 11, 0.32);
  const n = 6;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    const x = Math.sin(a) * 1.35, z = Math.cos(a) * 1.35;
    blk(0.6, 0.1, 0.6, i === 3 ? 0x9a1c2a : 0x5a2a7a, x, 0.2, z);
    const yaw = (a / DEG + 180) % 360;
    if (i === 3) person(x, -0.25, z, yaw, { shirt: 0x2a2a30, pants: 0x1c1c22, sitting: true });
    else if (i !== 0) person(x, -0.25, z, yaw, { sitting: true, shirt: [0, 0xc0504d, 0x4f81bd, 0, 0x9bbb59, 0x8064a2][i] });
    if (i !== 3) chipsOn(Math.sin(a) * 0.85, 0.2, Math.cos(a) * 0.85, [100, 50, 20, 0, 500, 10][i]);
  }
  sign([[["親", C.GOLD]]], 0, 1.5, -1.35, 0.6);
  return scene({
    seat: [[0, 1.05, 1.3], [0, 0.3, 0]],
    high: [[3.4, 2.6, 1.6], [0, 0.3, 0]],
    top: [[0, 4.5, 0.3], [0, 0, 0]],
  });
}

function diceMachine() {
  intro("サイコロ B：電子シックボー台", [
    ["真ん中に光る台座とガラスのドーム。中でサイコロが跳ねます。まわりに 5 台のプレイヤー端末（画面付きの小さな台）を半円に並べ、それぞれの椅子に座ります。"],
    [sub("置く大きさ 5 x 4 ブロック（高さ 2）／席 5／端末をクリックで今のメニュー。ディーラーなし（機械が振る）。")],
    [sub("本物のカジノにある電子シックボーの形。夜のカジノ風で、ドームと光の輪が目立ちます。")],
  ]);
  cyl(0.75, 0.9, 0x24242e, 0, 0, -0.9, { seg: 24 });
  cyl(0.8, 0.08, GOLDC, 0, 0.9, -0.9, { seg: 24 });
  const ring = [];
  for (let i = 0; i < 20; i++) {
    const a = (i / 20) * Math.PI * 2;
    ring.push([Math.sin(a) * 0.78, 0.5, -0.9 + Math.cos(a) * 0.78]);
  }
  bulbs(ring, 4);
  const dome = new THREE.Mesh(new THREE.SphereGeometry(0.45, 24, 12, 0, Math.PI * 2, 0, Math.PI / 2), new THREE.MeshLambertMaterial({ color: 0xcfe8ff, transparent: true, opacity: 0.25 }));
  dome.position.set(0, 0.98, -0.9);
  dAdd(dome);
  rollingDice(0, 1.04, -0.9, 0.14, 0.11, 21, 0.25);
  blk(1.6, 0.6, 0.12, 0x101018, 0, 1.6, -1.95);
  sign([[["4・5・6", C.WHITE]], [["合計 15 大", C.RED]]], 0, 1.68, -1.85, 0.55);
  blk(0.12, 1.6, 0.12, 0x24242e, 0, 0, -1.95);
  for (let i = 0; i < 5; i++) {
    const a = (-0.5 + i / 4) * 2.2;
    const x = Math.sin(a) * 1.75, z = -0.9 + Math.cos(a) * 1.75;
    const yaw = a / DEG + 180;
    const g = new THREE.Group();
    g.position.set(x, 0, z);
    g.rotation.y = -yaw * DEG;
    const base = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.85, 0.4), dMat(0x30303c));
    base.position.set(0, 0.425, 0);
    g.add(base);
    const scr = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.05, 0.34), dMat(0x2a7a5a, { glow: true }));
    scr.position.set(0, 0.88, 0);
    scr.rotation.x = -0.35;
    g.add(scr);
    dAdd(g);
    const sx = Math.sin(a) * 2.35, sz = -0.9 + Math.cos(a) * 2.35;
    stool(sx, sz, 0x2a2a6a, 0.8);
    if (i !== 2) person(sx, 0.25, sz, yaw, { sitting: true, shirt: [0xc0504d, 0x4f81bd, 0, 0x9bbb59, 0x8064a2][i] });
  }
  return scene({
    seat: [[0, 1.75, 1.45], [0, 1.0, -0.9]],
    high: [[3.8, 2.8, 1.4], [0, 0.8, -0.8]],
    top: [[0, 6, -0.6], [0, 0, -0.9]],
  });
}

function diceTower() {
  intro("サイコロ C：ダイスタワー＋カウンター", [
    ["バーのカウンターの奥に、木のダイスタワー（高さ 2.5 ブロック）。ディーラーが上からサイコロを落とすと、中の段で跳ねて手前の受け皿に転がり出ます。"],
    [sub("置く大きさ 5 x 3 ブロック（高さ 3）／席 5（カウンター席）／チップはカウンターのマットに置く。")],
    [sub("落ちる動きが見どころ。酒場・バー風の場所に合い、壁際に置けます。")],
  ]);
  blk(5, 1.0, 0.8, DARK_WOOD, 0, 0, -0.6);
  blk(5.1, 0.08, 0.9, WOOD, 0, 1.0, -0.6);
  blk(4.4, 0.01, 0.5, 0x1f6b45, 0, 1.08, -0.5);
  blk(5, 2.6, 0.2, 0x5a4030, 0, 0, -2.2);
  // タワー（正面が開いた箱と中の段）
  const tx = 0.9, tz = -1.6;
  blk(0.6, 2.5, 0.08, WOOD, tx, 0, tz - 0.3);
  blk(0.08, 2.5, 0.6, WOOD, tx - 0.3, 0, tz);
  blk(0.08, 2.5, 0.6, WOOD, tx + 0.3, 0, tz);
  blk(0.68, 0.1, 0.68, DARK_WOOD, tx, 2.5, tz);
  for (let i = 0; i < 3; i++) {
    const m = blk(0.5, 0.04, 0.4, 0x8a6a48, tx + (i % 2 ? 0.05 : -0.05), 0.7 + i * 0.55, tz);
    m.rotation.z = i % 2 ? 0.35 : -0.35;
  }
  blk(1.1, 0.06, 0.8, 0x8a6a48, tx, 1.08, tz + 0.65);
  blk(1.1, 0.12, 0.05, DARK_WOOD, tx, 1.08, tz + 1.05);
  // 落ちるサイコロ
  const dice = [0, 1, 2].map(() => mdl("chinchiro/die", 0.11, tx, 2.4, tz));
  const faces = [[2, 5, 5], [3, 4, 6], [1, 1, 1]];
  new Anim((now) => {
    const cyc = now % 120, k = Math.floor(now / 120);
    dice.forEach((d, i) => {
      const t = Math.max(0, cyc - i * 3);
      const rr = rng(Math.floor(now / 2) + i * 13);
      if (t < 30) {
        const y = 2.4 - (t / 30) * 1.25;
        const zig = Math.sin(t * 0.7 + i) * 0.12;
        d.position.set(tx + zig, y, tz);
        d.children[0].quaternion.copy(randQuat(rr));
      } else if (t < 44) {
        const u = (t - 30) / 14;
        d.position.set(tx + (i - 1) * 0.22 * u, 1.17 + Math.abs(Math.sin(u * Math.PI * 2)) * 0.08 * (1 - u), tz + 0.25 + u * 0.4);
        d.children[0].quaternion.copy(randQuat(rr));
      } else {
        d.position.set(tx + (i - 1) * 0.22, 1.17, tz + 0.65 + (i === 1 ? -0.08 : 0.04));
        d.children[0].quaternion.copy(dieQuat(faces[k % 3][i], i * 0.4)).multiply(new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI));
      }
    });
  });
  dealerAt(-0.3, -1.5, 180);
  for (let i = 0; i < 5; i++) {
    const x = -1.8 + i * 0.9;
    stool(x, 0.3, 0xa12722, 0.85);
    if (i !== 2) person(x, 0.3, 0.3, 180, { sitting: true, shirt: [0xc0504d, 0x4f81bd, 0, 0x9bbb59, 0x8064a2][i] });
    if (i !== 2) chipsOn(x, 1.09, -0.4, [50, 100, 0, 500, 10][i]);
  }
  return scene({
    seat: [[0, 1.8, 0.35], [0.5, 1.3, -1.4]],
    high: [[4.0, 2.8, 1.6], [0.3, 1.2, -1.0]],
    top: [[0, 6, 0], [0, 0, -0.8]],
  });
}

// ================================================================ 競馬

/** 馬の向き: モデルは +X（ItemDisplay と同じく 180 度回して描く）へ走る。heading は XZ 平面の進む向き。 */
function placeHorse(h, x, y, z, dx, dz) {
  h.position.set(x, y, z);
  h.rotation.y = Math.atan2(-dz, dx);
}

function horseOval() {
  intro("競馬 A：楕円コースのジオラマ", [
    ["床に 7 x 4 ブロックの楕円のダートコース（柵と芝の内側）。6 頭がコースを 1 周します。手前に 2 段のスタンド席、内側にオッズの掲示板。"],
    [sub("置く大きさ 7 x 6 ブロック（高さ 2）／席 6（スタンド 2 段）／馬は今の約 1.5 倍。スタンドの手すりの賭け札（6 色）をクリックで賭ける。")],
    [sub("「競馬場をミニチュアで見下ろす」形。周回するので、途中の順位の入れ替わりがよく見えます。")],
  ]);
  const A = 3.1, B = 1.6, cz = -1.0;
  // コース（円盤を重ねて楕円の輪）
  const ring = (a, b, color, y) => {
    const m = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 0.06, 48), dMat(color));
    m.scale.set(a, 1, b);
    m.position.set(0, y, cz);
    dAdd(m);
  };
  ring(A + 0.55, B + 0.55, 0xe8e8e0, 0.0);
  ring(A + 0.5, B + 0.5, 0x9a6e44, 0.02);
  ring(A - 0.45, B - 0.45, 0xe8e8e0, 0.04);
  ring(A - 0.5, B - 0.5, 0x4f8a3a, 0.06);
  blk(0.06, 0.6, 0.06, 0xe8e8e0, 0, 0, cz + B + 0.55);
  blk(0.04, 0.04, 1.0, 0xffffff, 0, 0.6, cz + B + 0.05, { glow: true });
  // 掲示板
  blk(1.6, 0.9, 0.1, 0x101018, 0, 0.5, cz - 0.3);
  blk(0.1, 0.5, 0.1, 0x30303a, 0, 0, cz - 0.3);
  sign([[["1 白 3.2", C.WHITE], [" 2 黒 11.2", C.GRAY]], [["3 赤 4.0", C.RED], [" 4 青 7.5", "#5555FF"]], [["5 黄 4.5", C.YELLOW], [" 6 緑 9.0", C.GREEN]]], 0, 0.75, cz - 0.12, 0.4);
  const horses = [0, 1, 2, 3, 4, 5].map((h) => mdl("horserace/horse_" + (h + 1), 0.5, 0, 0.3, 0));
  const speed = [1.0, 0.93, 0.99, 1.03, 0.97, 0.95];
  new Anim((now) => {
    const cyc = now % 300;
    horses.forEach((h, i) => {
      const lane = (i - 2.5) * 0.12;
      const p = Math.min(1, (cyc / 220) * speed[i] + Math.sin(cyc * 0.05 + i) * 0.01) ;
      const th = Math.PI / 2 + p * Math.PI * 2;
      const a = A + lane, b = B + lane;
      const x = a * Math.cos(th), z = cz + b * Math.sin(th);
      const bob = cyc < 220 && Math.floor(now / 2 + i) % 2 ? 0.03 : 0;
      placeHorse(h, x, 0.29 + bob, z, -a * Math.sin(th), b * Math.cos(th));
    });
  });
  for (let row = 0; row < 2; row++) {
    const z = 1.75 + row * 0.9, y = row * 0.5;
    blk(6, 0.45 + y, 0.85, 0x8a8a90, 0, 0, z);
    blk(6, 0.05, 0.05, 0xe8e8e0, 0, 0.9 + y, z - 0.42);
    for (let i = 0; i < 3; i++) {
      const x = -1.8 + i * 1.8;
      if (!(row === 0 && i === 1)) person(x, 0.45 + y - 0.55, z, 180, { sitting: true, shirt: [0xc0504d, 0x4f81bd, 0x9bbb59, 0x8064a2, 0x4bacc6, 0xf79646][row * 3 + i] });
    }
  }
  const silk = [0xf0f0ec, 0x242428, 0xd22828, 0x285ad2, 0xf0d228, 0x28a046];
  for (let i = 0; i < 6; i++) blk(0.22, 0.22, 0.04, silk[i], -1.25 + i * 0.5, 0.95, 1.31);
  return scene({
    seat: [[0, 1.9, 1.75], [0, 0.3, -1.0]],
    high: [[5.8, 3.6, 3.6], [0, 0.2, -0.8]],
    top: [[0, 8.5, 0.2], [0, 0, -0.6]],
  });
}

function horseCabinet() {
  intro("競馬 B：メダルゲーム筐体", [
    ["ゲームセンターの競馬ゲームのような大きな筐体。ガラスの天板の下に楕円コースがあり、まわりの 6 席（両側に 3 席ずつ）に画面付きの操作台。奥に大きな掲示板。"],
    [sub("置く大きさ 6 x 4 ブロック（高さ 3）／席 6／操作台クリックでメニュー。馬は今と同じくらいの大きさ。")],
    [sub("機械らしさとネオンが強い案。屋内のゲームコーナー向けで、席が多めに取れます。")],
  ]);
  blk(4.6, 0.9, 2.2, 0x2a2440, 0, 0, -0.6);
  blk(4.8, 0.1, 2.4, 0xc040c0, 0, 0.9, -0.6, { glow: true });
  const glass = new THREE.Mesh(new THREE.BoxGeometry(4.4, 0.35, 2.0), new THREE.MeshLambertMaterial({ color: 0xcfe8ff, transparent: true, opacity: 0.18 }));
  glass.position.set(0, 1.18, -0.6);
  dAdd(glass);
  const oval = (a, b, color, y) => {
    const m = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 0.03, 40), dMat(color));
    m.scale.set(a, 1, b);
    m.position.set(0, y, -0.6);
    dAdd(m);
  };
  oval(2.0, 0.85, 0x9a6e44, 1.0);
  oval(1.45, 0.4, 0x4f8a3a, 1.02);
  blk(2.6, 1.3, 0.15, 0x101018, 0, 1.0, -2.2);
  blk(2.8, 0.08, 0.2, 0xc040c0, 0, 2.3, -2.2, { glow: true });
  sign([[["DERBY", C.GOLD]], [["1 着 4 番（青）", "#5555FF"]]], 0, 1.4, -1.95, 0.7);
  const horses = [0, 1, 2, 3, 4, 5].map((h) => mdl("horserace/horse_" + (h + 1), 0.32, 0, 1.1, 0));
  const speed = [1.0, 0.94, 0.99, 1.03, 0.97, 0.95];
  new Anim((now) => {
    const cyc = now % 260;
    horses.forEach((h, i) => {
      const lane = (i - 2.5) * 0.07;
      const p = Math.min(1, (cyc / 200) * speed[i]);
      const th = Math.PI / 2 + p * Math.PI * 2;
      const a = 1.72 + lane, b = 0.62 + lane;
      const bob = cyc < 200 && Math.floor(now / 2 + i) % 2 ? 0.02 : 0;
      placeHorse(h, a * Math.cos(th), 1.1 + bob, -0.6 + b * Math.sin(th), -a * Math.sin(th), b * Math.cos(th));
    });
  });
  for (let side = 0; side < 2; side++) {
    for (let i = 0; i < 3; i++) {
      const x = -1.5 + i * 1.5, z = side ? -2.0 : 0.85;
      if (side) continue;
      blk(0.7, 0.95, 0.35, 0x30303c, x, 0, z);
      blk(0.6, 0.04, 0.28, 0x2a7a5a, x, 0.95, z, { glow: true });
      stool(x, z + 0.65, 0x2a2a6a, 0.8);
      if (i !== 1) person(x, 0.25, z + 0.65, 180, { sitting: true, shirt: [0xc0504d, 0x4f81bd, 0x9bbb59][i] });
    }
  }
  for (const zs of [-1, 1]) for (let i = 0; i < 2; i++) {
    const x = zs * 2.75, z = -1.1 + i * 1.0;
    blk(0.35, 0.95, 0.7, 0x30303c, x, 0, z);
    stool(x + zs * 0.65, z, 0x2a2a6a, 0.8);
    person(x + zs * 0.65, 0.25, z, zs < 0 ? 90 : 270, { sitting: true, shirt: [0x8064a2, 0x4bacc6, 0xf79646, 0x9bbb59][i + (zs + 1)] });
  }
  return scene({
    seat: [[0, 1.75, 1.5], [0, 1.0, -0.8]],
    high: [[4.6, 3.2, 2.6], [0, 1.0, -0.6]],
    top: [[0, 7, -0.3], [0, 0, -0.6]],
  });
}

function horseStraight() {
  intro("競馬 C：直線ダートコース＋観客席", [
    ["長さ 9 ブロックの直線コース（6 レーン）を床に作り、左にスタートゲート、右にゴール板。コースに沿って 2 段の観客席を並べます。"],
    [sub("置く大きさ 10 x 5 ブロック（高さ 2）／席 6（観客席）／馬は今の約 2 倍で、ゲートが開く動きも付けられる。")],
    [sub("いちばん迫力が出る案。広い場所が必要ですが、ゴール前の競り合いを横から見られます。")],
  ]);
  const L = 9, cz = -1.0;
  blk(L + 0.6, 0.06, 1.9, 0x9a6e44, 0, 0, cz);
  blk(L + 0.6, 0.35, 0.06, 0xe8e8e0, 0, 0, cz + 0.98);
  blk(L + 0.6, 0.35, 0.06, 0xe8e8e0, 0, 0, cz - 0.98);
  // スタートゲート
  const gx = -L / 2 + 0.2;
  blk(0.5, 0.9, 2.0, 0x8a8a90, gx - 0.25, 0, cz);
  blk(0.6, 0.1, 2.0, 0x2a7a3a, gx, 0.9, cz);
  const gates = [];
  for (let i = 0; i < 6; i++) {
    const z = cz - 0.75 + i * 0.3;
    const g = blk(0.04, 0.5, 0.26, 0x2a7a3a, gx + 0.05, 0.1, z);
    gates.push(g);
  }
  // ゴール板
  const fx = L / 2 - 0.4;
  blk(0.08, 1.4, 0.08, 0xe8e8e0, fx, 0, cz + 1.05);
  blk(0.5, 0.4, 0.05, 0xd22828, fx, 1.2, cz + 1.05);
  sign([[["GOAL", C.WHITE]]], fx, 1.7, cz + 1.05, 0.5);
  const horses = [0, 1, 2, 3, 4, 5].map((h) => mdl("horserace/horse_" + (h + 1), 0.6, 0, 0.36, 0));
  const finish = [178, 196, 170, 166, 184, 190];
  new Anim((now) => {
    const cyc = now % 260;
    gates.forEach((g) => { g.rotation.y = cyc > 10 && cyc < 230 ? Math.PI / 2.2 : 0; });
    horses.forEach((h, i) => {
      const z = cz - 0.75 + i * 0.3;
      const t = Math.max(0, cyc - 10);
      const u = Math.min(1.15, t / finish[i]);
      const len = fx - (gx + 0.35);
      const x = gx + 0.35 + len * hrProgress(Math.min(u, 1), [0.2, -0.1, 0.3, -0.3, 0, 0.1][i], 0.05, i) + (u > 1 ? (u - 1) * 2 : 0);
      const bob = t > 0 && u < 1.1 && Math.floor(now / 2 + i) % 2 ? 0.04 : 0;
      placeHorse(h, x, 0.36 + bob, z, 1, 0);
    });
  });
  for (let row = 0; row < 2; row++) {
    const z = 0.9 + row * 0.9, y = row * 0.5;
    blk(8, 0.45 + y, 0.85, 0x8a8a90, 0, 0, z);
    for (let i = 0; i < 3; i++) {
      const x = -2.4 + i * 2.4;
      if (!(row === 0 && i === 1)) person(x, 0.45 + y - 0.55, z, 180, { sitting: true, shirt: [0xc0504d, 0x4f81bd, 0x9bbb59, 0x8064a2, 0x4bacc6, 0xf79646][row * 3 + i] });
    }
  }
  return scene({
    seat: [[0, 1.9, 0.9], [1.0, 0.4, -1.0]],
    high: [[6.5, 3.4, 4.2], [0.5, 0.2, -0.8]],
    top: [[0, 10, 0.2], [0, 0, -0.5]],
  });
}

const DESIGNS = [
  ["wheel-a", "ホイール A ステージ", wheelStage],
  ["wheel-b", "ホイール B ゲームショー", wheelShow],
  ["wheel-c", "ホイール C 壁かけ＋床マット", wheelTheater],
  ["dice-a", "サイコロ A 座敷チンチロ", diceZashiki],
  ["dice-b", "サイコロ B 電子シックボー台", diceMachine],
  ["dice-c", "サイコロ C ダイスタワー", diceTower],
  ["horse-a", "競馬 A 楕円ジオラマ", horseOval],
  ["horse-b", "競馬 B メダルゲーム筐体", horseCabinet],
  ["horse-c", "競馬 C 直線コース", horseStraight],
];
for (const [id, label, make] of DESIGNS) addGame(id, label, { make, length: 1200 });
game = "wheel-a";
