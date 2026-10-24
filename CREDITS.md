# Development and asset provenance

- **EienMiku:** original concept, interaction and feature decisions, original 16×16 wrench artwork, repeated in-game testing, and detailed compatibility feedback.
- **OpenAI Codex:** most implementation code, integration adapters, automated verification, build and packaging scripts, documentation, and assistance with small pixel refinements under the author's direction.
- **Current texture:** the author-painted pixel sprite, refined through the recorded review process, in `art/final/universal-wrench.png`. The same bytes are used by the runtime item texture. Historical artwork experiments in `art/` are not the project icon or current texture.

The mod does not call a generative AI service at runtime.

Compatibility implementations use Minecraft/NeoForge APIs and the supported mods' native protocols. Third-party mod JARs, game files, local accounts, and development worlds are not distributed in the mod release or repository. Tests and compatibility notes identify the pinned mod versions used for validation. The supported projects retain their own names, artwork, licenses, and authorship.

The repository's code and original project assets are distributed under the MIT license in `LICENSE`. The Gradle wrapper is supplied by Gradle and retains its upstream licensing and notices.
