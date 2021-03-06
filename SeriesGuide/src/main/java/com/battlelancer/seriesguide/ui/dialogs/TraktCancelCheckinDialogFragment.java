package com.battlelancer.seriesguide.ui.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v7.app.AlertDialog;
import android.text.format.DateUtils;
import android.widget.Toast;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.enums.TraktAction;
import com.battlelancer.seriesguide.settings.TraktCredentials;
import com.battlelancer.seriesguide.traktapi.SgTrakt;
import com.battlelancer.seriesguide.util.ServiceUtils;
import com.battlelancer.seriesguide.util.TraktTask;
import com.battlelancer.seriesguide.util.TraktTask.InitBundle;
import com.uwetrottmann.trakt5.TraktV2;
import de.greenrobot.event.EventBus;
import java.io.IOException;

/**
 * Warns about an ongoing check-in, how long it takes until it is finished. Offers to override or
 * wait out.
 */
public class TraktCancelCheckinDialogFragment extends DialogFragment {

    private int mWait;

    /**
     * @param waitInMinutes The time to wait. If negative, will show as no time available.
     */
    public static TraktCancelCheckinDialogFragment newInstance(Bundle traktTaskData,
            int waitInMinutes) {
        TraktCancelCheckinDialogFragment f = new TraktCancelCheckinDialogFragment();
        f.setArguments(traktTaskData);
        f.mWait = waitInMinutes;
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = getActivity().getApplicationContext();
        final Bundle args = getArguments();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMessage(context.getString(R.string.traktcheckin_inprogress,
                mWait < 0 ? context.getString(R.string.not_available)
                        : DateUtils.formatElapsedTime(mWait)));

        builder.setPositiveButton(R.string.traktcheckin_cancel, new OnClickListener() {

            @Override
            @SuppressLint("CommitTransaction")
            public void onClick(DialogInterface dialog, int which) {
                AsyncTask<String, Void, String> cancelCheckinTask
                        = new AsyncTask<String, Void, String>() {

                    @Override
                    protected String doInBackground(String... params) {
                        // check for credentials
                        if (!TraktCredentials.get(context).hasCredentials()) {
                            return context.getString(R.string.trakt_error_credentials);
                        }

                        TraktV2 trakt = ServiceUtils.getTrakt(context);
                        try {
                            retrofit2.Response<Void> response = trakt.checkin()
                                    .deleteActiveCheckin()
                                    .execute();
                            if (response.isSuccessful()) {
                                return null;
                            } else {
                                if (SgTrakt.isUnauthorized(context, response)) {
                                    return context.getString(R.string.trakt_error_credentials);
                                }
                                SgTrakt.trackFailedRequest(context, "delete check-in", response);
                            }
                        } catch (IOException e) {
                            SgTrakt.trackFailedRequest(context, "delete check-in", e);
                        }

                        return context.getString(R.string.trakt_error_general);
                    }

                    @Override
                    protected void onPostExecute(String message) {
                        if (message == null) {
                            // all good
                            Toast.makeText(context, R.string.checkin_canceled_success_trakt,
                                    Toast.LENGTH_SHORT).show();

                            // relaunch the trakt task which called us to
                            // try the check in again
                            AsyncTaskCompat.executeParallel(new TraktTask(context, args));
                        } else {
                            // well, something went wrong
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                        }
                    }
                };

                AsyncTaskCompat.executeParallel(cancelCheckinTask);
            }
        });
        builder.setNegativeButton(R.string.traktcheckin_wait, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // broadcast check-in success
                EventBus.getDefault().post(new TraktTask.TraktActionCompleteEvent(
                        TraktAction.valueOf(args.getString(InitBundle.TRAKTACTION)), true, null));
            }
        });

        return builder.create();
    }
}
