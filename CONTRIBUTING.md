# Contributing to Create: New Era

Thanks for helping. Pull requests are welcome: fixes, features from the [roadmap](README.md#roadmap), translations, docs. For anything big, open an issue first so we can agree on the approach before you spend a weekend on it.

## Setting up

You need a JDK 17 or newer to start Gradle. Gradle downloads JDK 21 and the JetBrains Runtime on its own.

```sh
git clone https://github.com/<you>/create-new-era.git
cd create-new-era
./gradlew runClient          # first run downloads the dev modpack into run/
```

`runClient` starts with a realistic modpack (Sodium, Iris, Aeronautics…). If that's in your way, switch groups off in `gradle.properties` (`dev_client_mods`, `dev_shaders`, `dev_aeronautics`).

Most code changes don't need a restart. With the game running:

```sh
./gradlew hotswap            # recompile and swap changed classes into the running game
```

Textures, models and lang are copied too: press F3+T in game. Recipes and other data: `/reload`. New blocks, items, block entities or network payloads still need a restart. More in the [Development docs](https://create-new-era.tryberyl.dev/docs/dev/).

## Making a change

- Branch from `main`, one focused change per pull request. A bug fix and a refactor are two PRs.
- Keep the diff to what the change needs; don't reformat files you didn't otherwise touch.
- If you change behaviour, update the README, the docs in `website/docs/` and the in-game tooltip/Ponder text that describe it.

### Commits

Look at `git log` for the style. In short:

- Subject in the imperative, saying what the change does: `Show the shaft turning in a hole in the computer's back`, not `fixed stuff` or `Updates`.
- A body when the why isn't obvious: what was wrong, what changed, anything a reviewer would otherwise have to dig for. Wrap it at about 72 columns.
- Small, self-contained commits are fine; squash fixups before asking for review.

### Before you open the PR

All of these must pass:

```sh
./gradlew build                # compiles, builds the jar
./gradlew runGameTestServer    # in-game tests, exits non-zero on failure
./gradlew runData              # regenerates src/generated/resources
```

Commit whatever `runData` changes (blockstates, item models, loot tables, tags, `en_us.json`). A PR whose generated files are out of date will fail review.

### Tests

Behaviour changes need a GameTest: add or update one in `src/main/java/com/createnewera/gametest/`. `ComputerTests` shows the pattern: place the block, drive it (a creative motor for rotation, a redstone block for input), run real Lua, then assert on the world or the console. Bug fixes should come with a test that fails without the fix.

### Assets

Models, textures and Ponder structures are generated. Don't hand-edit the output; change the script and re-run it:

| What | Script |
| --- | --- |
| Block textures, detail atlases, the Control Plate item | `tools/textures/gen_textures.py` |
| Block models | `tools/models/gen_models.py` |
| Ponder structures | `tools/ponder/gen_ponder.py` (positions must match `NewEraPonderScenes.java`) |
| Previews without launching the game | `tools/render/iso_render.py` |

Keep Create's look. Study Create's own assets before drawing anything, reuse its casings by reference instead of copying them, and take colours from the palettes in `gen_textures.py`. Render your block with `iso_render.py` next to Create's blocks and check it fits before opening the PR; include the render in the PR.

### Translations

- English comes from datagen: block names via Registrate's `.lang(...)` in `ModBlocks`, everything else in `ModLang`. Never edit `src/generated/.../en_us.json` by hand.
- Spanish (`src/main/resources/assets/create_new_era/lang/es_es.json`) is hand-written and must get every new key. Both files have the same keys today; keep it that way.
- New languages are welcome as hand-written files next to `es_es.json`.

### Code style

- Match the code around you: naming, structure, how existing classes do the same thing.
- Comments explain why, not what. If a comment just restates the next line, delete it.
- Prefer small, readable changes over clever ones; the Lua API in particular should stay simple for players.
- New Lua functions go in `LuaComputer` (the `native` table) and `prelude.lua`, and also in `client/editor/LuaApi.java` so the editor suggests them.

## Reporting bugs

Open an issue with the bug report form and include:

- Minecraft, NeoForge, Create and Create: New Era versions (and Aeronautics if installed).
- Your full mod list, or the modpack and its version.
- What you did, what you expected, what happened. A Lua program that reproduces it is gold.
- `logs/latest.log` and, for crashes, the file from `crash-reports/`. Upload them to a paste site (mclo.gs works well) rather than pasting thousands of lines.

## License

Create: New Era is released under [The Unlicense](LICENSE). By contributing, you agree that your contribution is released under the same terms, into the public domain.
