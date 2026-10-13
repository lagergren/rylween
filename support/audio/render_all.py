"""
Renders every sound effect, music track and ambient loop, encodes them as MP3
and installs them into the game's resources (game/src/main/resources/audio).

Usage, from the repository root:

    uv run --project support/audio support/audio/render_all.py [sfx|music|ambience ...]

With no arguments it renders everything. MP3 encoding needs `lame` on the
PATH. WAV masters and render reports stay in build/audio, which git ignores.
Rendering is deterministic: each piece uses a fixed random seed, so the same
sources produce the same audio.

Each piece is also its own script: `render_sfx.py OUT_DIR [ID_FILTER]`, and
for example `title.py OUT_DIR` or `ambience_church.py OUT_DIR`.
"""

import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
OUT = ROOT / "build" / "audio"
GAME_AUDIO = ROOT / "game" / "src" / "main" / "resources" / "audio"

# script -> installed file name, without extension
MUSIC = {
    "title": "title",
    "wilderness_day": "wilderness-day",
    "wilderness_night": "wilderness-night",
    "town": "town",
    "tavern": "tavern",
    "temple": "temple",
    "dungeon": "dungeon",
    "cave": "cave",
    "castle_zadraz": "castle-zadraz",
    "battle": "battle",
    "ending": "ending",
}
AMBIENCE = {
    "ambience_church": "church",
    "ambience_tavern": "tavern",
    "ambience_inn": "inn",
    "ambience_smith": "smith",
    "ambience_merchant": "merchant",
    "ambience_town_street": "town-street",
    "ambience_hut": "hut",
}


def run(script: str, *args: str) -> None:
    subprocess.run([sys.executable, str(HERE / f"{script}.py"), *args], check=True, cwd=HERE)


def encode(wav: Path, mp3: Path, quality: str) -> None:
    mp3.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["lame", "--silent", "-V", quality, str(wav), str(mp3)], check=True)


def render_loops(pieces: dict[str, str], kind: str, quality: str) -> None:
    out = OUT / kind
    out.mkdir(parents=True, exist_ok=True)
    for script, name in pieces.items():
        print(f"{kind}: {name}", flush=True)
        run(script, str(out))
        encode(out / f"{name}.wav", GAME_AUDIO / kind / f"{name}.mp3", quality)


def render_sfx() -> None:
    out = OUT / "sfx"
    print("sfx: all", flush=True)
    run("render_sfx", str(out))
    for mp3 in sorted(out.glob("*/*.mp3")):
        target = GAME_AUDIO / "sfx" / mp3.parent.name / mp3.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(mp3, target)


def main(kinds: list[str]) -> None:
    if shutil.which("lame") is None:
        raise SystemExit("lame is required for MP3 encoding")
    selected = kinds or ["sfx", "music", "ambience"]
    if "sfx" in selected:
        render_sfx()
    if "music" in selected:
        render_loops(MUSIC, "music", "4")
    if "ambience" in selected:
        render_loops(AMBIENCE, "ambience", "5")


if __name__ == "__main__":
    main(sys.argv[1:])
