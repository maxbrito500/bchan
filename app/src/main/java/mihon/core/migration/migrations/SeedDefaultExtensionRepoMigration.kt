package mihon.core.migration.migrations

import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

/**
 * Seeds the default extension repository (Keiyoushi, the community-maintained repo for
 * Mihon/Tachiyomi forks) so the app ships with a working list of installable extensions.
 *
 * Runs always (so it also applies to fresh installs, which only execute [Migration.isAlways]
 * migrations) but only when no repository is configured yet. This seeds first launch without
 * ever re-adding a repo the user has intentionally removed or replaced.
 */
class SeedDefaultExtensionRepoMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val extensionRepoRepository =
            migrationContext.get<ExtensionRepoRepository>() ?: return@withIOContext false

        // Only seed when the user has no repositories configured.
        if (extensionRepoRepository.getAll().isNotEmpty()) return@withIOContext false

        try {
            extensionRepoRepository.upsertRepo(
                baseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
                name = "Keiyoushi",
                shortName = "Keiyoushi",
                website = "https://github.com/keiyoushi/extensions",
                signingKeyFingerprint = "NOFINGERPRINT-keiyoushi",
            )
        } catch (e: SaveExtensionRepoException) {
            logcat(LogPriority.ERROR, e) { "Error seeding default extension repo" }
        }
        return@withIOContext true
    }
}
