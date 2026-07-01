package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Moves manga into a virtual folder (or back to root when [folderId] is null).
 *
 * Folder membership is stored in the same manga<->category join as normal categories, but a manga
 * may be in at most one folder. This performs a surgical write: it preserves the manga's normal
 * (non-folder) category memberships and replaces only the folder membership, so dragging into a
 * folder never wipes the manga's tab-categories and moving between folders is atomic.
 */
class MoveMangaToFolder(
    private val categoryRepository: CategoryRepository,
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long, folderId: Long?) {
        await(listOf(mangaId), folderId)
    }

    suspend fun await(mangaIds: List<Long>, folderId: Long?) {
        try {
            for (mangaId in mangaIds) {
                val current = categoryRepository.getCategoriesByMangaId(mangaId)
                // Keep normal tab-categories; drop every folder membership (enforces single-folder).
                val keptIds = current.filterNot { it.isFolder }.map { it.id }
                val resultIds = if (folderId != null) keptIds + folderId else keptIds
                mangaRepository.setMangaCategories(mangaId, resultIds)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
