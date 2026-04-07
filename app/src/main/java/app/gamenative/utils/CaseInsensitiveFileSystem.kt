package app.gamenative.utils

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Okio [FileSystem] wrapper that resolves each path component against on-disk
 * casing before delegating to [FileSystem.SYSTEM]. Prevents duplicate directories
 * when Steam depot manifests use different casing than what's already installed
 * (e.g. DLC referencing `_Work` when the base game created `_work`).
 *
 * Three-level cache, all bounded:
 * 1. Full-path LRU — repeat operations on the same path return immediately.
 * 2. Nested segment cache — parent → (lowercase segment → child). Bounded by
 *    directory count (dozens to hundreds per game). No string allocation on lookup.
 * 3. Lowercase pool — avoids repeated lowercase() allocation for the small
 *    vocabulary of directory names that games reuse.
 */
class CaseInsensitiveFileSystem(
    delegate: FileSystem = SYSTEM,
    pathCacheCapacity: Int = PATH_CACHE_CAPACITY,
) : ForwardingFileSystem(delegate) {

    // full path → resolved path. LRU-bounded so it doesn't grow with file count.
    private val pathCache: MutableMap<Path, Path> = Collections.synchronizedMap(
        object : LinkedHashMap<Path, Path>(pathCacheCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, Path>): Boolean =
                size > pathCacheCapacity
        }
    )

    // parent → (lowercase segment → resolved child). bounded by directory count.
    private val segmentCache = ConcurrentHashMap<Path, ConcurrentHashMap<String, Path>>()

    // segment string → lowercased form. game paths reuse a small set of dir names.
    private val lowercasePool = ConcurrentHashMap<String, String>()

    override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        pathCache[path]?.let { return it }

        val root = path.root ?: return path
        val segments = path.segments
        if (segments.isEmpty()) return path

        var resolved = root
        for (segment in segments) {
            val lower = lowercasePool.computeIfAbsent(segment) { it.lowercase() }
            val parent = resolved
            val children = segmentCache.computeIfAbsent(parent) { ConcurrentHashMap() }
            resolved = children.computeIfAbsent(lower) {
                resolveSegment(parent, segment)
            }
        }

        pathCache[path] = resolved
        return resolved
    }

    private fun resolveSegment(parent: Path, segment: String): Path {
        val exact = parent / segment
        if (delegate.metadataOrNull(exact) != null) return exact
        return delegate.listOrNull(parent)
            ?.firstOrNull { it.name.equals(segment, ignoreCase = true) }
            ?: exact
    }

    companion object {
        private const val PATH_CACHE_CAPACITY = 1024
    }
}
