# Optional desktop repositories

The application includes 15 source-built providers. Additional plugins are optional; no personal plugin directory is copied into the installer. Repository presets reuse the existing **Plugins > Repositories** flow and require the user's action to download plugins.

| Repository | Manifest |
| --- | --- |
| CloudStream JVM extensions | <https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json> |
| Reflex Repo | <https://raw.githubusercontent.com/Reflex755/ReflexRepo/refs/heads/builds/repo.json> |
| saimuel repo | <https://raw.githubusercontent.com/saimuelbr/saimuelrepo/refs/heads/main/builds/repo.json> |

On 18 November 2026 these repositories supplied JVM builds that registered 14 additional providers: Dailymotion, DonghuaNoSekai, Doramas, Internet Archive, Invidious, Library of Ladev, MegaFlix, NetCine, OverFlix, PobreFlix, TopFilmes, Twitch, UltraCine and YouTube. This was a registration check, not proof of current streaming availability or plugin safety.

Known unsupported candidates were AnimesCloud, which required an unavailable `CloudflareKiller` class, and AnimesDigital, which required an Android-only initializer. Android DEX/`.cs3` archives do not become JVM plugins by changing the extension to `.jar`.

Repository contents can change. Review each plugin's origin, license and network behavior before adding it. The application's disabled built-in analytics do not control separately installed plugins. Streaming availability, regional access and rights to content depend on the source.
