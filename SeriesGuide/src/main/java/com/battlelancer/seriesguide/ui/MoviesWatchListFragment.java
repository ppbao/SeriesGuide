package com.battlelancer.seriesguide.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.adapters.MoviesCursorAdapter;
import com.battlelancer.seriesguide.settings.MoviesDistillationSettings;
import com.battlelancer.seriesguide.util.MovieTools;

import static com.battlelancer.seriesguide.provider.SeriesGuideContract.Movies;

/**
 * Loads and displays the users trakt movie watchlist.
 */
public class MoviesWatchListFragment extends MoviesBaseFragment {

    private static final int CONTEXT_WATCHLIST_REMOVE_ID = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        emptyView.setText(R.string.movies_watchlist_empty);

        return v;
    }

    @Override
    public void onPopupMenuClick(View v, final int movieTmdbId) {
        PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
        popupMenu.getMenu().add(0, CONTEXT_WATCHLIST_REMOVE_ID, 0, R.string.watchlist_remove);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case CONTEXT_WATCHLIST_REMOVE_ID: {
                        MovieTools.removeFromWatchlist(getContext(), movieTmdbId);
                        return true;
                    }
                }
                return false;
            }
        });
        popupMenu.show();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle args) {
        return new CursorLoader(getContext(), Movies.CONTENT_URI,
                MoviesCursorAdapter.MoviesQuery.PROJECTION, Movies.SELECTION_WATCHLIST, null,
                MoviesDistillationSettings.getSortQuery(getContext()));
    }

    @Override
    protected int getLoaderId() {
        return MoviesActivity.WATCHLIST_LOADER_ID;
    }
}
