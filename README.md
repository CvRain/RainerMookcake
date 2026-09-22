# Mooncake Overflow

> **347 Variants, None of Them Good.**
>
> 月饼溢出 —— 把中秋月饼塞进《我的世界》，然后把它氧化、涂蜡、切制、切开四份、再拼回去。

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1-62b47a)](#环境要求)
[![Forge](https://img.shields.io/badge/Forge-62.0.9-dfb469)](#环境要求)
[![License](https://img.shields.io/badge/License-MIT-blue)](#许可)

---

## 目录

- [这是什么](#这是什么)
- [核心设计：正交轴](#核心设计正交轴)
- [阶段一：月饼本体](#阶段一月饼本体已实现)
- [开发状态](#开发状态)
- [环境要求](#环境要求)
- [构建](#构建)
- [运行](#运行)
- [Linux / Wayland 已知问题](#linux--wayland-已知问题)
- [目录结构](#目录结构)
- [性能原则](#性能原则)
- [资源与命名约定](#资源与命名约定)
- [文档](#文档)
- [许可](#许可)

---

## 这是什么

一个搞怪向的 Minecraft Forge 模组，主题是**月饼**。

灵感来自 MC 圈那个经典段子：

> 你说得对，竖半砖的加入会带来无数种变体，会对内存和帧率带来影响……
> 但是铜块、斑驳的铜块、锈蚀的铜块、氧化的铜块、涂蜡的铜块、涂蜡的斑驳铜块……
> （下略 80 余种，最后以"不会"结尾）

既然铜块能有 300 多种变体，**月饼凭什么不能**。

玩法核心是把月饼做成一套**正交轴**，让名字、贴图和效果自动组合出来；
再叠加中秋神话（玉兔、嫦娥、后羿）和一点元游戏式的自嘲。
最终形态是一个"看起来在认真做月饼，实际在认真玩梗"的模组。

## 核心设计：正交轴

| 轴 | 取值 | 作用 |
|---|---|---|
| **馅料** | 可可豆 / 南瓜 / 莲蓉 / 五仁 / 豆沙 / … | 基础食用效果 |
| **氧化度** | 铜 / 斑驳 / 锈蚀 / 氧化 | 影响硬度（扣血量）与饱食度 |
| **涂蜡** | 是 / 否 | 蜜脾右键，锁死氧化度 |
| **加工形态** | 面团 / 生胚 / 烤熟 / 切制 / 竖半砖 / 月饼块 / 台阶 / 楼梯 / … | 复刻铜块全家桶 |
| **象限** | 1/4 ～ 4/4，每象限独立记录馅料与氧化度 | 切开再拼合 |
| **内容物** | 任意物品（`DataComponent`） | 月饼是容器 |

名字由轴组合**自动生成**：

```
涂蜡的斑驳的切制莲蓉半月月饼
氧化五仁竖半砖月饼
```

轴一多，名字长度就会指数增长 —— **放任它溢出 tooltip 宽度**，并给一个成就「名称溢出」。

关键实现原则：**轴走数据包驱动**，不手写几百个 `register()`。
这样玩家可以自己注册馅料，变体数量无限而代码零增长。

## 阶段一：月饼本体（已实现）

完整的工艺链，全部可在生存模式里跑通：

```
可可豆 + 装水的炼药锅      →  可可豆豆沙
4 小麦 + 糖 + 鸡蛋          →  4× 月饼面团
4 月饼面团 + 豆沙           →  4× 带馅面饼（四分之一个方块大小，可放置）
手持【圆形模具】右键压印    →  圆形的生月饼面团（压扁一点）
手持【方形模具】右键压印    →  方形的生月饼面团
空手右键压印好的面团        →  生圆月饼 / 生方月饼（形状在这里定型）
熔炉 / 烟熏炉 / 营火烤      →  月饼（形状带进成品）
                              右键吃（随时可吃）
                              Shift + 右键摆到地上，一格最多叠 4 块
切石机（月饼 → 月饼）       →  6 种形态任选：形状 × 纹样
```

### 两个轴

| 轴 | 取值 | 决定什么 | 在哪设置 |
|---|---|---|---|
| **形状** | 圆 / 方 | 方块本身的**几何外形** | **模具**（压印时定型） |
| **纹样** | 圆纹 / 方纹 / 花形纹 | 表面的**花纹贴图** | **切石机**（烤好之后随时改） |

2 × 3 = **6 种形态**，所以「方纹的圆月饼」和「圆纹的方月饼」都存在。

**为什么生月饼要拆成两个物品，成品却不用：**

- 熔炉配方**读不到输入物品的组件**，所以形状想活过熔炉，只能让**原料本身不同**
  → `raw_mooncake` / `square_raw_mooncake` 两个物品
- 但配方**结果可以带组件**，所以两次熔炉各自产出同一个 `mooncake`，
  只是带上不同的 `mooncake_kind` → 成品不用拆

### 内容清单

| 类型 | ID | 中文名 | English |
|---|---|---|---|
| 物品 | `cocoa_bean_paste` | 可可豆豆沙 | Cocoa Bean Paste |
| 物品 | `mooncake_dough` | 月饼面团 | Mooncake Dough |
| 方块 + 物品 | `filled_mooncake_dough` | 带馅面饼 | Filled Mooncake Dough |
| 物品 | `mooncake_mold` | 月饼模具 | Mooncake Mold |
| 方块 + 物品 | `raw_mooncake` | 生月饼 | Raw Mooncake |
| 方块 + 物品 | `mooncake` | 月饼 | Mooncake |
| 方块 | `mooncake_dough_block` | 带馅面饼（`stamped` 状态） | Filled Mooncake Dough |
| 方块 | `mooncake_block` | 月饼堆（`mooncakes` 1～4） | Mooncake |

### 交互细节

- **炼药锅**：手持可可豆右键装水的炼药锅，得到豆沙，**水位下降一级**（空了变回空锅）。
  所以一桶水最多出 3 份豆沙。任意水位的炼药锅都可以用。
- **模具**：有 64 点耐久，每次压印消耗 1 点。
- **带馅面饼占地四分之一个方块**（8×8 居中），未压印厚 4 像素，
  **压印后被压扁到 3 像素**，一眼能看出被"压"过。
- **方块掉落**：未压印掉「带馅面饼」，已压印掉「生月饼」（战利品表按 `stamped` 状态区分）。
- **生月饼可以平放**（12×2×12 的矮饼，有自定义碰撞箱），也可以直接丢进熔炉。
- **月饼随时可吃**：食物属性带 `alwaysEdible()`。
  原版食物默认只在饥饿值不满时能进食，所以不加这个会出现"创造能吃、生存满饱食度吃不了"。
- **月饼既能吃也能摆**，用 Shift 区分：
  - **右键** → 进食
  - **Shift + 右键** → 摆到地上；对着已有的月饼堆按可以继续叠，**一格最多 4 块**（2×2 摆开，仿原版海泡菜）
  - 按住 Shift 时不会进食，免得"想摆一块结果吃掉了"
  - 挖掉月饼堆掉落**相应数量**的月饼
- **月饼有两个互相独立的轴**，别混为一谈：

  | 轴 | 取值 | 决定什么 | 在哪设置 |
  |---|---|---|---|
  | **形状** | 圆 / 方 | 方块本身的**几何外形** | — |
  | **纹样** | 圆纹 / 方纹 / 花形纹 | 表面的**花纹贴图** | 切石机 |

  两轴组合 = **6 种形态**，所以「方纹的圆月饼」和「圆纹的方月饼」都存在。
- **两个模具**：`mooncake_mold`（圆形模具，配方竖排 `I`/`S`）和
  `square_mooncake_mold`（方形模具，配方横排 `IS`）—— 就是"竖着 / 横着"的关系。
  压印时决定**形状**，形状会一路带到成品。
- **月饼堆是「四格独立」的**：一个方块分 2×2 四格，**每格单独记形态**，可以混着摆。
  - 用 blockstate 的 **`multipart`** 拼模型：6 形态 × 4 位置 = 24 个单格模型 + 24 条规则，
    而不是把 7⁴ 种组合各写一个模型
  - 几何只由**形状**决定：方 = 完整方块；圆 = 切掉四角（十字形）。
    原版模型的 `elements` **只支持轴对齐长方体**，做不出真正的圆柱
  - 掉落**不走战利品表**，而是覆写 `getDrops` 四格逐一还原（原版 `ShulkerBoxBlock` 同理）
- **形态用切石机切换**：月饼放进切石机，会看到 6 个选项（`圆形月饼` / `圆月饼·方纹` / …）。
  - 形态存在 `mooncake_overflow:mooncake_kind` 组件上，**物品始终只有一个月饼**，不会物品爆炸
  - **为什么是切石机而不是模具**：熔炉配方不继承输入物品的组件。模具作用在烤制**之前**，
    形状活不过熔炉；切石机作用在烤制**之后**，于是完全绕开了这个限制
  - **默认形态（圆形状+圆纹样）不写组件** —— 否则熔炉烤出来的（无组件）和切石机切的
    （带 `kind=round_round`）组件不同，会无法堆叠
  - 形态会一路带到：物品名 → 物品模型 → 摆下去的方块外观 → 挖出来的掉落物
- **道歉信**：启动时打印，可在 `run/config/mooncake_overflow-common.toml` 里用 `logApology = false` 关掉。

### 尚未实现

- 月饼 + 烟花 → 月饼形状的烟花（原案里有，留到阶段一之后）
- 除可可豆以外的馅料

## 开发状态

🟢 **月饼系统完成** —— 构建、资源、交互全部跑通，并经玩家在客户端逐项实测确认。

- [x] ForgeGradle 7 构建链路
- [x] `runClient` / `runServer` / `runData` 全部可用
- [x] **工艺链**：豆沙 → 面团 → 带馅面饼 → 压印 → 生月饼 → 烤制 → 月饼
- [x] **形态系统**：形状（圆/方）× 纹样（圆/方/花）= 6 种，切石机切换，组件承载
- [x] **四格混装**：一个方块 2×2 四格各自独立，可以混着摆不同形态
- [x] 食用（随时可吃）、Shift 摆放、取暖、掉落、中英文本地化
- [ ] **氧化 + 涂蜡**（阶段二）：真正随时间氧化，蜜脾锁死
- [ ] **切开 / 拼合**（阶段三）：把一个圆月饼切成四份再拼成缝合怪
- [ ] **月饼块全家桶**（阶段四）：数据包驱动自动生成形态矩阵
- [ ] **月亮系统**（阶段五）：吃月亮
- [ ] **玉兔 / 五仁 / 礼盒 / 元游戏嘲讽**（阶段六）
- [ ] 月饼形状的烟花（要自定义 `FireworkShape`）
- [ ] 仓库里还缺一个 `LICENSE` 文件

完整点子集见 [`docs/absurd_ideas.md`](docs/absurd_ideas.md)。

## 环境要求

| 组件 | 版本 | 备注 |
|---|---|---|
| Minecraft | 26.1 | |
| Forge | 62.0.9 | |
| Java（编译目标） | 25 | 由 Gradle toolchain 自动下载，无需手动装 |
| Gradle | 9.3.1 | 通过 wrapper 使用，无需手动装 |
| ForgeGradle | 7.x | `[7.0.17,8)` |

> **首次构建会比较久。** ForgeGradle 7 会自动下载
> **JDK 25**（编译工具链，约 140 MB）和 **JDK 8**（Mavenizer 处理 access transformer 用，约 104 MB），
> 并完成一次 Minecraft/Forge 的 Mavenizer 处理。
> 本机实测首次完整构建约 2 分钟（不含 JDK 下载），之后都有缓存。

## 构建

```bash
./gradlew build
```

产物：`build/libs/mooncake_overflow-0.1.0-SNAPSHOT.jar`

## 运行

```bash
./gradlew runClient         # 启动客户端
./gradlew runServer         # 启动服务端（需先同意 EULA，见下）
./gradlew runData           # 数据生成，输出到 src/generated/resources
./gradlew runGameTestServer # 运行 gametest
```

**关于 `runServer`**：首次运行会在 `run/eula.txt` 写入 `eula=false` 并退出。
需要你**自己**把它改成 `eula=true` —— 这代表你本人同意 [Mojang EULA](https://aka.ms/MinecraftEULA)。

## Linux / Wayland 已知问题

如果在 Wayland 桌面环境下 `runClient` 崩溃并报：

```
GLFW error before init: [0x1000C]Wayland: The platform does not provide the window position
```

这是 **Forge 的已知 bug**，不是项目配置问题。

- 参考：[MinecraftForge#10062](https://github.com/MinecraftForge/MinecraftForge/issues/10062)、[MinecraftForge#10823](https://github.com/MinecraftForge/MinecraftForge/issues/10823)
- **原因**：MC 26.1 的 `GLX._initGlfw` 本来会执行 `glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11)` 强制走 X11，
  但 **Forge 的早期加载窗口 `fmlearlywindow` 先一步调用了 `glfwInit()`**，此时 hint 尚未生效，
  GLFW 3.4 便选了 Wayland；早期窗口在 Wayland 上调用 `glfwGetWindowPos` 会产生**非致命**错误
  `GLFW_FEATURE_UNAVAILABLE (0x1000C)`，随后被 MC 的 `checkGlfwError` 当成致命错误抛出。
- **环境变量救不了**：GLFW 二进制里没有 `GLFW_PLATFORM` 这个变量；取消 `WAYLAND_DISPLAY` 后
  它会回退到默认的 `wayland-0`，设成无效值则直接 `glfwInit` 失败。

**绕过办法**：编辑 `run/config/fml.toml`

```toml
earlyWindowControl = false
```

这样 Forge 不再抢跑，MC 自己的 X11 hint 生效，走 XWayland。代价是没有了 Forge 的早期加载小窗。

**彻底修法**：升级到 **Minecraft 26.1.2 + Forge 64.1.3**，其 changelog 包含官方修复：

> `64.0.12 Handle glfw window pos errors gracefully for wayland (#10852)`

升级后即可把 `earlyWindowControl` 改回 `true`。

> 注意：`run/` 目录在 `.gitignore` 中，所以这个改动是**本机设置**，不会进仓库 —— 这是合理的，
> 因为它跟具体桌面环境绑定。

## 目录结构

```
.
├── build.gradle                          # ForgeGradle 7 构建脚本
├── gradle.properties                     # 版本号、mod_id、group
├── settings.gradle
├── gradlew / gradlew.bat
├── docs/
│   ├── some_ideas.md                     # 最初的构思与合成链
│   └── absurd_ideas.md                   # 完整点子集 + 性能红线 + MVP 顺序
├── tools/
│   ├── generate_textures.py              # 贴图程序化生成器
│   ├── generate_block_models.py          # 月饼方块（1～4 块）模型生成器
│   └── check_resources.py                # 资源一致性检查（改完资源务必跑一次）
└── src/main/
    ├── java/org/cvrain/mooncakeoverflow/
    │   ├── MooncakeOverflow.java         # 主类：注册、炼药锅交互、道歉信
    │   ├── Config.java                   # 配置（目前只有 logApology）
    │   ├── block/
    │   │   ├── MooncakeDoughBlock.java   # 可压印的月饼面团方块
    │   │   ├── PattyBlock.java           # 平放的饼（生月饼用）
    │   │   └── MooncakeBlock.java        # 月饼堆（mooncakes 1～4 × pattern）
    │   ├── item/
    │   │   └── MooncakeBlockItem.java    # Shift 右键放置 / 普通右键进食 / 名字随图案
    │   ├── mooncake/
    │   │   └── MooncakePattern.java      # 图案枚举：圆形 / 方形 / 花形
    │   └── registry/
    │       ├── ModBlocks.java
    │       ├── ModItems.java
    │       ├── ModCreativeTabs.java
    │       └── ModDataComponents.java    # mooncake_pattern 组件
    └── resources/
        ├── META-INF/mods.toml
        ├── pack.mcmeta
        ├── assets/mooncake_overflow/     # items/ blockstates/ models/ textures/ lang/
        └── data/mooncake_overflow/       # recipe/ loot_table/
```

## 性能原则

**抽象是玩法层面的，不是性能层面的。**

这个模组可以很离谱，但不能让游戏变卡。三条硬约束：

1. **变体数据驱动** —— 不为每个变体写代码，也不给每个变体独立贴图（走调色板染色 + 程序化合成）
2. **零每 tick 逻辑** —— 氧化复用原版铜的**随机刻**；月饼块**不用 BlockEntity**；
   状态（馅料/象限/内容物）存在物品侧的 `DataComponent` 里；计时用 `Level#scheduleTick`
3. **程序化贴图只在资源重载时算一次**并缓存，运行时只查表

**允许**：几百个变体、少量自定义实体、粒子效果、一次性高开销
**不允许**：每 tick 扫描全世界、每帧动态生成模型/贴图、会无限增殖的方块或实体

## 资源与命名约定

- **`mod_id`**：`mooncake_overflow`（已锁定，改动会导致旧存档失效）
- **主包**：`org.cvrain.mooncakeoverflow`
- **显示名**：`Mooncake Overflow` / 中文 `月饼溢出`
- **副标题**：`347 Variants, None of Them Good`
- **贴图**：全部由 `tools/generate_textures.py` 生成，改完跑一次脚本即可：

  ```bash
  python3 tools/generate_textures.py
  ```

  脚本会同时输出一张 `/tmp/mooncake_textures_preview.png` 放大预览图方便人眼检查。
- **26.1 API 备忘**（踩过的坑，别再用旧名字）：
  - `ResourceLocation` → **`Identifier`**
  - 物品 NBT → **`DataComponentType`**
  - ⚠️ **物品模型的入口是 `assets/<ns>/items/<id>.json`，不是 `models/item/<id>.json`！**
    后者只是被前者引用的几何文件。只写 `models/item/` 不会报任何错，
    但物品在游戏里会渲染成**紫黑格**。26.1 原版有 1506 个 `items/` 文件，
    其中 760 个（所有方块物品）没有对应的 `models/item/`。
    格式：`{"model": {"type": "minecraft:model", "model": "<ns>:item/<name>"}}`，
    方块物品则指向 `block/<name>`。
  - 方块属性必须显式 `.setId(...)`
  - 事件监听用 `SomeEvent.BUS.addListener(Consumer<T>)`（EventBus 7）
  - `ItemStack.is(Item)` 已改为接收 `Predicate<Holder<Item>>`，直接比 `getItem()` 更稳
  - `CauldronInteraction.Dispatcher.put()` 是包私有，炼药锅只能走 `PlayerInteractEvent.RightClickBlock`
  - `minecraft:copy_state` 现在是把方块状态塞进物品组件，**不能**用来设掉落数量；
    数量随方块状态变化要用多个带 `block_state_property` 条件的掉落池
  - ⚠️ **方块物品的显示名前缀是显式声明的**：`Item.getDescriptionId()` 是 `final`（不能覆写），
    前缀由 `Item.Properties#useBlockDescriptionPrefix()` 决定，默认是 `item.`。
    方块物品不调它就会显示成 `item.<ns>.<id>` 这种原始键名。
    而且键里的 path 用的是**物品自己的 id**，不是方块 id
    （物品 `filled_mooncake_dough` → 键 `block.<ns>.filled_mooncake_dough`）。
  - ⚠️ **`useWithoutItem` 只在 `useItemOn` 返回 `TRY_WITH_EMPTY_HAND` 时才会被调用**。
    返回 `PASS` 会让"空手交互"这段代码永远不执行。
    原版源码里的判断是 `if (result instanceof InteractionResult.TryEmptyHandInteraction && hand == MAIN_HAND)`
  - **配方结果可以带组件**：`ItemStackTemplate` 有 `components` 字段，
    所以 `"result": {"id": "...", "components": {"ns:comp": "值"}}` 是合法的
    （切石机 / 熔炉 / 合成都适用）
  - **物品模型可以按组件值切换**：`assets/<ns>/items/<id>.json` 里用
    `{"type": "minecraft:select", "property": "minecraft:component", "component": "<组件id>",
      "cases": [{"when": <组件值>, "model": {...}}], "fallback": {...}}`
    （注意：`has_component` 只能判断"有没有"，不能判断值；要按值选必须用 `component`）
  - ⚠️ **多盒子拼一个形状时，顶面 UV 必须相对「这一块」归一化到 0..16**，
    不能直接用方块坐标。用方块坐标的话，7×7 的格子只会采到 16×16 贴图的左上角一小块，
    现象就是"每块只有四分之一个图案，四块拼起来才是完整的"（踩过）

### 改完资源记得跑检查

```bash
python3 tools/check_resources.py
```

它会校验：每个注册的物品/方块是否有对应的 `items/` `blockstates/` 文件、
模型与贴图引用是否存在、语言键是否齐全且没有死键。
**上面那个 `items/` 的坑就是它抓出来的**，建议每次动资源后都跑一次。

## 文档

- [`docs/some_ideas.md`](docs/some_ideas.md) —— 最初的构思与合成链
- [`docs/absurd_ideas.md`](docs/absurd_ideas.md) —— 完整点子集（12 类）+ 性能红线 + MVP 实现顺序

## 许可

`mods.toml` 中声明为 **MIT**，但仓库中尚无 `LICENSE` 文件，待补充。
