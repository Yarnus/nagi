package dev.nagi.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.nagi.app.data.*
import dev.nagi.sdk.NgaClient
import dev.nagi.sdk.SessionStore
import dev.nagi.sdk.createNgaClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun sessions(@ApplicationContext context: Context): SessionStore = AndroidSessionStore(context)
    @Provides @Singleton fun messageWrites(@ApplicationContext context: Context) =
        MessageWriteGuard(context.getSharedPreferences("message_write_guard", Context.MODE_PRIVATE))
    @Provides @Singleton fun browser(): BrowserCookies = AndroidBrowserCookies()
    @Provides @Singleton fun client(sessions: SessionStore): NgaClient = createNgaClient(sessions = sessions)
    @Provides @Singleton fun databases(@ApplicationContext context: Context): ReadingDaoProvider = AccountDatabases(context)
    @Provides @Singleton fun releaseChecker(): ReleaseChecker = GitHubReleaseChecker()
    @Provides @Singleton fun releaseDownloader(@ApplicationContext context: Context): dev.nagi.app.data.ReleaseDownloader =
        dev.nagi.app.data.GitHubReleaseDownloader(context)
    @Provides @Singleton fun updateCacheStore(@ApplicationContext context: Context): UpdateCacheStore =
        DataStoreUpdateCacheStore(context)
    @Provides @Singleton fun releaseUpdates(
        checker: ReleaseChecker,
        cacheStore: UpdateCacheStore,
        downloader: dev.nagi.app.data.ReleaseDownloader,
    ): ReleaseUpdateRepository = ReleaseUpdateRepository(checker, cacheStore, downloader = downloader)
}
