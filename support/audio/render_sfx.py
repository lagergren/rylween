"""
Renders every sound effect to OUT_DIR/<group>/<id>.wav and .mp3.

Each effect is normalized so its loudest 150 ms sits at a common K-weighted
(LUFS-style) RMS level,
then offset by its catalog gain (UI ticks quiet, impacts loud), with peaks
kept below -1 dBFS.

Usage: render_sfx.py OUT_DIR [ID_FILTER]
"""

import subprocess
import sys
from pathlib import Path

import numpy as np

import sfx
from sfx import SR

TARGET_RMS_DB = -16.0
PEAK_DB = -1.0

# (group, id, gain dB, render)
CATALOG = [
    ("combat", "miss-1", -3, lambda r: sfx.miss(r, 0)),
    ("combat", "miss-2", -3, lambda r: sfx.miss(r, 1)),
    ("combat", "miss-3", -3, lambda r: sfx.miss(r, 2)),
    ("combat", "miss-heavy", -3, lambda r: sfx.miss(r, 3)),
    ("combat", "hit-slash-1", 0, lambda r: sfx.slash(r, 0)),
    ("combat", "hit-slash-2", 0, lambda r: sfx.slash(r, 1)),
    ("combat", "hit-slash-3", 0, lambda r: sfx.slash(r, 2)),
    ("combat", "hit-chop-1", 0, lambda r: sfx.chop(r, 0)),
    ("combat", "hit-chop-2", 0, lambda r: sfx.chop(r, 1)),
    ("combat", "hit-blunt-1", 0, lambda r: sfx.blunt(r, 0)),
    ("combat", "hit-blunt-2", 0, lambda r: sfx.blunt(r, 1)),
    ("combat", "hit-blunt-3", 0, lambda r: sfx.blunt(r, 2)),
    ("combat", "hit-flesh-1", 0, lambda r: sfx.meat(r, 0)),
    ("combat", "hit-flesh-2", 0, lambda r: sfx.meat(r, 1)),
    ("combat", "hit-pierce-1", 0, lambda r: sfx.pierce(r, 0)),
    ("combat", "hit-pierce-2", 0, lambda r: sfx.pierce(r, 1)),
    ("combat", "bow-hit", -1, lambda r: sfx.ranged(r, "bow", True)),
    ("combat", "bow-miss", -3, lambda r: sfx.ranged(r, "bow", False)),
    ("combat", "crossbow-hit", -1, lambda r: sfx.ranged(r, "crossbow", True)),
    ("combat", "crossbow-miss", -3, lambda r: sfx.ranged(r, "crossbow", False)),
    ("combat", "sling-hit", -1, lambda r: sfx.ranged(r, "sling", True)),
    ("combat", "sling-miss", -3, lambda r: sfx.ranged(r, "sling", False)),
    ("combat", "dart-hit", -1, lambda r: sfx.ranged(r, "dart", True)),
    ("combat", "dart-miss", -3, lambda r: sfx.ranged(r, "dart", False)),
    ("combat", "fight", -3, sfx.fight),
    ("combat", "defend", -4, sfx.defend),
    ("combat", "battle-cry", -3, sfx.battle_cry),
    ("combat", "run-away", -4, sfx.run_away),
    ("combat", "run-fail", -3, sfx.run_fail),
    ("combat", "monster-claw", -1, sfx.claw),
    ("combat", "monster-bite", -1, sfx.bite),
    ("combat", "monster-hit", -1, sfx.monster_hit),
    ("combat", "hit-armor", -1, sfx.armor_clank),
    ("combat", "monster-miss", -3, sfx.monster_miss),
    ("combat", "monster-death", -1, sfx.monster_death),
    ("combat", "monster-dissolve", -3, sfx.monster_dissolve),
    ("combat", "member-death", -3, sfx.member_death),
    ("combat", "encounter", -2, sfx.encounter),
    ("combat", "ambush", -1, sfx.ambush),
    ("combat", "victory", -3, sfx.victory),
    ("combat", "defeat", -3, sfx.defeat),
    ("combat", "level-up", -3, sfx.level_up),
    ("combat", "quest-item", -3, sfx.quest_item),
    ("spell", "cast", -4, sfx.cast),
    ("spell", "fire", -2, sfx.fire),
    ("spell", "frost", -2, sfx.frost),
    ("spell", "confusion", -3, sfx.confusion),
    ("spell", "death-strike", -2, sfx.death_strike),
    ("spell", "hold", -3, sfx.hold),
    ("spell", "anti-magic", -3, sfx.anti_magic),
    ("spell", "heal", -3, sfx.heal),
    ("spell", "restoration", -3, sfx.restoration),
    ("spell", "rebirth", -3, sfx.rebirth),
    ("spell", "mana", -3, sfx.mana),
    ("spell", "banish-evil", -1, sfx.banish_evil),
    ("spell", "revelation", -4, sfx.revelation),
    ("spell", "teleport", -3, sfx.teleport),
    ("spell", "earthquake", -1, sfx.earthquake),
    ("spell", "blindness", -3, sfx.blindness),
    ("spell", "drums-of-death", -2, sfx.drums_of_death),
    ("spell", "kaels-axe", -1, sfx.kaels_axe),
    ("spell", "holy-hand-grenade", 0, sfx.holy_hand_grenade),
    ("spell", "flute-of-madness", -4, sfx.flute_of_madness),
    ("spell", "bless", -3, sfx.bless),
    ("spell", "poison", -3, sfx.poison),
    ("spell", "fizzle", -4, sfx.fizzle),
    ("spell", "monster-cast", -2, sfx.monster_cast),
    ("item", "ready-blade", -4, sfx.ready_blade),
    ("item", "unready-blade", -4, sfx.unready_blade),
    ("item", "ready-axe", -4, sfx.ready_axe),
    ("item", "unready-axe", -4, sfx.unready_axe),
    ("item", "ready-blunt", -4, sfx.ready_blunt),
    ("item", "unready-blunt", -4, sfx.unready_blunt),
    ("item", "ready-polearm", -4, sfx.ready_polearm),
    ("item", "unready-polearm", -4, sfx.unready_polearm),
    ("item", "ready-bow", -4, sfx.ready_bow),
    ("item", "ready-crossbow", -4, sfx.ready_crossbow),
    ("item", "ready-sling", -4, sfx.ready_sling),
    ("item", "ready-dart", -4, sfx.ready_dart),
    ("item", "unready-ranged", -4, sfx.unready_ranged),
    ("item", "ready-armor-metal", -4, sfx.ready_armor_metal),
    ("item", "unready-armor-metal", -4, sfx.unready_armor_metal),
    ("item", "ready-armor-leather", -4, sfx.ready_armor_leather),
    ("item", "unready-armor-leather", -4, sfx.unready_armor_leather),
    ("item", "ready-cloak", -4, sfx.ready_cloak),
    ("item", "unready-cloak", -4, sfx.unready_cloak),
    ("item", "ready-sphere", -4, sfx.ready_sphere),
    ("item", "unready-sphere", -4, sfx.unready_sphere),
    ("item", "potion", -4, sfx.potion),
    ("item", "scroll", -4, sfx.scroll),
    ("item", "torch", -4, sfx.torch),
    ("item", "pickup", -5, sfx.pickup),
    ("item", "drop", -5, sfx.drop),
    ("item", "offer", -4, sfx.offer),
    ("item", "gold-gain", -4, sfx.gold_gain),
    ("item", "gold-spend", -4, sfx.gold_spend),
    ("item", "not-enough-gold", -5, sfx.not_enough_gold),
    ("town", "anvil", -3, sfx.anvil),
    ("town", "meal", -5, sfx.meal),
    ("town", "drink", -4, sfx.drink),
    ("town", "crowd-cheer", -3, sfx.crowd_cheer),
    ("town", "thrown-out", -3, sfx.thrown_out),
    ("explore", "chest-open", -3, sfx.chest_open),
    ("explore", "chest-open-treasure", -3, lambda r: sfx.chest_open(r, True)),
    ("explore", "chest-close", -3, sfx.chest_close),
    ("explore", "locked", -4, sfx.locked),
    ("explore", "trap-gas", -3, sfx.trap_gas),
    ("explore", "trap-pit", -2, sfx.trap_pit),
    ("explore", "trap-boulder", -1, sfx.trap_boulder),
    ("explore", "trap-disarmed", -4, sfx.trap_disarmed),
    ("explore", "explosion", 0, sfx.explosion),
    ("explore", "door-open", -3, sfx.door_open),
    ("explore", "door-slam", -2, sfx.door_slam),
    ("explore", "stairs-down", -4, lambda r: sfx.stairs(r, True)),
    ("explore", "stairs-up", -4, lambda r: sfx.stairs(r, False)),
    ("explore", "bump", -9, sfx.bump),
    ("explore", "step-plain", -10, lambda r: sfx.footstep(r, "grass")),
    ("explore", "step-forest", -10, lambda r: sfx.footstep(r, "forest")),
    ("explore", "step-desert", -10, lambda r: sfx.footstep(r, "desert")),
    ("explore", "step-swamp", -10, lambda r: sfx.footstep(r, "swamp")),
    ("explore", "step-hills", -10, lambda r: sfx.footstep(r, "dirt")),
    ("explore", "step-stone", -10, lambda r: sfx.footstep(r, "stone")),
    ("explore", "enter-town", -4, sfx.enter_town),
    ("explore", "leave-town", -4, sfx.leave_town),
    ("explore", "enter-dungeon", -2, sfx.enter_dungeon),
    ("explore", "enter-cave", -3, sfx.enter_cave),
    ("explore", "enter-castle", -2, sfx.enter_castle),
    ("explore", "hunt-success", -3, lambda r: sfx.hunt(r, True)),
    ("explore", "hunt-fail", -3, lambda r: sfx.hunt(r, False)),
    ("explore", "camp", -5, sfx.camp),
    ("explore", "rest", -5, sfx.lullaby),
    ("explore", "cold-night", -5, sfx.cold_night),
    ("explore", "wait", -7, sfx.wait_hour),
    ("explore", "dawn", -6, sfx.dawn),
    ("explore", "nightfall", -6, sfx.nightfall),
    ("explore", "journal", -6, sfx.journal),
    ("explore", "riddle-right", -4, lambda r: sfx.riddle(r, True)),
    ("explore", "riddle-wrong", -3, lambda r: sfx.riddle(r, False)),
    ("party", "dice-roll", -4, sfx.dice_roll),
    ("party", "character-created", -4, sfx.character_created),
    ("party", "hunger", -4, sfx.hunger),
    ("party", "fatigue", -5, sfx.fatigue),
    ("ui", "move", -14, sfx.ui_move),
    ("ui", "select", -10, sfx.ui_select),
    ("ui", "back", -10, sfx.ui_back),
    ("ui", "error", -9, sfx.ui_error),
    ("ui", "page", -14, sfx.ui_page),
    ("ui", "save", -6, sfx.save),
    ("ui", "load", -6, sfx.load),
    ("ui", "title-start", -4, sfx.title_start),
    ("ui", "quit", -6, sfx.quit_game),
]


def k_weighted(x: np.ndarray) -> np.ndarray:
    """Applies the ITU-R BS.1770 K-weighting that LUFS loudness is measured with."""
    import pyloudnorm

    filters = pyloudnorm.Meter(SR)._filters
    return filters["high_pass"].apply_filter(filters["high_shelf"].apply_filter(x))


def finish(x: np.ndarray, gain_db: float) -> np.ndarray:
    x = sfx.highpass(x, 30.0)
    level = np.abs(x)
    last = np.nonzero(level > level.max() * 10 ** (-60 / 20))[0][-1]
    x = x[: min(len(x), last + int(0.02 * SR))]
    fade = min(len(x) // 4, int(0.015 * SR))
    x[-fade:] *= np.linspace(1.0, 0.0, fade)
    window = min(len(x), int(0.15 * SR))
    weighted = k_weighted(x)
    energy = np.convolve(weighted**2, np.ones(window) / window, mode="valid")
    rms = np.sqrt(energy.max())
    gain = 10 ** ((TARGET_RMS_DB + gain_db) / 20) / rms
    gain = min(gain, 10 ** (PEAK_DB / 20) / np.abs(x).max())
    return x * gain


def main(out_dir: Path, only: str | None) -> None:
    from scipy.io import wavfile

    for index, (group, name, gain_db, render) in enumerate(CATALOG):
        sound_id = f"{group}/{name}"
        if only and only not in sound_id:
            continue
        rng = np.random.default_rng(9000 + index)
        x = render(rng)
        if not np.all(np.isfinite(x)):
            raise SystemExit(f"{sound_id}: non-finite samples")
        x = finish(x, gain_db)
        path = out_dir / group / f"{name}.wav"
        path.parent.mkdir(parents=True, exist_ok=True)
        dither = (rng.random(len(x)) - rng.random(len(x))) / 32768.0
        wavfile.write(path, SR, np.clip(np.round((x + dither) * 32767), -32768, 32767).astype(np.int16))
        subprocess.run(["lame", "--silent", "-m", "m", "-b", "96", str(path), str(path.with_suffix(".mp3"))], check=True)
        print(f"{sound_id:30s} {len(x) / SR:5.2f} s  peak {20 * np.log10(np.abs(x).max()):6.1f} dBFS")


if __name__ == "__main__":
    main(Path(sys.argv[1]), sys.argv[2] if len(sys.argv) > 2 else None)
