package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.PipedSession
import app.vimusic.android.transaction
import app.vimusic.providers.piped.Piped
import app.vimusic.providers.piped.models.Instance
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow

interface SyncSettingsRepository {
    fun observePipedSessions(): Flow<List<PipedSession>>
    suspend fun fetchInstances(): Result<List<Instance>>?
    suspend fun login(
        apiBaseUrl: Url,
        username: String,
        password: String
    ): app.vimusic.providers.piped.models.Session?

    fun saveSession(session: app.vimusic.providers.piped.models.Session, username: String)
    fun deleteSession(session: PipedSession)
}

object DatabaseSyncSettingsRepository : SyncSettingsRepository {
    override fun observePipedSessions(): Flow<List<PipedSession>> = Database.pipedSessions()

    override suspend fun fetchInstances(): Result<List<Instance>>? = Piped.getInstances()

    override suspend fun login(
        apiBaseUrl: Url,
        username: String,
        password: String
    ): app.vimusic.providers.piped.models.Session? =
        Piped.login(
            apiBaseUrl = apiBaseUrl,
            username = username,
            password = password
        )?.getOrNull()

    override fun saveSession(
        session: app.vimusic.providers.piped.models.Session,
        username: String
    ) {
        transaction {
            Database.insert(
                PipedSession(
                    apiBaseUrl = session.apiBaseUrl,
                    username = username,
                    token = session.token
                )
            )
        }
    }

    override fun deleteSession(session: PipedSession) {
        transaction { Database.delete(session) }
    }
}
