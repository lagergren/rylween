package rylween.bitmapweb

internal fun bitmapPageHtml(): String =
    """
    <!doctype html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Rylween Bitmap</title>
      <style>
        :root {
          color-scheme: dark;
          --page: #151711;
          --bar: #20251b;
          --line: #394130;
          --text: #f1ead6;
          --muted: #bdb496;
          --accent: #7fbf9c;
          --button: #2f3c31;
          --button-hover: #3d533f;
        }

        * { box-sizing: border-box; }

        html,
        body {
          margin: 0;
          min-height: 100%;
          background: var(--page);
          color: var(--text);
          font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        }

        .shell {
          min-height: 100vh;
          display: grid;
          grid-template-rows: auto minmax(0, 1fr);
        }

        .topbar {
          min-height: 46px;
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 16px;
          padding: 8px 14px;
          border-bottom: 1px solid var(--line);
          background: var(--bar);
        }

        .brand {
          display: flex;
          align-items: center;
          gap: 10px;
          min-width: 0;
        }

        .mark {
          width: 24px;
          height: 24px;
          display: grid;
          place-items: center;
          border: 1px solid #8cad78;
          background: #26341f;
          color: #bcdc89;
          font-family: "SFMono-Regular", Menlo, Consolas, monospace;
          font-size: 14px;
          line-height: 1;
        }

        .title {
          font-size: 15px;
          font-weight: 700;
          letter-spacing: 0;
          white-space: nowrap;
        }

        .status {
          display: flex;
          align-items: center;
          gap: 10px;
          color: var(--muted);
          font-size: 13px;
          white-space: nowrap;
        }

        .dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #b7574f;
        }

        .dot.connected { background: #71b765; }

        .top-actions {
          display: flex;
          align-items: center;
          gap: 10px;
        }

        .stage {
          min-height: 0;
          position: relative;
          display: grid;
          place-items: center;
          padding: 12px;
        }

        canvas {
          width: min(100%, 1280px);
          max-height: calc(100vh - 86px);
          aspect-ratio: 16 / 9;
          image-rendering: pixelated;
          border: 1px solid var(--line);
          background: #060806;
          box-shadow: 0 18px 48px rgb(0 0 0 / 34%);
          outline: none;
        }

        .command-popover {
          position: absolute;
          top: 24px;
          right: 24px;
          z-index: 2;
          width: min(360px, calc(100% - 48px));
          max-height: calc(100% - 48px);
          overflow: auto;
          padding: 14px;
          border: 1px solid #5a6e47;
          background: rgb(21 27 18 / 96%);
          box-shadow: 0 18px 44px rgb(0 0 0 / 45%);
        }

        .command-popover h2 {
          margin: 0 0 12px;
          font-size: 13px;
          letter-spacing: 0;
          color: #d9cfaa;
        }

        .command-grid {
          display: grid;
          grid-template-columns: repeat(2, minmax(0, 1fr));
          gap: 8px;
        }

        .command-key {
          display: inline-grid;
          place-items: center;
          min-width: 42px;
          height: 24px;
          margin-right: 8px;
          border: 1px solid #6d8355;
          background: #202b1b;
          color: #f2d46f;
          font-family: "SFMono-Regular", Menlo, Consolas, monospace;
          font-size: 12px;
        }

        .canvas-tooltip {
          position: absolute;
          z-index: 3;
          max-width: 240px;
          padding: 7px 9px;
          border: 1px solid #758a57;
          background: rgb(12 16 10 / 96%);
          color: #f1ead6;
          font-size: 12px;
          line-height: 1.3;
          pointer-events: none;
          box-shadow: 0 10px 24px rgb(0 0 0 / 45%);
        }

        button {
          min-height: 34px;
          padding: 0 12px;
          border: 1px solid #516346;
          border-radius: 6px;
          background: var(--button);
          color: var(--text);
          font: inherit;
          font-size: 13px;
          cursor: pointer;
        }

        button:hover,
        button:focus-visible {
          background: var(--button-hover);
          outline: 1px solid var(--accent);
          outline-offset: 1px;
        }

        @media (max-width: 720px) {
          .topbar {
            align-items: flex-start;
            flex-direction: column;
            gap: 4px;
          }

          .top-actions {
            width: 100%;
            justify-content: space-between;
          }

          .stage {
            padding: 8px;
          }

          canvas {
            max-height: calc(100vh - 136px);
          }
        }
      </style>
    </head>
    <body>
      <main class="shell">
        <header class="topbar">
          <div class="brand">
            <div class="mark">R</div>
            <div class="title">Rylween Bitmap</div>
          </div>
          <div class="top-actions">
            <button id="commandToggle" type="button" hidden>Commands</button>
            <div class="status">
              <span id="connectionDot" class="dot"></span>
              <span id="connectionText">Connecting</span>
            </div>
          </div>
        </header>
        <section class="stage">
          <canvas id="game" width="1280" height="720" tabindex="0" aria-label="Rylween bitmap game"></canvas>
          <aside id="commandPopover" class="command-popover" hidden aria-label="Rylween command palette"></aside>
          <div id="canvasTooltip" class="canvas-tooltip" hidden></div>
        </section>
      </main>
      <script>
        const canvas = document.getElementById("game");
        const ctx = canvas.getContext("2d");
        const commandToggle = document.getElementById("commandToggle");
        const commandPopover = document.getElementById("commandPopover");
        const canvasTooltip = document.getElementById("canvasTooltip");
        const connectionDot = document.getElementById("connectionDot");
        const connectionText = document.getElementById("connectionText");
        let socket = null;
        let frame = null;
        let currentCommands = [];
        let statusHotspots = [];
        let titleFade = null;
        const assetVersion = "20260513-title-default-horse-marker";
        const layout = {
          map: { x: 114, y: 64, w: 420, h: 482 },
          text: { x: 746, y: 64, w: 420, h: 482 },
          status: { x: 154, y: 566, w: 972, h: 92 }
        };
        const bitmapAssets = {
          title: loadAsset(assetUrl("/assets/title-rider.png")),
          frame: loadAsset(assetUrl("/assets/screen-frame.png")),
          town: loadAsset(assetUrl("/assets/town-mainstreet.png")),
          encounter: loadAsset(assetUrl("/assets/encounter-scorpion.png")),
          bestiary: loadAsset(assetUrl("/assets/encounter-bestiary.png")),
          scenes: loadAsset(assetUrl("/assets/scene-atlas.png")),
          terrain: loadAsset(assetUrl("/assets/terrain-atlas.png")),
          sceneMainstreet: loadAsset(assetUrl("/assets/scene-thord-mainstreet.png")),
          sceneMerchant: loadAsset(assetUrl("/assets/scene-merchant.png")),
          sceneSmith: loadAsset(assetUrl("/assets/scene-smith.png")),
          sceneInn: loadAsset(assetUrl("/assets/scene-inn.png")),
          sceneTavern: loadAsset(assetUrl("/assets/scene-tavern.png")),
          sceneChurch: loadAsset(assetUrl("/assets/scene-church.png")),
          sceneGuild: loadAsset(assetUrl("/assets/scene-guild-closed.png")),
          monsterGoblinoid: loadAsset(assetUrl("/assets/monster-goblinoid.png")),
          monsterUndead: loadAsset(assetUrl("/assets/monster-undead.png")),
          monsterBeast: loadAsset(assetUrl("/assets/monster-beast.png")),
          monsterFlying: loadAsset(assetUrl("/assets/monster-flying.png")),
          monsterVermin: loadAsset(assetUrl("/assets/monster-vermin.png")),
          monsterBrute: loadAsset(assetUrl("/assets/monster-brute.png")),
          monsterHumanoidMagic: loadAsset(assetUrl("/assets/monster-humanoid-magic.png")),
          monsterBoss: loadAsset(assetUrl("/assets/monster-boss.png"))
        };

        function assetUrl(path) {
          return path + "?v=" + assetVersion;
        }

        function loadAsset(src) {
          const image = new Image();
          image.src = src;
          image.addEventListener("load", () => {
            if (frame) draw(frame);
          });
          return image;
        }

        function connect() {
          const protocol = location.protocol === "https:" ? "wss:" : "ws:";
          socket = new WebSocket(protocol + "//" + location.host + "/ws/play");
          socket.addEventListener("open", () => {
            connectionDot.classList.add("connected");
            connectionText.textContent = "Connected";
            canvas.focus();
          });
          socket.addEventListener("message", (event) => {
            const nextFrame = JSON.parse(event.data);
            const fadeFromTitle = isTitleFrame(frame) && !isTitleFrame(nextFrame);
            if (fadeFromTitle) beginTitleFade();
            frame = nextFrame;
            draw(frame);
            renderCommandPalette(frame.commands || []);
          });
          socket.addEventListener("close", () => {
            connectionDot.classList.remove("connected");
            connectionText.textContent = "Disconnected";
          });
        }

        function send(token) {
          if (!socket || socket.readyState !== WebSocket.OPEN) return;
          socket.send(token);
        }

        function isTitleFrame(candidate) {
          if (!candidate) return false;
          const kind = (candidate.backdrop || candidate.mode || "").toLowerCase();
          return candidate.mode === "title" || kind.includes("title");
        }

        function beginTitleFade() {
          if (!canvas.width || !canvas.height) return;
          const image = new Image();
          image.addEventListener("load", () => {
            titleFade = { image, started: performance.now(), duration: 520 };
            requestAnimationFrame(drawTitleFade);
          });
          image.src = canvas.toDataURL("image/png");
        }

        function drawTitleFade() {
          if (!titleFade || !frame) return;
          const elapsed = performance.now() - titleFade.started;
          const alpha = Math.max(0, 1 - elapsed / titleFade.duration);
          draw(frame);
          ctx.save();
          ctx.globalAlpha = alpha;
          ctx.drawImage(titleFade.image, 0, 0, canvas.width, canvas.height);
          ctx.restore();
          if (alpha > 0) requestAnimationFrame(drawTitleFade);
          else {
            titleFade = null;
            draw(frame);
          }
        }

        window.addEventListener("keydown", (event) => {
          if (event.key === "?" && currentCommands.length > 0) {
            event.preventDefault();
            toggleCommandPalette();
            return;
          }
          if (event.key === "Escape" && !commandPopover.hidden) {
            event.preventDefault();
            closeCommandPalette();
            return;
          }
          const token = keyToken(event);
          if (!token) return;
          event.preventDefault();
          send(token);
        });

        canvas.addEventListener("mousemove", (event) => {
          const point = canvasPoint(event);
          const hit = statusHotspots.find((hotspot) => (
            point.x >= hotspot.x &&
            point.x <= hotspot.x + hotspot.w &&
            point.y >= hotspot.y &&
            point.y <= hotspot.y + hotspot.h
          ));
          if (hit) showCanvasTooltip(hit.text, event);
          else hideCanvasTooltip();
        });

        canvas.addEventListener("mouseleave", hideCanvasTooltip);

        function keyToken(event) {
          if (event.key === "ArrowUp") return "ARROW_UP";
          if (event.key === "ArrowDown") return "ARROW_DOWN";
          if (event.key === "ArrowLeft") return "ARROW_LEFT";
          if (event.key === "ArrowRight") return "ARROW_RIGHT";
          if (event.key === "Escape") return "ESCAPE";
          if (event.key === "Enter") return "ENTER";
          if (event.key === " ") return " ";
          if (event.key.length === 1) return event.key.toUpperCase();
          return null;
        }

        function canvasPoint(event) {
          const rect = canvas.getBoundingClientRect();
          return {
            x: (event.clientX - rect.left) * canvas.width / rect.width,
            y: (event.clientY - rect.top) * canvas.height / rect.height
          };
        }

        function showCanvasTooltip(text, event) {
          const stage = canvas.parentElement.getBoundingClientRect();
          canvasTooltip.hidden = false;
          canvasTooltip.textContent = text;
          const x = Math.min(stage.width - canvasTooltip.offsetWidth - 12, event.clientX - stage.left + 14);
          const y = Math.min(stage.height - canvasTooltip.offsetHeight - 12, event.clientY - stage.top + 14);
          canvasTooltip.style.left = Math.max(12, x) + "px";
          canvasTooltip.style.top = Math.max(12, y) + "px";
        }

        function hideCanvasTooltip() {
          canvasTooltip.hidden = true;
        }

        commandToggle.addEventListener("click", () => {
          toggleCommandPalette();
          canvas.focus();
        });

        function renderCommandPalette(commands) {
          currentCommands = commands;
          commandToggle.hidden = commands.length === 0;
          if (commands.length === 0) {
            closeCommandPalette();
            commandPopover.textContent = "";
            return;
          }
          commandPopover.textContent = "";
          const heading = document.createElement("h2");
          heading.textContent = "Commands";
          commandPopover.appendChild(heading);
          const grid = document.createElement("div");
          grid.className = "command-grid";
          for (const command of commands) {
            const button = document.createElement("button");
            button.type = "button";
            const key = document.createElement("span");
            key.className = "command-key";
            key.textContent = command.label;
            button.appendChild(key);
            button.append(command.caption);
            button.addEventListener("click", () => {
              send(command.input);
              closeCommandPalette();
              canvas.focus();
            });
            grid.appendChild(button);
          }
          commandPopover.appendChild(grid);
        }

        function toggleCommandPalette() {
          if (currentCommands.length === 0) return;
          commandPopover.hidden = !commandPopover.hidden;
        }

        function closeCommandPalette() {
          commandPopover.hidden = true;
        }

        function draw(frame) {
          canvas.width = frame.width || 1280;
          canvas.height = frame.height || 720;
          ctx.imageSmoothingEnabled = false;
          ctx.textBaseline = "alphabetic";
          drawBackdrop(frame);
          if (isTitleFrame(frame)) return;
          if (frame.map) drawMap(frame.map);
          else drawScenePanel(frame);
          drawStatus(frame.status, frame.statusLines || []);
          drawTextPanel(frame);
          drawMessage(frame.message || "");
        }

        function drawBackdrop(frame) {
          const kind = (frame.backdrop || frame.mode || "").toLowerCase();
          ctx.fillStyle = "#10130d";
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          if (frame.mode === "title" || kind.includes("title")) {
            drawTitleBackdrop(frame);
          } else if (kind.includes("towne") || kind.includes("church") || kind.includes("smith") || kind.includes("inn") || kind.includes("merchant") || kind.includes("tavern") || kind.includes("guild")) {
            drawGamePanels();
          } else if (kind.includes("encounter") || kind.includes("scorpion") || frame.mode === "battle") {
            drawGamePanels();
          } else {
            drawGamePanels();
          }
        }

        function drawTitleBackdrop(frame) {
          drawCoverImage(bitmapAssets.title, "#111927");
          drawTitleGradient();
          drawGothicTitle(canvas.width / 2, 76);
          ctx.save();
          ctx.textAlign = "center";
          ctx.font = "15px Menlo, Monaco, Consolas, monospace";
          ctx.fillStyle = "rgba(232, 207, 142, 0.82)";
          ctx.fillText("Press any key", canvas.width / 2, canvas.height - 42);
          if (frame.subtitle) {
            ctx.textAlign = "right";
            ctx.fillStyle = "rgba(188, 170, 118, 0.72)";
            ctx.fillText(frame.subtitle, canvas.width - 42, canvas.height - 42);
          }
          ctx.restore();
        }

        function drawTitleGradient() {
          const top = ctx.createLinearGradient(0, 0, 0, 210);
          top.addColorStop(0, "rgba(0, 0, 0, 0.74)");
          top.addColorStop(1, "rgba(0, 0, 0, 0)");
          ctx.fillStyle = top;
          ctx.fillRect(0, 0, canvas.width, 240);
          const bottom = ctx.createLinearGradient(0, canvas.height - 180, 0, canvas.height);
          bottom.addColorStop(0, "rgba(0, 0, 0, 0)");
          bottom.addColorStop(1, "rgba(0, 0, 0, 0.66)");
          ctx.fillStyle = bottom;
          ctx.fillRect(0, canvas.height - 190, canvas.width, 190);
        }

        function drawGothicTitle(cx, y) {
          ctx.save();
          ctx.textAlign = "center";
          ctx.lineJoin = "round";
          ctx.shadowColor = "rgba(0, 0, 0, 0.9)";
          ctx.shadowBlur = 18;
          ctx.shadowOffsetY = 5;
          ctx.font = "700 58px Palatino, 'Book Antiqua', Georgia, serif";
          ctx.strokeStyle = "rgba(23, 8, 4, 0.92)";
          ctx.lineWidth = 9;
          ctx.strokeText("Adventurers", cx, y);
          ctx.fillStyle = "#f0d07a";
          ctx.fillText("Adventurers", cx, y);
          ctx.font = "700 42px Palatino, 'Book Antiqua', Georgia, serif";
          ctx.lineWidth = 7;
          ctx.strokeText("of Rylween", cx, y + 47);
          ctx.fillStyle = "#d9b764";
          ctx.fillText("of Rylween", cx, y + 47);
          ctx.shadowBlur = 0;
          ctx.strokeStyle = "rgba(196, 117, 41, 0.58)";
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.moveTo(cx - 320, y + 68);
          ctx.lineTo(cx - 84, y + 68);
          ctx.moveTo(cx + 84, y + 68);
          ctx.lineTo(cx + 320, y + 68);
          ctx.stroke();
          ctx.fillStyle = "rgba(214, 61, 35, 0.72)";
          ctx.beginPath();
          ctx.moveTo(cx, y + 57);
          ctx.lineTo(cx + 12, y + 68);
          ctx.lineTo(cx, y + 79);
          ctx.lineTo(cx - 12, y + 68);
          ctx.closePath();
          ctx.fill();
          ctx.restore();
        }

        function drawGamePanels() {
          drawFullImage(bitmapAssets.frame, "#10130d");
          drawImageShade("#020503", 0.1);
        }

        function drawCoverImage(image, fallback) {
          ctx.fillStyle = fallback;
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          if (!image.complete || image.naturalWidth === 0) return;
          const scale = Math.max(canvas.width / image.naturalWidth, canvas.height / image.naturalHeight);
          const w = image.naturalWidth * scale;
          const h = image.naturalHeight * scale;
          const x = (canvas.width - w) / 2;
          const y = (canvas.height - h) / 2;
          ctx.drawImage(image, x, y, w, h);
        }

        function drawFullImage(image, fallback) {
          ctx.fillStyle = fallback;
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          if (!image.complete || image.naturalWidth === 0) return;
          ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
        }

        function drawImageShade(color, alpha) {
          ctx.save();
          ctx.globalAlpha = alpha;
          ctx.fillStyle = color;
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          ctx.restore();
        }

        function drawMap(map) {
          const rows = map.rows || [];
          const cols = rows[0] ? rows[0].length : 0;
          if (rows.length === 0 || cols === 0) return;
          const panelBounds = layout.map;
          const configured = map.tileSize || 64;
          const size = Math.min(configured, Math.floor((panelBounds.w - 8) / cols), Math.floor((panelBounds.h - 52) / rows.length));
          const mapW = cols * size;
          const mapH = rows.length * size;
          const x = panelBounds.x + Math.floor((panelBounds.w - mapW) / 2);
          const y = panelBounds.y + Math.floor((panelBounds.h - mapH) / 2);
          for (let row = 0; row < rows.length; row += 1) {
            for (let col = 0; col < rows[row].length; col += 1) {
              drawTile(rows[row][col], x + col * size, y + row * size, size);
            }
          }
        }

        function drawScenePanel(frame) {
          const kind = (frame.backdrop || frame.mode || "").toLowerCase();
          if (!kind || kind === "wilderness" || kind.includes("title") || kind.includes("screen") || kind.includes("frame")) return;
          const bounds = scenePanelBounds();
          ctx.fillStyle = "#070a06";
          ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
          if (drawEncounterBanner(kind, bounds)) return;
          const image = sceneImageFor(kind);
          if (image) {
            drawCroppedImage(image, bounds.x, bounds.y, bounds.w, bounds.h);
          } else {
            if (drawSceneAtlasTile(kind, bounds)) return;
            drawSemanticSceneTile(kind, bounds.x, bounds.y, bounds.w, bounds.h);
          }
          drawSceneVignette(bounds);
        }

        function scenePanelBounds() {
          return {
            x: layout.map.x + 10,
            y: layout.map.y + 12,
            w: layout.map.w - 20,
            h: layout.map.h - 24
          };
        }

        function sceneImageFor(kind) {
          if (kind.includes("encounter") || kind.includes("scorpion")) return bitmapAssets.encounter;
          if (kind.includes("towne_mainstreet") || kind.includes("towne-mainstreet")) return bitmapAssets.sceneMainstreet;
          if (kind.includes("merchant")) return bitmapAssets.sceneMerchant;
          if (kind.includes("smith")) return bitmapAssets.sceneSmith;
          if (kind.includes("inn")) return bitmapAssets.sceneInn;
          if (kind.includes("tavern")) return bitmapAssets.sceneTavern;
          if (kind.includes("church")) return bitmapAssets.sceneChurch;
          if (kind.includes("guild")) return bitmapAssets.sceneGuild;
          if (kind.includes("monster_goblinoid") || kind.includes("monster-goblinoid") || kind.includes("monster.goblinoid")) return bitmapAssets.monsterGoblinoid;
          if (kind.includes("monster_undead") || kind.includes("monster-undead") || kind.includes("monster.undead")) return bitmapAssets.monsterUndead;
          if (kind.includes("monster_beast") || kind.includes("monster-beast") || kind.includes("monster.beast")) return bitmapAssets.monsterBeast;
          if (kind.includes("monster_flying") || kind.includes("monster-flying") || kind.includes("monster.flying")) return bitmapAssets.monsterFlying;
          if (kind.includes("monster_vermin") || kind.includes("monster-vermin") || kind.includes("monster.vermin")) return bitmapAssets.monsterVermin;
          if (kind.includes("monster_brute") || kind.includes("monster-brute") || kind.includes("monster.brute")) return bitmapAssets.monsterBrute;
          if (kind.includes("monster_humanoid_magic") || kind.includes("monster-humanoid-magic") || kind.includes("monster.humanoid_magic")) return bitmapAssets.monsterHumanoidMagic;
          if (kind.includes("monster_boss") || kind.includes("monster-boss") || kind.includes("monster.boss")) return bitmapAssets.monsterBoss;
          return null;
        }

        function sceneAtlasIndex(kind) {
          if (kind.includes("battle_victory") || kind.includes("battle-victory")) return 0;
          if (kind.includes("cave_entrance") || kind.includes("cave-entrance") || kind.endsWith(".cave")) return 1;
          if (kind.includes("inside_cave") || kind.includes("inside-cave") || kind.includes("cave_interior") || kind.includes("cave-interior")) return 2;
          if (kind.includes("death")) return 3;
          if (kind.includes("church")) return 4;
          if (kind.includes("guild")) return 5;
          if (kind.includes("inn")) return 6;
          if (kind.includes("towne_mainstreet") || kind.includes("towne-mainstreet")) return 7;
          if (kind.includes("merchant")) return 8;
          if (kind.includes("smith")) return 9;
          if (kind.includes("tavern")) return 10;
          if (kind.includes("wilderness_hut") || kind.includes("wilderness-hut") || kind.endsWith(".hut")) return 11;
          if (kind.includes("wilderness_no_tent") || kind.includes("wilderness-no-tent")) return 12;
          if (kind.includes("wilderness_tent") || kind.includes("wilderness-tent") || kind.endsWith(".tent")) return 13;
          if (kind.includes("ydarg_castle") || kind.includes("ydarg-castle")) return 14;
          return null;
        }

        function drawSceneAtlasTile(kind, bounds) {
          const index = sceneAtlasIndex(kind);
          const image = bitmapAssets.scenes;
          if (index === null || !image.complete || image.naturalWidth === 0) return false;
          const cols = 5;
          const rows = 3;
          const sourceW = image.naturalWidth / cols;
          const sourceH = image.naturalHeight / rows;
          const col = index % cols;
          const row = Math.floor(index / cols);
          drawAtlasCellCover(image, col * sourceW, row * sourceH, sourceW, sourceH, bounds);
          drawSceneVignette(bounds);
          return true;
        }

        function drawEncounterBanner(kind, bounds) {
          const match = kind.match(/encounter[_-]banner[_-]?(\d+)/);
          if (!match) return false;
          const fallback = randomEncounterImage(Number(match[1]));
          if (!fallback || !fallback.complete || fallback.naturalWidth === 0) return false;
          drawCroppedImage(fallback, bounds.x, bounds.y, bounds.w, bounds.h);
          drawSceneVignette(bounds);
          return true;
        }

        function randomEncounterImage(index) {
          return [
            bitmapAssets.monsterGoblinoid,
            bitmapAssets.monsterFlying,
            bitmapAssets.monsterUndead,
            bitmapAssets.monsterBeast,
            bitmapAssets.monsterVermin
          ][Math.max(0, Math.min(4, index - 1))];
        }

        function drawAtlasCellCover(image, sourceX, sourceY, sourceW, sourceH, bounds) {
          let sx = sourceX;
          let sy = sourceY;
          let sw = sourceW;
          let sh = sourceH;
          const sourceAspect = sourceW / sourceH;
          const targetAspect = bounds.w / bounds.h;
          if (sourceAspect > targetAspect) {
            sw = sourceH * targetAspect;
            sx = sourceX + (sourceW - sw) / 2;
          } else {
            sh = sourceW / targetAspect;
            sy = sourceY + (sourceH - sh) / 2;
          }
          ctx.drawImage(image, sx, sy, sw, sh, bounds.x, bounds.y, bounds.w, bounds.h);
        }

        function drawCroppedImage(image, x, y, w, h) {
          if (!image.complete || image.naturalWidth === 0) {
            drawDither(x, y, w, h, "#151b12", "#0b0f0a", 8);
            return;
          }
          const scale = Math.max(w / image.naturalWidth, h / image.naturalHeight);
          const iw = image.naturalWidth * scale;
          const ih = image.naturalHeight * scale;
          const ix = x + (w - iw) / 2;
          const iy = y + (h - ih) / 2;
          ctx.save();
          ctx.beginPath();
          ctx.rect(x, y, w, h);
          ctx.clip();
          ctx.drawImage(image, ix, iy, iw, ih);
          ctx.restore();
        }

        function drawSceneVignette(bounds) {
          const gradient = ctx.createRadialGradient(
            bounds.x + bounds.w / 2,
            bounds.y + bounds.h / 2,
            bounds.w * 0.2,
            bounds.x + bounds.w / 2,
            bounds.y + bounds.h / 2,
            bounds.w * 0.72
          );
          gradient.addColorStop(0, "rgba(0, 0, 0, 0)");
          gradient.addColorStop(1, "rgba(0, 0, 0, 0.38)");
          ctx.fillStyle = gradient;
          ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
        }

        function drawSemanticSceneTile(kind, x, y, w, h) {
          drawDither(x, y, w, h, "#151b12", "#0b0f0a", 8);
          ctx.fillStyle = "#7e9365";
          ctx.font = "18px Menlo, Monaco, Consolas, monospace";
          ctx.fillText(truncateToWidth(kind.replace(/_/g, " "), w - 32), x + 16, y + h / 2);
        }

        function drawTile(tile, x, y, size) {
          const slug = (tile.slug || "").toLowerCase();
          const settlement = (tile.settlement || "").toLowerCase();
          const variant = tile.variant || 0;
          if (drawTerrainAtlasTile(slug, settlement, variant, x, y, size, tile.center)) return;
          const colors = tileColors(slug, settlement, tile.passable);
          ctx.fillStyle = colors.base;
          ctx.fillRect(x, y, size, size);
          ctx.fillStyle = colors.shadow;
          ctx.fillRect(x, y + size - 5, size, 5);
          if (settlement === "town") drawHouse(x + 7, y + 8, size - 14, size - 12);
          else if (settlement === "castle") drawCastle(x + 6, y + 7, size - 12, size - 11);
          else if (settlement === "hut") drawHut(x + 8, y + 12, size - 16, size - 15);
          else if (slug.includes("forest")) drawForest(x, y, size, variant);
          else if (slug.includes("mountain")) drawMountain(x, y, size, variant);
          else if (slug.includes("water") || slug.includes("sea") || slug.includes("river")) drawWater(x, y, size, variant);
          else if (slug.includes("desert")) drawDesert(x, y, size, variant);
          else if (slug.includes("foothills")) drawFoothills(x, y, size, variant);
          else if (slug.includes("swamp")) drawSwamp(x, y, size, variant);
          else drawPlain(x, y, size, variant);
          ctx.strokeStyle = "rgba(5, 7, 5, 0.45)";
          ctx.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
          if (tile.center) {
            ctx.strokeStyle = "#f2d35e";
            ctx.lineWidth = 3;
            ctx.strokeRect(x + 3.5, y + 3.5, size - 7, size - 7);
            ctx.lineWidth = 1;
          }
        }

        function drawTerrainAtlasTile(slug, settlement, variant, x, y, size, center) {
          const atlas = bitmapAssets.terrain;
          const index = terrainAtlasIndex(slug, settlement);
          if (!index || !atlas.complete || atlas.naturalWidth === 0) return false;
          const cols = 8;
          const rows = 5;
          const sourceW = atlas.naturalWidth / cols;
          const sourceH = atlas.naturalHeight / rows;
          const sourceX = (index.col + (variant % 4)) * sourceW;
          const sourceY = index.row * sourceH;
          ctx.save();
          ctx.imageSmoothingEnabled = true;
          ctx.drawImage(atlas, sourceX, sourceY, sourceW, sourceH, x, y, size, size);
          ctx.restore();
          if (center) {
            drawPartyMarker(x + size / 2, y + size / 2, size);
          }
          return true;
        }

        function drawPartyMarker(cx, cy, size) {
          ctx.save();
          ctx.translate(cx, cy);
          const s = size / 64;
          ctx.lineCap = "round";
          ctx.lineJoin = "round";
          ctx.fillStyle = "rgba(5, 7, 5, 0.58)";
          ctx.beginPath();
          ctx.ellipse(0, size * 0.24, size * 0.28, size * 0.08, 0, 0, Math.PI * 2);
          ctx.fill();
          ctx.strokeStyle = "#291b10";
          ctx.lineWidth = Math.max(2, 4 * s);
          ctx.beginPath();
          ctx.moveTo(-15 * s, 8 * s);
          ctx.lineTo(-20 * s, 23 * s);
          ctx.moveTo(-5 * s, 10 * s);
          ctx.lineTo(-2 * s, 24 * s);
          ctx.moveTo(9 * s, 8 * s);
          ctx.lineTo(12 * s, 23 * s);
          ctx.moveTo(19 * s, 5 * s);
          ctx.lineTo(24 * s, 20 * s);
          ctx.stroke();
          ctx.fillStyle = "#7b4b27";
          ctx.beginPath();
          ctx.ellipse(0, 3 * s, 23 * s, 12 * s, -0.05, 0, Math.PI * 2);
          ctx.fill();
          ctx.beginPath();
          ctx.moveTo(18 * s, -4 * s);
          ctx.lineTo(31 * s, -12 * s);
          ctx.lineTo(34 * s, -3 * s);
          ctx.lineTo(24 * s, 7 * s);
          ctx.closePath();
          ctx.fill();
          ctx.fillStyle = "#2d1b11";
          ctx.beginPath();
          ctx.moveTo(-20 * s, -1 * s);
          ctx.lineTo(-31 * s, -10 * s);
          ctx.lineTo(-27 * s, 4 * s);
          ctx.closePath();
          ctx.fill();
          ctx.fillStyle = "#c8a357";
          ctx.beginPath();
          ctx.arc(2 * s, -17 * s, 5 * s, 0, Math.PI * 2);
          ctx.fill();
          ctx.fillStyle = "#44613a";
          ctx.beginPath();
          ctx.moveTo(-8 * s, -10 * s);
          ctx.lineTo(9 * s, -12 * s);
          ctx.lineTo(13 * s, 5 * s);
          ctx.lineTo(-13 * s, 4 * s);
          ctx.closePath();
          ctx.fill();
          ctx.strokeStyle = "#e4d385";
          ctx.lineWidth = Math.max(1.5, 2 * s);
          ctx.beginPath();
          ctx.moveTo(9 * s, -14 * s);
          ctx.lineTo(23 * s, -28 * s);
          ctx.stroke();
          ctx.fillStyle = "#f2d35e";
          ctx.beginPath();
          ctx.arc(26 * s, -29 * s, 2.5 * s, 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
        }

        function terrainAtlasIndex(slug, settlement) {
          if (settlement === "town") return { row: 3, col: 4 };
          if (settlement === "castle") return { row: 4, col: 4 };
          if (settlement === "hut") return { row: 4, col: 0 };
          if (slug.includes("forest")) return { row: 0, col: 4 };
          if (slug.includes("mountain")) return { row: 1, col: 0 };
          if (slug.includes("water") || slug.includes("sea") || slug.includes("river")) return { row: 3, col: 0 };
          if (slug.includes("desert")) return { row: 2, col: 0 };
          if (slug.includes("foothills")) return { row: 1, col: 4 };
          if (slug.includes("swamp")) return { row: 2, col: 4 };
          if (slug.includes("plain")) return { row: 0, col: 0 };
          return { row: 0, col: 0 };
        }

        function tileColors(slug, settlement, passable) {
          if (settlement === "town") return { base: "#6e7b45", shadow: "#4c5730" };
          if (settlement === "castle") return { base: "#74706a", shadow: "#504d49" };
          if (settlement === "hut") return { base: "#617348", shadow: "#3e4b30" };
          if (slug.includes("forest")) return { base: "#295738", shadow: "#1f3b29" };
          if (slug.includes("mountain")) return { base: "#6b6c64", shadow: "#484a44" };
          if (slug.includes("water") || slug.includes("sea") || slug.includes("river")) return { base: "#2a5d74", shadow: "#1b3d4c" };
          if (slug.includes("desert")) return { base: "#c7ad64", shadow: "#8b7040" };
          if (slug.includes("foothills")) return { base: "#7b8150", shadow: "#555b38" };
          if (slug.includes("swamp")) return { base: "#46563a", shadow: "#2d3828" };
          if (!passable) return { base: "#3b3d34", shadow: "#292b24" };
          return { base: "#617b44", shadow: "#435c31" };
        }

        function drawPlain(x, y, size, variant) {
          const tufts = 10 + variant * 2;
          ctx.fillStyle = variant % 2 === 0 ? "#96ad61" : "#86a55a";
          for (let i = 0; i < tufts; i += 1) {
            const tx = x + 6 + ((i * 17 + variant * 13) % (size - 12));
            const ty = y + 8 + ((i * 11 + variant * 9) % (size - 18));
            ctx.fillRect(tx, ty, 7, 2);
            if ((i + variant) % 4 === 0) {
              ctx.fillStyle = "#d8cc78";
              ctx.fillRect(tx + 2, ty - 3, 2, 2);
              ctx.fillStyle = variant % 2 === 0 ? "#96ad61" : "#86a55a";
            }
          }
        }

        function drawForest(x, y, size, variant) {
          const trees = [4, 5, 6, 5][variant % 4];
          for (let i = 0; i < trees; i += 1) {
            const tx = x + 8 + ((i * 19 + variant * 11) % (size - 24));
            const ty = y + 7 + ((i * 13 + variant * 7) % (size - 30));
            ctx.fillStyle = "#593d24";
            ctx.fillRect(tx + 7, ty + 20, 5, 14);
            ctx.fillStyle = i % 2 === 0 ? "#78ad5b" : "#5f9648";
            triangle(tx - 3, ty + 24, tx + 10, ty, tx + 23, ty + 24);
            ctx.fillStyle = "#3f7439";
            triangle(tx, ty + 32, tx + 10, ty + 10, tx + 21, ty + 32);
          }
        }

        function drawMountain(x, y, size, variant) {
          const peaks = [
            [[0.04, 0.88, 0.34, 0.12, 0.66, 0.88], [0.38, 0.9, 0.68, 0.24, 0.96, 0.9]],
            [[0.02, 0.9, 0.42, 0.18, 0.78, 0.9], [0.24, 0.88, 0.62, 0.08, 0.98, 0.88]],
            [[0.08, 0.9, 0.32, 0.26, 0.58, 0.9], [0.35, 0.9, 0.74, 0.13, 0.94, 0.9]],
            [[0.03, 0.9, 0.5, 0.1, 0.97, 0.9]]
          ][variant % 4];
          for (const peak of peaks) {
            ctx.fillStyle = "#9d9f92";
            triangle(x + peak[0] * size, y + peak[1] * size, x + peak[2] * size, y + peak[3] * size, x + peak[4] * size, y + peak[5] * size);
            ctx.fillStyle = "#cfd0c0";
            triangle(x + peak[2] * size, y + peak[3] * size, x + (peak[2] + 0.1) * size, y + (peak[3] + 0.22) * size, x + (peak[2] - 0.12) * size, y + (peak[3] + 0.21) * size);
            ctx.fillStyle = "rgba(60, 62, 58, 0.45)";
            triangle(x + peak[2] * size, y + peak[3] * size, x + peak[4] * size, y + peak[5] * size, x + (peak[2] + 0.08) * size, y + (peak[3] + 0.26) * size);
          }
        }

        function drawFoothills(x, y, size, variant) {
          ctx.fillStyle = variant % 2 === 0 ? "#a4aa6d" : "#909e5c";
          for (let i = 0; i < 3; i += 1) {
            const left = x + (i * 0.28 + 0.02 + variant * 0.015) * size;
            const top = y + (0.28 + ((i + variant) % 2) * 0.08) * size;
            triangle(left, y + size * 0.86, left + size * 0.18, top, left + size * 0.38, y + size * 0.86);
          }
          ctx.fillStyle = "#6f7548";
          ctx.fillRect(x + 6, y + size - 11, size - 12, 4);
        }

        function drawWater(x, y, size, variant) {
          ctx.strokeStyle = "#8cc5c6";
          ctx.lineWidth = 2;
          for (let i = 0; i < 5; i += 1) {
            ctx.beginPath();
            ctx.moveTo(x + 6, y + 10 + i * 10 + variant);
            ctx.quadraticCurveTo(x + size * 0.42, y + 4 + i * 10 + variant * 2, x + size - 6, y + 10 + i * 10);
            ctx.stroke();
          }
          ctx.lineWidth = 1;
        }

        function drawDesert(x, y, size, variant) {
          ctx.strokeStyle = "#f0d98b";
          ctx.lineWidth = 2;
          for (let i = 0; i < 4; i += 1) {
            ctx.beginPath();
            ctx.moveTo(x + 5, y + 13 + i * 11);
            ctx.quadraticCurveTo(x + size * (0.35 + variant * 0.04), y + 3 + i * 11, x + size - 5, y + 12 + i * 11);
            ctx.stroke();
          }
          ctx.lineWidth = 1;
          if (variant === 1 || variant === 3) {
            ctx.fillStyle = "#7f8a50";
            ctx.fillRect(x + size * 0.68, y + size * 0.46, 4, size * 0.28);
            ctx.fillRect(x + size * 0.6, y + size * 0.56, 12, 3);
            ctx.fillRect(x + size * 0.72, y + size * 0.5, 11, 3);
          } else {
            ctx.fillStyle = "#9b7740";
            ctx.fillRect(x + size * 0.67, y + size * 0.7, 13, 6);
            ctx.fillRect(x + size * 0.24, y + size * 0.42, 9, 5);
          }
        }

        function drawSwamp(x, y, size, variant) {
          ctx.fillStyle = "#394a35";
          ctx.beginPath();
          ctx.ellipse(x + size * 0.48, y + size * 0.55, size * 0.32, size * 0.18, 0, 0, Math.PI * 2);
          ctx.fill();
          ctx.fillStyle = "#798e4f";
          for (let i = 0; i < 10; i += 1) {
            const tx = x + 6 + ((i * 13 + variant * 9) % (size - 12));
            const ty = y + 8 + ((i * 7 + variant * 5) % (size - 16));
            ctx.fillRect(tx, ty, 4, 12);
            ctx.fillRect(tx - 3, ty + 5, 10, 3);
          }
        }

        function drawHouse(x, y, w, h) {
          ctx.fillStyle = "#7c5334";
          ctx.fillRect(x, y + h * 0.35, w, h * 0.65);
          ctx.fillStyle = "#4b2e24";
          triangle(x - 8, y + h * 0.42, x + w / 2, y, x + w + 8, y + h * 0.42);
          ctx.fillStyle = "#d7bf73";
          ctx.fillRect(x + w * 0.18, y + h * 0.52, w * 0.18, h * 0.18);
          ctx.fillStyle = "#3b2419";
          ctx.fillRect(x + w * 0.58, y + h * 0.58, w * 0.18, h * 0.42);
        }

        function drawHut(x, y, w, h) {
          ctx.fillStyle = "#6b4a2a";
          ctx.fillRect(x, y + h * 0.4, w, h * 0.6);
          ctx.fillStyle = "#3d2c1e";
          triangle(x - 3, y + h * 0.45, x + w / 2, y, x + w + 3, y + h * 0.45);
        }

        function drawCastle(x, y, w, h) {
          ctx.fillStyle = "#aaa69a";
          ctx.fillRect(x, y + h * 0.25, w, h * 0.75);
          ctx.fillRect(x + 2, y, w * 0.22, h);
          ctx.fillRect(x + w * 0.75, y, w * 0.22, h);
          ctx.fillStyle = "#3c3936";
          ctx.fillRect(x + w * 0.42, y + h * 0.58, w * 0.16, h * 0.42);
        }

        function drawStatus(status, lines) {
          statusHotspots = [];
          const x = layout.status.x;
          const y = layout.status.y;
          const w = layout.status.w;
          if (!status) {
            drawFittedText(lines.slice(0, 5).join("   "), x + 20, y + 44, w - 40, 16, 12, "#d6e0b3");
            return;
          }

          const chips = [
            { label: "TIME", value: status.time, color: "#f0d46d", width: 132 },
            { label: "ST", value: status.condition, color: statusColor(status.condition), width: 132 },
            { label: "POS", value: status.x + "x" + status.y, color: "#9ec1e4", width: 112 },
            { label: "FD", value: String(status.food), color: "#d6e68d", width: 92 },
            { label: "MV", value: String(status.moves), color: "#a9d4a2", width: 86 },
            { label: "AU", value: String(status.gold), color: "#e8c56a", width: 112 }
          ];
          const gap = 8;
          const total = chips.reduce((sum, chip) => sum + chip.width, 0) + gap * (chips.length - 1);
          let cx = x + Math.floor((w - total) / 2);
          for (const chip of chips) {
            drawStatusChip(cx, y + 14, chip.width, chip.label, chip.value, chip.color);
            cx += chip.width + gap;
          }

          const members = status.members || [];
          const memberGap = 10;
          const memberW = Math.min(220, Math.floor((w - 48 - memberGap * Math.max(0, members.length - 1)) / Math.max(1, members.length)));
          let mx = x + Math.floor((w - (memberW * members.length + memberGap * Math.max(0, members.length - 1))) / 2);
          for (const member of members) {
            drawMemberStatus(member, mx, y + 54, memberW);
            mx += memberW + memberGap;
          }
        }

        function drawStatusChip(x, y, w, label, value, color) {
          ctx.font = "10px Menlo, Monaco, Consolas, monospace";
          ctx.fillStyle = "#899a72";
          ctx.fillText(label, x + 8, y + 14);
          ctx.font = "700 13px Menlo, Monaco, Consolas, monospace";
          ctx.fillStyle = color;
          ctx.fillText(truncateToWidth(value, w - 48), x + 42, y + 14);
        }

        function drawMemberStatus(member, x, y, w) {
          const label = member.slot + " " + member.name;
          const barX = x + 6;
          const barY = y + 15;
          const barW = w - 12;
          ctx.font = "11px Menlo, Monaco, Consolas, monospace";
          ctx.fillStyle = member.dead ? "#b86f68" : "#d8d0ae";
          ctx.fillText(truncateToWidth(label, w - 12), x + 6, y + 9);
          drawTinyBar(barX, barY, barW, 5, member.hp, member.hpMax, member.dead ? "#7d3533" : "#b85748");
          statusHotspots.push({ x: barX, y: barY - 2, w: barW, h: 9, text: member.name + " health: " + member.hp + "/" + member.hpMax });
          if (member.manaMax) {
            drawTinyBar(barX, barY + 9, barW, 4, member.mana || 0, member.manaMax, "#5b8fce");
            statusHotspots.push({ x: barX, y: barY + 7, w: barW, h: 8, text: member.name + " mana: " + (member.mana || 0) + "/" + member.manaMax });
          }
        }

        function drawTinyBar(x, y, w, h, value, max, color) {
          const ratio = Math.max(0, Math.min(1, value / Math.max(1, max)));
          ctx.fillStyle = "rgba(5, 8, 5, 0.66)";
          ctx.fillRect(x, y, w, h);
          ctx.fillStyle = color;
          ctx.fillRect(x, y, Math.max(1, Math.floor(w * ratio)), h);
        }

        function statusColor(condition) {
          if (condition === "STARVING") return "#dc6b57";
          if (condition === "HUNGRY") return "#e0ad55";
          return "#9bd073";
        }

        function drawTextPanel(frame) {
          const x = layout.text.x + 28;
          const y = layout.text.y + 58;
          const maxWidth = layout.text.w - 56;
          ctx.fillStyle = "#f1ead6";
          ctx.font = "27px Menlo, Monaco, Consolas, monospace";
          ctx.fillText(truncateToWidth(frame.title || "", maxWidth), x, y);
          ctx.font = "15px Menlo, Monaco, Consolas, monospace";
          ctx.fillStyle = "#bdb496";
          ctx.fillText(truncateToWidth(frame.subtitle || "", maxWidth), x, y + 30);
          ctx.font = "16px Menlo, Monaco, Consolas, monospace";
          ctx.fillStyle = "#efe4c6";
          let ty = y + 64;
          const bottom = layout.text.y + layout.text.h - 32;
          for (const line of (frame.textLines || [])) {
            const wrapped = wrapCanvasLines(line, maxWidth);
            for (const part of wrapped) {
              if (ty > bottom) return;
              ctx.fillText(part, x, ty);
              ty += 20;
            }
          }
        }

        function drawMessage(message) {
          if (!message) return;
          const y = layout.text.y + layout.text.h - 104;
          softPanel(layout.text.x + 24, y, layout.text.w - 48, 82, "rgba(20, 24, 17, 0.78)");
          ctx.font = "20px Menlo, Monaco, Consolas, monospace";
          ctx.fillStyle = "#f0d58a";
          wrapCanvasText(message, layout.text.x + 48, y + 36, layout.text.w - 96, 25);
        }

        function drawFittedText(text, x, y, maxWidth, startSize, minSize, color) {
          ctx.fillStyle = color;
          let size = startSize;
          do {
            ctx.font = size + "px Menlo, Monaco, Consolas, monospace";
            if (ctx.measureText(text).width <= maxWidth) break;
            size -= 1;
          } while (size >= minSize);
          ctx.fillText(truncateToWidth(text, maxWidth), x, y);
        }

        function truncateToWidth(text, maxWidth) {
          if (ctx.measureText(text).width <= maxWidth) return text;
          let end = text.length;
          while (end > 0 && ctx.measureText(text.slice(0, end) + "...").width > maxWidth) {
            end -= 1;
          }
          return text.slice(0, end) + "...";
        }

        function drawAnsiArt(lines, x, y, fontSize, color, lineHeight, maxLines) {
          ctx.textBaseline = "top";
          ctx.font = fontSize + "px Menlo, Monaco, Consolas, monospace";
          ctx.fillStyle = color;
          for (const [index, line] of lines.slice(0, maxLines).entries()) {
            ctx.fillText(line, x, y + index * lineHeight);
          }
          ctx.textBaseline = "alphabetic";
        }

        function softPanel(x, y, w, h, fill) {
          ctx.fillStyle = fill;
          ctx.fillRect(x, y, w, h);
        }

        function drawMountains(x, baseY, color) {
          ctx.fillStyle = color;
          for (let i = 0; i < 9; i += 1) {
            const start = x + i * 160 - 40;
            triangle(start, baseY, start + 95, baseY - 122 - (i % 3) * 28, start + 210, baseY);
          }
        }

        function drawDither(x, y, w, h, a, b, step) {
          ctx.fillStyle = a;
          ctx.fillRect(x, y, w, h);
          ctx.fillStyle = b;
          for (let yy = y; yy < y + h; yy += step) {
            for (let xx = x + ((yy / step) % 2) * step; xx < x + w; xx += step * 2) {
              ctx.fillRect(xx, yy, step, step);
            }
          }
        }

        function drawScorpionMark(x, y, scale, color) {
          ctx.save();
          ctx.translate(x, y);
          ctx.scale(scale, scale);
          ctx.strokeStyle = color;
          ctx.fillStyle = color;
          ctx.lineWidth = 10;
          ctx.lineCap = "round";
          ctx.beginPath();
          ctx.ellipse(0, 0, 58, 34, 0, 0, Math.PI * 2);
          ctx.fill();
          for (let i = -2; i <= 2; i += 1) {
            const lx = i * 22;
            ctx.beginPath();
            ctx.moveTo(lx, 12);
            ctx.lineTo(lx - 42, 50 + Math.abs(i) * 8);
            ctx.lineTo(lx - 70, 44 + Math.abs(i) * 10);
            ctx.stroke();
            ctx.beginPath();
            ctx.moveTo(lx, 12);
            ctx.lineTo(lx + 42, 50 + Math.abs(i) * 8);
            ctx.lineTo(lx + 70, 44 + Math.abs(i) * 10);
            ctx.stroke();
          }
          ctx.lineWidth = 14;
          ctx.beginPath();
          ctx.moveTo(50, -4);
          ctx.quadraticCurveTo(94, -48, 128, -12);
          ctx.stroke();
          ctx.beginPath();
          ctx.moveTo(-50, -4);
          ctx.quadraticCurveTo(-94, -48, -128, -12);
          ctx.stroke();
          ctx.lineWidth = 12;
          ctx.beginPath();
          ctx.moveTo(0, -26);
          ctx.quadraticCurveTo(36, -74, 8, -114);
          ctx.quadraticCurveTo(-18, -150, 20, -178);
          ctx.stroke();
          triangle(20, -194, 36, -160, 4, -166);
          ctx.restore();
        }

        function triangle(x1, y1, x2, y2, x3, y3) {
          ctx.beginPath();
          ctx.moveTo(x1, y1);
          ctx.lineTo(x2, y2);
          ctx.lineTo(x3, y3);
          ctx.closePath();
          ctx.fill();
        }

        function wrapCanvasText(text, x, y, maxWidth, lineHeight) {
          for (const line of wrapCanvasLines(text, maxWidth)) {
            ctx.fillText(line, x, y);
            y += lineHeight;
          }
        }

        function wrapCanvasLines(text, maxWidth) {
          const words = String(text || "").split(" ");
          const lines = [];
          let line = "";
          for (const word of words) {
            const test = line ? line + " " + word : word;
            if (ctx.measureText(test).width > maxWidth && line) {
              lines.push(line);
              line = word;
            } else {
              line = test;
            }
          }
          if (line) lines.push(line);
          return lines.length > 0 ? lines : [""];
        }

        connect();
      </script>
    </body>
    </html>
    """.trimIndent()
