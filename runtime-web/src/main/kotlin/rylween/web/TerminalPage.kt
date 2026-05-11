package rylween.web

private const val ANSI_ROWS = 25
private const val ANSI_COLUMNS = 80

internal fun terminalPageHtml(): String =
    """
    <!doctype html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Rylween</title>
      <style>
        :root {
          color-scheme: dark;
          --page: #141414;
          --panel: #202020;
          --panel-soft: #2b2b2b;
          --text: #f1f1e6;
          --muted: #b6b2a4;
          --accent: #74b9a9;
          --black: #050505;
          --red: #dc5f64;
          --green: #7ecb72;
          --yellow: #e5c565;
          --blue: #5d88d8;
          --magenta: #bd78c8;
          --cyan: #62c8d7;
          --white: #f2eee2;
        }

        * {
          box-sizing: border-box;
        }

        html,
        body {
          min-height: 100%;
          margin: 0;
        }

        body {
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
          min-height: 48px;
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 16px;
          padding: 8px 14px;
          border-bottom: 1px solid #353535;
          background: #1a1a1a;
        }

        .brand {
          display: flex;
          align-items: center;
          gap: 10px;
          min-width: 0;
        }

        .mark {
          width: 22px;
          height: 22px;
          border: 1px solid #8ebbad;
          background: #263d36;
          display: grid;
          place-items: center;
          color: var(--accent);
          font-size: 14px;
          line-height: 1;
        }

        .title {
          font-size: 15px;
          font-weight: 700;
          letter-spacing: 0;
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
          background: #c75f5f;
        }

        .dot.connected {
          background: #76c26a;
        }

        .stage {
          min-height: 0;
          display: grid;
          place-items: center;
          padding: 16px;
        }

        .terminal-frame {
          width: min(100%, 1180px);
          height: min(calc(100vh - 84px), 760px);
          min-height: 360px;
          border: 1px solid #3c3c3c;
          background: var(--black);
          overflow: auto;
          box-shadow: 0 18px 48px rgb(0 0 0 / 34%);
        }

        #terminal {
          margin: 0;
          width: max-content;
          min-width: 100%;
          min-height: 100%;
          padding: 14px;
          color: var(--white);
          background: var(--black);
          font-family: "SFMono-Regular", "Cascadia Mono", "Liberation Mono", Menlo, Consolas, monospace;
          font-size: clamp(12px, 1.45vw, 19px);
          line-height: 1.18;
          letter-spacing: 0;
          white-space: pre;
          outline: none;
          user-select: none;
        }

        .fg-black { color: var(--black); }
        .fg-red { color: var(--red); }
        .fg-green { color: var(--green); }
        .fg-yellow { color: var(--yellow); }
        .fg-blue { color: var(--blue); }
        .fg-magenta { color: var(--magenta); }
        .fg-cyan { color: var(--cyan); }
        .fg-white { color: var(--white); }
        .bg-black { background: var(--black); }
        .bg-red { background: #651b21; }
        .bg-green { background: #245525; }
        .bg-yellow { background: #65571d; }
        .bg-blue { background: #102e69; }
        .bg-magenta { background: #562264; }
        .bg-cyan { background: #185763; }
        .bg-white { background: #cfc9bb; }

        @media (max-width: 720px) {
          .topbar {
            align-items: flex-start;
            flex-direction: column;
            gap: 4px;
          }

          .stage {
            padding: 8px;
          }

          .terminal-frame {
            height: calc(100vh - 92px);
            min-height: 300px;
          }

          #terminal {
            padding: 10px;
            font-size: 12px;
          }
        }
      </style>
    </head>
    <body>
      <main class="shell">
        <header class="topbar">
          <div class="brand">
            <div class="mark">R</div>
            <div class="title">Rylween</div>
          </div>
          <div class="status">
            <span id="connectionDot" class="dot"></span>
            <span id="connectionText">Connecting</span>
          </div>
        </header>
        <section class="stage">
          <div class="terminal-frame">
            <pre id="terminal" tabindex="0" aria-label="Rylween terminal"></pre>
          </div>
        </section>
      </main>
      <script>
        const rows = $ANSI_ROWS;
        const columns = $ANSI_COLUMNS;
        const terminal = document.getElementById("terminal");
        const connectionDot = document.getElementById("connectionDot");
        const connectionText = document.getElementById("connectionText");
        const colors = ["black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"];
        const blankStyle = { fg: "white", bg: "black" };
        const state = {
          row: 0,
          col: 0,
          fg: "white",
          bg: "black",
          savedRow: 0,
          savedCol: 0,
          cells: [],
        };
        let socket = null;

        function cloneStyle() {
          return { fg: state.fg, bg: state.bg };
        }

        function blankCell() {
          return { ch: " ", fg: blankStyle.fg, bg: blankStyle.bg };
        }

        function resetCells() {
          state.cells = [];
          for (let row = 0; row < rows; row += 1) {
            const line = [];
            for (let col = 0; col < columns; col += 1) {
              line.push(blankCell());
            }
            state.cells.push(line);
          }
          state.row = 0;
          state.col = 0;
          state.fg = "white";
          state.bg = "black";
        }

        function setStatus(text, connected) {
          connectionText.textContent = text;
          connectionDot.classList.toggle("connected", connected);
        }

        function escapeHtml(value) {
          return value
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;");
        }

        function classFor(cell) {
          return "fg-" + cell.fg + " bg-" + cell.bg;
        }

        function render() {
          const html = [];
          for (const line of state.cells) {
            let currentClass = "";
            for (const cell of line) {
              const cellClass = classFor(cell);
              if (cellClass !== currentClass) {
                if (currentClass !== "") {
                  html.push("</span>");
                }
                currentClass = cellClass;
                html.push('<span class="' + currentClass + '">');
              }
              html.push(escapeHtml(cell.ch));
            }
            if (currentClass !== "") {
              html.push("</span>");
            }
            html.push("\n");
          }
          terminal.innerHTML = html.join("");
        }

        function scroll() {
          state.cells.shift();
          state.cells.push(Array.from({ length: columns }, blankCell));
          state.row = rows - 1;
        }

        function newline() {
          state.row += 1;
          state.col = 0;
          if (state.row >= rows) {
            scroll();
          }
        }

        function putChar(ch) {
          if (ch === "\n") {
            newline();
            return;
          }
          if (ch === "\r") {
            state.col = 0;
            return;
          }
          if (state.col >= columns) {
            newline();
          }
          state.cells[state.row][state.col] = { ch, ...cloneStyle() };
          state.col += 1;
        }

        function clearDisplay(mode) {
          if (mode === 2 || mode === 3) {
            resetCells();
            return;
          }
          if (mode === 1) {
            for (let row = 0; row <= state.row; row += 1) {
              const end = row === state.row ? state.col : columns;
              for (let col = 0; col < end; col += 1) {
                state.cells[row][col] = blankCell();
              }
            }
            return;
          }
          for (let row = state.row; row < rows; row += 1) {
            const start = row === state.row ? state.col : 0;
            for (let col = start; col < columns; col += 1) {
              state.cells[row][col] = blankCell();
            }
          }
        }

        function clearLine(mode) {
          if (mode === 2) {
            for (let col = 0; col < columns; col += 1) {
              state.cells[state.row][col] = blankCell();
            }
            return;
          }
          if (mode === 1) {
            for (let col = 0; col <= state.col; col += 1) {
              state.cells[state.row][col] = blankCell();
            }
            return;
          }
          for (let col = state.col; col < columns; col += 1) {
            state.cells[state.row][col] = blankCell();
          }
        }

        function numberParam(params, index, fallback) {
          const value = Number.parseInt(params[index] || "", 10);
          if (Number.isNaN(value)) {
            return fallback;
          }
          return value;
        }

        function applySgr(params) {
          const codes = params.length === 0 || params[0] === "" ? ["0"] : params;
          for (const raw of codes) {
            const code = Number.parseInt(raw, 10);
            if (Number.isNaN(code) || code === 0) {
              state.fg = "white";
              state.bg = "black";
            } else if (code >= 30 && code <= 37) {
              state.fg = colors[code - 30];
            } else if (code >= 40 && code <= 47) {
              state.bg = colors[code - 40];
            }
          }
        }

        function applyCsi(command, params) {
          if (command === "m") {
            applySgr(params);
          } else if (command === "H" || command === "f") {
            state.row = Math.max(0, Math.min(rows - 1, numberParam(params, 0, 1) - 1));
            state.col = Math.max(0, Math.min(columns - 1, numberParam(params, 1, 1) - 1));
          } else if (command === "A") {
            state.row = Math.max(0, state.row - numberParam(params, 0, 1));
          } else if (command === "B") {
            state.row = Math.min(rows - 1, state.row + numberParam(params, 0, 1));
          } else if (command === "C") {
            state.col = Math.min(columns - 1, state.col + numberParam(params, 0, 1));
          } else if (command === "D") {
            state.col = Math.max(0, state.col - numberParam(params, 0, 1));
          } else if (command === "J") {
            clearDisplay(numberParam(params, 0, 0));
          } else if (command === "K") {
            clearLine(numberParam(params, 0, 0));
          } else if (command === "s") {
            state.savedRow = state.row;
            state.savedCol = state.col;
          } else if (command === "u") {
            state.row = state.savedRow;
            state.col = state.savedCol;
          }
        }

        function consumeCsi(text, start) {
          let index = start;
          while (index < text.length) {
            const code = text.charCodeAt(index);
            if (code >= 0x40 && code <= 0x7e) {
              const body = text.slice(start, index);
              const params = body.replace("?", "").split(";");
              applyCsi(text[index], params);
              return index + 1;
            }
            index += 1;
          }
          return index;
        }

        function consumeAnsi(text) {
          let index = 0;
          while (index < text.length) {
            const ch = text[index];
            if (ch === "\u001b" && text[index + 1] === "[") {
              index = consumeCsi(text, index + 2);
              continue;
            }
            putChar(ch);
            index += 1;
          }
          render();
        }

        function userName() {
          const stored = window.localStorage.getItem("rylweenUser");
          if (stored && stored.trim() !== "") {
            return stored;
          }
          const entered = window.prompt("Adventurer name", "adventurer") || "adventurer";
          window.localStorage.setItem("rylweenUser", entered);
          return entered;
        }

        function connect() {
          const scheme = window.location.protocol === "https:" ? "wss" : "ws";
          const path = "/ws/play?user=" + encodeURIComponent(userName());
          socket = new WebSocket(scheme + "://" + window.location.host + path);
          socket.addEventListener("open", () => {
            setStatus("Connected", true);
            terminal.focus();
          });
          socket.addEventListener("message", event => consumeAnsi(String(event.data)));
          socket.addEventListener("close", () => setStatus("Disconnected", false));
          socket.addEventListener("error", () => setStatus("Connection error", false));
        }

        function sendInput(value) {
          if (!socket || socket.readyState !== WebSocket.OPEN) {
            return;
          }
          socket.send("key:" + value);
        }

        terminal.addEventListener("keydown", event => {
          let value = "";
          if (event.key === "ArrowUp") {
            value = "\u001b[A";
          } else if (event.key === "ArrowDown") {
            value = "\u001b[B";
          } else if (event.key === "ArrowRight") {
            value = "\u001b[C";
          } else if (event.key === "ArrowLeft") {
            value = "\u001b[D";
          } else if (event.key === "Enter") {
            value = "\r";
          } else if (event.key === "Backspace") {
            value = "\u007f";
          } else if (event.key.length === 1 && !event.metaKey && !event.ctrlKey) {
            value = event.key;
          }
          if (value !== "") {
            event.preventDefault();
            sendInput(value);
          }
        });

        resetCells();
        render();
        connect();
      </script>
    </body>
    </html>
    """.trimIndent()
