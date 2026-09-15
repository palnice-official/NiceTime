package app.nicetime.android.core.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class HistoryRepository(context: Context) {
    private val dao = NiceTimeDatabase.get(context).libraryDao()

    fun observeProgress(): Flow<List<WatchProgressEntity>> = dao.observeProgress()

    suspend fun saveProgress(entity: WatchProgressEntity) = dao.saveProgress(entity)

    suspend fun clearHistory() = dao.clearHistory()
}
