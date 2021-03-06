package com.battlelancer.seriesguide.loaders;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.battlelancer.seriesguide.items.MovieDetails;
import com.battlelancer.seriesguide.util.DBUtils;
import com.battlelancer.seriesguide.util.MovieTools;
import com.uwetrottmann.androidutils.GenericSimpleLoader;
import com.uwetrottmann.tmdb2.entities.Movie;
import com.uwetrottmann.trakt5.entities.Ratings;
import java.util.Date;

import static com.battlelancer.seriesguide.provider.SeriesGuideContract.Movies;

/**
 * Tries to load current movie details from trakt and TMDb, if failing tries to fall back to local
 * database copy.
 */
public class MovieLoader extends GenericSimpleLoader<MovieDetails> {

    private int mTmdbId;

    public MovieLoader(Context context, int tmdbId) {
        super(context);
        mTmdbId = tmdbId;
    }

    @Override
    public MovieDetails loadInBackground() {
        // try loading from trakt and tmdb, this might return a cached response
        MovieDetails details = MovieTools.Download.getMovieDetails(getContext(), mTmdbId);

        // update local database
        updateLocalMovie(getContext(), details, mTmdbId);

        // fill in details from local database
        Cursor movieQuery = getContext().getContentResolver()
                .query(Movies.buildMovieUri(mTmdbId), MovieQuery.PROJECTION, null, null, null);
        if (movieQuery == null || !movieQuery.moveToFirst() || movieQuery.getCount() < 1) {
            if (movieQuery != null) {
                movieQuery.close();
            }
            // ensure list flags and watched flag are false on failure
            // (assumption: movie not in db, it has the truth, so can't be in any lists or watched)
            details.inCollection = false;
            details.inWatchlist = false;
            details.isWatched = false;
            return details;
        }

        // set local state for watched, collected and watchlist status
        // assumption: local db has the truth for these
        details.inCollection = DBUtils.restoreBooleanFromInt(
                movieQuery.getInt(MovieQuery.IN_COLLECTION));
        details.inWatchlist = DBUtils.restoreBooleanFromInt(
                movieQuery.getInt(MovieQuery.IN_WATCHLIST));
        details.isWatched = DBUtils.restoreBooleanFromInt(movieQuery.getInt(MovieQuery.WATCHED));
        // also use local state of user rating
        details.userRating = movieQuery.getInt(MovieQuery.RATING_USER);

        // only overwrite other info if remote data failed to load
        if (details.traktRatings() == null) {
            details.traktRatings(new Ratings());
            details.traktRatings().rating = (double) movieQuery.getInt(MovieQuery.RATING_TRAKT);
            details.traktRatings().votes = movieQuery.getInt(MovieQuery.RATING_VOTES_TRAKT);
        }
        if (details.tmdbMovie() == null) {
            details.tmdbMovie(new Movie());
            details.tmdbMovie().imdb_id = movieQuery.getString(MovieQuery.IMDB_ID);
            details.tmdbMovie().title = movieQuery.getString(MovieQuery.TITLE);
            details.tmdbMovie().overview = movieQuery.getString(MovieQuery.OVERVIEW);
            details.tmdbMovie().poster_path = movieQuery.getString(MovieQuery.POSTER);
            details.tmdbMovie().runtime = movieQuery.getInt(MovieQuery.RUNTIME_MIN);
            details.tmdbMovie().vote_average = movieQuery.getDouble(MovieQuery.RATING_TMDB);
            details.tmdbMovie().vote_count = movieQuery.getInt(MovieQuery.RATING_VOTES_TMDB);
            // if stored release date is Long.MAX, movie has no release date
            long releaseDateMs = movieQuery.getLong(MovieQuery.RELEASED_UTC_MS);
            details.tmdbMovie().release_date = releaseDateMs == Long.MAX_VALUE ? null
                    : new Date(releaseDateMs);
        }

        // clean up
        movieQuery.close();

        return details;
    }

    private static void updateLocalMovie(Context context,
            MovieDetails details, int tmdbId) {
        ContentValues values = MovieTools.buildBasicMovieContentValues(details);
        if (values.size() == 0) {
            // nothing to update, downloading probably failed :(
            return;
        }

        // if movie does not exist in database, will do nothing
        context.getContentResolver().update(Movies.buildMovieUri(tmdbId), values, null, null);
    }

    private interface MovieQuery {

        String[] PROJECTION = {
                Movies.TITLE, // 0
                Movies.OVERVIEW,
                Movies.RELEASED_UTC_MS,
                Movies.POSTER,
                Movies.WATCHED, // 4
                Movies.IN_COLLECTION,
                Movies.IN_WATCHLIST,
                Movies.IMDB_ID,
                Movies.RUNTIME_MIN,
                Movies.RATING_TMDB, // 9
                Movies.RATING_VOTES_TMDB,
                Movies.RATING_TRAKT,
                Movies.RATING_VOTES_TRAKT,
                Movies.RATING_USER // 13
        };

        int TITLE = 0;
        int OVERVIEW = 1;
        int RELEASED_UTC_MS = 2;
        int POSTER = 3;
        int WATCHED = 4;
        int IN_COLLECTION = 5;
        int IN_WATCHLIST = 6;
        int IMDB_ID = 7;
        int RUNTIME_MIN = 8;
        int RATING_TMDB = 9;
        int RATING_VOTES_TMDB = 10;
        int RATING_TRAKT = 11;
        int RATING_VOTES_TRAKT = 12;
        int RATING_USER = 13;
    }
}
