package tachiyomi.data.category

import tachiyomi.domain.category.model.Category

object CategoryMapper {
    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        version: Long,
        uid: Long,
        lastModifiedAt: Long,
        isFolder: Long,
        cover: String?,
        locked: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            version = version,
            uid = uid,
            lastModifiedAt = lastModifiedAt,
            isFolder = isFolder == 1L,
            cover = cover,
            locked = locked == 1L,
        )
    }
}
