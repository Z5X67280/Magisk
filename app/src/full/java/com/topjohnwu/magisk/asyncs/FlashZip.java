package com.topjohnwu.magisk.asyncs;

import android.app.Activity;
import android.net.Uri;
import android.view.View;

import com.topjohnwu.magisk.Const;
import com.topjohnwu.magisk.Data;
import com.topjohnwu.magisk.FlashActivity;
import com.topjohnwu.magisk.MagiskManager;
import com.topjohnwu.magisk.components.SnackbarMaker;
import com.topjohnwu.magisk.utils.Utils;
import com.topjohnwu.magisk.utils.ZipUtils;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class FlashZip extends ParallelTask<Void, Void, Integer> {

    private Uri mUri;
    private File mCachedFile;
    private List<String> console, logs;

    public FlashZip(Activity context, Uri uri, List<String> console, List<String> logs) {
        super(context);
        mUri = uri;
        this.console = console;
        this.logs = logs;
        mCachedFile = new File(context.getCacheDir(), "install.zip");
    }

    private boolean unzipAndCheck() throws Exception {
        ZipUtils.unzip(mCachedFile, mCachedFile.getParentFile(), "META-INF/com/google/android", true);
        return ShellUtils.fastCmdResult("grep -q '#MAGISK' " + new File(mCachedFile.getParentFile(), "updater-script"));
    }

    @Override
    protected Integer doInBackground(Void... voids) {
        MagiskManager mm = Data.MM();
        try {
            console.add("- 复制文件到临时文件夹......");

            mCachedFile.delete();
            try (
                InputStream in = mm.getContentResolver().openInputStream(mUri);
                OutputStream out = new BufferedOutputStream(new FileOutputStream(mCachedFile))
            ) {
                if (in == null) throw new FileNotFoundException();
                InputStream buf= new BufferedInputStream(in);
                ShellUtils.pump(buf, out);
            } catch (FileNotFoundException e) {
                console.add("! tan90°的URL！");
                throw e;
            } catch (IOException e) {
                console.add("! 无法将文件复制到缓存中！");
                throw e;
            }
            if (!unzipAndCheck()) return 0;
            console.add("- 安装中...... " + Utils.getNameFromUri(mm, mUri));
            if (!Shell.su("cd " + mCachedFile.getParent(),
                    "BOOTMODE=true sh update-binary dummy 1 " + mCachedFile)
                    .to(console, logs)
                    .exec().isSuccess())
                return -1;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        console.add("- 安装完毕！重启以生效！");
        return 1;
    }

    // -1 = error, manual install; 0 = invalid zip; 1 = success
    @Override
    protected void onPostExecute(Integer result) {
        FlashActivity activity = (FlashActivity) getActivity();
        Shell.su("rm -rf " + mCachedFile.getParent(), "rm -rf " + Const.TMP_FOLDER_PATH).submit();
        switch (result) {
            case -1:
                console.add("! 安装失败！");
                SnackbarMaker.showUri(getActivity(), mUri);
                break;
            case 0:
                console.add("! 这个zip文件并不是 Magisk Module!");
                break;
            case 1:
                // Reload modules
                Utils.loadModules();
                break;
        }
        activity.reboot.setVisibility(result > 0 ? View.VISIBLE : View.GONE);
        activity.buttonPanel.setVisibility(View.VISIBLE);
    }
}
