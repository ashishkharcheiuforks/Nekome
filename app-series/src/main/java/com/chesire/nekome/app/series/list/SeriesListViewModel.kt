package com.chesire.nekome.app.series.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.chesire.nekome.core.AuthCaster
import com.chesire.nekome.core.extensions.postError
import com.chesire.nekome.core.extensions.postSuccess
import com.chesire.nekome.core.flags.AsyncState
import com.chesire.nekome.core.flags.UserSeriesStatus
import com.chesire.nekome.core.models.SeriesModel
import com.chesire.nekome.series.SeriesRepository
import com.chesire.nekome.server.Resource
import com.hadilq.liveevent.LiveEvent
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel to use with the [SeriesListFragment], handles sending updates for a series.
 */
class SeriesListViewModel @Inject constructor(
    private val repo: SeriesRepository,
    private val authCaster: AuthCaster
) : ViewModel() {
    val series = repo.getSeries().asLiveData()
    private val _deletionStatus = LiveEvent<AsyncState<SeriesModel, SeriesListDeleteError>>()
    val deletionStatus: LiveData<AsyncState<SeriesModel, SeriesListDeleteError>> = _deletionStatus

    private val _refreshStatus = LiveEvent<AsyncState<Any, Any>>()
    val refreshStatus: LiveData<AsyncState<Any, Any>>
        get() = _refreshStatus

    /**
     * Sends an update for a series, will fire [callback] on completion.
     */
    fun updateSeries(
        userSeriesId: Int,
        newProgress: Int,
        newUserSeriesStatus: UserSeriesStatus,
        callback: (Resource<SeriesModel>) -> Unit
    ) = viewModelScope.launch {
        val response = repo.updateSeries(userSeriesId, newProgress, newUserSeriesStatus)
        if (response is Resource.Error && response.code == Resource.Error.CouldNotRefresh) {
            authCaster.issueRefreshingToken()
        } else {
            callback(response)
        }
    }

    /**
     * Sends a delete request to the repository, will notify status on [deletionStatus] with
     * [SeriesListDeleteError.DeletionFailure] if a failure occurs.
     */
    fun deleteSeries(seriesModel: SeriesModel) = viewModelScope.launch {
        val response = repo.deleteSeries(seriesModel)
        if (response is Resource.Error) {
            if (response.code == Resource.Error.CouldNotRefresh) {
                authCaster.issueRefreshingToken()
            } else {
                _deletionStatus.postError(
                    seriesModel,
                    SeriesListDeleteError.DeletionFailure
                )
            }
        }
    }

    /**
     * Forces a refresh of all series.
     */
    fun refreshAllSeries() = viewModelScope.launch {
        val syncCommands = listOf(
            repo.refreshAnime(),
            repo.refreshManga()
        )

        if (syncCommands.any { it is Resource.Error }) {
            _refreshStatus.postError(Any())
        } else {
            _refreshStatus.postSuccess(Any())
        }
    }
}
