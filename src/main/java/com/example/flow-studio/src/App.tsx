// @ts-nocheck
import { useState, useRef, useEffect, useCallback } from "react";

const NODE_TYPES = {
  START: { shape: "pill", colorA: "#34d399", colorB: "#065f46", w: 120, h: 44, label: "Inicio" },
  END: { shape: "pill", colorA: "#f87171", colorB: "#7f1d1d", w: 120, h: 44, label: "Fin" },
  PROCESS: { shape: "rect", colorA: "#60a5fa", colorB: "#1e3a8a", w: 150, h: 54, label: "Proceso" },
  DECISION: { shape: "diamond", colorA: "#fbbf24", colorB: "#78350f", w: 144, h: 90, label: "Condición" },
  IO: { shape: "parallelogram", colorA: "#c084fc", colorB: "#3b0764", w: 150, h: 54, label: "E/S" },
  LOOP: { shape: "hexagon", colorA: "#22d3ee", colorB: "#0c4a6e", w: 150, h: 54, label: "Bucle" },
};

const SNAP = 8;
const uid = () => `n${Math.random().toString(36).slice(2, 9)}`;

const hitRect = (px, py, cx, cy, w, h) =>
  Math.abs(px - cx) <= w / 2 && Math.abs(py - cy) <= h / 2;
const dist2p = (x1, y1, x2, y2) => Math.hypot(x2 - x1, y2 - y1);
const distSeg = (p, a, b) => {
  const l2 = (b.x - a.x) ** 2 + (b.y - a.y) ** 2;
  if (l2 === 0) return dist2p(p.x, p.y, a.x, a.y);
  const t = Math.max(0, Math.min(1, ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2));
  return dist2p(p.x, p.y, a.x + t * (b.x - a.x), a.y + t * (b.y - a.y));
};

const PORT_POS = (node, port) => {
  const t = NODE_TYPES[node.type];
  if (port === "top") return { x: node.x, y: node.y - t.h / 2 };
  if (port === "bottom") return { x: node.x, y: node.y + t.h / 2 };
  if (port === "left") return { x: node.x - t.w / 2, y: node.y };
  if (port === "right") return { x: node.x + t.w / 2, y: node.y };
  return { x: node.x, y: node.y + t.h / 2 };
};
const PORTS = ["top", "bottom", "left", "right"];
const getNearestPort = (node, wx, wy) => {
  let best = "bottom", minDist = Infinity;
  for (const p of PORTS) {
    const pos = PORT_POS(node, p);
    const d = dist2p(wx, wy, pos.x, pos.y);
    if (d < minDist) { minDist = d; best = p; }
  }
  return best;
};

const getEdgeGeom = (edge, n1, n2) => {
  const sPort = edge.sourcePort ?? "bottom";
  const tPort = edge.targetPort ?? "top";
  const src = PORT_POS(n1, sPort);
  const tgt = PORT_POS(n2, tPort);
  const { x: sx, y: sy } = src;
  const { x: ex, y: ey } = tgt;

  const isLoop = sy >= ey - 20;
  let pts = [], dot = null, labelPos = { x: 0, y: 0 };

  if (isLoop) {
    const outX = sPort === "left" || sPort === "right" ? sx : n1.x - NODE_TYPES[n1.type].w / 2;
    const sideX = Math.min(outX, ex - NODE_TYPES[n2.type].w / 2) - 52;
    const topOff = edge.jointOffset ?? 40;
    pts = [
      [sx, sy], [outX, sy], [sideX, sy],
      [sideX, ey - topOff], [ex, ey - topOff], [ex, ey],
    ];
    dot = { x: ex, y: ey - topOff };
    labelPos = { x: sideX - 8, y: (sy + ey - topOff) / 2 };
  } else {
    const midY = (sy + ey) / 2 + (edge.jointOffset ?? 0);
    if (sPort === "left" || sPort === "right") {
      const sdX = sPort === "right" ? Math.max(sx + 32, ex) : Math.min(sx - 32, ex);
      pts = [[sx, sy], [sdX, sy], [sdX, midY], [ex, midY], [ex, ey]];
      dot = { x: ex, y: midY };
      labelPos = { x: sdX, y: (sy + midY) / 2 };
    } else if (tPort === "left" || tPort === "right") {
      const sdX = tPort === "right" ? Math.max(sx + 32, ex) : Math.min(sx - 32, ex);
      pts = [[sx, sy], [sx, midY], [sdX, midY], [sdX, ey], [ex, ey]];
      dot = { x: sdX, y: midY };
      labelPos = { x: sdX, y: (sy + midY) / 2 };
    } else {
      pts = [[sx, sy], [sx, midY], [ex, midY], [ex, ey]];
      dot = { x: ex, y: midY };
      labelPos = { x: (sx + ex) / 2, y: midY };
    }
  }
  return { pts, dot, labelPos };
};

const tracePath = (ctx, shape, w, h) => {
  const o = 18, p = 20;
  if (shape === "rect") ctx.roundRect(-w / 2, -h / 2, w, h, 6);
  else if (shape === "pill") ctx.roundRect(-w / 2, -h / 2, w, h, h / 2);
  else if (shape === "diamond") {
    ctx.moveTo(0, -h / 2); ctx.lineTo(w / 2, 0); ctx.lineTo(0, h / 2); ctx.lineTo(-w / 2, 0);
  } else if (shape === "parallelogram") {
    ctx.moveTo(-w / 2 + o, -h / 2); ctx.lineTo(w / 2 + o, -h / 2);
    ctx.lineTo(w / 2 - o, h / 2); ctx.lineTo(-w / 2 - o, h / 2);
  } else if (shape === "hexagon") {
    ctx.moveTo(-w / 2 + p, -h / 2); ctx.lineTo(w / 2 - p, -h / 2);
    ctx.lineTo(w / 2, 0); ctx.lineTo(w / 2 - p, h / 2);
    ctx.lineTo(-w / 2 + p, h / 2); ctx.lineTo(-w / 2, 0);
  }
};

const drawPorts = (ctx, node, isSelected) => {
  const r = 4;
  for (const p of PORTS) {
    const pos = PORT_POS(node, p);
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, r, 0, Math.PI * 2);
    ctx.fillStyle = isSelected ? "rgba(255,255,255,0.35)" : "rgba(255,255,255,0.12)";
    ctx.fill();
    ctx.strokeStyle = isSelected ? "rgba(255,255,255,0.6)" : "rgba(255,255,255,0.25)";
    ctx.lineWidth = 1;
    ctx.stroke();
  }
};

const drawNode = (ctx, node, isSelected, showPorts) => {
  const { w, h, shape, colorA, colorB } = NODE_TYPES[node.type];
  ctx.save();
  ctx.translate(node.x, node.y);
  const grd = ctx.createLinearGradient(0, -h / 2, 0, h / 2);
  grd.addColorStop(0, colorA + "bb");
  grd.addColorStop(1, colorB + "dd");
  ctx.beginPath();
  tracePath(ctx, shape, w, h);
  ctx.closePath();
  ctx.fillStyle = grd;
  ctx.fill();
  ctx.lineWidth = isSelected ? 2.5 : 1.5;
  ctx.strokeStyle = isSelected ? "rgba(255,255,255,0.92)" : colorA + "cc";
  if (isSelected) { ctx.shadowColor = colorA; ctx.shadowBlur = 18; }
  ctx.stroke();
  ctx.shadowBlur = 0;
  if (shape === "rect" || shape === "pill") {
    ctx.beginPath();
    ctx.roundRect(-w / 2 + 5, -h / 2 + 4, w - 10, 5, 2);
    ctx.fillStyle = "rgba(255,255,255,0.10)";
    ctx.fill();
  }
  ctx.fillStyle = "rgba(241,245,249,0.96)";
  ctx.textAlign = "center"; ctx.textBaseline = "middle";
  ctx.font = '600 12px "Fira Code","JetBrains Mono",monospace';
  ctx.fillText(node.label.length > 18 ? node.label.slice(0, 16) + "…" : node.label, 0, 1);
  ctx.restore();
  if (showPorts) drawPorts(ctx, node, isSelected);
};

const drawArrow = (ctx, p1, p2, color) => {
  const a = Math.atan2(p2[1] - p1[1], p2[0] - p1[0]), sz = 7;
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(p2[0], p2[1]);
  ctx.lineTo(p2[0] - sz * Math.cos(a - Math.PI / 6), p2[1] - sz * Math.sin(a - Math.PI / 6));
  ctx.lineTo(p2[0] - sz * Math.cos(a + Math.PI / 6), p2[1] - sz * Math.sin(a + Math.PI / 6));
  ctx.closePath();
  ctx.fill();
};

const drawEdge = (ctx, edge, n1, n2, isSelected, isEditing) => {
  const { pts, dot, labelPos } = getEdgeGeom(edge, n1, n2);
  const isLoop = n1.y >= n2.y - 20;
  const color = isSelected ? "#60a5fa" : isLoop ? "#22d3ee99" : "#3a5472";
  ctx.lineWidth = isSelected ? 2.5 : 1.8;
  ctx.strokeStyle = color;
  if (isLoop && !isSelected) ctx.setLineDash([6, 4]);
  if (isSelected) { ctx.shadowColor = "#60a5fa55"; ctx.shadowBlur = 10; }
  ctx.beginPath();
  ctx.moveTo(pts[0][0], pts[0][1]);
  for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1]);
  ctx.stroke();
  ctx.shadowBlur = 0;
  ctx.setLineDash([]);
  drawArrow(ctx, pts[pts.length - 2], pts[pts.length - 1], color);
  if (edge.label && !isEditing) {
    ctx.save();
    ctx.translate(labelPos.x, labelPos.y);
    const lw = Math.max(36, ctx.measureText(edge.label).width + 16);
    ctx.beginPath();
    ctx.roundRect(-lw / 2, -10, lw, 20, 4);
    ctx.fillStyle = isSelected ? "#1a3460" : "#07111f";
    ctx.strokeStyle = isSelected ? "#60a5fa" : "#243c5e";
    ctx.lineWidth = 1.5;
    ctx.fill(); ctx.stroke();
    ctx.fillStyle = isSelected ? "#bfdbfe" : "#6b8fb5";
    ctx.textAlign = "center"; ctx.textBaseline = "middle";
    ctx.font = '700 10.5px "Fira Code","JetBrains Mono",monospace';
    ctx.fillText(edge.label, 0, 0);
    ctx.restore();
  }
  if (dot && isSelected) {
    ctx.beginPath();
    ctx.arc(dot.x, dot.y, 5, 0, Math.PI * 2);
    ctx.fillStyle = "#080d14";
    ctx.fill();
    ctx.strokeStyle = "#60a5fa";
    ctx.lineWidth = 2;
    ctx.stroke();
  }
};

const drawMinimap = (ctx, nodes, viewport, cssW, cssH) => {
  const MW = 174, MH = 116;
  const MX = cssW - MW - 14, MY = cssH - MH - 14;
  const arr = Object.values(nodes);
  if (!arr.length) return;
  ctx.fillStyle = "rgba(7,11,19,0.92)";
  ctx.strokeStyle = "#1a3050";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.roundRect(MX, MY, MW, MH, 7);
  ctx.fill(); ctx.stroke();
  let x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity;
  arr.forEach((n) => {
    const c = NODE_TYPES[n.type];
    x1 = Math.min(x1, n.x - c.w / 2 - 50);
    y1 = Math.min(y1, n.y - c.h / 2 - 50);
    x2 = Math.max(x2, n.x + c.w / 2 + 50);
    y2 = Math.max(y2, n.y + c.h / 2 + 50);
  });
  const scl = Math.min((MW - 10) / (x2 - x1 || 1), (MH - 10) / (y2 - y1 || 1));
  const ox = MX + 5 - x1 * scl, oy = MY + 5 - y1 * scl;
  const mm = (wx, wy) => ({ x: ox + wx * scl, y: oy + wy * scl });
  ctx.save();
  ctx.beginPath();
  ctx.rect(MX + 1, MY + 1, MW - 2, MH - 2);
  ctx.clip();
  arr.forEach((n) => {
    const cfg = NODE_TYPES[n.type], p = mm(n.x, n.y);
    const nw = Math.max(cfg.w * scl, 4), nh = Math.max(cfg.h * scl, 3);
    ctx.fillStyle = cfg.colorA + "dd";
    ctx.beginPath();
    ctx.roundRect(p.x - nw / 2, p.y - nh / 2, nw, nh, 1);
    ctx.fill();
  });
  const v = viewport;
  const tl = mm(-v.x / v.zoom, -v.y / v.zoom);
  const br = mm((-v.x + cssW) / v.zoom, (-v.y + cssH) / v.zoom);
  ctx.strokeStyle = "#3b82f6";
  ctx.lineWidth = 1.5;
  ctx.fillStyle = "rgba(59,130,246,0.07)";
  ctx.beginPath();
  ctx.rect(tl.x, tl.y, br.x - tl.x, br.y - tl.y);
  ctx.fill(); ctx.stroke();
  ctx.restore();
  ctx.fillStyle = "#1e3a5a";
  ctx.textAlign = "right"; ctx.textBaseline = "bottom";
  ctx.font = '8px "Fira Code",monospace';
  ctx.fillText("MINIMAP", MX + MW - 4, MY - 2);
};

const INITIAL_NODES = {
  n1: { id: "n1", type: "START", label: "Inicio", x: 380, y: 90 },
  n2: { id: "n2", type: "DECISION", label: "¿i < 5?", x: 380, y: 240 },
  n3: { id: "n3", type: "PROCESS", label: "i = i + 1", x: 380, y: 400 },
  n4: { id: "n4", type: "END", label: "Fin", x: 590, y: 400 },
};
const INITIAL_EDGES = {
  e1: { id: "e1", from: "n1", to: "n2", label: "", sourcePort: "bottom", targetPort: "top" },
  e2: { id: "e2", from: "n2", to: "n3", label: "Sí", sourcePort: "bottom", targetPort: "top" },
  e3: { id: "e3", from: "n2", to: "n4", label: "No", sourcePort: "right", targetPort: "top" },
  e4: { id: "e4", from: "n3", to: "n2", label: "", sourcePort: "bottom", targetPort: "top", jointOffset: 60 },
};

export default function App() {
  const canvasRef = useRef(null);
  const inputRef = useRef(null);
  const [nodes, setNodes] = useState(INITIAL_NODES);
  const [edges, setEdges] = useState(INITIAL_EDGES);
  const [viewport, setViewport] = useState({ x: 80, y: 50, zoom: 1 });
  const [selection, setSelection] = useState(new Set());
  const [selectedEdgeId, setSelectedEdgeId] = useState(null);
  const [mode, setMode] = useState("MOVE");
  const [editingNode, setEditingNode] = useState(null);
  const [editingEdge, setEditingEdge] = useState(null);
  const history = useRef([{ nodes: INITIAL_NODES, edges: INITIAL_EDGES }]);
  const histIdx = useRef(0);

  const saveHist = (n, e) => {
    history.current = history.current.slice(0, histIdx.current + 1);
    history.current.push({ nodes: JSON.parse(JSON.stringify(n)), edges: JSON.parse(JSON.stringify(e)) });
    if (history.current.length > 80) history.current.shift();
    else histIdx.current++;
  };
  const undo = () => { if (histIdx.current <= 0) return; histIdx.current--; const s = history.current[histIdx.current]; setNodes(s.nodes); setEdges(s.edges); };
  const redo = () => { if (histIdx.current >= history.current.length - 1) return; histIdx.current++; const s = history.current[histIdx.current]; setNodes(s.nodes); setEdges(s.edges); };

  const nodesR = useRef(nodes);
  const edgesR = useRef(edges);
  const viewR = useRef(viewport);
  nodesR.current = nodes;
  edgesR.current = edges;
  viewR.current = viewport;

  const ia = useRef({
    isDragging: false, isPanning: false, isBoxSel: false, isDragJoint: false,
    lastPanX: 0, lastPanY: 0, startX: 0, startY: 0, dragOffsets: {},
    boxRect: null, connectingFrom: null, dragJointEdgeId: null,
    mousePos: { x: 0, y: 0 }, guides: [],
  });

  const px2w = (cx, cy) => { const v = viewR.current; return { x: (cx - v.x) / v.zoom, y: (cy - v.y) / v.zoom }; };
  const cc = (e) => {
    const r = canvasRef.current.getBoundingClientRect();
    return { cx: (e.clientX ?? e.touches?.[0]?.clientX ?? 0) - r.left, cy: (e.clientY ?? e.touches?.[0]?.clientY ?? 0) - r.top };
  };

  const render = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    const dpr = window.devicePixelRatio || 1;
    const cssW = canvas.width / dpr, cssH = canvas.height / dpr;
    const { x: vx, y: vy, zoom } = viewport;
    const s = ia.current;
    ctx.resetTransform();
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.scale(dpr, dpr);
    ctx.fillStyle = "#070c15";
    ctx.fillRect(0, 0, cssW, cssH);
    ctx.translate(vx, vy);
    ctx.scale(zoom, zoom);
    const wMinX = -vx / zoom, wMinY = -vy / zoom, wMaxX = (cssW - vx) / zoom, wMaxY = (cssH - vy) / zoom;
    const step = zoom < 0.5 ? 60 : 30;
    ctx.fillStyle = "#152033";
    for (let gx = Math.floor(wMinX / step) * step; gx < wMaxX; gx += step)
      for (let gy = Math.floor(wMinY / step) * step; gy < wMaxY; gy += step)
        ctx.fillRect(gx - 1, gy - 1, 2, 2);
    Object.values(edges).forEach((edge) => {
      const n1 = nodes[edge.from], n2 = nodes[edge.to];
      if (n1 && n2) drawEdge(ctx, edge, n1, n2, selectedEdgeId === edge.id, editingEdge?.id === edge.id);
    });
    if (s.connectingFrom) {
      const n1 = nodes[s.connectingFrom];
      if (n1) {
        const srcP = getNearestPort(n1, s.mousePos.x, s.mousePos.y);
        const src = PORT_POS(n1, srcP);
        ctx.strokeStyle = "#34d399";
        ctx.lineWidth = 2;
        ctx.setLineDash([6, 4]);
        ctx.beginPath();
        ctx.moveTo(src.x, src.y);
        ctx.lineTo(s.mousePos.x, s.mousePos.y);
        ctx.stroke();
        ctx.setLineDash([]);
      }
    }
    if (s.guides.length) {
      ctx.strokeStyle = "#ec489977";
      ctx.lineWidth = 1 / zoom;
      ctx.setLineDash([4, 4]);
      ctx.beginPath();
      s.guides.forEach((g) => {
        if (g.type === "x") { ctx.moveTo(g.val, wMinY); ctx.lineTo(g.val, wMaxY); }
        else { ctx.moveTo(wMinX, g.val); ctx.lineTo(wMaxX, g.val); }
      });
      ctx.stroke();
      ctx.setLineDash([]);
    }
    Object.values(nodes).forEach((n) => drawNode(ctx, n, selection.has(n.id), mode === "CONNECT" || selection.has(n.id)));
    if (s.isBoxSel && s.boxRect) {
      const { x, y, w, h } = s.boxRect;
      ctx.fillStyle = "rgba(96,165,250,0.07)";
      ctx.strokeStyle = "#3b82f6";
      ctx.lineWidth = 1 / zoom;
      ctx.fillRect(x, y, w, h);
      ctx.strokeRect(x, y, w, h);
    }
    ctx.resetTransform();
    ctx.scale(dpr, dpr);
    drawMinimap(ctx, nodes, viewport, cssW, cssH);
    ctx.fillStyle = "#1e3a5a";
    ctx.textAlign = "left"; ctx.textBaseline = "bottom";
    ctx.font = '10px "Fira Code",monospace';
    ctx.fillText(`${Math.round(zoom * 100)}%`, 14, cssH - 10);
    const nc = Object.keys(nodes).length, ec = Object.keys(edges).length;
    ctx.textAlign = "left"; ctx.textBaseline = "bottom";
    ctx.font = '9px "Fira Code",monospace';
    ctx.fillStyle = "#152535";
    ctx.fillText(`${nc} nodos · ${ec} conexiones`, 14, cssH - 24);
  }, [nodes, edges, viewport, selection, selectedEdgeId, editingEdge, mode]);

  useEffect(() => {
    let id;
    const loop = () => { render(); id = requestAnimationFrame(loop); };
    loop();
    return () => cancelAnimationFrame(id);
  }, [render]);

  useEffect(() => {
    const resize = () => {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.parentElement.getBoundingClientRect();
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      canvas.style.width = `${rect.width}px`;
      canvas.style.height = `${rect.height}px`;
    };
    resize();
    window.addEventListener("resize", resize);
    return () => window.removeEventListener("resize", resize);
  }, []);

  const onPointerDown = (e) => {
    const { cx, cy } = cc(e), wc = px2w(cx, cy), s = ia.current;
    s.mousePos = wc;
    setEditingNode(null);
    setEditingEdge(null);
    if (e.button === 1 || e.altKey) { s.isPanning = true; s.lastPanX = cx; s.lastPanY = cy; return; }
    const nArr = Object.values(nodesR.current);
    let hitNode = null;
    for (let i = nArr.length - 1; i >= 0; i--) {
      const n = nArr[i];
      if (hitRect(wc.x, wc.y, n.x, n.y, NODE_TYPES[n.type].w, NODE_TYPES[n.type].h)) { hitNode = n; break; }
    }
    if (mode === "CONNECT") { if (hitNode) s.connectingFrom = hitNode.id; return; }
    if (hitNode) {
      s.isDragging = true;
      setSelectedEdgeId(null);
      let sel = new Set(selection);
      if (!e.shiftKey && !sel.has(hitNode.id)) { sel.clear(); sel.add(hitNode.id); }
      else if (e.shiftKey) { sel.has(hitNode.id) ? sel.delete(hitNode.id) : sel.add(hitNode.id); }
      setSelection(sel);
      s.dragOffsets = {};
      sel.forEach((id) => { s.dragOffsets[id] = { dx: nodesR.current[id].x - wc.x, dy: nodesR.current[id].y - wc.y }; });
      return;
    }
    let hitEdge = null, hitJoint = null;
    Object.values(edgesR.current).forEach((edge) => {
      const n1 = nodesR.current[edge.from], n2 = nodesR.current[edge.to];
      if (!n1 || !n2) return;
      const geom = getEdgeGeom(edge, n1, n2);
      if (selectedEdgeId === edge.id && geom.dot && dist2p(wc.x, wc.y, geom.dot.x, geom.dot.y) < 12) { hitJoint = edge; return; }
      for (let i = 0; i < geom.pts.length - 1; i++) {
        if (distSeg(wc, { x: geom.pts[i][0], y: geom.pts[i][1] }, { x: geom.pts[i + 1][0], y: geom.pts[i + 1][1] }) < 8) { hitEdge = edge; break; }
      }
      if (edge.label && hitRect(wc.x, wc.y, geom.labelPos.x, geom.labelPos.y, 60, 20)) hitEdge = edge;
    });
    if (hitJoint) { s.isDragJoint = true; s.dragJointEdgeId = hitJoint.id; s.lastPanY = wc.y; }
    else if (hitEdge) { setSelectedEdgeId(hitEdge.id); setSelection(new Set()); }
    else {
      if (!e.shiftKey) { setSelection(new Set()); setSelectedEdgeId(null); }
      s.isBoxSel = true; s.startX = wc.x; s.startY = wc.y;
    }
  };

  const onPointerMove = (e) => {
    const { cx, cy } = cc(e), wc = px2w(cx, cy), s = ia.current;
    s.mousePos = wc;
    if (s.isPanning) {
      setViewport((v) => ({ ...v, x: v.x - (cx - s.lastPanX), y: v.y - (cy - s.lastPanY) }));
      s.lastPanX = cx; s.lastPanY = cy; return;
    }
    if (s.connectingFrom) return;
    if (s.isDragJoint && s.dragJointEdgeId) {
      const dy = wc.y - s.lastPanY;
      setEdges((prev) => { const ed = prev[s.dragJointEdgeId]; return { ...prev, [ed.id]: { ...ed, jointOffset: (ed.jointOffset ?? 0) + dy } }; });
      s.lastPanY = wc.y; return;
    }
    if (s.isBoxSel) {
      s.boxRect = { x: Math.min(s.startX, wc.x), y: Math.min(s.startY, wc.y), w: Math.abs(wc.x - s.startX), h: Math.abs(wc.y - s.startY) };
      const ns = new Set();
      Object.values(nodesR.current).forEach((n) => { if (hitRect(n.x, n.y, s.boxRect.x + s.boxRect.w / 2, s.boxRect.y + s.boxRect.h / 2, s.boxRect.w, s.boxRect.h)) ns.add(n.id); });
      setSelection(ns); return;
    }
    if (s.isDragging && selection.size > 0) {
      let sdx = 0, sdy = 0;
      s.guides = [];
      if (selection.size === 1) {
        const mid = [...selection][0];
        const rx = wc.x + s.dragOffsets[mid].dx, ry = wc.y + s.dragOffsets[mid].dy;
        Object.values(nodesR.current).forEach((n) => {
          if (n.id === mid) return;
          if (Math.abs(rx - n.x) < SNAP) { sdx = n.x - rx; s.guides.push({ type: "x", val: n.x }); }
          if (Math.abs(ry - n.y) < SNAP) { sdy = n.y - ry; s.guides.push({ type: "y", val: n.y }); }
        });
      }
      setNodes((prev) => {
        const nx = { ...prev };
        selection.forEach((id) => { nx[id] = { ...nx[id], x: wc.x + s.dragOffsets[id].dx + sdx, y: wc.y + s.dragOffsets[id].dy + sdy }; });
        return nx;
      });
    }
  };

  const onPointerUp = (e) => {
    const { cx, cy } = cc(e), wc = px2w(cx, cy), s = ia.current;
    if (s.connectingFrom) {
      const tgt = Object.values(nodesR.current).find((n) => hitRect(wc.x, wc.y, n.x, n.y, NODE_TYPES[n.type].w, NODE_TYPES[n.type].h) && n.id !== s.connectingFrom);
      if (tgt) {
        if (!Object.values(edgesR.current).some((ed) => ed.from === s.connectingFrom && ed.to === tgt.id)) {
          const srcNode = nodesR.current[s.connectingFrom];
          const sPort = getNearestPort(srcNode, wc.x, wc.y);
          const tPort = getNearestPort(tgt, wc.x, wc.y);
          const ne = { id: uid(), from: s.connectingFrom, to: tgt.id, label: "", sourcePort: sPort, targetPort: tPort };
          setEdges((prev) => { const ned = { ...prev, [ne.id]: ne }; saveHist(nodesR.current, ned); return ned; });
        }
      } else {
        let hitEdge = null, hitPos = null;
        Object.values(edgesR.current).forEach((edge) => {
          const n1 = nodesR.current[edge.from], n2 = nodesR.current[edge.to];
          if (!n1 || !n2) return;
          const geom = getEdgeGeom(edge, n1, n2);
          for (let i = 0; i < geom.pts.length - 1; i++) {
            const a = { x: geom.pts[i][0], y: geom.pts[i][1] };
            const b = { x: geom.pts[i + 1][0], y: geom.pts[i + 1][1] };
            if (distSeg(wc, a, b) < 12) {
              const l2 = (b.x - a.x) ** 2 + (b.y - a.y) ** 2;
              const t = Math.max(0, Math.min(1, ((wc.x - a.x) * (b.x - a.x) + (wc.y - a.y) * (b.y - a.y)) / l2));
              hitEdge = edge; hitPos = { x: a.x + t * (b.x - a.x), y: a.y + t * (b.y - a.y) }; return;
            }
          }
        });
        if (hitEdge && hitPos) {
          const nid = uid();
          const nn = { id: nid, type: "PROCESS", label: "", x: Math.round(hitPos.x / 10) * 10, y: Math.round(hitPos.y / 10) * 10 };
          const srcNode = nodesR.current[s.connectingFrom];
          const sPort = getNearestPort(srcNode, nn.x, nn.y);
          const ne = { id: uid(), from: s.connectingFrom, to: nid, label: "", sourcePort: sPort, targetPort: "top" };
          setNodes((prev) => ({ ...prev, [nid]: nn }));
          setEdges((prev) => { const ned = { ...prev, [ne.id]: ne }; saveHist({ ...nodesR.current, [nid]: nn }, ned); return ned; });
          setSelection(new Set([nid]));
        }
      }
    }
    if (s.isDragging) saveHist(nodesR.current, edgesR.current);
    s.isDragging = false; s.isPanning = false; s.isBoxSel = false; s.isDragJoint = false;
    s.connectingFrom = null; s.dragJointEdgeId = null; s.boxRect = null; s.guides = [];
  };

  const onDoubleClick = (e) => {
    const { cx, cy } = cc(e), wc = px2w(cx, cy);
    const node = Object.values(nodesR.current).reverse().find((n) => hitRect(wc.x, wc.y, n.x, n.y, NODE_TYPES[n.type].w, NODE_TYPES[n.type].h));
    if (node) { setEditingNode(node); setTimeout(() => inputRef.current?.select(), 50); return; }
    const edge = Object.values(edgesR.current).find((ed) => {
      const n1 = nodesR.current[ed.from], n2 = nodesR.current[ed.to];
      if (!n1 || !n2) return false;
      return hitRect(wc.x, wc.y, getEdgeGeom(ed, n1, n2).labelPos.x, getEdgeGeom(ed, n1, n2).labelPos.y, 60, 20);
    });
    if (edge) { setEditingEdge(edge); setTimeout(() => inputRef.current?.select(), 50); }
  };

  const onWheel = (e) => {
    e.preventDefault();
    const { cx, cy } = cc(e), f = e.deltaY > 0 ? 0.9 : 1.1;
    setViewport((v) => {
      const nz = Math.max(0.15, Math.min(3, v.zoom * f));
      return { x: cx - (cx - v.x) * (nz / v.zoom), y: cy - (cy - v.y) * (nz / v.zoom), zoom: nz };
    });
  };

  useEffect(() => {
    const onKey = (e) => {
      if (document.activeElement.tagName === "INPUT") return;
      const k = e.key.toLowerCase();
      if (k === "delete" || k === "backspace") {
        if (selection.size > 0) {
          setNodes((p) => { const n = { ...p }; selection.forEach((id) => delete n[id]); return n; });
          setEdges((p) => { const ed = { ...p }; Object.values(ed).forEach((edge) => { if (selection.has(edge.from) || selection.has(edge.to)) delete ed[edge.id]; }); return ed; });
          setSelection(new Set());
        }
        if (selectedEdgeId) { setEdges((p) => { const ed = { ...p }; delete ed[selectedEdgeId]; return ed; }); setSelectedEdgeId(null); }
      }
      if (k === "m") setMode("MOVE");
      if (k === "c") setMode("CONNECT");
      if (k === "r") setViewport({ x: 80, y: 50, zoom: 1 });
      if (k === "escape") { setSelection(new Set()); setSelectedEdgeId(null); setEditingNode(null); setEditingEdge(null); }
      if ((e.ctrlKey || e.metaKey) && k === "z") { e.preventDefault(); e.shiftKey ? redo() : undo(); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selection, selectedEdgeId]);

  const addNode = (type) => {
    const canvas = canvasRef.current;
    const dpr = window.devicePixelRatio || 1;
    const wc = px2w(canvas ? canvas.width / dpr / 2 : 400, canvas ? canvas.height / dpr / 2 : 300);
    const n = { id: uid(), type, label: NODE_TYPES[type].label, x: Math.round(wc.x / 10) * 10, y: Math.round(wc.y / 10) * 10 };
    setNodes((prev) => { const nxt = { ...prev, [n.id]: n }; saveHist(nxt, edgesR.current); return nxt; });
    setSelection(new Set([n.id]));
    setMode("MOVE");
  };

  const selEdge = edges[selectedEdgeId];
  const selSrc = selEdge ? nodes[selEdge.from] : null;
  const nodeEditPos = editingNode ? { left: viewport.x + editingNode.x * viewport.zoom, top: viewport.y + editingNode.y * viewport.zoom } : null;
  const edgeEditPos = editingEdge && nodes[editingEdge.from] && nodes[editingEdge.to] ? (() => {
    const g = getEdgeGeom(editingEdge, nodes[editingEdge.from], nodes[editingEdge.to]);
    return { left: viewport.x + g.labelPos.x * viewport.zoom, top: viewport.y + g.labelPos.y * viewport.zoom };
  })() : null;

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100vh", overflow: "hidden", userSelect: "none", background: "#070c15", color: "#94a3b8", fontFamily: '"Fira Code","JetBrains Mono",monospace' }}>
      <header style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 8, padding: "10px 16px", flexShrink: 0, zIndex: 10, background: "#09101f", borderBottom: "1px solid #0f2035" }}>
        <div style={{ display: "flex", borderRadius: 8, overflow: "hidden", border: "1px solid #0f2a40", fontSize: 12, fontWeight: 700 }}>
          <button onClick={() => setMode("MOVE")} style={{ padding: "6px 16px", background: mode === "MOVE" ? "#2563eb" : "#0b1525", color: mode === "MOVE" ? "#fff" : "#4a6a8a", transition: "all .15s", cursor: "pointer" }}>
            ↖ Mover <span style={{ opacity: 0.3, fontSize: 9 }}>[M]</span>
          </button>
          <button onClick={() => setMode("CONNECT")} style={{ padding: "6px 16px", background: mode === "CONNECT" ? "#059669" : "#0b1525", color: mode === "CONNECT" ? "#fff" : "#4a6a8a", borderLeft: "1px solid #0f2a40", transition: "all .15s", cursor: "pointer" }}>
            ⟶ Conectar <span style={{ opacity: 0.3, fontSize: 9 }}>[C]</span>
          </button>
        </div>
        <div style={{ display: "flex", gap: 6, overflowX: "auto" }}>
          {Object.entries(NODE_TYPES).map(([k, v]) => (
            <button key={k} onClick={() => addNode(k)}
              style={{ padding: "5px 10px", fontSize: 11, fontWeight: 700, borderRadius: 8, border: `1px solid ${v.colorA}44`, background: `${v.colorA}14`, color: v.colorA, cursor: "pointer", whiteSpace: "nowrap", transition: "filter .15s" }}
              onMouseEnter={(e) => (e.target.style.filter = "brightness(1.25)")} onMouseLeave={(e) => (e.target.style.filter = "")}>
              + {v.label}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 6, marginLeft: "auto", alignItems: "center" }}>
          <button onClick={undo} title="Ctrl+Z" style={{ padding: "5px 12px", fontSize: 13, borderRadius: 8, border: "1px solid #0f2a40", background: "#0b1525", color: "#3d6080", cursor: "pointer" }}>↩</button>
          <button onClick={redo} title="Ctrl+Shift+Z" style={{ padding: "5px 12px", fontSize: 13, borderRadius: 8, border: "1px solid #0f2a40", background: "#0b1525", color: "#3d6080", cursor: "pointer" }}>↪</button>
          <div style={{ width: 1, height: 20, background: "#0f2a40", margin: "0 4px" }} />
          <span style={{ display: "block", fontSize: 9, color: "#1a3050", fontWeight: 700, letterSpacing: "0.15em", textTransform: "uppercase" }}>
            Alt+Arr=Pan · R=Reset · Scroll=Zoom · Del=Borrar · Dbl=Editar
          </span>
        </div>
      </header>
      <div style={{ flex: 1, position: "relative", overflow: "hidden" }}>
        <canvas ref={canvasRef}
          style={{ position: "absolute", top: 0, right: 0, bottom: 0, left: 0, outline: "none", touchAction: "none", cursor: mode === "CONNECT" ? "crosshair" : "default" }}
          onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerLeave={onPointerUp}
          onWheel={onWheel} onDoubleClick={onDoubleClick} tabIndex={0} />
        {nodeEditPos && editingNode && (
          <div style={{ position: "absolute", zIndex: 20, left: nodeEditPos.left, top: nodeEditPos.top, transform: "translate(-50%,-50%)" }}>
            <input ref={inputRef}
              style={{ background: "#070c15", border: "2px solid #2563eb", borderRadius: 8, padding: "6px 12px", color: "#f1f5f9", fontSize: 13, fontFamily: "inherit", textAlign: "center", outline: "none", boxShadow: "0 0 0 4px rgba(37,99,235,0.2), 0 20px 40px rgba(0,0,0,0.8)", width: NODE_TYPES[editingNode.type].w * viewport.zoom }}
              defaultValue={editingNode.label}
              onBlur={(e) => { setNodes((p) => ({ ...p, [editingNode.id]: { ...p[editingNode.id], label: e.target.value } })); setEditingNode(null); }}
              onKeyDown={(e) => { if (e.key === "Enter") e.target.blur(); if (e.key === "Escape") setEditingNode(null); }} />
          </div>
        )}
        {edgeEditPos && editingEdge && (
          <div style={{ position: "absolute", zIndex: 20, left: edgeEditPos.left, top: edgeEditPos.top, transform: "translate(-50%,-50%)" }}>
            <input ref={inputRef}
              style={{ background: "#070c15", border: "2px solid #2563eb", borderRadius: 6, padding: "4px 8px", color: "#f1f5f9", fontSize: 11, fontFamily: "inherit", textAlign: "center", outline: "none", width: 96 }}
              defaultValue={editingEdge.label}
              onBlur={(e) => { setEdges((p) => ({ ...p, [editingEdge.id]: { ...p[editingEdge.id], label: e.target.value } })); setEditingEdge(null); }}
              onKeyDown={(e) => { if (e.key === "Enter") e.target.blur(); if (e.key === "Escape") setEditingEdge(null); }} />
          </div>
        )}
        {selEdge && !editingEdge && (
          <div style={{ position: "absolute", bottom: 20, left: "50%", transform: "translateX(-50%)", zIndex: 20, display: "flex", alignItems: "flex-end", gap: 16, background: "rgba(7,11,19,0.97)", backdropFilter: "blur(12px)", border: "1px solid #0f2a40", borderRadius: 16, padding: "12px 20px", boxShadow: "0 24px 64px -12px rgba(0,0,0,0.95)" }}>
            <div style={{ minWidth: 140 }}>
              <div style={{ fontSize: 9, color: "#1a3a5a", fontWeight: 700, letterSpacing: "0.18em", textTransform: "uppercase", marginBottom: 6 }}>Etiqueta</div>
              <input type="text" placeholder="Sí / No / …"
                style={{ background: "#040810", border: "1px solid #0f2a40", borderRadius: 10, padding: "7px 12px", color: "#94a3b8", width: "100%", fontSize: 12, fontFamily: "inherit", outline: "none" }}
                value={selEdge.label} onChange={(e) => setEdges((p) => ({ ...p, [selEdge.id]: { ...p[selEdge.id], label: e.target.value } }))} />
            </div>
            {selSrc && (() => {
              const P = (label, portKey, defaultPort, ports, onChange) => (
                <div>
                  <div style={{ fontSize: 9, color: "#1a3a5a", fontWeight: 700, letterSpacing: "0.18em", textTransform: "uppercase", marginBottom: 6, textAlign: "center" }}>{label}</div>
                  <div style={{ display: "flex", background: "#040810", border: "1px solid #0f2a40", borderRadius: 10, overflow: "hidden" }}>
                    {ports.map(([pt, ic]) => (
                      <button key={pt} onClick={() => onChange(pt)}
                        style={{ padding: "7px 14px", fontSize: 14, fontWeight: 700, cursor: "pointer", transition: "all .15s", background: (selEdge[portKey] ?? defaultPort) === pt ? "#0891b2" : "transparent", color: (selEdge[portKey] ?? defaultPort) === pt ? "#fff" : "#2a4060", border: "none" }}>
                        {ic}
                      </button>
                    ))}
                  </div>
                </div>
              );
              const allPorts = [["top", "⬆"], ["bottom", "⬇"], ["left", "⬅"], ["right", "➡"]];
              return (
                <>
                  {P("Origen", "sourcePort", "bottom", allPorts, (pt) => setEdges((p) => ({ ...p, [selEdge.id]: { ...p[selEdge.id], sourcePort: pt } })))}
                  {P("Destino", "targetPort", "top", allPorts, (pt) => setEdges((p) => ({ ...p, [selEdge.id]: { ...p[selEdge.id], targetPort: pt } })))}
                </>
              );
            })()}
            <button onClick={() => { setEdges((p) => { const n = { ...p }; delete n[selectedEdgeId]; return n; }); setSelectedEdgeId(null); }}
              style={{ padding: "7px 12px", fontSize: 12, fontWeight: 700, background: "rgba(239,68,68,0.10)", color: "#f87171", border: "1px solid rgba(239,68,68,0.3)", borderRadius: 10, cursor: "pointer", alignSelf: "flex-end", marginBottom: 1 }}>
              ✕
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
