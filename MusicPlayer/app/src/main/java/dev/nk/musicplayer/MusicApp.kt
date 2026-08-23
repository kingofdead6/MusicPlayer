package dev.nk.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.nk.musicplayer.ui.components.TrackArtworkFetcher

/**
 * Implements [ImageLoaderFactory] so every `AsyncImage` in the app picks this loader up
 * automatically, without having to be handed one.
 *
 * The custom fetcher is what makes cover art actually appear: see [TrackArtworkFetcher] for
 * why MediaStore's album-art provider alone is not enough. Both caches are sized generously
 * because artwork is the one thing this app draws constantly and re-decoding a cover on every
 * scroll is the difference between a smooth list and a stuttering one.
 */
class MusicApp : Application(), ImageLoaderFactory {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(TrackArtworkFetcher.Factory()) }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("artwork"))
                .maxSizeBytes(96L * 1024 * 1024)
                .build()
        }
        // Artwork for a given track never changes under us, so a cross-fade on every cache
        // hit would just add a flicker to scrolling. AlbumArt does its own fade on first load.
        .crossfade(false)
        .build()
}
