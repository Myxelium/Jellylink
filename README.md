<!--
Title: Jellylink — Self-Hosted Jellyfin Lavalink Plugin for Discord Music Bots
Description: Jellylink is an open-source Lavalink plugin that integrates Jellyfin with Lavalink so Discord music bots can stream audio directly from your Jellyfin media server. Self-hosted, private, and high-quality audio streaming for your bot.
Keywords: Jellyfin Lavalink plugin, Jellyfin music bot, Discord music bot self-hosted, stream music from Jellyfin to Discord, Lavalink plugin, Jellyfin audio streaming
-->
# Jellylink – Jellyfin Music Plugin for Lavalink

[![CI](https://github.com/Myxelium/Jellylink/actions/workflows/ci.yml/badge.svg)](https://github.com/Myxelium/Jellylink/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![JitPack](https://jitpack.io/v/Myxelium/Jellylink.svg)](https://jitpack.io/#Myxelium/Jellylink)

Play music from your **Jellyfin** media server through **Lavalink**. Jellylink is a Lavalink plugin that lets Discord bots search and stream audio directly from a Jellyfin library — no YouTube, Spotify, or SoundCloud dependencies required.

> **Perfect for:** Self-hosted music collections, privacy-focused bots, lossless audio streaming (FLAC/ALAC), and offline music libraries.

---

## ✨ Features

- 🔍 **Search your Jellyfin library** from any Lavalink client using the `jfsearch:` prefix
- 🎵 **Stream audio directly** — supports FLAC, MP3, AAC, OGG, OPUS, and other formats
- 🎨 **Rich metadata** — track title, artist, album, duration, and album artwork
- ⚙️ **Configurable quality** — stream original files or transcode to specific bitrate/codec
- 🔐 **Secure authentication** — automatic token management with configurable refresh
- 🚀 **Performance optimized** — LRU cache, thread-safe operations, connection pooling
- 🔄 **Resilient** — automatic retry on token expiration (401 errors)
- 🌐 **Multi-source** — works alongside YouTube, SoundCloud, Spotify, and all other Lavalink sources

---

## Installation

### Prerequisites

- [Lavalink v4](https://github.com/lavalink-devs/Lavalink) (tested with 4.0.8)
- A running [Jellyfin](https://jellyfin.org/) server with music in its library
- Java 17+

### Step 1 — Add the Plugin to Lavalink

Add the following to your Lavalink `application.yml`:

```yaml
lavalink:
  plugins:
    - dependency: com.github.Myxelium:Jellylink:v0.2.0
      repository: https://jitpack.io
```

> **Tip:** Replace `v0.2.0` with the version you want. Check available versions on the [Releases](https://github.com/Myxelium/Jellylink/releases) page.

Lavalink will automatically download the plugin on startup.

<details>
<summary><strong>Alternative: Manual Installation</strong></summary>

**Option A: Download the JAR**

Grab the latest `jellylink-x.x.x.jar` from the [Releases](https://github.com/Myxelium/Jellylink/releases) page.

**Option B: Build from Source**

```bash
git clone https://github.com/Myxelium/Jellylink.git
cd Jellylink
./gradlew build
```

The JAR will be at `build/libs/jellylink-0.2.0.jar`.

Copy the JAR into your Lavalink `plugins/` directory:

```
lavalink/
├── application.yml
├── Lavalink.jar
└── plugins/
    └── jellylink-0.2.0.jar    ← put it here
```

If you use **Docker**, mount it into the container's plugins volume:

```yaml
volumes:
  - ./application.yml:/opt/Lavalink/application.yml
  - ./plugins/:/opt/Lavalink/plugins/
```

</details>

### Step 2 — Configure Lavalink

Add the following to your `application.yml` under `plugins:`:

```yaml
plugins:
  jellylink:
    jellyfin:
      baseUrl: "http://your-jellyfin-server:8096"
      username: "your_username"
      password: "your_password"
      searchLimit: 5          # max results to return (default: 5)
      audioQuality: "ORIGINAL" # ORIGINAL | HIGH | MEDIUM | LOW | custom kbps
      audioCodec: "mp3"       # only used when audioQuality is not ORIGINAL
      tokenRefreshMinutes: 30 # re-authenticate every N minutes (0 = only on 401)
```

#### Audio Quality Options

| Value       | Bitrate   | Description                              |
|-------------|-----------|------------------------------------------|
| `ORIGINAL`  | —         | Serves the raw file (FLAC, MP3, etc.)    |
| `HIGH`      | 320 kbps  | Transcoded via Jellyfin                  |
| `MEDIUM`    | 192 kbps  | Transcoded via Jellyfin                  |
| `LOW`       | 128 kbps  | Transcoded via Jellyfin                  |
| `256`       | 256 kbps  | Any number = custom bitrate in kbps      |

#### Docker Networking

If Lavalink runs in Docker and Jellyfin runs on the host:
- Use your host's LAN IP (e.g. `http://192.168.1.100:8096`)
- Or use `http://host.docker.internal:8096` (Docker Desktop)
- Or use `http://172.17.0.1:8096` (Docker bridge gateway)

### Step 3 — Restart Lavalink

Restart Lavalink and check the logs. You should see:

```
Loaded plugin: jellylink-jellyfin
```

Verify at `GET /v4/info` — `jellyfin` should appear under `sourceManagers`.

---

## Usage

Search your Jellyfin library using the `jfsearch:` prefix when loading tracks:

```
jfsearch:Bohemian Rhapsody
jfsearch:Daft Punk
jfsearch:Bach Cello Suite
```

### Example with Lavalink4NET (C#)

> **Lavalink4NET users:** Install the companion NuGet package [Lavalink4NET.Jellyfin](https://github.com/Myxelium/Lavalink4NET.Jellyfin) for built-in search mode support, query parsing, and source detection.

```bash
dotnet add package Lavalink4NET.Jellyfin
```

```csharp
using Lavalink4NET.Jellyfin;
using Lavalink4NET.Rest.Entities.Tracks;

// Parse user input — automatically detects jfsearch:, ytsearch:, scsearch:, etc.
var (searchMode, cleanQuery) = SearchQueryParser.Parse(
    "jfsearch:Bohemian Rhapsody",
    defaultMode: JellyfinSearchMode.Jellyfin  // default when no prefix
);

var options = new TrackLoadOptions { SearchMode = searchMode };
var result = await audioService.Tracks.LoadTracksAsync(cleanQuery, options);
```

Or use `ParseExtended` for detailed source info:

```csharp
var result = SearchQueryParser.ParseExtended(userInput, JellyfinSearchMode.Jellyfin);

if (result.IsJellyfin)
    Console.WriteLine("Searching Jellyfin library...");

Console.WriteLine($"Source: {result.SourceName}, Query: {result.Query}");
```

### Example with Lavalink.py (Python)

```python
results = await player.node.get_tracks("jfsearch:Bohemian Rhapsody")
```

### Example with Shoukaku (JavaScript)

```javascript
const result = await node.rest.resolve("jfsearch:Bohemian Rhapsody");
```

The plugin only handles identifiers starting with `jfsearch:`. All other sources (YouTube, SoundCloud, Spotify, etc.) continue to work normally.

---

## 🔧 Advanced Configuration

### Performance Tuning

The plugin includes several optimizations for production use:

- **LRU Cache**: Metadata is cached with a default limit of 10,000 entries. When exceeded, the least recently used entries are evicted automatically.
- **Thread Safety**: All authentication and metadata operations are thread-safe using concurrent data structures and synchronization.
- **Connection Pooling**: HTTP client uses persistent connections for improved performance.

### Custom Configuration (Optional)

You can customize the metadata cache size by modifying the Spring bean configuration:

```kotlin
@Bean
fun jellyfinMetadataStore(): JellyfinMetadataStore {
    return JellyfinMetadataStore(maxSize = 5000) // Custom cache size
}
```

---

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| `Jellyfin authentication failed` | Check `baseUrl`, `username`, and `password`. Make sure the URL is reachable from the Lavalink host/container. |
| `No Jellyfin results found` | Verify the song exists in your Jellyfin library and that the user has access to it. |
| `Unknown file format` | Update to the latest version — this was fixed by using direct audio streaming. |
| `No cover art` | Update to the latest version — artwork URLs are now always included. Jellyfin has to be public to internet.|
| `401 Unauthorized after working` | Token may have expired. The plugin automatically re-authenticates. Check `tokenRefreshMinutes` setting. |
| `Connection timeout / refused` | If using Docker, ensure proper network configuration (see Docker Networking section above). |

### Enable Debug Logging

Add to your Lavalink `application.yml`:

```yaml
logging:
  level:
    dev.jellylink: DEBUG
```

This will show detailed logs for authentication, search requests, and playback URLs.

---

## 📊 Recent Improvements

### Bug Fixes
- 🔒 **Fixed race condition** in authentication - prevents duplicate login requests under high concurrency
- 🧠 **Fixed memory leak** - implemented LRU cache with automatic eviction (10,000 entry limit)
- 🛡️ **Enhanced JSON escaping** - properly handles special characters in passwords (newlines, tabs, etc.)
- ✅ **Improved validation** - prevents negative duration values from corrupting metadata

### Optimizations
- ⚡ **Lazy initialization** - base URL normalization cached to reduce repeated string operations
- 🔄 **Double-checked locking** - faster authentication checks without unnecessary synchronization
- 📝 **Kotlin idioms** - replaced `StringBuilder` with `buildString {}` for cleaner code

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

### Development Setup

```bash
git clone https://github.com/Myxelium/Jellylink.git
cd Jellylink
./gradlew build
```

### Code Quality

This project uses:
- **Detekt** for static code analysis
- **ktlint** for code formatting

Run checks with:
```bash
./gradlew check
```

---

## 📝 License

MIT License - see [LICENSE](LICENSE) file for details.

---

## ⭐ Support

If you find this plugin useful, please consider:
- ⭐ Starring the repository
- 🐛 Reporting bugs via [GitHub Issues](https://github.com/Myxelium/Jellylink/issues)
- 💡 Suggesting features or improvements
- 🤝 Contributing code or documentation

---

## 🔗 Related Projects

- [Lavalink](https://github.com/lavalink-devs/Lavalink) - Standalone audio sending node
- [Jellyfin](https://jellyfin.org/) - The Free Software Media System
- [Lavalink4NET.Jellyfin](https://github.com/Myxelium/Lavalink4NET.Jellyfin) - C# companion package with search mode support
