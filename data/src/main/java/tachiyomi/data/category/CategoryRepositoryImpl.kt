package tachiyomi.data.category

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class CategoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, CategoryMapper::mapCategory) }
    }

    override suspend fun getAll(): List<Category> {
        return handler.awaitList { categoriesQueries.getCategories(CategoryMapper::mapCategory) }
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { categoriesQueries.getCategories(CategoryMapper::mapCategory) }
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory)
        }
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory)
        }
    }

    override suspend fun getFolders(): List<Category> {
        return handler.awaitList { categoriesQueries.getFolders(CategoryMapper::mapCategory) }
    }

    override fun getFoldersAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { categoriesQueries.getFolders(CategoryMapper::mapCategory) }
    }

    // SY -->
    override suspend fun insert(category: Category): Long {
        return handler.awaitOneExecutable(true) {
            categoriesQueries.insert(
                name = category.name,
                order = category.order,
                flags = category.flags,
                version = category.version,
                uid = category.uid,
                last_modified_at = category.lastModifiedAt,
                isFolder = if (category.isFolder) 1L else 0L,
                cover = category.cover,
                locked = if (category.locked) 1L else 0L,
            )
            categoriesQueries.selectLastInsertedRowId()
        }
    }
    // SY <--

    override suspend fun setFolderCover(categoryId: Long, cover: String?) {
        handler.await {
            categoriesQueries.setFolderCover(cover = cover, categoryId = categoryId)
        }
    }

    override suspend fun updatePartial(update: CategoryUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartial(updates: List<CategoryUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun Database.updatePartialBlocking(update: CategoryUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            version = update.version,
            uid = update.uid,
            last_modified_at = update.lastModifiedAt,
            isSyncing = null,
            isFolder = update.isFolder?.let { if (it) 1L else 0L },
            cover = update.cover,
            locked = update.locked?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }

    override suspend fun updateAllFlags(flags: Long?) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun delete(categoryId: Long) {
        handler.await {
            categoriesQueries.delete(
                categoryId = categoryId,
            )
        }
    }
}
