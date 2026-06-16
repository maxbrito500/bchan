package mihon.core.migration.migrations

import android.app.Application
import android.os.Build
import android.provider.DocumentsContract
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.Downloader
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import java.io.File

/**
 * SY: downloads used to be stored as `<root>/<source>/<manga>/<chapter>`. They are now stored flat
 * as `<root>/<manga>/<chapter>` with number-based chapter folder names so that switching providers
 * reuses the same folders instead of re-downloading to a new location.
 *
 * This one-time migration moves existing downloads into the flat layout (merging folders that share
 * the same title), deletes the emptied source folders, and renames chapter folders to the new
 * number-based convention so they keep being recognized. It never aborts: failures are logged and
 * skipped (leftover legacy folders are still matched via [DownloadProvider.getValidChapterDirNames]).
 */
class FlattenDownloadsMigration : Migration {
    override val version: Float = 76f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val context = migrationContext.get<Application>() ?: return@withIOContext false
        val storageManager = migrationContext.get<StorageManager>() ?: return@withIOContext false
        val provider = migrationContext.get<DownloadProvider>() ?: return@withIOContext false
        val getAllManga = migrationContext.get<GetAllManga>() ?: return@withIOContext false
        val getChaptersByMangaId = migrationContext.get<GetChaptersByMangaId>() ?: return@withIOContext false
        val sourceManager = migrationContext.get<SourceManager>() ?: return@withIOContext false

        val root = storageManager.getDownloadsDirectory() ?: return@withIOContext true

        val mangaList = getAllManga.await()
        // folderName -> manga, favorites win on collision (associateBy keeps the last entry).
        val mangaByFolder = mangaList
            .sortedBy { it.favorite }
            .associateBy { provider.getMangaDirName(it.ogTitle) }

        // The names the per-source folders could have had (account for the non-ASCII setting both ways).
        val knownSourceDirNames = buildSet {
            (sourceManager.getVisibleOnlineSources() + sourceManager.getStubSources()).forEach { source ->
                add(DiskUtil.buildValidFilename(source.toString(), disallowNonAscii = false).lowercase())
                add(DiskUtil.buildValidFilename(source.toString(), disallowNonAscii = true).lowercase())
            }
        }

        // Phase A: flatten source folders into the downloads root.
        root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name?.lowercase() in knownSourceDirNames }
            .forEach { sourceDir ->
                sourceDir.listFiles().orEmpty()
                    .filter { it.isDirectory }
                    .forEach { mangaDir ->
                        try {
                            flattenMangaDir(context, mangaDir, root)
                        } catch (e: Throwable) {
                            logcat(LogPriority.WARN, e) { "Failed to flatten download folder ${mangaDir.name}" }
                        }
                    }
                deleteIfEmptyish(sourceDir)
            }

        // Phase B: rename chapter folders to the forced number-based names.
        root.listFiles().orEmpty()
            .filter { it.isDirectory }
            .forEach { mangaDir ->
                val manga = mangaByFolder[mangaDir.name] ?: return@forEach
                val chapters = try {
                    getChaptersByMangaId.await(manga.id)
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) { "Failed to load chapters for ${manga.ogTitle}" }
                    return@forEach
                }
                mangaDir.listFiles().orEmpty().forEach entry@{ entry ->
                    val name = entry.name ?: return@entry
                    if (name == DiskUtil.NOMEDIA_FILE || name.endsWith(Downloader.TMP_DIR_SUFFIX)) return@entry

                    val chapter = chapters.firstOrNull { ch ->
                        provider.getValidChapterDirNames(ch.name, ch.scanlator, ch.url, ch.chapterNumber)
                            .contains(name)
                    } ?: return@entry

                    var target = provider.getChapterDirName(
                        chapter.name,
                        chapter.scanlator,
                        chapter.url,
                        chapter.chapterNumber,
                    )
                    if (entry.isFile && entry.extension == "cbz") target += ".cbz"

                    if (name == target || mangaDir.findFile(target) != null) return@entry
                    try {
                        entry.renameTo(target)
                    } catch (e: Throwable) {
                        logcat(LogPriority.WARN, e) { "Failed to rename chapter folder $name -> $target" }
                    }
                }
            }

        // Drop the cache index files so the download cache rebuilds against the new layout.
        File(context.cacheDir, "dl_index_cache_v3").delete()
        File(context.cacheDir, "dl_index_cache_v4").delete()

        return@withIOContext true
    }

    private fun flattenMangaDir(context: Application, mangaDir: UniFile, root: UniFile) {
        val name = mangaDir.name ?: return
        val existing = root.findFile(name)
        if (existing == null) {
            if (!moveInto(context, mangaDir, root)) {
                logcat(LogPriority.WARN) { "Could not move download folder $name into root" }
            }
        } else {
            // Merge: move entries that don't already exist at the destination.
            mangaDir.listFiles().orEmpty().forEach { entry ->
                val entryName = entry.name ?: return@forEach
                if (existing.findFile(entryName) == null) {
                    moveInto(context, entry, existing)
                }
            }
            deleteIfEmptyish(mangaDir)
        }
    }

    /**
     * Moves [file] into [targetParent] keeping its name. Tries a cheap rename/move first and falls
     * back to a recursive copy. Returns true on success.
     */
    private fun moveInto(context: Application, file: UniFile, targetParent: UniFile): Boolean {
        val name = file.name ?: return false

        // Fast path 1: both backed by java.io.File on the same filesystem.
        val srcPath = file.filePath
        val dstParentPath = targetParent.filePath
        if (srcPath != null && dstParentPath != null) {
            if (File(srcPath).renameTo(File(dstParentPath, name))) return true
        }

        // Fast path 2: SAF document move within the same tree (API 24+).
        val srcParent = file.parentFile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            srcParent != null &&
            DocumentsContract.isDocumentUri(context, file.uri) &&
            DocumentsContract.isDocumentUri(context, srcParent.uri) &&
            DocumentsContract.isDocumentUri(context, targetParent.uri)
        ) {
            try {
                val moved = DocumentsContract.moveDocument(
                    context.contentResolver,
                    file.uri,
                    srcParent.uri,
                    targetParent.uri,
                )
                if (moved != null) return true
            } catch (_: Throwable) {
                // fall through to copy
            }
        }

        // Fallback: recursive copy + delete.
        return if (copyRecursively(file, targetParent)) {
            file.delete()
        } else {
            false
        }
    }

    private fun copyRecursively(src: UniFile, targetParent: UniFile): Boolean {
        val name = src.name ?: return false
        return if (src.isDirectory) {
            val newDir = targetParent.findFile(name)?.takeIf { it.isDirectory }
                ?: targetParent.createDirectory(name)
                ?: return false
            src.listFiles().orEmpty().all { copyRecursively(it, newDir) }
        } else {
            val newFile = targetParent.findFile(name) ?: targetParent.createFile(name) ?: return false
            try {
                src.openInputStream().use { input ->
                    newFile.openOutputStream().use { output -> input.copyTo(output) }
                }
                true
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to copy ${src.name}" }
                false
            }
        }
    }

    /** Deletes a directory when it only contains marker files (e.g. .nomedia). */
    private fun deleteIfEmptyish(dir: UniFile) {
        val children = dir.listFiles().orEmpty()
        if (children.all { it.name == DiskUtil.NOMEDIA_FILE }) {
            children.forEach { it.delete() }
            dir.delete()
        }
    }
}
