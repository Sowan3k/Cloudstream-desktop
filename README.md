# CloudStream Burgundy

A beta Windows desktop fork of [sheikhshariarnehal/Cloudstream-desktop](https://github.com/sheikhshariarnehal/Cloudstream-desktop), maintained by [Sowan3k](https://github.com/Sowan3k). Built with Kotlin, Compose Desktop and LibVLC. Original history, copyright notices and GPLv3 licensing are retained.

This fork adds the Burgundy Wine light/dark theme, playback and fullscreen fixes, buffering recovery, and disabled bundled-provider telemetry. See [changes](CHANGELOG.md), [playback details](docs/playback-fixes.md) and [third-party provenance](THIRD_PARTY.md).

## Beta download

Current release: [**1.0.4-beta.1**](https://github.com/Sowan3k/Cloudstream-desktop/releases/tag/v1.0.4-beta.1).

| Download | Contents |
| --- | --- |
| [Windows installer](https://github.com/Sowan3k/Cloudstream-desktop/releases/download/v1.0.4-beta.1/CloudStream.Burgundy-1.0.4.msi) | `CloudStream.Burgundy-1.0.4.msi`, Windows 64-bit with Java runtime included. |
| [Complete source bundle](https://github.com/Sowan3k/Cloudstream-desktop/releases/download/v1.0.4-beta.1/CloudStream-Burgundy-1.0.4-beta.1-source.zip) | Application, pinned core and provider source trees. |
| [SHA-256 checksums](https://github.com/Sowan3k/Cloudstream-desktop/releases/download/v1.0.4-beta.1/SHA256SUMS.txt) | File-integrity values for the installer, source and accompanying metadata. |

- The beta installer is **unsigned**. A matching checksum confirms file integrity, not publisher identity or a malware scan.
- Playback requires a matching 64-bit VLC installation. Local native checks used LibVLC 3.0.20.
- The installer has a distinct Burgundy product identity. Both builds share `%USERPROFILE%\.cloudstream_desktop`, including settings, history and installed plugins; back up that folder before switching builds.
- This is a beta. Physical audio devices, every live provider, all GPU/driver combinations and clean-machine MSI installation are not fully verified.

After downloading the installer, run this in its download folder and compare the `Hash` value with its entry in `SHA256SUMS.txt`:

```powershell
Get-FileHash -Algorithm SHA256 .\CloudStream.Burgundy-1.0.4.msi
```

The published beta passed **45 JVM tests**, core compilation and MSI packaging locally, plus [Windows CI](https://github.com/Sowan3k/Cloudstream-desktop/actions/runs/45055562405). These checks do not establish every live provider's availability.

## Providers and privacy

The build includes 15 source-built providers from a pinned upstream repository. All 14 distinct bundled analytics endpoints are disabled before provider construction. A loopback regression test proves a control event can be received and that usage events stop after disabling the trackers.

Additional plugins remain optional. Open **Plugins > Repositories** to choose from the JVM repository presets. Downloads start only after you choose Add or Sync. [Repository notes](docs/repositories.md) list the previous registration results and known unsupported candidates.

User-installed plugins execute third-party code and can make their own network requests. They are not covered by the bundled-provider telemetry safeguard. No personal plugin directory, settings, credentials, watch history or downloaded media is included in this source repository or installer. Streaming rights and availability depend on the source.

## Build from source

Use Git and JDK 17. Clone the app and both pinned source dependencies into one parent directory:

```powershell
git clone --branch v1.0.4-beta.1 https://github.com/Sowan3k/Cloudstream-desktop.git desktop
git clone https://github.com/recloudstream/cloudstream.git cloudstream
git -C cloudstream checkout --detach 2dcc25c3945c61dfa95d1710ced7c74acfe84baa
git clone https://github.com/nehalDIU/nehal-CloudStream.git nehal-CloudStream
git -C nehal-CloudStream checkout --detach 673f061d7a796a693cb744c218e9f4141a9867fe
```

Alternatively, extract the **complete source bundle** linked above; it already contains all three folders. GitHub's automatic “Source code” ZIP contains only the application repository and does not include the sibling dependencies.

From the directory containing the three folders:

```powershell
cd desktop
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat jvmTest --no-daemon --max-workers=2
.\gradlew.bat run
.\gradlew.bat packageMsi --no-daemon --max-workers=2
```

Adjust `JAVA_HOME` to your JDK 17 directory. The MSI appears in `build/compose/binaries/main/msi/`. The release source bundle includes the same `desktop`, `cloudstream` and `nehal-CloudStream` layout.

These commands build the published beta tag. Use `--branch main` instead when cloning to work on the current fork.

`build-dependencies.json` records the exact upstream revisions. The root Gradle 9.9.2 wrapper pins the distribution checksum; Windows CI uses the same dependency pins and runs JVM tests plus MSI packaging. Kotlin 2.4.0's [fully supported Gradle range](https://kotlinlang.org/docs/gradle-configure-project.html) ends at 9.5.0, so changing tool versions requires fresh validation. Existing plugins emit deprecation warnings.

## Controls

- Space / K: play or pause; Left / J and Right / L: seek ten seconds.
- Up / Down: volume; M: mute.
- F / F11: player fullscreen; Escape: close a drawer, exit fullscreen or go back.
- Player lock blocks playback shortcuts; drawer controls retain keyboard focus.

## License and attribution

This modified application is distributed under [GPLv3](LICENSE). Keep the original notices and provide corresponding source when redistributing binaries. [THIRD_PARTY.md](THIRD_PARTY.md) records the pinned core/provider provenance and the provider repository's public-domain declaration. Optional external plugins retain their own licenses.

The Burgundy palette references [Serafim Cloud's Burgundy Wine theme](https://21st.dev/@serafimcloud/themes/burgundy-wine). This fork is not presented as an official upstream release.
