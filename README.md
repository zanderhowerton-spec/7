# NPCLife – autonomous player-like behaviour for Citizens2 (Paper 1.21.11)

An *addon* for Citizens2 (it does not modify or replace Citizens). Adds a persistent `npclife` trait
to NPCs and a single scheduler that drives per-NPC "brains".

## Build
Requires JDK 21 and Maven.

    mvn package            # -> target/NPCLife-0.4.0.jar

No local JDK? Push this folder to a GitHub repo; the included workflow
(.github/workflows/build.yml) builds the jar and attaches it as a downloadable artifact.

If Maven can't resolve Citizens, open https://maven.citizensnpcs.co/repo/net/citizensnpcs/citizens-main/
and set `<citizens.version>` in pom.xml to a version that exists there.

## Install
1. Install Citizens (a build supporting 1.21.11) and put NPCLife-0.4.0.jar in `plugins/`.
2. Restart. Look for `NPCLife enabled. Head control: ...` in the console.

## 60-second test
    /npc create Bob            (spawn point becomes Bob's wander centre)
    /npcx behavior set explorer
    /npcx behavior enable
    /npcx debug                (shows what he is doing right now)

20 NPCs, all different:

    /npcx select all
    /npcx behavior randomize
    /npcx behavior enable

Other examples:

    /npcx behavior probability jump 30
    /npcx behavior cooldown jump 5-15
    /npcx behavior toggle sneak_spam off
    /npcx behavior save mycharacter       (then: /npcx behavior set mycharacter)
    /npcx wander radius 60
    /npcx wander center                   (reset centre to where the NPC stands)
    /npcx look player Steve   |   /npcx look rotate 90 10   |   /npcx look 100 64 200   |   /npcx look stop
    /npcx jump toggle  |  /npcx sprint toggle  |  /npcx sneak spam 5
    /npcx hotbar DIAMOND_SWORD,APPLE,TORCH    (SWAP_ITEM cycles these)
    /npcx interact   /npcx wave   /npcx swing

Probabilities are percent chances rolled at every "think" (each NPC thinks every `think-min..think-max`
seconds, default 0.6-1.8 s). Each roll is independent, so timing is never periodic.

## How it works
* One repeating task for all NPCs. Brains with no real player within `active-range` are skipped.
* Actions occupy channels: HEAD, MOVE, JUMP, SPRINT, SNEAK, HAND. Different channels run in parallel
  (walk + look + jump). A busy channel can only be taken by a higher-priority action (FLEE > APPROACH > WALK > IDLE).
* Every action has a duration/timeout; walking has stuck detection; failed walks get a longer cooldown.
* Wander boundary: targets are clamped inside the radius, checked again every second; an NPC that ends up outside
  is walked back (teleported to the centre after 15 s if it cannot). `max-wander-radius` (default 100) is a hard cap.
  Don't also enable Citizens' own Wander on the same NPC; NPCLife's WALK action replaces it.

## New in 0.4.0 - army tools + teams (combat AI comes in the next stages)
    /npcx team create red            /npcx team create blue
    /npcx team add red me            (puts YOU in team red)
    /npcx team hostile red blue      (red and blue are enemies)
    /npcx army kit save knight       (copies your armor, hotbar, offhand and inventory)
    /npcx army spawn 50 blue kit=knight mix=20/50/30 formation=grid
    /npcx army select                (selects the whole army; every /npcx command then applies to all of them)
    /npcx army respawn               (brings back every dead army NPC with its kit)
Weighted kits: kit=knight:60,archer:40.  Names come from plugins/NPCLife/names.txt (one username per line; those NPCs also get that
player's skin), then generated names. Skill (beginner/average/expert) and a combat style are stored on every NPC for the combat stages.

## New in 0.3.0
* Interaction is limited to: barrels, chests, copper chests, ender chests, shulker boxes (lid opens/closes) and hoppers,
  brewing stands, anvils, crafting/smithing tables, looms, furnaces, smokers, blast furnaces, enchanting tables (look + arm swing).
* `shopkeeper`: much calmer (rare crouches, no jumping), opens containers far more often (`work-bias`).
* `customer` (`/npcx behavior set customer`): roams within 40 blocks, finds shopkeepers and walks to the side of the shopkeeper
  with the most air blocks; if no side works it browses near the shop. Action: `browse`.
* `/npcx sprintjump`: jumps only while actually sprinting, one hop per ~1 s.
* Unlimited food for EVERY player NPC (also existing ones): hungry NPCs swap bread/steak/carrot/golden carrot into the main hand,
  eat for 40 ticks (the flag `/npc useitem` sets), then swap back. Walking continues at 20% speed (navigation speed, no potion
  effect, so no swirl particles); 60% of meals include continuous hopping. `/npcx eat` forces it. Config: auto-eat, eat-below,
  hunger-rate, eat-speed-multiplier, eat-jump-chance.
* NPCs already using `/npcx behavior set shopkeeper` keep their old saved numbers: run the command again to pick up the new ones.

## New in 0.2.0
    /npcx sprintjump [on|off]        sprint + jump continuously while travelling around
    /npcx sneak spam [sec]           ~3 crouches/second, looking at the nearest player
    /npcx behavior set shopkeeper    stands mostly still, small random head moves/crouches, GREET = crouch-bows at
                                     nearby players, uses chests/crafting tables/furnaces/anvils etc. (radius 6)
    /npcx use continuous|once|stop   hold right-click on the held item (eat/drink/shield/bow pose); /npcx hold <item> first
    /npcx group set <name>|clear|list   group members seek each other out (approach/follow/stop beside)
    /npcx wander home <blocks|off|default>

Block interaction now keeps the NPC staring at the block until it has closed/restored it again.

Return-home rule (all NPCs, also pre-existing ones): more than `return-distance` (40) blocks from the spawn point for
`return-wait-seconds` (20) -> walks back in hops; not back after `return-timeout-seconds` (120) -> teleported.
Pre-existing NPCs get the trait at startup; their "spawn point" is where they stand when the plugin first sees them
(Citizens doesn't record the original spawn spot). Because of this rule, autonomous wandering is clamped to
min(wander radius, return distance - 1); raise it with `/npcx wander home 100` or turn it off with `home off`.

## Sequences (routines / recorder)
    /npcx sequence record intro      (you walk/look/jump/sneak/sprint/swing/switch items)  then  /npcx sequence save
    /npcx sequence play intro loop combine     ('combine' keeps autonomous behaviour running alongside)
    /npcx sequence create shop ; /npcx sequence add shop WAIT 3-8 ; ... ; /npcx sequence stop
Steps (also editable in plugins/NPCLife/sequences.yml; `#` = comment, `?NN` = NN% chance, `IFNEAR` = only if a player is near):
    WAIT 2-5 | WALK dx dy dz (relative to where play started) | WALKABS x y z | LOOK yaw pitch | LOOKAT x y z | LOOKPLAYER |
    LOOKFREE | JUMP | HOP sec | SPRINT on/off | SNEAK on/off | SPAM sec | SWING | WAVE | INTERACT | HOLD ITEM | USE on/off
Example village routine: WAIT 5-20 / LOOK 90 0 / ?50 JUMP / WALK 8 0 3 / INTERACT / WAIT 2-4 / WALK 0 0 0
Sequences persist per NPC and resume after restart. Return-home is paused while a sequence plays.

## Honest status / limits
Not implemented: item drop/pickup and inventory-GUI moves (Citizens NPCs have no real client/GUI), strafe/walk-backwards,
formation movement. Crafting tables/furnaces etc. are look + arm swing only, no real GUI.

Things that depend on Citizens internals and may need a tweak per Citizens build:
* head-only rotation while walking (uses Citizens' NMS.setHeadYaw via reflection; `/npcx debug` reports availability),
* sneak (Citizens' SneakTrait) and sprint flag visibility,
* jumping via velocity (Citizens' own pathfinder also auto-jumps up blocks).

Interactions: doors/trapdoors/gates/levers/buttons are changed by setting block state (and restored afterwards);
chest/barrel lids are cosmetic. These do NOT fire PlayerInteractEvent, so protection plugins won't see them, and
no items are moved. Set `modify-world: false` in config.yml to disable the block-state changes.
