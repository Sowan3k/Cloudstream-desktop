# Attribution and source provenance

This is a modified fork of [CloudStream Desktop by sheikhshariarnehal](https://github.com/sheikhshariarnehal/Cloudstream-desktop). Original copyright notices, history and the GPLv3 license in `LICENSE` are retained. Local fixes and the Burgundy Wine adaptation are maintained by Sowan3k. See the changelog for modification dates.

## Source dependencies

Exact revisions are recorded in `build-dependencies.json` and checked out by Windows CI.

| Component | Source and revision | Upstream licensing information |
| --- | --- | --- |
| CloudStream core | [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream/tree/2dcc25c3945c61dfa95d1710ced7c74acfe84baa) | Preserve its `LICENSE` and source notices. |
| Built-in provider source | [nehalDIU/nehal-CloudStream](https://github.com/nehalDIU/nehal-CloudStream/tree/673f061d7a796a693cb744c218e9f4141a9867fe) | The pinned README declares the code public domain, names Nehal as maintainer, and credits CloudStream 3 and Aliucord. Its linked root `LICENSE` file is absent. This records the upstream declaration, not a new license grant for every dependency. |
| Burgundy Wine palette reference | [Serafim Cloud's theme](https://21st.dev/@serafimcloud/themes/burgundy-wine) | Color reference used for the desktop theme adaptation. |

The provider repository's pinned README and the core license accompany the release source bundle. External Maven dependencies remain declared in their original Gradle build files; this document is not a complete dependency-license audit.

## Provider privacy

The pinned built-in providers contain analytics hooks that can transmit search queries, viewed URLs, heartbeat data and a session identifier to a third-party dashboard. This fork blanks all bundled tracker endpoints before constructing those providers. A loopback regression test checks that their usage events are not sent after disabling them.

User-installed plugins execute third-party code and may make their own network requests. The bundled-provider safeguard does not establish the behavior of those separate plugins. Optional plugin binaries, personal settings, credentials, watch history and downloaded media are not included in this repository or installer.
