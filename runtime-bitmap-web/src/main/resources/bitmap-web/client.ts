type Bounds = { x: number; y: number; w: number; h: number };
type Layout = { map: Bounds; text: Bounds; status: Bounds };
type BitmapAssets = Record<string, HTMLImageElement>;
type StatusHotspot = Bounds & { text: string };
type SceneFadeSnapshot = { image: HTMLCanvasElement; bounds: Bounds };
type TitleFade = { image: HTMLCanvasElement; frame: BitmapFrame; started: number; duration: number };
type SceneFade = { image: HTMLCanvasElement; bounds: Bounds; started: number; duration: number };
type TerrainAtlasIndex = { row: number; col: number };
type TileColors = { base: string; shadow: string };
type PartyStat = [string, string | number | undefined | null];
type TextEffect = "None" | "Fast" | "Reveal";
type TextRevealSettings = { enabled: boolean; charsPerSecond: number };
type TextDisplaySettings = { bitmapFont: boolean; bitmapFontScale: number };
type RuntimePreferenceSettings = { pauseDelayScale: number };
type TextReveal = {
    complete: boolean;
    duration: number;
    effect: TextEffect;
    frameKey: string;
    started: number;
    startOffset: number;
    totalChars: number;
};
type TextRevealDrawState = {
    active: boolean;
    cursor: { x: number; y: number } | null;
    remaining: number;
};

type BitmapCommand = {
    label: string;
    caption: string;
    input: string;
};

type BitmapTextInput = {
    value?: string;
    maxLength?: number;
};

type BitmapTile = {
    slug?: string;
    settlement?: string;
    variant?: number;
    passable?: boolean;
    center?: boolean;
    waterMask?: number;
};

type BitmapMap = {
    rows?: BitmapTile[][];
    tileSize?: number;
};

type BitmapStatusMember = {
    slot: string | number;
    name: string;
    hp: number;
    hpMax: number;
    mana?: number;
    manaMax?: number;
    manaRelevant?: boolean;
    dead?: boolean;
};

type BitmapStatus = {
    time: string;
    condition: string;
    x: number;
    y: number;
    torches: number;
    food: number;
    moves: number;
    gold: number;
    members?: BitmapStatusMember[];
};

type BitmapPortrait = {
    imageSlug?: string;
    memberName?: string;
    memberNumber?: number;
    gender?: string;
    race?: string;
    pcClass?: string;
};

type BitmapImageAsset = {
    id: string;
    resource?: string;
    placement?: string;
    transition?: string;
};

type BitmapPartyMemberPage = {
    name?: string;
    gender?: string;
    race?: string;
    pcClass?: string;
    level?: number;
    experience?: number;
    hitPoints?: number;
    hitPointMax?: number;
    mana?: number;
    manaMax?: number;
    weaponSkill?: number;
    health?: number;
    wisdom?: number;
    charisma?: number;
    weapon?: string;
    armor?: string;
    status?: string;
};

type BitmapPartySummaryPage = {
    partyName?: string;
    memberCount?: number;
    gold?: number;
    food?: number;
    torches?: number;
    moves?: number;
    rest?: number;
    kills?: number;
};

type BitmapPartyPage = {
    kind?: string;
    pauseLine?: string;
    member?: BitmapPartyMemberPage;
    summary?: BitmapPartySummaryPage;
    inventory?: string[];
};

type BitmapFrame = {
    width?: number;
    height?: number;
    mode?: string;
    backdrop?: string;
    subtitle?: string;
    title?: string;
    textLines?: string[];
    textInput?: BitmapTextInput | null;
    textEffect?: TextEffect;
    status?: BitmapStatus | null;
    statusLines?: string[];
    commands?: BitmapCommand[];
    map?: BitmapMap;
    message?: string;
    portrait?: BitmapPortrait;
    partyPage?: BitmapPartyPage;
    visualTransition?: string;
    imageAssets?: BitmapImageAsset[];
};

type ServerStatus = {
    activeUsers: string[];
    authRequired: boolean;
    connectedUsers: number;
    currentUser?: string | null;
    serverType: string;
};

function requireElement<T extends HTMLElement>(id: string, elementType: { new(): T }): T {
    const element = document.getElementById(id);
    if (!(element instanceof elementType)) throw new Error("Missing #" + id);
    return element;
}

const canvas = requireElement("game", HTMLCanvasElement);
const context = canvas.getContext("2d");
if (!context) throw new Error("Canvas 2D context is unavailable");
const ctx = context;
const commandToggle = requireElement("commandToggle", HTMLButtonElement);
const commandPopover = requireElement("commandPopover", HTMLElement);
const preferencesToggle = requireElement("preferencesToggle", HTMLButtonElement);
const preferencesPopover = requireElement("preferencesPopover", HTMLElement);
const serverInfoToggle = requireElement("serverInfoToggle", HTMLButtonElement);
const serverInfoPopover = requireElement("serverInfoPopover", HTMLElement);
const userAccount = requireElement("userAccount", HTMLElement);
const textRevealToggle = requireElement("textRevealToggle", HTMLInputElement);
const bitmapFontToggle = requireElement("bitmapFontToggle", HTMLInputElement);
const bitmapFontScale = requireElement("bitmapFontScale", HTMLInputElement);
const bitmapFontScaleValue = requireElement("bitmapFontScaleValue", HTMLElement);
const textRevealSpeed = requireElement("textRevealSpeed", HTMLInputElement);
const textRevealSpeedValue = requireElement("textRevealSpeedValue", HTMLElement);
const pauseDelayScale = requireElement("pauseDelayScale", HTMLInputElement);
const pauseDelayScaleValue = requireElement("pauseDelayScaleValue", HTMLElement);
const canvasTooltip = requireElement("canvasTooltip", HTMLElement);
const connectionDot = requireElement("connectionDot", HTMLElement);
const connectionText = requireElement("connectionText", HTMLElement);
let socket: WebSocket | null = null;
let frame: BitmapFrame | null = null;
let currentCommands: BitmapCommand[] = [];
let statusHotspots: StatusHotspot[] = [];
let titleFade: TitleFade | null = null;
let sceneFade: SceneFade | null = null;
let textReveal: TextReveal | null = null;
let textRevealAnimation = 0;
const assetVersion = "20260515-scene-assets";
const textRevealEnabledKey = "rylween.bitmap.textReveal.enabled";
const textRevealSpeedKey = "rylween.bitmap.textReveal.speed";
const bitmapFontEnabledKey = "rylween.bitmap.textFont.bitmap";
const bitmapFontScaleKey = "rylween.bitmap.textFont.scale";
const pauseDelayScaleKey = "rylween.bitmap.pauseDelay.scale";
const pauseDelayScaleMessagePrefix = "settings:pauseDelayScale=";
const defaultBitmapFontScale = 1.75;
const minBitmapFontScale = 1.45;
const maxBitmapFontScale = 2.1;
const defaultPauseDelayScale = 1.0;
const minPauseDelayScale = 0.25;
const maxPauseDelayScale = 2.0;
const textRevealSettings: TextRevealSettings = {
    enabled: storedBoolean(textRevealEnabledKey, true),
    charsPerSecond: storedNumber(textRevealSpeedKey, 220, 90, 280)
};
const textDisplaySettings: TextDisplaySettings = {
    bitmapFont: storedBoolean(bitmapFontEnabledKey, true),
    bitmapFontScale: storedNumber(bitmapFontScaleKey, defaultBitmapFontScale, minBitmapFontScale, maxBitmapFontScale)
};
const runtimePreferenceSettings: RuntimePreferenceSettings = {
    pauseDelayScale: storedNumber(pauseDelayScaleKey, defaultPauseDelayScale, minPauseDelayScale, maxPauseDelayScale)
};
const layout: Layout = {
    map: {x: 114, y: 64, w: 420, h: 482},
    text: {x: 746, y: 64, w: 420, h: 482},
    status: {x: 154, y: 566, w: 972, h: 92}
};
const fantasyFontHeight = 7;
const fantasyFontAdvance = 5.5;
const fantasyGlyphs: Record<string, string[]> = {
    " ": ["00000", "00000", "00000", "00000", "00000", "00000", "00000"],
    "!": ["00100", "00100", "00100", "00100", "00100", "00000", "00100"],
    "\"": ["01010", "01010", "01010", "00000", "00000", "00000", "00000"],
    "#": ["01010", "11111", "01010", "01010", "11111", "01010", "00000"],
    "$": ["00100", "01111", "10100", "01110", "00101", "11110", "00100"],
    "%": ["11001", "11010", "00100", "01000", "10110", "00110", "00000"],
    "&": ["01100", "10010", "10100", "01000", "10101", "10010", "01101"],
    "'": ["00100", "00100", "01000", "00000", "00000", "00000", "00000"],
    "(": ["00010", "00100", "01000", "01000", "01000", "00100", "00010"],
    ")": ["01000", "00100", "00010", "00010", "00010", "00100", "01000"],
    "*": ["00000", "10101", "01110", "11111", "01110", "10101", "00000"],
    "+": ["00000", "00100", "00100", "11111", "00100", "00100", "00000"],
    ",": ["00000", "00000", "00000", "00000", "00110", "00100", "01000"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    ".": ["00000", "00000", "00000", "00000", "00000", "01100", "01100"],
    "/": ["00001", "00010", "00100", "01000", "10000", "00000", "00000"],
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "10000", "11110", "00001", "00001", "11110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00010", "11100"],
    ":": ["00000", "01100", "01100", "00000", "01100", "01100", "00000"],
    ";": ["00000", "01100", "01100", "00000", "01100", "00100", "01000"],
    "<": ["00010", "00100", "01000", "10000", "01000", "00100", "00010"],
    "=": ["00000", "00000", "11111", "00000", "11111", "00000", "00000"],
    ">": ["01000", "00100", "00010", "00001", "00010", "00100", "01000"],
    "?": ["01110", "10001", "00001", "00010", "00100", "00000", "00100"],
    "@": ["01110", "10001", "10111", "10101", "10111", "10000", "01110"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01111", "10000", "10000", "10111", "10001", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    "[": ["01110", "01000", "01000", "01000", "01000", "01000", "01110"],
    "\\": ["10000", "01000", "00100", "00010", "00001", "00000", "00000"],
    "]": ["01110", "00010", "00010", "00010", "00010", "00010", "01110"],
    "^": ["00100", "01010", "10001", "00000", "00000", "00000", "00000"],
    "_": ["00000", "00000", "00000", "00000", "00000", "00000", "11111"],
    "`": ["01000", "00100", "00010", "00000", "00000", "00000", "00000"],
    "a": ["00000", "00000", "01110", "00001", "01111", "10001", "01111"],
    "b": ["10000", "10000", "10110", "11001", "10001", "10001", "11110"],
    "c": ["00000", "00000", "01111", "10000", "10000", "10000", "01111"],
    "d": ["00001", "00001", "01101", "10011", "10001", "10001", "01111"],
    "e": ["00000", "00000", "01110", "10001", "11111", "10000", "01110"],
    "f": ["00110", "01000", "01000", "11100", "01000", "01000", "01000"],
    "g": ["00000", "00000", "01111", "10001", "10001", "01111", "00001"],
    "h": ["10000", "10000", "10110", "11001", "10001", "10001", "10001"],
    "i": ["00100", "00000", "01100", "00100", "00100", "00100", "01110"],
    "j": ["00010", "00000", "00110", "00010", "00010", "10010", "01100"],
    "k": ["10000", "10000", "10010", "10100", "11000", "10100", "10010"],
    "l": ["01100", "00100", "00100", "00100", "00100", "00100", "01110"],
    "m": ["00000", "00000", "11010", "10101", "10101", "10101", "10101"],
    "n": ["00000", "00000", "10110", "11001", "10001", "10001", "10001"],
    "o": ["00000", "00000", "01110", "10001", "10001", "10001", "01110"],
    "p": ["00000", "00000", "11110", "10001", "11110", "10000", "10000"],
    "q": ["00000", "00000", "01111", "10001", "01111", "00001", "00001"],
    "r": ["00000", "00000", "10110", "11001", "10000", "10000", "10000"],
    "s": ["00000", "00000", "01111", "10000", "01110", "00001", "11110"],
    "t": ["01000", "01000", "11100", "01000", "01000", "01001", "00110"],
    "u": ["00000", "00000", "10001", "10001", "10001", "10011", "01101"],
    "v": ["00000", "00000", "10001", "10001", "10001", "01010", "00100"],
    "w": ["00000", "00000", "10001", "10001", "10101", "10101", "01010"],
    "x": ["00000", "00000", "10001", "01010", "00100", "01010", "10001"],
    "y": ["00000", "00000", "10001", "10001", "01111", "00001", "01110"],
    "z": ["00000", "00000", "11111", "00010", "00100", "01000", "11111"],
    "{": ["00010", "00100", "00100", "01000", "00100", "00100", "00010"],
    "|": ["00100", "00100", "00100", "00100", "00100", "00100", "00100"],
    "}": ["01000", "00100", "00100", "00010", "00100", "00100", "01000"],
    "~": ["00000", "00000", "01000", "10101", "00010", "00000", "00000"]
};
const bitmapAssets: BitmapAssets = {
    title: loadAsset(assetUrl("/assets/title-rider.png")),
    titleLogo: loadAsset(assetUrl("/assets/title-logo.png")),
    frame: loadAsset(assetUrl("/assets/screen-frame.png")),
    town: loadAsset(assetUrl("/assets/town-mainstreet.png")),
    encounter: loadAsset(assetUrl("/assets/encounter-scorpion.png")),
    bestiary: loadAsset(assetUrl("/assets/encounter-bestiary.png")),
    scenes: loadAsset(assetUrl("/assets/scene-atlas.png")),
    terrain: loadAsset(assetUrl("/assets/terrain-atlas.png")),
    castle: loadAsset(assetUrl("/assets/castle-atlas.png")),
    waterConnect: loadAsset(assetUrl("/assets/water-connect-atlas.png")),
    partyMarker: loadAsset(assetUrl("/assets/party-rider.png")),
    partyOverview: loadAsset(assetUrl("/assets/party-overview.png")),
    sceneMainstreet: loadAsset(assetUrl("/assets/scene-thord-mainstreet.png")),
    sceneMerchant: loadAsset(assetUrl("/assets/scene-merchant.png")),
    sceneSmith: loadAsset(assetUrl("/assets/scene-smith.png")),
    sceneInn: loadAsset(assetUrl("/assets/scene-inn.png")),
    sceneTavern: loadAsset(assetUrl("/assets/scene-tavern.png")),
    sceneChurch: loadAsset(assetUrl("/assets/scene-church.png")),
    sceneGuild: loadAsset(assetUrl("/assets/scene-guild-closed.png")),
    sceneCampTent: loadAsset(assetUrl("/assets/scene-camp-tent.png")),
    sceneCampNight: loadAsset(assetUrl("/assets/scene-camp-night.png")),
    monsterGoblinoid: loadAsset(assetUrl("/assets/monster-goblinoid.png")),
    monsterUndead: loadAsset(assetUrl("/assets/monster-undead.png")),
    monsterBeast: loadAsset(assetUrl("/assets/monster-beast.png")),
    monsterFlying: loadAsset(assetUrl("/assets/monster-flying.png")),
    monsterVermin: loadAsset(assetUrl("/assets/monster-vermin.png")),
    monsterBrute: loadAsset(assetUrl("/assets/monster-brute.png")),
    monsterHumanoidMagic: loadAsset(assetUrl("/assets/monster-humanoid-magic.png")),
    monsterBoss: loadAsset(assetUrl("/assets/monster-boss.png"))
};
const portraitAssets = new Map<string, HTMLImageElement>();
const gameImageAssets = new Map<string, HTMLImageElement>();
const compiledBitmapAssets = new Map<string, BitmapImageAsset>();

function storedBoolean(key: string, fallback: boolean): boolean {
    const value = window.localStorage.getItem(key);
    if (value === null) return fallback;
    return value === "true";
}

function storedNumber(key: string, fallback: number, min: number, max: number): number {
    const value = Number(window.localStorage.getItem(key) || fallback);
    if (!Number.isFinite(value)) return fallback;
    return Math.max(min, Math.min(max, value));
}

function configureTextRevealControls(): void {
    textRevealToggle.checked = textRevealSettings.enabled;
    textRevealSpeed.value = String(textRevealSettings.charsPerSecond);
    textRevealSpeed.disabled = !textRevealSettings.enabled;
    updateTextRevealSpeedValue();
    textRevealToggle.addEventListener("change", () => {
        textRevealSettings.enabled = textRevealToggle.checked;
        window.localStorage.setItem(textRevealEnabledKey, String(textRevealSettings.enabled));
        textRevealSpeed.disabled = !textRevealSettings.enabled;
        syncTextReveal(frame, true);
        if (frame) draw(frame);
    });
    textRevealSpeed.addEventListener("input", () => {
        textRevealSettings.charsPerSecond = storedNumberFromInput(textRevealSpeed, 220, 90, 280);
        window.localStorage.setItem(textRevealSpeedKey, String(textRevealSettings.charsPerSecond));
        updateTextRevealSpeedValue();
    });
}

function configureTextDisplayControls(): void {
    bitmapFontToggle.checked = textDisplaySettings.bitmapFont;
    bitmapFontScale.value = String(textDisplaySettings.bitmapFontScale);
    bitmapFontScale.disabled = !textDisplaySettings.bitmapFont;
    updateBitmapFontScaleValue();
    bitmapFontToggle.addEventListener("change", () => {
        textDisplaySettings.bitmapFont = bitmapFontToggle.checked;
        window.localStorage.setItem(bitmapFontEnabledKey, String(textDisplaySettings.bitmapFont));
        bitmapFontScale.disabled = !textDisplaySettings.bitmapFont;
        if (frame) draw(frame);
    });
    bitmapFontScale.addEventListener("input", () => {
        textDisplaySettings.bitmapFontScale = storedNumberFromInput(bitmapFontScale, defaultBitmapFontScale, minBitmapFontScale, maxBitmapFontScale);
        window.localStorage.setItem(bitmapFontScaleKey, String(textDisplaySettings.bitmapFontScale));
        updateBitmapFontScaleValue();
        if (frame) draw(frame);
    });
}

function configureRuntimePreferenceControls(): void {
    pauseDelayScale.value = runtimePreferenceSettings.pauseDelayScale.toFixed(2);
    updatePauseDelayScaleValue();
    pauseDelayScale.addEventListener("input", () => {
        runtimePreferenceSettings.pauseDelayScale = storedNumberFromInput(
            pauseDelayScale,
            defaultPauseDelayScale,
            minPauseDelayScale,
            maxPauseDelayScale
        );
        window.localStorage.setItem(pauseDelayScaleKey, String(runtimePreferenceSettings.pauseDelayScale));
        updatePauseDelayScaleValue();
        sendRuntimePreferences();
    });
}

function updateTextRevealSpeedValue(): void {
    textRevealSpeedValue.textContent = Math.round(textRevealSettings.charsPerSecond).toString() + " cps";
}

function updateBitmapFontScaleValue(): void {
    bitmapFontScaleValue.textContent = textDisplaySettings.bitmapFontScale.toFixed(2) + "x";
}

function updatePauseDelayScaleValue(): void {
    pauseDelayScaleValue.textContent = runtimePreferenceSettings.pauseDelayScale.toFixed(2) + "x";
}

function storedNumberFromInput(input: HTMLInputElement, fallback: number, min: number, max: number): number {
    const value = Number(input.value || fallback);
    if (!Number.isFinite(value)) return fallback;
    return Math.max(min, Math.min(max, value));
}

function assetUrl(path: string): string {
    return path + "?v=" + assetVersion;
}

function loadAsset(src: string): HTMLImageElement {
    const image = new Image();
    image.src = src;
    image.addEventListener("load", () => {
        if (frame && !titleFade && !sceneFade) draw(frame);
    });
    return image;
}

function portraitAsset(imageSlug: string | undefined): HTMLImageElement | null {
    if (!imageSlug) return null;
    if (!portraitAssets.has(imageSlug)) portraitAssets.set(imageSlug, loadGameImageAsset(imageSlug));
    return portraitAssets.get(imageSlug) ?? null;
}

function recordCompiledImageAssets(assets: BitmapImageAsset[]): void {
    assets.forEach((asset) => {
        if (asset.id) compiledBitmapAssets.set(asset.id, asset);
    });
}

function compiledGameImage(imageSlug: string | undefined): HTMLImageElement | null {
    if (!imageSlug || !compiledBitmapAssets.has(imageSlug)) return null;
    return gameImageAsset(imageSlug);
}

function gameImageAsset(imageSlug: string): HTMLImageElement {
    if (!gameImageAssets.has(imageSlug)) gameImageAssets.set(imageSlug, loadGameImageAsset(imageSlug));
    return gameImageAssets.get(imageSlug) as HTMLImageElement;
}

function loadGameImageAsset(imageSlug: string): HTMLImageElement {
    return loadAsset(assetUrl("/assets/game-image/" + encodeURIComponent(imageSlug)));
}

type BitmapSmokeAsset = {
    id: string;
    resource: string;
    placement: string;
    transition: string;
};

type BitmapSmokeSnapshot = {
    mode: string | null;
    backdrop: string | null;
    title: string | null;
    textLineCount: number;
    statusLineCount: number;
    commandCount: number;
    mapCells: number;
    compiledImageAssets: BitmapSmokeAsset[];
};

type BitmapSmokeWindow = Window & {
    __rylweenBitmapSmoke?: () => BitmapSmokeSnapshot;
};

function bitmapSmokeSnapshot(): BitmapSmokeSnapshot {
    return {
        mode: frame?.mode ?? null,
        backdrop: frame?.backdrop ?? null,
        title: frame?.title ?? null,
        textLineCount: frame?.textLines?.length ?? 0,
        statusLineCount: frame?.statusLines?.length ?? 0,
        commandCount: currentCommands.length,
        mapCells: (frame?.map?.rows || []).reduce((count, row) => count + row.length, 0),
        compiledImageAssets: Array.from(compiledBitmapAssets.values()).map((asset) => ({
            id: asset.id,
            resource: asset.resource || "",
            placement: asset.placement || "",
            transition: asset.transition || ""
        }))
    };
}

(window as BitmapSmokeWindow).__rylweenBitmapSmoke = bitmapSmokeSnapshot;

function connect(): void {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    socket = new WebSocket(protocol + "//" + location.host + "/ws/play");
    socket.addEventListener("open", () => {
        connectionDot.classList.add("connected");
        connectionText.textContent = "Connected";
        refreshServerInfo().catch(() => undefined);
        sendRuntimePreferences();
        canvas.focus();
    });
    socket.addEventListener("message", (event) => {
        const nextFrame = JSON.parse(event.data) as BitmapFrame;
        recordCompiledImageAssets(nextFrame.imageAssets || []);
        const previousFrame = frame;
        const fadeFromTitle = isTitleFrame(frame) && !isTitleFrame(nextFrame);
        const sceneFadeSnapshot = shouldFadeVisual(frame, nextFrame) ? captureScenePanel() : null;
        const continuingSceneFade =
            Boolean(sceneFade && previousFrame && visualKey(previousFrame) === visualKey(nextFrame));
        if (fadeFromTitle && beginTitleFade(nextFrame)) {
            syncTextReveal(nextFrame, true, previousFrame);
            renderCommandPalette(nextFrame.commands || []);
            return;
        }
        titleFade = null;
        frame = nextFrame;
        syncTextReveal(frame, true, previousFrame);
        if (!continuingSceneFade) draw(frame);
        renderCommandPalette(frame.commands || []);
        if (sceneFadeSnapshot) beginSceneFade(sceneFadeSnapshot);
    });
    socket.addEventListener("close", () => {
        connectionDot.classList.remove("connected");
        connectionText.textContent = "Disconnected";
    });
}

async function refreshServerInfo(): Promise<void> {
    const response = await fetch("/api/status", {credentials: "same-origin"});
    if (!response.ok) throw new Error("status " + response.status);
    const status = (await response.json()) as ServerStatus;
    const user = status.currentUser || "not signed in";
    const runtimeName = runtimeDisplayName(status.serverType);
    serverInfoToggle.textContent = "Status";
    userAccount.textContent = user;
    userAccount.title = "Logged in as " + user;
    serverInfoPopover.innerHTML =
        "<h2>Status</h2>" +
        "<dl>" +
        "<dt>Runtime</dt><dd>" + escapeHtml(runtimeName) + "</dd>" +
        "<dt>Account</dt><dd>" + escapeHtml(user) + "</dd>" +
        "<dt>Auth</dt><dd>" + (status.authRequired ? "enabled" : "dev bypass") + "</dd>" +
        "<dt>Connected players</dt><dd>" + status.connectedUsers.toString() + "</dd>" +
        "<dt>Active users</dt><dd>" + escapeHtml(status.activeUsers.join(", ") || "none") + "</dd>" +
        "</dl>";
}

function runtimeDisplayName(serverType: string): string {
    if (serverType === "ansi") return "ANSI";
    if (serverType === "bitmap") return "Illustrated";
    return serverType;
}

function escapeHtml(text: string): string {
    return text.replace(/[&<>"']/g, (char) => {
        switch (char) {
            case "&":
                return "&amp;";
            case "<":
                return "&lt;";
            case ">":
                return "&gt;";
            case '"':
                return "&quot;";
            default:
                return "&#39;";
        }
    });
}

function send(token: string): void {
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    socket.send(token);
}

function sendRuntimePreferences(): void {
    send(pauseDelayScaleMessagePrefix + runtimePreferenceSettings.pauseDelayScale.toFixed(2));
}

function isTitleFrame(candidate: BitmapFrame | null): boolean {
    if (!candidate) return false;
    if (candidate.mode === "title_menu") return false;
    const kind = (candidate.backdrop || candidate.mode || "").toLowerCase();
    return candidate.mode === "title" || kind.includes("title");
}

function beginTitleFade(nextFrame: BitmapFrame): boolean {
    if (!canvas.width || !canvas.height) return false;
    const image = document.createElement("canvas");
    image.width = canvas.width;
    image.height = canvas.height;
    const snapshotContext = image.getContext("2d");
    if (!snapshotContext) return false;
    snapshotContext.drawImage(canvas, 0, 0);
    frame = nextFrame;
    titleFade = {image, frame: nextFrame, started: performance.now(), duration: 520};
    requestAnimationFrame(drawTitleFade);
    return true;
}

function drawTitleFade(): void {
    const fade = titleFade;
    if (!fade) return;
    const elapsed = performance.now() - fade.started;
    const alpha = Math.max(0, 1 - elapsed / fade.duration);
    draw(fade.frame);
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.drawImage(fade.image, 0, 0, canvas.width, canvas.height);
    ctx.restore();
    if (alpha > 0 && titleFade === fade) requestAnimationFrame(drawTitleFade);
    else {
        if (titleFade === fade) titleFade = null;
        if (frame) draw(frame);
    }
}

function shouldFadeVisual(previous: BitmapFrame | null, next: BitmapFrame | null): boolean {
    if (!previous || !next) return false;
    if (isTitleFrame(previous) || isTitleFrame(next)) return false;
    const previousKey = visualKey(previous);
    const nextKey = visualKey(next);
    if (!previousKey || !nextKey || previousKey === nextKey) return false;
    return (next.visualTransition || "None") === "Fade" || isSceneSurface(previous) || isSceneSurface(next);
}

function visualKey(candidate: BitmapFrame | null): string {
    if (!candidate) return "";
    if (candidate.mode === "boot") return "";
    if (candidate.portrait) return "portrait:" + candidate.portrait.imageSlug;
    if (candidate.partyPage) return "party:" + (candidate.partyPage.kind || "");
    if (candidate.map) return "map:" + (candidate.backdrop || "wilderness");
    return "scene:" + (candidate.mode || "") + ":" + (candidate.backdrop || "");
}

function isSceneSurface(candidate: BitmapFrame | null): boolean {
    if (!candidate) return false;
    if (candidate.mode === "boot" || candidate.mode === "title_menu") return false;
    if (candidate.portrait || candidate.partyPage || candidate.map) return true;
    const kind = (candidate.backdrop || candidate.mode || "").toLowerCase();
    return Boolean(kind) && !kind.includes("title");
}

function captureScenePanel(): SceneFadeSnapshot | null {
    if (!canvas.width || !canvas.height) return null;
    const bounds = scenePanelBounds();
    const image = document.createElement("canvas");
    image.width = bounds.w;
    image.height = bounds.h;
    const snapshotContext = image.getContext("2d");
    if (!snapshotContext) return null;
    snapshotContext.drawImage(canvas, bounds.x, bounds.y, bounds.w, bounds.h, 0, 0, bounds.w, bounds.h);
    return {image, bounds};
}

function beginSceneFade(snapshot: SceneFadeSnapshot): void {
    sceneFade = {image: snapshot.image, bounds: snapshot.bounds, started: performance.now(), duration: 360};
    requestAnimationFrame(drawSceneFade);
}

function drawSceneFade(): void {
    if (!sceneFade || !frame) return;
    const elapsed = performance.now() - sceneFade.started;
    const progress = Math.min(1, elapsed / sceneFade.duration);
    const alpha = Math.max(0, 1 - easeOutCubic(progress));
    draw(frame);
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.drawImage(sceneFade.image, sceneFade.bounds.x, sceneFade.bounds.y);
    ctx.restore();
    if (alpha > 0) requestAnimationFrame(drawSceneFade);
    else {
        sceneFade = null;
        draw(frame);
    }
}

function easeOutCubic(t: number): number {
    return 1 - Math.pow(1 - t, 3);
}

function syncTextReveal(candidate: BitmapFrame | null, resetExisting = false, previousCandidate: BitmapFrame | null = null): void {
    if (!candidate) {
        finishTextReveal(false);
        textReveal = null;
        return;
    }
    const effect = effectiveTextEffect(candidate);
    const frameKey = textRevealKey(candidate);
    const totalChars = textRevealCharCount(candidate);
    const startOffset = resetExisting ? visibleTextPrefixCharCount(previousCandidate, candidate) : 0;
    const duration = textRevealDuration(effect, totalChars - startOffset);
    if (duration <= 0) {
        finishTextReveal(false);
        textReveal = null;
        return;
    }
    const sameReveal =
        textReveal &&
        textReveal.frameKey === frameKey &&
        textReveal.effect === effect &&
        textReveal.startOffset === startOffset &&
        textReveal.totalChars === totalChars;
    if (sameReveal && !resetExisting) return;
    textReveal = {
        complete: false,
        duration,
        effect,
        frameKey,
        started: performance.now(),
        startOffset,
        totalChars
    };
    scheduleTextReveal();
}

function effectiveTextEffect(candidate: BitmapFrame): TextEffect {
    if (!textRevealSettings.enabled) return "None";
    if (candidate.textInput || candidate.partyPage) return "None";
    const lines = candidate.textLines || [];
    if (lines.length === 0) return "None";
    return candidate.textEffect || "None";
}

function textRevealDuration(effect: TextEffect, totalChars: number): number {
    if (effect === "None" || totalChars <= 0) return 0;
    const raw = totalChars / textRevealSettings.charsPerSecond * 1000;
    if (effect === "Fast") return Math.max(55, Math.min(160, raw));
    return Math.max(130, Math.min(560, raw));
}

function textRevealKey(candidate: BitmapFrame): string {
    return JSON.stringify([
        textRevealSurfaceKey(candidate),
        candidate.textLines || []
    ]);
}

function textRevealSurfaceKey(candidate: BitmapFrame): string {
    return JSON.stringify([
        candidate.mode || "",
        candidate.backdrop || "",
        candidate.title || "",
        candidate.subtitle || "",
        candidate.textEffect || "None"
    ]);
}

function textRevealCharCount(candidate: BitmapFrame): number {
    return (candidate.textLines || []).reduce((sum, line) => sum + textRevealLineCost(line), 0);
}

function visibleTextPrefixCharCount(previous: BitmapFrame | null, candidate: BitmapFrame): number {
    if (!previous) return 0;
    if (textRevealSurfaceKey(previous) !== textRevealSurfaceKey(candidate)) return 0;
    const previousLines = previous.textLines || [];
    const nextLines = candidate.textLines || [];
    let visible = 0;
    const lineCount = Math.min(previousLines.length, nextLines.length);
    for (let index = 0; index < lineCount; index += 1) {
        const previousLine = previousLines[index];
        const nextLine = nextLines[index];
        if (previousLine === nextLine) {
            visible += textRevealLineCost(nextLine);
            continue;
        }
        visible += commonPrefixLength(previousLine, nextLine);
        break;
    }
    return Math.min(visible, textRevealCharCount(candidate));
}

function textRevealLineCost(line: string): number {
    return Math.max(1, Array.from(line).length);
}

function commonPrefixLength(left: string, right: string): number {
    const leftChars = Array.from(left);
    const rightChars = Array.from(right);
    const limit = Math.min(leftChars.length, rightChars.length);
    let index = 0;
    while (index < limit && leftChars[index] === rightChars[index]) {
        index += 1;
    }
    return index;
}

function scheduleTextReveal(): void {
    if (textRevealAnimation !== 0) return;
    textRevealAnimation = requestAnimationFrame(animateTextReveal);
}

function animateTextReveal(): void {
    textRevealAnimation = 0;
    if (!frame || !textReveal || textReveal.complete) return;
    if (!sceneFade) draw(frame);
    if (textReveal && !textReveal.complete) scheduleTextReveal();
}

function textRevealLimit(candidate: BitmapFrame): number {
    if (!textReveal) return Number.POSITIVE_INFINITY;
    if (textReveal.frameKey !== textRevealKey(candidate)) return Number.POSITIVE_INFINITY;
    if (textReveal.complete) return Number.POSITIVE_INFINITY;
    const progress = Math.min(1, (performance.now() - textReveal.started) / textReveal.duration);
    if (progress >= 1) {
        textReveal.complete = true;
        return Number.POSITIVE_INFINITY;
    }
    const revealChars = Math.max(0, textReveal.totalChars - textReveal.startOffset);
    return textReveal.startOffset + Math.floor(revealChars * progress);
}

function isTextRevealActive(): boolean {
    return Boolean(frame && textReveal && !textReveal.complete && textReveal.frameKey === textRevealKey(frame));
}

function finishTextReveal(redraw = true): boolean {
    if (!textReveal || textReveal.complete) return false;
    textReveal.complete = true;
    if (textRevealAnimation !== 0) {
        cancelAnimationFrame(textRevealAnimation);
        textRevealAnimation = 0;
    }
    if (redraw && frame) draw(frame);
    return true;
}

window.addEventListener("keydown", (event: KeyboardEvent) => {
    if (event.key === "Escape" && closeOpenPopover()) {
        event.preventDefault();
        canvas.focus();
        return;
    }
    if (isInteractiveElement(event.target)) return;
    if (!(frame && frame.textInput) && isTextRevealActive()) {
        event.preventDefault();
        finishTextReveal();
        return;
    }
    if (event.key === "?" && !(frame && frame.textInput) && currentCommands.length > 0) {
        event.preventDefault();
        toggleCommandPalette();
        return;
    }
    const token = keyToken(event);
    if (!token) return;
    event.preventDefault();
    send(token);
});

window.addEventListener("pointerdown", (event: PointerEvent) => {
    if (isPopoverControlTarget(event.target)) return;
    closeOpenPopover();
});

canvas.addEventListener("mousemove", (event: MouseEvent) => {
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

function keyToken(event: KeyboardEvent): string | null {
    if (event.key === "ArrowUp") return "ARROW_UP";
    if (event.key === "ArrowDown") return "ARROW_DOWN";
    if (event.key === "ArrowLeft") return "ARROW_LEFT";
    if (event.key === "ArrowRight") return "ARROW_RIGHT";
    if (event.key === "Escape") return "ESCAPE";
    if (event.key === "Enter") return "ENTER";
    if (event.key === "Backspace") return "BACKSPACE";
    if (event.key === "Delete") return "DELETE";
    if (event.key === " ") return " ";
    if (event.key.length === 1) return event.key;
    return null;
}

function canvasPoint(event: MouseEvent): { x: number; y: number } {
    const rect = canvas.getBoundingClientRect();
    return {
        x: (event.clientX - rect.left) * canvas.width / rect.width,
        y: (event.clientY - rect.top) * canvas.height / rect.height
    };
}

function showCanvasTooltip(text: string, event: MouseEvent): void {
    const stage = canvas.parentElement?.getBoundingClientRect();
    if (!stage) return;
    canvasTooltip.hidden = false;
    canvasTooltip.textContent = text;
    const x = Math.min(stage.width - canvasTooltip.offsetWidth - 12, event.clientX - stage.left + 14);
    const y = Math.min(stage.height - canvasTooltip.offsetHeight - 12, event.clientY - stage.top + 14);
    canvasTooltip.style.left = Math.max(12, x) + "px";
    canvasTooltip.style.top = Math.max(12, y) + "px";
}

function hideCanvasTooltip(): void {
    canvasTooltip.hidden = true;
}

commandToggle.addEventListener("click", () => {
    toggleCommandPalette();
    canvas.focus();
});

preferencesToggle.addEventListener("click", () => {
    const opening = preferencesPopover.hidden;
    closeCommandPalette();
    closeServerInfoPopover();
    setPreferencesOpen(opening);
    if (!opening) canvas.focus();
});

serverInfoToggle.addEventListener("click", () => {
    const opening = serverInfoPopover.hidden;
    closeCommandPalette();
    closePreferencesPopover();
    setServerInfoOpen(opening);
    if (opening) {
        refreshServerInfo().catch(() => {
            serverInfoPopover.innerHTML = "<h2>Server</h2><p>Unavailable</p>";
        });
    }
    canvas.focus();
});

function renderCommandPalette(commands: BitmapCommand[]): void {
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
            if (finishTextReveal()) {
                canvas.focus();
                return;
            }
            send(command.input);
            closeCommandPalette();
            canvas.focus();
        });
        grid.appendChild(button);
    }
    commandPopover.appendChild(grid);
}

function toggleCommandPalette(): void {
    if (currentCommands.length === 0) return;
    const opening = commandPopover.hidden;
    closePreferencesPopover();
    closeServerInfoPopover();
    setCommandPaletteOpen(opening);
}

function closeCommandPalette(): void {
    setCommandPaletteOpen(false);
}

function closePreferencesPopover(): void {
    setPreferencesOpen(false);
}

function closeServerInfoPopover(): void {
    setServerInfoOpen(false);
}

function closeOpenPopover(): boolean {
    if (!commandPopover.hidden) {
        closeCommandPalette();
        return true;
    }
    if (!preferencesPopover.hidden) {
        closePreferencesPopover();
        return true;
    }
    if (!serverInfoPopover.hidden) {
        closeServerInfoPopover();
        return true;
    }
    return false;
}

function setCommandPaletteOpen(open: boolean): void {
    commandPopover.hidden = !open;
    commandToggle.setAttribute("aria-expanded", String(open));
}

function setPreferencesOpen(open: boolean): void {
    preferencesPopover.hidden = !open;
    preferencesToggle.setAttribute("aria-expanded", String(open));
}

function setServerInfoOpen(open: boolean): void {
    serverInfoPopover.hidden = !open;
    serverInfoToggle.setAttribute("aria-expanded", String(open));
}

function isInteractiveElement(target: EventTarget | null): boolean {
    if (!(target instanceof HTMLElement)) return false;
    return target.matches("button, input, textarea, select, option, label");
}

function isPopoverControlTarget(target: EventTarget | null): boolean {
    if (!(target instanceof Node)) return false;
    return (
        preferencesPopover.contains(target) ||
        preferencesToggle.contains(target) ||
        serverInfoPopover.contains(target) ||
        serverInfoToggle.contains(target) ||
        commandPopover.contains(target) ||
        commandToggle.contains(target)
    );
}

function draw(frame: BitmapFrame): void {
    canvas.width = frame.width || 1280;
    canvas.height = frame.height || 720;
    ctx.imageSmoothingEnabled = false;
    ctx.textBaseline = "alphabetic";
    if (frame.mode === "title_menu") {
        drawTitleMenu(frame);
        return;
    }
    if (frame.mode === "static_document") {
        drawStaticDocument(frame);
        return;
    }
    drawBackdrop(frame);
    if (isTitleFrame(frame)) return;
    if (frame.map) drawMap(frame.map);
    else drawScenePanel(frame);
    drawStatus(frame.status, frame.statusLines || []);
    drawTextPanel(frame);
    drawMessage(frame.message || "");
}

function drawBackdrop(frame: BitmapFrame): void {
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

function drawTitleBackdrop(frame: BitmapFrame): void {
    drawCoverImage(bitmapAssets.title, "#111927");
    drawTitleGradient();
    drawTitleLogo(canvas.width / 2, 24);
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

function drawTitleMenu(frame: BitmapFrame): void {
    drawCoverImage(bitmapAssets.title, "#111927");
    drawTitleGradient();
    drawTitleLogo(canvas.width / 2, 24);
    const lines = frame.textLines || [];
    const panelW = 430;
    const rowH = 30;
    const panelH = Math.max(246, lines.length * rowH + 58);
    const x = Math.floor((canvas.width - panelW) / 2);
    const y = 214;
    ctx.save();
    ctx.fillStyle = "rgba(6, 8, 6, 0.72)";
    ctx.fillRect(x, y, panelW, panelH);
    ctx.strokeStyle = "rgba(215, 191, 115, 0.52)";
    ctx.lineWidth = 1;
    ctx.strokeRect(x + 0.5, y + 0.5, panelW - 1, panelH - 1);
    ctx.textAlign = "center";
    lines.forEach((line, index) => {
        const isHeader = index === 0;
        const isDisabled = line.toLowerCase().includes("no save");
        ctx.font = isHeader ? "700 25px Palatino, 'Palatino Linotype', Georgia, serif" : "17px Menlo, Monaco, Consolas, monospace";
        ctx.fillStyle = isHeader ? "#ead68a" : isDisabled ? "rgba(174, 166, 144, 0.55)" : "#f0ead4";
        ctx.fillText(truncateToWidth(line, panelW - 58), canvas.width / 2, y + 58 + index * rowH);
    });
    ctx.restore();
}

function drawStaticDocument(frame: BitmapFrame): void {
    drawCoverImage(bitmapAssets.town, "#10130d");
    drawImageShade("#020503", 0.55);
    const x = 58;
    const y = 40;
    const w = canvas.width - x * 2;
    const h = canvas.height - 78;
    ctx.save();
    ctx.fillStyle = "rgba(4, 7, 5, 0.82)";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = "rgba(218, 189, 107, 0.56)";
    ctx.lineWidth = 1;
    ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
    ctx.textAlign = "left";
    ctx.fillStyle = "#ead68a";
    ctx.font = "700 30px Palatino, 'Book Antiqua', Georgia, serif";
    ctx.fillText(truncateToWidth(frame.title || "", w - 64), x + 34, y + 48);
    ctx.textAlign = "right";
    ctx.font = "14px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "rgba(218, 201, 150, 0.78)";
    ctx.fillText(truncateToWidth(frame.subtitle || "", 260), x + w - 34, y + 45);
    ctx.textAlign = "left";
    const lines = frame.textLines || [];
    const promptLine = documentPromptLine(lines);
    const bodyLines = documentBodyLines(lines, promptLine);
    const reveal = textRevealDrawState(frame);
    let ty = y + 90;
    let inCode = false;
    const textX = x + 38;
    const textW = w - 76;
    const bottom = y + h - 72;
    bodyLines.forEach((line) => {
        if (ty > bottom) return;
        const drawn = drawDocumentLine(line, textX, ty, textW, inCode, reveal);
        inCode = drawn.inCode;
        ty += drawn.height;
    });
    drawTextRevealCursor(reveal);
    if (promptLine) {
        ctx.textAlign = "center";
        ctx.font = "700 15px Menlo, Monaco, Consolas, monospace";
        ctx.fillStyle = "#d7bc6c";
        ctx.fillText(markdownInlineText(promptLine), x + w / 2, y + h - 30);
        ctx.textAlign = "left";
    }
    ctx.restore();
}

function documentPromptLine(lines: string[]): string {
    const candidate = lines.length > 0 ? lines[lines.length - 1] : "";
    return /^\(.*RETURN.*\)/i.test(candidate) || candidate.includes("ESC to exit") ? candidate : "";
}

function documentBodyLines(lines: string[], promptLine: string): string[] {
    const body = promptLine ? lines.slice(0, -1) : lines.slice();
    while (body.length > 0 && body[body.length - 1].trim() === "") body.pop();
    return body;
}

function drawDocumentLine(
    line: string,
    x: number,
    y: number,
    maxWidth: number,
    inCode: boolean,
    reveal: TextRevealDrawState
): { height: number; inCode: boolean } {
    if (line.startsWith("```")) {
        return { height: inCode ? 12 : 8, inCode: !inCode };
    }
    if (line.trim() === "") return { height: 13, inCode };
    if (inCode) {
        ctx.fillStyle = "rgba(13, 18, 13, 0.7)";
        ctx.fillRect(x - 10, y - 14, maxWidth + 20, 20);
        ctx.font = "13px Menlo, Monaco, Consolas, monospace";
        drawRevealText(truncateToWidth(markdownInlineText(line), maxWidth), x, y, "#d8d1b8", "rgba(216, 209, 184, 0.24)", reveal);
        return { height: 20, inCode };
    }
    const heading = line.match(/^(#{1,3})\s+(.*)$/);
    if (heading) {
        const level = heading[1].length;
        ctx.font =
            level === 1 ? "700 25px Palatino, 'Book Antiqua', Georgia, serif" :
            level === 2 ? "700 20px Palatino, 'Book Antiqua', Georgia, serif" :
            "700 16px Palatino, 'Book Antiqua', Georgia, serif";
        const color = level === 1 ? "#f1d77b" : level === 2 ? "#e8c96d" : "#d8bd73";
        const height = level === 1 ? 32 : level === 2 ? 27 : 22;
        drawRevealText(truncateToWidth(markdownInlineText(heading[2]), maxWidth), x, y, color, "rgba(232, 201, 109, 0.25)", reveal);
        return { height, inCode };
    }
    if (line.startsWith(">")) {
        const text = markdownInlineText(line.replace(/^>\s?/, ""));
        ctx.fillStyle = "rgba(218, 189, 107, 0.42)";
        ctx.fillRect(x - 12, y - 15, 3, 19);
        ctx.font = "italic 15px Georgia, 'Times New Roman', serif";
        drawRevealText(truncateToWidth(text, maxWidth - 10), x + 8, y, "#cfc39f", "rgba(207, 195, 159, 0.23)", reveal);
        return { height: 21, inCode };
    }
    ctx.font = "15px Georgia, 'Times New Roman', serif";
    drawRevealText(truncateToWidth(markdownInlineText(line), maxWidth), x, y, "#efe4c6", "rgba(239, 228, 198, 0.25)", reveal);
    return { height: 19, inCode };
}

function markdownInlineText(line: string): string {
    return line
        .replace(/&mdash;/g, "-")
        .replace(/\*\*([^*]+)\*\*/g, "$1")
        .replace(/\*([^*]+)\*/g, "$1")
        .replace(/`([^`]+)`/g, "$1");
}

function drawTitleGradient(): void {
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

function drawTitleLogo(cx: number, y: number): void {
    const logo = bitmapAssets.titleLogo;
    if (!logo.complete || logo.naturalWidth === 0) {
        drawGothicTitle(cx, y + 52);
        return;
    }
    const width = 720;
    const height = width * (logo.naturalHeight / logo.naturalWidth);
    ctx.drawImage(logo, cx - width / 2, y, width, height);
}

function currentFantasyTextScale(): number {
    return textDisplaySettings.bitmapFontScale;
}

function currentFantasyLineHeight(scale = currentFantasyTextScale()): number {
    return Math.ceil(fantasyFontHeight * scale + 6);
}

function fantasyTextWidth(text: string, scale = currentFantasyTextScale()): number {
    const glyphCount = Array.from(String(text)).length;
    return glyphCount * fantasyFontAdvance * scale;
}

function truncateFantasyToWidth(text: string, maxWidth: number, scale = currentFantasyTextScale()): string {
    if (fantasyTextWidth(text, scale) <= maxWidth) return text;
    let end = text.length;
    while (end > 0 && fantasyTextWidth(text.slice(0, end) + "...", scale) > maxWidth) {
        end -= 1;
    }
    return text.slice(0, end) + "...";
}

function wrapFantasyLines(text: string, maxWidth: number, scale = currentFantasyTextScale()): string[] {
    const words = String(text || "").trim().split(/\s+/).filter(Boolean);
    const lines: string[] = [];
    const maxChars = Math.max(1, Math.floor(maxWidth / (fantasyFontAdvance * scale)));
    let line = "";
    for (const word of words) {
        const wordChars = Array.from(word);
        if (wordChars.length > maxChars) {
            if (line) {
                lines.push(line);
                line = "";
            }
            for (let index = 0; index < wordChars.length; index += maxChars) {
                const chunk = wordChars.slice(index, index + maxChars).join("");
                if (index + maxChars < wordChars.length) lines.push(chunk);
                else line = chunk;
            }
            continue;
        }
        const lineChars = Array.from(line).length;
        if (line && lineChars + 1 + wordChars.length > maxChars) {
            lines.push(line);
            line = word;
        } else {
            line = line ? line + " " + word : word;
        }
    }
    if (line) lines.push(line);
    return lines.length > 0 ? lines : [""];
}

function drawFantasyText(
    text: string,
    x: number,
    y: number,
    scale: number,
    color: string,
    align: CanvasTextAlign = "left",
    strokeColor?: string,
    shadowColor?: string
): void {
    const value = String(text || "");
    let left = x;
    const width = fantasyTextWidth(value, scale);
    if (align === "center") left -= width / 2;
    else if (align === "right") left -= width;
    if (shadowColor) {
        drawFantasyTextCells(value, left + scale, y + scale, scale, shadowColor);
    }
    if (strokeColor) {
        drawFantasyTextCells(value, left - 1, y, scale, strokeColor);
        drawFantasyTextCells(value, left + 1, y, scale, strokeColor);
        drawFantasyTextCells(value, left, y - 1, scale, strokeColor);
        drawFantasyTextCells(value, left, y + 1, scale, strokeColor);
    }
    drawFantasyTextCells(value, left, y, scale, color);
}

function drawFantasyRevealText(
    text: string,
    x: number,
    baseline: number,
    color: string,
    dimColor: string,
    reveal: TextRevealDrawState,
    scale = currentFantasyTextScale()
): void {
    const top = baseline - fantasyFontHeight * scale;
    const value = String(text || "");
    if (!reveal.active) {
        drawFantasyText(value, x, top, scale, color);
        return;
    }
    const charCost = Math.max(1, value.length);
    const visible = Math.max(0, Math.min(value.length, reveal.remaining));
    drawFantasyText(value, x, top, scale, dimColor);
    if (visible > 0) {
        drawFantasyText(value.slice(0, visible), x, top, scale, color);
    }
    if (!reveal.cursor && visible < value.length) {
        reveal.cursor = {
            x: x + fantasyTextWidth(value.slice(0, visible), scale) + 4,
            y: top + fantasyFontHeight * scale
        };
    }
    reveal.remaining -= charCost;
}

function drawFantasyTextCells(text: string, x: number, y: number, scale: number, color: string): void {
    ctx.fillStyle = color;
    Array.from(text).forEach((char, index) => {
        const glyph = fantasyGlyphs[char] || fantasyGlyphs[char.toUpperCase()] || fantasyGlyphs["?"];
        const gx = x + index * fantasyFontAdvance * scale;
        glyph.forEach((row, rowIndex) => {
            Array.from(row).forEach((cell, columnIndex) => {
                if (cell === "1") {
                    ctx.fillRect(
                        gx + columnIndex * scale,
                        y + rowIndex * scale,
                        Math.max(1, scale),
                        Math.max(1, scale)
                    );
                }
            });
        });
    });
}

function drawGothicTitle(cx: number, y: number): void {
    ctx.save();
    drawFantasyText("ADVENTURERS", cx, y - 38, 7, "#f0d07a", "center", "rgba(23, 8, 4, 0.95)", "rgba(0, 0, 0, 0.85)");
    drawFantasyText("OF RYLWEEN", cx, y + 28, 5, "#d9b764", "center", "rgba(23, 8, 4, 0.95)", "rgba(0, 0, 0, 0.85)");
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

function drawGamePanels(): void {
    drawFullImage(bitmapAssets.frame, "#10130d");
    drawImageShade("#020503", 0.1);
}

function drawCoverImage(image: HTMLImageElement, fallback: string): void {
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

function drawFullImage(image: HTMLImageElement, fallback: string): void {
    ctx.fillStyle = fallback;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    if (!image.complete || image.naturalWidth === 0) return;
    ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
}

function drawImageShade(color: string, alpha: number): void {
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.restore();
}

function drawMap(map: BitmapMap): void {
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

function drawScenePanel(frame: BitmapFrame): void {
    if (frame.portrait) {
        drawPortraitPanel(frame.portrait);
        return;
    }
    if (frame.partyPage) {
        drawPartyOverviewPanel(frame.partyPage);
        return;
    }
    const kind = (frame.backdrop || frame.mode || "").toLowerCase();
    if (!kind || kind === "wilderness" || kind.includes("title") || kind.includes("screen") || kind.includes("frame")) return;
    const bounds = scenePanelBounds();
    ctx.fillStyle = "#070a06";
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
    const image = sceneImageFor(kind);
    if (image) {
        drawCroppedImage(image, bounds.x, bounds.y, bounds.w, bounds.h);
    } else {
        if (drawEncounterBanner(kind, bounds)) return;
        if (drawSceneAtlasTile(kind, bounds)) return;
        drawSemanticSceneTile(kind, bounds.x, bounds.y, bounds.w, bounds.h);
    }
    drawSceneVignette(bounds);
}

function drawPortraitPanel(portrait: BitmapPortrait): void {
    const bounds = scenePanelBounds();
    ctx.fillStyle = "#070a06";
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
    const image = compiledGameImage(portrait.imageSlug) || portraitAsset(portrait.imageSlug);
    if (image && image.complete && image.naturalWidth > 0) {
        drawCroppedImage(image, bounds.x, bounds.y, bounds.w, bounds.h);
    } else {
        drawSemanticSceneTile("character", bounds.x, bounds.y, bounds.w, bounds.h);
    }
    drawSceneVignette(bounds);
    const labelY = bounds.y + bounds.h - 86;
    const name = portrait.memberName || "Character " + String(portrait.memberNumber || 1);
    const details = [portrait.gender, portrait.race, portrait.pcClass].filter(Boolean).join(" ");
    softPanel(bounds.x + 22, labelY, bounds.w - 44, 58, "rgba(5, 8, 6, 0.68)");
    ctx.textAlign = "center";
    ctx.font = "700 22px Palatino, 'Palatino Linotype', Georgia, serif";
    ctx.fillStyle = "#ead68a";
    ctx.fillText(truncateToWidth(name, bounds.w - 72), bounds.x + bounds.w / 2, labelY + 25);
    ctx.font = "14px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#efe4c6";
    ctx.fillText(truncateToWidth(details || "Choose a path", bounds.w - 72), bounds.x + bounds.w / 2, labelY + 47);
    ctx.textAlign = "left";
}

function drawPartyOverviewPanel(page: BitmapPartyPage): void {
    const bounds = scenePanelBounds();
    drawCroppedImage(bitmapAssets.partyOverview, bounds.x, bounds.y, bounds.w, bounds.h);
    drawSceneVignette(bounds);
    const heading = page.summary ? page.summary.partyName || "Party" : (page.kind || "Party").toUpperCase();
    const caption = page.summary ? "Company Ledger" : "Inventory Ledger";
    softPanel(bounds.x + 34, bounds.y + bounds.h - 126, bounds.w - 68, 78, "rgba(5, 8, 6, 0.70)");
    ctx.textAlign = "center";
    ctx.font = "700 25px Palatino, 'Palatino Linotype', Georgia, serif";
    ctx.fillStyle = "#ead68a";
    ctx.fillText(truncateToWidth(heading, bounds.w - 98), bounds.x + bounds.w / 2, bounds.y + bounds.h - 92);
    ctx.font = "14px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#efe4c6";
    ctx.fillText(caption, bounds.x + bounds.w / 2, bounds.y + bounds.h - 66);
    ctx.textAlign = "left";
}

function scenePanelBounds(): Bounds {
    return {
        x: layout.map.x + 10,
        y: layout.map.y + 12,
        w: layout.map.w - 20,
        h: layout.map.h - 24
    };
}

function sceneImageFor(kind: string): HTMLImageElement | null {
    const compiled = compiledGameImage(kind);
    if (compiled) return compiled;
    if (kind.includes("encounter") || kind.includes("scorpion")) return bitmapAssets.encounter;
    if (kind.includes("towne_mainstreet") || kind.includes("towne-mainstreet")) return bitmapAssets.sceneMainstreet;
    if (kind.includes("merchant")) return bitmapAssets.sceneMerchant;
    if (kind.includes("smith")) return bitmapAssets.sceneSmith;
    if (kind.includes("inn")) return bitmapAssets.sceneInn;
    if (kind.includes("tavern")) return bitmapAssets.sceneTavern;
    if (kind.includes("church")) return bitmapAssets.sceneChurch;
    if (kind.includes("guild")) return bitmapAssets.sceneGuild;
    if (kind.includes("wilderness_tent") || kind.includes("wilderness-tent") || normalizedKind(kind).endsWith("tent")) return bitmapAssets.sceneCampTent;
    if (kind.includes("wilderness_no_tent") || kind.includes("wilderness-no-tent") || kind.includes("camp")) return bitmapAssets.sceneCampNight;
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

function normalizedKind(kind: string): string {
    return kind.toLowerCase().replace(/[^a-z0-9]+/g, "");
}

function sceneAtlasIndex(kind: string): number | null {
    const normalized = normalizedKind(kind);
    if (normalized.includes("battlevictory")) return 0;
    if (normalized.includes("caveentrance") || normalized.includes("backdropcave") || normalized.endsWith("cave")) return 1;
    if (normalized.includes("insidecave") || normalized.includes("caveinterior")) return 2;
    if (normalized.includes("death")) return 3;
    if (normalized.includes("church")) return 4;
    if (normalized.includes("guild")) return 5;
    if (normalized.includes("inn")) return 6;
    if (normalized.includes("towne") && normalized.includes("mainstreet")) return 7;
    if (normalized.includes("merchant")) return 8;
    if (normalized.includes("smith")) return 9;
    if (normalized.includes("tavern")) return 10;
    if (normalized.includes("wildernesshut") || normalized.endsWith("hut")) return 11;
    if (normalized.includes("wildernessnotent")) return 12;
    if (normalized.includes("wildernesstent") || normalized.endsWith("tent")) return 13;
    if (normalized.includes("ydargcastle")) return 14;
    return null;
}

function drawSceneAtlasTile(kind: string, bounds: Bounds): boolean {
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

function drawEncounterBanner(kind: string, bounds: Bounds): boolean {
    const match = kind.match(/encounter[_-]banner[_-]?(\d+)/);
    if (!match) return false;
    const fallback = randomEncounterImage(Number(match[1]));
    if (!fallback || !fallback.complete || fallback.naturalWidth === 0) return false;
    drawCroppedImage(fallback, bounds.x, bounds.y, bounds.w, bounds.h);
    drawSceneVignette(bounds);
    return true;
}

function randomEncounterImage(index: number): HTMLImageElement {
    return [
        bitmapAssets.monsterGoblinoid,
        bitmapAssets.monsterFlying,
        bitmapAssets.monsterUndead,
        bitmapAssets.monsterBeast,
        bitmapAssets.monsterVermin
    ][Math.max(0, Math.min(4, index - 1))];
}

function drawAtlasCellCover(
    image: HTMLImageElement,
    sourceX: number,
    sourceY: number,
    sourceW: number,
    sourceH: number,
    bounds: Bounds
): void {
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

function drawCroppedImage(image: HTMLImageElement, x: number, y: number, w: number, h: number): void {
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

function drawSceneVignette(bounds: Bounds): void {
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

function drawSemanticSceneTile(kind: string, x: number, y: number, w: number, h: number): void {
    drawDither(x, y, w, h, "#151b12", "#0b0f0a", 8);
    ctx.fillStyle = "#7e9365";
    ctx.font = "18px Menlo, Monaco, Consolas, monospace";
    ctx.fillText(truncateToWidth(kind.replace(/_/g, " "), w - 32), x + 16, y + h / 2);
}

function drawTile(tile: BitmapTile, x: number, y: number, size: number): void {
    const slug = (tile.slug || "").toLowerCase();
    const settlement = (tile.settlement || "").toLowerCase();
    const variant = tile.variant || 0;
    if (slug.includes("castle-room") || slug.includes("castle-stairs")) {
        drawCastleGridTile(tile, x, y, size, variant);
        return;
    }
    if (isWaterTerrain(slug)) {
        drawConnectedWater(tile, x, y, size);
        if (tile.center) drawPartyMarker(x + size / 2, y + size / 2, size);
        return;
    }
    if (drawTerrainAtlasTile(slug, settlement, variant, x, y, size, tile.center === true)) return;
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
    else if (isWaterTerrain(slug)) drawConnectedWater(tile, x, y, size);
    else if (slug.includes("desert")) drawDesert(x, y, size, variant);
    else if (slug.includes("foothills")) drawFoothills(x, y, size, variant);
    else if (slug.includes("swamp")) drawSwamp(x, y, size, variant);
    else drawPlain(x, y, size, variant);
    ctx.strokeStyle = "rgba(5, 7, 5, 0.45)";
    ctx.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
    drawSettlementOverlay(settlement, x, y, size);
    if (tile.center) {
        ctx.strokeStyle = "#f2d35e";
        ctx.lineWidth = 3;
        ctx.strokeRect(x + 3.5, y + 3.5, size - 7, size - 7);
        ctx.lineWidth = 1;
    }
}

function drawCastleGridTile(tile: BitmapTile, x: number, y: number, size: number, variant: number): void {
    const slug = (tile.slug || "").toLowerCase();
    const isCurrent = tile.center === true;
    const isStairs = slug.includes("stairs");
    const atlas = bitmapAssets.castle;
    if (atlas.complete && atlas.naturalWidth > 0) {
        const cols = 4;
        const rows = 3;
        const sourceW = atlas.naturalWidth / cols;
        const sourceH = atlas.naturalHeight / rows;
        const row = isStairs ? 2 : slug.includes("visited") ? 1 : 0;
        const col = isStairs ? 0 : variant % cols;
        ctx.save();
        ctx.imageSmoothingEnabled = true;
        ctx.drawImage(atlas, col * sourceW, row * sourceH, sourceW, sourceH, x, y, size, size);
        ctx.restore();
        drawCastleTileOverlay(tile, x, y, size);
        return;
    }
    const isUnexplored = slug.includes("unexplored");
    const stone = isUnexplored ? "#393c42" : "#54565d";
    const mortar = isUnexplored ? "#23262b" : "#32343a";
    ctx.fillStyle = stone;
    ctx.fillRect(x, y, size, size);
    ctx.strokeStyle = mortar;
    ctx.lineWidth = 1;
    const block = Math.max(10, Math.floor(size / 4));
    for (let yy = y; yy < y + size; yy += block) {
        const offset = ((yy - y) / block + variant) % 2 === 0 ? 0 : block / 2;
        ctx.beginPath();
        ctx.moveTo(x, yy + 0.5);
        ctx.lineTo(x + size, yy + 0.5);
        ctx.stroke();
        for (let xx = x + offset; xx < x + size; xx += block) {
            ctx.beginPath();
            ctx.moveTo(xx + 0.5, yy);
            ctx.lineTo(xx + 0.5, Math.min(y + size, yy + block));
            ctx.stroke();
        }
    }
    if (isUnexplored) {
        ctx.fillStyle = "rgba(8, 10, 12, 0.34)";
        ctx.fillRect(x + 4, y + 4, size - 8, size - 8);
    }
    if (isStairs) {
        ctx.strokeStyle = "#d8c16d";
        ctx.lineWidth = Math.max(2, size / 28);
        for (let i = 0; i < 4; i += 1) {
            const inset = size * (0.22 + i * 0.08);
            ctx.strokeRect(x + inset, y + inset, size - inset * 2, size - inset * 2);
        }
        ctx.lineWidth = 1;
    }
    if (isCurrent) {
        ctx.fillStyle = "#f1d06f";
        ctx.strokeStyle = "rgba(30, 18, 3, 0.82)";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(x + size / 2, y + size / 2, size * 0.18, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        ctx.lineWidth = 1;
    }
    ctx.strokeStyle = isCurrent ? "#f3d779" : "rgba(8, 9, 10, 0.72)";
    ctx.lineWidth = isCurrent ? 3 : 1;
    ctx.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
    ctx.lineWidth = 1;
}

function drawCastleTileOverlay(tile: BitmapTile, x: number, y: number, size: number): void {
    if (tile.center === true) {
        ctx.fillStyle = "#f1d06f";
        ctx.strokeStyle = "rgba(30, 18, 3, 0.82)";
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(x + size / 2, y + size / 2, size * 0.18, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
    }
    ctx.strokeStyle = tile.center === true ? "#f3d779" : "rgba(8, 9, 10, 0.72)";
    ctx.lineWidth = tile.center === true ? 3 : 1;
    ctx.strokeRect(x + 0.5, y + 0.5, size - 1, size - 1);
    ctx.lineWidth = 1;
}

function drawTerrainAtlasTile(
    slug: string,
    settlement: string,
    variant: number,
    x: number,
    y: number,
    size: number,
    center: boolean
): boolean {
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
    drawSettlementOverlay(settlement, x, y, size);
    if (center) {
        drawPartyMarker(x + size / 2, y + size / 2, size);
    }
    return true;
}

function drawSettlementOverlay(settlement: string, x: number, y: number, size: number): void {
    if (!settlement) return;
    const label = settlement === "castle" ? "C" : settlement === "hut" ? "H" : "T";
    const color = settlement === "castle" ? "#ffe16f" : settlement === "hut" ? "#f2e3a0" : "#fff0a8";
    const badge = Math.max(20, Math.floor(size * 0.36));
    const bx = x + size - badge - 5;
    const by = y + 5;
    ctx.save();
    ctx.shadowColor = "rgba(0, 0, 0, 0.72)";
    ctx.shadowBlur = 8;
    ctx.lineWidth = Math.max(2, Math.floor(size * 0.045));
    ctx.strokeStyle = color;
    ctx.strokeRect(x + 4.5, y + 4.5, size - 9, size - 9);
    ctx.fillStyle = "rgba(9, 7, 4, 0.78)";
    ctx.fillRect(bx, by, badge, badge);
    ctx.strokeStyle = color;
    ctx.strokeRect(bx + 0.5, by + 0.5, badge - 1, badge - 1);
    ctx.shadowBlur = 0;
    ctx.fillStyle = color;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.font = "bold " + Math.floor(badge * 0.62) + "px Menlo, Monaco, Consolas, monospace";
    ctx.fillText(label, bx + badge / 2, by + badge / 2 + 1);
    ctx.restore();
}

function drawPartyMarker(cx: number, cy: number, size: number): void {
    const marker = bitmapAssets.partyMarker;
    if (!marker.complete || marker.naturalWidth === 0) return;
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.shadowColor = "rgba(0, 0, 0, 0.72)";
    ctx.shadowBlur = 8;
    ctx.shadowOffsetY = 2;
    const markerSize = size * 1.12;
    ctx.drawImage(marker, cx - markerSize / 2, cy - markerSize / 2, markerSize, markerSize);
    ctx.restore();
}

function terrainAtlasIndex(slug: string, settlement: string): TerrainAtlasIndex {
    if (settlement === "town") return {row: 3, col: 4};
    if (settlement === "castle") return {row: 4, col: 4};
    if (settlement === "hut") return {row: 4, col: 0};
    if (slug.includes("forest")) return {row: 0, col: 4};
    if (slug.includes("mountain")) return {row: 1, col: 0};
    if (isWaterTerrain(slug)) return {row: 3, col: 0};
    if (slug.includes("desert")) return {row: 2, col: 0};
    if (slug.includes("foothills")) return {row: 1, col: 4};
    if (slug.includes("swamp")) return {row: 2, col: 4};
    if (slug.includes("plain")) return {row: 0, col: 0};
    return {row: 0, col: 0};
}

function tileColors(slug: string, settlement: string, passable: boolean | undefined): TileColors {
    if (settlement === "town") return {base: "#6e7b45", shadow: "#4c5730"};
    if (settlement === "castle") return {base: "#74706a", shadow: "#504d49"};
    if (settlement === "hut") return {base: "#617348", shadow: "#3e4b30"};
    if (slug.includes("forest")) return {base: "#295738", shadow: "#1f3b29"};
    if (slug.includes("mountain")) return {base: "#6b6c64", shadow: "#484a44"};
    if (isWaterTerrain(slug)) return {base: "#2a5d74", shadow: "#1b3d4c"};
    if (slug.includes("desert")) return {base: "#c7ad64", shadow: "#8b7040"};
    if (slug.includes("foothills")) return {base: "#7b8150", shadow: "#555b38"};
    if (slug.includes("swamp")) return {base: "#46563a", shadow: "#2d3828"};
    if (!passable) return {base: "#3b3d34", shadow: "#292b24"};
    return {base: "#617b44", shadow: "#435c31"};
}

function drawPlain(x: number, y: number, size: number, variant: number): void {
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

function drawForest(x: number, y: number, size: number, variant: number): void {
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

function drawMountain(x: number, y: number, size: number, variant: number): void {
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

function drawFoothills(x: number, y: number, size: number, variant: number): void {
    ctx.fillStyle = variant % 2 === 0 ? "#a4aa6d" : "#909e5c";
    for (let i = 0; i < 3; i += 1) {
        const left = x + (i * 0.28 + 0.02 + variant * 0.015) * size;
        const top = y + (0.28 + ((i + variant) % 2) * 0.08) * size;
        triangle(left, y + size * 0.86, left + size * 0.18, top, left + size * 0.38, y + size * 0.86);
    }
    ctx.fillStyle = "#6f7548";
    ctx.fillRect(x + 6, y + size - 11, size - 12, 4);
}

function isWaterTerrain(slug: string): boolean {
    return slug.includes("water") || slug.includes("sea") || slug.includes("river");
}

function drawConnectedWater(tile: BitmapTile, x: number, y: number, size: number): void {
    const atlas = bitmapAssets.waterConnect;
    const mask = Math.max(0, Math.min(15, tile.waterMask || 0));
    if (!atlas.complete || atlas.naturalWidth === 0) {
        drawTerrainAtlasTile("water", "", tile.variant || 0, x, y, size, false);
        return;
    }
    const cols = 4;
    const maskRows = 4;
    const sourceW = atlas.naturalWidth / cols;
    const sourceH = sourceW;
    const variantCount = Math.max(1, Math.floor(atlas.naturalHeight / (sourceH * maskRows)));
    const variant = Math.max(0, tile.variant || 0) % variantCount;
    const sourceX = (mask % cols) * sourceW;
    const sourceY = (variant * maskRows + Math.floor(mask / cols)) * sourceH;
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(atlas, sourceX, sourceY, sourceW, sourceH, x, y, size, size);
    ctx.restore();
}

function drawDesert(x: number, y: number, size: number, variant: number): void {
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

function drawSwamp(x: number, y: number, size: number, variant: number): void {
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

function drawHouse(x: number, y: number, w: number, h: number): void {
    ctx.fillStyle = "#7c5334";
    ctx.fillRect(x, y + h * 0.35, w, h * 0.65);
    ctx.fillStyle = "#4b2e24";
    triangle(x - 8, y + h * 0.42, x + w / 2, y, x + w + 8, y + h * 0.42);
    ctx.fillStyle = "#d7bf73";
    ctx.fillRect(x + w * 0.18, y + h * 0.52, w * 0.18, h * 0.18);
    ctx.fillStyle = "#3b2419";
    ctx.fillRect(x + w * 0.58, y + h * 0.58, w * 0.18, h * 0.42);
}

function drawHut(x: number, y: number, w: number, h: number): void {
    ctx.fillStyle = "#6b4a2a";
    ctx.fillRect(x, y + h * 0.4, w, h * 0.6);
    ctx.fillStyle = "#3d2c1e";
    triangle(x - 3, y + h * 0.45, x + w / 2, y, x + w + 3, y + h * 0.45);
}

function drawCastle(x: number, y: number, w: number, h: number): void {
    ctx.fillStyle = "#aaa69a";
    ctx.fillRect(x, y + h * 0.25, w, h * 0.75);
    ctx.fillRect(x + 2, y, w * 0.22, h);
    ctx.fillRect(x + w * 0.75, y, w * 0.22, h);
    ctx.fillStyle = "#3c3936";
    ctx.fillRect(x + w * 0.42, y + h * 0.58, w * 0.16, h * 0.42);
}

function drawStatus(status: BitmapStatus | null | undefined, lines: string[]): void {
    statusHotspots = [];
    const x = layout.status.x;
    const y = layout.status.y;
    const w = layout.status.w;
    if (!status) {
        drawFittedText(lines.slice(0, 5).join("   "), x + 20, y + 44, w - 40, 16, 12, "#d6e0b3");
        return;
    }

    const chips = [
        {label: "TIME", value: status.time, color: "#f0d46d", width: 138},
        {label: "STATUS", value: status.condition, color: statusColor(status.condition), width: 164},
        {label: "POS", value: status.x + "x" + status.y, color: "#9ec1e4", width: 108},
        {label: "TORCHES", value: String(status.torches), color: "#d8c16d", width: 126},
        {label: "FOOD", value: String(status.food), color: "#d6e68d", width: 104},
        {label: "MOVES LEFT", value: String(status.moves), color: "#a9d4a2", width: 142},
        {label: "GOLD", value: String(status.gold), color: "#e8c56a", width: 116}
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
    const memberW = Math.min(230, Math.floor((w - 36 - memberGap * Math.max(0, members.length - 1)) / Math.max(1, members.length)));
    let mx = x + Math.floor((w - (memberW * members.length + memberGap * Math.max(0, members.length - 1))) / 2);
    for (const member of members) {
        drawMemberStatus(member, mx, y + 50, memberW);
        mx += memberW + memberGap;
    }
}

function drawStatusChip(x: number, y: number, w: number, label: string, value: string, color: string): void {
    ctx.font = "11px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#899a72";
    ctx.fillText(label, x + 8, y + 17);
    const labelWidth = Math.min(w - 42, Math.ceil(ctx.measureText(label).width) + 20);
    ctx.font = "700 15px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = color;
    ctx.fillText(truncateToWidth(value, w - labelWidth - 8), x + labelWidth, y + 17);
}

function drawMemberStatus(member: BitmapStatusMember, x: number, y: number, w: number): void {
    const label = member.slot + " " + member.name;
    const barX = x + 5;
    const barY = y + 18;
    const barW = w - 10;
    ctx.font = "700 13px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = member.dead ? "#b86f68" : "#d8d0ae";
    ctx.fillText(truncateToWidth(label, w - 10), x + 5, y + 11);
    drawTinyBar(barX, barY, barW, 7, member.hp, member.hpMax, member.dead ? "#7d3533" : "#b85748");
    statusHotspots.push({
        x: barX,
        y: barY - 2,
        w: barW,
        h: 11,
        text: member.name + " health: " + member.hp + "/" + member.hpMax
    });
    if (member.manaMax) {
        const relevantMana = member.manaRelevant !== false;
        const manaValue = relevantMana ? member.mana || 0 : member.manaMax;
        drawTinyBar(barX, barY + 12, barW, 6, manaValue, member.manaMax, relevantMana ? "#5b8fce" : "#596170");
        statusHotspots.push({
            x: barX,
            y: barY + 10,
            w: barW,
            h: 10,
            text: relevantMana ? member.name + " mana: " + (member.mana || 0) + "/" + member.manaMax : member.name + ": not a spellcaster"
        });
    }
}

function drawTinyBar(x: number, y: number, w: number, h: number, value: number, max: number, color: string): void {
    const ratio = Math.max(0, Math.min(1, value / Math.max(1, max)));
    ctx.fillStyle = "rgba(5, 8, 5, 0.66)";
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = color;
    ctx.fillRect(x, y, Math.max(1, Math.floor(w * ratio)), h);
}

function statusColor(condition: string): string {
    if (condition === "STARVING") return "#dc6b57";
    if (condition === "HUNGRY") return "#e0ad55";
    return "#9bd073";
}

function drawTextPanel(frame: BitmapFrame): void {
    if (frame.partyPage) {
        drawPartyTextPanel(frame.partyPage);
        return;
    }
    const x = layout.text.x + 28;
    const y = layout.text.y + 58;
    const maxWidth = layout.text.w - 56;
    ctx.fillStyle = "#f1ead6";
    ctx.font = "27px Menlo, Monaco, Consolas, monospace";
    ctx.fillText(truncateToWidth(frame.title || "", maxWidth), x, y);
    ctx.font = "15px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#bdb496";
    ctx.fillText(truncateToWidth(frame.subtitle || "", maxWidth), x, y + 30);
    let ty = y + 64;
    const bottom = frame.textInput ? layout.text.y + layout.text.h - 96 : layout.text.y + layout.text.h - 32;
    const reveal = textRevealDrawState(frame);
    const bitmapScale = currentFantasyTextScale();
    const bitmapLineHeight = currentFantasyLineHeight(bitmapScale);
    for (const line of (frame.textLines || [])) {
        if (textDisplaySettings.bitmapFont) {
            for (const part of wrapFantasyLines(line, maxWidth, bitmapScale)) {
                if (ty > bottom) break;
                drawFantasyRevealText(
                    truncateFantasyToWidth(part, maxWidth, bitmapScale),
                    x,
                    ty,
                    "#efe4c6",
                    "rgba(239, 228, 198, 0.25)",
                    reveal,
                    bitmapScale
                );
                ty += bitmapLineHeight;
            }
        } else {
            ctx.font = "15px Menlo, Monaco, Consolas, monospace";
            for (const part of wrapCanvasLines(line, maxWidth)) {
                if (ty > bottom) break;
                drawRevealText(truncateToWidth(part, maxWidth), x, ty, "#efe4c6", "rgba(239, 228, 198, 0.25)", reveal);
                ty += 20;
            }
        }
    }
    drawTextRevealCursor(reveal);
    drawTextInput(frame.textInput, x, layout.text.y + layout.text.h - 72, maxWidth);
}

function drawPartyTextPanel(page: BitmapPartyPage): void {
    const x = layout.text.x + 28;
    const y = layout.text.y + 48;
    const maxWidth = layout.text.w - 56;
    if (page.member) drawPartyMemberText(page.member, x, y, maxWidth);
    else if (page.summary) drawPartySummaryText(page.summary, x, y, maxWidth);
    else drawPartyInventoryText(page, x, y, maxWidth);
    drawPartyPause(page.pauseLine || "", x, layout.text.y + layout.text.h - 30, maxWidth);
}

function drawPartyMemberText(member: BitmapPartyMemberPage, x: number, y: number, maxWidth: number): void {
    ctx.font = "700 28px Palatino, 'Palatino Linotype', Georgia, serif";
    ctx.fillStyle = "#f1df9f";
    ctx.fillText(truncateToWidth(member.name || "", maxWidth), x, y);
    ctx.font = "14px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#bdb496";
    ctx.fillText(truncateToWidth([member.gender, member.race, member.pcClass].filter(Boolean).join(" "), maxWidth), x, y + 26);
    drawPartyDivider(x, y + 42, maxWidth);
    drawPartyStatGrid([
        ["Level", member.level],
        ["XP", member.experience],
        ["Health", member.hitPoints + "/" + member.hitPointMax],
        ["Mana", member.mana + "/" + member.manaMax],
        ["Weapon Skill", member.weaponSkill],
        ["Body Health", member.health],
        ["Wisdom", member.wisdom],
        ["Charisma", member.charisma]
    ], x, y + 70, maxWidth);
    drawPartySection("Equipment", x, y + 236, maxWidth);
    drawPartyInfoRow("Weapon", member.weapon, x, y + 270, maxWidth);
    drawPartyInfoRow("Armor", member.armor, x, y + 302, maxWidth);
    drawPartyInfoRow("Status", member.status, x, y + 334, maxWidth);
}

function drawPartySummaryText(summary: BitmapPartySummaryPage, x: number, y: number, maxWidth: number): void {
    ctx.font = "700 28px Palatino, 'Palatino Linotype', Georgia, serif";
    ctx.fillStyle = "#f1df9f";
    ctx.fillText(truncateToWidth(summary.partyName || "Party", maxWidth), x, y);
    ctx.font = "14px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#bdb496";
    ctx.fillText("Party Ledger", x, y + 26);
    drawPartyDivider(x, y + 42, maxWidth);
    drawPartyStatGrid([
        ["Members", summary.memberCount],
        ["Gold", summary.gold],
        ["Food", summary.food],
        ["Torches", summary.torches],
        ["Moves", summary.moves],
        ["Rest", summary.rest],
        ["Kills", summary.kills]
    ], x, y + 70, maxWidth);
}

function drawPartyInventoryText(page: BitmapPartyPage, x: number, y: number, maxWidth: number): void {
    ctx.font = "700 28px Palatino, 'Palatino Linotype', Georgia, serif";
    ctx.fillStyle = "#f1df9f";
    ctx.fillText("Inventory", x, y);
    drawPartyDivider(x, y + 24, maxWidth);
    let ty = y + 58;
    const bottom = layout.text.y + layout.text.h - 62;
    const entries = page.inventory && page.inventory.length > 0 ? page.inventory : ["Nothing carried."];
    const bitmapScale = currentFantasyTextScale();
    const bitmapLineHeight = currentFantasyLineHeight(bitmapScale);
    for (const entry of entries) {
        if (textDisplaySettings.bitmapFont) {
            for (const part of wrapFantasyLines(entry, maxWidth, bitmapScale)) {
                if (ty > bottom) return;
                drawFantasyText(truncateFantasyToWidth(part, maxWidth, bitmapScale), x, ty - fantasyFontHeight * bitmapScale, bitmapScale, "#efe4c6");
                ty += bitmapLineHeight + 1;
            }
        } else {
            ctx.font = "15px Menlo, Monaco, Consolas, monospace";
            ctx.fillStyle = "#efe4c6";
            for (const part of wrapCanvasLines(entry, maxWidth)) {
                if (ty > bottom) return;
                ctx.fillText(truncateToWidth(part, maxWidth), x, ty);
                ty += 20;
            }
        }
    }
}

function drawPartyStatGrid(stats: PartyStat[], x: number, y: number, maxWidth: number): void {
    const colGap = 24;
    const colW = Math.floor((maxWidth - colGap) / 2);
    stats.forEach((stat, index) => {
        const col = index % 2;
        const row = Math.floor(index / 2);
        drawPartyInfoRow(stat[0], String(stat[1]), x + col * (colW + colGap), y + row * 34, colW);
    });
}

function drawPartySection(label: string, x: number, y: number, maxWidth: number): void {
    ctx.font = "700 15px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#d9bd70";
    ctx.fillText(label.toUpperCase(), x, y);
    drawPartyDivider(x, y + 10, maxWidth);
}

function drawPartyInfoRow(label: string, value: string | number | undefined | null, x: number, y: number, width: number): void {
    ctx.font = "12px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#9a937b";
    ctx.fillText(label, x, y);
    ctx.font = "700 16px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#f1ead6";
    ctx.fillText(truncateToWidth(String(value || "-"), width), x, y + 18);
}

function drawPartyDivider(x: number, y: number, width: number): void {
    ctx.fillStyle = "rgba(203, 176, 104, 0.42)";
    ctx.fillRect(x, y, width, 1);
}

function drawPartyPause(text: string, x: number, y: number, maxWidth: number): void {
    if (!text) return;
    if (textDisplaySettings.bitmapFont) {
        const scale = Math.max(1.35, currentFantasyTextScale() - 0.2);
        drawFantasyText(truncateFantasyToWidth(text, maxWidth, scale), x, y - fantasyFontHeight * scale, scale, "#bdb496");
        return;
    }
    ctx.font = "13px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#bdb496";
    ctx.fillText(truncateToWidth(text, maxWidth), x, y);
}

function drawTextInput(input: BitmapTextInput | null | undefined, x: number, y: number, maxWidth: number): void {
    if (!input) return;
    const boxY = y - 26;
    const boxHeight = 46;
    softPanel(x - 10, boxY, maxWidth + 20, boxHeight, "rgba(9, 13, 10, 0.76)");
    ctx.fillStyle = "rgba(203, 176, 104, 0.72)";
    ctx.fillRect(x, y + 8, maxWidth, 1);
    ctx.font = "19px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#f7ebcc";
    const value = input.value || "";
    const visibleValue = truncateToWidth(value, maxWidth - 76);
    ctx.fillText(visibleValue, x, y);
    const cursorX = Math.min(x + ctx.measureText(visibleValue).width + 4, x + maxWidth - 68);
    ctx.fillStyle = "#f0d58a";
    ctx.fillRect(cursorX, y - 21, 2, 27);
    ctx.font = "13px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#bdb496";
    ctx.textAlign = "right";
    ctx.fillText(String(value.length) + "/" + String(input.maxLength || 0), x + maxWidth, y);
    ctx.textAlign = "left";
}

function textRevealDrawState(candidate: BitmapFrame): TextRevealDrawState {
    const limit = textRevealLimit(candidate);
    return {
        active: Number.isFinite(limit),
        cursor: null,
        remaining: limit
    };
}

function drawRevealText(text: string, x: number, y: number, color: string, dimColor: string, reveal: TextRevealDrawState): void {
    if (!reveal.active) {
        ctx.fillStyle = color;
        ctx.fillText(text, x, y);
        return;
    }
    const charCost = Math.max(1, text.length);
    const visible = Math.max(0, Math.min(text.length, reveal.remaining));
    ctx.fillStyle = dimColor;
    ctx.fillText(text, x, y);
    if (visible > 0) {
        ctx.fillStyle = color;
        ctx.fillText(text.slice(0, visible), x, y);
    }
    if (!reveal.cursor && visible < text.length) {
        reveal.cursor = {x: x + ctx.measureText(text.slice(0, visible)).width + 4, y};
    }
    reveal.remaining -= charCost;
}

function drawTextRevealCursor(reveal: TextRevealDrawState): void {
    if (!(reveal.active && reveal.cursor)) return;
    const alpha = 0.58 + Math.sin(performance.now() / 58) * 0.24;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.fillStyle = "#f0d58a";
    ctx.fillRect(reveal.cursor.x, reveal.cursor.y - 15, 2, 18);
    ctx.restore();
}

function drawMessage(message: string): void {
    if (!message) return;
    const y = layout.text.y + 24;
    softPanel(layout.text.x + 24, y, layout.text.w - 48, 74, "rgba(20, 24, 17, 0.78)");
    ctx.font = "18px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#f0d58a";
    wrapCanvasText(message, layout.text.x + 48, y + 32, layout.text.w - 96, 23);
}

function drawFittedText(
    text: string,
    x: number,
    y: number,
    maxWidth: number,
    startSize: number,
    minSize: number,
    color: string
): void {
    ctx.fillStyle = color;
    let size = startSize;
    do {
        ctx.font = size + "px Menlo, Monaco, Consolas, monospace";
        if (ctx.measureText(text).width <= maxWidth) break;
        size -= 1;
    } while (size >= minSize);
    ctx.fillText(truncateToWidth(text, maxWidth), x, y);
}

function truncateToWidth(text: string, maxWidth: number): string {
    if (ctx.measureText(text).width <= maxWidth) return text;
    let end = text.length;
    while (end > 0 && ctx.measureText(text.slice(0, end) + "...").width > maxWidth) {
        end -= 1;
    }
    return text.slice(0, end) + "...";
}

function softPanel(x: number, y: number, w: number, h: number, fill: string): void {
    ctx.fillStyle = fill;
    ctx.fillRect(x, y, w, h);
}

function drawDither(x: number, y: number, w: number, h: number, a: string, b: string, step: number): void {
    ctx.fillStyle = a;
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = b;
    for (let yy = y; yy < y + h; yy += step) {
        for (let xx = x + ((yy / step) % 2) * step; xx < x + w; xx += step * 2) {
            ctx.fillRect(xx, yy, step, step);
        }
    }
}

function triangle(x1: number, y1: number, x2: number, y2: number, x3: number, y3: number): void {
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.lineTo(x3, y3);
    ctx.closePath();
    ctx.fill();
}

function wrapCanvasText(text: string, x: number, y: number, maxWidth: number, lineHeight: number): void {
    for (const line of wrapCanvasLines(text, maxWidth)) {
        ctx.fillText(line, x, y);
        y += lineHeight;
    }
}

function wrapCanvasLines(text: string, maxWidth: number): string[] {
    const words = String(text || "").split(" ");
    const lines: string[] = [];
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

configureTextRevealControls();
configureTextDisplayControls();
configureRuntimePreferenceControls();
refreshServerInfo().catch(() => undefined);
connect();
