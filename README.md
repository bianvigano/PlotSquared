<p align="center">
    <img src="https://raw.githubusercontent.com/IntellectualSites/Assets/main/plugins/PlotSquared/PlotSquared.svg" width="250">
</p>

---

PlotSquared is a land and world management plugin for Minecraft.
It includes several highly configurable world generators.
You can create plots of land in existing worlds using plot clusters, or you can have a full world of plots.

For the end user, PlotSquared is packed with a tonne of cool features.
It allows you to merge plots, and build together with your friends.
You can also change a lot of plot specific settings in the form of
flags. Such as: weather, time, game modes, pvp status.

Whilst we provide a whole load of unique features, the biggest focus
is to provide a lag-free and smooth experience.

## How It Works

This repository is a Gradle multi-module project with two main modules:

* `Core` (`plotsquared-core`) contains the platform-independent logic such as plot models, commands, flags,
  configuration handling, storage integration, generators, and API/event systems.
* `Bukkit` (`plotsquared-bukkit`) contains the Paper/Bukkit plugin entry point, listeners, platform adapters,
  permissions integrations, and the final shaded plugin jar.

At runtime, Bukkit loads the plugin through `plugin.yml`, which points to `com.plotsquared.bukkit.BukkitPlatform`.
During startup, the Bukkit module:

* creates the core `PlotSquared` instance
* loads configuration and localization resources
* initializes dependency injection
* wires storage, UUID services, listeners, commands, and plot world generators
* registers the `/plots` command and Bukkit/Paper event listeners

In short: `Core` contains the main behavior, while `Bukkit` connects that behavior to a Minecraft server and
packages the plugin you install.

## Build The Jar

### Requirements

* A real git clone of the repository. The build checks for `.git`, so a downloaded ZIP archive will not work.
* Java 21 installed locally
* Linux/macOS shell or Windows terminal that can run the Gradle wrapper

### Build Only The Bukkit Plugin Jar

Run this from the repository root:

```bash
./gradlew :plotsquared-bukkit:shadowJar
```

The plugin jar will be created at:

```text
Bukkit/build/libs/plotsquared-bukkit-<version>.jar
```

Example output from this repository:

```text
Bukkit/build/libs/plotsquared-bukkit-7.5.14-SNAPSHOT.jar
```

### Build Everything

To build all modules:

```bash
./gradlew clean build
```

This will also build the shaded jars because the `build` task depends on `shadowJar`.

### Local Test Server

The repository also provides helper tasks for launching local Paper test servers for supported versions, for example:

```bash
./gradlew runServer-1.20.6
```

These tasks automatically use the freshly built Bukkit plugin jar.

### Which Jar Should Be Used On The Server?

Use the jar from `Bukkit/build/libs/`.

The jar from `Core/build/libs/` is the shared core library artifact, not the Paper/Bukkit plugin you drop into the
server `plugins/` folder.

## Server Compatibility

PlotSquared is built from the Bukkit module and can be used on Bukkit-compatible server software.

* Paper: supported
* Purpur: supported through Paper compatibility
* Spigot: supported, although some behavior is more optimized on Paper
* Folia: supported with a Folia-aware scheduler compatibility layer

### Folia Notes

Folia support in this repository uses runtime scheduler detection and switches PlotSquared scheduling to Folia schedulers
when available.

To avoid unsafe global world access on Folia, some global sweep tasks are intentionally disabled:

* the global world unload sweep
* the periodic player cleanup sweep
* the global entity cleanup sweep

The plugin still builds as a single jar and does not require a separate Folia-only artifact.

## `worlds.yml` Example: `plot.floor_claimed`

Plot worlds can optionally override the floor block after a plot is claimed.

Add this to the relevant world section in `worlds.yml`:

```yml
plot:
  floor: minecraft:grass_block
  floor_claimed: minecraft:lime_concrete
```

The block form still works for both keys:

```yml
plot:
  floor: minecraft:red_stained_glass
  floor_claimed: minecraft:air
```

Behavior:

* `plot.floor` is used for unclaimed plots
* `plot.floor_claimed` is used after the plot is claimed
* if `plot.floor_claimed` is missing or set to `null`, the claimed floor override is disabled

Example with the override disabled:

```yml
plot:
  floor: minecraft:grass_block
  floor_claimed: null
```

`plot.floor_claimed` uses the same `BlockBucket` / pattern format as `plot.floor`, so weighted patterns are also valid:

```yml
plot:
  floor: minecraft:grass_block
  floor_claimed: 50%minecraft:lime_concrete,50%minecraft:green_concrete
```

Both keys can also be written as sections with a nested `schematic` configuration.
If `plot.floor_claimed.schematic.on_claim` is enabled, it is preferred on claim.
Otherwise `plot.floor.schematic.on_claim` is used as a fallback.
On unclaim, the plot returns to the normal `plot.floor` block configuration.

```yml
plot:
  floor:
    schematic:
      on_claim: true
      schematics: []
      specify_on_claim: false
      place_top_block: true
      file: 'null'
  floor_claimed:
    schematic:
      on_claim: true
      schematics: []
      specify_on_claim: false
      place_top_block: true
      file: 'null'
```

Meaning of the schematic options:

* `on_claim`: enables schematic pasting for that floor configuration when a plot is claimed
* `specify_on_claim`: allows the schematic name provided during claim to be used first
* `place_top_block`: controls whether this schematic is pasted on top of the plot floor height or from the area's minimum build height
* if `specify_on_claim` is `true` and a schematic name is provided during claim, PlotSquared tries that schematic first
* if no schematic name is provided, or the requested schematic is not found, PlotSquared falls back to `file`
* if `specify_on_claim` is `false`, PlotSquared only uses the value from `file`
* if `place_top_block` is `true`, this schematic uses the same "paste on top" behavior as the normal PlotSquared schematic setting
* if `place_top_block` is `false`, this schematic is pasted from the area's minimum build height instead
* if a claimed floor schematic is active and no claimed floor `block` is configured, the normal `plot.floor` layer is cleared for claimed plots
* `schematics: []` is kept for compatibility with the existing area-level schematic format

If you use the section form and still want to define floor blocks, add a `block`, `blocks`, or `value` entry inside the section.

How to use it:

* put your schematic files inside `plugins/PlotSquared/schematics/`
* write the file name in `plot.floor.schematic.file` or `plot.floor_claimed.schematic.file`
* you can use `claimed-floor.schem`, `claimed-floor.schematic`, or just `claimed-floor`
* if no file extension is given, PlotSquared automatically tries `.schem`
* if `plot.floor_claimed.schematic.on_claim` is `true`, that schematic is pasted when the plot is claimed
* if `plot.floor_claimed.schematic.on_claim` is not enabled, PlotSquared falls back to `plot.floor.schematic`
* if the plot is unclaimed, the floor returns to the normal `plot.floor` block setup

Example folder layout:

```text
plugins/
  PlotSquared/
    schematics/
      floor-default.schem
      claimed-floor.schem
```

Example config with both block and schematic support:

```yml
plot:
  floor:
    block: minecraft:red_stained_glass
    schematic:
      on_claim: false
      schematics: []
      specify_on_claim: false
      place_top_block: true
      file: 'floor-default.schem'
  floor_claimed:
    block: minecraft:air
    schematic:
      on_claim: true
      schematics: []
      specify_on_claim: false
      place_top_block: true
      file: 'claimed-floor.schem'
```


<p align="center">
    <a href="https://bstats.org/plugin/bukkit/PlotSquared" title="PlotSquared on bStats">
        <img src="https://bstats.org/signatures/bukkit/PlotSquared.svg" />
    </a>
</p>

## Links

* [Download](https://www.spigotmc.org/resources/77506/)
* [Discord](https://discord.gg/intellectualsites)
* [Wiki](https://intellectualsites.gitbook.io/plotsquared/)
* [Issues](https://github.com/IntellectualSites/PlotSquared/issues)
* [Translations](https://intellectualsites.crowdin.com/plotsquared/)
* [Contributing](https://github.com/IntellectualSites/.github/blob/main/CONTRIBUTING.md)

### Developer Resources

* [API Documentation](https://intellectualsites.gitbook.io/plotsquared/api/api-documentation)
* [Event API](https://intellectualsites.gitbook.io/plotsquared/api/event-api)
* [Flag API](https://intellectualsites.gitbook.io/plotsquared/api/flag-api)

# Official Addons

* [Plot2Dynmap](http://www.spigotmc.org/resources/plot2dynmap.1292/)
* [HoloPlots](https://www.spigotmc.org/resources/holoplots.4880/)
* [PlotHider](https://www.spigotmc.org/resources/plot-hider.20701/)

### Edit The Code

Want to add new features to PlotSquared or fix bugs yourself? You can get the game running, with PlotSquared, from the code here:

For additional information about compiling PlotSquared,
see [CONTRIBUTING.md](https://github.com/IntellectualSites/.github/blob/main/CONTRIBUTING.md)

### Submitting Your Changes

PlotSquared is open source (specifically licensed under GPL v3), so note that your contributions will also be open source. The
best way to submit a change is to create a fork on GitHub, put your changes there, and then create a "pull request" on our
PlotSquared repository.

<a href="https://yourkit.com/">
    <img src="https://www.yourkit.com/images/yklogo.png">
</a>

Thank you to YourKit for supporting our product by providing us with their innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator
of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/),
and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).
