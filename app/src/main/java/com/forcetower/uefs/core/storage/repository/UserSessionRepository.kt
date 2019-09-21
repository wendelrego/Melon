package com.forcetower.uefs.core.storage.repository

import android.content.SharedPreferences
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.forcetower.uefs.AppExecutors
import com.forcetower.uefs.core.model.service.UserSessionDTO
import com.forcetower.uefs.core.model.unes.UserSession
import com.forcetower.uefs.core.storage.database.UDatabase
import com.forcetower.uefs.core.storage.network.UService
import com.forcetower.uefs.core.util.isStudentFromUEFS
import timber.log.Timber
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSessionRepository @Inject constructor(
    private val database: UDatabase,
    private val service: UService,
    private val executors: AppExecutors,
    private val preferences: SharedPreferences
) {

    @WorkerThread
    fun onSessionStarted(): UserSession {
        val uuid = UUID.randomUUID().toString()
        val now = Calendar.getInstance().timeInMillis
        val session = UserSession(uuid, now)
        database.userSessionDao().insert(session)
        return session
    }

    @WorkerThread
    fun onUserInteraction() {
        val now = Calendar.getInstance().timeInMillis
        val session = database.userSessionDao().getLatestSession() ?: onSessionStarted()
        database.userSessionDao().updateLastInteraction(session.uid, now)
    }

    @WorkerThread
    fun syncSessions() {
        val sessions = database.userSessionDao().getUnsyncedSessions()
        if (sessions.isEmpty()) return

        val start = sessions.map { it.started }.min() ?: 0
        val end = sessions.map { it.started }.max() ?: 0
        val dto = UserSessionDTO(start, end, sessions)
        try {
            val response = service.saveSessions(dto).execute()
            if (response.isSuccessful) {
                sessions.forEach { database.userSessionDao().markSyncedSession(it.uid) }
            }
            Timber.d("Sessions sync completed")
        } catch (error: Throwable) {
            Timber.e(error, "It seems that the sync failed")
        }
    }

    @AnyThread
    fun onUserInteractionAsync() {
        if (!preferences.isStudentFromUEFS()) return
        executors.diskIO().execute { onUserInteraction() }
    }

    @AnyThread
    fun onSessionStartedAsync() {
        if (!preferences.isStudentFromUEFS()) return
        executors.diskIO().execute { onSessionStarted() }
    }
    
    @AnyThread
    fun onSyncSessionsAsync() {
        executors.networkIO().execute { syncSessions() }
    }
}