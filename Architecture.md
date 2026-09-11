# Ling Gacha Addon - Technical Architecture Specification

This document provides a comprehensive architectural and engineering overview of the **Ling Gacha Addon** mod for Minecraft 1.21.1 (NeoForge). It covers the component topology, network protocol, probabilistic algorithms, client rendering pipelines, security validation, and persistence strategies.

---

## 1. System Overview & Core Topology

Ling Gacha Addon adopts a strict **Server-Authoritative, Thin-Client Pipeline**. All state mutations, currency balance evaluations, random number generations, pity counters, and inventory deliveries are computed exclusively on the logical server thread. The client environment functions as an interactive visual interface and rendering engine.

```
+-----------------------------------------------------------------------------------+
|                                CLIENT SUBSYSTEM                                   |
|                                                                                   |
|  +------------------------+  +-------------------------+  +--------------------+  |
|  |      GachaScreen       |  |    GachaRevealScreen    |  |    Modal Dialogs   |  |
|  | (Full-Screen Canvas)   |  | (Pull Reveal Matrix)    |  | (Pool/Mail/History)|  |
|  +-----------+------------+  +------------+------------+  +---------+----------+  |
|              |                            |                         |             |
|              +--------------------+       |       +-----------------+             |
|                                   |       |       |                               |
|                                   v       v       v                               |
|                           +-------------------------------+                       |
|                           |      AnimationManager         |                       |
|                           |  - GifDecoder (GIF89a Stream) |                       |
|                           |  - AnimatedTexture (OpenGL)   |                       |
|                           |  - AfmaAnimation (JSON Spec)  |                       |
|                           +---------------+---------------+                       |
+-------------------------------------------|---------------------------------------+
                                            |
                         Network Packets    | CustomPacketPayload via PacketDistributor
                         (JSON / Records)   |
                                            v
+-----------------------------------------------------------------------------------+
|                                SERVER SUBSYSTEM                                   |
|                                                                                   |
|                           +-------------------------------+                       |
|                           |       GachaNet Router         |                       |
|                           |  (Permission & Route Guard)   |                       |
|                           +---------------+---------------+                       |
|                                           |                                       |
|                  +------------------------+------------------------+              |
|                  |                                                 |              |
|                  v                                                 v              |
|  +-------------------------------+                 +---------------------------+  |
|  |         CoinsService          |                 |       GachaManager        |  |
|  | - ling_q_shop Balance Bridge  |                 | - Pity / 50-50 State Mach |  |
|  | - FTB Teams Balance Multiplex |                 | - Cumulative Weighted RNG |  |
|  +-------------------------------+                 | - Pool Item Selection     |  |
|                                                    +-------------+-------------+  |
|                                                                  |                |
|                                                                  v                |
|                                                    +---------------------------+  |
|                                                    |      PlayerGachaData      |  |
|                                                    | - Isolated Banner Records |  |
|                                                    | - Mailbox Delivery Engine |  |
|                                                    | - JSON Disk Persistence   |  |
|                                                    +---------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 2. Component Breakdown & Class Responsibilities

### 2.1 Core & Lifecycle
- `com.holysweet.linggacha.LingGachaMod`: Entry point registered to NeoForge mod bus. Registers items, commands, payloads, and network channels.

### 2.2 Server & Gacha Engine
- `com.holysweet.linggacha.gacha.GachaManager`: Singleton controller orchestrating pull workflows, pool loading from disk, pity evaluation, and broadcast announcements.
- `com.holysweet.linggacha.gacha.GachaBanner`: Data record representing an active banner (ID, title, cost, discount percentage, banner type, item pool).
- `com.holysweet.linggacha.gacha.GachaItemEntry`: Single item candidate in a banner pool (Item ID, count, rarity, weight, rate-up flag, SNBT tag string).
- `com.holysweet.linggacha.gacha.GachaRarity`: Enumeration defining item star tiers (`THREE_STAR`, `FOUR_STAR`, `FIVE_STAR`), color codes, and display properties.
- `com.holysweet.linggacha.gacha.PlayerGachaData`: Per-player runtime state manager holding isolated pity dictionaries, pull histories, Afterglow Corals, and reward mailboxes. Serializes to `config/ling_gacha/playerdata/<UUID>.json`.
- `com.holysweet.linggacha.gacha.CoinsService`: Abstracted currency bridge interfacing with `ling_q_shop` balances and FTB Teams shared accounts.

### 2.3 Network Communication Layer
- `com.holysweet.linggacha.network.GachaNet`: Central registry of all NeoForge `CustomPacketPayload` packet types, payload codecs, and server execution handlers.
- Payloads:
  - `RequestOpenGachaPayload` / `SyncBannerDataPayload`
  - `ConvenePullPayload` / `ConveneResultPayload`
  - `RequestPullHistoryPayload` / `SyncPullHistoryPayload`
  - `RequestMailboxPayload` / `SyncMailboxPayload` / `ClaimMailboxItemPayload`
  - `AdminUpdateBannerPayload` / `AdminDeleteBannerPayload` / `AdminAddGachaItemPayload` / `AdminUpdateGachaItemPayload` / `AdminRemoveGachaItemPayload`

### 2.4 Client UI & Media Rendering Subsystem
- `com.holysweet.linggacha.client.screen.GachaScreen`: Primary full-screen gacha hub rendering live 3D preview item showcases, banner selection sidebars, and admin controls.
- `com.holysweet.linggacha.client.screen.GachaRevealScreen`: Cinematic pull result reveal screen featuring soundwave waveforms, 1x / 10x cards, and custom `.gif`/`.afma` animation playback.
- `com.holysweet.linggacha.client.screen.BannerSettingsModal`: In-game admin modal for editing banner pricing, metadata, and background asset references.
- `com.holysweet.linggacha.client.screen.ItemPoolEditModal`: Admin modal for listing, adding, and removing pool items.
- `com.holysweet.linggacha.client.screen.ItemAddEditModal`: Admin modal for editing weights, star ratings, and NBT attributes of items.
- `com.holysweet.linggacha.client.screen.GachaHistoryModal`: Paginated modal displaying isolated pull logs per banner or across all pools.
- `com.holysweet.linggacha.client.screen.GachaMailboxModal`: Interactive private reward claim center with selective and bulk claiming.
- `com.holysweet.linggacha.client.InventoryGachaButton`: Event subscriber attaching the clover icon launcher button into the ESC Pause Menu (`PauseScreen`).

### 2.5 Media Engine (`client.animation`)
- `GifDecoder`: Standalone pure Java binary decoder parsing GIF89a stream structures without external dependencies.
- `AnimatedTexture`: Allocates and updates runtime OpenGL `DynamicTexture` instances in Minecraft's `TextureManager`.
- `AfmaAnimation`: JSON descriptor parser and sequence runner for discrete sprite frame sequences.
- `AnimationManager`: Central asset loader and memory manager caching background textures and pull animations loaded from local disk paths.

---

## 3. Mathematical & Algorithmic Specifications

### 3.1 Tier Selection & Pity Mathematics

When a pull request is dispatched for player $P$ on banner $B$, the system evaluates rarity tier determination sequentially:

```
[Start Pull]
     │
     ▼
[Evaluate 5-Star Probability P5(C5)]
     ├─ Roll < P5(C5) ──► [Select 5-Star Tier] ──► [Reset C5 = 0, C4++]
     │
     ▼
[Evaluate 4-Star Probability P4(C4)]
     ├─ Roll < P4(C4) ──► [Select 4-Star Tier] ──► [Reset C4 = 0, C5++]
     │
     ▼
[Fallback to 3-Star Tier] ───────────────────────► [Increment C5++, C4++]
```

#### 5-Star Rarity Calculation
- Let $C_5 \in [0, 80]$ denote the current consecutive pulls without a 5-Star reward on banner $B$.
- The base rate is $P_{\text{base}} = 0.008$ ($0.8\%$).
- The soft pity threshold begins at pull index 65:
  $$P_5(C_5) = \begin{cases} 
  0.008 & \text{if } C_5 < 65 \\ 
  0.008 + (C_5 - 64) \times 0.0585 & \text{if } 65 \le C_5 < 80 \\ 
  1.0 & \text{if } C_5 \ge 80 
  \end{cases}$$

#### 4-Star Rarity Calculation
- Let $C_4 \in [0, 10]$ denote the current consecutive pulls without a 4-Star reward.
- The base rate is $P_{4,\text{base}} = 0.060$ ($6.0\%$).
  $$P_4(C_4) = \begin{cases} 
  0.060 & \text{if } C_4 < 10 \\ 
  1.0 & \text{if } C_4 \ge 10 
  \end{cases}$$

### 3.2 50/50 Guarantee Finite State Machine

For banners configured with type `FEATURED_RESONATOR`:

```
                 +---------------------------+
                 | bannerGuaranteed == false |
                 +-------------+-------------+
                               |
               5-Star Rolled   | (50% RNG Check)
                               v
            +------------------+------------------+
            |                                     |
    [Win 50/50 (50%)]                     [Lose 50/50 (50%)]
            |                                     |
            v                                     v
+-----------------------+             +-----------------------+
| Featured Rate-Up Item |             | Standard 5-Star Item  |
| State: UNCHANGED      |             | State: GUARANTEED     |
| (Guaranteed = false)  |             | (Guaranteed = true)   |
+-----------------------+             +-----------+-----------+
                                                  |
                                  Next 5-Star Hit | (100% Rate-Up)
                                                  v
                                      +-----------------------+
                                      | Featured Rate-Up Item |
                                      | State: RESET          |
                                      | (Guaranteed = false)  |
                                      +-----------------------+
```

### 3.3 Weighted Cumulative Item Selection

Once a tier $T \in \{3, 4, 5\}$ is resolved, candidate items are sampled proportionally to their weight:
$$P(\text{item}_k) = \frac{W_k}{\sum_{j=1}^{M} W_j}$$
Where $M$ is the number of items in tier $T$ matching the rate-up or standard condition, and $W_k$ is the integer weight configured in `GachaItemEntry`.

---

## 4. Mailbox & Delivery Lifecycle

To prevent dropped item entities or inventory overflow losses, Ling Gacha Addon implements a decoupled Mailbox storage pipeline:

1. **Pull Execution**: When items are rolled, they are appended to the player's `mailbox` array in `PlayerGachaData`.
2. **Server-Side Inventory Check**: When a claim request (`ClaimMailboxItemPayload`) is received:
   - The server inspects `player.getInventory().getFreeSlot()`.
   - If slots are available, the item is constructed (restoring full SNBT/components) and added to the player's inventory via `player.getInventory().add(stack)`.
   - The corresponding `mailId` entry is removed from `PlayerGachaData` and disk is saved.
   - If inventory is completely full, the item remains untouched in the mailbox and an alert is sent.

---

## 5. Media & Dynamic Texture Subsystem

### 5.1 GIF89a Streaming Decoding
`GifDecoder.java` reads the raw byte stream of `.gif` files from `config/ling_gacha/backgrounds/` or `config/ling_gacha/animations/`:
- Parses header blocks, Logical Screen Descriptors, and Global Color Tables.
- Processes Graphic Control Extension blocks for delay time (converted to milliseconds) and transparent color indices.
- Unpacks LZW compressed image descriptors into individual `BufferedImage` frames.

### 5.2 GPU Texture Management (`AnimatedTexture.java`)
- For each decoded frame, a `NativeImage` buffer is allocated using Minecraft's native memory allocator.
- A unique `ResourceLocation` is generated (`ling_gacha_dynamic:anim_<hash>_<frame>`) and bound to a `DynamicTexture` registered in `Minecraft.getInstance().getTextureManager()`.
- On render tick, the elapsed time determines the active frame index based on cumulative frame delays.
- Upon screen disposal (`onClose()`), `AnimatedTexture.close()` iterates through all registered frames, invoking `DynamicTexture.close()` and freeing GPU memory to guarantee zero VRAM leaks.

### 5.3 3D Item Floating Matrix
The 3D showcase on `GachaScreen` computes real-time transformation matrices:
- **Sinusoidal Bobbing**: $Y(t) = \sin(t \times 2.2) \times 3.5\text{ px}$
- **Continuous Y-Axis Rotation**: $\theta(t) = (t \times 45.0^\circ) \bmod 360.0^\circ$
- **Fixed Forward Tilt**: $16.0^\circ$ around the X-axis for isometric depth perception.
- Rendered via `ItemRenderer.renderStatic()` under full light level `15728880`.

---

## 6. Security & Permission Architecture

All administrative network operations are guarded by the server-side validator in `GachaNet.canAdmin(ServerPlayer player)`:

```java
public static boolean canAdmin(ServerPlayer player) {
    if (player == null) return false;
    return player.hasPermissions(2) 
        || player.isCreative() 
        || player.getServer().isSingleplayerOwner(player.getGameProfile());
}
```

- **Permission Matrix**:
  - `hasPermissions(2)`: Server Operators with level 2 or higher.
  - `isCreative()`: Players currently in Creative game mode.
  - `isSingleplayerOwner()`: Integrated singleplayer hosts (even if "Allow Cheats" is disabled).
- Unauthenticated client packets attempting administrative mutations are rejected immediately and logged to server console.

---

## 7. Storage Specifications

### File Paths
- **Banner Configuration**: `config/ling_gacha/banners.json`
- **Player Data**: `config/ling_gacha/playerdata/<UUID>.json`
- **Background Media**: `config/ling_gacha/backgrounds/`
- **Pull Animation Media**: `config/ling_gacha/animations/`

### Thread Safety & Disk Writing
Data persistence is serialized via Google `Gson` with pretty-printing enabled. Writes occur synchronously on state mutations during pull operations, admin saves, and mailbox claims, ensuring zero data loss upon abrupt server shutdowns.
