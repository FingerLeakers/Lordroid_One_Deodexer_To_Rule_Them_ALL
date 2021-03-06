/*
 *  Lordroid One Deodexer To Rule Them All
 * 
 *  Copyright 2016 Rachid Boudjelida <rachidboudjelida@gmail.com>
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package deodex.controlers;

import java.io.File;
import java.util.ArrayList;

import com.alee.laf.progressbar.WebProgressBar;

import deodex.R;
import deodex.S;
import deodex.obj.ApkLegacy;
import deodex.tools.Deodexer;
import deodex.tools.FailTracker;
import deodex.tools.FilesUtils;
import deodex.tools.Logger;
import deodex.tools.Zip;

public class ApkWorkerLegacy implements Watchable, Runnable {

	public WebProgressBar progressBar = new WebProgressBar();
	ArrayList<File> apkList;
	LoggerPan logPan;
	boolean doSign;
	boolean doZipalign;
	ThreadWatcher threadWatcher;
	File tempFolder;
	private boolean signStatus = false;
	private boolean zipAlignStatus = false;

	/**
	 * the constructor of a Legacy apk deodexer
	 * 
	 * @param apkList
	 *            : list of apks to be deodexed
	 * @param logPan
	 *            : a LoggerPan in which it will log all operations
	 * @param tempFolder
	 *            : the tempFolder that will be used for scratch Note : make
	 *            sure this folder is only used by this instance to avoid any
	 *            conflicts
	 * @param doSign
	 *            : if true the apk will be resigned
	 * @param doZipalign
	 *            : if true the apk will be zipaligned
	 */
	public ApkWorkerLegacy(ArrayList<File> apkList, LoggerPan logPan, File tempFolder, boolean doSign,
			boolean doZipalign) {
		this.apkList = apkList;
		this.logPan = logPan;
		this.doSign = doSign;
		this.doZipalign = doZipalign;
		this.tempFolder = tempFolder;
		this.tempFolder.mkdirs();
		progressBar.setMinimum(0);
		progressBar.setMaximum(this.apkList.size() > 0 ? this.apkList.size() : 1);
		progressBar.setStringPainted(true);
	}

	// FIXME change this to setThreadWatcher() to avoid confusion
	/**
	 * @param watcher
	 *            : the watcher to set this instance only accept one watcher if
	 *            you set a new one the previews one will be forgoten
	 */
	@Override
	public void addThreadWatcher(ThreadWatcher watcher) {
		this.threadWatcher = watcher;
	}

	/**
	 * will make a serie of actions to deodex the parameter File apk will return
	 * true only if all tasks are successful all the failed tasks will be sent
	 * to the logger
	 * 
	 * @param apk
	 * @return true only if the apk was deodexed
	 */
	private boolean deodexApk(ApkLegacy apk) {
		boolean copyStatus = false;
		copyStatus = apk.copyNeededFiles(tempFolder);

		if (!copyStatus) {
			this.logPan.addLog(R.getString(S.LOG_WARNING) + "[" + apk.origApk.getName() + "]"
					+ R.getString("log.copy.to.tmp.failed"));
			return false;
		} else {
			// we deodex now !
			boolean deodexStatus = false;
			deodexStatus = Deodexer.deoDexApkLegacy(apk.tempOdex, apk.classes);

			if (!deodexStatus) {
				this.logPan.addLog(R.getString(S.LOG_WARNING) + "[" + apk.origApk.getName() + "]"
						+ R.getString("log.deodex.failed"));
				return false;
			} else {
				ArrayList<File> classes = new ArrayList<File>();
				classes.add(apk.classes);
				boolean putBack = false;
				putBack = Zip.addFilesToExistingZip(apk.tempApk, classes);
				if (!putBack) {
					this.logPan.addLog(R.getString(S.LOG_WARNING) + "[" + apk.origApk.getName() + "]"
							+ R.getString("log.add.classes.failed"));
				} else {
					if (this.doSign) {
						signStatus = Deodexer.signApk(apk.tempApk, apk.tempSigned);
						if (!signStatus)
							apk.tempApk.renameTo(apk.tempSigned);
					} else {
						apk.tempApk.renameTo(apk.tempSigned);
					}
					if (this.doZipalign) {
						this.zipAlignStatus = Zip.zipAlignAPk(apk.tempSigned, apk.tempZipaligned);
						if (!this.zipAlignStatus)
							apk.tempSigned.renameTo(apk.tempZipaligned);
					} else {
						apk.tempSigned.renameTo(apk.tempZipaligned);
					}

				}

			}

		}

		// if we got here all things are right put stuffBack
		boolean pushBack = apk.tempZipaligned.renameTo(apk.origApk);
		if (pushBack)
			FilesUtils.deleteRecursively(apk.origOdex);
		else {
			this.logPan.addLog(R.getString(S.LOG_ERROR) + "[" + apk.origApk.getName() + "]"
					+ R.getString("log.putback.apk.failed"));
			return false;
		}

		// clean temp dir we don't wanna
		FilesUtils.deleteRecursively(apk.tempApk);
		FilesUtils.deleteRecursively(apk.tempOdex);
		FilesUtils.deleteRecursively(apk.classes);
		FilesUtils.deleteRecursively(apk.tempSigned);
		FilesUtils.deleteRecursively(apk.tempZipaligned);

		return true;
	}

	/**
	 * when all tasks are done we call this one to tell the watcher we are done
	 */
	private void finalMove() {
		progressBar.setValue(progressBar.getMaximum());
		progressBar.setString(R.getString("progress.done"));
		progressBar.setEnabled(false);
		this.threadWatcher.updateProgress();
		threadWatcher.done(this);
	}

	/**
	 * @return the progressBar
	 */
	public WebProgressBar getProgressBar() {
		return progressBar;
	}

	/**
	 * 
	 * @return int 0<=i=> 100 percentage of the current progress
	 */
	private String percent() {
		return (this.progressBar.getValue() * 100 / this.progressBar.getMaximum()) + "%";
	}

	@Override
	public void run() {
		if (apkList != null && apkList.size() > 0) {
			for (File f : this.apkList) {
				ApkLegacy apk = new ApkLegacy(f);
				boolean success = this.deodexApk(apk);
				if (success) {
					logPan.addLog(
							R.getString(S.LOG_INFO) + "[" + apk.origApk.getName() + "]" + R.getString(S.LOG_SUCCESS)
									+ (this.doSign ? (this.signStatus ? R.getString("log.resign.ok")
											: R.getString("log.resign.fail")) : "")
									+ (this.doZipalign ? (this.zipAlignStatus ? R.getString("log.zipalign.ok")
											: R.getString("log.zipalign.fail")) : ""));
				} else {
					apk.reverseMove();
					logPan.addLog(
							R.getString(S.LOG_ERROR) + "[" + apk.origApk.getName() + "]" + R.getString(S.LOG_FAIL));
					FailTracker.addFailed(apk.origApk);
				}
				progressBar.setValue(progressBar.getValue() + 1);
				progressBar.setString(R.getString("progress.apks") + " " + this.percent());
				this.threadWatcher.updateProgress();
			}
			finalMove();
		} else {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Logger.appendLog("[ApkWorkerLegacy][EX]" + e.getStackTrace());
				e.printStackTrace();
				// lets make sure the whatcher is always updated even when an
				// Exception is thrown
				finalMove();
			}
		}

	}

	/**
	 * Note : only one watcher can watch this class
	 * 
	 * @param progressBar
	 *            the progressBar to set
	 */
	public void setProgressBar(WebProgressBar progressBar) {
		this.progressBar = progressBar;
	}

}
