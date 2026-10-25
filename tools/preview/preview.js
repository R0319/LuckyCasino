// LuckyCasino のアニメーションをブラウザで再生する。
// 位置・動きの長さ・順番は paper/ の Java（*Layout, CardView, ChipView, *Table）をそのまま移したもの。
// 1 tick = 50 ms。ItemDisplay はモデルを Y 軸まわりに 180 度回して描く（実機と同じ）。
"use strict";

const DEG = Math.PI / 180;
const TOP = 0.95;
const MODEL_SCALE = 4 / 3;
const C = {
  GOLD: "#FFAA00", AQUA: "#55FFFF", YELLOW: "#FFFF55", GRAY: "#AAAAAA", DARK_GRAY: "#555555",
  GREEN: "#55FF55", RED: "#FF5555", WHITE: "#FFFFFF", DARK_RED: "#AA0000", BLACK: "#000000",
};

// ---------------------------------------------------------------- 描画の土台

const stage = document.getElementById("stage");
const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.outputEncoding = THREE.sRGBEncoding;
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
stage.prepend(renderer.domElement);
const camera = new THREE.PerspectiveCamera(70, 16 / 10, 0.05, 200);
const controls = new THREE.OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.dampingFactor = 0.12;

function resize() {
  const w = stage.clientWidth, h = stage.clientHeight;
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
}
new ResizeObserver(resize).observe(stage);

const textureCache = new Map();
function texture(name) {
  if (!textureCache.has(name)) {
    const t = new THREE.TextureLoader().load(ASSETS.textures[name]);
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestFilter;
    t.encoding = THREE.sRGBEncoding;
    textureCache.set(name, t);
  }
  return textureCache.get(name);
}
const materialCache = new Map();
function material(name) {
  if (!materialCache.has(name)) {
    // 光るブロック（ランプ・当たりの帯。実機は明るさ 15 の Display）は陰影なしで
    const glow = /glowstone|shroomlight/.test(name);
    const Mat = glow ? THREE.MeshBasicMaterial : THREE.MeshLambertMaterial;
    materialCache.set(name, new Mat({ map: texture(name), transparent: true, alphaTest: 0.1 }));
  }
  return materialCache.get(name);
}

// Minecraft のモデルの面の頂点（FaceInfo）。uv は (u0,v0) (u0,v1) (u1,v1) (u1,v0) の順に付く
const FACE = {
  down: { n: [0, -1, 0], v: [["x0", "y0", "z1"], ["x0", "y0", "z0"], ["x1", "y0", "z0"], ["x1", "y0", "z1"]] },
  up: { n: [0, 1, 0], v: [["x0", "y1", "z0"], ["x0", "y1", "z1"], ["x1", "y1", "z1"], ["x1", "y1", "z0"]] },
  north: { n: [0, 0, -1], v: [["x1", "y1", "z0"], ["x1", "y0", "z0"], ["x0", "y0", "z0"], ["x0", "y1", "z0"]] },
  south: { n: [0, 0, 1], v: [["x0", "y1", "z1"], ["x0", "y0", "z1"], ["x1", "y0", "z1"], ["x1", "y1", "z1"]] },
  west: { n: [-1, 0, 0], v: [["x0", "y1", "z0"], ["x0", "y0", "z0"], ["x0", "y0", "z1"], ["x0", "y1", "z1"]] },
  east: { n: [1, 0, 0], v: [["x1", "y1", "z1"], ["x1", "y0", "z1"], ["x1", "y0", "z0"], ["x1", "y1", "z0"]] },
};

const geometryCache = new Map();
/** モデルの面をテクスチャごとの BufferGeometry にまとめる（座標は 0〜16 を 0〜1 に）。 */
function modelGeometries(path) {
  if (geometryCache.has(path)) return geometryCache.get(path);
  const elements = ASSETS.models[path];
  if (!elements) throw new Error("model not found: " + path);
  const byTexture = new Map();
  for (const el of elements) {
    const b = { x0: el.from[0] / 16, y0: el.from[1] / 16, z0: el.from[2] / 16, x1: el.to[0] / 16, y1: el.to[1] / 16, z1: el.to[2] / 16 };
    // 要素の回転（Minecraft の FaceBakery と同じく、軸の正の向きに右手系で angle 度）
    let rotate = (p) => p;
    let rotateN = (n) => n;
    if (el.rotation) {
      const axis = { x: [1, 0, 0], y: [0, 1, 0], z: [0, 0, 1] }[el.rotation.axis];
      const q = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(...axis), el.rotation.angle * DEG);
      const o = new THREE.Vector3(...el.rotation.origin.map((n) => n / 16));
      rotate = (p) => new THREE.Vector3(...p).sub(o).applyQuaternion(q).add(o).toArray();
      rotateN = (n) => new THREE.Vector3(...n).applyQuaternion(q).toArray();
    }
    for (const [face, def] of Object.entries(el.faces)) {
      const info = { n: rotateN(FACE[face].n), v: FACE[face].v };
      const [u0, v0, u1, v1] = def.uv.map((n) => n / 16);
      const uvs = [[u0, v0], [u0, v1], [u1, v1], [u1, v0]];
      const pts = info.v.map((p) => rotate([b[p[0]], b[p[1]], b[p[2]]]));
      if (!byTexture.has(def.texture)) byTexture.set(def.texture, { pos: [], uv: [], normal: [] });
      const g = byTexture.get(def.texture);
      // 外向きになるよう三角形の向きをそろえる
      const a = new THREE.Vector3(...pts[0]), bb = new THREE.Vector3(...pts[1]), c = new THREE.Vector3(...pts[2]);
      const cross = bb.clone().sub(a).cross(c.clone().sub(a));
      const outward = cross.dot(new THREE.Vector3(...info.n)) >= 0;
      const order = outward ? [0, 1, 2, 0, 2, 3] : [0, 2, 1, 0, 3, 2];
      for (const i of order) {
        g.pos.push(...pts[i]);
        g.uv.push(uvs[i][0], 1 - uvs[i][1]);
        g.normal.push(...info.n);
      }
    }
  }
  const out = [];
  for (const [tex, g] of byTexture) {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.Float32BufferAttribute(g.pos, 3));
    geo.setAttribute("uv", new THREE.Float32BufferAttribute(g.uv, 2));
    geo.setAttribute("normal", new THREE.Float32BufferAttribute(g.normal, 3));
    out.push({ geo, tex });
  }
  geometryCache.set(path, out);
  return out;
}

/** ItemDisplay（transform NONE）の中身：モデルの中心を原点に置き、Y 軸まわりに 180 度回す。 */
function itemContent(path) {
  const content = new THREE.Group();
  content.rotation.y = Math.PI;
  const mesh = new THREE.Group();
  mesh.position.set(-0.5, -0.5, -0.5);
  for (const { geo, tex } of modelGeometries(path)) mesh.add(new THREE.Mesh(geo, material(tex)));
  content.add(mesh);
  return content;
}

// ---------------------------------------------------------------- 時計（tick）と予約

class Sim {
  constructor() {
    this.now = 0;
    this.seq = 0;
    this.tasks = [];
    this.entities = new Set();
    this.scene = new THREE.Scene();
    this.duration = 1;
    this.chat = [];
  }

  /** ticks 後に fn（CasinoTable.later と同じく最低 1 tick 後）。 */
  later(ticks, fn) {
    this.tasks.push({ at: Math.floor(this.now) + Math.max(1, Math.round(ticks)), seq: this.seq++, fn });
  }

  advance(ticks) {
    const target = this.now + ticks;
    for (;;) {
      let next = null;
      for (const t of this.tasks) if (next === null || t.at < next.at || (t.at === next.at && t.seq < next.seq)) next = t;
      if (!next || next.at > target) break;
      this.now = Math.max(this.now, next.at);
      this.tasks.splice(this.tasks.indexOf(next), 1);
      next.fn();
    }
    this.now = target;
  }

  render() {
    for (const e of this.entities) e.update(this.now);
  }

  dispose() {
    for (const e of [...this.entities]) e.remove();
    this.scene.clear();
  }
}

let sim = null;

// ---------------------------------------------------------------- エンティティ

function lerp(a, b, t) { return a + (b - a) * t; }

/** Display エンティティ。位置はテレポートの補間、transformation は補間時間つきで変わる。 */
class Display {
  constructor(at, content) {
    this.outer = new THREE.Group();
    this.inner = new THREE.Group();
    this.inner.matrixAutoUpdate = false;
    this.outer.add(this.inner);
    if (content) this.inner.add(content);
    this.teleportDuration = 0;
    this.pos = { from: [at.x, at.y, at.z], to: [at.x, at.y, at.z], start: 0, dur: 0 };
    this.yaw = at.yaw || 0;
    const id = { t: [0, 0, 0], q: new THREE.Quaternion(), s: [1, 1, 1] };
    this.tr = { from: id, to: id, start: 0, dur: 0 };
    this.interpolationDuration = 0;
    this.removed = false;
    sim.scene.add(this.outer);
    sim.entities.add(this);
  }

  setTransformation(tr) {
    this.tr = { from: this.currentTr(sim.now), to: tr, start: Math.floor(sim.now), dur: this.interpolationDuration };
  }

  teleport(at) {
    const cur = this.currentPos(sim.now);
    this.pos = { from: cur, to: [at.x, at.y, at.z], start: Math.floor(sim.now), dur: this.teleportDuration };
    if (at.yaw !== undefined) this.yaw = at.yaw;
  }

  location() {
    const p = this.pos.to;
    return { x: p[0], y: p[1], z: p[2], yaw: this.yaw };
  }

  currentPos(now) {
    const p = this.pos;
    const k = p.dur <= 0 ? 1 : Math.min(1, Math.max(0, (now - p.start) / p.dur));
    return [0, 1, 2].map((i) => lerp(p.from[i], p.to[i], k));
  }

  currentTr(now) {
    const r = this.tr;
    const k = r.dur <= 0 ? 1 : Math.min(1, Math.max(0, (now - r.start) / r.dur));
    if (k >= 1) return r.to;
    return {
      t: [0, 1, 2].map((i) => lerp(r.from.t[i], r.to.t[i], k)),
      q: r.from.q.clone().slerp(r.to.q, k),
      s: [0, 1, 2].map((i) => lerp(r.from.s[i], r.to.s[i], k)),
    };
  }

  update(now) {
    const p = this.currentPos(now);
    this.outer.position.set(p[0], p[1], p[2]);
    this.outer.rotation.y = -this.yaw * DEG;
    const tr = this.currentTr(now);
    this.inner.matrix.compose(new THREE.Vector3(...tr.t), tr.q, new THREE.Vector3(...tr.s));
  }

  remove() {
    if (this.removed) return;
    this.removed = true;
    sim.scene.remove(this.outer);
    sim.entities.delete(this);
  }
}

function transformation(t, axis, deg, s) {
  const q = new THREE.Quaternion();
  if (axis) q.setFromAxisAngle(new THREE.Vector3(...axis), deg * DEG);
  return { t: [t.x ?? t[0], t.y ?? t[1], t.z ?? t[2]], q, s: [s, s, s] };
}

class ItemDisplay extends Display {
  constructor(at, path) {
    super(at, null);
    this.setItem(path);
  }

  setItem(path) {
    this.inner.clear();
    this.inner.add(itemContent(path));
    this.path = path;
  }
}

/** BlockDisplay は角が原点。cube は 0〜1 の箱、post は柵の柱。 */
class BlockDisplay extends Display {
  constructor(at, color, scale, shape) {
    const box = shape === "post" ? new THREE.BoxGeometry(0.25, 1, 0.25) : new THREE.BoxGeometry(1, 1, 1);
    box.translate(0.5, 0.5, 0.5);
    const mesh = new THREE.Mesh(box, new THREE.MeshLambertMaterial({ color }));
    super(at, mesh);
    const tr = { t: [-scale[0] / 2, 0, -scale[2] / 2], q: new THREE.Quaternion(), s: scale };
    this.tr = { from: tr, to: tr, start: 0, dur: 0 };
  }
}

/** TextDisplay（ビルボード）。1 行 = 0.25 ブロック × scale、下端がエンティティの位置。 */
class TextDisplay extends Display {
  constructor(at, scale) {
    const canvas = document.createElement("canvas");
    const tex = new THREE.CanvasTexture(canvas);
    tex.magFilter = THREE.LinearFilter;
    tex.encoding = THREE.sRGBEncoding;
    const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false }));
    sprite.center.set(0.5, 0);
    super(at, sprite);
    this.canvas = canvas;
    this.tex = tex;
    this.sprite = sprite;
    this.scale = scale;
    this.background = "rgba(0,0,0,0.25)";
    this.text([]);
  }

  /** lines: [[ [文字, 色], ... ], ...] */
  text(lines) {
    this.lines = lines;
    const px = 36, pad = 6;
    const ctx = this.canvas.getContext("2d");
    ctx.font = `${px}px DotGothic16, sans-serif`;
    let width = 0;
    for (const line of lines) width = Math.max(width, line.reduce((w, [t]) => w + ctx.measureText(t).width, 0));
    const empty = lines.length === 0 || width === 0;
    this.canvas.width = Math.max(4, Math.ceil(width + pad * 2));
    this.canvas.height = Math.max(4, lines.length * px * 1.1 + pad * 2);
    ctx.font = `${px}px DotGothic16, sans-serif`;
    if (!empty && this.background) {
      ctx.fillStyle = this.background;
      ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    }
    ctx.textBaseline = "top";
    lines.forEach((line, i) => {
      let x = pad + (width - line.reduce((w, [t]) => w + ctx.measureText(t).width, 0)) / 2;
      for (const [t, color] of line) {
        ctx.fillStyle = "rgba(0,0,0,0.35)";
        ctx.fillText(t, x + 3, pad + i * px * 1.1 + 3);
        ctx.fillStyle = color || C.WHITE;
        ctx.fillText(t, x, pad + i * px * 1.1);
        x += ctx.measureText(t).width;
      }
    });
    this.tex.needsUpdate = true;
    const h = (empty ? 0.0001 : lines.length * 0.25) * this.scale;
    this.sprite.scale.set(h * this.canvas.width / this.canvas.height, h, 1);
    this.sprite.visible = !empty;
  }
}

/** プレイヤーの形の人形（ディーラーの Mannequin、座っているボット）。 */
class Figure {
  constructor(at, opts) {
    this.group = new THREE.Group();
    const skin = new THREE.MeshLambertMaterial({ color: opts.skin || 0xc89a74 });
    const shirt = new THREE.MeshLambertMaterial({ color: opts.shirt });
    const pants = new THREE.MeshLambertMaterial({ color: opts.pants });
    const hair = new THREE.MeshLambertMaterial({ color: opts.hair || 0x3b2a1a });
    const box = (w, h, d, mat, x, y, z) => {
      const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
      m.position.set(x, y, z);
      return m;
    };
    const sit = !!opts.sitting;
    const hip = sit ? 0.55 : 0.75;
    // 脚（座っていれば前に伸ばす）
    for (const sx of [-0.125, 0.125]) {
      const leg = new THREE.Group();
      leg.position.set(sx, hip, 0);
      leg.add(box(0.25, 0.75, 0.25, pants, 0, -0.375, 0));
      if (sit) leg.rotation.x = -Math.PI / 2;
      this.group.add(leg);
    }
    this.group.add(box(0.5, 0.75, 0.25, shirt, 0, hip + 0.375, 0));
    const head = new THREE.Group();
    head.position.set(0, hip + 0.75, 0);
    head.add(box(0.5, 0.5, 0.5, skin, 0, 0.25, 0));
    head.add(box(0.52, 0.14, 0.52, hair, 0, 0.45, 0));
    head.add(box(0.06, 0.06, 0.02, new THREE.MeshBasicMaterial({ color: 0x2b2b6b }), -0.11, 0.24, 0.255));
    head.add(box(0.06, 0.06, 0.02, new THREE.MeshBasicMaterial({ color: 0x2b2b6b }), 0.11, 0.24, 0.255));
    this.group.add(head);
    this.arms = [];
    for (const sx of [-0.375, 0.375]) {
      const arm = new THREE.Group();
      arm.position.set(sx, hip + 0.7, 0);
      arm.add(box(0.25, 0.75, 0.25, shirt, 0, -0.3, 0));
      arm.add(box(0.25, 0.12, 0.25, skin, 0, -0.62, 0));
      if (sit) arm.rotation.x = -0.6;
      this.group.add(arm);
      this.arms.push(arm);
    }
    this.baseArm = sit ? -0.6 : 0;
    this.group.position.set(at.x, at.y, at.z);
    this.group.rotation.y = -at.yaw * DEG;
    this.swingAt = -100;
    this.removed = false;
    sim.scene.add(this.group);
    sim.entities.add(this);
  }

  /** 利き手を振る（Mannequin.swingMainHand、6 tick）。 */
  swing() {
    this.swingAt = sim.now;
  }

  update(now) {
    const k = (now - this.swingAt) / 6;
    const right = this.arms[0];
    right.rotation.x = k >= 0 && k <= 1 ? this.baseArm - Math.sin(k * Math.PI) * 1.2 : this.baseArm;
    right.rotation.z = k >= 0 && k <= 1 ? Math.sin(k * Math.PI) * 0.3 : 0;
  }

  remove() {
    if (this.removed) return;
    this.removed = true;
    sim.scene.remove(this.group);
    sim.entities.delete(this);
  }
}

// ---------------------------------------------------------------- 画面まわり（チャット・タイトル）

const chatEl = document.getElementById("chat");
const hudEl = document.getElementById("hud");
const actionbarEl = document.getElementById("actionbar");
const titleEl = document.getElementById("titleOverlay");

/** チャットに 1 行。parts は [[文字, 色], ...]。 */
function chat(parts) {
  const p = document.createElement("p");
  p.className = "line";
  for (const [t, color] of parts) {
    const s = document.createElement("span");
    s.textContent = t;
    s.style.color = color || C.WHITE;
    p.appendChild(s);
  }
  chatEl.appendChild(p);
  chatEl.scrollTop = chatEl.scrollHeight;
}

let titleUntil = -1;
function showTitle(main, sub, stayTicks) {
  document.getElementById("titleMain").textContent = main[0];
  document.getElementById("titleMain").style.color = main[1];
  document.getElementById("titleSub").textContent = sub[0];
  document.getElementById("titleSub").style.color = sub[1];
  titleEl.hidden = false;
  titleUntil = sim.now + stayTicks + 10;
}

let actionbarUntil = -1;
function actionbar(text) {
  actionbarEl.textContent = text;
  actionbarUntil = sim.now + 40;
}

function button(label, color) {
  return ["[" + label + "]", color];
}

// ---------------------------------------------------------------- ワールド

function buildWorld(s) {
  s.scene.background = new THREE.Color(0x88a9e8);
  s.scene.fog = new THREE.Fog(0x88a9e8, 18, 60);
  s.scene.add(new THREE.AmbientLight(0xffffff, 0.62));
  const sun = new THREE.DirectionalLight(0xffffff, 0.55);
  sun.position.set(0.4, 1, 0.7);
  s.scene.add(sun);
  // スーパーフラットの草ブロック（試験サーバーと同じ）
  const c = document.createElement("canvas");
  c.width = c.height = 16;
  const g = c.getContext("2d");
  let seed = 7;
  const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const v = 0.85 + rnd() * 0.25;
    g.fillStyle = `rgb(${Math.round(93 * v)},${Math.round(150 * v)},${Math.round(59 * v)})`;
    g.fillRect(x, y, 1, 1);
  }
  const tex = new THREE.CanvasTexture(c);
  tex.magFilter = THREE.NearestFilter;
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(60, 60);
  tex.encoding = THREE.sRGBEncoding;
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(60, 60), new THREE.MeshLambertMaterial({ map: tex }));
  ground.rotation.x = -Math.PI / 2;
  s.scene.add(ground);
}

/** テーブルの局所座標 → 位置（向きは yaw 0 のテーブル。局所 = ワールド）。 */
function at(local, yawOffset = 0) {
  return { x: local[0], y: local[1], z: local[2], yaw: yawOffset };
}

// ---------------------------------------------------------------- CasinoTable の共通部分

class Table {
  constructor(shape) {
    this.shape = shape;
    this.seats = [];
    this.fixtures = [];
  }

  spawn() {
    const sh = this.shape;
    const model = new ItemDisplay(at(sh.modelAt || [0, 0, 0], sh.modelYaw || 0), sh.model);
    const ms = transformation([0, 0, 0], null, 0, sh.modelScale || MODEL_SCALE);
    model.tr = { from: ms, to: ms, start: 0, dur: 0 };
    const d = sh.dealer;
    if (d) {
      this.dealer = new Figure({ x: d[0], y: 0, z: d[2], yaw: 0 }, { shirt: 0x1d1d22, pants: 0x18181c, skin: 0xd2a07c, hair: 0x2a1c12 });
      const name = new TextDisplay(at([d[0], 2.05, d[2]]), 0.5);
      name.text([[["ディーラー", C.GOLD]]]);
    }
    this.status = new TextDisplay(at(sh.status || [d[0], 2.45, d[2]]), sh.statusScale || 1);
    for (let i = 0; i < this.shape.seats.length; i++) {
      const p = this.shape.seats[i];
      new BlockDisplay(at([p[0], 0, p[2]]), 0x42291a, [1, 0.38, 1], "post");
      new BlockDisplay(at([p[0], 0.38, p[2]]), 0xa12722, [0.5, 0.1, 0.5]);
    }
  }

  /** ボット（またはあなた）を座らせる。 */
  sit(index, name, chips, you) {
    const p = this.shape.seats[index];
    const yaw = this.shape.seatYaw ? this.shape.seatYaw(index) : 180;
    const colors = [0x3b6fb6, 0x7b3fa0, 0xb5553a, 0x2f8f6a, 0x9a8a2a, 0x4a4a8a];
    const fig = new Figure({ x: p[0], y: 0, z: p[2], yaw }, { sitting: true, shirt: you ? 0x2e9e9e : colors[index % 6], pants: 0x2a2f55 });
    const tag = new TextDisplay(at([p[0], 1.62, p[2]]), 0.45);
    tag.text([[[name, C.WHITE]]]);
    this.seats[index] = { name, chips, atRisk: 0, you: !!you, fig, tag };
    // 自分の体と名札は、自分の目線では見えない（Minecraft の一人称と同じ）
    if (you) { fig.group.visible = false; tag.outer.visible = false; this.youParts = [fig.group, tag.outer]; }
  }

  label(local, scale) {
    const t = new TextDisplay(at(local), scale);
    t.teleportDuration = CARD_STEP;
    return t;
  }

  swing() {
    this.dealer.swing();
  }

  setStatus(lines) {
    this.status.text(lines);
  }

  broadcast(parts) {
    chat(parts);
  }

  /** あなたにだけ届く文。 */
  sendYou(index, parts) {
    if (this.seats[index] && this.seats[index].you) chat(parts);
  }
}

// ---------------------------------------------------------------- CardView / ChipView

const CARD_STEP = 5;
const CARD_SCALE = 0.4;
const SUITS = ["spade", "heart", "diamond", "club"];
const RANKS = ["a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k"];

/** "S10" "HA" のような書き方からカード。 */
function card(s) {
  const suit = "SHDC".indexOf(s[0]);
  const r = s.slice(1);
  const rank = r === "A" ? 0 : r === "J" ? 10 : r === "Q" ? 11 : r === "K" ? 12 : Number(r) - 1;
  return { suit, rank, code: suit * 13 + rank + 1 };
}
function cardModel(code) {
  if (code === 0) return "card/back";
  return "card/" + SUITS[Math.floor((code - 1) / 13)] + "_" + RANKS[(code - 1) % 13];
}
function cardText(c) {
  const suit = "♠♥♦♣"[c.suit];
  const rank = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"][c.rank];
  return [suit + rank, c.suit === 1 || c.suit === 2 ? C.RED : C.WHITE];
}
function cardPoints(c) {
  return Math.min(10, c.rank + 1);
}
function handTotal(cards) {
  let total = 0, aces = 0;
  for (const c of cards) { total += cardPoints(c); if (c.rank === 0) aces++; }
  if (aces && total + 10 <= 21) return total + 10;
  return total;
}
function handSoft(cards) {
  let total = 0, aces = 0;
  for (const c of cards) { total += cardPoints(c); if (c.rank === 0) aces++; }
  return aces > 0 && total + 10 <= 21;
}

function cardTransform(translation, deg) {
  return transformation(translation, [0, 0, 1], deg, CARD_SCALE);
}

class CardView {
  /** CardView.deal：from（target から見た差）から弧を描いて配る。 */
  static deal(target, from, code, faceUp) {
    const flip = faceUp ? 180 : 0;
    const d = new ItemDisplay(target, cardModel(faceUp ? code : 0));
    const start = cardTransform(from, flip);
    d.tr = { from: start, to: start, start: 0, dur: 0 };
    d.teleportDuration = CARD_STEP;
    const card = new CardView(d, code, faceUp);
    const mid = [from[0] * 0.5, from[1] * 0.5 + 0.3, from[2] * 0.5];
    card.animate(cardTransform(mid, flip), 1);
    card.animate(cardTransform([0, 0, 0], flip), 1 + CARD_STEP);
    return card;
  }

  constructor(display, code, faceUp) {
    this.display = display;
    this.code = code;
    this.faceUp = faceUp;
  }

  reveal(code) {
    if (this.faceUp || this.display.removed) return;
    this.code = code;
    this.faceUp = true;
    this.display.setItem(cardModel(code));
    this.animate(cardTransform([0, 0.15, 0], 90), 0);
    this.animate(cardTransform([0, 0, 0], 180), CARD_STEP);
  }

  moveTo(target) {
    this.display.teleport(target);
  }

  remove() {
    this.display.remove();
  }

  animate(tr, delay) {
    const apply = () => {
      if (this.display.removed) return;
      this.display.interpolationDuration = CARD_STEP;
      this.display.setTransformation(tr);
    };
    if (delay <= 0) apply();
    else sim.later(delay, apply);
  }
}

/** ポーカーの手札：持ち主（あなた）にだけ表、ほかの人には裏。 */
class PrivateCardView {
  static deal(target, from, code, ownerIsViewer) {
    const viewerSeesFront = ownerIsViewer();
    const d = new ItemDisplay(target, cardModel(viewerSeesFront ? code : 0));
    const flip = viewerSeesFront ? 180 : 0;
    const start = cardTransform(from, flip);
    d.tr = { from: start, to: start, start: 0, dur: 0 };
    d.teleportDuration = CARD_STEP;
    const v = new PrivateCardView(d, code, viewerSeesFront);
    const mid = [from[0] * 0.5, from[1] * 0.5 + 0.3, from[2] * 0.5];
    v.animate(cardTransform(mid, flip), 1);
    v.animate(cardTransform([0, 0, 0], flip), 1 + CARD_STEP);
    return v;
  }

  constructor(display, code, front) {
    this.display = display;
    this.code = code;
    this.front = front;
    this.revealed = false;
  }

  reveal() {
    if (this.revealed || this.display.removed) return;
    this.revealed = true;
    if (this.front) return; // 持ち主には最初から表が見えている
    this.display.setItem(cardModel(this.code));
    this.animate(cardTransform([0, 0.15, 0], 90), 0);
    this.animate(cardTransform([0, 0, 0], 180), CARD_STEP);
  }

  muck(target) {
    if (this.display.removed) return;
    this.display.teleport({ ...target, yaw: this.display.yaw });
    sim.later(CARD_STEP + 1, () => this.remove());
  }

  remove() {
    this.display.remove();
  }

  animate(tr, delay) {
    CardView.prototype.animate.call(this, tr, delay);
  }
}

const DENOMINATIONS = [1000, 500, 100, 50, 10];

class ChipView {
  constructor() {
    this.stacks = [];
    this.label = null;
    this.base = null;
    this.amount = 0;
    this.spacing = 0.15;
  }

  static columns(amount) {
    const out = [];
    let rest = amount;
    for (const value of DENOMINATIONS) {
      const count = Math.floor(rest / value);
      rest -= count * value;
      if (count > 0) out.push("chip/c" + value + "_" + Math.min(count, 10));
    }
    return out;
  }

  static offset(base, o) {
    const r = (base.yaw || 0) * DEG;
    return { x: base.x + o * Math.cos(r), y: base.y, z: base.z + o * Math.sin(r), yaw: base.yaw };
  }

  show(base, amount, withLabel = true) {
    if (amount === this.amount && this.base && base.x === this.base.x && base.y === this.base.y && base.z === this.base.z) return;
    this.remove();
    this.base = { ...base };
    this.amount = amount;
    if (amount <= 0) return;
    const scale = withLabel ? 0.3 : 0.22;
    this.spacing = withLabel ? 0.15 : 0.07;
    const cols = ChipView.columns(amount);
    cols.forEach((path, i) => {
      const o = (i - (cols.length - 1) / 2) * this.spacing;
      const d = new ItemDisplay(ChipView.offset(base, o), path);
      const tr = transformation([0, 0, 0], null, 0, scale);
      d.tr = { from: tr, to: tr, start: 0, dur: 0 };
      d.teleportDuration = 8;
      this.stacks.push(d);
    });
    if (withLabel) {
      this.label = new TextDisplay({ x: base.x, y: base.y + 0.28, z: base.z }, 0.5);
      this.label.text([[[String(amount), C.GOLD]]]);
      this.label.teleportDuration = 8;
    }
  }

  slideTo(target) {
    this.stacks.forEach((d, i) => {
      const o = (i - (this.stacks.length - 1) / 2) * this.spacing;
      d.teleport(ChipView.offset(target, o));
    });
    if (this.label) this.label.teleport({ x: target.x, y: target.y + 0.28, z: target.z });
    this.base = { ...target };
  }

  remove() {
    this.stacks.forEach((d) => d.remove());
    this.stacks = [];
    if (this.label) { this.label.remove(); this.label = null; }
    this.amount = 0;
    this.base = null;
  }
}

// ---------------------------------------------------------------- 再生の進め方（イベントを順に、待ち時間つきで）

class Player {
  constructor(play) {
    this.play = play;
    this.queue = [];
    this.waiting = false;
    this.pumping = false;
  }

  enqueue(events) {
    this.queue.push(...events);
    this.pump();
  }

  pump() {
    if (this.pumping) return;
    this.pumping = true;
    try {
      while (this.queue.length && !this.waiting) {
        const delay = this.play(this.queue.shift());
        if (delay > 0) {
          this.pumping = false;
          sim.later(delay, () => this.pump());
          return;
        }
      }
    } finally {
      this.pumping = false;
    }
  }
}

// ================================================================ ブラックジャック

const BJ = {
  seatX: (i) => 1.6 - 0.8 * i,
  shape: { model: "table/blackjack", dealer: [0, 0, -1.55], seats: [0, 1, 2, 3, 4].map((i) => [1.6 - 0.8 * i, 0, 1.55]) },
  shoe: [1.4, TOP + 0.05, -0.6],
  playerCard(seat, hand, hands, index) {
    const base = BJ.seatX(seat) + (hand - (hands - 1) / 2) * 0.34;
    return [base + 0.06 * index, TOP + 0.004 + 0.003 * index, 0.25 - 0.05 * index];
  },
  dealerCard: (i) => [-0.2 + 0.14 * i, TOP + 0.004 + 0.003 * i, -0.55],
  chips: (seat) => [BJ.seatX(seat), TOP + 0.001, 0.68],
  dealerChips: [-0.9, TOP + 0.001, -0.6],
  handLabel(seat, hand, hands) {
    const c = BJ.playerCard(seat, hand, hands, 0);
    return [c[0], TOP + 0.45, c[2]];
  },
  dealerLabel: [0, TOP + 0.7, -0.55],
};

function delta(from, to, yawOffset = 0) {
  const dx = from[0] - to[0], dz = from[2] - to[2];
  const r = yawOffset * DEG;
  return [dx * Math.cos(r) + dz * Math.sin(r), from[1] - to[1], -dx * Math.sin(r) + dz * Math.cos(r)];
}

function blackjackScenario() {
  const t = new Table(BJ.shape);
  t.spawn();
  const YOU = 3;
  t.sit(1, "Bot1", 1000);
  t.sit(2, "Bot2", 1000);
  t.sit(YOU, "あなた", 1000, true);
  const bets = { 1: 50, 2: 100, 3: 200 };
  const minBet = 10, maxBet = 1000;
  const pending = {};
  let state = "IDLE", countdown = 0;
  const handCards = {}, handViews = {}, betViews = {}, handLabels = {};
  const payoutViews = [];
  const dealerCards = [], dealerViews = [];
  let dealerLabel = null;
  let turn = null;

  const limits = [["賭け金 " + minBet + "〜" + maxBet + "（練習用）", C.GRAY]];
  function status() {
    if (state === "IDLE") t.setStatus([[["ベットを待っています", C.AQUA]], limits]);
    else if (state === "BETTING") t.setStatus([[["ベット受付中（あと " + Math.max(countdown, 0) + " 秒）", C.AQUA]], limits]);
    else t.setStatus([[[turn !== null ? t.seats[turn].name + " の番" : "勝負中", C.YELLOW]], limits]);
  }
  status();

  // 席に着いた 10 tick 後にボットが賭ける。あなたはメニューで 200 枚を選んで決定
  for (const i of [1, 2, YOU]) {
    sim.later(i === YOU ? 34 : 10 + i * 3, () => {
      pending[i] = new ChipView();
      pending[i].show(at(BJ.chips(i)), bets[i]);
      t.seats[i].chips -= bets[i];
      if (state === "IDLE") {
        state = "BETTING";
        countdown = 15;
        t.broadcast([["ベットを受け付けています（あと " + countdown + " 秒）", C.AQUA]]);
      }
      if (Object.keys(pending).length === 3) countdown = Math.min(countdown, 2);
      status();
    });
  }
  sim.later(12, () => chat([["（あなたは賭け金メニューで 200 枚を選んで決定）", C.DARK_GRAY]]));

  // 1 秒ごと
  const tick = () => {
    if (state === "BETTING") {
      countdown--;
      if (countdown <= 0) deal();
      status();
    } else if (state === "PLAYING" && turn === YOU) {
      countdown--;
      actionbar("あなたの番です（あと " + Math.max(countdown, 0) + " 秒）");
    }
    sim.later(20, tick);
  };
  sim.later(20, tick);

  const P = (s, h, c) => ({ k: "PlayerCard", seat: s, hand: h, card: card(c) });
  const events = [
    P(1, 0, "S8"), P(2, 0, "DA"), P(3, 0, "C5"), { k: "DealerCard", card: card("D6") },
    P(1, 0, "H8"), P(2, 0, "CK"), P(3, 0, "H6"), { k: "DealerCard", hidden: true },
    { k: "Turn", seat: 1, hand: 0, then: "SPLIT" },
  ];
  const after = {
    "1:0:SPLIT": [{ k: "Split", seat: 1, hand: 0, newHand: 1 }, P(1, 0, "C3"), { k: "Turn", seat: 1, hand: 0, then: "HIT" }],
    "1:0:HIT": [P(1, 0, "SQ"), { k: "HandDone", seat: 1, hand: 0 }, P(1, 1, "H10"), { k: "Turn", seat: 1, hand: 1, then: "STAND" }],
    "1:1:STAND": [{ k: "HandDone", seat: 1, hand: 1 }, { k: "HandDone", seat: 2, hand: 0 }, { k: "Turn", seat: 3, hand: 0, then: "DOUBLE" }],
    "3:0:DOUBLE": [
      { k: "Doubled", seat: 3, hand: 0, bet: 400 }, P(3, 0, "D9"), { k: "HandDone", seat: 3, hand: 0 },
      { k: "HoleCardRevealed", card: card("S10") }, { k: "DealerCard", card: card("C4") }, { k: "DealerDone" },
      { k: "Settled", seat: 1, hand: 0, outcome: "WIN", bet: 50, payout: 100 },
      { k: "Settled", seat: 1, hand: 1, outcome: "LOSE", bet: 50, payout: 0 },
      { k: "Settled", seat: 2, hand: 0, outcome: "BLACKJACK", bet: 100, payout: 250 },
      { k: "Settled", seat: 3, hand: 0, outcome: "PUSH", bet: 400, payout: 400 },
      { k: "RoundOver" },
    ],
  };

  const player = new Player(play);

  function deal() {
    state = "PLAYING";
    for (const i of [1, 2, 3]) {
      pending[i].remove();
      handCards[i] = [[]];
      handViews[i] = [[]];
      const chips = new ChipView();
      chips.show(at(BJ.chips(i)), bets[i]);
      betViews[i] = [chips];
      handLabels[i] = [t.label(BJ.handLabel(i, 0, 1), 0.45)];
    }
    dealerLabel = t.label(BJ.dealerLabel, 0.45);
    t.broadcast([["ノーモアベット。配ります", C.AQUA]]);
    player.enqueue(events);
  }

  function totalText(cards, canBeBlackjack) {
    if (!cards.length) return [];
    const total = handTotal(cards);
    if (canBeBlackjack && cards.length === 2 && total === 21) return [["ブラックジャック", C.GOLD]];
    if (total > 21) return [[total + " バースト", C.RED]];
    return [[(handSoft(cards) && total < 21 ? "ソフト " : "") + total, C.WHITE]];
  }
  function updateHandLabel(seat, hand, result) {
    const label = handLabels[seat][hand];
    if (!label) return;
    const lines = [totalText(handCards[seat][hand], handCards[seat].length === 1)];
    if (result) lines.push([result]);
    label.text(lines);
  }
  function updateDealerLabel() {
    dealerLabel.text(dealerCards.length ? [totalText(dealerCards, dealerViews.length === dealerCards.length)] : []);
  }
  function chipSpot(seat, hand) {
    const hands = handCards[seat].length;
    const s = BJ.chips(seat), c = BJ.playerCard(seat, hand, hands, 0);
    return at([c[0], s[1], s[2]]);
  }
  function payoutSpot(seat, hand) {
    const hands = handCards[seat].length;
    const s = BJ.chips(seat), c = BJ.playerCard(seat, hand, hands, 0);
    return at([c[0] - 0.17, s[1], s[2] + 0.1]);
  }

  function play(e) {
    switch (e.k) {
      case "PlayerCard": {
        const cards = handCards[e.seat], views = handViews[e.seat];
        while (cards.length <= e.hand) { cards.push([]); views.push([]); }
        const idx = cards[e.hand].length;
        const p = BJ.playerCard(e.seat, e.hand, cards.length, idx);
        views[e.hand].push(CardView.deal(at(p), delta(BJ.shoe, p), e.card.code, true));
        cards[e.hand].push(e.card);
        updateHandLabel(e.seat, e.hand);
        t.swing();
        return 8;
      }
      case "DealerCard": {
        const p = BJ.dealerCard(dealerViews.length);
        dealerViews.push(CardView.deal(at(p), delta(BJ.shoe, p), e.hidden ? 0 : e.card.code, !e.hidden));
        if (!e.hidden) dealerCards.push(e.card);
        updateDealerLabel();
        t.swing();
        return 8;
      }
      case "HoleCardRevealed":
        dealerCards.splice(1, 0, e.card);
        dealerViews[1].reveal(e.card.code);
        updateDealerLabel();
        return 14;
      case "Split": {
        const cards = handCards[e.seat], views = handViews[e.seat];
        while (cards.length <= e.newHand) { cards.push([]); views.push([]); }
        cards[e.newHand].push(cards[e.hand].splice(1, 1)[0]);
        views[e.newHand].push(views[e.hand].splice(1, 1)[0]);
        const hands = cards.length;
        for (let h = 0; h < hands; h++) views[h].forEach((v, i) => v.moveTo(at(BJ.playerCard(e.seat, h, hands, i))));
        const bv = betViews[e.seat];
        const amount = bv[0].amount;
        bv.push(new ChipView());
        bv.forEach((v, h) => v.show(chipSpot(e.seat, h), amount));
        t.seats[e.seat].chips -= amount;
        const labels = handLabels[e.seat];
        labels.push(t.label(BJ.handLabel(e.seat, e.newHand, hands), 0.45));
        labels.forEach((l, h) => { l.teleport(at(BJ.handLabel(e.seat, h, hands))); updateHandLabel(e.seat, h); });
        return 10;
      }
      case "Doubled":
        betViews[e.seat][e.hand].show(chipSpot(e.seat, e.hand), e.bet);
        return 4;
      case "Turn":
        startTurn(e);
        return 0;
      case "HandDone":
        updateHandLabel(e.seat, e.hand);
        return 6;
      case "DealerDone":
        updateDealerLabel();
        return 12;
      case "Settled":
        settle(e);
        return 10;
      case "RoundOver":
        sim.later(70, finishRound);
        return 0;
    }
    return 0;
  }

  const ACTION = { HIT: ["ヒット", C.GREEN], STAND: ["スタンド", C.RED], DOUBLE: ["ダブルダウン", C.GOLD], SPLIT: ["スプリット", C.AQUA] };

  function startTurn(e) {
    player.waiting = true;
    turn = e.seat;
    countdown = 20;
    status();
    const act = () => {
      player.waiting = false;
      turn = null;
      if (e.then === "DOUBLE") t.seats[e.seat].chips -= 200;
      player.enqueue(after[e.seat + ":" + e.hand + ":" + e.then]);
    };
    if (e.seat === YOU) {
      const total = handTotal(handCards[e.seat][e.hand]);
      showTitle(["あなたの番", C.YELLOW], ["合計 " + total, C.WHITE], 24);
      chat([["手札 合計 " + total + "： ", C.WHITE], button("ヒット", C.GREEN), [" "], button("スタンド", C.RED), [" "], button("ダブルダウン", C.GOLD)]);
      sim.later(50, () => { chat([["（あなたは [ダブルダウン] を押した）", C.DARK_GRAY]]); act(); });
    } else {
      sim.later(12, act);
    }
  }

  function settle(e) {
    const seat = t.seats[e.seat];
    seat.chips += e.payout;
    const bet = betViews[e.seat][e.hand];
    const result = { BLACKJACK: "ブラックジャック！", WIN: "勝ち", PUSH: "引き分け", LOSE: "負け", BUST: "バースト" }[e.outcome];
    const color = { BLACKJACK: C.GREEN, WIN: C.GREEN, PUSH: C.YELLOW, LOSE: C.RED, BUST: C.RED }[e.outcome];
    updateHandLabel(e.seat, e.hand, [e.outcome === "BLACKJACK" ? "3:2 で勝ち" : result, color]);
    if (e.outcome === "LOSE" || e.outcome === "BUST") bet.slideTo(at(BJ.dealerChips));
    else if (e.outcome === "WIN" || e.outcome === "BLACKJACK") {
      const win = new ChipView();
      win.show(at(BJ.dealerChips), e.payout - e.bet);
      payoutViews.push(win);
      const spot = payoutSpot(e.seat, e.hand);
      sim.later(2, () => win.slideTo(spot));
      if (e.outcome === "BLACKJACK") sparkle(at(BJ.handLabel(e.seat, e.hand, handCards[e.seat].length)), 0xffd75e);
    }
    const net = e.payout - e.bet;
    t.sendYou(e.seat, [[result + "（" + (net > 0 ? "+" + net : net) + " 枚、手元 " + seat.chips + " 枚）", color]]);
  }

  function finishRound() {
    for (const i of [1, 2, 3]) {
      handViews[i].forEach((l) => l.forEach((v) => v.remove()));
      betViews[i].forEach((v) => v.remove());
      handLabels[i].forEach((l) => l.remove());
    }
    payoutViews.forEach((v) => v.remove());
    dealerViews.forEach((v) => v.remove());
    dealerLabel.remove();
    state = "IDLE";
    status();
    chat([["（次の回の賭け金メニューが開く）", C.DARK_GRAY]]);
  }

  return { you: YOU, table: t, end: 0 };
}

/** 花火の粒（Particle.FIREWORK などの代わり）。 */
function sparkle(pos, color) {
  const n = 30;
  const geo = new THREE.BufferGeometry();
  const pts = [], vel = [];
  for (let i = 0; i < n; i++) {
    pts.push(pos.x, pos.y, pos.z);
    vel.push((Math.random() - 0.5) * 0.08, Math.random() * 0.08, (Math.random() - 0.5) * 0.08);
  }
  geo.setAttribute("position", new THREE.Float32BufferAttribute(pts, 3));
  const points = new THREE.Points(geo, new THREE.PointsMaterial({ color, size: 0.06 }));
  sim.scene.add(points);
  const born = sim.now;
  const fx = {
    removed: false,
    update(now) {
      const age = now - born;
      const a = geo.attributes.position;
      for (let i = 0; i < n; i++) {
        a.setXYZ(i, pos.x + vel[i * 3] * age, pos.y + vel[i * 3 + 1] * age - 0.002 * age * age, pos.z + vel[i * 3 + 2] * age);
      }
      a.needsUpdate = true;
      points.material.opacity = Math.max(0, 1 - age / 25);
      points.material.transparent = true;
      if (age > 25) fx.remove();
    },
    remove() {
      if (fx.removed) return;
      fx.removed = true;
      sim.scene.remove(points);
      sim.entities.delete(fx);
    },
  };
  sim.entities.add(fx);
}

// ================================================================ ルーレット

const WHEEL_ORDER = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26];
const REDS = new Set([1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]);
const PAYOUT = { STRAIGHT: 35, SPLIT: 17, STREET: 11, CORNER: 8, LINE: 5, DOZEN: 2, COLUMN: 2, RED: 1, BLACK: 1, EVEN: 1, ODD: 1, LOW: 1, HIGH: 1 };
const BET_NAME = { STRAIGHT: "ストレートアップ", SPLIT: "スプリット", STREET: "ストリート", CORNER: "コーナー", LINE: "ライン", DOZEN: "ダズン", COLUMN: "コラム", RED: "赤", BLACK: "黒", EVEN: "偶数", ODD: "奇数", LOW: "1〜18", HIGH: "19〜36" };

const RB = {
  straight: (n, a) => ({ type: "STRAIGHT", anchor: n, numbers: [n], amount: a }),
  split: (x, y, a) => {
    const lo = Math.min(x, y), hi = Math.max(x, y);
    return { type: "SPLIT", anchor: lo * 100 + hi, numbers: [lo, hi], amount: a };
  },
  street: (row, a) => { const f = (row - 1) * 3 + 1; return { type: "STREET", anchor: row, numbers: [f, f + 1, f + 2], amount: a }; },
  corner: (n, a) => n === 0 ? { type: "CORNER", anchor: 0, numbers: [0, 1, 2, 3], amount: a } : { type: "CORNER", anchor: n, numbers: [n, n + 1, n + 3, n + 4], amount: a },
  line: (row, a) => { const f = (row - 1) * 3 + 1; return { type: "LINE", anchor: row, numbers: [0, 1, 2, 3, 4, 5].map((i) => f + i), amount: a }; },
  dozen: (d, a) => ({ type: "DOZEN", anchor: d, numbers: Array.from({ length: 12 }, (_, i) => (d - 1) * 12 + 1 + i), amount: a }),
  column: (c, a) => ({ type: "COLUMN", anchor: c, numbers: Array.from({ length: 12 }, (_, i) => c + 3 * i), amount: a }),
  outside: (type, a) => {
    const nums = [];
    for (let n = 1; n <= 36; n++) {
      const inn = { RED: REDS.has(n), BLACK: !REDS.has(n), EVEN: n % 2 === 0, ODD: n % 2 === 1, LOW: n <= 18, HIGH: n >= 19 }[type];
      if (inn) nums.push(n);
    }
    return { type, anchor: 0, numbers: nums, amount: a };
  },
};

const RL = {
  PX: 48, MAT_U: 58, MAT_V: 30, CELL: 9, WHEEL_U: 28, WHEEL_V: 48,
  BALL_TRACK: 0.44, BALL_POCKET: 0.32,
  shape: { model: "table/roulette", dealer: [-1.2, 0, -1.55], seats: [0, 1, 2, 3, 4].map((i) => [1.7 - 0.85 * i, 0, 1.55]) },
  wheel() { return [RL.WHEEL_U / RL.PX - 2, TOP + 0.002, RL.WHEEL_V / RL.PX - 1]; },
  aroundWheel(angle, radius, height) {
    const c = RL.wheel(), r = angle * DEG;
    return [c[0] + radius * Math.sin(r), TOP + height, c[2] - radius * Math.cos(r)];
  },
  resultLabel() { const c = RL.wheel(); return [c[0], TOP + 0.9, c[2]]; },
  history: [-0.4, TOP + 0.75, -0.75],
  croupierChips: [-0.4, TOP + 0.001, -0.75],
  px(u, v) { return [u / RL.PX - 2, TOP + 0.001, v / RL.PX - 1]; },
  cellCenter(n) {
    const { MAT_U, MAT_V, CELL } = RL;
    if (n === 0) return [MAT_U + CELL / 2, MAT_V + CELL * 1.5];
    const column = Math.floor((n - 1) / 3), row = 2 - (n - 1) % 3;
    return [MAT_U + CELL + CELL * column + CELL / 2, MAT_V + CELL * row + CELL / 2];
  },
  spot(bet) {
    const { MAT_U, MAT_V, CELL } = RL;
    switch (bet.type) {
      case "STRAIGHT": { const c = RL.cellCenter(bet.anchor); return RL.px(c[0], c[1]); }
      case "SPLIT": {
        const lo = Math.floor(bet.anchor / 100), hi = bet.anchor % 100;
        const a = RL.cellCenter(lo), b = RL.cellCenter(hi);
        if (lo === 0) return RL.px(MAT_U + CELL, b[1]);
        return RL.px((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
      }
      case "STREET": { const c = RL.cellCenter((bet.anchor - 1) * 3 + 1); return RL.px(c[0], MAT_V + CELL * 3); }
      case "CORNER": {
        if (bet.anchor === 0) return RL.px(MAT_U + CELL, MAT_V + CELL * 3);
        const a = RL.cellCenter(bet.anchor), d = RL.cellCenter(bet.anchor + 4);
        return RL.px((a[0] + d[0]) / 2, (a[1] + d[1]) / 2);
      }
      case "LINE": { const c = RL.cellCenter((bet.anchor - 1) * 3 + 1); return RL.px(c[0] + CELL / 2, MAT_V + CELL * 3); }
      case "DOZEN": return RL.px(MAT_U + CELL + 36 * (bet.anchor - 1) + 18, MAT_V + CELL * 3 + CELL / 2);
      case "COLUMN": return RL.px(MAT_U + CELL * 13 + CELL / 2, MAT_V + CELL * (3 - bet.anchor) + CELL / 2);
      default: {
        const index = ["LOW", "EVEN", "RED", "BLACK", "ODD", "HIGH"].indexOf(bet.type);
        return RL.px(MAT_U + CELL + 18 * index + 9, MAT_V + CELL * 4 + CELL / 2);
      }
    }
  },
};

function numberParts(n) {
  const color = n === 0 ? C.GREEN : REDS.has(n) ? C.RED : C.WHITE;
  return [n + " " + (n === 0 ? "緑" : REDS.has(n) ? "赤" : "黒"), color];
}

/** 毎回同じ動きになる乱数（実機は SecureRandom）。 */
function seeded(seed) {
  let s = seed;
  return { nextInt: (n) => { s = (s * 1103515245 + 12345) & 0x7fffffff; return s % n; } };
}

function rouletteScenario() {
  const t = new Table(RL.shape);
  t.spawn();
  const YOU = 2;
  const SPIN_TICKS = 160, STEP = 2;
  const BETTING_SECONDS = 6;
  const minBet = 10, maxBet = 500, maxTotal = 2000;
  const random = seeded(20261025);
  const RESULT = 17;
  t.sit(1, "Bot1", 1000);
  t.sit(YOU, "あなた", 1000, true);
  t.sit(3, "Bot3", 1000);
  t.sit(4, "Bot4", 1000);
  const plans = {
    1: [RB.straight(17, 10), RB.outside("RED", 50)],
    2: [RB.corner(13, 20), RB.dozen(2, 50), RB.column(2, 30)],
    3: [RB.split(0, 3, 10), RB.outside("BLACK", 100)],
    4: [RB.line(1, 20), RB.outside("ODD", 40), RB.straight(26, 10)],
  };
  let state = "IDLE", countdown = 0;
  let wheelAngle = 0;
  const history = [32, 5, 0, 21, 14];
  const bets = {};
  const chipViews = {};
  const payoutViews = [];

  const wheel = new ItemDisplay(at(RL.wheel()), "roulette/wheel");
  const wt = (deg) => transformation([0, 0, 0], [0, 1, 0], deg, 1.0);
  wheel.tr = { from: wt(0), to: wt(0), start: 0, dur: 0 };
  const ball = new ItemDisplay(at(RL.aroundWheel(0, RL.BALL_POCKET, 0.045)), "roulette/ball");
  const bt = transformation([0, 0, 0], null, 0, 0.5);
  ball.tr = { from: bt, to: bt, start: 0, dur: 0 };
  ball.teleportDuration = STEP;
  const resultLabel = t.label(RL.resultLabel(), 1.0);
  const historyLabel = t.label(RL.history, 0.5);
  function updateHistory() {
    const line = history.map((n) => [n + " ", n === 0 ? C.GREEN : REDS.has(n) ? C.RED : C.WHITE]);
    historyLabel.text([[["これまでの結果", C.GOLD]], line]);
  }
  updateHistory();

  const limits = [["1 か所 " + minBet + "〜" + maxBet + "、合計 " + maxTotal + " まで（練習用）", C.GRAY]];
  function status() {
    const head = {
      IDLE: ["ベットを待っています", C.AQUA],
      BETTING: ["ベット受付中（あと " + Math.max(countdown, 0) + " 秒）", C.AQUA],
      SPINNING: ["ノーモアベット", C.YELLOW],
      PAYING: ["払い戻し中", C.YELLOW],
    }[state];
    t.setStatus([[head], limits]);
  }
  status();

  const key = (b) => b.type + ":" + b.anchor;
  const offset = (spot, seat) => {
    const d = (seat - 2) * 0.012;
    return [spot[0] + d, spot[1] + seat * 0.0005, spot[2] + d * 0.5];
  };

  function place(i, bet) {
    bets[i] = bets[i] || [];
    bets[i].push(bet);
    t.seats[i].chips -= bet.amount;
    chipViews[i] = chipViews[i] || {};
    const view = chipViews[i][key(bet)] = chipViews[i][key(bet)] || new ChipView();
    view.show(at(offset(RL.spot(bet), i)), bet.amount, false);
    if (state === "IDLE") {
      state = "BETTING";
      countdown = BETTING_SECONDS;
      t.broadcast([["ベットを受け付けています（あと " + countdown + " 秒）", C.AQUA]]);
    }
    status();
  }

  for (const i of [1, 3, 4]) sim.later(10 + i * 4, () => plans[i].forEach((b) => place(i, b)));
  // あなたは盤のメニューで 3 か所に置く
  plans[YOU].forEach((b, k) => sim.later(30 + k * 14, () => {
    place(YOU, b);
    chat([["（盤のメニューで " + BET_NAME[b.type] + " に " + b.amount + " 枚）", C.DARK_GRAY]]);
  }));

  const tick = () => {
    if (state === "BETTING") {
      countdown--;
      if (countdown === 3) t.broadcast([["まもなく締め切ります", C.YELLOW]]);
      if (countdown <= 0) spin();
      status();
    }
    sim.later(20, tick);
  };
  sim.later(20, tick);

  function spin() {
    state = "SPINNING";
    t.broadcast([["ノーモアベット", C.AQUA]]);
    resultLabel.text([]);
    animateSpin(RESULT);
    sim.later(SPIN_TICKS + 10, payout);
  }

  const easeOut = (x, p) => 1 - Math.pow(1 - x, p);

  // RouletteTable.animateSpin と同じ計算
  function animateSpin(result) {
    const startWheel = wheelAngle;
    const wheelTurn = 720 + random.nextInt(360);
    const endWheel = startWheel + wheelTurn;
    const target = WHEEL_ORDER.indexOf(result) * (360 / 37) - endWheel;
    const startBall = random.nextInt(360);
    const diff = Math.round(target - startBall);
    const ballTurn = 4 * 360 + (((diff % 360) + 360) % 360);
    const ballOffset = (target - startBall) - diff;
    for (let tt = 0; tt <= SPIN_TICKS; tt += STEP) {
      const progress = tt / SPIN_TICKS;
      const w = startWheel + wheelTurn * easeOut(progress, 2);
      const b = startBall + (ballTurn + ballOffset) * easeOut(progress, 3);
      let radius, height = 0.045;
      if (progress < 0.7) radius = RL.BALL_TRACK;
      else {
        const k = (progress - 0.7) / 0.3;
        radius = RL.BALL_TRACK + (RL.BALL_POCKET - RL.BALL_TRACK) * Math.min(1, k * 1.3);
        if (k < 0.8) height += Math.abs(Math.sin(k * Math.PI * 4)) * 0.04 * (1 - k);
      }
      const pos = RL.aroundWheel(b, radius, height);
      sim.later(Math.max(1, tt), () => {
        wheel.interpolationDuration = STEP;
        wheel.setTransformation(wt(w));
        ball.teleport(at(pos));
      });
    }
    wheelAngle = endWheel % 360;
  }

  function payout() {
    state = "PAYING";
    history.unshift(RESULT);
    updateHistory();
    resultLabel.text([[numberParts(RESULT)]]);
    sparkle(at(RL.resultLabel()), 0xffffff);
    const totals = {};
    for (const i of [1, 2, 3, 4]) {
      for (const bet of bets[i]) {
        const win = bet.numbers.includes(RESULT);
        const pay = win ? bet.amount * (1 + PAYOUT[bet.type]) : 0;
        t.seats[i].chips += pay;
        totals[i] = totals[i] || [0, 0];
        totals[i][0] += bet.amount;
        totals[i][1] += pay;
        const view = chipViews[i][key(bet)];
        if (pay === 0) view.slideTo(at(RL.croupierChips));
        else {
          const w = new ChipView();
          w.show(at(RL.croupierChips), pay - bet.amount);
          payoutViews.push(w);
          const spot = offset(RL.spot(bet), i);
          sim.later(3, () => w.slideTo(at([spot[0] + 0.05, spot[1], spot[2] - 0.05])));
        }
      }
    }
    const [sum, back] = totals[YOU];
    const net = back - sum;
    showTitle([numberParts(RESULT)[0], numberParts(RESULT)[1]], [net > 0 ? "+" + net + " 枚" : net === 0 ? "±0" : net + " 枚", net > 0 ? C.GREEN : net === 0 ? C.YELLOW : C.RED], 40);
    chat([["結果 ", C.WHITE], numberParts(RESULT), ["：払い戻し " + back + " 枚（手元 " + t.seats[YOU].chips + " 枚）", C.GRAY]]);
    status();
    sim.later(80, finishRound);
  }

  function finishRound() {
    for (const i in chipViews) for (const k in chipViews[i]) chipViews[i][k].remove();
    payoutViews.forEach((v) => v.remove());
    state = "IDLE";
    status();
    t.broadcast([["次の回のベットを受け付けています", C.AQUA]]);
  }

  return { you: YOU, table: t };
}

// ================================================================ ポーカー

const PK = {
  SEAT: [[2.55, -0.2], [1.2, 1.55], [0.4, 1.55], [-0.4, 1.55], [-1.2, 1.55], [-2.55, -0.2]],
  shape: null,
  yawOffset: (seat) => seat === 0 ? -90 : seat === 5 ? 90 : 0,
  holeCard(seat, k) {
    const spread = (k - 0.5) * 0.12, y = TOP + 0.004 + 0.003 * k;
    if (seat === 0) return [1.55, y, -0.2 - spread];
    if (seat === 5) return [-1.55, y, -0.2 + spread];
    return [PK.SEAT[seat][0] + spread, y, 0.45];
  },
  bet(seat) {
    if (seat === 0 || seat === 5) return [seat === 0 ? 1.15 : -1.15, TOP + 0.001, 0.25];
    return [PK.SEAT[seat][0], TOP + 0.001, 0.1];
  },
  button(seat) {
    if (seat === 0 || seat === 5) return [seat === 0 ? 1.6 : -1.6, TOP + 0.01, 0.2];
    return [PK.SEAT[seat][0] + 0.26, TOP + 0.01, 0.6];
  },
  board: (i) => [-0.6 + 0.3 * i, TOP + 0.004, -0.2],
  pot: [0, TOP + 0.001, 0.2],
  seatLabel: (seat) => [PK.SEAT[seat][0], 1.75, PK.SEAT[seat][1]],
  deck: [0.3, TOP + 0.05, -0.75],
  potLabel: [0, TOP + 0.45, 0.2],
};
PK.shape = {
  model: "table/poker", dealer: [0, 0, -1.55], seats: PK.SEAT.map((s) => [s[0], 0, s[1]]),
  // 端の席はテーブルの方（横）を向いて座る
  seatYaw: (i) => (i === 0 ? 90 : i === 5 ? -90 : 180),
};

let pokerViewerIsOwner = true;

function pokerScenario() {
  const t = new Table(PK.shape);
  t.spawn();
  const YOU = 2;
  const SEATS = [0, 1, 2, 3, 5];
  const names = { 0: "Bot1", 1: "Bot2", 2: "あなた", 3: "Bot4", 5: "Bot6" };
  for (const i of SEATS) t.sit(i, names[i], 1000, i === YOU);
  const sb = 5, bb = 10;
  let state = "STARTING", countdown = 3, buttonSeat = 1, current = null;
  const shownStack = {}, shownBet = {}, lastAction = {}, folded = {};
  const holeViews = {}, betViews = {}, seatLabels = {};
  const boardViews = [];
  const payoutViews = [];
  const potView = new ChipView();
  let shownPot = 0, potLabel = null;

  const buttonDisplay = new ItemDisplay(at(PK.button(0)), "poker/button");
  const btr = transformation([0, 0, 0], null, 0, 0.3);
  buttonDisplay.tr = { from: btr, to: btr, start: 0, dur: 0 };
  buttonDisplay.teleportDuration = 8;

  const limits = [["ブラインド " + sb + "/" + bb + "（練習用）", C.GRAY]];
  function status() {
    if (state === "STARTING") t.setStatus([[["まもなく配ります（" + Math.max(countdown, 0) + " 秒）", C.AQUA]], limits]);
    else if (state === "IDLE") t.setStatus([[["あと 1 人そろうと始まります", C.AQUA]], limits]);
    else t.setStatus([[[current !== null ? t.seats[current].name + " の番（あと " + Math.max(countdown, 0) + " 秒）" : "勝負中", C.YELLOW]], limits]);
  }
  status();

  const player = new Player(play);
  const tick = () => {
    if (state === "STARTING") {
      if (--countdown <= 0) startHand();
      status();
    } else if (state === "PLAYING" && current !== null) {
      countdown--;
      if (current === YOU) actionbar("あなたの番です（あと " + Math.max(countdown, 0) + " 秒）");
      status();
    }
    sim.later(20, tick);
  };
  sim.later(20, tick);

  const A = (seat, action, added, streetBet, then) => ({ k: "Acted", seat, action, added, streetBet });
  const T = (seat, legal) => ({ k: "Turn", seat, legal });
  const L = (toCall, minRaiseTo, maxRaiseTo, streetBet, pot) => ({ toCall, canRaise: true, minRaiseTo, maxRaiseTo, streetBet, pot });
  const B = (street, cards) => ({ k: "Board", street, cards: cards.map(card) });
  // 1 回分の流れ（PokerRound が返すイベントと同じ並び）。Turn のあとに続くのが、その人の行動の結果
  const script = [
    { k: "ButtonMoved", seat: 1 },
    { k: "Blind", seat: 2, amount: 5, big: false }, { k: "Blind", seat: 3, amount: 10, big: true },
    { k: "HoleCards", seat: 0, first: card("C9"), second: card("D4") },
    { k: "HoleCards", seat: 1, first: card("CK"), second: card("DQ") },
    { k: "HoleCards", seat: 2, first: card("H7"), second: card("D7") },
    { k: "HoleCards", seat: 3, first: card("SJ"), second: card("S10") },
    { k: "HoleCards", seat: 5, first: card("HA"), second: card("C3") },
    T(5), A(5, "CALL", 10, 10),
    T(0), A(0, "FOLD", 0, 0),
    T(1), A(1, "RAISE", 30, 30),
    T(2, L(25, 50, 1000, 5, 55)), A(2, "CALL", 25, 30),
    T(3), A(3, "CALL", 20, 30),
    T(5), A(5, "CALL", 20, 30),
    { k: "BetsCollected", pot: 120 },
    B("フロップ", ["HK", "C7", "D2"]),
    T(2, L(0, 10, 970, 0, 120)), A(2, "CHECK", 0, 0),
    T(3), A(3, "BET", 40, 40),
    T(5), A(5, "FOLD", 0, 0),
    T(1), A(1, "CALL", 40, 40),
    T(2, L(40, 80, 970, 0, 200)), A(2, "RAISE", 120, 120),
    T(3), A(3, "CALL", 80, 120),
    T(1), A(1, "CALL", 80, 120),
    { k: "BetsCollected", pot: 480 },
    B("ターン", ["S9"]),
    T(2, L(0, 10, 850, 0, 480)), A(2, "BET", 200, 200),
    T(3), A(3, "FOLD", 0, 0),
    T(1), A(1, "CALL", 200, 200),
    { k: "BetsCollected", pot: 880 },
    B("リバー", ["C2"]),
    T(2, L(0, 10, 650, 0, 880)), A(2, "CHECK", 0, 0),
    T(1), A(1, "CHECK", 0, 0),
    { k: "Shown", seat: 2, first: card("H7"), second: card("D7"), value: "フルハウス" },
    { k: "Shown", seat: 1, first: card("CK"), second: card("DQ"), value: "ツーペア" },
    { k: "Won", seat: 2, amount: 880, pot: 0, value: "フルハウス" },
    { k: "HandOver" },
  ];
  const YOUR_CHOICE = { 0: "コール", 1: "チェック", 2: "レイズ（合計 120）", 3: "ベット 200", 4: "チェック" };
  let yourTurns = 0;

  function startHand() {
    state = "PLAYING";
    for (const i of SEATS) {
      shownStack[i] = 1000;
      shownBet[i] = 0;
      lastAction[i] = "";
      folded[i] = false;
      holeViews[i] = [];
      betViews[i] = new ChipView();
      seatLabels[i] = t.label(PK.seatLabel(i), 0.6);
      updateSeatLabel(i);
    }
    potLabel = t.label(PK.potLabel, 0.6);
    t.broadcast([["新しい勝負を配ります（5 人）", C.AQUA]]);
    player.enqueue(script);
  }

  function updateSeatLabel(i) {
    const label = seatLabels[i];
    if (!label) return;
    const turn = current === i;
    const lines = [[[(i === buttonSeat ? "Ⓓ " : "") + t.seats[i].name, turn ? C.YELLOW : folded[i] ? C.DARK_GRAY : C.WHITE]], [[shownStack[i] + " 枚", C.GOLD]]];
    if (lastAction[i]) lines.push([[lastAction[i], folded[i] ? C.DARK_GRAY : C.AQUA]]);
    label.text(lines);
  }
  function updatePotLabel() {
    let total = shownPot;
    for (const i of SEATS) total += shownBet[i] || 0;
    potLabel.text(total > 0 ? [[["ポット " + total, C.GOLD]]] : []);
  }
  function showBet(i) {
    betViews[i].show(at(PK.bet(i)), shownBet[i]);
  }
  function dealHole(i, k, c) {
    const p = PK.holeCard(i, k), yaw = PK.yawOffset(i);
    holeViews[i].push(PrivateCardView.deal(at(p, yaw), delta(PK.deck, p, yaw), c.code, () => i === YOU && pokerViewerIsOwner));
  }

  function play(e) {
    switch (e.k) {
      case "ButtonMoved":
        buttonSeat = e.seat;
        buttonDisplay.teleport(at(PK.button(e.seat)));
        return 8;
      case "Blind":
        shownStack[e.seat] -= e.amount;
        shownBet[e.seat] = e.amount;
        lastAction[e.seat] = (e.big ? "BB " : "SB ") + e.amount;
        showBet(e.seat);
        updateSeatLabel(e.seat);
        return 6;
      case "HoleCards":
        dealHole(e.seat, 0, e.first);
        sim.later(4, () => dealHole(e.seat, 1, e.second));
        t.swing();
        if (e.seat === YOU) chat([["あなたの手札: ", C.WHITE], cardText(e.first), [" "], cardText(e.second)]);
        return 9;
      case "Turn":
        startTurn(e);
        return 0;
      case "Acted":
        acted(e);
        return 8;
      case "BetsCollected": {
        const moving = [];
        for (const i of SEATS) {
          if (betViews[i].amount > 0) {
            betViews[i].slideTo(at(PK.pot));
            moving.push(betViews[i]);
            payoutViews.push(betViews[i]);
            betViews[i] = new ChipView();
          }
          shownBet[i] = 0;
        }
        shownPot = e.pot;
        sim.later(9, () => { moving.forEach((v) => v.remove()); potView.show(at(PK.pot), shownPot); });
        updatePotLabel();
        return 12;
      }
      case "Board": {
        const first = boardViews.length;
        e.cards.forEach((c, k) => {
          sim.later(1 + k * 6, () => {
            const p = PK.board(first + k);
            boardViews.push(CardView.deal(at(p), delta(PK.deck, p), c.code, true));
          });
        });
        t.swing();
        for (const i of SEATS) {
          if (!folded[i] && !lastAction[i].startsWith("オールイン")) { lastAction[i] = ""; updateSeatLabel(i); }
        }
        const line = [[e.street + ": ", C.AQUA]];
        e.cards.forEach((c) => line.push(cardText(c), [" "]));
        t.broadcast(line);
        return 8 + e.cards.length * 6;
      }
      case "Shown":
        holeViews[e.seat].forEach((v) => v.reveal());
        lastAction[e.seat] = e.value;
        updateSeatLabel(e.seat);
        t.broadcast([[t.seats[e.seat].name + ": ", C.WHITE], cardText(e.first), [" "], cardText(e.second), [" " + e.value, C.GOLD]]);
        return 16;
      case "Won": {
        shownPot -= e.amount;
        shownStack[e.seat] += e.amount;
        potView.show(at(PK.pot), Math.max(shownPot, 0));
        const win = new ChipView();
        win.show(at(PK.pot), e.amount);
        payoutViews.push(win);
        const target = at(PK.bet(e.seat));
        sim.later(2, () => win.slideTo(target));
        lastAction[e.seat] = e.value + " +" + e.amount;
        updateSeatLabel(e.seat);
        updatePotLabel();
        t.broadcast([[t.seats[e.seat].name + " がポット " + e.amount + " 枚を獲得（" + e.value + "）", C.GREEN]]);
        if (e.seat === YOU) showTitle(["獲得 +" + e.amount + " 枚", C.GREEN], [e.value, C.GOLD], 36);
        sparkle(at(PK.seatLabel(e.seat)), 0x7cff6b);
        return 24;
      }
      case "HandOver":
        sim.later(70, finishHand);
        return 0;
    }
    return 0;
  }

  function acted(e) {
    const i = e.seat;
    shownStack[i] -= e.added;
    shownBet[i] = e.streetBet;
    const text = { FOLD: "フォールド", CHECK: "チェック", CALL: "コール " + e.added, BET: "ベット " + e.streetBet, RAISE: "レイズ " + e.streetBet }[e.action];
    lastAction[i] = text;
    if (e.action === "FOLD") {
      folded[i] = true;
      holeViews[i].forEach((v) => v.muck(at(PK.deck)));
      holeViews[i] = [];
    } else if (e.action !== "CHECK") {
      showBet(i);
    }
    current = null;
    for (const s of SEATS) updateSeatLabel(s);
    updatePotLabel();
    t.broadcast([[t.seats[i].name + ": " + text, C.GRAY]]);
  }

  function startTurn(e) {
    player.waiting = true;
    current = e.seat;
    countdown = 25;
    status();
    for (const s of SEATS) updateSeatLabel(s);
    const go = () => { player.waiting = false; player.pump(); };
    if (e.seat === YOU) {
      const l = e.legal;
      showTitle(["あなたの番", C.YELLOW], [l.toCall === 0 ? "チェックできます" : "コール " + l.toCall + " 枚", C.WHITE], 24);
      const line = [["ポット " + l.pot + " 枚： ", C.WHITE]];
      if (l.toCall > 0) line.push(button("フォールド", C.RED), [" "], button("コール " + l.toCall, C.GREEN), [" "]);
      else line.push(button("チェック", C.GREEN), [" "]);
      const verb = l.toCall === 0 && l.streetBet === 0 ? "ベット" : "レイズ";
      line.push(button(verb + " " + l.minRaiseTo, C.GOLD), [" "], button("金額を選ぶ", C.YELLOW), [" "], button("オールイン", C.DARK_RED));
      chat(line);
      const choice = YOUR_CHOICE[yourTurns++];
      sim.later(40, () => { chat([["（あなたは " + choice + " を選んだ）", C.DARK_GRAY]]); go(); });
    } else {
      sim.later(12 + ((e.seat * 7) % 10), go);
    }
  }

  function finishHand() {
    for (const i of SEATS) {
      holeViews[i].forEach((v) => v.remove());
      betViews[i].remove();
      seatLabels[i].remove();
      seatLabels[i] = null;
    }
    boardViews.forEach((v) => v.remove());
    potView.remove();
    payoutViews.forEach((v) => v.remove());
    potLabel.remove();
    state = "STARTING";
    countdown = 5;
    current = null;
    status();
    t.broadcast([["5 秒後に次の勝負を配ります", C.GRAY]]);
  }

  return { you: YOU, table: t };
}


// ================================================================ スロット（SlotMachine と同じ動き）

const SLOT = {
  AXIS_Y: 0.5 + (21 - 8) / 16,
  REEL_X: [(3.9 - 8) / 16, 0, (12.1 - 8) / 16],
  STRIPS: [
    ["SEVEN", "CHERRY", "LEMON", "BELL", "MELON", "BAR", "LEMON", "CHERRY", "BELL", "MELON", "LEMON", "BAR", "CHERRY", "MELON", "BELL", "LEMON"],
    ["SEVEN", "LEMON", "MELON", "CHERRY", "BELL", "BAR", "LEMON", "MELON", "CHERRY", "BELL", "LEMON", "BAR", "MELON", "CHERRY", "LEMON", "BELL"],
    ["SEVEN", "BELL", "LEMON", "MELON", "CHERRY", "BAR", "LEMON", "BELL", "MELON", "LEMON", "BAR", "CHERRY", "BELL", "LEMON", "MELON", "LEMON"],
  ],
  NAMES: { SEVEN: "7", BAR: "BAR", BELL: "ベル", MELON: "スイカ", LEMON: "レモン", CHERRY: "チェリー" },
  PAY: { SEVEN: 300, BAR: 50, BELL: 20, MELON: 15, LEMON: 10, C1: 1, C2: 4, C3: 20 },
  /** Line と同じ（リールごとの段: 上 +1、中央 0、下 −1）。 */
  LINES: [
    { name: "上の段", rows: [1, 1, 1] },
    { name: "中央の段", rows: [0, 0, 0] },
    { name: "下の段", rows: [-1, -1, -1] },
    { name: "右下がりの斜め", rows: [1, 0, -1] },
    { name: "右上がりの斜め", rows: [-1, 0, 1] },
  ],
  ROW: 2.4 / 16, LINE_Z: (14.55 - 8) / 16, DIAGONAL_DEGREES: Math.atan2(2.4, 12.1 - 8) / DEG,
  STEP: 2, SPEED: 67.5, FACE: 360 / 16, STOP_AT: [32, 44, 56], REACH_EXTRA: 26, OVERSHOOT: 6, BLUR_SPEED: 25,
  shape: { model: "table/slot", seats: [[0, 0, 1.0]], dealer: null, status: [0, 2.04, 0.32], statusScale: 0.4, modelAt: [0, 0.5, 0], modelScale: 1, modelYaw: 180 },
};

/** Paytable.evaluate と同じ。 */
function slotEvaluate(line) {
  const [a, b, c] = line;
  if (a === "CHERRY") {
    const n = b === "CHERRY" ? (c === "CHERRY" ? 3 : 2) : 1;
    return { times: SLOT.PAY["C" + n], name: "チェリー " + n + " つ", big: n === 3 };
  }
  if (a === b && b === c) return { times: SLOT.PAY[a], name: SLOT.NAMES[a] + " が 3 つ", big: a === "SEVEN" || a === "BAR" };
  return { times: 0, name: "", big: false };
}

function slotSymbols(line, stops) {
  return stops.map((s, i) => SLOT.STRIPS[i][(((s + line.rows[i]) % 16) + 16) % 16]);
}

/** Spin.of と同じ（賭け金を 5 ラインに等分し、当たったラインの分を足す）。 */
function slotSpin(stops, bet) {
  const lineBet = bet / SLOT.LINES.length;
  const wins = [];
  let payout = 0;
  SLOT.LINES.forEach((line, i) => {
    const win = slotEvaluate(slotSymbols(line, stops));
    if (win.times > 0) { wins.push({ line: i, win }); payout += lineBet * win.times; }
  });
  let best = null;
  for (const w of wins) if (!best || w.win.times > best.win.times) best = w;
  return { wins, payout, best, big: wins.some((w) => w.win.big) };
}

/** Spin.reachLines と同じ。 */
function slotReachLines(stops) {
  const out = [];
  SLOT.LINES.forEach((line, i) => {
    const [a, b] = slotSymbols(line, stops);
    if (a === b && (a === "SEVEN" || a === "BAR")) out.push(i);
  });
  return out;
}

/** SlotMachine.lineTransform と同じ。 */
function slotLineTransform(index, shown) {
  const line = SLOT.LINES[index];
  const s = shown ? 1 : 0;
  const slope = line.rows[0] - line.rows[2];
  if (slope === 0) return transformation([0, line.rows[0] * SLOT.ROW, 0], null, 0, s);
  const q = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 0, 1), SLOT.DIAGONAL_DEGREES * Math.sign(slope) * DEG);
  const pivot = new THREE.Vector3(0, SLOT.AXIS_Y - 0.5, -SLOT.LINE_Z);
  const t = pivot.clone().sub(pivot.clone().applyQuaternion(q));
  return { t: [t.x, t.y, t.z], q, s: [s, s, s] };
}

/** SlotMachine.reelPath と同じ。 */
function slotReelPath(start, stop, end) {
  const windup = 2, accel = 4, decel = 14, V = SLOT.SPEED;
  const main = end - 4 - windup;
  const cruise = Math.max(0, main - accel - decel);
  const nominal = V * accel / 2 + V * cruise + V * decel / 3;
  const base = start - 8;
  const target = (((stop % 16) + 16) % 16) * SLOT.FACE;
  let offset = target - (base + nominal);
  offset = ((offset % 360) + 540) % 360 - 180;
  const travel = nominal + offset;
  const scale = (travel + SLOT.OVERSHOOT) / nominal;
  const path = [];
  for (let t = 0; t <= end; t++) {
    if (t <= windup) path.push(start - 8 * t / windup);
    else if (t <= windup + main) {
      const tau = t - windup;
      let f;
      if (tau < accel) f = V * tau * tau / (2 * accel);
      else if (tau < accel + cruise) f = V * accel / 2 + V * (tau - accel);
      else {
        const u = Math.min(1, (tau - accel - cruise) / decel);
        f = V * accel / 2 + V * cruise + V * decel / 3 * (1 - Math.pow(1 - u, 3));
      }
      path.push(base + scale * f);
    } else {
      const u = (t - windup - main) / 4;
      path.push(base + travel + SLOT.OVERSHOOT * (1 - u * u * (3 - 2 * u)));
    }
  }
  return path;
}

function slotScenario() {
  const t = new Table(SLOT.shape);
  t.spawn();
  const YOU = 0;
  t.sit(YOU, "あなた", 1000, true);
  const reelTr = (deg) => transformation([0, 0, 0], [1, 0, 0], -deg, 1);
  const scaled = (s) => transformation([0, 0, 0], null, 0, s);
  const reelAngle = [3 * SLOT.FACE, 9 * SLOT.FACE, 13 * SLOT.FACE];
  const blurred = [false, false, false];
  const reels = [0, 1, 2].map((i) => {
    const r = new ItemDisplay(at([SLOT.REEL_X[i], SLOT.AXIS_Y, 0], 180), "slot/reel" + (i + 1));
    r.tr = { from: reelTr(reelAngle[i]), to: reelTr(reelAngle[i]), start: 0, dur: 0 };
    return r;
  });
  const leverTr = (deg) => transformation([0, 0, 0], [1, 0, 0], -deg, 1);
  const lever = new ItemDisplay(at([0.56, 1.0, 0.05], 180), "slot/lever");
  const lamps = new ItemDisplay(at([0, 0.5, 0], 180), "slot/lamps_a");
  const winlines = SLOT.LINES.map((_, i) => {
    const band = new ItemDisplay(at([0, 0.5, 0], 180), "slot/winline");
    band.tr = { from: slotLineTransform(i, false), to: slotLineTransform(i, false), start: 0, dur: 0 };
    return band;
  });
  const showLines = (lines, on) => lines.forEach((i) => { winlines[i].interpolationDuration = 0; winlines[i].setTransformation(slotLineTransform(i, on)); });
  const hideLines = () => showLines([0, 1, 2, 3, 4], false);
  const resultLabel = t.label([0, 2.42, 0.32], 0.4);
  let state = "IDLE", bet = 10, celebrating = false, lampPhase = false;
  const shower = [];
  const seat = t.seats[YOU];
  function status() {
    t.setStatus([[[seat.name, C.AQUA]], [["1 回 " + bet + " 枚", C.GRAY]]]);
  }
  status();
  function lampTick() {
    lampPhase = !lampPhase;
    lamps.setItem(lampPhase ? "slot/lamps_b" : "slot/lamps_a");
    sim.later(celebrating ? 2 : state === "SPINNING" ? 5 : 16, lampTick);
  }
  lampTick();

  function pullLever() {
    lever.interpolationDuration = 3;
    lever.setTransformation(leverTr(55));
    sim.later(4, () => { lever.interpolationDuration = 7; lever.setTransformation(leverTr(0)); });
  }

  function spin(stops) {
    seat.chips -= bet;
    state = "SPINNING";
    resultLabel.text([]);
    hideLines();
    status();
    chat([["（あなたはレバーを引いた）", C.DARK_GRAY]]);
    pullLever();
    const reachLines = slotReachLines(stops);
    const reach = reachLines.length > 0;
    const ends = SLOT.STOP_AT.slice();
    if (reach) ends[2] += SLOT.REACH_EXTRA;
    const paths = [0, 1, 2].map((i) => slotReelPath(reelAngle[i], stops[i], ends[i]));
    stops.forEach((s, i) => { reelAngle[i] = s * SLOT.FACE; });
    const lastTick = ends[2];
    for (let tk = SLOT.STEP; tk <= lastTick; tk += SLOT.STEP) {
      const tick = tk;
      sim.later(tick, () => {
        for (let i = 0; i < 3; i++) {
          const path = paths[i];
          if (tick >= path.length) continue;
          const speed = Math.abs(path[tick] - path[tick - SLOT.STEP]) / SLOT.STEP;
          const blur = speed > SLOT.BLUR_SPEED;
          if (blur !== blurred[i]) { blurred[i] = blur; reels[i].setItem("slot/reel" + (i + 1) + (blur ? "_blur" : "")); }
          reels[i].interpolationDuration = SLOT.STEP;
          reels[i].setTransformation(reelTr(path[tick]));
        }
      });
    }
    if (reach) {
      sim.later(ends[1], () => { celebrating = true; actionbar("リーチ！"); });
      for (let tk = ends[1]; tk < ends[2] - 2; tk += 6) {
        const on = ((tk - ends[1]) / 6) % 2 === 0;
        sim.later(tk, () => showLines(reachLines, on));
      }
      sim.later(ends[2] - 2, hideLines);
    }
    sim.later(lastTick + 4, () => reveal(stops));
  }

  function blinkLines(lines, ticks) {
    for (let tk = 0; tk < ticks; tk += 4) {
      const on = (tk / 4) % 2 === 0;
      sim.later(Math.max(1, tk), () => showLines(lines, on));
    }
  }

  function chipShower(count) {
    const models = ["chip/c10_1", "chip/c50_1", "chip/c100_1", "chip/c500_1", "chip/c1000_1"];
    const rnd = seeded(99);
    for (let n = 0; n < count; n++) {
      const p = [0, 0.3, 0.5];
      const v = [(rnd.nextInt(1000) / 1000 - 0.5) * 0.12, 0.16 + rnd.nextInt(1000) / 1000 * 0.1, 0.05 + rnd.nextInt(1000) / 1000 * 0.07];
      const axis = rnd.nextInt(1000) / 1000;
      const model = models[rnd.nextInt(models.length)];
      sim.later(Math.max(1, n * 2), () => {
        const chip = new ItemDisplay(at(p), model);
        chip.teleportDuration = 2;
        chip.interpolationDuration = 2;
        chip.tr = { from: scaled(0.5), to: scaled(0.5), start: 0, dur: 0 };
        shower.push(chip);
        for (let tk = 2; tk <= 26; tk += 2) {
          const tick = tk;
          sim.later(tick, () => {
            chip.teleport(at([p[0] + v[0] * tick, p[1] + v[1] * tick - 0.012 * tick * tick, p[2] + v[2] * tick]));
            const q = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(axis, 1 - axis, 0.3).normalize(), tick * 0.6);
            chip.setTransformation({ t: [0, 0, 0], q, s: [0.5, 0.5, 0.5] });
          });
        }
        sim.later(28, () => chip.remove());
      });
    }
  }

  function reveal(stops) {
    state = "SHOWING";
    const result = slotSpin(stops, bet);
    const payout = result.payout;
    const lines = result.wins.map((w) => w.line);
    seat.chips += payout;
    let wait;
    if (!result.best) {
      celebrating = false;
      resultLabel.text([[["はずれ", C.GRAY]]]);
      wait = 12;
    } else if (!result.big) {
      celebrating = true;
      resultLabel.text([[["+" + payout + " 枚", C.GOLD]]]);
      blinkLines(lines, 32);
      wait = 36;
    } else {
      celebrating = true;
      resultLabel.text([[["大当たり！ +" + payout + " 枚", C.GOLD]]]);
      blinkLines(lines, 70);
      chipShower(result.best.win.times >= 100 ? 18 : 10);
      showTitle(["大当たり！", C.GOLD], [result.best.win.name + (lines.length > 1 ? "（" + lines.length + " ライン）" : "") + "  +" + payout + " 枚", C.YELLOW], 36);
      wait = 76;
    }
    if (!result.best) {
      chat([["はずれ", C.GRAY]]);
    } else {
      const net = payout - bet;
      chat([[(result.big ? "大当たり" : "当たり") + "  " + payout + " 枚もどり", C.GOLD], ["（差し引き " + (net >= 0 ? "+" : "−") + Math.abs(net) + " 枚）", net >= 0 ? C.GREEN : C.GRAY]]);
      const lineBet = bet / SLOT.LINES.length;
      for (const w of result.wins) chat([["  " + SLOT.LINES[w.line].name + "：" + w.win.name, C.YELLOW], ["  +" + lineBet * w.win.times + " 枚", C.GRAY]]);
    }
    sim.later(wait, () => {
      shower.forEach((c) => c.remove());
      celebrating = false;
      hideLines();
      state = "IDLE";
      status();
    });
  }

  // 台本: はずれ → 右下がりの斜めでベル 3 つ（ほかにチェリーの 2 ライン）→ 賭け金を 50 にして右上がりの斜めで 7 7 7（リーチから大当たり）
  sim.later(20, () => spin([3, 0, 0]));
  sim.later(112, () => spin([2, 4, 2]));
  sim.later(214, () => { bet = 50; status(); chat([["（あなたは賭け金を 1 回 50 枚にした）", C.DARK_GRAY]]); });
  sim.later(236, () => spin([1, 0, 15]));
  return { you: YOU, table: t, camera: slotCamera };
}

function slotCamera(kind) {
  if (kind === "seat") {
    camera.position.set(0, 1.48, 1.0);
    controls.target.set(0, 1.25, 0);
  } else if (kind === "high") {
    camera.position.set(1.5, 2.3, 2.6);
    controls.target.set(0, 1.15, 0);
  } else {
    camera.position.set(0.0, 1.32, 1.35);
    controls.target.set(0, 1.3, 0);
  }
}

// ================================================================ 画面の操作

const GAMES = {
  blackjack: { make: blackjackScenario, length: 520 },
  roulette: { make: rouletteScenario, length: 480 },
  poker: { make: pokerScenario, length: 1500 },
  slot: { make: slotScenario, length: 420 },
};
let game = "blackjack";
let playing = true;
let speed = 1;
let current = null;

function cameraPreset(kind) {
  const t = current.table;
  const seat = t.shape.seats[current.you];
  controls.enabled = true;
  (t.youParts || []).forEach((o) => { o.visible = kind !== "seat"; });
  if (current.camera) {
    current.camera(kind);
  } else if (kind === "seat") {
    camera.position.set(seat[0], 1.48, seat[2] - 0.05);
    controls.target.set(seat[0] * 0.35, TOP - 0.1, -0.5);
  } else if (kind === "high") {
    camera.position.set(0.6, 3.3, 4.4);
    controls.target.set(0, TOP, 0);
  } else {
    camera.position.set(0, 5.2, 0.01);
    controls.target.set(0, TOP, 0);
  }
  controls.update();
}

function start(name) {
  if (sim) sim.dispose();
  game = name;
  chatEl.querySelectorAll(".line").forEach((n) => n.remove());
  titleEl.hidden = true;
  actionbarEl.textContent = "";
  sim = new Sim();
  buildWorld(sim);
  current = GAMES[name].make();
  sim.duration = GAMES[name].length;
  cameraPreset(document.getElementById("camera").value);
  document.querySelectorAll(".tabs button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.game === name)));
  setPlaying(true);
}

function setPlaying(p) {
  playing = p;
  document.getElementById("play").textContent = p ? "一時停止" : "再生";
}

document.querySelectorAll(".tabs button").forEach((b) => b.addEventListener("click", () => start(b.dataset.game)));
document.getElementById("play").addEventListener("click", () => setPlaying(!playing));
document.getElementById("restart").addEventListener("click", () => start(game));
document.getElementById("speed").addEventListener("change", (e) => { speed = Number(e.target.value); });
document.getElementById("camera").addEventListener("change", (e) => cameraPreset(e.target.value));

let last = performance.now();
function frame(nowMs) {
  const dt = Math.min(100, nowMs - last);
  last = nowMs;
  if (sim && playing) {
    sim.advance((dt / 50) * speed);
    if (sim.now >= sim.duration) setPlaying(false);
  }
  if (sim) {
    sim.render();
    const sec = (sim.now / 20).toFixed(1);
    hudEl.textContent = Math.floor(sim.now) + " tick（" + sec + " 秒）";
    document.getElementById("bar").style.width = Math.min(100, (sim.now / sim.duration) * 100) + "%";
    if (sim.now > titleUntil) titleEl.hidden = true;
    if (sim.now > actionbarUntil) actionbarEl.textContent = "";
  }
  controls.update();
  renderer.render(sim ? sim.scene : new THREE.Scene(), camera);
  requestAnimationFrame(frame);
}

(document.fonts && document.fonts.load ? document.fonts.load("36px DotGothic16") : Promise.resolve()).catch(() => {}).then(() => {
  document.getElementById("loading").remove();
  resize();
  start(game);
  requestAnimationFrame(frame);
});
