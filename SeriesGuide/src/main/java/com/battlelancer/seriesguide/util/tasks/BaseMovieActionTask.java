package com.battlelancer.seriesguide.util.tasks;

import android.content.Context;
import android.support.annotation.NonNull;
import com.battlelancer.seriesguide.backend.HexagonTools;
import com.battlelancer.seriesguide.settings.TraktCredentials;
import com.battlelancer.seriesguide.traktapi.SgTrakt;
import com.battlelancer.seriesguide.util.MovieTools;
import com.battlelancer.seriesguide.util.ServiceUtils;
import com.uwetrottmann.androidutils.AndroidUtils;
import com.uwetrottmann.seriesguide.backend.movies.Movies;
import com.uwetrottmann.seriesguide.backend.movies.model.Movie;
import com.uwetrottmann.seriesguide.backend.movies.model.MovieList;
import com.uwetrottmann.trakt5.TraktV2;
import com.uwetrottmann.trakt5.entities.MovieIds;
import com.uwetrottmann.trakt5.entities.SyncItems;
import com.uwetrottmann.trakt5.entities.SyncMovie;
import com.uwetrottmann.trakt5.entities.SyncResponse;
import com.uwetrottmann.trakt5.services.Sync;
import de.greenrobot.event.EventBus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Base class for executing movie actions.
 */
public abstract class BaseMovieActionTask extends BaseActionTask {

    private final int movieTmdbId;

    public BaseMovieActionTask(Context context, int movieTmdbId) {
        super(context);
        this.movieTmdbId = movieTmdbId;
    }

    @Override
    protected Integer doInBackground(Void... params) {
        if (isCancelled()) {
            return null;
        }

        // if sending to service, check for connection
        if (isSendingToHexagon() || isSendingToTrakt()) {
            if (!AndroidUtils.isNetworkConnected(getContext())) {
                return ERROR_NETWORK;
            }
        }

        // send to hexagon
        if (isSendingToHexagon()) {
            Movie movie = new Movie();
            movie.setTmdbId(movieTmdbId);

            setHexagonMovieProperties(movie);

            List<Movie> movies = new ArrayList<>();
            movies.add(movie);

            MovieList movieList = new MovieList();
            movieList.setMovies(movies);

            try {
                Movies moviesService = HexagonTools.getMoviesService(getContext());
                if (moviesService == null) {
                    return ERROR_HEXAGON_API;
                }
                moviesService.save(movieList).execute();
            } catch (IOException e) {
                HexagonTools.trackFailedRequest(getContext(), "save movie", e);
                return ERROR_HEXAGON_API;
            }
        }

        // send to trakt
        if (isSendingToTrakt()) {
            TraktV2 trakt = ServiceUtils.getTrakt(getContext());
            if (!TraktCredentials.get(getContext()).hasCredentials()) {
                return ERROR_TRAKT_AUTH;
            }

            Sync traktSync = trakt.sync();
            SyncItems items = new SyncItems().movies(
                    new SyncMovie().id(MovieIds.tmdb(movieTmdbId)));

            try {
                Response<SyncResponse> response = doTraktAction(traktSync, items).execute();
                if (response.isSuccessful()) {
                    if (isMovieNotFound(response.body())) {
                        return ERROR_TRAKT_API_NOT_FOUND;
                    }
                } else {
                    if (SgTrakt.isUnauthorized(getContext(), response)) {
                        return ERROR_TRAKT_AUTH;
                    }
                    SgTrakt.trackFailedRequest(getContext(), getTraktAction(), response);
                    return ERROR_TRAKT_API;
                }
            } catch (IOException e) {
                SgTrakt.trackFailedRequest(getContext(), getTraktAction(), e);
                return ERROR_TRAKT_API;
            }
        }

        // update local state
        if (!doDatabaseUpdate(getContext(), movieTmdbId)) {
            return ERROR_DATABASE;
        }

        return SUCCESS;
    }

    @Override
    protected void onPostExecute(Integer result) {
        super.onPostExecute(result);

        // always post event so UI releases locks
        EventBus.getDefault().post(new MovieTools.MovieChangedEvent(movieTmdbId));
    }

    private static boolean isMovieNotFound(SyncResponse response) {
        return response.not_found != null && response.not_found.movies != null
                && response.not_found.movies.size() != 0;
    }

    protected abstract boolean doDatabaseUpdate(Context context, int movieTmdbId);

    /**
     * Set properties to send to hexagon. To disable hexagon uploading, override {@link
     * #isSendingToHexagon()}}.
     */
    protected abstract void setHexagonMovieProperties(Movie movie);

    @NonNull
    protected abstract String getTraktAction();

    @NonNull
    protected abstract Call<SyncResponse> doTraktAction(Sync traktSync, SyncItems items);
}
