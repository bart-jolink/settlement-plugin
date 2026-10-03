# Plan: OSRS Settlement sidebar minigame (v1)

Sidebar settlement builder fed only by OSRS activity (skill XP, Slayer, boss KC, clues). Pure-Java engine + thin RuneLite layer, per-account persistence via RSProfile config, targeting ~100h to the first Wonder; Wonder progression caps at level 10.

Work only happens in this repository; the plugin-hub clone is a read-only reference. Follow [AGENTS.md](../AGENTS.md).

## Design decisions
- Offline XP is ignored (rebaseline every login). Everyone starts fresh (no retroactive XP/KC).
- Sources: skill XP, Slayer, boss kill counts (incl. raids), clue completions. Not quests, collection log, level-ups or minigames - it should be the same for a maxed account and a fresh one.
- Yield per XP drop follows a concave power curve: `units = (xp/10)^(log(8)/log(10))` -> 10xp = 1, 100xp = 8. Splitting XP into smaller drops yields slightly more resources; fractions accumulate.
- No passive/idle production. Sidebar only, no chat notifications. Tabbed Swing panel with in-game item sprites as icons.
- Bounty board: 3 slots, objectives remain ready until claimed, then refill; delivery objectives consume stock on click. At most one live bounty may reward a blueprint. Bounties cannot be abandoned, and use a generic pool ("boss kills", "Slayer Drops", "Herblore XP"; never a specific gated boss). Members content (incl. Sailing) is assumed. When a blueprint becomes unlockable it takes the next slot that opens. Expedition blueprints are excluded from random bounties.
- Expeditions: five shared definitions with per-profile progress, worked on one at a time. Only the first unclaimed expedition progresses; claiming it unlocks the next. Objectives include skill XP, monster parts, clue completions, specific encounters, boss kills, and raids.
  The sequence unlocks Keldagrim Consortium, Museum Camp, Arceuus Library, Jaltevas Pyramid, and Tower of Voices. Objectives mix skill XP, resources, clues, encounters, boss kills, and raids.
- Endgame: build the Wonder at Town Hall 10 after reaching level 10 with every non-optional building and obtaining its blueprint. The Wonder has 10 levels; each level boosts all yields, and costs use the standard 1.55x growth.
- Byproducts (gems, bonus Basic Monster Parts) accumulate as deterministic fractions instead of random rolls, which keeps balance predictable and testable.
- Epic Monster Parts and Raid Artifacts are spent on the Altar, which adds +3% to all yields per level. Artifacts are included in Altar costs at every level and in the Wonder's cost.
- Processing consumes 0.5 raw per unit of work, otherwise Logs/Stone were starved by three consumers each.
- Incomplete bounties expire after 3 hours of logged-in play; completed objectives remain available to claim. Expiry prevents a board full of tasks an account never does (e.g. boss kills for a skiller) from blocking blueprints forever. Expired blueprint bounties are re-offered with a new objective.
- Bounty supply rewards have a 10% chance to include Basic Monster Parts. Monster part bounty rewards (always for boss-kill bounties) roll one size between a Tier 1 and a Tier 3 boss kill's expected parts: 20–200 Basic, 5–100 Rare from Town Hall 4, 1–40 Epic from Town Hall 6, plus a 2% Artifact chance from Town Hall 6.
- Clues give fixed parts plus a 10% bonus batch. Easy 2 Basic; Medium 5 Basic; Hard 10 Basic + 20 Rare bonus; Elite 20 Basic, 5 Rare + 10 Epic bonus (averages a Tier 1 boss kill); Master 40 Basic, 12 Rare, 2 Epic + 20 Epic bonus.
- All building resources, including Curios, Monster Parts, and Artifacts, use a 0.85 initial cost scale and grow by 1.55x per level.
- Events: every hour of logged-in play (6,000 ticks) `EventScheduler` starts a new event that lasts 2 hours (12,000 ticks), so after the first hour two are always active (state `events` list + `nextEventAtPlaytime`). A settlement starts with an event and the scheduler immediately replenishes an empty event list, keeping at least one event live. Draws are 70% buff / 30% debuff and may repeat. Effects: one gathering skill (x3 / x0.5), one processing skill's output (x2 / x0.5, input use unchanged), clue rewards (x2 / x0.5), monster drops = boss/raid drop quantities + Slayer yields (x2 / x0.5), or all of the above (x1.5 / x0.75). Different events multiply; the same buff twice adds its factor (x3 + x3 = x6), the same setback twice compounds (x0.25). Stacks multiplicatively with buildings and boosts; bounty rewards are unaffected. Shown in the Activities tab; event cards size the block dynamically, and the boosts row is omitted when no boosts are active. Catalog lives in `model/SettlementEvent`.

## Economy (per skill)
| Skill | Produces (consumes) | Building |
|---|---|---|
| Woodcutting | Logs | Lumber Camp |
| Mining | Ore 50% / Stone 50%, Gems from Quarry lvl 3 | Quarry |
| Fishing | Fish | Fishing Dock |
| Hunter | Hides | Hunter's Lodge |
| Farming | Grain / Herbs 50/50 | Farmstead |
| Thieving | Coins (+ Gems) | Thieves' Guild |
| Keldagrim Consortium | Separate raw, processed, and special goods exchanges; trade 500 raw or 300 processed/special goods for 100 base units of another item in the same category; output increases by 10% per Consortium level (110 at level 1, 200 at level 10) | Keldagrim Expedition blueprint |
| Agility | Marks | Courier Post |
| Sailing | Cargo | Harbor |
| Hitpoints (all combat styles) | Bones | Barracks |
| Slayer | Basic Monster Parts from XP (+ bonus Basic Parts), using the normal XP-to-yield curve | Slayer Tower |
| Smithing / Firemaking | Ore -> Bars / Logs -> Charcoal | Forge |
| Construction | Logs -> Planks | Sawmill |
| Crafting / Fletching | Hides -> Leather / Logs -> Arrows | Workshop |
| Cooking | Fish (Grain fallback) -> Rations | Tavern |
| Herblore | Herbs -> Potions | Apothecary |
| Runecraft / Magic | Stone -> Runes / Runes -> Enchantments | Wizard Tower |
| Prayer | Bones -> Blessings | Temple |
| Boss KC (allowlist, tier 1/2/3) | Independent Basic / Rare / Epic rolls; a successful roll awards 100 / 200 / 400 parts by tier. Registered raids also give Artifacts | Trophy Hall increases part-drop odds |
| Epic Monster Parts + Artifacts (spent at every Altar level) | +3% all yields per level | Altar |
| Clues (by tier) | Curios + Coins; Easy+ fixed Monster Parts, Hard+ a 10% bonus batch (Elite averages a Tier 1 boss kill) | Treasury |

- Attack/Strength/Defence/Ranged are ignored: Hitpoints XP covers every combat style and avoids double counting.
- Processing without raw stock: the shortfall is stored as Labour for that skill (unlimited) and used automatically once raw materials arrive. A per-settlement Stock-tab toggle pauses only queued labour; new processing XP still uses available inputs, and resuming drains the queue. The pause setting persists per profile.

## Bounty rewards
- Every non-blueprint objective uses the same reward-family roll: 5/9 supplies, 2/9 yield boost, 2/9 monster-part bundle. Boss and delivery objectives have no reward-family override.
- Supply budgets are 400 units normally and 600 for delivery, divided between two or three distinct resources with equal packet-size probability. Common delivery supplies at Town Hall 1 pay 300 each for two resources or 200 each for three, before bonuses. Delivery supplies exclude the submitted resource; supplies retain a 10% chance to replace one entry with Basic Monster Parts.
- Town Hall adds 20% per level above 1 to supply budgets and scaled XP/Slayer/delivery requirements. Museum Camp adds 10% supply reward value per level. Amounts retain two-significant-digit rounding; boss/clue requirement formulas are unchanged.
- Non-blueprint rarity probabilities are normalized from weights 0.70/0.20/0.05: approximately 73.68% Common, 21.05% Rare, 5.26% Epic. Rare/Epic multiply resource rewards and boost duration by 2/4, and requirements by 1.5/2.5. Boost strength remains 2x.
- Monster bundles include Basic Parts, Rare Parts from Town Hall 3, and Epic Parts from Town Hall 5. Artifacts retain a separate Town Hall 6 gate and 2% chance per bundle. Bundle base quantities are unchanged and do not scale with Town Hall or Museum Camp.
- Newly eligible bounty blueprints take priority as Common rewards, chosen uniformly among eligible unowned blueprints. Only one blueprint bounty can be active; expedition-exclusive blueprints are excluded. Saved bounties are not rerolled by these generation changes.

## Buildings
- Tier A (Town Hall 1, no blueprint): Town Hall, Lumber Camp, Quarry, Fishing Dock, Farmstead, Barracks.
- Tier B (Town Hall 3 + blueprint): Tavern, Thieves' Guild, Courier Post, Hunter's Lodge, Keldagrim Consortium. The Consortium's blueprint is expedition-only.
- Tier C (Town Hall 4 + blueprint): Sawmill, Forge, Apothecary, Slayer Tower, Museum Camp (expedition: +10% bounty supply rewards/lvl).
- Tier D (Town Hall 5 + blueprint): Workshop, Temple, Harbor, Wizard Tower, Arceuus Library (expedition: +20% bounty boost duration/lvl).
- Tier E (Town Hall 6 + blueprint): Trophy Hall, Treasury, Jaltevas Pyramid (expedition: positive events +10% stronger/lvl).
- Tier F (Town Hall 8 + blueprint): Altar, Tower of Voices (expedition: +5% all yields/lvl).
- Wonder: Town Hall 10, every non-optional building at level 10, its blueprint, and its level 1 resource cost. The Wonder has 10 levels. Expedition buildings are optional; the Trophy Hall and Altar are required.
- Buildings go up to level 10 and cannot exceed the Town Hall level. Town Hall level n+1 needs n+2 other buildings at level n.
- Economy tuning values live in `engine/Balance` and the relevant catalogs/models and must be checked by the simulation test. Raids are the primary Artifact source; rare Town Hall 6+ bounty rewards can also grant one, allowing raid-free progression more slowly. Re-verify time-to-Wonder targets after reward/cost tuning.
- Simulation policy: five seeds pursue Town Hall prerequisites, then the remaining mandatory buildings through level 10 before the first Wonder. Expedition buildings marked optional are excluded. Pick the highest-level eligible prerequisite building, breaking ties by upgrade cost; once Town Hall 10 is reached, finish the lowest-level mandatory buildings first. Re-evaluate resource deficits each minute, recursively gather processing inputs, and complete bounties to obtain required blueprints. No fixed activity windows or trading. Bossing uses Tier 2 at 12 kills/hour; raids use Tier 3 at 3/hour and clues are Medium at 3/hour. Claim free completed rewards, but only spend materials on delivery bounties needed for blueprint progression. Keep configured timing assertions unchanged during rebalance. Run `./gradlew test --tests '*SettlementSimulationTest*BalancedPlayer*' -PsimulationOutput` for milestone and activity-hour diagnostics; resource-gap snapshots are not cumulative time spent blocked.

## Profile safety and release updates

- Schema 6 is the first supported release baseline. Pre-release migration code has been removed; old test profiles are not automatically converted or deleted. The config group and `state` key remain unchanged.
- A settlement is founded only when its primary record and pre-reset snapshot are absent. Existing empty, malformed, incompatible, or unsupported-version data blocks loading instead of creating a replacement. Supported profiles are not unconditionally rewritten on startup. Obsolete recovery keys are ignored and left untouched.
- Loading validates JSON structure, field preservation, enums, and progress values before accepting the state. It rejects duplicate fields and lossy conversions rather than silently dropping entries or resetting progress. Missing optional collections can use their normal empty defaults.
- Blocked profiles show a protected-state view with Retry, not gameplay or reset controls. Retry never discards unsaved state, automatically restores an older backup, or authorizes overwriting incompatible data.
- Writes require a writable profile session and compare the primary record with the last accepted value. An unexpected change blocks saving rather than guessing which progress should win. Failed saves retain their state and serialized candidate by account in memory; returning to that account offers Retry. Closing the process loses any data that could not be saved.
- Sidebar commands, reset/restore confirmations, and queued snapshots are bound to the displayed session. Old views cannot act on another account or a later session. Shutdown saves prepared snapshots rather than reading the mutable engine from the Swing thread.
- Healthy-profile resets require confirmation and a verified pre-reset snapshot under `state-before-reset`. Each reset replaces the previous restore point with current live progress before saving a fresh settlement; the warning explicitly states this replacement. If the subsequent primary write fails, the newly written snapshot remains.
- Restore Settlement is disabled without a valid snapshot. A confirmed restore swaps the snapshot with current live progress, including unsaved changes, so restoring again swaps back. Both records are validated and writes verified. A failed swap attempts checked rollback; if rollback cannot finish, saving pauses and Retry attempts recovery before saving live progress. Recovery data held only in memory is lost when the process exits.
- RuneLite controls disk persistence and cloud sync. Config setter/readback success confirms only in-memory acceptance. The comparison is not an atomic cross-device transaction, and the two-key restore swap is not crash-atomic; these guards do not guarantee recovery from crashes, disk failures, or cloud conflicts.
- Player-facing building names are Keldagrim Consortium and Jaltevas Pyramid. While no player saves are in use, their enum identifiers may be renamed with the display names; after release, changes to serialized enum names, saved fields, or progress semantics require a deliberate versioned conversion. Future balance-only changes need no schema bump. Keep objective identities stable, preserve earned buildings, blueprints, claimed expeditions, and compatible unfinished progress, and never lower saved objective progress when a threshold decreases.
- No speculative future migrations are included. A future conversion must work on a copy, validate all results, preserve the original in a protected backup before committing, and be safe to repeat. If conversion or backup cannot safely succeed, leave the primary record untouched and block loading.

## Implementation status
- The domain/economy engine, activity tracking, event/bounty/expedition systems, guarded profile persistence, and sidebar UI are implemented under `src/main/java`.
- Automated tests under `src/test/java` cover the engine and balance simulation, event scheduling, XP/chat tracking, profile safety, and persistence.
- The root `icon.png` is packaged as a classpath resource and used by the sidebar navigation button.

## Remaining release checks
- Run `./gradlew build` and review the plugin-hub checklist in [AGENTS.md](../AGENTS.md).
- Complete the manual in-game checks below; only a user can verify RuneLite behavior.

## Manual verification (in-game)
Profile safety: switch accounts/world types and confirm separate progress; disable/re-enable, relog, and restart to confirm persistence; switch accounts during reset and restore confirmations and confirm neither the new account nor a later session is changed. On a disposable test profile with a preserved copy, test invalid/unsupported saved JSON: the sidebar should show a protected state, Retry should leave the original and existing snapshot unchanged, and no reset/restore should be offered. Before the first reset, Restore should be disabled. Reset twice and confirm the second reset replaces the first restore point with the latest live progress. Restore twice and confirm it swaps back and forth; cancel either confirmation and verify nothing changes. If saving fails, check that gameplay pauses, Retry preserves pending progress, and no unrelated account is overwritten. These are user-operated checks; never automate game input.

Chop logs -> Logs rise; relog -> no credit for existing XP; Smithing XP -> Bars/Labour and Keldagrim progress; 100 Slayer XP -> 8 base Monster Parts before multipliers; boss part rolls award 100 / 200 / 400 by tier without changing odds; raid -> tiered parts plus Artifacts; complete and claim Keldagrim -> Keldagrim Consortium blueprint; Consortium exchange -> consumes 500 raw or 300 processed/special stock and grants 110 output at level 1 or 200 at level 10; Curios appear under Spoils and not in the Consortium exchange; Jaltevas Pyramid strengthens positive events; pause queued labour -> fresh processing XP still uses available inputs, resume drains queued work; late Altar upgrade -> Artifact cost; Hard/Elite/Master clue -> correct tier's bonus part roll; bounty -> rare part/Artifact rewards; agility lap -> nothing; finish a bounty objective -> click Claim, see the reward toast, and refill the slot; Deliver -> consumes stock on click; build Wonder -> requires every non-optional building at level 5, its blueprint and Artifacts; switch accounts -> separate settlements; restart client -> progress persists; Activities always has at least one event, sizes event cards to the active count, omits the empty boost row, pauses event time while logged out, and survives relog.
