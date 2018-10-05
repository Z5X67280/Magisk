package com.topjohnwu.magisk;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import com.topjohnwu.magisk.components.AboutCardRow;
import com.topjohnwu.magisk.receivers.BootReceiver;
import com.topjohnwu.magisk.receivers.ManagerUpdate;
import com.topjohnwu.magisk.receivers.PackageReceiver;
import com.topjohnwu.magisk.receivers.RebootReceiver;
import com.topjohnwu.magisk.receivers.ShortcutReceiver;
import com.topjohnwu.magisk.services.OnBootService;
import com.topjohnwu.magisk.services.UpdateCheckService;
import com.topjohnwu.magisk.utils.FingerprintHelper;
import com.topjohnwu.magisk.utils.Utils;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class Data {
    // Global app instance
    public static WeakReference<MagiskManager> weakApp;
    public static Handler mainHandler = new Handler(Looper.getMainLooper());
    public static Map<Class, Class> classMap = new HashMap<>();

    // Current status
    public static String magiskVersionString;
    public static int magiskVersionCode = -1;
    public static boolean magiskHide;

    // Update Info
    public static String remoteMagiskVersionString;
    public static int remoteMagiskVersionCode = -1;
    public static String magiskLink;
    public static String magiskNoteLink;
    public static String magiskMD5;
    public static String remoteManagerVersionString;
    public static int remoteManagerVersionCode = -1;
    public static String managerLink;
    public static String managerNoteLink;
    public static String uninstallerLink;
    public static int snetVersionCode;
    public static String snetLink;

    // Install flags
    public static boolean keepVerity = false;
    public static boolean keepEnc = false;

    // Configs
    public static boolean isDarkTheme;
	public static boolean cnRepo;
    public static int suRequestTimeout;
    public static int suLogTimeout = 14;
    public static int suAccessState;
    public static boolean suFingerprint;
    public static int multiuserMode;
    public static int suResponseType;
    public static int suNotificationType;
    public static int suNamespaceMode;
    public static int updateChannel;
    public static int repoOrder;

    static {
        classMap.put(MagiskManager.class, a.q.class);
        classMap.put(MainActivity.class, a.b.class);
        classMap.put(SplashActivity.class, a.c.class);
        classMap.put(AboutActivity.class, a.d.class);
        classMap.put(DonationActivity.class, a.e.class);
        classMap.put(FlashActivity.class, a.f.class);
        classMap.put(NoUIActivity.class, a.g.class);
        classMap.put(BootReceiver.class, a.h.class);
        classMap.put(PackageReceiver.class, a.i.class);
        classMap.put(ManagerUpdate.class, a.j.class);
        classMap.put(RebootReceiver.class, a.k.class);
        classMap.put(ShortcutReceiver.class, a.l.class);
        classMap.put(OnBootService.class, a.m.class);
        classMap.put(UpdateCheckService.class, a.n.class);
        classMap.put(AboutCardRow.class, a.o.class);
        classMap.put(SuRequestActivity.class, a.p.class);

    }

    public static void loadMagiskInfo() {
        try {
            magiskVersionString = ShellUtils.fastCmd("magisk -v").split(":")[0];
            magiskVersionCode = Integer.parseInt(ShellUtils.fastCmd("magisk -V"));
            String s = ShellUtils.fastCmd((magiskVersionCode >= Const.MAGISK_VER.RESETPROP_PERSIST ?
                    "resetprop -p " : "getprop ") + Const.MAGISKHIDE_PROP);
            magiskHide = s.isEmpty() || Integer.parseInt(s) != 0;
        } catch (NumberFormatException ignored) {}
    }

    public static MagiskManager MM() {
        return weakApp.get();
    }

    public static void exportPrefs() {
        // Flush prefs to disk
        MagiskManager mm = MM();
        mm.prefs.edit().commit();
        File xml = new File(mm.getFilesDir().getParent() + "/shared_prefs",
                mm.getPackageName() + "_preferences.xml");
        Shell.su(Utils.fmt("for usr in /data/user/*; do cat %s > ${usr}/%s; done", xml, Const.MANAGER_CONFIGS)).exec();
    }

    public static void importPrefs() {
        MagiskManager mm = MM();
        SuFile config = new SuFile(Utils.fmt("/data/user/%d/%s", Const.USER_ID, Const.MANAGER_CONFIGS));
        if (config.exists()) {
            SharedPreferences.Editor editor = mm.prefs.edit();
            try {
                SuFileInputStream is = new SuFileInputStream(config);
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(is, "UTF-8");
                parser.nextTag();
                parser.require(XmlPullParser.START_TAG, null, "map");
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG)
                        continue;
                    String key = parser.getAttributeValue(null, "name");
                    String value = parser.getAttributeValue(null, "value");
                    switch (parser.getName()) {
                        case "string":
                            parser.require(XmlPullParser.START_TAG, null, "string");
                            editor.putString(key, parser.nextText());
                            parser.require(XmlPullParser.END_TAG, null, "string");
                            break;
                        case "boolean":
                            parser.require(XmlPullParser.START_TAG, null, "boolean");
                            editor.putBoolean(key, Boolean.parseBoolean(value));
                            parser.nextTag();
                            parser.require(XmlPullParser.END_TAG, null, "boolean");
                            break;
                        case "int":
                            parser.require(XmlPullParser.START_TAG, null, "int");
                            editor.putInt(key, Integer.parseInt(value));
                            parser.nextTag();
                            parser.require(XmlPullParser.END_TAG, null, "int");
                            break;
                        case "long":
                            parser.require(XmlPullParser.START_TAG, null, "long");
                            editor.putLong(key, Long.parseLong(value));
                            parser.nextTag();
                            parser.require(XmlPullParser.END_TAG, null, "long");
                            break;
                        case "float":
                            parser.require(XmlPullParser.START_TAG, null, "int");
                            editor.putFloat(key, Float.parseFloat(value));
                            parser.nextTag();
                            parser.require(XmlPullParser.END_TAG, null, "int");
                            break;
                        default:
                            parser.next();
                    }
                }
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
            editor.remove(Const.Key.ETAG_KEY);
            editor.apply();
            loadConfig();
            config.delete();
        }
    }

    public static void loadConfig() {
        MagiskManager mm = MM();
        // su
        suRequestTimeout = Utils.getPrefsInt(mm.prefs, Const.Key.SU_REQUEST_TIMEOUT, Const.Value.timeoutList[2]);
        suResponseType = Utils.getPrefsInt(mm.prefs, Const.Key.SU_AUTO_RESPONSE, Const.Value.SU_PROMPT);
        suNotificationType = Utils.getPrefsInt(mm.prefs, Const.Key.SU_NOTIFICATION, Const.Value.NOTIFICATION_TOAST);
        suAccessState = mm.mDB.getSettings(Const.Key.ROOT_ACCESS, Const.Value.ROOT_ACCESS_APPS_AND_ADB);
        multiuserMode = mm.mDB.getSettings(Const.Key.SU_MULTIUSER_MODE, Const.Value.MULTIUSER_MODE_OWNER_ONLY);
        suNamespaceMode = mm.mDB.getSettings(Const.Key.SU_MNT_NS, Const.Value.NAMESPACE_MODE_REQUESTER);
        suFingerprint = mm.mDB.getSettings(Const.Key.SU_FINGERPRINT, 0) != 0;
        if (suFingerprint && !FingerprintHelper.canUseFingerprint()) {
            // User revoked the fingerprint
            mm.mDB.setSettings(Const.Key.SU_FINGERPRINT, 0);
            suFingerprint = false;
        }

        // config
        isDarkTheme = mm.prefs.getBoolean(Const.Key.DARK_THEME, false);
		cnRepo = mm.prefs.getBoolean(Const.Key.CN_REPO, false);
        updateChannel = Utils.getPrefsInt(mm.prefs, Const.Key.UPDATE_CHANNEL, Const.Value.STABLE_CHANNEL);
        repoOrder = mm.prefs.getInt(Const.Key.REPO_ORDER, Const.Value.ORDER_DATE);
    }

    public static void writeConfig() {
        MM().prefs.edit()
                .putBoolean(Const.Key.DARK_THEME, isDarkTheme)
				.putBoolean(Const.Key.CN_REPO, cnRepo)
                .putBoolean(Const.Key.MAGISKHIDE, magiskHide)
                .putBoolean(Const.Key.HOSTS, Const.MAGISK_HOST_FILE.exists())
                .putBoolean(Const.Key.COREONLY, Const.MAGISK_DISABLE_FILE.exists())
                .putBoolean(Const.Key.SU_FINGERPRINT, suFingerprint)
                .putString(Const.Key.SU_REQUEST_TIMEOUT, String.valueOf(suRequestTimeout))
                .putString(Const.Key.SU_AUTO_RESPONSE, String.valueOf(suResponseType))
                .putString(Const.Key.SU_NOTIFICATION, String.valueOf(suNotificationType))
                .putString(Const.Key.ROOT_ACCESS, String.valueOf(suAccessState))
                .putString(Const.Key.SU_MULTIUSER_MODE, String.valueOf(multiuserMode))
                .putString(Const.Key.SU_MNT_NS, String.valueOf(suNamespaceMode))
                .putString(Const.Key.UPDATE_CHANNEL, String.valueOf(updateChannel))
                .putInt(Const.Key.UPDATE_SERVICE_VER, Const.UPDATE_SERVICE_VER)
                .putInt(Const.Key.REPO_ORDER, repoOrder)
                .apply();
    }
}
