package org.tribler.tsap.downloads;

import org.tribler.tsap.R;
import org.tribler.tsap.Torrent;
import org.tribler.tsap.streaming.PlayButtonListener;
import org.tribler.tsap.util.MainThreadPoller;
import org.tribler.tsap.util.Poller.IPollListener;
import org.tribler.tsap.util.ThumbnailUtils;
import org.tribler.tsap.util.Utility;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Activity that shows detailed information of a download
 * 
 * @author Dirk Schut & Niels Spruit
 * 
 */
public class DownloadActivity extends Activity implements IPollListener {
	private ActionBar mActionBar;
	private Download mDownload;
	private Torrent mTorrent;
	private View mView;
	private MainThreadPoller mPoller;

	public final static String INTENT_MESSAGE = "org.tribler.tsap.DownloadActivity.IntentMessage";

	/**
	 * Sets the desired options in the action bar
	 * 
	 * @param title
	 *            The title to be displayed in the action bar
	 */
	private void setupActionBar(String title) {
		mActionBar = getActionBar();
		mActionBar.setTitle(title);
		mActionBar.setDisplayHomeAsUpEnabled(true);
	}

	/**
	 * Fills the layout with the current values of the download
	 */
	private void fillLayout() {
		fillInfoLayout();
		fillThumbnail();
		fillProgressLayout();

		TextView descr = (TextView) mView
				.findViewById(R.id.download_info_description);
		descr.setText("");
	}

	/**
	 * Loads the thumbnail into the view
	 */
	private void fillThumbnail() {
		ImageView thumb = (ImageView) mView
				.findViewById(R.id.download_info_thumbnail);

		ThumbnailUtils.loadThumbnail(
				ThumbnailUtils.getThumbnailLocation(mTorrent.getInfoHash()),
				thumb, this);
	}

	/**
	 * Fills the views of the information of the download
	 */
	private void fillInfoLayout() {
		DownloadStatus downStat = mDownload.getDownloadStatus();

		TextView size = (TextView) mView
				.findViewById(R.id.download_info_filesize);
		size.setText(Utility.convertBytesToString(mTorrent.getSize()));

		TextView download = (TextView) mView
				.findViewById(R.id.download_info_down_speed);
		download.setText(Utility.convertBytesPerSecToString(downStat
				.getDownloadSpeed()));

		TextView upload = (TextView) mView
				.findViewById(R.id.download_info_up_speed);
		upload.setText(Utility.convertBytesPerSecToString(downStat
				.getUploadSpeed()));

		TextView availability = (TextView) mView
				.findViewById(R.id.download_info_availability);
		availability.setText(Integer.toString(mDownload.getAvailability()));
	}

	/**
	 * Fills the status progress views with the correct values
	 * 
	 * @param downStat
	 *            The status of the download
	 * @param statusCode
	 */
	private void fillProgressLayout() {
		DownloadStatus downStat = mDownload.getDownloadStatus();
		int statusCode = downStat.getStatus();
		TextView status = (TextView) mView
				.findViewById(R.id.download_info_status_text);
		status.setText(Utility.convertDownloadStateIntToMessage(statusCode)
				+ ((statusCode == 2 || statusCode == 3) ? " ("
						+ Math.round(downStat.getProgress() * 100) + "%)" : ""));

		TextView eta = (TextView) mView
				.findViewById(R.id.download_info_eta_text);
		eta.setText((statusCode == 3) ? Utility.convertSecondsToString(downStat
				.getETA()) : "Unknown");

		ProgressBar bar = (ProgressBar) mView
				.findViewById(R.id.download_info_progress_bar);
		bar.setProgress((int) (100 * downStat.getProgress()));
	}

	/**
	 * Initializes the view and the instance variables
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mView = getWindow().getDecorView().getRootView();
		setContentView(R.layout.activity_download);

		Intent intent = getIntent();
		mDownload = (Download) intent.getSerializableExtra(INTENT_MESSAGE);
		mTorrent = mDownload.getTorrent();

		setupActionBar(mTorrent.getName());
		fillLayout();

		mPoller = new MainThreadPoller(this, 2000, this);
		mPoller.start();
	}

	/**
	 * Pauses polling
	 */
	@Override
	public void onPause() {
		super.onPause();
		mPoller.stop();
	}

	/**
	 * Resumes the poller
	 */
	@Override
	public void onResume() {
		super.onResume();
		mPoller.start();
	}

	/**
	 * Called when one of the icons in the start bar is tapped: When the home
	 * icon is tapped, go back. If any other icon is tapped do the default
	 * action.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		// Handle presses on the action bar items
		switch (menuItem.getItemId()) {
		case android.R.id.home:
			onBackPressed();
			return true;
		case R.id.action_remove:
			onRemovePressed(mTorrent, this, true);
			return true;
		case R.id.action_stream:
			onStreamPressed(mTorrent, this);
			return true;
		default:
			return super.onOptionsItemSelected(menuItem);
		}
	}

	/**
	 * Called when the poller time expired: updated the progress of the current
	 * download
	 */
	@Override
	public void onPoll() {
		String infohash = mTorrent.getInfoHash();
		XMLRPCDownloadManager.getInstance().getProgressInfo(infohash);
		Download currDownload = XMLRPCDownloadManager.getInstance()
				.getCurrentDownload();
		if (currDownload != null
				&& currDownload.getTorrent().getInfoHash().equals(infohash)) {
			mDownload = currDownload;
			fillLayout();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_download_activity, menu);
		return true;
	}

	/**
	 * Will download the selected torrent.
	 * 
	 * @param torrent
	 *            the Torrent to be downloaded
	 */
	public static void onDownloadPressed(Torrent torrent) {
		XMLRPCDownloadManager.getInstance().downloadTorrent(
				torrent.getInfoHash(), torrent.getName());
	}

	/**
	 * Will start the stream by simulating a buttonclick
	 * 
	 * @param torrent
	 *            the torrent to be removed
	 * @param activity
	 *            activity from which the method is called
	 */
	public static void onStreamPressed(Torrent torrent, Activity activity) {
		PlayButtonListener onClickListener = new PlayButtonListener(torrent,
				activity, false);
		onClickListener.onClick();
	}

	/**
	 * 
	 * Will remove the torrent and ask if also the data should be deleted
	 * 
	 * @param torrent
	 *            the torrent to be removed
	 * @param activity
	 *            activity from which the method is called
	 * @param onBackPress
	 *            if the back button should be pressed after removing the
	 *            torrent
	 */
	public static void onRemovePressed(final Torrent torrent,
			final Activity activity, final boolean onBackPress) {
		// Show dialog
		AlertDialog.Builder alertRemove = new AlertDialog.Builder(activity);
		alertRemove
				.setTitle(R.string.remove_download_dialog_title)
				.setMessage(R.string.remove_download_dialog_message)
				// Android.R.string.yes == Ok -
				// https://code.google.com/p/android/issues/detail?id=3713
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								XMLRPCDownloadManager.getInstance()
										.deleteTorrent(torrent.getInfoHash(),
												true);
								if (onBackPress)
									activity.onBackPressed();
							}
						})
				// Android.R.string.no == Cancel -
				// https://code.google.com/p/android/issues/detail?id=3713
				.setNegativeButton(R.string.no,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								XMLRPCDownloadManager.getInstance()
										.deleteTorrent(torrent.getInfoHash(),
												false);
								if (onBackPress)
									activity.onBackPressed();
							}
						})
				.setNeutralButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
							}
						}).show();
	}
}