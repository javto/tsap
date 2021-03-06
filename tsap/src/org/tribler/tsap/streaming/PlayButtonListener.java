package org.tribler.tsap.streaming;

import org.tribler.tsap.Torrent;
import org.tribler.tsap.downloads.Download;
import org.tribler.tsap.downloads.XMLRPCDownloadManager;
import org.tribler.tsap.util.MainThreadPoller;
import org.tribler.tsap.util.Poller.IPollListener;
import org.tribler.tsap.util.Utility;
import org.videolan.vlc.gui.video.VideoPlayerActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;

/**
 * Class that makes sure that a torrent is downloaded (if needed), shows a
 * dialog with the download status and starts VLC when the video is ready to be
 * streamed.
 * 
 * @author Niels Spruit
 * 
 */
public class PlayButtonListener implements IPollListener {

	private final int POLLER_INTERVAL = 1000; // in ms

	private Torrent torrent;
	private String infoHash;
	private boolean needsToBeDownloaded;
	private MainThreadPoller mPoller;
	private VODDialogFragment vodDialog;
	private ProgressDialog pDialog;
	private boolean inVODMode = false;
	private Activity mActivity;
	private Download mDownload;

	/**
	 * @param torrent
	 *            The torrent of a which an info screen was opened
	 * @param activity
	 *            The activity in which the button resides
	 * @param needsToBeDownloaded
	 *            Indicates whether the torrent still needs to be downloaded
	 */
	public PlayButtonListener(Torrent torrent, Activity activity,
			boolean needsToBeDownloaded) {
		this.torrent = torrent;
		this.infoHash = torrent.getInfoHash();
		this.needsToBeDownloaded = needsToBeDownloaded;
		this.mActivity = activity;
		this.mPoller = new MainThreadPoller(this, POLLER_INTERVAL, mActivity);
	}

	/**
	 * When the 'Play video' button is pressed, this method will start
	 * downloading the torrent (if necessary), disable the button and show the
	 * VOD dialog
	 */
	public void onClick() {
		// start downloading the torrent
		if (needsToBeDownloaded) {
			startDownload();
		}

		// start waiting for VOD
		vodDialog = new VODDialogFragment(mPoller);
		vodDialog.show(mActivity.getFragmentManager(), "wait_vod");
		mPoller.start();
	}

	/**
	 * Starts downloading the torrent
	 */
	private void startDownload() {
		XMLRPCDownloadManager.getInstance().downloadTorrent(infoHash,
				torrent.getName());
	}

	/**
	 * Called when the Poller returns: starts streaming the video when its ready
	 * or updates the message of the vod dialog to the current download status
	 */
	@Override
	public void onPoll() {
		XMLRPCDownloadManager.getInstance().getProgressInfo(infoHash);
		mDownload = XMLRPCDownloadManager.getInstance().getCurrentDownload();
		pDialog = (ProgressDialog) vodDialog.getDialog();
		if (mDownload != null) {
			if (!mDownload.getTorrent().getInfoHash().equals(infoHash))
				return;

			if (mDownload.isVODPlayable())
				startStreaming();
			else
				update();
		}
	}

	/**
	 * Starts streaming the video with VLC and cleans up the dialog and poller
	 */
	private void startStreaming() {
		Uri videoLink = XMLRPCDownloadManager.getInstance().getVideoUri();
		if (videoLink != null) {
			Intent intent = new Intent(Intent.ACTION_VIEW,
					XMLRPCDownloadManager.getInstance().getVideoUri(),
					mActivity.getApplicationContext(),
					VideoPlayerActivity.class);
			mActivity.startActivity(intent);
			pDialog.dismiss();
		} else
			pDialog.setMessage("No video file could be found in the torrent");

		mPoller.stop();
	}

	/**
	 * Starts downloading in vod mode if necessary and updates the dialog
	 */
	private void update() {
		int statusCode = mDownload.getDownloadStatus().getStatus();
		if (statusCode == 3 && !inVODMode) {
			XMLRPCDownloadManager.getInstance().startVOD(infoHash);
			inVODMode = true;
		}
		updateDialog();
	}

	/**
	 * Updates the message of the dialog to the current download status
	 */
	private void updateDialog() {
		if (pDialog != null) {
			int statusCode = mDownload.getDownloadStatus().getStatus();
			if (statusCode == 3)
				updateVODMessage();
			else
				updateMessageToStatus(statusCode);
		}
	}

	/**
	 * Updates the dialog message to show the VOD ETA
	 */
	private void updateVODMessage() {
		pDialog.setMessage("Video starts playing in about "
				+ Utility.convertSecondsToString(mDownload.getVOD_ETA())
				+ " ("
				+ Utility.convertBytesPerSecToString(mDownload
						.getDownloadStatus().getDownloadSpeed()) + ").");
		pDialog.setProgress((int) Math.ceil(mDownload.getDownloadStatus().getProgress()*100));
	}

	/**
	 * Updated the dialog message to the current status
	 * 
	 * @param statusCode
	 *            The status code of the download
	 */
	private void updateMessageToStatus(int statusCode) {
		pDialog.setMessage("Download status: "
				+ Utility.convertDownloadStateIntToMessage(statusCode)
				+ ((statusCode == 2) ? " ("
						+ Math.round(mDownload.getDownloadStatus()
								.getProgress() * 100) + "%)" : ""));
	}
}
