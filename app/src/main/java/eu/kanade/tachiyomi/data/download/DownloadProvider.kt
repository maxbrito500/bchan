package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.math.BigDecimal
import java.util.Locale

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<manga>/<chapter>
 *
 * SY: downloads are no longer split per source. A single flat folder holds every series
 * regardless of the provider it was downloaded from, and chapter folders are named after the
 * chapter number (see [forcedChapterDirName]) so they are reused when switching providers.
 *
 * @param context the application context.
 */
class DownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Returns the root downloads directory if it exists.
     */
    fun findDownloadsRoot(): UniFile? = downloadsDir

    /**
     * Returns the download directory for a manga. For internal use only.
     *
     * @param mangaTitle the title of the manga to query.
     */
    internal fun getMangaDir(mangaTitle: String): Result<UniFile> {
        val downloadsDir = downloadsDir
        if (downloadsDir == null) {
            logcat(LogPriority.ERROR) { "Failed to create download directory" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_download_directory)),
            )
        }

        val mangaDirName = getMangaDirName(mangaTitle)
        val mangaDir = downloadsDir.createDirectory(mangaDirName)
        if (mangaDir == null) {
            val displayablePath = downloadsDir.displayablePath + "/$mangaDirName"
            logcat(LogPriority.ERROR) { "Failed to create manga download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        return Result.success(mangaDir)
    }

    /**
     * Returns the download directory for a manga if it exists.
     *
     * @param mangaTitle the title of the manga to query.
     */
    fun findMangaDir(mangaTitle: String): UniFile? {
        return downloadsDir?.findFile(getMangaDirName(mangaTitle))
    }

    // SY -->
    /**
     * Returns the archived cover file stored in a manga's download directory, if it exists.
     *
     * @param mangaTitle the title of the manga to query.
     */
    fun findMangaCover(mangaTitle: String): UniFile? {
        return findMangaDir(mangaTitle)
            ?.findFile(COVER_FILE_NAME)
            ?.takeIf { it.exists() }
    }
    // SY <--

    /**
     * Returns the download directory for a chapter if it exists.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param chapterUrl the url of the chapter to query.
     * @param chapterNumber the recognized number of the chapter to query.
     * @param mangaTitle the title of the manga to query.
     */
    fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        chapterNumber: Double,
        mangaTitle: String,
    ): UniFile? {
        val mangaDir = findMangaDir(mangaTitle)
        return getValidChapterDirNames(chapterName, chapterScanlator, chapterUrl, chapterNumber).asSequence()
            .mapNotNull { mangaDir?.findFile(it) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     */
    fun findChapterDirs(chapters: List<Chapter>, manga: Manga): Pair<UniFile?, List<UniFile>> {
        val mangaDir = findMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */) ?: return null to emptyList()
        return mangaDir to chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url, chapter.chapterNumber).asSequence()
                .mapNotNull { mangaDir.findFile(it) }
                .firstOrNull()
        }
    }

    // SY -->

    /**
     * Returns a list of all files in manga directory that don't match any of the given chapters.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     */
    fun findUnmatchedChapterDirs(
        chapters: List<Chapter>,
        manga: Manga,
    ): List<UniFile> {
        val mangaDir = findMangaDir(/* SY --> */ manga.ogTitle /* SY <-- */) ?: return emptyList()
        return mangaDir.listFiles().orEmpty().asList().filter {
            // Never treat the archived cover or the .nomedia marker as an unmatched
            // chapter, otherwise the cleanup job would delete them.
            if (it.name == COVER_FILE_NAME || it.name == DiskUtil.NOMEDIA_FILE) return@filter false
            chapters.find { chp ->
                getValidChapterDirNames(chp.name, chp.scanlator, chp.url, chp.chapterNumber).any { dir ->
                    mangaDir.findFile(dir) != null
                }
            } == null ||
                it.name?.endsWith(Downloader.TMP_DIR_SUFFIX) == true
        }
    }
    // SY <--

    /**
     * Returns the download directory name for a manga.
     *
     * @param mangaTitle the title of the manga to query.
     */
    fun getMangaDirName(mangaTitle: String): String {
        return DiskUtil.buildValidFilename(
            mangaTitle,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
        )
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * When the chapter number is recognized, a provider-independent number-based name is used
     * (see [forcedChapterDirName]); otherwise it falls back to the legacy provider-based name.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterUrl url of the chapter to query.
     * @param chapterNumber the recognized number of the chapter to query.
     */
    fun getChapterDirName(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        chapterNumber: Double,
        disallowNonAsciiFilenames: Boolean = libraryPreferences.disallowNonAsciiFilenames.get(),
        includeChapterUrlHash: Boolean = downloadPreferences.includeChapterUrlHash.get(),
    ): String {
        return forcedChapterDirName(chapterName, chapterNumber)
            ?: legacyChapterDirName(chapterName, chapterScanlator, chapterUrl, disallowNonAsciiFilenames, includeChapterUrlHash)
    }

    // SY -->
    /**
     * Builds the forced, provider-independent chapter directory name from the chapter number,
     * preserving any descriptive tag that trails the number (e.g. "Episode 1 - Raw" -> "episode001 - raw").
     *
     * Returns null when the chapter number is not recognized (< 0), so the caller falls back to the
     * legacy provider-based name.
     */
    private fun forcedChapterDirName(chapterName: String, chapterNumber: Double): String? {
        if (chapterNumber < 0) return null

        val intPart = chapterNumber.toLong()
        val numberStr = if (chapterNumber == intPart.toDouble()) {
            intPart.toString().padStart(3, '0')
        } else {
            val plain = BigDecimal.valueOf(chapterNumber).stripTrailingZeros().toPlainString()
            val frac = plain.substringAfter('.', "")
            intPart.toString().padStart(3, '0') + if (frac.isEmpty()) "" else ".$frac"
        }

        var dirName = "episode$numberStr"
        val tag = extractTrailingTag(chapterName, chapterNumber)
        if (!tag.isNullOrBlank()) {
            dirName += " - $tag"
        }
        // Keep names stable regardless of the non-ASCII setting so they match across providers/settings.
        return DiskUtil.buildValidFilename(dirName, DiskUtil.MAX_FILE_NAME_BYTES - 4)
    }

    /**
     * Extracts the descriptive text that trails the chapter number in [chapterName], normalized
     * (separators stripped, lower-cased). Returns null when nothing meaningful trails the number.
     *
     * Best-effort: when the number can't be located in the name, no tag is used.
     */
    private fun extractTrailingTag(chapterName: String, chapterNumber: Double): String? {
        val intPart = chapterNumber.toLong()
        val numPattern = if (chapterNumber == intPart.toDouble()) {
            // Allow leading zeros (e.g. "Episode 01") and a trailing ".0".
            "0*" + intPart.toString() + "(?:\\.0+)?"
        } else {
            Regex.escape(BigDecimal.valueOf(chapterNumber).stripTrailingZeros().toPlainString())
        }
        // Match the number not preceded/followed by another digit (a leading "." is a separator, allowed).
        val regex = Regex("(?<!\\d)$numPattern(?!\\d)")
        val match = regex.findAll(chapterName).lastOrNull() ?: return null

        val rest = chapterName.substring(match.range.last + 1)
        val separators = charArrayOf('-', ':', '.', ' ', '_', '(', ')', '[', ']', '#')
        val tag = rest
            .trim(*separators)
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ENGLISH)
        return tag.ifBlank { null }
    }
    // SY <--

    /**
     * Legacy provider-based chapter directory name (chapter name + scanlator + optional url hash).
     * Used as a fallback for unrecognized chapter numbers and to match downloads made before the
     * number-based naming was introduced.
     */
    private fun legacyChapterDirName(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        disallowNonAsciiFilenames: Boolean,
        includeChapterUrlHash: Boolean,
    ): String {
        var dirName = sanitizeChapterName(chapterName)
        if (!chapterScanlator.isNullOrBlank()) {
            dirName = chapterScanlator + "_" + dirName
        }
        // Subtract 7 bytes for hash and underscore, 4 bytes for .cbz
        dirName = DiskUtil.buildValidFilename(dirName, DiskUtil.MAX_FILE_NAME_BYTES - 11, disallowNonAsciiFilenames)
        if (includeChapterUrlHash) dirName += "_" + md5(chapterUrl).take(6)
        return dirName
    }

    /**
     * Returns list of names that might have been previously used as
     * the directory name for a chapter.
     * Add to this list if naming pattern ever changes.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterUrl url of the chapter to query.
     */
    private fun getLegacyChapterDirNames(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
    ): List<String> {
        val sanitizedChapterName = sanitizeChapterName(chapterName)
        val chapterNameV1 = DiskUtil.buildValidFilename(
            when {
                !chapterScanlator.isNullOrBlank() -> "${chapterScanlator}_$sanitizedChapterName"
                else -> sanitizedChapterName
            },
        )

        // Get the filename that would be generated if the user were
        // using the other value for the disallow non-ASCII
        // filenames setting. This ensures that chapters downloaded
        // before the user changed the setting can still be found.
        val otherChapterDirName =
            legacyChapterDirName(
                chapterName,
                chapterScanlator,
                chapterUrl,
                !libraryPreferences.disallowNonAsciiFilenames.get(),
                !downloadPreferences.includeChapterUrlHash.get(),
            )

        return buildList(2) {
            // Chapter name without hash (unable to handle duplicate
            // chapter names)
            add(chapterNameV1)
            add(otherChapterDirName)
        }
    }

    /**
     * Return the new name for the chapter (in case it's empty or blank)
     *
     * @param chapterName the name of the chapter
     */
    private fun sanitizeChapterName(chapterName: String): String {
        return chapterName.ifBlank {
            "Chapter"
        }
    }

    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        return getChapterDirName(oldChapter.name, oldChapter.scanlator, oldChapter.url, oldChapter.chapterNumber) !=
            getChapterDirName(newChapter.name, newChapter.scanlator, newChapter.url, newChapter.chapterNumber)
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * Includes both the forced number-based name and all legacy/provider-based candidates so
     * downloads made before the new naming (or with the url-hash setting) still match.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterUrl url of the chapter to query.
     * @param chapterNumber the recognized number of the chapter to query.
     */
    fun getValidChapterDirNames(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        chapterNumber: Double,
    ): List<String> {
        val legacyCanonical = legacyChapterDirName(
            chapterName,
            chapterScanlator,
            chapterUrl,
            libraryPreferences.disallowNonAsciiFilenames.get(),
            downloadPreferences.includeChapterUrlHash.get(),
        )
        val legacyChapterDirNames = getLegacyChapterDirNames(chapterName, chapterScanlator, chapterUrl)

        return buildList {
            // SY --> forced number-based name (canonical going forward)
            forcedChapterDirName(chapterName, chapterNumber)?.let {
                add(it)
                add("$it.cbz")
            }
            // SY <--

            // Folder of images
            add(legacyCanonical)
            // Archived chapters
            add("$legacyCanonical.cbz")

            if (chapterScanlator.isNullOrBlank()) {
                // Previously null scanlator fields were converted to "" due to a bug
                add("_$legacyCanonical")
                add("_$legacyCanonical.cbz")
            } else {
                // Legacy chapter directory name used in v0.9.2 and before
                add(DiskUtil.buildValidFilename(chapterName))
            }

            // any legacy names
            legacyChapterDirNames.forEach {
                add(it)
                add("$it.cbz")
            }
        }
    }

    // SY -->
    companion object {
        /**
         * Name of the cover archived alongside a manga's downloaded chapters.
         * Matches the local source convention (LocalCoverManager).
         */
        const val COVER_FILE_NAME = "cover.jpg"
    }
    // SY <--
}
