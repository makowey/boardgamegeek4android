package com.boardgamegeek.service;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;

import com.boardgamegeek.R;
import com.boardgamegeek.auth.Authenticator;
import com.boardgamegeek.io.BggService;
import com.boardgamegeek.model.Buddy;
import com.boardgamegeek.model.User;
import com.boardgamegeek.model.persister.BuddyPersister;
import com.boardgamegeek.provider.BggContract.Buddies;
import com.boardgamegeek.util.DateTimeUtils;
import com.boardgamegeek.util.PreferencesUtils;

import timber.log.Timber;

/**
 * Syncs the list of buddies. Only runs every few days.
 */
public class SyncBuddiesList extends SyncTask {
	public SyncBuddiesList(Context context, BggService service) {
		super(context, service);
	}

	@Override
	public void execute(Account account, SyncResult syncResult) {
		Timber.i("Syncing list of buddies...");
		try {
			if (!PreferencesUtils.getSyncBuddies(mContext)) {
				Timber.i("...buddies not set to sync");
				return;
			}

			long lastCompleteSync = Authenticator.getLong(mContext, SyncService.TIMESTAMP_BUDDIES);
			if (lastCompleteSync >= 0 && DateTimeUtils.howManyDaysOld(lastCompleteSync) < 3) {
				Timber.i("...skipping; we synced already within the last 3 days");
				return;
			}

			showNotification("Downloading list of GeekBuddies");
			User user = mService.user(account.name, 1, 1);// XXX: buddies don't seem to be paged at 100

			showNotification("Storing list of GeekBuddies");

			Authenticator.putInt(mContext, Authenticator.KEY_USER_ID, user.getId());

			BuddyPersister persister = new BuddyPersister(mContext);
			int count = 0;
			count += persister.saveList(Buddy.fromUser(user));
			count += persister.saveList(user.getBuddies());
			syncResult.stats.numEntries += count;
			Timber.i("Synced " + count + " buddies");

			showNotification("Discarding old GeekBuddies");
			// TODO: delete avatar images associated with this list
			// Actually, these are now only in the cache!
			ContentResolver resolver = mContext.getContentResolver();
			count = resolver.delete(Buddies.CONTENT_URI, Buddies.UPDATED_LIST + "<?",
				new String[] { String.valueOf(persister.getTimestamp()) });
			syncResult.stats.numDeletes += count;
			Timber.i("Removed " + count + " people who are no longer buddies");

			Authenticator.putLong(mContext, SyncService.TIMESTAMP_BUDDIES, persister.getTimestamp());
		} finally {
			Timber.i("...complete!");
		}
	}

	@Override
	public int getNotification() {
		return R.string.sync_notification_buddies_list;
	}
}
