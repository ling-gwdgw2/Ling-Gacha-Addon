# Ling Gacha Addon (Convene System)

Ling Gacha Addon is an expansion mod for ling_q_shop (Minecraft 1.21.1 NeoForge) that brings a full-fledged gacha convene system inspired by Wuthering Waves into Minecraft.

---

## Key Features

### 1. Banner Types
- **Featured Resonator (Character Banner)**:
  - 50/50 Rule: 50% chance for the featured 5-Star item. If lost, the next 5-Star is 100% guaranteed to be the featured rate-up item.
- **Featured Weapon (Weapon Banner)**:
  - 100% Guaranteed: When pulling a 5-Star item, it is always the featured rate-up weapon without any off-banner loss.
- **Novice Convene (Beginner Banner)**:
  - 20% discount on 10-pulls (costs 8 pulls worth of Primogems) with a guaranteed 5-Star within the first 50 pulls.
- **Standard Convene (Permanent Banner)**:
  - Standard pool containing 5-Star, 4-Star, and 3-Star rewards.

### 2. Rates and Pity Mechanics
- **5-Star (Legendary)**: Base rate of 0.8%. Soft pity starts at pulls 65-79 (rate scales up sharply). Hard pity guarantees a 5-Star at pull 80.
- **4-Star (Epic)**: Base rate of 6.0%. Guaranteed at least one 4-Star item every 10 pulls.
- **3-Star (Supplies)**: Base rate of 93.2%.
- **Afterglow Coral Cashback**: Pulling items awards Afterglow Corals (15 for 5-Star, 3 for 4-Star, 1 for 3-Star).
- **Per-Banner Isolated Pity**: Each banner tracks its own independent 5-Star pity, 4-Star pity, guaranteed state, and pull count.

### 3. Gacha Mailbox / Reward Storage
- **Inventory Overflow Protection**: When pulling gacha, won items are automatically stored in the player's private Gacha Mailbox.
- **Selective & Bulk Claim**: Players can inspect rewards, view pull dates, and claim items individually or using "Claim All".
- **Capacity Safe**: Items will never drop on the ground or be lost if the player's inventory is full.

### 4. Custom Media & Animation Engine
- **Supported Formats**: Supports animated GIF (.gif), sprite frame sequence (.afma), PNG (.png), and JPEG (.jpg).
- **Banner Backgrounds**: Place background files in `config/ling_gacha/backgrounds/` or use resource locations (e.g. `ling_gacha:textures/gui/banners/featured_character.png`).
- **Custom Pull Animations**: Place pull animation files in `config/ling_gacha/animations/`:
  - `pull_5star.gif` / `pull_5star.afma` - Plays when pulling a 5-Star item.
  - `pull_4star.gif` / `pull_4star.afma` - Plays when pulling a 4-Star item.
  - `pull_3star.gif` / `pull_3star.afma` - Plays when pulling a 3-Star item.
  - `pull.gif` / `pull.afma` - Default pull animation.
  - Built-in frequency soundwave spectrogram fallback if no custom animation files are present.

### 5. In-Game Admin Editor
- **Permission**: Requires OP Level 2, Creative Mode, or Singleplayer World Host.
- **Edit Mode**: Toggle `[Edit Mode]` directly inside the Gacha UI to:
  - Create new banners and customize titles, subtitles, pull costs, discounts, and backgrounds.
  - Open `[Pool]` to edit item weights, custom display names, rate-up status, and rarity tiers.
  - Click `[+ Add Hand Item]` to immediately register the held item in hand with full SNBT data into the banner pool.

---

## Technical Architecture & System Specification

### 1. Architecture Overview
Ling Gacha Addon utilizes a strict **Client-Server Authoritative Architecture** built on the NeoForge 1.21.1 network pipeline. All mathematical calculations (RNG, pity tracking, currency deduction, mailbox state mutations) execute solely on the dedicated logical server, while the client acts as a high-fidelity visual and audio rendering frontend.

```
[Client GUI / Input Layer]
         │
         ▼ (Custom CustomPacketPayload via PacketDistributor)
[Network Serialization Layer (GachaNet)]
         │
         ▼ (Server Execution Thread)
[Economy Validation (CoinsService / FTB Teams)]
         │
         ▼ (Deduction Succeeded)
[Gacha Core Engine (GachaManager)]
  ├── Pity & 50/50 State Machine
  ├── Weighted Cumulative RNG Tier Selector
  └── Item Pool Selector (with SNBT Restoration)
         │
         ▼ (State Mutation)
[Persistence Layer (PlayerGachaData JSON Engine)]
         │
         ▼ (Network Response)
[Client Screen Pipeline (GachaRevealScreen / AnimationManager)]
  ├── OpenGL DynamicTexture Frame Uploads
  └── 3D Item Renderer & Particle Matrix
```

---

### 2. Network Protocol & Payload Matrix

All communication between client and server is handled via typed `CustomPacketPayload` records registered in `GachaNet.java`:

| Payload ID | Direction | Purpose & Data Fields |
| :--- | :--- | :--- |
| `request_open_gacha` | Client -> Server | Requests synchronized banner lists, active pricing, and current currency balance. |
| `sync_banner_data` | Server -> Client | Sends all active banners, item pools, costs, discounts, and the player's mailbox pending count. |
| `convene_pull` | Client -> Server | Dispatches convene request (`bannerId`, `pullCount`: 1 or 10). |
| `convene_result` | Server -> Client | Transmits roll results (`prizes`, `highestStars`, `coralsAwarded`, `currencyRemaining`). |
| `request_pull_history` | Client -> Server | Requests historical pull records for a specific banner ID or `ALL`. |
| `sync_pull_history` | Server -> Client | Returns paginated pull records (`bannerId`, `itemId`, `stars`, `pity`, `timestamp`). |
| `request_mailbox` | Client -> Server | Queries the player's pending reward mailbox list. |
| `sync_mailbox` | Server -> Client | Synchronizes mailbox item list with full metadata, custom names, and SNBT. |
| `claim_mailbox_item` | Client -> Server | Requests claim of a specific `mailId` or `ALL` into player inventory. |
| `admin_update_banner` | Client -> Server | Administrative banner configuration update (Title, Cost, Discount, Background). |
| `admin_delete_banner` | Client -> Server | Administrative banner removal. |
| `admin_add_gacha_item` | Client -> Server | Adds a new item entry to a banner pool with weights and SNBT. |
| `admin_update_gacha_item`| Client -> Server | Updates an existing item entry's rarity, weight, name, or count. |
| `admin_remove_gacha_item`| Client -> Server | Deletes an item entry from a banner pool. |

---

### 3. Probability & Pity Algorithm Specification

The convene RNG system implements a deterministic, multi-stage tier resolution algorithm in `GachaManager.java`:

#### Tier Determination Pipeline
1. **5-Star Pity Check**:
   - Base 5-Star Probability: $P_5 = 0.008$ ($0.8\%$).
   - Current 5-Star Pity counter: $C_5 \in [0, 80]$.
   - Soft Pity Range: $C_5 \ge 65$. Probability scales linearly:
     $$P_5(C_5) = 0.008 + (C_5 - 64) \times 0.0585$$
   - Hard Pity: When $C_5 = 80$, $P_5(80) = 1.0$ ($100\%$).
2. **4-Star Pity Check** (if 5-Star not hit):
   - Base 4-Star Probability: $P_4 = 0.060$ ($6.0\%$).
   - Current 4-Star Pity counter: $C_4 \in [0, 10]$.
   - Hard Pity: When $C_4 = 10$, $P_4(10) = 1.0$ ($100\%$).
3. **3-Star Fallback**:
   - If neither 5-Star nor 4-Star triggers, 3-Star supplies pool is selected ($P_3 \approx 93.2\%$).

#### 50/50 & Guarantee State Machine
- For `FEATURED_RESONATOR` banners:
  - If a 5-Star is rolled and `bannerGuaranteed == false`, roll random boolean ($50\%$ chance).
  - If won: Item is chosen from rate-up 5-Star pool; `bannerGuaranteed` remains `false`.
  - If lost: Item is chosen from standard 5-Star pool; `bannerGuaranteed` becomes `true`.
  - If `bannerGuaranteed == true`: Next 5-Star is 100% forced rate-up; state resets to `false`.
- For `FEATURED_WEAPON` banners:
  - 100% Rate-up guarantee; no 50/50 loss mechanism.

#### Weighted Item Selection
Once a tier is selected, items in that tier are chosen using cumulative weight sampling:
$$P(item_i) = \frac{W_i}{\sum_{j=1}^{N} W_j}$$
Where $W_i$ is the configured weight integer of item $i$.

---

### 4. Client Animation & Media Rendering Pipeline

The client features a native media engine designed to bypass standard Minecraft texture atlas restrictions:

- **Native GIF Streaming Decoder (`GifDecoder.java`)**:
  - Direct binary stream parsing of GIF89a specifications (Logical Screen Descriptor, Global/Local Color Tables, Graphics Control Extensions, and LZW image data).
  - Extracts per-frame disposal methods, transparency indices, and millisecond frame delays.
- **Dynamic OpenGL Texture Engine (`AnimatedTexture.java`)**:
  - Converts unpacked `BufferedImage` frames into `NativeImage` byte buffers.
  - Dynamically binds and registers textures into Minecraft's `TextureManager` as runtime `DynamicTexture` objects.
  - Automatically unloads and releases OpenGL texture memory on modal close to eliminate VRAM leakage.
- **AFMA Frame Sequence Descriptor (`AfmaAnimation.java`)**:
  - Parses JSON descriptor files containing custom frame rates, loop flags, and discrete sprite frame sequences.
- **Real-Time 3D Showcase Matrix**:
  - Renders rotating 3D preview item models with bobbing sinusoidal oscillations:
    $$Y_{offset} = \sin(t \times 2.2) \times 3.5\text{ px}$$
    $$\theta_{rotation} = (t \times 45.0^\circ) \bmod 360.0^\circ$$
  - Rendered via `ItemRenderer.renderStatic()` with `ItemDisplayContext.FIXED` under full ambient illumination (`15728880`).

---

### 5. Economy & Currency Integration

The mod connects directly to `ling_q_shop`'s balance manager through `CoinsService.java`:
- **Team Balance Awareness**:
  - Queries `FTB Teams` API if present. If a player belongs to a team, the team's shared account is checked and debited.
  - If FTB Teams is absent or the player is solo, their individual player account is charged.
- **Atomic Balance Deduction**:
  - Pre-flight balance checks prevent partial pulls. Currency is deducted atomically before any RNG calculation or mailbox insertion occurs.

---

### 6. Storage & Serialization Schema

#### Banner Definitions (`config/ling_gacha/banners.json`)
```json
[
  {
    "id": "featured_character",
    "title": "Vermillion Flight",
    "subtitle": "Featured 5-Star Resonator & Gear",
    "type": "FEATURED_RESONATOR",
    "cost": 160,
    "discountPercent": 0,
    "previewItem": "minecraft:netherite_sword",
    "backgroundImage": "ling_gacha:textures/gui/banners/featured_character.png",
    "items": [
      {
        "itemId": "minecraft:netherite_sword",
        "count": 1,
        "stars": 5,
        "customName": "Blazing Sunblade",
        "weight": 10,
        "isRateUp": true,
        "snbt": "{display:{Lore:['\"A legendary blade\"']}}"
      }
    ]
  }
]
```

#### Player Data Schema (`config/ling_gacha/playerdata/<UUID>.json`)
```json
{
  "total": 120,
  "corals": 24,
  "bannerPity5": {
    "featured_character": 42,
    "featured_weapon": 10
  },
  "bannerPity4": {
    "featured_character": 4,
    "featured_weapon": 8
  },
  "bannerGuaranteed": {
    "featured_character": true
  },
  "bannerPulls": {
    "featured_character": 90,
    "featured_weapon": 30
  },
  "history": [
    {
      "banner": "featured_character",
      "item": "minecraft:netherite_sword",
      "name": "Blazing Sunblade",
      "stars": 5,
      "time": 1724458510000,
      "pity": 72
    }
  ],
  "mailbox": [
    {
      "mailId": "c4b1d6f2-3e5a-4b9d-a8e1-9c8f1d2e3a4b",
      "banner": "featured_character",
      "item": "minecraft:netherite_sword",
      "count": 1,
      "stars": 5,
      "name": "Blazing Sunblade",
      "snbt": null,
      "time": 1724458510000
    }
  ]
}
```

---

## How to Use

### 1. Opening the Gacha Screen
- Command: `/convene` or `/gacha`
- ESC Pause Menu: Click the clover icon button on the right side of the Game Menu.
- Convene Tide Item: Right-click the `ling_gacha:convene_tide` token item.

### 2. Configuration & Data Storage
- **Banner Configuration**: `config/ling_gacha/banners.json`
  - Stores all banner definitions, item pools, costs, discounts, and background textures.
- **Player Data**: `config/ling_gacha/playerdata/<Player_UUID>.json`
  - Secure server-side storage for player pity counts, pull history, Afterglow Corals, and Mailbox items.
- **Reload Command**: `/convene reload` or `/gacha reload` (Admin only).
