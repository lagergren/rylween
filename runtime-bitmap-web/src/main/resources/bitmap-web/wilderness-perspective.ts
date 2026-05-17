(() => {
    type ToolFacing = "North" | "East" | "South" | "West";
    type ToolPoint = { x: number; y: number };
    type ToolRect = { x: number; y: number; w: number; h: number };
    type ToolPerspectiveQuad = {
        nearLeft: ToolPoint;
        nearRight: ToolPoint;
        farRight: ToolPoint;
        farLeft: ToolPoint;
    };
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
        frontTile?: string;
        terrainAssetsReady: boolean;
        visibleSettlements: string[];
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
    const toolAssetVersion = "20260517-view-mode-keys";
    const toolVisualNoiseA = 12.9898;
    const toolVisualNoiseB = 78.233;
    const toolVisualNoiseScale = 43758.5453123;
    const toolPerspectiveTerrainAssets: Record<string, HTMLImageElement> = {
        plain: toolLoadAsset("/assets/wilderness/terrain-plain.png"),
        forest: toolLoadAsset("/assets/wilderness/terrain-forest.png"),
        foothills: toolLoadAsset("/assets/wilderness/terrain-foothills.png"),
        mountain: toolLoadAsset("/assets/wilderness/terrain-mountain.png"),
        desert: toolLoadAsset("/assets/wilderness/terrain-desert.png"),
        swamp: toolLoadAsset("/assets/wilderness/terrain-swamp.png"),
        water: toolLoadAsset("/assets/wilderness/terrain-water.png"),
        town: toolLoadAsset("/assets/wilderness/terrain-town.png"),
        hut: toolLoadAsset("/assets/wilderness/terrain-hut.png"),
        castle: toolLoadAsset("/assets/wilderness/terrain-castle.png"),
        road: toolLoadAsset("/assets/wilderness/terrain-road.png"),
        void: toolLoadAsset("/assets/wilderness/terrain-void.png")
    };

    let toolState = {
        x: 12,
        y: 10,
        facing: "South" as ToolFacing,
        perspective: true,
        mapWidth: 0,
        mapHeight: 0,
        data: null as ToolResponse | null,
    };

    function toolLoadAsset(path: string): HTMLImageElement {
        const image = new Image();
        image.src = path + "?v=" + toolAssetVersion;
        image.addEventListener("load", toolDraw);
        return image;
    }

    function toolSnapshot(): ToolSnapshot {
        const view = toolPerspectiveView(toolState.data?.viewport || null, toolState.facing);
        const front = view?.cells.find((cell) => cell.forward === 1 && cell.side === 0);
        const visibleSettlements = Array.from(
            new Set(
                (view?.cells || [])
                    .map((cell) => cell.tile.settlement || "")
                    .filter((settlement) => settlement.length > 0),
            ),
        );
        return {
            x: toolState.x,
            y: toolState.y,
            facing: toolState.facing,
            mode: toolState.perspective ? "3d" : "2d",
            cells: view?.cells.length ?? 0,
            mapCells: (toolState.data?.viewport.rows || []).reduce((count, row) => count + row.length, 0),
            frontTile: front ? toolTerrainKey(front.tile) : undefined,
            terrainAssetsReady: Object.values(toolPerspectiveTerrainAssets).every((image) => image.complete && image.naturalWidth > 0),
            visibleSettlements,
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
        const requestedFacing = toolState.facing;
        const query =
            "?x=" + encodeURIComponent(String(toolState.x)) +
            "&y=" + encodeURIComponent(String(toolState.y)) +
            "&facing=" + encodeURIComponent(requestedFacing);
        const response = await fetch("/api/wilderness-perspective" + query, {credentials: "same-origin"});
        if (!response.ok) throw new Error("wilderness perspective status " + response.status);
        const data = (await response.json()) as ToolResponse;
        const facing = toolState.facing === requestedFacing ? data.facing : toolState.facing;
        toolState = {
            ...toolState,
            x: data.position.x,
            y: data.position.y,
            facing,
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

    function toolRotateFacing(direction: number): void {
        toolSetFacing(toolRotatedFacing(toolState.facing, direction));
    }

    function toolMoveRelative(facing: ToolFacing): void {
        const delta = toolMovementDeltaForFacing(facing);
        toolMove(delta.dx, delta.dy, toolState.facing);
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
        if (toolState.perspective) toolDrawCompass(toolState.facing);
    }

    function toolDrawCompass(facing: ToolFacing): void {
        const centerX = toolCanvas.width - 48;
        const centerY = 48;
        const radius = 30;
        toolCtx.save();
        toolCtx.translate(centerX, centerY);
        toolCtx.fillStyle = "rgba(7, 9, 7, 0.72)";
        toolCtx.beginPath();
        toolCtx.arc(0, 0, radius + 7, 0, Math.PI * 2);
        toolCtx.fill();
        toolCtx.strokeStyle = "rgba(218, 185, 98, 0.48)";
        toolCtx.stroke();
        toolCtx.strokeStyle = "rgba(218, 185, 98, 0.34)";
        toolCtx.beginPath();
        toolCtx.moveTo(0, -radius);
        toolCtx.lineTo(0, radius);
        toolCtx.moveTo(-radius, 0);
        toolCtx.lineTo(radius, 0);
        toolCtx.stroke();
        toolCtx.font = "700 10px Menlo, Monaco, Consolas, monospace";
        toolCtx.textAlign = "center";
        toolCtx.textBaseline = "middle";
        toolCtx.fillStyle = "#ecd992";
        toolCtx.fillText("N", 0, -radius + 5);
        toolCtx.fillText("E", radius - 5, 0);
        toolCtx.fillText("S", 0, radius - 5);
        toolCtx.fillText("W", -radius + 5, 0);
        toolCtx.rotate(toolFacingAngle(facing));
        toolCtx.fillStyle = "#f0c44f";
        toolCtx.beginPath();
        toolCtx.moveTo(0, -radius + 7);
        toolCtx.lineTo(8, 7);
        toolCtx.lineTo(0, 2);
        toolCtx.lineTo(-8, 7);
        toolCtx.closePath();
        toolCtx.fill();
        toolCtx.restore();
    }

    function toolFacingAngle(facing: ToolFacing): number {
        switch (facing) {
            case "North":
                return 0;
            case "East":
                return Math.PI / 2;
            case "South":
                return Math.PI;
            case "West":
                return -Math.PI / 2;
        }
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
        toolDrawPerspectiveBackdrop(bounds, "dawn");
        toolDrawPerspectiveSettlementBackdrop(view, bounds);
        toolCtx.save();
        toolCtx.beginPath();
        toolCtx.rect(bounds.x, bounds.y, bounds.w, bounds.h);
        toolCtx.clip();
        view.cells.forEach((cell) => toolDrawPerspectiveGroundCell(cell, bounds));
        view.cells.forEach((cell) => toolDrawPerspectiveCellContent(cell, bounds));
        toolCtx.restore();
    }

    function toolPerspectiveView(map: ToolMap | null, facing: ToolFacing): ToolPerspectiveView | null {
        return map?.perspective?.views.find((view) => view.facing === facing) || null;
    }

    function toolDrawPerspectiveBackdrop(bounds: ToolRect, time: string): void {
        const theme = toolBackdropTheme(time);
        const base = toolCtx.createLinearGradient(0, bounds.y, 0, bounds.y + bounds.h);
        base.addColorStop(0, theme.skyTop);
        base.addColorStop(0.35, theme.skyMid);
        base.addColorStop(0.62, theme.skyLow);
        base.addColorStop(1, "#11170f");
        toolCtx.fillStyle = base;
        toolCtx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
        toolDrawPerspectiveDistantLandscape(bounds, theme);
    }

    function toolDrawPerspectiveDistantLandscape(bounds: ToolRect, theme: {
        skyTop: string;
        skyMid: string;
        skyLow: string;
        ridgeFar: string;
        ridgeNear: string;
        forest: string;
        haze: string;
        glow: string;
        groundTint: string;
        lightAlpha: number;
    }): void {
        toolCtx.save();
        const glow = toolCtx.createRadialGradient(
            bounds.x + bounds.w * 0.74,
            bounds.y + bounds.h * 0.16,
            bounds.w * 0.05,
            bounds.x + bounds.w * 0.72,
            bounds.y + bounds.h * 0.18,
            bounds.w * 0.36,
        );
        glow.addColorStop(0, theme.glow);
        glow.addColorStop(1, "rgba(0, 0, 0, 0)");
        toolCtx.fillStyle = glow;
        toolCtx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h * 0.55);
        toolDrawPerspectiveRidge(bounds, bounds.y + bounds.h * 0.38, bounds.h * 0.2, theme.ridgeFar, 7, 0.17);
        toolDrawPerspectiveRidge(bounds, bounds.y + bounds.h * 0.49, bounds.h * 0.16, theme.ridgeNear, 6, 0.41);
        toolDrawPerspectiveTreeline(bounds, theme.forest);
        const haze = toolCtx.createLinearGradient(0, bounds.y + bounds.h * 0.25, 0, bounds.y + bounds.h * 0.72);
        haze.addColorStop(0, "rgba(0, 0, 0, 0)");
        haze.addColorStop(0.45, theme.haze);
        haze.addColorStop(1, "rgba(5, 8, 4, 0.24)");
        toolCtx.fillStyle = haze;
        toolCtx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h * 0.76);
        const left = toolCtx.createLinearGradient(bounds.x, 0, bounds.x + bounds.w * 0.32, 0);
        left.addColorStop(0, "rgba(2, 5, 2, 0.78)");
        left.addColorStop(1, "rgba(2, 5, 2, 0)");
        toolCtx.fillStyle = left;
        toolCtx.fillRect(bounds.x, bounds.y, bounds.w * 0.32, bounds.h);
        const right = toolCtx.createLinearGradient(bounds.x + bounds.w, 0, bounds.x + bounds.w * 0.68, 0);
        right.addColorStop(0, "rgba(2, 5, 2, 0.78)");
        right.addColorStop(1, "rgba(2, 5, 2, 0)");
        toolCtx.fillStyle = right;
        toolCtx.fillRect(bounds.x + bounds.w * 0.68, bounds.y, bounds.w * 0.32, bounds.h);
        toolCtx.fillStyle = theme.groundTint;
        toolCtx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
        if (theme.lightAlpha > 0) {
            toolCtx.fillStyle = "rgba(0, 0, 0, " + theme.lightAlpha.toFixed(2) + ")";
            toolCtx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
        }
        toolCtx.restore();
    }

    function toolDrawPerspectiveRidge(
        bounds: ToolRect,
        baseline: number,
        height: number,
        color: string,
        peaks: number,
        phase: number,
    ): void {
        toolCtx.fillStyle = color;
        toolCtx.beginPath();
        toolCtx.moveTo(bounds.x, bounds.y + bounds.h);
        toolCtx.lineTo(bounds.x, baseline);
        for (let index = 0; index <= peaks; index += 1) {
            const x = bounds.x + bounds.w * (index / peaks);
            const peak = baseline - height * (0.42 + toolSeededUnit(phase * 1000, index) * 0.58);
            toolCtx.lineTo(x - bounds.w / peaks / 2, baseline - height * 0.2);
            toolCtx.lineTo(x, peak);
            toolCtx.lineTo(x + bounds.w / peaks / 2, baseline - height * 0.16);
        }
        toolCtx.lineTo(bounds.x + bounds.w, bounds.y + bounds.h);
        toolCtx.closePath();
        toolCtx.fill();
    }

    function toolDrawPerspectiveTreeline(bounds: ToolRect, color: string): void {
        toolCtx.fillStyle = color;
        const baseY = bounds.y + bounds.h * 0.56;
        for (let index = 0; index < 28; index += 1) {
            const x = bounds.x + bounds.w * (index / 27);
            const h = bounds.h * (0.12 + toolSeededUnit(420, index) * 0.1);
            const w = bounds.w * (0.022 + toolSeededUnit(880, index) * 0.018);
            toolCtx.beginPath();
            toolCtx.moveTo(x, baseY - h);
            toolCtx.lineTo(x - w, baseY + h * 0.15);
            toolCtx.lineTo(x + w, baseY + h * 0.15);
            toolCtx.closePath();
            toolCtx.fill();
            toolCtx.fillRect(x - w * 0.12, baseY - h * 0.08, w * 0.24, h * 0.34);
        }
        const ground = toolCtx.createLinearGradient(0, baseY, 0, bounds.y + bounds.h);
        ground.addColorStop(0, "rgba(16, 31, 13, 0.82)");
        ground.addColorStop(1, "rgba(6, 10, 5, 0.9)");
        toolCtx.fillStyle = ground;
        toolCtx.fillRect(bounds.x, baseY, bounds.w, bounds.h - (baseY - bounds.y));
    }

    function toolBackdropTheme(time: string): {
        skyTop: string;
        skyMid: string;
        skyLow: string;
        ridgeFar: string;
        ridgeNear: string;
        forest: string;
        haze: string;
        glow: string;
        groundTint: string;
        lightAlpha: number;
    } {
        switch (time.toLowerCase()) {
            case "midday":
                return {
                    skyTop: "#1f4667",
                    skyMid: "#6e9eb0",
                    skyLow: "#d5c28a",
                    ridgeFar: "rgba(89, 99, 97, 0.74)",
                    ridgeNear: "rgba(52, 76, 48, 0.88)",
                    forest: "rgba(19, 54, 26, 0.76)",
                    haze: "rgba(217, 222, 178, 0.18)",
                    glow: "rgba(246, 220, 142, 0.24)",
                    groundTint: "rgba(221, 214, 121, 0.1)",
                    lightAlpha: 0.06,
                };
            case "twilight":
                return {
                    skyTop: "#171936",
                    skyMid: "#624366",
                    skyLow: "#b46840",
                    ridgeFar: "rgba(55, 52, 67, 0.82)",
                    ridgeNear: "rgba(39, 48, 43, 0.92)",
                    forest: "rgba(12, 30, 21, 0.82)",
                    haze: "rgba(205, 91, 73, 0.2)",
                    glow: "rgba(218, 92, 62, 0.3)",
                    groundTint: "rgba(194, 88, 58, 0.12)",
                    lightAlpha: 0.16,
                };
            case "midnight":
                return {
                    skyTop: "#050713",
                    skyMid: "#10182b",
                    skyLow: "#1d2b36",
                    ridgeFar: "rgba(27, 32, 47, 0.88)",
                    ridgeNear: "rgba(14, 26, 25, 0.94)",
                    forest: "rgba(3, 13, 10, 0.9)",
                    haze: "rgba(98, 124, 162, 0.14)",
                    glow: "rgba(145, 174, 218, 0.14)",
                    groundTint: "rgba(29, 45, 72, 0.2)",
                    lightAlpha: 0.28,
                };
            default:
                return {
                    skyTop: "#14233a",
                    skyMid: "#5d6f74",
                    skyLow: "#c79b57",
                    ridgeFar: "rgba(72, 79, 84, 0.8)",
                    ridgeNear: "rgba(45, 61, 46, 0.9)",
                    forest: "rgba(15, 39, 21, 0.78)",
                    haze: "rgba(245, 183, 96, 0.18)",
                    glow: "rgba(247, 184, 89, 0.36)",
                    groundTint: "rgba(224, 179, 77, 0.12)",
                    lightAlpha: 0.1,
                };
        }
    }

    function toolDrawPerspectiveSettlementBackdrop(view: ToolPerspectiveView, bounds: ToolRect): void {
        const center = view.cells.find((cell) => cell.forward === 0 && cell.side === 0);
        const key = center ? toolSettlementBackdropKey(toolTerrainKey(center.tile)) : null;
        if (!key) return;
        const image = toolPerspectiveTerrainAssets[key];
        if (image && image.complete && image.naturalWidth > 0) {
            toolCtx.save();
            toolCtx.globalAlpha = 0.26;
            toolCtx.filter = "saturate(0.82) brightness(0.68)";
            toolDrawImageCover(image, bounds);
            toolCtx.restore();
        }
        const veil = toolCtx.createLinearGradient(0, bounds.y, 0, bounds.y + bounds.h);
        veil.addColorStop(0, "rgba(4, 7, 4, 0.08)");
        veil.addColorStop(0.42, "rgba(6, 10, 5, 0.32)");
        veil.addColorStop(1, "rgba(5, 8, 4, 0.72)");
        toolCtx.fillStyle = veil;
        toolCtx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
    }

    function toolSettlementBackdropKey(key: string): string | null {
        if (key === "town" || key === "hut" || key === "castle") return key;
        return null;
    }

    function toolDrawImageCover(image: HTMLImageElement, bounds: ToolRect): void {
        const sourceAspect = image.naturalWidth / image.naturalHeight;
        const targetAspect = bounds.w / bounds.h;
        let sourceX = 0;
        let sourceY = 0;
        let sourceW = image.naturalWidth;
        let sourceH = image.naturalHeight;
        if (sourceAspect > targetAspect) {
            sourceW = image.naturalHeight * targetAspect;
            sourceX = (image.naturalWidth - sourceW) / 2;
        } else {
            sourceH = image.naturalWidth / targetAspect;
            sourceY = (image.naturalHeight - sourceH) / 2;
        }
        toolCtx.drawImage(image, sourceX, sourceY, sourceW, sourceH, bounds.x, bounds.y, bounds.w, bounds.h);
    }

    function toolDrawPerspectiveGroundCell(cell: ToolPerspectiveCell, bounds: ToolRect): void {
        const quad = toolPerspectiveQuad(bounds, cell.forward, cell.side);
        const key = toolTerrainKey(cell.tile);
        const visualTile = key === "void" ? {...cell.tile, slug: "plain", passable: true} : cell.tile;
        const colors = toolTileColors(visualTile);
        toolCtx.save();
        toolCtx.globalAlpha = key === "void" ? 0.68 : 1;
        toolFillPerspectiveQuad(quad, colors.base);
        toolFillPerspectiveQuad(quad, "rgba(0, 0, 0, " + Math.min(0.52, 0.1 + cell.forward * 0.07) + ")");
        toolDrawPerspectiveTerrainTexture({...cell, tile: visualTile}, quad);
        if (key === "void" || cell.forward <= 1) {
            toolCtx.strokeStyle = key === "void" ? "rgba(126, 154, 87, 0.07)" : "rgba(237, 212, 132, 0.045)";
            toolCtx.lineWidth = 0.55;
            toolStrokePerspectiveQuad(quad);
        }
        toolCtx.restore();
    }

    function toolDrawPerspectiveCellContent(cell: ToolPerspectiveCell, bounds: ToolRect): void {
        const key = toolTerrainKey(cell.tile);
        if (cell.forward === 0 || key === "plain" || key === "water" || key === "desert" || key === "road" || key === "void") return;
        const sprite = toolPerspectiveAsset(cell.tile);
        if (!sprite || !sprite.complete || sprite.naturalWidth === 0) return;
        const quad = toolPerspectiveQuad(bounds, cell.forward, cell.side);
        const nearCenter = toolMidpoint(quad.nearLeft, quad.nearRight);
        const farCenter = toolMidpoint(quad.farLeft, quad.farRight);
        const cellWidth = Math.max(10, toolDistance(quad.nearLeft, quad.nearRight));
        const contentWidth =
            cellWidth *
            (key === "town" || key === "castle" || key === "hut" ? 1.38 : key === "mountain" ? 1.55 : 1.18);
        const contentHeight = contentWidth * (sprite.naturalHeight / sprite.naturalWidth);
        const anchorX = farCenter.x + (nearCenter.x - farCenter.x) * 0.66;
        const anchorY = farCenter.y + (nearCenter.y - farCenter.y) * 0.78;
        toolCtx.save();
        toolCtx.imageSmoothingEnabled = true;
        toolCtx.globalAlpha = Math.max(0.45, 1 - cell.forward * 0.08);
        toolCtx.drawImage(sprite, anchorX - contentWidth / 2, anchorY - contentHeight, contentWidth, contentHeight);
        if (key === "town" || key === "castle" || key === "hut") {
            toolDrawPerspectiveSettlementLabel(key, anchorX, anchorY - contentHeight * 0.62, Math.max(16, contentWidth * 0.15));
        }
        toolCtx.restore();
    }

    function toolPerspectiveHorizonY(bounds: ToolRect): number {
        return bounds.y + bounds.h * 0.16;
    }

    function toolPerspectiveNearY(bounds: ToolRect): number {
        return bounds.y + bounds.h * 0.96;
    }

    function toolPerspectivePoint(bounds: ToolRect, depth: number, side: number): ToolPoint {
        const horizonY = toolPerspectiveHorizonY(bounds);
        const nearY = toolPerspectiveNearY(bounds);
        const distance = depth + 1;
        const y = horizonY + (nearY - horizonY) / distance;
        const unit = bounds.w * 0.34 / Math.pow(distance, 0.9);
        return {x: bounds.x + bounds.w / 2 + side * unit, y};
    }

    function toolPerspectiveQuad(bounds: ToolRect, forward: number, side: number): ToolPerspectiveQuad {
        const left = side - 0.5;
        const right = side + 0.5;
        return {
            nearLeft: toolPerspectivePoint(bounds, forward, left),
            nearRight: toolPerspectivePoint(bounds, forward, right),
            farRight: toolPerspectivePoint(bounds, forward + 1, right),
            farLeft: toolPerspectivePoint(bounds, forward + 1, left)
        };
    }

    function toolFillPerspectiveQuad(quad: ToolPerspectiveQuad, fillStyle: string): void {
        toolCtx.fillStyle = fillStyle;
        toolCtx.beginPath();
        toolCtx.moveTo(quad.nearLeft.x, quad.nearLeft.y);
        toolCtx.lineTo(quad.nearRight.x, quad.nearRight.y);
        toolCtx.lineTo(quad.farRight.x, quad.farRight.y);
        toolCtx.lineTo(quad.farLeft.x, quad.farLeft.y);
        toolCtx.closePath();
        toolCtx.fill();
    }

    function toolStrokePerspectiveQuad(quad: ToolPerspectiveQuad): void {
        toolCtx.beginPath();
        toolCtx.moveTo(quad.nearLeft.x, quad.nearLeft.y);
        toolCtx.lineTo(quad.nearRight.x, quad.nearRight.y);
        toolCtx.lineTo(quad.farRight.x, quad.farRight.y);
        toolCtx.lineTo(quad.farLeft.x, quad.farLeft.y);
        toolCtx.closePath();
        toolCtx.stroke();
    }

    function toolClipPerspectiveQuad(quad: ToolPerspectiveQuad): void {
        toolCtx.beginPath();
        toolCtx.moveTo(quad.nearLeft.x, quad.nearLeft.y);
        toolCtx.lineTo(quad.nearRight.x, quad.nearRight.y);
        toolCtx.lineTo(quad.farRight.x, quad.farRight.y);
        toolCtx.lineTo(quad.farLeft.x, quad.farLeft.y);
        toolCtx.closePath();
        toolCtx.clip();
    }

    function toolDrawPerspectiveTerrainTexture(cell: ToolPerspectiveCell, quad: ToolPerspectiveQuad): void {
        const key = toolTerrainKey(cell.tile);
        const bounds = toolPerspectiveQuadBounds(quad);
        toolCtx.save();
        toolClipPerspectiveQuad(quad);
        const texture = toolPerspectiveGroundTexture(cell.tile);
        if (texture && texture.complete && texture.naturalWidth > 0) {
            const source = toolPerspectiveTextureSource(texture, key);
            toolCtx.globalAlpha = key === "plain" ? 0.74 : key === "water" || key === "desert" ? 0.58 : 0.42;
            toolCtx.drawImage(texture, source.x, source.y, source.w, source.h, bounds.x, bounds.y, bounds.w, bounds.h);
            toolCtx.globalAlpha = 1;
        }
        if (key === "plain" || key === "foothills" || key === "forest" || key === "road") {
            toolDrawGrassDetail(cell, bounds, key);
        }
        if (key === "water") {
            toolCtx.strokeStyle = "rgba(156, 210, 220, 0.42)";
            for (let index = 0; index < 4; index += 1) {
                const y = bounds.y + bounds.h * (0.25 + index * 0.17);
                toolCtx.beginPath();
                toolCtx.moveTo(bounds.x, y);
                toolCtx.quadraticCurveTo(bounds.x + bounds.w * 0.35, y - 5, bounds.x + bounds.w * 0.68, y);
                toolCtx.quadraticCurveTo(bounds.x + bounds.w * 0.85, y + 4, bounds.x + bounds.w, y - 1);
                toolCtx.stroke();
            }
        } else if (key === "desert") {
            toolCtx.strokeStyle = "rgba(245, 216, 143, 0.24)";
            for (let index = 0; index < 4; index += 1) {
                const y = bounds.y + bounds.h * (0.25 + index * 0.16);
                toolCtx.beginPath();
                toolCtx.moveTo(bounds.x + bounds.w * 0.1, y);
                toolCtx.quadraticCurveTo(bounds.x + bounds.w * 0.5, y - 8, bounds.x + bounds.w * 0.9, y);
                toolCtx.stroke();
            }
        } else {
            toolCtx.strokeStyle = "rgba(197, 213, 122, 0.2)";
            for (let index = 0; index < 5; index += 1) {
                const x = bounds.x + bounds.w * (0.12 + index * 0.19);
                toolCtx.beginPath();
                toolCtx.moveTo(x, bounds.y + bounds.h * 0.72);
                toolCtx.lineTo(x + bounds.w * 0.04, bounds.y + bounds.h * 0.58);
                toolCtx.stroke();
            }
        }
        toolCtx.restore();
    }

    function toolPerspectiveTextureSource(texture: HTMLImageElement, key: string): ToolRect {
        if (key === "plain" || key === "forest" || key === "foothills" || key === "road") {
            return {
                x: texture.naturalWidth * 0.16,
                y: texture.naturalHeight * 0.53,
                w: texture.naturalWidth * 0.68,
                h: texture.naturalHeight * 0.33,
            };
        }
        return {
            x: texture.naturalWidth * 0.08,
            y: texture.naturalHeight * 0.48,
            w: texture.naturalWidth * 0.84,
            h: texture.naturalHeight * 0.42,
        };
    }

    function toolDrawGrassDetail(cell: ToolPerspectiveCell, bounds: ToolRect, key: string): void {
        const density = key === "road" ? 0.45 : key === "foothills" ? 0.72 : 1;
        const tufts = Math.max(5, Math.min(22, Math.floor(bounds.w * bounds.h / 3600 * density)));
        const seed = toolTerrainSeed(cell, key);
        toolCtx.lineWidth = Math.max(0.6, Math.min(1.5, bounds.h * 0.015));
        for (let index = 0; index < tufts; index += 1) {
            const x = bounds.x + toolSeededUnit(seed, index * 5 + 1) * bounds.w;
            const y = bounds.y + (0.32 + toolSeededUnit(seed, index * 5 + 2) * 0.62) * bounds.h;
            const h = Math.max(2.2, bounds.h * (0.035 + toolSeededUnit(seed, index * 5 + 3) * 0.055));
            const lean = (toolSeededUnit(seed, index * 5 + 4) - 0.5) * bounds.w * 0.035;
            toolCtx.strokeStyle = index % 3 === 0 ? "rgba(222, 220, 132, 0.28)" : "rgba(138, 177, 78, 0.31)";
            toolCtx.beginPath();
            toolCtx.moveTo(x, y);
            toolCtx.lineTo(x + lean, y - h);
            toolCtx.moveTo(x, y);
            toolCtx.lineTo(x - lean * 0.55, y - h * 0.72);
            toolCtx.stroke();
        }
    }

    function toolTerrainSeed(cell: ToolPerspectiveCell, key: string): number {
        let seed = (cell.forward + 13) * 73856093 + (cell.side + 31) * 19349663;
        for (let index = 0; index < key.length; index += 1) seed += key.charCodeAt(index) * (index + 17);
        return seed;
    }

    function toolSeededUnit(seed: number, salt: number): number {
        // Deterministic renderer-only noise for terrain details. This is not gameplay RNG.
        const value = Math.sin((seed + 1) * toolVisualNoiseA + salt * toolVisualNoiseB) * toolVisualNoiseScale;
        return value - Math.floor(value);
    }

    function toolPerspectiveGroundTexture(tile: ToolTile): HTMLImageElement | null {
        const slug = (tile.slug || "plain").toLowerCase();
        if (slug.includes("water")) return toolPerspectiveTerrainAssets.water;
        if (slug.includes("desert")) return toolPerspectiveTerrainAssets.desert;
        if (slug.includes("foothills")) return toolPerspectiveTerrainAssets.foothills;
        if (slug.includes("swamp")) return toolPerspectiveTerrainAssets.swamp;
        if (slug.includes("forest")) return toolPerspectiveTerrainAssets.plain;
        if (slug.includes("mountain")) return toolPerspectiveTerrainAssets.foothills;
        if (slug.includes("road")) return toolPerspectiveTerrainAssets.road;
        if (slug.includes("plain")) return toolPerspectiveTerrainAssets.plain;
        return toolPerspectiveTerrainAssets.plain;
    }

    function toolPerspectiveQuadBounds(quad: ToolPerspectiveQuad): ToolRect {
        const xs = [quad.nearLeft.x, quad.nearRight.x, quad.farRight.x, quad.farLeft.x];
        const ys = [quad.nearLeft.y, quad.nearRight.y, quad.farRight.y, quad.farLeft.y];
        const minX = Math.min(...xs);
        const maxX = Math.max(...xs);
        const minY = Math.min(...ys);
        const maxY = Math.max(...ys);
        return {x: minX, y: minY, w: maxX - minX, h: maxY - minY};
    }

    function toolDrawPerspectiveSettlementLabel(key: string, x: number, y: number, size: number): void {
        const label = key === "castle" ? "C" : key === "hut" ? "H" : "T";
        toolCtx.save();
        toolCtx.fillStyle = "rgba(8, 6, 3, 0.76)";
        toolCtx.fillRect(x - size / 2, y - size / 2, size, size);
        toolCtx.strokeStyle = "#f2d777";
        toolCtx.strokeRect(x - size / 2 + 0.5, y - size / 2 + 0.5, size - 1, size - 1);
        toolCtx.fillStyle = "#f8e49a";
        toolCtx.font = "bold " + Math.max(10, Math.floor(size * 0.62)) + "px Menlo, Monaco, Consolas, monospace";
        toolCtx.textAlign = "center";
        toolCtx.textBaseline = "middle";
        toolCtx.fillText(label, x, y + 1);
        toolCtx.restore();
    }

    function toolMidpoint(a: ToolPoint, b: ToolPoint): ToolPoint {
        return {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2};
    }

    function toolDistance(a: ToolPoint, b: ToolPoint): number {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    function toolPerspectiveAsset(tile: ToolTile): HTMLImageElement | null {
        return toolPerspectiveTerrainAssets[toolTerrainKey(tile)] || null;
    }

    function toolTerrainKey(tile: ToolTile): string {
        const slug = (tile.settlement || tile.slug || "void").toLowerCase();
        if (slug === "town" || slug === "hut" || slug === "castle") return slug;
        if (slug.includes("forest")) return "forest";
        if (slug.includes("mountain")) return "mountain";
        if (slug.includes("water")) return "water";
        if (slug.includes("desert")) return "desert";
        if (slug.includes("foothills")) return "foothills";
        if (slug.includes("swamp")) return "swamp";
        if (slug.includes("road")) return "road";
        if (slug.includes("plain")) return "plain";
        return "void";
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
        const viewMode = toolViewModeKey(event);
        if (viewMode) {
            const enabled = viewMode === "3d";
            if (toolState.perspective !== enabled) {
                event.preventDefault();
                toolState.perspective = enabled;
                toolSyncInputs();
                toolDraw();
                return;
            }
            if (viewMode === "3d") {
                event.preventDefault();
                return;
            }
        }
        if (event.altKey || event.ctrlKey || event.metaKey) return;
        const control = toolNavigationControlForEvent(event);
        if (!control) return;
        if (toolState.perspective) {
            event.preventDefault();
            if (control === "forward") toolMoveRelative(toolState.facing);
            else if (control === "back") toolMoveRelative(toolRotatedFacing(toolState.facing, 2));
            else if (control === "turnLeft") toolRotateFacing(-1);
            else if (control === "turnRight") toolRotateFacing(1);
            return;
        }
        event.preventDefault();
        const facing = toolAbsoluteFacingForControl(control);
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

    /** Maps browser-only tool view keys while leaving numpad movement intact. */
    function toolViewModeKey(event: KeyboardEvent): "2d" | "3d" | null {
        if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return null;
        if (event.code === "Digit2") return "2d";
        if (event.code === "Digit3") return "3d";
        return null;
    }

    /** Converts crawler keys for the isolated perspective tool. */
    function toolNavigationControlForEvent(event: KeyboardEvent): "forward" | "back" | "turnLeft" | "turnRight" | null {
        const key = event.key;
        if (key === "ArrowUp" || key === "8" || event.code === "Numpad8") return "forward";
        if (key === "ArrowDown" || event.code === "Numpad2") return "back";
        if (key === "ArrowLeft" || key === "4" || event.code === "Numpad4") return "turnLeft";
        if (key === "ArrowRight" || key === "6" || event.code === "Numpad6") return "turnRight";
        return null;
    }

    function toolAbsoluteFacingForControl(control: "forward" | "back" | "turnLeft" | "turnRight"): ToolFacing {
        switch (control) {
            case "forward":
                return "North";
            case "turnRight":
                return "East";
            case "back":
                return "South";
            case "turnLeft":
                return "West";
        }
    }

    function toolRotatedFacing(facing: ToolFacing, direction: number): ToolFacing {
        const order: ToolFacing[] = ["North", "East", "South", "West"];
        const index = order.indexOf(facing);
        return order[(index + direction + order.length) % order.length];
    }

    function toolMovementDeltaForFacing(facing: ToolFacing): { dx: number; dy: number } {
        switch (facing) {
            case "North":
                return {dx: 0, dy: -1};
            case "East":
                return {dx: 1, dy: 0};
            case "South":
                return {dx: 0, dy: 1};
            case "West":
                return {dx: -1, dy: 0};
        }
    }

    toolCanvas.focus();
    toolLoad().catch(toolShowError);
})();
