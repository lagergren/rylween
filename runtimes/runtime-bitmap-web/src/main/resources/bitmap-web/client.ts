type Bounds = { x: number; y: number; w: number; h: number };
type CanvasPoint = { x: number; y: number };
type PerspectiveQuad = {
    nearLeft: CanvasPoint;
    nearRight: CanvasPoint;
    farRight: CanvasPoint;
    farLeft: CanvasPoint;
};
type WildernessBackdropTheme = {
    skyTop: string;
    skyMid: string;
    skyLow: string;
    haze: string;
    glow: string;
    groundTint: string;
    lightAlpha: number;
};
type Layout = { map: Bounds; text: Bounds; status: Bounds };
type BitmapAssets = Record<string, HTMLImageElement>;
type StatusHotspot = Bounds & { text: string; member?: BitmapPartyMemberPage | null };
// A clickable region of the canvas and the input token a click sends, the same
// token the matching key press would send.
type ChoiceTarget = Bounds & { token: string };
type ChoiceSegment = { start: number; end: number; token: string };
type SceneFadeSnapshot = { image: HTMLCanvasElement; bounds: Bounds };
type TitleFade = { image: HTMLCanvasElement; frame: BitmapFrame; started: number; duration: number };
type SceneFade = { image: HTMLCanvasElement; bounds: Bounds; started: number; duration: number };
type TerrainAtlasIndex = { row: number; col: number };
type TileColors = { base: string; shadow: string };
type PartyStat = [string, string | number | undefined | null];
type TextEffect = "None" | "Fast" | "Reveal";
type WildernessFacing = "North" | "East" | "South" | "West";
type StatusSeverity = "normal" | "warning" | "danger";
type StatusChip = { label: string; value: string; color: string; width: number; severity?: StatusSeverity };
type TextRevealSettings = { enabled: boolean; charsPerSecond: number };
type TextDisplaySettings = { bitmapFont: boolean; bitmapFontScale: number; bitmapLineSpacing: number };
type RuntimePreferenceSettings = { pauseDelayScale: number };
type WildernessDisplaySettings = { perspective: boolean; facing: WildernessFacing };
type WildernessRotationTransition = {
    from: WildernessFacing;
    to: WildernessFacing;
    direction: number;
    started: number;
    duration: number;
};
type PendingWildernessMove = { facing: WildernessFacing; x: number; y: number };
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
    x: number;
    y: number;
    variant?: number;
    passable?: boolean;
    center?: boolean;
    waterMask?: number;
};
type TerrainTransitionAsset = { edge: HTMLImageElement; corner: HTMLImageElement };
type TerrainTransitionSide = "north" | "east" | "south" | "west";
type TerrainTransitionCorner = "northwest" | "northeast" | "southeast" | "southwest";

type BitmapPerspectiveRect = {
    x: number;
    y: number;
    w: number;
    h: number;
};

type BitmapPerspectiveCell = {
    worldX: number;
    worldY: number;
    forward: number;
    side: number;
    distanceBand: number;
    scale: number;
    rect: BitmapPerspectiveRect;
    zOrder: number;
    tile: BitmapTile;
};

type BitmapPerspectiveView = {
    facing: WildernessFacing;
    cells: BitmapPerspectiveCell[];
};

type BitmapPerspectiveSet = {
    width: number;
    height: number;
    views: BitmapPerspectiveView[];
};

type BitmapMap = {
    rows?: BitmapTile[][];
    tileSize?: number;
    perspective?: BitmapPerspectiveSet | null;
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
    details?: BitmapPartyMemberPage | null;
};

type BitmapStatus = {
    time: string;
    condition: string;
    x: number;
    y: number;
    facing?: WildernessFacing | null;
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
    dexterity?: number;
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
    kills?: BitmapPartyKillsPage;
};

type BitmapPartyKillsPage = {
    title?: string[];
    entries?: { monster?: string; count?: number }[];
    empty?: string[];
};

type BitmapDocumentLine = {
    kind?: string;
    text?: string;
};

type BitmapDocument = {
    columns?: number;
    rows?: number;
    lines?: BitmapDocumentLine[];
    prompt?: string;
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
    document?: BitmapDocument | null;
    music?: string | null;
};

type ServerStatus = {
    activeUsers: string[];
    authRequired: boolean;
    connectedUsers: number;
    currentUser?: string | null;
    serverType: string;
    version: string;
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
const gameTitle = requireElement("gameTitle", HTMLElement);
const textRevealToggle = requireElement("textRevealToggle", HTMLInputElement);
const bitmapFontToggle = requireElement("bitmapFontToggle", HTMLInputElement);
const wildernessViewModeSelect = requireElement("wildernessViewModeSelect", HTMLSelectElement);
const bitmapFontScale = requireElement("bitmapFontScale", HTMLInputElement);
const bitmapFontScaleValue = requireElement("bitmapFontScaleValue", HTMLElement);
const bitmapLineSpacing = requireElement("bitmapLineSpacing", HTMLInputElement);
const bitmapLineSpacingValue = requireElement("bitmapLineSpacingValue", HTMLElement);
const textRevealSpeed = requireElement("textRevealSpeed", HTMLInputElement);
const textRevealSpeedValue = requireElement("textRevealSpeedValue", HTMLElement);
const pauseDelayScale = requireElement("pauseDelayScale", HTMLInputElement);
const pauseDelayScaleValue = requireElement("pauseDelayScaleValue", HTMLElement);
const soundToggle = requireElement("soundToggle", HTMLInputElement);
const soundVolume = requireElement("soundVolume", HTMLInputElement);
const soundVolumeValue = requireElement("soundVolumeValue", HTMLElement);
const canvasTooltip = requireElement("canvasTooltip", HTMLElement);
const connectionDot = requireElement("connectionDot", HTMLElement);
const connectionText = requireElement("connectionText", HTMLElement);
let socket: WebSocket | null = null;
let frame: BitmapFrame | null = null;
let currentCommands: BitmapCommand[] = [];
let statusHotspots: StatusHotspot[] = [];
let choiceTargets: ChoiceTarget[] = [];
let pointerPoint: CanvasPoint | null = null;
// The highlighted choice in a menu, which Up/Down move and Enter picks, and the
// menu's choice keys in screen order.
let menuHighlight: string | null = null;
let menuHighlightTokens: string[] = [];
let pointerRedraw = 0;
let titleFade: TitleFade | null = null;
let sceneFade: SceneFade | null = null;
let textReveal: TextReveal | null = null;
let textRevealAnimation = 0;
let wildernessRotationTransition: WildernessRotationTransition | null = null;
let pendingWildernessMove: PendingWildernessMove | null = null;
let centerSettlementSceneSuppressedKey: string | null = null;
let serverFacingInitialized = false;
let wildernessRotationAnimation = 0;
const assetVersion = "20260518-terrain-transitions";
const textRevealEnabledKey = "rylween.bitmap.textReveal.enabled";
const textRevealSpeedKey = "rylween.bitmap.textReveal.speed";
const bitmapFontEnabledKey = "rylween.bitmap.textFont.bitmap.v3";
const bitmapFontScaleKey = "rylween.bitmap.textFont.scale";
const bitmapLineSpacingKey = "rylween.bitmap.textFont.lineSpacing.v2";
const pauseDelayScaleKey = "rylween.bitmap.pauseDelay.scale";
const wildernessViewModeStorageKey = "rylween.bitmap.wilderness.viewMode.v1";
const pauseDelayScaleMessagePrefix = "settings:pauseDelayScale=";
const defaultBitmapFontScale = 1.75;
const minBitmapFontScale = 1.45;
const maxBitmapFontScale = 2.1;
const defaultBitmapLineSpacing = 1.3;
const minBitmapLineSpacing = 0.9;
const maxBitmapLineSpacing = 1.8;
const defaultPauseDelayScale = 1.0;
const documentFontFamily = "Menlo, Monaco, Consolas, 'DejaVu Sans Mono', monospace";
const minPauseDelayScale = 0.25;
const maxPauseDelayScale = 2.0;
const textRevealSettings: TextRevealSettings = {
    enabled: storedBoolean(textRevealEnabledKey, true),
    charsPerSecond: storedNumber(textRevealSpeedKey, 220, 90, 280)
};
const textDisplaySettings: TextDisplaySettings = {
    bitmapFont: storedBoolean(bitmapFontEnabledKey, false),
    bitmapFontScale: storedNumber(bitmapFontScaleKey, defaultBitmapFontScale, minBitmapFontScale, maxBitmapFontScale),
    bitmapLineSpacing: storedNumber(bitmapLineSpacingKey, defaultBitmapLineSpacing, minBitmapLineSpacing, maxBitmapLineSpacing)
};
const runtimePreferenceSettings: RuntimePreferenceSettings = {
    pauseDelayScale: storedNumber(pauseDelayScaleKey, defaultPauseDelayScale, minPauseDelayScale, maxPauseDelayScale)
};
const soundEnabledKey = "rylween.bitmap.sound.enabled";
const soundVolumeKey = "rylween.bitmap.sound.volume";
const defaultSoundVolume = 70;
const musicFadeSeconds = 1.2;
const musicLevel = 0.8;
const maxDecodedMusicTracks = 3;

type AudioCatalogMessage = {
    type: "audio";
    sounds: Record<string, string>;
    music: Record<string, string>;
    cues: Record<string, string[]>;
};

type SoundMessage = {
    type: "sound";
    id: string;
};

type MusicMessage = {
    type: "music";
    id: string | null;
};

type PlayingMusic = {
    id: string;
    source: AudioBufferSourceNode;
    gain: GainNode;
};

/**
 * Plays the game's sound effects and looping music with Web Audio.
 *
 * Browsers only allow audio after the player interacts with the page, so the
 * audio context starts on the first key press or click; music asked for before
 * then starts with it. Sound effects are decoded once and kept. Only the few
 * most recent music tracks stay decoded, since one takes tens of megabytes.
 */
class GameAudio {
    private context: AudioContext | null = null;
    private output: GainNode | null = null;
    private soundUrls = new Map<string, string>();
    private musicUrls = new Map<string, string>();
    private cues = new Map<string, string[]>();
    private cueRotation = new Map<string, number>();
    private soundBuffers = new Map<string, Promise<AudioBuffer | null>>();
    private musicBuffers = new Map<string, Promise<AudioBuffer | null>>();
    private wantedMusic: string | null = null;
    private playing: PlayingMusic | null = null;

    constructor(private enabled: boolean, private volume: number) {}

    setCatalog(catalog: AudioCatalogMessage): void {
        this.soundUrls = new Map(Object.entries(catalog.sounds || {}));
        this.musicUrls = new Map(Object.entries(catalog.music || {}));
        this.cues = new Map(Object.entries(catalog.cues || {}));
        this.soundBuffers.clear();
        this.musicBuffers.clear();
        this.prefetchSounds();
        this.syncMusic();
    }

    /** Starts audio on the first user gesture; later calls only resume a suspended context. */
    unlock(): void {
        if (!this.context) {
            if (typeof AudioContext === "undefined") return;
            this.context = new AudioContext();
            this.output = this.context.createGain();
            this.output.connect(this.context.destination);
            this.applyVolume();
            this.prefetchSounds();
        }
        if (this.enabled && this.context.state === "suspended") this.context.resume().catch(() => undefined);
        this.syncMusic();
    }

    soundUrlList(): string[] {
        return Array.from(this.soundUrls.values());
    }

    musicUrlList(): string[] {
        return Array.from(this.musicUrls.values());
    }

    setEnabled(enabled: boolean): void {
        this.enabled = enabled;
        if (!this.context) return;
        if (enabled) {
            this.context.resume().catch(() => undefined);
            this.syncMusic();
        } else {
            this.context.suspend().catch(() => undefined);
        }
    }

    setVolume(volume: number): void {
        this.volume = volume;
        this.applyVolume();
    }

    playSound(id: string): void {
        const context = this.context;
        const output = this.output;
        if (!this.enabled || !context || !output) return;
        this.soundBuffer(id).then((buffer) => {
            if (!buffer || !this.enabled) return;
            const source = context.createBufferSource();
            source.buffer = buffer;
            source.connect(output);
            source.start();
        });
    }

    /** Plays one of the sounds the game maps to a client-side cue, in rotation. */
    playCue(cue: string): void {
        const sounds = this.cues.get(cue);
        if (!sounds || sounds.length === 0) return;
        const next = this.cueRotation.get(cue) || 0;
        this.cueRotation.set(cue, next + 1);
        this.playSound(sounds[next % sounds.length]);
    }

    /** Crossfades to a looping track, or fades out when null. Asking for the current track changes nothing. */
    setMusic(id: string | null | undefined): void {
        this.wantedMusic = id || null;
        this.syncMusic();
    }

    private syncMusic(): void {
        const context = this.context;
        const output = this.output;
        const wanted = this.wantedMusic;
        if (!context || !output) return;
        if ((this.playing ? this.playing.id : null) === wanted) return;
        this.fadeOutPlaying(context);
        if (!wanted) return;
        this.musicBuffer(wanted).then((buffer) => {
            if (!buffer || this.wantedMusic !== wanted) return;
            if (this.playing && this.playing.id === wanted) return;
            this.fadeOutPlaying(context);
            const gain = context.createGain();
            gain.gain.setValueAtTime(0, context.currentTime);
            gain.gain.linearRampToValueAtTime(musicLevel, context.currentTime + musicFadeSeconds);
            gain.connect(output);
            const source = context.createBufferSource();
            source.buffer = buffer;
            source.loop = true;
            source.connect(gain);
            source.start();
            this.playing = {id: wanted, source, gain};
        });
    }

    private fadeOutPlaying(context: AudioContext): void {
        const playing = this.playing;
        if (!playing) return;
        this.playing = null;
        const now = context.currentTime;
        playing.gain.gain.cancelScheduledValues(now);
        playing.gain.gain.setValueAtTime(playing.gain.gain.value, now);
        playing.gain.gain.linearRampToValueAtTime(0, now + musicFadeSeconds);
        playing.source.stop(now + musicFadeSeconds + 0.05);
    }

    private applyVolume(): void {
        if (!this.context || !this.output) return;
        const level = this.volume / 100;
        this.output.gain.setTargetAtTime(level * level, this.context.currentTime, 0.02);
    }

    private prefetchSounds(): void {
        if (!this.context) return;
        this.soundUrls.forEach((_url, id) => {
            this.soundBuffer(id);
        });
    }

    private soundBuffer(id: string): Promise<AudioBuffer | null> {
        let buffer = this.soundBuffers.get(id);
        if (!buffer) {
            buffer = this.decode(this.soundUrls.get(id));
            this.soundBuffers.set(id, buffer);
        }
        return buffer;
    }

    private musicBuffer(id: string): Promise<AudioBuffer | null> {
        let buffer = this.musicBuffers.get(id);
        if (buffer) {
            this.musicBuffers.delete(id);
        } else {
            buffer = this.decode(this.musicUrls.get(id));
        }
        this.musicBuffers.set(id, buffer);
        while (this.musicBuffers.size > maxDecodedMusicTracks) {
            const oldest = this.musicBuffers.keys().next().value as string;
            this.musicBuffers.delete(oldest);
        }
        return buffer;
    }

    private decode(url: string | undefined): Promise<AudioBuffer | null> {
        const context = this.context;
        if (!context || !url) return Promise.resolve(null);
        return fetch(url)
            .then((response) => (response.ok ? response.arrayBuffer() : Promise.reject(new Error("audio " + response.status))))
            .then((data) => context.decodeAudioData(data))
            .catch(() => null);
    }
}

const gameAudio = new GameAudio(
    storedBoolean(soundEnabledKey, true),
    storedNumber(soundVolumeKey, defaultSoundVolume, 0, 100)
);
const wildernessDisplaySettings: WildernessDisplaySettings = {
    perspective: storedWildernessViewMode() === "3d",
    facing: "South"
};
const wildernessBackdropThemes: Record<string, WildernessBackdropTheme> = {
    dawn: {
        skyTop: "#14233a",
        skyMid: "#5d6f74",
        skyLow: "#c79b57",
        haze: "rgba(245, 183, 96, 0.18)",
        glow: "rgba(247, 184, 89, 0.36)",
        groundTint: "rgba(224, 179, 77, 0.12)",
        lightAlpha: 0.1
    },
    midday: {
        skyTop: "#1f4667",
        skyMid: "#6e9eb0",
        skyLow: "#d5c28a",
        haze: "rgba(217, 222, 178, 0.18)",
        glow: "rgba(246, 220, 142, 0.24)",
        groundTint: "rgba(221, 214, 121, 0.1)",
        lightAlpha: 0.06
    },
    twilight: {
        skyTop: "#171936",
        skyMid: "#624366",
        skyLow: "#b46840",
        haze: "rgba(205, 91, 73, 0.2)",
        glow: "rgba(218, 92, 62, 0.3)",
        groundTint: "rgba(194, 88, 58, 0.12)",
        lightAlpha: 0.16
    },
    midnight: {
        skyTop: "#050713",
        skyMid: "#10182b",
        skyLow: "#1d2b36",
        haze: "rgba(98, 124, 162, 0.14)",
        glow: "rgba(145, 174, 218, 0.14)",
        groundTint: "rgba(29, 45, 72, 0.2)",
        lightAlpha: 0.28
    }
};
const layout: Layout = {
    map: {x: 114, y: 64, w: 420, h: 482},
    text: {x: 746, y: 64, w: 420, h: 482},
    status: {x: 154, y: 566, w: 972, h: 92}
};
const fantasyFontHeight = 7;
const fantasyFontAdvance = 6.2;
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
    title: loadAsset(assetUrl("/assets/title/title-rider.png")),
    titleLogo: loadAsset(assetUrl("/assets/title/title-logo.png")),
    frame: loadAsset(assetUrl("/assets/ui/screen-frame.png")),
    town: loadAsset(assetUrl("/assets/town/town-mainstreet.png")),
    encounter: loadAsset(assetUrl("/assets/encounters/encounter-scorpion.png")),
    bestiary: loadAsset(assetUrl("/assets/encounters/encounter-bestiary.png")),
    scenes: loadAsset(assetUrl("/assets/atlases/scene-atlas.png")),
    terrain: loadAsset(assetUrl("/assets/atlases/terrain-atlas.png")),
    castle: loadAsset(assetUrl("/assets/atlases/castle-atlas.png")),
    waterConnect: loadAsset(assetUrl("/assets/atlases/water-connect-atlas.png")),
    partyMarker: loadAsset(assetUrl("/assets/party/party-rider.png")),
    partyOverview: loadAsset(assetUrl("/assets/party/party-overview.png")),
    sceneMainstreet: loadAsset(assetUrl("/assets/scenes/scene-thord-mainstreet.png")),
    sceneMerchant: loadAsset(assetUrl("/assets/scenes/scene-merchant.png")),
    sceneSmith: loadAsset(assetUrl("/assets/scenes/scene-smith.png")),
    sceneInn: loadAsset(assetUrl("/assets/scenes/scene-inn.png")),
    sceneTavern: loadAsset(assetUrl("/assets/scenes/scene-tavern.png")),
    sceneChurch: loadAsset(assetUrl("/assets/scenes/scene-church.png")),
    sceneGuild: loadAsset(assetUrl("/assets/scenes/scene-guild-closed.png")),
    sceneCampTent: loadAsset(assetUrl("/assets/scenes/scene-camp-tent.png")),
    sceneCampNight: loadAsset(assetUrl("/assets/scenes/scene-camp-night.png")),
    monsterGoblinoid: loadAsset(assetUrl("/assets/monsters/monster-goblinoid.png")),
    monsterUndead: loadAsset(assetUrl("/assets/monsters/monster-undead.png")),
    monsterBeast: loadAsset(assetUrl("/assets/monsters/monster-beast.png")),
    monsterFlying: loadAsset(assetUrl("/assets/monsters/monster-flying.png")),
    monsterVermin: loadAsset(assetUrl("/assets/monsters/monster-vermin.png")),
    monsterBrute: loadAsset(assetUrl("/assets/monsters/monster-brute.png")),
    monsterHumanoidMagic: loadAsset(assetUrl("/assets/monsters/monster-humanoid-magic.png")),
    monsterBoss: loadAsset(assetUrl("/assets/monsters/monster-boss.png"))
};
const portraitAssets = new Map<string, HTMLImageElement>();
const gameImageAssets = new Map<string, HTMLImageElement>();
const compiledBitmapAssets = new Map<string, BitmapImageAsset>();
const perspectiveTerrainAssets: Record<string, HTMLImageElement> = {
    plain: loadAsset(assetUrl("/assets/wilderness/terrain-plain.png")),
    forest: loadAsset(assetUrl("/assets/wilderness/terrain-forest.png")),
    foothills: loadAsset(assetUrl("/assets/wilderness/terrain-foothills.png")),
    mountain: loadAsset(assetUrl("/assets/wilderness/terrain-mountain.png")),
    desert: loadAsset(assetUrl("/assets/wilderness/terrain-desert.png")),
    swamp: loadAsset(assetUrl("/assets/wilderness/terrain-swamp.png")),
    water: loadAsset(assetUrl("/assets/wilderness/terrain-water.png")),
    town: loadAsset(assetUrl("/assets/wilderness/terrain-town.png")),
    hut: loadAsset(assetUrl("/assets/wilderness/terrain-hut.png")),
    castle: loadAsset(assetUrl("/assets/wilderness/terrain-castle.png")),
    road: loadAsset(assetUrl("/assets/wilderness/terrain-road.png")),
    void: loadAsset(assetUrl("/assets/wilderness/terrain-void.png"))
};
const terrainTransitionAssets: Record<string, TerrainTransitionAsset> = {
    forest: {
        edge: loadAsset(assetUrl("/assets/wilderness/transitions/edge-forest.png")),
        corner: loadAsset(assetUrl("/assets/wilderness/transitions/corner-forest.png"))
    },
    swamp: {
        edge: loadAsset(assetUrl("/assets/wilderness/transitions/edge-swamp.png")),
        corner: loadAsset(assetUrl("/assets/wilderness/transitions/corner-swamp.png"))
    },
    desert: {
        edge: loadAsset(assetUrl("/assets/wilderness/transitions/edge-desert.png")),
        corner: loadAsset(assetUrl("/assets/wilderness/transitions/corner-desert.png"))
    },
    foothills: {
        edge: loadAsset(assetUrl("/assets/wilderness/transitions/edge-foothills.png")),
        corner: loadAsset(assetUrl("/assets/wilderness/transitions/corner-foothills.png"))
    },
    mountain: {
        edge: loadAsset(assetUrl("/assets/wilderness/transitions/edge-mountain.png")),
        corner: loadAsset(assetUrl("/assets/wilderness/transitions/corner-mountain.png"))
    },
    water: {
        edge: loadAsset(assetUrl("/assets/wilderness/transitions/edge-water.png")),
        corner: loadAsset(assetUrl("/assets/wilderness/transitions/corner-water.png"))
    }
};
const terrainTransitionPriority: Record<string, number> = {
    void: -1,
    plain: 0,
    road: 1,
    foothills: 2,
    desert: 3,
    forest: 4,
    swamp: 5,
    mountain: 6,
    water: 7
};

function storedBoolean(key: string, fallback: boolean): boolean {
    const value = window.localStorage.getItem(key);
    if (value === null) return fallback;
    return value === "true";
}

function storedWildernessViewMode(): "2d" | "3d" {
    return window.localStorage.getItem(wildernessViewModeStorageKey) === "3d" ? "3d" : "2d";
}

function wildernessViewMode(): "2d" | "3d" {
    return wildernessDisplaySettings.perspective ? "3d" : "2d";
}

function syncWildernessViewControls(): void {
    const mode = wildernessViewMode();
    wildernessViewModeSelect.value = mode;
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
    bitmapLineSpacing.value = String(textDisplaySettings.bitmapLineSpacing);
    bitmapFontScale.disabled = !textDisplaySettings.bitmapFont;
    bitmapLineSpacing.disabled = false;
    updateBitmapFontScaleValue();
    updateBitmapLineSpacingValue();
    bitmapFontToggle.addEventListener("change", () => {
        textDisplaySettings.bitmapFont = bitmapFontToggle.checked;
        window.localStorage.setItem(bitmapFontEnabledKey, String(textDisplaySettings.bitmapFont));
        bitmapFontScale.disabled = !textDisplaySettings.bitmapFont;
        bitmapLineSpacing.disabled = false;
        if (frame) draw(frame);
    });
    bitmapFontScale.addEventListener("input", () => {
        textDisplaySettings.bitmapFontScale = storedNumberFromInput(bitmapFontScale, defaultBitmapFontScale, minBitmapFontScale, maxBitmapFontScale);
        window.localStorage.setItem(bitmapFontScaleKey, String(textDisplaySettings.bitmapFontScale));
        updateBitmapFontScaleValue();
        if (frame) draw(frame);
    });
    bitmapLineSpacing.addEventListener("input", () => {
        textDisplaySettings.bitmapLineSpacing = storedNumberFromInput(
            bitmapLineSpacing,
            defaultBitmapLineSpacing,
            minBitmapLineSpacing,
            maxBitmapLineSpacing
        );
        window.localStorage.setItem(bitmapLineSpacingKey, String(textDisplaySettings.bitmapLineSpacing));
        updateBitmapLineSpacingValue();
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

function configureSoundControls(): void {
    soundToggle.checked = storedBoolean(soundEnabledKey, true);
    soundVolume.value = String(storedNumber(soundVolumeKey, defaultSoundVolume, 0, 100));
    soundVolume.disabled = !soundToggle.checked;
    updateSoundVolumeValue();
    soundToggle.addEventListener("change", () => {
        window.localStorage.setItem(soundEnabledKey, String(soundToggle.checked));
        soundVolume.disabled = !soundToggle.checked;
        gameAudio.unlock();
        gameAudio.setEnabled(soundToggle.checked);
    });
    soundVolume.addEventListener("input", () => {
        const volume = storedNumberFromInput(soundVolume, defaultSoundVolume, 0, 100);
        window.localStorage.setItem(soundVolumeKey, String(volume));
        updateSoundVolumeValue();
        gameAudio.setVolume(volume);
    });
}

function updateSoundVolumeValue(): void {
    soundVolumeValue.textContent = soundVolume.value + "%";
}

function configureWildernessDisplayControls(): void {
    syncWildernessViewControls();
    wildernessViewModeSelect.addEventListener("change", () => {
        setWildernessPerspective(wildernessViewModeSelect.value === "3d");
    });
}

function updateTextRevealSpeedValue(): void {
    textRevealSpeedValue.textContent = Math.round(textRevealSettings.charsPerSecond).toString() + " cps";
}

function updateBitmapFontScaleValue(): void {
    bitmapFontScaleValue.textContent = textDisplaySettings.bitmapFontScale.toFixed(2) + "x";
}

function updateBitmapLineSpacingValue(): void {
    bitmapLineSpacingValue.textContent = textDisplaySettings.bitmapLineSpacing.toFixed(2) + "x";
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
    x: number | null;
    y: number | null;
    textLineCount: number;
    statusLineCount: number;
    commandCount: number;
    mapCells: number;
    centerSettlementSceneSuppressed: boolean;
    wildernessFacing: WildernessFacing;
    wildernessViewMode: string;
    perspectiveCells: number;
    terrainTransitionAssetsReady: boolean;
    compiledImageAssets: BitmapSmokeAsset[];
    audioSoundUrls: string[];
    audioMusicUrls: string[];
};

type BitmapSmokeWindow = Window & {
    __rylweenBitmapSmoke?: () => BitmapSmokeSnapshot;
    __rylweenBitmapSetWildernessView?: (mode: string, facing?: WildernessFacing) => BitmapSmokeSnapshot;
};

function bitmapSmokeSnapshot(): BitmapSmokeSnapshot {
    return {
        mode: frame?.mode ?? null,
        backdrop: frame?.backdrop ?? null,
        title: frame?.title ?? null,
        x: frame?.status?.x ?? null,
        y: frame?.status?.y ?? null,
        textLineCount: frame?.textLines?.length ?? 0,
        statusLineCount: frame?.statusLines?.length ?? 0,
        commandCount: currentCommands.length,
        mapCells: (frame?.map?.rows || []).reduce((count, row) => count + row.length, 0),
        centerSettlementSceneSuppressed:
            Boolean(frame?.map && centerSettlementSceneSuppressedKey === mapCenterSettlementSceneKey(frame.map)),
        wildernessFacing: wildernessDisplaySettings.facing,
        wildernessViewMode: wildernessDisplaySettings.perspective ? "3d" : "2d",
        perspectiveCells: perspectiveView(frame?.map)?.cells.length ?? 0,
        terrainTransitionAssetsReady: terrainTransitionAssetsReady(),
        compiledImageAssets: Array.from(compiledBitmapAssets.values()).map((asset) => ({
            id: asset.id,
            resource: asset.resource || "",
            placement: asset.placement || "",
            transition: asset.transition || ""
        })),
        audioSoundUrls: gameAudio.soundUrlList(),
        audioMusicUrls: gameAudio.musicUrlList()
    };
}

(window as BitmapSmokeWindow).__rylweenBitmapSmoke = bitmapSmokeSnapshot;
(window as BitmapSmokeWindow).__rylweenBitmapSetWildernessView = (mode: string, facing?: WildernessFacing): BitmapSmokeSnapshot => {
    setWildernessPerspective(mode === "3d");
    if (facing) wildernessDisplaySettings.facing = facing;
    if (frame) draw(frame);
    return bitmapSmokeSnapshot();
};

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
        const message = JSON.parse(event.data) as {type?: string};
        if (message.type === "audio") {
            gameAudio.setCatalog(message as unknown as AudioCatalogMessage);
            return;
        }
        if (message.type === "sound") {
            gameAudio.playSound((message as unknown as SoundMessage).id);
            return;
        }
        if (message.type === "music") {
            gameAudio.setMusic((message as unknown as MusicMessage).id);
            return;
        }
        const nextFrame = message as unknown as BitmapFrame;
        gameAudio.setMusic(nextFrame.music);
        syncMenuHighlight(nextFrame);
        recordCompiledImageAssets(nextFrame.imageAssets || []);
        const previousFrame = frame;
        applyServerWildernessFacing(nextFrame);
        applyPendingWildernessFacing(nextFrame);
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
        updateCenterSettlementSceneSuppression(previousFrame, nextFrame);
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
    gameTitle.textContent = "Adventurers of Rylween (v" + status.version + ")";
    userAccount.textContent = user;
    userAccount.title = "Logged in as " + user;
    serverInfoPopover.innerHTML =
        "<h2>Status</h2>" +
        "<dl>" +
        "<dt>Runtime</dt><dd>" + escapeHtml(runtimeName) + "</dd>" +
        "<dt>Version</dt><dd>" + escapeHtml(status.version) + "</dd>" +
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
    if (candidate.map) return "map:" + (candidate.backdrop || "wilderness") + ":" + mapCenterVisualKey(candidate.map);
    return "scene:" + (candidate.mode || "") + ":" + (candidate.backdrop || "");
}

function mapCenterVisualKey(map: BitmapMap): string {
    const center = mapCenterTile(map);
    return center ? perspectiveTerrainKey(center) : "terrain";
}

/** Finds the party's current map tile in a viewport supplied by the runtime. */
function mapCenterTile(map: BitmapMap): BitmapTile | null {
    const rows = map.rows || [];
    for (const row of rows) {
        const center = row.find((tile) => tile.center);
        if (center) return center;
    }
    return null;
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

window.addEventListener("pointerdown", () => gameAudio.unlock());

window.addEventListener("keydown", (event: KeyboardEvent) => {
    gameAudio.unlock();
    if (event.key === "Escape" && closeOpenPopover()) {
        gameAudio.playCue("MenuBack");
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
    if (!(frame && frame.textInput) && handleMenuHighlightKey(event)) {
        event.preventDefault();
        return;
    }
    if (!(frame && frame.textInput) && handleWildernessDisplayShortcut(event)) {
        event.preventDefault();
        return;
    }
    const token = keyToken(event);
    if (!token) return;
    event.preventDefault();
    trackFacingFromMovementToken(token);
    send(token);
});

window.addEventListener("pointerdown", (event: PointerEvent) => {
    if (isPopoverControlTarget(event.target)) return;
    closeOpenPopover();
});

canvas.addEventListener("mousemove", (event: MouseEvent) => {
    const point = canvasPoint(event);
    const hit = statusHotspots.find((hotspot) => boundsContain(hotspot, point));
    if (hit && hit.member) showMemberTooltip(hit.member, event);
    else if (hit) showCanvasTooltip(hit.text, event);
    else hideCanvasTooltip();
    updatePointer(point);
});

canvas.addEventListener("mouseleave", () => {
    hideCanvasTooltip();
    updatePointer(null);
});

// Clicking a choice sends its key, so menus, inventory pickers, and paging
// prompts can be played with the mouse. Where the only thing to do is press
// RETURN (the title screen, "(Smash RETURN key)" pauses), any click does it.
canvas.addEventListener("click", (event: MouseEvent) => {
    const target = choiceTargetAt(canvasPoint(event));
    const token = target?.token ?? anywhereClickToken();
    if (!token) return;
    event.preventDefault();
    if (finishTextReveal()) return;
    if (target) gameAudio.playCue("MenuSelect");
    send(token);
});

function anywhereClickToken(): string | null {
    if (frame && isTitleFrame(frame)) return "ENTER";
    if (choiceTargets.length === 0) return null;
    return choiceTargets.every((target) => target.token === "ENTER") ? "ENTER" : null;
}

function boundsContain(bounds: Bounds, point: CanvasPoint): boolean {
    return point.x >= bounds.x && point.x <= bounds.x + bounds.w && point.y >= bounds.y && point.y <= bounds.y + bounds.h;
}

function choiceTargetAt(point: CanvasPoint): ChoiceTarget | null {
    return choiceTargets.find((target) => boundsContain(target, point)) || null;
}

function choiceTargetKey(target: ChoiceTarget | null): string {
    return target ? target.token + "@" + Math.round(target.x) + "," + Math.round(target.y) : "";
}

// Views where the arrow keys page or do nothing useful, so they never move a menu highlight.
const noMenuHighlightModes = new Set(["boot", "debug_map", "party_page"]);
// Views whose standing text offers keys, such as "(D) Descend", while the arrows
// walk; there only a menu that RETURN leaves takes the arrows.
const crawlModes = new Set(["castle", "dungeon"]);

// The choice keys of a menu the keyboard can step through, in screen order: at
// least two choices, none of which is an arrow key itself. In the wilderness the
// map text offers no keys, so a menu there means the party is not walking.
function menuChoiceTokens(candidate: BitmapFrame): string[] {
    const mode = candidate.mode || "";
    if (candidate.textInput || candidate.document || noMenuHighlightModes.has(mode)) return [];
    const tokens = (candidate.textLines || []).flatMap((line) => choiceSegments(line).map((segment) => segment.token));
    if (tokens.length < 2 || tokens.some((token) => token.startsWith("ARROW_"))) return [];
    if (crawlModes.has(mode) && !tokens.includes("ENTER")) return [];
    return tokens;
}

// A new menu starts with "RETURN leaves." highlighted, so a stray Enter still
// leaves; a menu without one has no highlight until an arrow key is pressed.
function syncMenuHighlight(next: BitmapFrame): void {
    const tokens = menuChoiceTokens(next);
    if (tokens.join("|") === menuHighlightTokens.join("|")) return;
    menuHighlightTokens = tokens;
    menuHighlight = tokens.includes("ENTER") ? "ENTER" : null;
}

function handleMenuHighlightKey(event: KeyboardEvent): boolean {
    if (menuHighlightTokens.length === 0 || event.altKey || event.ctrlKey || event.metaKey) return false;
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        const tokens = menuHighlightTokens;
        const step = event.key === "ArrowDown" ? 1 : -1;
        const current = menuHighlight === null ? (step > 0 ? -1 : tokens.length) : tokens.indexOf(menuHighlight);
        menuHighlight = tokens[(current + step + tokens.length) % tokens.length];
        gameAudio.playCue("MenuMove");
        requestPointerRedraw();
        return true;
    }
    if (event.key === "Enter" && menuHighlight !== null) {
        gameAudio.playCue("MenuSelect");
        send(menuHighlight);
        return true;
    }
    return false;
}

function updatePointer(point: CanvasPoint | null): void {
    const previous = choiceTargetKey(pointerPoint ? choiceTargetAt(pointerPoint) : null);
    pointerPoint = point;
    const next = point ? choiceTargetAt(point) : null;
    // The mouse and the keyboard share one highlight.
    if (next && menuHighlightTokens.includes(next.token)) menuHighlight = next.token;
    canvas.style.cursor = next || (point && anywhereClickToken()) ? "pointer" : "";
    if (choiceTargetKey(next) !== previous) {
        if (next) gameAudio.playCue("MenuMove");
        requestPointerRedraw();
    }
}

function requestPointerRedraw(): void {
    if (pointerRedraw !== 0) return;
    pointerRedraw = requestAnimationFrame(() => {
        pointerRedraw = 0;
        if (frame && !sceneFade && !titleFade) draw(frame);
    });
}

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

function handleWildernessDisplayShortcut(event: KeyboardEvent): boolean {
    if (!canUseWildernessPerspective()) return false;
    if (event.altKey || event.ctrlKey || event.metaKey) return false;
    const control = wildernessCrawlerControlForEvent(event);
    if (!control) return false;
    if (!wildernessDisplaySettings.perspective) return false;
    if (control === "forward") {
        send(movementTokenForFacing(wildernessDisplaySettings.facing));
        return true;
    }
    if (control === "back") {
        send(movementTokenForFacing(oppositeFacing(wildernessDisplaySettings.facing)));
        return true;
    }
    if (control === "turnLeft") {
        rotateWildernessFacing(-1);
        return true;
    }
    if (control === "turnRight") {
        rotateWildernessFacing(1);
        return true;
    }
    return true;
}

function canUseWildernessPerspective(): boolean {
    return Boolean(frame && frame.mode === "wilderness" && frame.map && frame.map.perspective);
}

/** Applies the display-mode preference shared by the titlebar selector. */
function setWildernessPerspective(enabled: boolean): void {
    const changed = wildernessDisplaySettings.perspective !== enabled;
    wildernessDisplaySettings.perspective = enabled;
    window.localStorage.setItem(wildernessViewModeStorageKey, wildernessViewMode());
    syncWildernessViewControls();
    if (!changed) return;
    wildernessRotationTransition = null;
    if (frame) draw(frame);
}

function setWildernessFacing(facing: WildernessFacing, direction = rotationDirection(wildernessDisplaySettings.facing, facing)): void {
    const previous = wildernessDisplaySettings.facing;
    if (previous === facing) return;
    if (wildernessDisplaySettings.perspective && frame?.mode === "wilderness") {
        wildernessRotationTransition = {
            from: previous,
            to: facing,
            direction,
            started: performance.now(),
            duration: 180
        };
        scheduleWildernessRotationAnimation();
    }
    wildernessDisplaySettings.facing = facing;
    if (frame) draw(frame);
}

function rotateWildernessFacing(direction: number): void {
    setWildernessFacing(rotatedFacing(wildernessDisplaySettings.facing, direction), direction);
}

/** Records the intended facing for a movement key until the runtime confirms a move. */
function trackFacingFromMovementToken(token: string): void {
    if (!canUseWildernessPerspective()) return;
    const facing = facingForToken(token);
    const status = frame?.status;
    if (facing && status) pendingWildernessMove = {facing, x: status.x, y: status.y};
}

/** Initializes the browser-facing 3D compass from the runtime save once per connection. */
function applyServerWildernessFacing(nextFrame: BitmapFrame): void {
    if (serverFacingInitialized || nextFrame.mode !== "wilderness") return;
    const facing = nextFrame.status?.facing;
    serverFacingInitialized = true;
    if (facing) wildernessDisplaySettings.facing = facing;
}

/** Applies facing after the runtime confirms that a wilderness movement changed position. */
function applyPendingWildernessFacing(nextFrame: BitmapFrame): void {
    const pending = pendingWildernessMove;
    if (!pending || nextFrame.mode !== "wilderness" || !nextFrame.status) return;
    pendingWildernessMove = null;
    if (nextFrame.status.x === pending.x && nextFrame.status.y === pending.y) return;
    wildernessRotationTransition = null;
    wildernessDisplaySettings.facing = pending.facing;
}

/**
 * After leaving a settlement interior, keep the next wilderness frame in the
 * normal outdoor 3D view until the party moves away from that settlement tile.
 */
function updateCenterSettlementSceneSuppression(previousFrame: BitmapFrame | null, nextFrame: BitmapFrame): void {
    const nextKey = nextFrame.map ? mapCenterSettlementSceneKey(nextFrame.map) : null;
    if (!nextKey) {
        centerSettlementSceneSuppressedKey = null;
        return;
    }
    if (nextFrame.mode === "wilderness" && nextFrame.map && isSettlementSceneFrame(previousFrame)) {
        centerSettlementSceneSuppressedKey = nextKey;
        return;
    }
    if (centerSettlementSceneSuppressedKey && centerSettlementSceneSuppressedKey !== nextKey) {
        centerSettlementSceneSuppressedKey = null;
    }
}

/** Recognizes full-panel settlement art that should fade out to wilderness on leave. */
function isSettlementSceneFrame(candidate: BitmapFrame | null): boolean {
    if (!candidate || candidate.map) return false;
    const kind = normalizedKind(candidate.backdrop || candidate.mode || "");
    return (
        kind.includes("townemainstreet") ||
        kind.includes("townmainstreet") ||
        kind.includes("wildernesshut") ||
        kind.includes("ydargcastle")
    );
}

function scheduleWildernessRotationAnimation(): void {
    if (wildernessRotationAnimation !== 0) return;
    wildernessRotationAnimation = requestAnimationFrame(() => {
        wildernessRotationAnimation = 0;
        if (!wildernessRotationTransition || !frame) return;
        draw(frame);
        if (wildernessRotationTransition) scheduleWildernessRotationAnimation();
    });
}

function rotatedFacing(facing: WildernessFacing, direction: number): WildernessFacing {
    const order: WildernessFacing[] = ["North", "East", "South", "West"];
    const index = order.indexOf(facing);
    return order[(index + direction + order.length) % order.length];
}

function oppositeFacing(facing: WildernessFacing): WildernessFacing {
    return rotatedFacing(facing, 2);
}

function rotationDirection(from: WildernessFacing, to: WildernessFacing): number {
    const order: WildernessFacing[] = ["North", "East", "South", "West"];
    const delta = order.indexOf(to) - order.indexOf(from);
    if (delta === 1 || delta === -3) return 1;
    if (delta === -1 || delta === 3) return -1;
    return 1;
}

function movementTokenForFacing(facing: WildernessFacing): string {
    switch (facing) {
        case "North":
            return "ARROW_UP";
        case "East":
            return "ARROW_RIGHT";
        case "South":
            return "ARROW_DOWN";
        case "West":
            return "ARROW_LEFT";
    }
}

/** Converts 3D wilderness navigation keys while reserving top-row 2/3 for view mode. */
function wildernessCrawlerControlForEvent(event: KeyboardEvent): "forward" | "back" | "turnLeft" | "turnRight" | null {
    const key = event.key;
    if (key === "ArrowUp" || key === "8" || event.code === "Numpad8") return "forward";
    if (key === "ArrowDown" || event.code === "Numpad2") return "back";
    if (key === "ArrowLeft" || key === "4" || event.code === "Numpad4") return "turnLeft";
    if (key === "ArrowRight" || key === "6" || event.code === "Numpad6") return "turnRight";
    return null;
}

function facingForToken(token: string): WildernessFacing | null {
    if (token === "ARROW_UP" || token === "8") return "North";
    if (token === "ARROW_RIGHT" || token === "6") return "East";
    if (token === "ARROW_DOWN" || token === "2") return "South";
    if (token === "ARROW_LEFT" || token === "4") return "West";
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
    canvasTooltip.classList.remove("member-tooltip");
    canvasTooltip.textContent = text;
    placeCanvasTooltip(event);
}

// The character sheet shown when hovering a name in the status bar: the same
// details as the party page, laid out as a compact card.
function showMemberTooltip(member: BitmapPartyMemberPage, event: MouseEvent): void {
    const stat = (label: string, value: string | number | undefined): string =>
        "<dt>" + escapeHtml(label) + "</dt><dd>" + escapeHtml(String(value ?? "-")) + "</dd>";
    const dead = (member.status || "").toLowerCase() === "dead";
    const identity = [member.gender, member.race, member.pcClass].filter(Boolean).join(" ");
    const stats = [
        stat("Health", (member.hitPoints ?? 0) + "/" + (member.hitPointMax ?? 0)),
        (member.manaMax || 0) > 0 ? stat("Mana", (member.mana ?? 0) + "/" + member.manaMax) : stat("Mana", "-"),
        stat("Level", member.level),
        stat("XP", (member.experience ?? 0).toLocaleString("en-US")),
        stat("Weapon skill", member.weaponSkill),
        stat("Body health", member.health),
        stat("Wisdom", member.wisdom),
        stat("Charisma", member.charisma),
        stat("Dexterity", member.dexterity)
    ];
    canvasTooltip.classList.add("member-tooltip");
    canvasTooltip.innerHTML =
        "<div class=\"member-card-name\">" + escapeHtml(member.name || "") +
        (dead ? " <span class=\"member-card-dead\">Dead</span>" : "") + "</div>" +
        "<div class=\"member-card-identity\">" + escapeHtml(identity) + "</div>" +
        "<dl class=\"member-card-stats\">" + stats.join("") + "</dl>" +
        "<dl class=\"member-card-gear\">" + stat("Weapon", member.weapon) + stat("Armor", member.armor) + "</dl>";
    placeCanvasTooltip(event);
}

function placeCanvasTooltip(event: MouseEvent): void {
    const stage = canvas.parentElement?.getBoundingClientRect();
    if (!stage) return;
    canvasTooltip.hidden = false;
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
    choiceTargets = [];
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
    const textW = panelW - 58;
    const rowGap = 10;
    const wrapped = lines.map((line, index) => {
        const isHeader = index === 0;
        ctx.font = isHeader ? "700 25px Palatino, 'Palatino Linotype', Georgia, serif" : "17px Menlo, Monaco, Consolas, monospace";
        return wrapCanvasLines(line, textW);
    });
    const panelH = Math.max(
        246,
        wrapped.reduce((height, row, index) => height + row.length * (index === 0 ? 30 : 22) + rowGap, 48)
    );
    const x = Math.floor((canvas.width - panelW) / 2);
    const y = 214;
    ctx.save();
    ctx.fillStyle = "rgba(6, 8, 6, 0.72)";
    ctx.fillRect(x, y, panelW, panelH);
    ctx.strokeStyle = "rgba(215, 191, 115, 0.52)";
    ctx.lineWidth = 1;
    ctx.strokeRect(x + 0.5, y + 0.5, panelW - 1, panelH - 1);
    ctx.textAlign = "center";
    let textY = y + 58;
    lines.forEach((line, index) => {
        const isHeader = index === 0;
        const isDisabled = line.toLowerCase().includes("no save");
        ctx.font = isHeader ? "700 25px Palatino, 'Palatino Linotype', Georgia, serif" : "17px Menlo, Monaco, Consolas, monospace";
        ctx.fillStyle = isHeader ? "#ead68a" : isDisabled ? "rgba(174, 166, 144, 0.55)" : "#f0ead4";
        const lineHeight = isHeader ? 30 : 22;
        if (!isHeader) {
            const segments = choiceSegments(line);
            if (segments.length === 1) {
                addChoiceTarget({x: x + 16, y: textY - 17, w: panelW - 32, h: lineHeight * wrapped[index].length, token: segments[0].token});
            }
        }
        for (const part of wrapped[index]) {
            ctx.fillText(part, canvas.width / 2, textY);
            textY += lineHeight;
        }
        textY += rowGap;
    });
    if (frame.subtitle) {
        ctx.textAlign = "right";
        ctx.font = "15px Menlo, Monaco, Consolas, monospace";
        ctx.fillStyle = "rgba(188, 170, 118, 0.72)";
        ctx.fillText(frame.subtitle, canvas.width - 42, canvas.height - 42);
    }
    ctx.restore();
}

function drawStaticDocument(frame: BitmapFrame): void {
    drawCoverImage(bitmapAssets.town, "#10130d");
    drawImageShade("#020503", 0.55);
    const x = 58;
    const y = 40;
    const w = canvas.width - x * 2;
    const h = canvas.height - 78;
    const textX = x + 38;
    const textW = w - 76;
    ctx.save();
    ctx.fillStyle = "rgba(4, 7, 5, 0.86)";
    ctx.fillRect(x, y, w, h);
    ctx.strokeStyle = "rgba(218, 189, 107, 0.56)";
    ctx.lineWidth = 1;
    ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
    ctx.textAlign = "right";
    ctx.font = "14px " + documentFontFamily;
    ctx.fillStyle = "rgba(218, 201, 150, 0.78)";
    ctx.fillText(truncateToWidth(frame.subtitle || "", 220), textX + textW, y + 42);
    ctx.textAlign = "left";
    ctx.font = "700 24px " + documentFontFamily;
    ctx.fillStyle = "#ead68a";
    ctx.fillText(truncateToWidth(frame.title || "", textW - 240), textX, y + 44);
    ctx.fillStyle = "rgba(218, 189, 107, 0.32)";
    ctx.fillRect(textX, y + 58, textW, 1);
    const page = frame.document;
    if (page) {
        const reveal = textRevealDrawState(frame);
        drawDocumentPage(page, textX, y + 72, textW, h - 124, reveal);
        drawTextRevealCursor(reveal);
        if (page.prompt) {
            ctx.font = "700 15px " + documentFontFamily;
            const promptWidth = ctx.measureText(page.prompt).width;
            const promptX = textX + (textW - promptWidth) / 2;
            registerChoiceTargets(page.prompt, promptX, y + h - 40, promptWidth, 26, (value) => ctx.measureText(value).width);
            ctx.font = "700 15px " + documentFontFamily;
            ctx.fillStyle = "#d7bc6c";
            ctx.fillText(page.prompt, promptX, y + h - 22);
        }
    }
    ctx.restore();
}

// Document pages arrive laid out on a fixed character grid (columns x rows), so
// the font is sized to fit the grid and every line, including ASCII art and
// tables, is drawn in one monospace face.
function drawDocumentPage(page: BitmapDocument, x: number, top: number, width: number, height: number, reveal: TextRevealDrawState): void {
    const rows = Math.max(1, page.rows || 1);
    const lineHeight = Math.floor(height / rows);
    const fontSize = documentFontSize(width, Math.max(1, page.columns || 1), lineHeight);
    const regular = fontSize + "px " + documentFontFamily;
    const bold = "700 " + regular;
    ctx.font = regular;
    const cellWidth = ctx.measureText("M").width;
    const baseline = Math.round(lineHeight * 0.72);
    const lines = (page.lines || []).slice(0, rows);
    lines.forEach((line, index) => {
        if (line.kind !== "Code") return;
        const rowTop = top + index * lineHeight;
        ctx.fillStyle = "rgba(22, 28, 19, 0.82)";
        ctx.fillRect(x - 14, rowTop, width + 28, lineHeight);
        ctx.fillStyle = "rgba(218, 189, 107, 0.34)";
        ctx.fillRect(x - 14, rowTop, 2, lineHeight);
    });
    lines.forEach((line, index) => {
        const rowTop = top + index * lineHeight;
        const textY = rowTop + baseline;
        const text = line.text || "";
        switch (line.kind) {
            case "Heading1":
            case "Heading2": {
                // Section headings are drawn larger than the grid font; the blank
                // lines the layout keeps around them leave room for the taller glyphs.
                const major = line.kind === "Heading1";
                ctx.font = documentHeadingFont(text, Math.round(fontSize * (major ? 1.35 : 1.2)), width);
                drawRevealText(text, x, textY, major ? "#f4dc84" : "#e8c96d", "rgba(232, 201, 109, 0.25)", reveal);
                if (lines[index + 1]?.kind !== line.kind) {
                    ctx.fillStyle = major ? "rgba(232, 201, 109, 0.62)" : "rgba(232, 201, 109, 0.4)";
                    ctx.fillRect(x, textY + Math.max(4, Math.round(lineHeight * 0.2)), width, 1);
                }
                break;
            }
            case "Heading3":
                ctx.font = bold;
                drawRevealText(text, x, textY, "#d9be74", "rgba(217, 190, 116, 0.25)", reveal);
                break;
            case "Quote":
                ctx.fillStyle = "rgba(218, 189, 107, 0.5)";
                ctx.fillRect(x + Math.round(cellWidth * 0.4), rowTop, 2, lineHeight);
                ctx.font = "italic " + regular;
                drawRevealText(text, x + cellWidth * 2, textY, "#d8cba7", "rgba(216, 203, 167, 0.23)", reveal);
                break;
            case "Code":
                ctx.font = regular;
                drawRevealText(text, x, textY, "#d9d3bc", "rgba(217, 211, 188, 0.24)", reveal);
                break;
            default:
                ctx.font = regular;
                drawRevealText(text, x, textY, "#efe4c6", "rgba(239, 228, 198, 0.25)", reveal);
        }
    });
}

function documentHeadingFont(text: string, size: number, width: number): string {
    let fitted = size;
    ctx.font = "700 " + fitted + "px " + documentFontFamily;
    while (fitted > 10 && ctx.measureText(text).width > width) {
        fitted -= 1;
        ctx.font = "700 " + fitted + "px " + documentFontFamily;
    }
    return ctx.font;
}

function documentFontSize(width: number, columns: number, lineHeight: number): number {
    ctx.font = "100px " + documentFontFamily;
    const advance = ctx.measureText("M").width / 100;
    return Math.max(10, Math.min(Math.floor(width / (columns * advance)), Math.floor(lineHeight * 0.8)));
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
    return Math.ceil((fantasyFontHeight * scale + 10) * textDisplaySettings.bitmapLineSpacing);
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
    if (fantasyTextWidth(text, scale) <= maxWidth) return [text];
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
    const top = baseline - fantasyFontHeight * scale - 1;
    const value = String(text || "");
    if (!reveal.active) {
        drawFantasyText(value, x, top, scale, color);
        return;
    }
    const charCost = Math.max(1, value.length);
    const visible = Math.max(0, Math.min(value.length, reveal.remaining));
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
    ctx.textAlign = "center";
    ctx.textBaseline = "alphabetic";
    ctx.shadowColor = "rgba(0, 0, 0, 0.82)";
    ctx.shadowBlur = 12;
    ctx.shadowOffsetY = 4;
    ctx.lineJoin = "round";
    ctx.lineWidth = 5;
    ctx.strokeStyle = "rgba(23, 8, 4, 0.95)";
    ctx.fillStyle = "#f0d07a";
    ctx.font = "700 58px Palatino, Georgia, serif";
    ctx.strokeText("ADVENTURERS", cx, y - 16);
    ctx.fillText("ADVENTURERS", cx, y - 16);
    ctx.lineWidth = 4;
    ctx.fillStyle = "#d9b764";
    ctx.font = "700 42px Palatino, Georgia, serif";
    ctx.strokeText("OF RYLWEEN", cx, y + 44);
    ctx.fillText("OF RYLWEEN", cx, y + 44);
    ctx.shadowColor = "transparent";
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
    if (wildernessDisplaySettings.perspective && drawPerspectiveMap(map)) return;
    const rows = map.rows || [];
    const cols = rows[0] ? rows[0].length : 0;
    if (rows.length === 0 || cols === 0) return;
    const transitionLayer = shouldDrawTerrainTransitions(rows);
    const panelBounds = layout.map;
    const configured = map.tileSize || 64;
    const size = Math.min(configured, Math.floor((panelBounds.w - 8) / cols), Math.floor((panelBounds.h - 52) / rows.length));
    const mapW = cols * size;
    const mapH = rows.length * size;
    const x = panelBounds.x + Math.floor((panelBounds.w - mapW) / 2);
    const y = panelBounds.y + Math.floor((panelBounds.h - mapH) / 2);
    for (let row = 0; row < rows.length; row += 1) {
        for (let col = 0; col < rows[row].length; col += 1) {
            drawTile(rows[row][col], x + col * size, y + row * size, size, !transitionLayer);
        }
    }
    if (!transitionLayer) return;
    drawTerrainTransitions(rows, x, y, size);
    drawWildernessMapMarkers(rows, x, y, size);
}

function drawPerspectiveMap(map: BitmapMap): boolean {
    const view = perspectiveView(map);
    if (!view || !map.perspective) return false;
    const bounds = scenePanelBounds();
    const theme = wildernessBackdropThemeForStatus(frame?.status);
    ctx.save();
    ctx.beginPath();
    ctx.rect(bounds.x, bounds.y, bounds.w, bounds.h);
    ctx.clip();
    if (drawPerspectiveCenterSettlementScene(map, bounds, theme)) {
        ctx.restore();
        return true;
    }
    drawPerspectiveBackdrop(bounds, theme, view);
    drawPerspectiveSettlementBackdrop(view, bounds);
    const transition = activeWildernessRotationTransition();
    if (transition) {
        const fromView = perspectiveViewFor(map, transition.from);
        const toView = perspectiveViewFor(map, transition.to);
        if (fromView && toView) {
            const progress = rotationProgress(transition);
            const slide = bounds.w * 0.16 * transition.direction;
            drawPerspectiveViewCells(fromView, bounds, 1 - progress * 0.65, -slide * progress);
            drawPerspectiveViewCells(toView, bounds, 0.45 + progress * 0.55, slide * (1 - progress));
        } else {
            drawPerspectiveViewCells(view, bounds);
        }
    } else {
        drawPerspectiveViewCells(view, bounds);
    }
    drawPerspectiveLighting(bounds, theme);
    drawSceneVignette(bounds);
    ctx.restore();
    drawPerspectiveFacingLabel(bounds, view.facing);
    return true;
}

/**
 * Replaces the 3D wilderness view with the authored settlement scene when the
 * party stands on a town, hut, or castle square but has not entered it yet.
 */
function drawPerspectiveCenterSettlementScene(map: BitmapMap, bounds: Bounds, theme: WildernessBackdropTheme): boolean {
    const key = mapCenterSettlementSceneKey(map);
    if (!key || key === centerSettlementSceneSuppressedKey) return false;
    const kind = mapCenterSettlementBackdropKind(map);
    if (!kind) return false;
    ctx.fillStyle = "#070a06";
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
    drawSceneBackdropForKind(kind, bounds);
    drawSettlementSceneTimeShade(bounds, theme);
    drawSceneVignette(bounds);
    drawPerspectiveFacingLabel(bounds, wildernessDisplaySettings.facing);
    return true;
}

/** Resolves the settlement under the party into the scene key used by bitmap backdrops. */
function mapCenterSettlementBackdropKind(map: BitmapMap): string | null {
    const center = mapCenterTile(map);
    return center ? perspectiveSettlementBackdropKind(perspectiveTerrainKey(center)) : null;
}

/** Keys the settlement tile under the party so suppression lasts only until the party moves away. */
function mapCenterSettlementSceneKey(map: BitmapMap): string | null {
    const center = mapCenterTile(map);
    if (!center) return null;
    const kind = perspectiveSettlementBackdropKind(perspectiveTerrainKey(center));
    return kind ? center.x + "," + center.y + ":" + kind : null;
}

/** Applies the current wilderness time-of-day lighting to settlement scenes. */
function drawSettlementSceneTimeShade(bounds: Bounds, theme: WildernessBackdropTheme): void {
    const nightAlpha = Math.max(0, Math.min(0.42, theme.lightAlpha * 0.7));
    if (nightAlpha <= 0) return;
    ctx.save();
    ctx.fillStyle = "rgba(4, 9, 18, " + nightAlpha.toFixed(2) + ")";
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
    ctx.restore();
}

function perspectiveView(map: BitmapMap | null | undefined): BitmapPerspectiveView | null {
    return perspectiveViewFor(map, wildernessDisplaySettings.facing);
}

function perspectiveViewFor(map: BitmapMap | null | undefined, facing: WildernessFacing): BitmapPerspectiveView | null {
    const views = map?.perspective?.views || [];
    return views.find((view) => view.facing === facing) || views[0] || null;
}

function activeWildernessRotationTransition(): WildernessRotationTransition | null {
    if (!wildernessRotationTransition) return null;
    if (performance.now() - wildernessRotationTransition.started >= wildernessRotationTransition.duration) {
        wildernessRotationTransition = null;
        return null;
    }
    return wildernessRotationTransition;
}

function rotationProgress(transition: WildernessRotationTransition): number {
    return Math.max(0, Math.min(1, (performance.now() - transition.started) / transition.duration));
}

function perspectiveSceneTerrainKey(view: BitmapPerspectiveView): string {
    const center = view.cells.find((cell) => cell.forward === 0 && cell.side === 0);
    const ahead = view.cells.find((cell) => cell.forward === 1 && cell.side === 0);
    const candidate = ahead || center || view.cells[0];
    return candidate ? perspectiveTerrainKey(candidate.tile) : "plain";
}

function drawPerspectiveViewCells(view: BitmapPerspectiveView, bounds: Bounds, alpha = 1, offsetX = 0): void {
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.translate(offsetX, 0);
    ctx.beginPath();
    ctx.rect(bounds.x, bounds.y, bounds.w, bounds.h);
    ctx.clip();
    drawPerspectiveRowBackfill(view, bounds);
    for (const cell of view.cells) {
        drawPerspectiveGroundCell(cell, bounds);
    }
    for (const cell of view.cells) {
        drawPerspectiveCellContent(cell, bounds);
    }
    ctx.restore();
}

function drawPerspectiveBackdrop(bounds: Bounds, theme: WildernessBackdropTheme, view: BitmapPerspectiveView): void {
    const base = ctx.createLinearGradient(0, bounds.y, 0, bounds.y + bounds.h);
    base.addColorStop(0, theme.skyTop);
    base.addColorStop(0.35, theme.skyMid);
    base.addColorStop(0.62, theme.skyLow);
    base.addColorStop(1, "#11170f");
    ctx.fillStyle = base;
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
    drawPerspectiveDistantLandscape(bounds, theme, perspectiveSceneTerrainKey(view));
}

function drawPerspectiveDistantLandscape(bounds: Bounds, theme: WildernessBackdropTheme, terrainKey: string): void {
    ctx.save();
    const glow = ctx.createRadialGradient(
        bounds.x + bounds.w * 0.74,
        bounds.y + bounds.h * 0.16,
        bounds.w * 0.05,
        bounds.x + bounds.w * 0.72,
        bounds.y + bounds.h * 0.18,
        bounds.w * 0.36,
    );
    glow.addColorStop(0, theme.glow);
    glow.addColorStop(1, "rgba(0, 0, 0, 0)");
    ctx.fillStyle = glow;
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h * 0.55);
    drawPerspectiveBackdropPngs(bounds, terrainKey);
    const haze = ctx.createLinearGradient(0, bounds.y + bounds.h * 0.25, 0, bounds.y + bounds.h * 0.72);
    haze.addColorStop(0, "rgba(0, 0, 0, 0)");
    haze.addColorStop(0.45, theme.haze);
    haze.addColorStop(1, "rgba(5, 8, 4, 0.24)");
    ctx.fillStyle = haze;
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h * 0.76);
    ctx.restore();
}

function drawPerspectiveBackdropPngs(bounds: Bounds, terrainKey: string): void {
    const key = perspectiveBackdropKey(terrainKey);
    const image = perspectiveTerrainAssets[key] || perspectiveTerrainAssets.plain;
    const ground = perspectiveTerrainAssets[perspectiveGroundBackdropKey(key)] || image;
    if (!imageReady(image) || !imageReady(ground)) {
        drawPerspectiveFallbackBackdrop(bounds, key);
        return;
    }
    const horizon = bounds.y + bounds.h * perspectiveBackdropHorizon(key);
    drawPerspectiveGroundRaster(ground, key, bounds, horizon);
    drawPerspectiveRepeatedRaster(image, bounds, perspectiveBackdropBaseY(key), perspectiveBackdropWidth(key), 0.78);
    if (key === "forest" || key === "swamp") {
        drawPerspectiveSideRaster(perspectiveTerrainAssets.forest, bounds, 0.7);
    } else if (key !== "desert" && key !== "water") {
        drawPerspectiveSideRaster(perspectiveTerrainAssets.plain, bounds, 0.52);
    }
}

function perspectiveBackdropKey(key: string): string {
    if (key === "void") return "plain";
    if (key === "road") return "plain";
    return key;
}

function perspectiveGroundBackdropKey(key: string): string {
    if (key === "mountain") return "foothills";
    if (key === "forest") return "plain";
    if (key === "void") return "plain";
    return key;
}

function perspectiveBackdropHorizon(key: string): number {
    if (key === "mountain" || key === "foothills") return 0.3;
    if (key === "forest" || key === "swamp") return 0.36;
    return 0.44;
}

function perspectiveBackdropBaseY(key: string): number {
    if (key === "mountain" || key === "foothills") return 0.68;
    if (key === "forest" || key === "swamp") return 0.76;
    return 0.88;
}

function perspectiveBackdropWidth(key: string): number {
    if (key === "mountain" || key === "foothills") return 0.48;
    if (key === "forest" || key === "swamp") return 0.56;
    return 0.52;
}

function drawPerspectiveGroundRaster(image: HTMLImageElement, key: string, bounds: Bounds, horizon: number): void {
    const source = perspectiveTextureSource(image, key);
    const targetH = bounds.y + bounds.h - horizon;
    const targetW = bounds.w * 0.62;
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.globalAlpha = 0.82;
    for (let index = -1; index <= 2; index += 1) {
        const x = bounds.x + index * targetW * 0.72;
        ctx.drawImage(image, source.x, source.y, source.w, source.h, x, horizon, targetW, targetH);
    }
    ctx.restore();
}

function drawPerspectiveRepeatedRaster(
    image: HTMLImageElement,
    bounds: Bounds,
    baseYRatio: number,
    widthRatio: number,
    alpha: number,
): void {
    if (!imageReady(image)) return;
    const targetW = bounds.w * widthRatio;
    const targetH = targetW * (image.naturalHeight / image.naturalWidth);
    const step = targetW * 0.48;
    const count = Math.ceil(bounds.w / step) + 3;
    const baseY = bounds.y + bounds.h * baseYRatio;
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.globalAlpha = alpha;
    for (let index = 0; index < count; index += 1) {
        const x = bounds.x - targetW * 0.38 + index * step;
        const y = baseY - targetH + (index % 2) * bounds.h * 0.018;
        ctx.drawImage(image, x, y, targetW, targetH);
    }
    ctx.restore();
}

function drawPerspectiveSideRaster(image: HTMLImageElement, bounds: Bounds, alpha: number): void {
    if (!imageReady(image)) return;
    const targetW = bounds.w * 0.7;
    const targetH = targetW * (image.naturalHeight / image.naturalWidth);
    const y = bounds.y + bounds.h * 0.84 - targetH;
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.globalAlpha = alpha;
    ctx.drawImage(image, bounds.x - targetW * 0.38, y, targetW, targetH);
    ctx.drawImage(image, bounds.x + bounds.w - targetW * 0.62, y + bounds.h * 0.02, targetW, targetH);
    ctx.restore();
}

function drawPerspectiveFallbackBackdrop(bounds: Bounds, key: string): void {
    const horizon = bounds.y + bounds.h * perspectiveBackdropHorizon(key);
    const ground = ctx.createLinearGradient(0, horizon, 0, bounds.y + bounds.h);
    ground.addColorStop(0, key === "desert" ? "rgba(143, 112, 62, 0.82)" : "rgba(37, 58, 25, 0.82)");
    ground.addColorStop(1, key === "desert" ? "rgba(79, 58, 34, 0.9)" : "rgba(12, 20, 9, 0.92)");
    ctx.fillStyle = ground;
    ctx.fillRect(bounds.x, horizon, bounds.w, bounds.y + bounds.h - horizon);
}

function imageReady(image: HTMLImageElement | null | undefined): image is HTMLImageElement {
    return !!image && image.complete && image.naturalWidth > 0;
}

function drawPerspectiveLighting(bounds: Bounds, theme: WildernessBackdropTheme): void {
    ctx.save();
    ctx.fillStyle = theme.groundTint;
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
    if (theme.lightAlpha > 0) {
        ctx.fillStyle = "rgba(0, 0, 0, " + theme.lightAlpha.toFixed(2) + ")";
        ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
    }
    ctx.restore();
}

function wildernessBackdropThemeForStatus(status: BitmapStatus | null | undefined): WildernessBackdropTheme {
    const time = (status?.time || "").toLowerCase();
    if (time.includes("midnight") || time.includes("night")) return wildernessBackdropThemes.midnight;
    if (time.includes("twilight") || time.includes("dusk")) return wildernessBackdropThemes.twilight;
    if (time.includes("midday") || time.includes("day")) return wildernessBackdropThemes.midday;
    return wildernessBackdropThemes.dawn;
}

function drawPerspectiveSettlementBackdrop(view: BitmapPerspectiveView, bounds: Bounds): void {
    const center = view.cells.find((cell) => cell.forward === 0 && cell.side === 0);
    const kind = center ? perspectiveSettlementBackdropKind(perspectiveTerrainKey(center.tile)) : null;
    if (!kind) return;
    ctx.save();
    ctx.globalAlpha = 0.32;
    ctx.filter = "saturate(0.82) brightness(0.68)";
    drawSceneBackdropForKind(kind, bounds);
    ctx.restore();
    const veil = ctx.createLinearGradient(0, bounds.y, 0, bounds.y + bounds.h);
    veil.addColorStop(0, "rgba(4, 7, 4, 0.08)");
    veil.addColorStop(0.42, "rgba(6, 10, 5, 0.32)");
    veil.addColorStop(1, "rgba(5, 8, 4, 0.72)");
    ctx.fillStyle = veil;
    ctx.fillRect(bounds.x, bounds.y, bounds.w, bounds.h);
}

function perspectiveSettlementBackdropKind(key: string): string | null {
    if (key === "town") return "towne-mainstreet";
    if (key === "hut") return "wilderness-hut";
    if (key === "castle") return "ydarg-castle";
    return null;
}

function drawSceneBackdropForKind(kind: string, bounds: Bounds): void {
    const image = sceneImageFor(kind);
    if (image) {
        drawCroppedImage(image, bounds.x, bounds.y, bounds.w, bounds.h);
        return;
    }
    if (drawSceneAtlasTile(kind, bounds)) return;
    drawSemanticSceneTile(kind, bounds.x, bounds.y, bounds.w, bounds.h);
}

function drawPerspectiveRowBackfill(view: BitmapPerspectiveView, bounds: Bounds): void {
    const rows = new Map<number, BitmapPerspectiveCell[]>();
    for (const cell of view.cells) {
        const row = rows.get(cell.forward) || [];
        row.push(cell);
        rows.set(cell.forward, row);
    }
    Array.from(rows.keys())
        .sort((a, b) => b - a)
        .forEach((forward) => {
            const row = rows.get(forward) || [];
            const left = row.reduce<BitmapPerspectiveCell | null>(
                (candidate, cell) => (candidate === null || cell.side < candidate.side ? cell : candidate),
                null,
            );
            const right = row.reduce<BitmapPerspectiveCell | null>(
                (candidate, cell) => (candidate === null || cell.side > candidate.side ? cell : candidate),
                null,
            );
            if (left) drawPerspectiveSideBackfillCell(left, bounds, -1);
            if (right) drawPerspectiveSideBackfillCell(right, bounds, 1);
        });
}

function drawPerspectiveSideBackfillCell(cell: BitmapPerspectiveCell, bounds: Bounds, side: -1 | 1): void {
    const quad = perspectiveQuad(bounds, cell.forward, cell.side);
    const extension =
        side < 0 ?
            {
                nearLeft: {x: bounds.x, y: quad.nearLeft.y},
                nearRight: quad.nearLeft,
                farRight: quad.farLeft,
                farLeft: {x: bounds.x, y: quad.farLeft.y}
            } :
            {
                nearLeft: quad.nearRight,
                nearRight: {x: bounds.x + bounds.w, y: quad.nearRight.y},
                farRight: {x: bounds.x + bounds.w, y: quad.farRight.y},
                farLeft: quad.farRight
            };
    const key = perspectiveTerrainKey(cell.tile);
    const visualTile = key === "void" ? {...cell.tile, slug: "plain", passable: true} : cell.tile;
    const alpha = key === "void" ? 0.16 : Math.max(0.18, 0.42 - cell.forward * 0.06);
    ctx.save();
    ctx.globalAlpha = alpha;
    drawPerspectiveTerrainTexture({...cell, tile: visualTile}, extension, alpha);
    ctx.restore();
}

function drawPerspectiveGroundCell(cell: BitmapPerspectiveCell, bounds: Bounds): void {
    const quad = perspectiveQuad(bounds, cell.forward, cell.side);
    const key = perspectiveTerrainKey(cell.tile);
    const visualTile = key === "void" ? {...cell.tile, slug: "plain", passable: true} : cell.tile;
    const slug = (visualTile.slug || "plain").toLowerCase();
    const settlement = (visualTile.settlement || "").toLowerCase();
    const colors = tileColors(slug, settlement, visualTile.passable);
    const alpha = key === "void" ? 0.08 : Math.max(0.22, 0.54 - cell.forward * 0.08);
    ctx.save();
    ctx.globalAlpha = alpha;
    fillPerspectiveQuad(quad, colors.base);
    fillPerspectiveQuad(quad, "rgba(0, 0, 0, " + Math.min(0.36, 0.06 + cell.forward * 0.05) + ")");
    drawPerspectiveTerrainTexture({...cell, tile: visualTile}, quad, alpha);
    if (cell.forward <= 1 && Math.abs(cell.side) <= 1) {
        ctx.strokeStyle = "rgba(237, 212, 132, 0.035)";
        ctx.lineWidth = 0.55;
        strokePerspectiveQuad(quad);
    }
    ctx.restore();
}

function drawPerspectiveCellContent(cell: BitmapPerspectiveCell, bounds: Bounds): void {
    const key = perspectiveTerrainKey(cell.tile);
    if (cell.forward === 0 || key === "plain" || key === "water" || key === "desert" || key === "road" || key === "void") return;
    const sprite = perspectiveAssetForTile(cell.tile);
    if (!sprite || !sprite.complete || sprite.naturalWidth === 0) return;
    const quad = perspectiveQuad(bounds, cell.forward, cell.side);
    const nearCenter = midpoint(quad.nearLeft, quad.nearRight);
    const farCenter = midpoint(quad.farLeft, quad.farRight);
    const cellWidth = Math.max(10, distance(quad.nearLeft, quad.nearRight));
    const contentWidth =
        cellWidth *
        (key === "town" || key === "castle" || key === "hut" ? 1.38 : key === "mountain" ? 1.55 : 1.18);
    const contentHeight = contentWidth * (sprite.naturalHeight / sprite.naturalWidth);
    const anchorX = farCenter.x + (nearCenter.x - farCenter.x) * 0.66;
    const anchorY = farCenter.y + (nearCenter.y - farCenter.y) * 0.78;
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.globalAlpha = Math.max(0.45, 1 - cell.forward * 0.08);
    ctx.drawImage(sprite, anchorX - contentWidth / 2, anchorY - contentHeight, contentWidth, contentHeight);
    if (key === "town" || key === "castle" || key === "hut") {
        drawPerspectiveSettlementLabel(key, anchorX, anchorY - contentHeight * 0.62, Math.max(16, contentWidth * 0.15));
    }
    ctx.restore();
}

function perspectiveHorizonY(bounds: Bounds): number {
    return bounds.y + bounds.h * 0.44;
}

function perspectiveNearY(bounds: Bounds): number {
    return bounds.y + bounds.h * 0.96;
}

function perspectivePoint(bounds: Bounds, depth: number, side: number): CanvasPoint {
    const horizonY = perspectiveHorizonY(bounds);
    const nearY = perspectiveNearY(bounds);
    const distance = depth + 1;
    const y = horizonY + (nearY - horizonY) / distance;
    const unit = bounds.w * 0.42 / Math.pow(distance, 0.72);
    return {x: bounds.x + bounds.w / 2 + side * unit, y};
}

function perspectiveQuad(bounds: Bounds, forward: number, side: number): PerspectiveQuad {
    const left = side - 0.5;
    const right = side + 0.5;
    return {
        nearLeft: perspectivePoint(bounds, forward, left),
        nearRight: perspectivePoint(bounds, forward, right),
        farRight: perspectivePoint(bounds, forward + 1, right),
        farLeft: perspectivePoint(bounds, forward + 1, left)
    };
}

function fillPerspectiveQuad(quad: PerspectiveQuad, fillStyle: string): void {
    ctx.fillStyle = fillStyle;
    ctx.beginPath();
    ctx.moveTo(quad.nearLeft.x, quad.nearLeft.y);
    ctx.lineTo(quad.nearRight.x, quad.nearRight.y);
    ctx.lineTo(quad.farRight.x, quad.farRight.y);
    ctx.lineTo(quad.farLeft.x, quad.farLeft.y);
    ctx.closePath();
    ctx.fill();
}

function strokePerspectiveQuad(quad: PerspectiveQuad): void {
    ctx.beginPath();
    ctx.moveTo(quad.nearLeft.x, quad.nearLeft.y);
    ctx.lineTo(quad.nearRight.x, quad.nearRight.y);
    ctx.lineTo(quad.farRight.x, quad.farRight.y);
    ctx.lineTo(quad.farLeft.x, quad.farLeft.y);
    ctx.closePath();
    ctx.stroke();
}

function clipPerspectiveQuad(quad: PerspectiveQuad): void {
    ctx.beginPath();
    ctx.moveTo(quad.nearLeft.x, quad.nearLeft.y);
    ctx.lineTo(quad.nearRight.x, quad.nearRight.y);
    ctx.lineTo(quad.farRight.x, quad.farRight.y);
    ctx.lineTo(quad.farLeft.x, quad.farLeft.y);
    ctx.closePath();
    ctx.clip();
}

function drawPerspectiveTerrainTexture(cell: BitmapPerspectiveCell, quad: PerspectiveQuad, alphaScale = 1): void {
    const key = perspectiveTerrainKey(cell.tile);
    const bounds = perspectiveQuadBounds(quad);
    ctx.save();
    clipPerspectiveQuad(quad);
    const texture = perspectiveGroundTextureForTile(cell.tile);
    if (texture && texture.complete && texture.naturalWidth > 0) {
        const source = perspectiveTextureSource(texture, key);
        const textureAlpha = key === "plain" ? 0.74 : key === "water" || key === "desert" ? 0.58 : 0.42;
        ctx.globalAlpha = textureAlpha * alphaScale;
        ctx.drawImage(texture, source.x, source.y, source.w, source.h, bounds.x, bounds.y, bounds.w, bounds.h);
        ctx.globalAlpha = 1;
    }
    ctx.restore();
}

function perspectiveTextureSource(texture: HTMLImageElement, key: string): Bounds {
    if (key === "plain" || key === "forest" || key === "foothills" || key === "road") {
        return {
            x: texture.naturalWidth * 0.16,
            y: texture.naturalHeight * 0.53,
            w: texture.naturalWidth * 0.68,
            h: texture.naturalHeight * 0.33
        };
    }
    return {
        x: texture.naturalWidth * 0.08,
        y: texture.naturalHeight * 0.48,
        w: texture.naturalWidth * 0.84,
        h: texture.naturalHeight * 0.42
    };
}

function perspectiveGroundTextureForTile(tile: BitmapTile): HTMLImageElement | null {
    const slug = (tile.slug || "plain").toLowerCase();
    if (isWaterTerrain(slug)) return perspectiveTerrainAssets.water;
    if (slug.includes("desert")) return perspectiveTerrainAssets.desert;
    if (slug.includes("foothills")) return perspectiveTerrainAssets.foothills;
    if (slug.includes("swamp")) return perspectiveTerrainAssets.swamp;
    if (slug.includes("forest")) return perspectiveTerrainAssets.plain;
    if (slug.includes("mountain")) return perspectiveTerrainAssets.foothills;
    if (slug.includes("road")) return perspectiveTerrainAssets.road;
    if (slug.includes("plain")) return perspectiveTerrainAssets.plain;
    return perspectiveTerrainAssets.plain;
}

function perspectiveQuadBounds(quad: PerspectiveQuad): Bounds {
    const xs = [quad.nearLeft.x, quad.nearRight.x, quad.farRight.x, quad.farLeft.x];
    const ys = [quad.nearLeft.y, quad.nearRight.y, quad.farRight.y, quad.farLeft.y];
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    return {x: minX, y: minY, w: maxX - minX, h: maxY - minY};
}

function drawPerspectiveSettlementLabel(key: string, x: number, y: number, size: number): void {
    const label = key === "castle" ? "C" : key === "hut" ? "H" : "T";
    ctx.save();
    ctx.fillStyle = "rgba(8, 6, 3, 0.76)";
    ctx.fillRect(x - size / 2, y - size / 2, size, size);
    ctx.strokeStyle = "#f2d777";
    ctx.strokeRect(x - size / 2 + 0.5, y - size / 2 + 0.5, size - 1, size - 1);
    ctx.fillStyle = "#f8e49a";
    ctx.font = "bold " + Math.max(10, Math.floor(size * 0.62)) + "px Menlo, Monaco, Consolas, monospace";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(label, x, y + 1);
    ctx.restore();
}

function midpoint(a: CanvasPoint, b: CanvasPoint): CanvasPoint {
    return {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2};
}

function distance(a: CanvasPoint, b: CanvasPoint): number {
    return Math.hypot(a.x - b.x, a.y - b.y);
}

function drawPerspectiveFacingLabel(bounds: Bounds, facing: WildernessFacing): void {
    ctx.save();
    ctx.fillStyle = "rgba(5, 8, 5, 0.62)";
    ctx.fillRect(bounds.x + 14, bounds.y + 14, 104, 28);
    ctx.strokeStyle = "rgba(218, 189, 107, 0.46)";
    ctx.strokeRect(bounds.x + 14.5, bounds.y + 14.5, 103, 27);
    ctx.font = "700 13px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#ead68a";
    ctx.fillText("VIEW " + facing.toUpperCase(), bounds.x + 24, bounds.y + 33);
    ctx.restore();
    drawPerspectiveCompass(bounds, facing);
}

function drawPerspectiveCompass(bounds: Bounds, facing: WildernessFacing): void {
    const centerX = bounds.x + bounds.w - 44;
    const centerY = bounds.y + 44;
    const radius = 29;
    ctx.save();
    ctx.translate(centerX, centerY);
    ctx.fillStyle = "rgba(5, 8, 5, 0.68)";
    ctx.beginPath();
    ctx.arc(0, 0, radius + 7, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "rgba(232, 204, 121, 0.48)";
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.arc(0, 0, radius + 6, 0, Math.PI * 2);
    ctx.stroke();
    ctx.strokeStyle = "rgba(232, 204, 121, 0.32)";
    ctx.beginPath();
    ctx.moveTo(0, -radius);
    ctx.lineTo(0, radius);
    ctx.moveTo(-radius, 0);
    ctx.lineTo(radius, 0);
    ctx.stroke();
    ctx.font = "700 10px Menlo, Monaco, Consolas, monospace";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillStyle = "#ead68a";
    ctx.fillText("N", 0, -radius + 5);
    ctx.fillText("E", radius - 5, 0);
    ctx.fillText("S", 0, radius - 5);
    ctx.fillText("W", -radius + 5, 0);
    ctx.rotate(facingAngle(facing));
    ctx.fillStyle = "#f0c44f";
    ctx.strokeStyle = "rgba(26, 11, 5, 0.78)";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(0, -radius + 7);
    ctx.lineTo(8, 7);
    ctx.lineTo(0, 2);
    ctx.lineTo(-8, 7);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    ctx.restore();
}

function facingAngle(facing: WildernessFacing): number {
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

function perspectiveAssetForTile(tile: BitmapTile): HTMLImageElement | null {
    return perspectiveTerrainAssets[perspectiveTerrainKey(tile)] ?? null;
}

function perspectiveTerrainKey(tile: BitmapTile): string {
    const slug = (tile.slug || "void").toLowerCase();
    const settlement = (tile.settlement || "").toLowerCase();
    if (settlement === "town" || settlement === "castle" || settlement === "hut") return settlement;
    if (slug === "town" || slug === "castle" || slug === "hut") return slug;
    if (isWaterTerrain(slug)) return "water";
    if (slug.includes("forest")) return "forest";
    if (slug.includes("mountain")) return "mountain";
    if (slug.includes("desert")) return "desert";
    if (slug.includes("foothills")) return "foothills";
    if (slug.includes("swamp")) return "swamp";
    if (slug.includes("road")) return "road";
    if (slug.includes("plain")) return "plain";
    return "void";
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
    const caption = page.summary ? "Company Ledger" : page.kills ? "Kill Ledger" : "Inventory Ledger";
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

function drawTile(tile: BitmapTile, x: number, y: number, size: number, drawCenter = true): void {
    const slug = (tile.slug || "").toLowerCase();
    const settlement = (tile.settlement || "").toLowerCase();
    const variant = tile.variant || 0;
    if (slug.includes("castle-room") || slug.includes("castle-stairs")) {
        drawCastleGridTile(tile, x, y, size, variant);
        return;
    }
    if (isWaterTerrain(slug)) {
        drawConnectedWater(tile, x, y, size);
        if (drawCenter && tile.center) drawPartyMarker(x + size / 2, y + size / 2, size);
        return;
    }
    if (drawTerrainAtlasTile(slug, settlement, variant, x, y, size, drawCenter && tile.center === true)) return;
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
    if (drawCenter && tile.center) {
        ctx.strokeStyle = "#f2d35e";
        ctx.lineWidth = 3;
        ctx.strokeRect(x + 3.5, y + 3.5, size - 7, size - 7);
        ctx.lineWidth = 1;
    }
}

function shouldDrawTerrainTransitions(rows: BitmapTile[][]): boolean {
    return rows.some((row) =>
        row.some((tile) => {
            const slug = (tile.slug || "").toLowerCase();
            return !slug.includes("castle-room") && !slug.includes("castle-stairs") && terrainFamily(tile) !== null;
        }),
    );
}

function drawTerrainTransitions(rows: BitmapTile[][], startX: number, startY: number, size: number): void {
    rows.forEach((row, rowIndex) => {
        row.forEach((tile, colIndex) => {
            const family = terrainFamily(tile);
            if (!family) return;
            const x = startX + colIndex * size;
            const y = startY + rowIndex * size;
            drawTerrainTransitionEdge(rows, family, colIndex, rowIndex, x, y, size, "north", 0, -1);
            drawTerrainTransitionEdge(rows, family, colIndex, rowIndex, x, y, size, "east", 1, 0);
            drawTerrainTransitionEdge(rows, family, colIndex, rowIndex, x, y, size, "south", 0, 1);
            drawTerrainTransitionEdge(rows, family, colIndex, rowIndex, x, y, size, "west", -1, 0);
            drawTerrainTransitionCorner(rows, family, colIndex, rowIndex, x, y, size, "northwest", -1, -1);
            drawTerrainTransitionCorner(rows, family, colIndex, rowIndex, x, y, size, "northeast", 1, -1);
            drawTerrainTransitionCorner(rows, family, colIndex, rowIndex, x, y, size, "southeast", 1, 1);
            drawTerrainTransitionCorner(rows, family, colIndex, rowIndex, x, y, size, "southwest", -1, 1);
        });
    });
}

function drawTerrainTransitionEdge(
    rows: BitmapTile[][],
    family: string,
    col: number,
    row: number,
    x: number,
    y: number,
    size: number,
    side: TerrainTransitionSide,
    dx: number,
    dy: number,
): void {
    const overlay = terrainTransitionOverlayFamily(family, terrainFamilyAt(rows, col + dx, row + dy));
    if (!overlay) return;
    drawRotatedTransitionImage(terrainTransitionAssets[overlay].edge, x, y, size, terrainTransitionSideTurns(side));
}

function drawTerrainTransitionCorner(
    rows: BitmapTile[][],
    family: string,
    col: number,
    row: number,
    x: number,
    y: number,
    size: number,
    corner: TerrainTransitionCorner,
    dx: number,
    dy: number,
): void {
    const overlay = terrainTransitionOverlayFamily(family, terrainFamilyAt(rows, col + dx, row + dy));
    if (!overlay) return;
    if (terrainFamilyAt(rows, col + dx, row) === overlay || terrainFamilyAt(rows, col, row + dy) === overlay) return;
    drawRotatedTransitionImage(terrainTransitionAssets[overlay].corner, x, y, size, terrainTransitionCornerTurns(corner));
}

function drawWildernessMapMarkers(rows: BitmapTile[][], startX: number, startY: number, size: number): void {
    rows.forEach((row, rowIndex) => {
        row.forEach((tile, colIndex) => {
            if (tile.center) drawPartyMarker(startX + colIndex * size + size / 2, startY + rowIndex * size + size / 2, size);
        });
    });
}

function terrainTransitionOverlayFamily(current: string, neighbor: string | null): string | null {
    if (!neighbor || current === neighbor) return null;
    const overlay = terrainTransitionAssets[neighbor];
    if (!overlay) return null;
    const currentPriority = terrainTransitionPriority[current] ?? 0;
    const neighborPriority = terrainTransitionPriority[neighbor] ?? 0;
    return neighborPriority > currentPriority ? neighbor : null;
}

function terrainFamilyAt(rows: BitmapTile[][], col: number, row: number): string | null {
    if (row < 0 || row >= rows.length) return null;
    const cells = rows[row];
    if (col < 0 || col >= cells.length) return null;
    return terrainFamily(cells[col]);
}

function terrainFamily(tile: BitmapTile): string | null {
    const slug = (tile.slug || "").toLowerCase();
    const settlement = (tile.settlement || "").toLowerCase();
    if (settlement || slug === "town" || slug === "castle" || slug === "hut") return null;
    if (slug.includes("castle-room") || slug.includes("castle-stairs")) return null;
    if (isWaterTerrain(slug)) return "water";
    if (slug.includes("mountain")) return "mountain";
    if (slug.includes("swamp")) return "swamp";
    if (slug.includes("forest")) return "forest";
    if (slug.includes("desert")) return "desert";
    if (slug.includes("foothills")) return "foothills";
    if (slug.includes("road")) return "road";
    if (slug.includes("plain")) return "plain";
    if (slug === "void") return null;
    return tile.passable === false ? null : "plain";
}

function drawRotatedTransitionImage(image: HTMLImageElement, x: number, y: number, size: number, quarterTurns: number): void {
    if (!image.complete || image.naturalWidth === 0) return;
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.translate(x + size / 2, y + size / 2);
    ctx.rotate(quarterTurns * Math.PI / 2);
    ctx.drawImage(image, -size / 2, -size / 2, size, size);
    ctx.restore();
}

function terrainTransitionSideTurns(side: TerrainTransitionSide): number {
    switch (side) {
        case "north":
            return 1;
        case "east":
            return 2;
        case "south":
            return 3;
        case "west":
            return 0;
    }
}

function terrainTransitionCornerTurns(corner: TerrainTransitionCorner): number {
    switch (corner) {
        case "northwest":
            return 0;
        case "northeast":
            return 1;
        case "southeast":
            return 2;
        case "southwest":
            return 3;
    }
}

function terrainTransitionAssetsReady(): boolean {
    return Object.values(terrainTransitionAssets).every((asset) =>
        asset.edge.complete &&
        asset.edge.naturalWidth > 0 &&
        asset.corner.complete &&
        asset.corner.naturalWidth > 0,
    );
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

    const chips: StatusChip[] = [
        {label: "TIME", value: status.time, color: "#f0d46d", width: 138},
        {label: "STATUS", value: status.condition, color: statusColor(status.condition), width: 164, severity: statusSeverity(status.condition)},
        {label: "POS", value: status.x + "x" + status.y, color: "#9ec1e4", width: 108},
        {label: "TORCHES", value: String(status.torches), color: "#d8c16d", width: 126},
        {label: "FOOD", value: String(status.food), color: foodStatusColor(status), width: 104, severity: foodStatusSeverity(status)},
        {label: "MOVES LEFT", value: String(status.moves), color: "#a9d4a2", width: 142},
        {label: "GOLD", value: String(status.gold), color: "#e8c56a", width: 116}
    ];
    const gap = 8;
    const total = chips.reduce((sum, chip) => sum + chip.width, 0) + gap * (chips.length - 1);
    let cx = x + Math.floor((w - total) / 2);
    for (const chip of chips) {
        drawStatusChip(cx, y + 14, chip.width, chip.label, chip.value, chip.color, chip.severity);
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

function drawStatusChip(
    x: number,
    y: number,
    w: number,
    label: string,
    value: string,
    color: string,
    severity: StatusSeverity = "normal"
): void {
    ctx.font = "11px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = "#899a72";
    ctx.fillText(label, x + 8, y + 17);
    const labelWidth = Math.min(w - 42, Math.ceil(ctx.measureText(label).width) + 20);
    ctx.font = (severity === "danger" ? "800 16px " : "700 15px ") + "Menlo, Monaco, Consolas, monospace";
    if (severity === "danger") {
        ctx.shadowColor = "rgba(220, 73, 58, 0.36)";
        ctx.shadowBlur = 8;
    }
    ctx.fillStyle = color;
    ctx.fillText(truncateToWidth(value, w - labelWidth - 8), x + labelWidth, y + 17);
    ctx.shadowBlur = 0;
    ctx.shadowColor = "transparent";
}

function drawMemberStatus(member: BitmapStatusMember, x: number, y: number, w: number): void {
    const label = member.slot + " " + member.name;
    const barX = x + 5;
    const barY = y + 18;
    const barW = w - 10;
    ctx.font = "700 13px Menlo, Monaco, Consolas, monospace";
    ctx.fillStyle = member.dead ? "#b86f68" : "#d8d0ae";
    const shownLabel = truncateToWidth(label, w - 10);
    ctx.fillText(shownLabel, x + 5, y + 11);
    if (member.details) {
        // Non-casters carry a mana pool they cannot use; the card shows no mana for them, like the greyed bar.
        const details = member.manaRelevant === false ? {...member.details, manaMax: 0} : member.details;
        statusHotspots.push({x: x + 1, y: y - 3, w: ctx.measureText(shownLabel).width + 8, h: 18, text: member.name, member: details});
    }
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
    if (statusSeverity(condition) === "danger") return "#e25a49";
    if (statusSeverity(condition) === "warning") return "#e0ad55";
    return "#9bd073";
}

function statusSeverity(condition: string): StatusSeverity {
    switch (condition) {
        case "STARVING":
        case "EXHAUSTED":
        case "WEAK":
        case "FAINT":
            return "danger";
        case "HUNGRY":
        case "TIRED":
            return "warning";
        default:
            return "normal";
    }
}

function foodStatusSeverity(status: BitmapStatus): StatusSeverity {
    if (status.food <= 0) return "danger";
    const livingMembers = Math.max(1, (status.members || []).filter((member) => !member.dead).length);
    return status.food < livingMembers * 3 ? "warning" : "normal";
}

function foodStatusColor(status: BitmapStatus): string {
    const severity = foodStatusSeverity(status);
    if (severity === "danger") return "#e25a49";
    if (severity === "warning") return "#e0ad55";
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
    const fantasy = textDisplaySettings.bitmapFont;
    ctx.font = textPanelFont;
    const rows = textPanelRows(frame.textLines || [], maxWidth, bitmapScale);
    const lineHeight = fittedLineHeight(
        fantasy ? currentFantasyLineHeight(bitmapScale) : Math.ceil(24 * textDisplaySettings.bitmapLineSpacing),
        fantasy ? Math.ceil(fantasyFontHeight * bitmapScale) + 3 : 19,
        rows.length,
        bottom - ty
    );
    const measure = textPanelMeasure(bitmapScale);
    const columnWidths = textPanelColumnWidths(rows, maxWidth, measure);
    for (const row of rows) {
        if (ty > bottom) break;
        const rowTop = ty - Math.round(lineHeight * 0.72);
        if (row.kind === "text" && row.text === row.line) {
            registerChoiceTargets(row.text, x, rowTop, maxWidth, lineHeight, measure);
        } else if (row.kind === "text") {
            const segments = choiceSegments(row.line);
            if (segments.length === 1) addChoiceTarget({x: x - 8, y: rowTop, w: maxWidth + 16, h: lineHeight, token: segments[0].token});
        }
        if (row.kind === "stat") {
            drawCreationStatLine(row.stat.label, row.stat.value, x, ty, maxWidth, textPanelLineColor(row.line), reveal);
        } else if (row.kind === "columns") {
            drawTextPanelColumns(row.cells, x, ty, rowTop, lineHeight, columnWidths.get(row.cells.length) || [maxWidth], reveal, bitmapScale);
        } else if (fantasy) {
            drawFantasyRevealText(row.text, x, ty, textPanelLineColor(row.line), textPanelLineDimColor(row.line), reveal, bitmapScale);
        } else {
            ctx.font = textPanelFont;
            drawRevealText(row.text, x, ty, textPanelLineColor(row.line), textPanelLineDimColor(row.line), reveal);
        }
        ty += lineHeight;
    }
    drawTextRevealCursor(reveal);
    drawTextInput(frame.textInput, x, layout.text.y + layout.text.h - 72, maxWidth);
}

type CreationStatLine = { label: string; value: string };

// One drawn row of the text panel: a wrapped piece of a prose line, a row of
// aligned choice columns (cells separated by a tab), or a creation stat line.
type TextPanelRow =
    | { kind: "text"; line: string; text: string }
    | { kind: "columns"; line: string; cells: string[] }
    | { kind: "stat"; line: string; stat: CreationStatLine };

const textPanelFont = "17px Georgia, 'Times New Roman', serif";
const textPanelColumnGap = 20;

function textPanelRows(lines: string[], maxWidth: number, scale: number): TextPanelRow[] {
    const rows: TextPanelRow[] = [];
    for (const line of lines) {
        const stat = creationStatLine(line);
        if (stat) {
            rows.push({kind: "stat", line, stat});
        } else if (line.includes("\t")) {
            rows.push({kind: "columns", line, cells: line.split("\t")});
        } else {
            const parts = textDisplaySettings.bitmapFont ? wrapFantasyLines(line, maxWidth, scale) : wrapCanvasLines(line, maxWidth);
            for (const text of parts) rows.push({kind: "text", line, text});
        }
    }
    return rows;
}

// Uses the preferred line height when every row fits above the bottom edge,
// otherwise tightens it (down to a legible minimum) so menus are never cut off.
function fittedLineHeight(preferred: number, minimum: number, rowCount: number, available: number): number {
    if (rowCount <= 1) return preferred;
    return Math.max(minimum, Math.min(preferred, Math.floor(available / (rowCount - 1))));
}

const choiceMnemonic = /\(([^()\s]{1,6})\)/g;
const choiceHintSeparator = /\s*(?:\||,)\s*/g;

// The keys a line of game text offers when clicked: "(H)eal person" style
// mnemonics (several on one line when the line starts with one), "A. Long
// sword" list keys, and key hints such as "RETURN leaves." or
// "[← previous | → next | ESC exit]". Prose yields no segments.
function choiceSegments(text: string): ChoiceSegment[] {
    const mnemonics: ChoiceSegment[] = [];
    for (const match of text.matchAll(choiceMnemonic)) {
        const start = match.index ?? 0;
        const token = choiceTokenForLabel(match[1]);
        if (token && (start === 0 || /[\s\/|,\[]/.test(text[start - 1]))) mnemonics.push({start, end: text.length, token});
    }
    if (mnemonics.length > 0 && text.slice(0, mnemonics[0].start).trim() === "") {
        mnemonics[0].start = 0;
        mnemonics.forEach((segment, index) => {
            if (index + 1 < mnemonics.length) segment.end = mnemonics[index + 1].start;
        });
        return mnemonics;
    }
    const listKey = text.match(/^\s*([A-Za-z0-9])\.\s/);
    if (listKey) return [{start: 0, end: text.length, token: listKey[1]}];
    return choiceHintSegments(text);
}

function choiceTokenForLabel(label: string): string | null {
    const upper = label.toUpperCase();
    if (upper === "ESC" || upper === "ESCAPE") return "ESCAPE";
    if (upper === "RETURN" || upper === "ENTER") return "ENTER";
    return Array.from(label).length === 1 ? label : null;
}

// Splits a key-hint line at "|" or "," and maps each part to its key; a line
// with any part that names no key is prose and stays unclickable.
function choiceHintSegments(text: string): ChoiceSegment[] {
    const parts: ChoiceSegment[] = [];
    let start = 0;
    const addPart = (end: number): boolean => {
        const part = text.slice(start, end);
        if (part.trim() === "") return true;
        const token = choiceHintToken(part);
        if (!token) return false;
        parts.push({start, end, token});
        return true;
    };
    for (const separator of text.matchAll(choiceHintSeparator)) {
        const index = separator.index ?? 0;
        if (!addPart(index)) return [];
        start = index + separator[0].length;
    }
    return addPart(text.length) ? parts : [];
}

function choiceHintToken(part: string): string | null {
    if (/\bESC\b/.test(part)) return "ESCAPE";
    if (/\b(RETURN|ENTER)\b/.test(part)) return "ENTER";
    if (/^\W*(←|previous\b)/i.test(part)) return "ARROW_LEFT";
    if (/^\W*(→|next\b)/i.test(part)) return "ARROW_RIGHT";
    if (/^\W*Arrows?\b/.test(part)) return "ARROW_RIGHT";
    return null;
}

// Registers the clickable segments of a drawn line. A line offering a single
// key is clickable across the whole [x, x + width] row; lines with several
// keys get one target per measured segment.
function registerChoiceTargets(
    text: string,
    x: number,
    top: number,
    width: number,
    height: number,
    measure: (value: string) => number
): void {
    const segments = choiceSegments(text);
    for (const segment of segments) {
        if (segments.length === 1) {
            addChoiceTarget({x: x - 8, y: top, w: width + 16, h: height, token: segment.token});
        } else {
            const left = x + measure(text.slice(0, segment.start));
            const right = x + measure(text.slice(0, segment.end).trimEnd());
            addChoiceTarget({x: left - 6, y: top, w: right - left + 12, h: height, token: segment.token});
        }
    }
}

// Highlights the target under the mouse pointer, or the menu's highlighted
// choice, as it is registered, before its text is drawn on top.
function addChoiceTarget(target: ChoiceTarget): void {
    choiceTargets.push(target);
    const hovered = pointerPoint !== null && boundsContain(target, pointerPoint);
    if (!hovered && target.token !== menuHighlight) return;
    ctx.save();
    ctx.fillStyle = "rgba(232, 201, 109, 0.16)";
    ctx.fillRect(target.x, target.y, target.w, target.h);
    ctx.fillStyle = "rgba(232, 201, 109, 0.6)";
    ctx.fillRect(target.x, target.y, 2, target.h);
    ctx.restore();
}

function textPanelMeasure(scale: number): (value: string) => number {
    if (textDisplaySettings.bitmapFont) return (value) => fantasyTextWidth(value, scale);
    return (value) => {
        ctx.font = textPanelFont;
        return ctx.measureText(value).width;
    };
}

// Column widths shared by every row with the same number of cells, so a
// column (inventory names, catalog prices) starts at one x for the whole menu:
// each column is as wide as its widest cell when that fits, otherwise the
// columns split the panel evenly.
function textPanelColumnWidths(rows: TextPanelRow[], maxWidth: number, measure: (value: string) => number): Map<number, number[]> {
    const natural = new Map<number, number[]>();
    for (const row of rows) {
        if (row.kind !== "columns") continue;
        const widths = natural.get(row.cells.length) || row.cells.map(() => 0);
        row.cells.forEach((cell, index) => {
            widths[index] = Math.max(widths[index], measure(cell));
        });
        natural.set(row.cells.length, widths);
    }
    const fitted = new Map<number, number[]>();
    natural.forEach((widths, count) => {
        const withGaps = widths.map((width, index) => width + (index < count - 1 ? textPanelColumnGap : 0));
        const total = withGaps.reduce((sum, width) => sum + width, 0);
        fitted.set(count, total <= maxWidth ? withGaps : widths.map(() => maxWidth / count));
    });
    return fitted;
}

// Draws the tab-separated cells of a row at their column positions; the
// runtime lays out inventory pickers and price lists this way.
function drawTextPanelColumns(
    cells: string[],
    x: number,
    y: number,
    rowTop: number,
    rowHeight: number,
    columnWidths: number[],
    reveal: TextRevealDrawState,
    scale: number
): void {
    const measure = textPanelMeasure(scale);
    // A row whose only key is in its first cell, like "(1) Sword | 160 au", is
    // one choice across the whole row; inventory rows offer a key per cell.
    const firstCellSegments = choiceSegments(cells[0]);
    const wholeRow = firstCellSegments.length === 1 && cells.slice(1).every((cell) => choiceSegments(cell).length === 0);
    if (wholeRow) {
        const rowWidth = columnWidths.reduce((sum, width) => sum + width, 0);
        addChoiceTarget({x: x - 8, y: rowTop, w: rowWidth + 16, h: rowHeight, token: firstCellSegments[0].token});
    }
    let cellX = x;
    cells.forEach((cell, index) => {
        const columnWidth = columnWidths[index] ?? columnWidths[columnWidths.length - 1];
        const cellWidth = index < cells.length - 1 ? columnWidth - textPanelColumnGap : columnWidth;
        if (!wholeRow) registerChoiceTargets(cell, cellX, rowTop, cellWidth, rowHeight, measure);
        const color = textPanelLineColor(cell);
        const dimColor = textPanelLineDimColor(cell);
        if (textDisplaySettings.bitmapFont) {
            drawFantasyRevealText(truncateFantasyToWidth(cell, cellWidth, scale), cellX, y, color, dimColor, reveal, scale);
        } else {
            ctx.font = textPanelFont;
            drawRevealText(truncateToWidth(cell, cellWidth), cellX, y, color, dimColor, reveal);
        }
        cellX += columnWidth;
    });
    // The separators count toward the frame's reveal length.
    if (reveal.active) reveal.remaining -= cells.length - 1;
}

function creationStatLine(line: string): CreationStatLine | null {
    const match = line.match(/^\s*(Weapon Skill|Health|Wisdom|Charisma|Dexterity)\s*:\s*(\d+)\s*$/);
    return match ? { label: match[1], value: match[2] } : null;
}

function drawCreationStatLine(
    label: string,
    value: string,
    x: number,
    y: number,
    maxWidth: number,
    color: string,
    reveal: TextRevealDrawState
): void {
    const charCost = Math.max(1, label.length + value.length + 1);
    if (reveal.active) {
        if (reveal.remaining <= 0) {
            if (!reveal.cursor) reveal.cursor = {x, y};
            reveal.remaining -= charCost;
            return;
        }
        reveal.remaining -= charCost;
    }
    const labelX = x;
    const colonX = x + Math.min(178, Math.floor(maxWidth * 0.48));
    const valueX = Math.min(x + maxWidth - 4, colonX + 54);
    ctx.font = textPanelFont;
    ctx.fillStyle = color;
    ctx.textAlign = "left";
    ctx.fillText(label, labelX, y);
    ctx.fillText(":", colonX, y);
    ctx.textAlign = "right";
    ctx.fillText(value, valueX, y);
    ctx.textAlign = "left";
}

/** Chooses the normal or disabled prose color for text-window rows. */
function textPanelLineColor(line: string): string {
    return isDeadTargetLine(line) ? "#8f968d" : "#efe4c6";
}

/** Chooses the reveal-tail color that matches the row's normal/disabled state. */
function textPanelLineDimColor(line: string): string {
    return isDeadTargetLine(line) ? "rgba(143, 150, 141, 0.22)" : "rgba(239, 228, 198, 0.25)";
}

/** Detects combat target rows where the target slot is intentionally not selectable. */
function isDeadTargetLine(line: string): boolean {
    return /^\s{3,}\S.*\(dead\)\s*$/.test(line);
}

function drawPartyTextPanel(page: BitmapPartyPage): void {
    const x = layout.text.x + 28;
    const y = layout.text.y + 48;
    const maxWidth = layout.text.w - 56;
    if (page.member) drawPartyMemberText(page.member, x, y, maxWidth);
    else if (page.summary) drawPartySummaryText(page.summary, x, y, maxWidth);
    else if (page.kills) drawPartyKillsText(page.kills, x, y, maxWidth);
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
        ["Charisma", member.charisma],
        ["Dexterity", member.dexterity]
    ], x, y + 70, maxWidth);
    drawPartySection("Equipment", x, y + 270, maxWidth);
    drawPartyInfoRow("Weapon", member.weapon, x, y + 304, maxWidth);
    drawPartyInfoRow("Armor", member.armor, x, y + 336, maxWidth);
    drawPartyInfoRow("Status", member.status, x, y + 368, maxWidth);
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
    const entries = page.inventory && page.inventory.length > 0 ? page.inventory : ["Nothing carried."];
    // Same slot letters and two-column layout as the inventory pickers.
    const columns = entries.length > 1 ? 2 : 1;
    const columnWidth = maxWidth / columns;
    const cellWidth = columnWidth - (columns > 1 ? textPanelColumnGap : 0);
    drawPartyLedger("Inventory", Math.ceil(entries.length / columns), x, y, maxWidth, (row, ty) => {
        for (let column = 0; column < columns; column++) {
            const entry = entries[row * columns + column];
            if (entry !== undefined) drawPartyCell(entry, x + column * columnWidth, ty, cellWidth);
        }
    });
}

/** Monsters slain, most first: the monster's name, and how many fell, in a column of their own. */
function drawPartyKillsText(kills: BitmapPartyKillsPage, x: number, y: number, maxWidth: number): void {
    const title = (kills.title || []).filter((line) => line.trim().length > 0).join(" ") || "Monsters slain";
    const entries = kills.entries || [];
    if (entries.length === 0) {
        const empty = kills.empty || [];
        drawPartyLedger(title, empty.length, x, y, maxWidth, (row, ty) => drawPartyCell(empty[row], x, ty, maxWidth));
        return;
    }
    const countX = x + maxWidth * 0.8;
    drawPartyLedger(title, entries.length, x, y, maxWidth, (row, ty) => {
        const entry = entries[row];
        drawPartyCell(entry.monster || "", x, ty, countX - x - textPanelColumnGap);
        drawPartyCell(String(entry.count ?? 0), countX, ty, x + maxWidth - countX);
    });
}

/** A titled party ledger page: its heading, a divider, and [rowCount] rows that [drawRow] fills at each baseline. */
function drawPartyLedger(
    title: string,
    rowCount: number,
    x: number,
    y: number,
    maxWidth: number,
    drawRow: (row: number, baseline: number) => void
): void {
    ctx.font = "700 28px Palatino, 'Palatino Linotype', Georgia, serif";
    ctx.fillStyle = "#f1df9f";
    ctx.fillText(title, x, y);
    drawPartyDivider(x, y + 24, maxWidth);
    const top = y + 58;
    const bottom = layout.text.y + layout.text.h - 62;
    const bitmapScale = currentFantasyTextScale();
    const lineHeight = textDisplaySettings.bitmapFont
        ? fittedLineHeight(currentFantasyLineHeight(bitmapScale) + 1, Math.ceil(fantasyFontHeight * bitmapScale) + 3, rowCount, bottom - top)
        : fittedLineHeight(25, 19, rowCount, bottom - top);
    for (let row = 0; row < rowCount; row++) {
        const baseline = top + row * lineHeight;
        if (baseline > bottom) return;
        drawRow(row, baseline);
    }
}

function drawPartyCell(text: string, x: number, baseline: number, cellWidth: number): void {
    if (textDisplaySettings.bitmapFont) {
        const bitmapScale = currentFantasyTextScale();
        drawFantasyText(truncateFantasyToWidth(text, cellWidth, bitmapScale), x, baseline - fantasyFontHeight * bitmapScale, bitmapScale, "#efe4c6");
    } else {
        ctx.font = textPanelFont;
        ctx.fillStyle = "#efe4c6";
        ctx.fillText(truncateToWidth(text, cellWidth), x, baseline);
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
        registerChoiceTargets(text, x, y - 20, maxWidth, 26, (value) => fantasyTextWidth(value, scale));
        drawFantasyText(truncateFantasyToWidth(text, maxWidth, scale), x, y - fantasyFontHeight * scale, scale, "#bdb496");
        return;
    }
    ctx.font = "13px Menlo, Monaco, Consolas, monospace";
    registerChoiceTargets(text, x, y - 18, maxWidth, 26, (value) => ctx.measureText(value).width);
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
    const value = String(text || "");
    if (ctx.measureText(value).width <= maxWidth) return [value];
    const words = value.trim().split(/\s+/).filter(Boolean);
    const lines: string[] = [];
    let line = "";
    const flushLongWord = (word: string): void => {
        if (line) {
            lines.push(line);
            line = "";
        }
        let chunk = "";
        for (const char of Array.from(word)) {
            const test = chunk + char;
            if (chunk && ctx.measureText(test).width > maxWidth) {
                lines.push(chunk);
                chunk = char;
            } else {
                chunk = test;
            }
        }
        line = chunk;
    };
    for (const word of words) {
        if (ctx.measureText(word).width > maxWidth) {
            flushLongWord(word);
            continue;
        }
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
configureSoundControls();
configureWildernessDisplayControls();
refreshServerInfo().catch(() => undefined);
connect();
