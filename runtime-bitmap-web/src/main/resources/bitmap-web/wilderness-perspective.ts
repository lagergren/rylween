(() => {
    type ToolFacing = "North" | "East" | "South" | "West";
    type ToolRect = { x: number; y: number; w: number; h: number };
    type ToolTile = {
        slug?: string;
        settlement?: string;
        variant?: number;
        passable?: boolean;
        center?: boolean;
        waterMask?: number;
    };
    type ToolPerspectiveCell = {
        worldX: number;
        worldY: number;
        forward: number;
        side: number;
        distanceBand: number;
        scale: number;
        rect: ToolRect;
        zOrder: number;
        tile: ToolTile;
    };
    type ToolPerspectiveView = { facing: ToolFacing; cells: ToolPerspectiveCell[] };
    type ToolPerspectiveSet = { width: number; height: number; views: ToolPerspectiveView[] };
    type ToolMap = { rows: ToolTile[][]; tileSize: number; perspective: ToolPerspectiveSet };
    type ToolResponse = {
        position: { x: number; y: number };
        facing: ToolFacing;
        mapWidth: number;
        mapHeight: number;
        viewport: ToolMap;
    };
    type ToolSnapshot = {
        x: number;
        y: number;
        facing: ToolFacing;
        mode: "2d" | "3d";
        cells: number;
        mapCells: number;
    };
    type ToolSmokeWindow = Window & {
        __rylweenWildernessPerspectiveTool?: () => ToolSnapshot;
        __rylweenWildernessPerspectiveSet?: (
            x: number,
            y: number,
            facing: ToolFacing,
            mode: "2d" | "3d",
        ) => Promise<ToolSnapshot>;
    };

    function toolElement<T extends HTMLElement>(id: string, elementType: { new(): T }): T {
        const element = document.getElementById(id);
        if (!(element instanceof elementType)) throw new Error("Missing #" + id);
        return element;
    }

    const toolCanvas = toolElement("wildernessTool", HTMLCanvasElement);
    const toolContext = toolCanvas.getContext("2d");
    if (!toolContext) throw new Error("Canvas 2D context is unavailable");
    const toolCtx: CanvasRenderingContext2D = toolContext;
    const toolX = toolElement("toolX", HTMLInputElement);
    const toolY = toolElement("toolY", HTMLInputElement);
    const toolFacing = toolElement("toolFacing", HTMLSelectElement);
    const toolPerspective = toolElement("toolPerspective", HTMLInputElement);
    const toolStatus = toolElement("toolStatus", HTMLElement);
    const toolSpriteCache = new Map<string, HTMLCanvasElement>();

    let toolState = {
        x: 12,
        y: 10,
        facing: "North" as ToolFacing,
        perspective: true,
        mapWidth: 0,
        mapHeight: 0,
        data: null as ToolResponse | null,
    };

    function toolSnapshot(): ToolSnapshot {
        const view = toolPerspectiveView(toolState.data?.viewport || null, toolState.facing);
        return {
            x: toolState.x,
            y: toolState.y,
            facing: toolState.facing,
            mode: toolState.perspective ? "3d" : "2d",
            cells: view?.cells.length ?? 0,
            mapCells: (toolState.data?.viewport.rows || []).reduce((count, row) => count + row.length, 0),
        };
    }

    (window as ToolSmokeWindow).__rylweenWildernessPerspectiveTool = toolSnapshot;
    (window as ToolSmokeWindow).__rylweenWildernessPerspectiveSet = async (
        x: number,
        y: number,
        facing: ToolFacing,
        mode: "2d" | "3d",
    ): Promise<ToolSnapshot> => {
        toolState.x = x;
        toolState.y = y;
        toolState.facing = facing;
        toolState.perspective = mode === "3d";
        await toolLoad();
        return toolSnapshot();
    };

    async function toolLoad(): Promise<void> {
        toolStatus.textContent = "Loading map";
        const query =
            "?x=" + encodeURIComponent(String(toolState.x)) +
            "&y=" + encodeURIComponent(String(toolState.y)) +
            "&facing=" + encodeURIComponent(toolState.facing);
        const response = await fetch("/api/wilderness-perspective" + query, {credentials: "same-origin"});
        if (!response.ok) throw new Error("wilderness perspective status " + response.status);
        const data = (await response.json()) as ToolResponse;
        toolState = {
            ...toolState,
            x: data.position.x,
            y: data.position.y,
            facing: data.facing,
            mapWidth: data.mapWidth,
            mapHeight: data.mapHeight,
            data,
        };
        toolSyncInputs();
        toolDraw();
    }

    function toolSyncInputs(): void {
        toolX.value = String(toolState.x);
        toolY.value = String(toolState.y);
        toolFacing.value = toolState.facing;
        toolPerspective.checked = toolState.perspective;
        toolStatus.textContent =
            "x" + toolState.x + " y" + toolState.y + " " + toolState.facing + " " + (toolState.perspective ? "3D" : "2D");
    }

    function toolSetPositionFromInputs(): void {
        const x = Number(toolX.value);
        const y = Number(toolY.value);
        if (Number.isFinite(x)) toolState.x = Math.round(x);
        if (Number.isFinite(y)) toolState.y = Math.round(y);
        toolState.facing = toolFacing.value as ToolFacing;
        toolState.perspective = toolPerspective.checked;
        toolLoad().catch(toolShowError);
    }

    function toolShowError(error: unknown): void {
        toolStatus.textContent = "Tool error";
        toolCtx.fillStyle = "#090b08";
        toolCtx.fillRect(0, 0, toolCanvas.width, toolCanvas.height);
        toolCtx.fillStyle = "#e9d58a";
        toolCtx.font = "16px Menlo, Monaco, Consolas, monospace";
        toolCtx.fillText(error instanceof Error ? error.message : String(error), 28, 44);
    }

    function toolMove(dx: number, dy: number, facing: ToolFacing): void {
        toolState.x = Math.max(0, Math.min(Math.max(0, toolState.mapWidth - 1), toolState.x + dx));
        toolState.y = Math.max(1, Math.min(Math.max(1, toolState.mapHeight), toolState.y + dy));
        toolState.facing = facing;
        toolLoad().catch(toolShowError);
    }

    function toolSetFacing(facing: ToolFacing): void {
        toolState.facing = facing;
        toolSyncInputs();
        toolDraw();
    }

    function toolDraw(): void {
        const data = toolState.data;
        toolCtx.imageSmoothingEnabled = false;
        toolCtx.fillStyle = "#080b09";
        toolCtx.fillRect(0, 0, toolCanvas.width, toolCanvas.height);
        if (!data) return;
        if (toolState.perspective) toolDrawPerspective(data.viewport);
        else toolDrawMap2d(data.viewport);
        toolDrawHud();
    }

    function toolDrawHud(): void {
        toolCtx.save();
        toolCtx.textAlign = "left";
        toolCtx.font = "13px Menlo, Monaco, Consolas, monospace";
        toolCtx.fillStyle = "rgba(7, 9, 7, 0.72)";
        toolCtx.fillRect(16, 14, 206, 28);
        toolCtx.strokeStyle = "rgba(218, 185, 98, 0.42)";
        toolCtx.strokeRect(16.5, 14.5, 205, 27);
        toolCtx.fillStyle = "#ecd992";
        toolCtx.fillText("x" + toolState.x + " y" + toolState.y + " " + toolState.facing + " " + (toolState.perspective ? "3D" : "2D"), 28, 33);
        toolCtx.restore();
    }

    function toolDrawMap2d(map: ToolMap): void {
        const rows = map.rows || [];
        const cols = rows[0]?.length || 0;
        const tile = Math.floor(Math.min((toolCanvas.width - 112) / Math.max(1, cols), (toolCanvas.height - 84) / Math.max(1, rows.length)));
        const startX = Math.floor((toolCanvas.width - cols * tile) / 2);
        const startY = Math.floor((toolCanvas.height - rows.length * tile) / 2);
        toolCtx.fillStyle = "#0c120c";
        toolCtx.fillRect(startX - 8, startY - 8, cols * tile + 16, rows.length * tile + 16);
        rows.forEach((row, y) => {
            row.forEach((cell, x) => {
                const colors = toolTileColors(cell);
                toolCtx.fillStyle = colors.base;
                toolCtx.fillRect(startX + x * tile, startY + y * tile, tile, tile);
                toolCtx.strokeStyle = colors.edge;
                toolCtx.strokeRect(startX + x * tile + 0.5, startY + y * tile + 0.5, tile - 1, tile - 1);
                if (cell.center) {
                    toolCtx.fillStyle = "#f4d36f";
                    toolCtx.fillRect(startX + x * tile + Math.floor(tile * 0.32), startY + y * tile + Math.floor(tile * 0.32), Math.max(5, Math.floor(tile * 0.36)), Math.max(5, Math.floor(tile * 0.36)));
                }
            });
        });
    }

    function toolDrawPerspective(map: ToolMap): void {
        const view = toolPerspectiveView(map, toolState.facing);
        if (!view) {
            toolDrawMap2d(map);
            return;
        }
        const bounds = {x: 40, y: 34, w: toolCanvas.width - 80, h: toolCanvas.height - 68};
        toolDrawPerspectiveBackdrop(bounds);
        view.cells.forEach((cell) => toolDrawPerspectiveCell(cell, bounds, map.perspective.width, map.perspective.height));
    }

    function toolPerspectiveView(map: ToolMap | null, facing: ToolFacing): ToolPerspectiveView | null {
        return map?.perspective?.views.find((view) => view.facing === facing) || null;
    }

    function toolDrawPerspectiveBackdrop(bounds: ToolRect): void {
        const sky = toolCtx.createLinearGradient(0, bounds.y, 0, bounds.y + bounds.h);
        sky.addColorStop(0, "#1f3140");
        sky.addColorStop(0.48, "#4d5f53");
        sky.addColorStop(0.5, "#2f3b23");
        sky.addColorStop(1, "#14170d");
        toolCtx.fillStyle = sky;
        toolCtx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
        toolCtx.fillStyle = "rgba(233, 197, 107, 0.13)";
        toolCtx.beginPath();
        toolCtx.arc(bounds.x + bounds.w * 0.78, bounds.y + bounds.h * 0.17, 58, 0, Math.PI * 2);
        toolCtx.fill();
        toolCtx.strokeStyle = "rgba(232, 202, 128, 0.22)";
        for (let line = 0; line < 6; line += 1) {
            const y = bounds.y + bounds.h * (0.54 + line * 0.072);
            toolCtx.beginPath();
            toolCtx.moveTo(bounds.x + bounds.w * (0.15 - line * 0.018), y);
            toolCtx.lineTo(bounds.x + bounds.w * (0.85 + line * 0.018), y);
            toolCtx.stroke();
        }
    }

    function toolDrawPerspectiveCell(cell: ToolPerspectiveCell, bounds: ToolRect, sourceW: number, sourceH: number): void {
        const rect = {
            x: bounds.x + cell.rect.x / sourceW * bounds.w,
            y: bounds.y + cell.rect.y / sourceH * bounds.h,
            w: cell.rect.w / sourceW * bounds.w,
            h: cell.rect.h / sourceH * bounds.h,
        };
        const sprite = toolPerspectiveSprite(cell.tile);
        toolCtx.save();
        toolCtx.globalAlpha = Math.max(0.5, Math.min(1, 0.5 + cell.scale * 0.55));
        toolCtx.drawImage(sprite, rect.x, rect.y, rect.w, rect.h);
        if (cell.forward === 0) {
            toolCtx.strokeStyle = "rgba(250, 220, 120, 0.88)";
            toolCtx.lineWidth = 3;
            toolCtx.strokeRect(rect.x + rect.w * 0.35, rect.y + rect.h * 0.48, rect.w * 0.3, rect.h * 0.22);
        }
        toolCtx.restore();
    }

    function toolPerspectiveSprite(tile: ToolTile): HTMLCanvasElement {
        const slug = tile.settlement || tile.slug || "void";
        const variant = Math.abs(Number(tile.variant || 0)) % 4;
        const key = slug + ":" + variant;
        const cached = toolSpriteCache.get(key);
        if (cached) return cached;
        const sprite = document.createElement("canvas");
        sprite.width = 360;
        sprite.height = 250;
        const context = sprite.getContext("2d");
        if (!context) return sprite;
        context.clearRect(0, 0, sprite.width, sprite.height);
        toolDrawGround(context, slug, variant);
        switch (slug) {
            case "forest":
                toolDrawForest(context, variant);
                break;
            case "mountain":
                toolDrawMountains(context, variant);
                break;
            case "water":
                toolDrawWater(context, variant);
                break;
            case "desert":
                toolDrawDesert(context, variant);
                break;
            case "foothills":
                toolDrawFoothills(context, variant);
                break;
            case "swamp":
                toolDrawSwamp(context, variant);
                break;
            case "town":
                toolDrawTown(context, variant);
                break;
            case "hut":
                toolDrawHut(context, variant);
                break;
            case "castle":
                toolDrawCastle(context, variant);
                break;
            case "void":
                toolDrawVoid(context);
                break;
            default:
                toolDrawPlain(context, variant);
        }
        toolSpriteCache.set(key, sprite);
        return sprite;
    }

    function toolDrawGround(context: CanvasRenderingContext2D, slug: string, variant: number): void {
        const colors = toolTileColors({slug});
        const y = 158 + variant * 3;
        context.fillStyle = colors.base;
        context.beginPath();
        context.ellipse(180, y, 160, 54, 0, 0, Math.PI * 2);
        context.fill();
        context.strokeStyle = colors.edge;
        context.lineWidth = 3;
        context.stroke();
    }

    function toolDrawPlain(context: CanvasRenderingContext2D, variant: number): void {
        context.strokeStyle = "rgba(194, 181, 95, 0.65)";
        for (let index = 0; index < 11; index += 1) {
            const x = 44 + index * 27 + (variant % 2) * 7;
            context.beginPath();
            context.moveTo(x, 154);
            context.lineTo(x + 8, 132 - (index % 3) * 6);
            context.lineTo(x + 13, 157);
            context.stroke();
        }
    }

    function toolDrawForest(context: CanvasRenderingContext2D, variant: number): void {
        for (let index = 0; index < 8; index += 1) {
            const x = 56 + index * 35 + ((index + variant) % 2) * 10;
            const h = 72 + ((index + variant) % 3) * 14;
            context.fillStyle = "#172916";
            toolTriangle(context, x, 154 - h, x - 34, 172, x + 34, 172);
            context.fillStyle = "#274d25";
            toolTriangle(context, x, 126 - h * 0.35, x - 27, 168, x + 27, 168);
            context.fillStyle = "#5a3418";
            context.fillRect(x - 4, 160, 8, 32);
        }
    }

    function toolDrawMountains(context: CanvasRenderingContext2D, variant: number): void {
        for (let index = 0; index < 3; index += 1) {
            const x = 94 + index * 82 + variant * 4;
            const top = 58 + (index % 2) * 18;
            context.fillStyle = "#374041";
            toolTriangle(context, x, top, x - 78, 178, x + 86, 178);
            context.fillStyle = "#65706a";
            toolTriangle(context, x, top, x - 18, 101, x + 24, 99);
        }
    }

    function toolDrawWater(context: CanvasRenderingContext2D, variant: number): void {
        context.strokeStyle = "rgba(145, 203, 209, 0.72)";
        context.lineWidth = 5;
        for (let row = 0; row < 5; row += 1) {
            context.beginPath();
            for (let index = 0; index < 9; index += 1) {
                const x = 50 + index * 34;
                const y = 126 + row * 14 + Math.sin(index + variant) * 4;
                if (index === 0) context.moveTo(x, y);
                else context.quadraticCurveTo(x + 14, y - 7, x + 29, y);
            }
            context.stroke();
        }
    }

    function toolDrawDesert(context: CanvasRenderingContext2D, variant: number): void {
        context.strokeStyle = "rgba(101, 72, 31, 0.54)";
        for (let index = 0; index < 6; index += 1) {
            context.beginPath();
            context.arc(70 + index * 42, 142 + ((index + variant) % 2) * 15, 28, Math.PI, Math.PI * 1.85);
            context.stroke();
        }
    }

    function toolDrawFoothills(context: CanvasRenderingContext2D, variant: number): void {
        context.fillStyle = "#56633b";
        for (let index = 0; index < 5; index += 1) {
            context.beginPath();
            context.ellipse(65 + index * 58, 150 + ((index + variant) % 2) * 10, 46, 30, -0.08, 0, Math.PI * 2);
            context.fill();
        }
    }

    function toolDrawSwamp(context: CanvasRenderingContext2D, variant: number): void {
        context.fillStyle = "rgba(58, 82, 54, 0.82)";
        for (let index = 0; index < 7; index += 1) {
            context.fillRect(46 + index * 41, 112 + ((index + variant) % 3) * 10, 8, 60);
        }
        context.strokeStyle = "rgba(101, 124, 78, 0.76)";
        context.beginPath();
        context.moveTo(42, 154);
        context.bezierCurveTo(104, 132, 154, 180, 216, 148);
        context.bezierCurveTo(248, 132, 288, 154, 326, 143);
        context.stroke();
    }

    function toolDrawTown(context: CanvasRenderingContext2D, variant: number): void {
        for (let index = 0; index < 5; index += 1) {
            const x = 82 + index * 40;
            const y = 110 + ((index + variant) % 2) * 12;
            context.fillStyle = "#8d7049";
            context.fillRect(x, y, 34, 46);
            context.fillStyle = "#442820";
            toolTriangle(context, x + 17, y - 22, x - 4, y + 4, x + 38, y + 4);
        }
    }

    function toolDrawHut(context: CanvasRenderingContext2D, variant: number): void {
        context.fillStyle = "#7b5a34";
        context.fillRect(132, 112 + variant * 2, 92, 58);
        context.fillStyle = "#3b2316";
        toolTriangle(context, 178, 72 + variant * 2, 112, 124, 244, 124);
        context.fillStyle = "#1b120c";
        context.fillRect(170, 134, 18, 36);
    }

    function toolDrawCastle(context: CanvasRenderingContext2D, variant: number): void {
        context.fillStyle = "#3c4145";
        context.fillRect(118, 90, 124, 78);
        context.fillRect(104, 70 + variant * 2, 30, 98);
        context.fillRect(226, 70 + variant * 2, 30, 98);
        context.fillStyle = "#111315";
        context.fillRect(166, 126, 28, 42);
        context.strokeStyle = "#7d8583";
        context.strokeRect(118.5, 90.5, 123, 77);
    }

    function toolDrawVoid(context: CanvasRenderingContext2D): void {
        context.fillStyle = "rgba(11, 12, 13, 0.82)";
        context.fillRect(52, 106, 256, 82);
        context.strokeStyle = "rgba(120, 112, 96, 0.35)";
        context.strokeRect(52.5, 106.5, 255, 81);
    }

    function toolTriangle(context: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number, x3: number, y3: number): void {
        context.beginPath();
        context.moveTo(x1, y1);
        context.lineTo(x2, y2);
        context.lineTo(x3, y3);
        context.closePath();
        context.fill();
    }

    function toolTileColors(tile: ToolTile): { base: string; edge: string } {
        const slug = tile.settlement || tile.slug || "void";
        switch (slug) {
            case "forest":
                return {base: "#234822", edge: "#1b3218"};
            case "mountain":
                return {base: "#59615f", edge: "#343a3b"};
            case "water":
                return {base: "#255666", edge: "#173641"};
            case "desert":
                return {base: "#b7934d", edge: "#765c2b"};
            case "foothills":
                return {base: "#637543", edge: "#3b4a2a"};
            case "swamp":
                return {base: "#3d5138", edge: "#263523"};
            case "town":
                return {base: "#7b6741", edge: "#4d3921"};
            case "hut":
                return {base: "#6a5132", edge: "#3a2818"};
            case "castle":
                return {base: "#45484a", edge: "#202325"};
            case "void":
                return {base: "#111312", edge: "#070807"};
            default:
                return {base: "#6c7c3f", edge: "#3f4d29"};
        }
    }

    toolX.addEventListener("change", toolSetPositionFromInputs);
    toolY.addEventListener("change", toolSetPositionFromInputs);
    toolFacing.addEventListener("change", toolSetPositionFromInputs);
    toolPerspective.addEventListener("change", toolSetPositionFromInputs);
    window.addEventListener("keydown", (event: KeyboardEvent) => {
        if (event.altKey || event.ctrlKey || event.metaKey) return;
        if (event.key === "2" && !event.shiftKey) {
            event.preventDefault();
            toolState.perspective = !toolState.perspective;
            toolSyncInputs();
            toolDraw();
            return;
        }
        const facing = toolFacingForArrow(event.key);
        if (!facing) return;
        event.preventDefault();
        if (event.shiftKey) {
            toolSetFacing(facing);
            return;
        }
        switch (facing) {
            case "North":
                toolMove(0, -1, facing);
                break;
            case "East":
                toolMove(1, 0, facing);
                break;
            case "South":
                toolMove(0, 1, facing);
                break;
            case "West":
                toolMove(-1, 0, facing);
                break;
        }
    });

    function toolFacingForArrow(key: string): ToolFacing | null {
        if (key === "ArrowUp") return "North";
        if (key === "ArrowRight") return "East";
        if (key === "ArrowDown") return "South";
        if (key === "ArrowLeft") return "West";
        return null;
    }

    toolCanvas.focus();
    toolLoad().catch(toolShowError);
})();
