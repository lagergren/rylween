# Adventurers of Rylween Missions

This document describes the current authored mission chains in `RylweenGame.kt`.
Coordinates use the game map's `x,y` convention. Location handles such as
`Locations.borinCamp` are runtime state placements; the current replay fixtures
place them as listed here, while normal authored prose renders their coordinates
through `location(...).coordinates`.

## Power-Item Path

The final Castle Ydarg victory path requires five power milestones:
`fireShield`, `masterCrown`, `footOfJazzy`, `braceletsOfThun`, and `silverKey`.
Castle Ydarg entry is also gated by the Silver Key, and completion marks
`ydargVictory` for Hall of Heroes reporting.

| Chain | Start | Mission trigger | Main flags | Rewards and unlocks |
|-------|-------|-----------------|------------|---------------------|
| Garth's cargo | Garth dialogue | Random wilderness step, 9 percent, after `garth.bandits.accepted` | `garth.bandits`: `none -> accepted -> recovered -> closed` | Bandit battle recovers cargo. Returning to Garth grants 2,000 gold and a red gem. |
| Fire Shield | Marcus the hermit dialogue | Random wilderness step, 9 percent, after `hermit.trollTooth.accepted`; then `Locations.hermitDungeon` | `hermit.trollTooth`: `none -> accepted -> extracted -> delivered -> fireShieldAcquired` | Troll battle yields a tooth. Marcus reveals the Fire Shield dungeon. The dungeon grants the Fire Shield milestone. Current fixtures place `hermitDungeon` at `45,13`. |
| Bracelets of Thun | Harry sells a map | `Locations.thunTemple`, after `bracelets.thun.mapBought` | `bracelets.thun`: `none -> mapBought -> braceletsTaken` | Costs 10,000 gold for the map. Temple entry requires five altar gems from the authored altar-gem item pool. The dungeon grants the Bracelets of Thun milestone. Current fixtures place `thunTemple` at `41,30`. |
| Master Crown | Broth points to Ithienn, then Ithienn's three tasks | `Locations.koboldVillage`, swamp terrain, `Locations.borinCamp`, `Locations.nolrimCrypt`, and `Locations.masterCrownDungeon` | `nymph.seeker`: `none -> told -> wizard3tActive`; `masterCrown`: `none -> task1Assigned -> task1Done -> task2Assigned -> task2Done -> task3Assigned -> borinMet -> daggerTaken -> borinPaid -> crownTold -> crownTaken` | Kobold village proves task 1. Skullweed on any swamp terrain proves task 2. Borin sends the party to Nolrim's crypt for Reebar, then grants 3,578 gold and red/blue/green gems. Ithienn reveals the Master Crown dungeon, which grants the Master Crown milestone. Current fixtures place `koboldVillage` at `53,10`, `borinCamp` at `47,28`, `nolrimCrypt` at `11,23`, and `masterCrownDungeon` at `23,32`. |
| Foot of Jazzy | Leander trades information for an item worth at least 35 gold | `Locations.beastCave`, then any foothills terrain after the ogres are cleared | `jazzy.ogre`: `none -> accepted -> ogresCleared -> jazzyDefeated` | Ogre cave grants 5,645 gold and Mithril Mail, then a note points to the eastern peaks. The foothills event battles Jazzy's minions and Jazzy, then grants the Foot of Jazzy milestone. Current fixtures place `beastCave` at `11,23`. |
| Silver Key | Bernak sells a map, then Gotharp and Azaaza reveal the theft trail | `Locations.silverKeyPit`, random wilderness step 9 percent for hobgoblin caravan, then `Locations.hobgoblinCave` | `silverKey`: `none -> mapBought -> noteFound -> thiefSqueezed -> smithConfessed -> caveLocated -> keyTaken` | Bernak's map costs 50,000 gold. The Silver Sanctum produces the clue note. Gotharp names the smith, Azaaza reveals the hobgoblin caravan, the random caravan reveals the cave, and the cave path grants the Silver Key milestone. Current fixtures place `silverKeyPit` at `45,13` and `hobgoblinCave` at `46,13`. |

## Other Mission Chains

| Chain | Start | Trigger or target | Main flags | Rewards and unlocks |
|-------|-------|-------------------|------------|---------------------|
| Ranger letter | Nrim-To dialogue | Deliver to Marraga through normal NPC dialogue | `ranger.letter`: `none -> accepted -> delivered` | Marraga grants a Magic Sword. The recipient location is resolved through NPC role placement, so it may be authored/random rather than a fixed coordinate. |
| Areth's book | Areth, Gorm, Nevaak, then Areth again | Normal NPC dialogue branches | `fighter.priestBook`: `none -> priestTold -> priestVisited -> wizardRobbed -> fighterGrateful` | Nevaak gives up the book; returning to Areth grants the Dancing Sword. |
| Erethor's Lute | Tybalt dialogue | Fixed coordinate `6,20`; then return to Tybalt and Kaal | `erethor.lute`: `none -> rumorTold -> luteTaken -> noteReceived -> cloakReceived` | Erethor temple dungeon grants the Lute. Tybalt gives a note, and Kaal grants the Cloak of Gods. |
| Peasant's wife | Ron dialogue | Fixed coordinate `42,23`; then return to Ron | `peasant.wife`: `none -> accepted -> rescued -> paid` | The cave/dungeon rescue marks Tammy rescued. Ron grants 3,000 gold, Adamantium Mail, Holy Hand Grenade, amber potion, fire wand, and crystal bowl. |

## Support Flags

`church.donationOnce` prevents repeating the same donation branch. `smith.beerOffer`
records the smith's first beer-offer branch. `encounter.immune` is scoped to an
encounter and is used by combat scripts rather than long-lived story progress.

## Known Cleanup Notes

Two current dungeon-event narration sites still use hardcoded member numbers
inside authored scripts: Erethor's Lute chamber names member 3 for the surprise
line and member 4 for finding the Lute. They should move to a reusable
filtered party-member selector such as `party.randomMember(AliveOnly)` or
`party.firstMember(AliveOnly)`, then render the selected member name through the
same text renderer. Until that helper exists, changing only the number would not
fix dead-member or small-party cases.
