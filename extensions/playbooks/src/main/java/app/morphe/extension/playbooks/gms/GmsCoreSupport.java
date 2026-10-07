package app.morphe.extension.playbooks.gms;

import static app.morphe.extension.playbooks.shared.Text.t;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

@SuppressWarnings("unused")
public final class GmsCoreSupport {
    private static final String TAG = "BooksPiko";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private static final String META_GMSCORE_PACKAGE = "app.bookspiko.GMSCORE_PACKAGE";
    private static final String DEFAULT_GMSCORE_PACKAGE = "app.revanced.android.gms";
    private static final int REQUEST_GET_ACCOUNTS = 0x5b0c;

    private static volatile boolean checkedThisProcess;

    private GmsCoreSupport() {
    }

    /**
     * Injected at the start of AppSingleton.getAccountComponent(Account).
     * Google and GmsCore accounts with the same e-mail must share one account component,
     * because the component's DataStore files are keyed by e-mail only.
     */
    public static Account normalizeAccount(Account account, String gmsCoreAccountType) {
        if (account != null && GOOGLE_ACCOUNT_TYPE.equals(account.type)) {
            return new Account(account.name, gmsCoreAccountType);
        }
        return account;
    }

    /**
     * Injected after super.onResume() of the base activity. Runs once per process.
     */
    public static void onActivityResumed(Activity activity) {
        if (checkedThisProcess) return;
        checkedThisProcess = true;

        try {
            String gmsCorePackage = gmsCorePackage(activity);
            ApplicationInfo info;
            try {
                info = activity.getPackageManager().getApplicationInfo(gmsCorePackage, 0);
            } catch (PackageManager.NameNotFoundException e) {
                showDialog(activity, null, t(
                        "GmsCore(" + gmsCorePackage + ")가 설치되어 있지 않습니다.\n"
                                + "로그인과 라이브러리 동기화를 하려면 ReVanced GmsCore를 설치하세요.",
                        "GmsCore (" + gmsCorePackage + ") is not installed.\n"
                                + "Install ReVanced GmsCore to sign in and sync your library."));
                return;
            }

            if (!info.enabled) {
                // Seen on Onyx BOOX: "Auto Freeze" disables GmsCore in the background, after which
                // its account provider can no longer be found.
                showDialog(activity, gmsCorePackage, t(
                        "GmsCore(" + gmsCorePackage + ")가 비활성화(동결)되어 있습니다.\n\n"
                                + "BOOX 사용 시: 앱 관리에서 GmsCore를 자동 동결(Auto Freeze) 대상에서 제외하고 "
                                + "백그라운드 실행을 허용하세요.\n\n"
                                + "ADB로 복구: adb shell pm enable --user 0 " + gmsCorePackage,
                        "GmsCore (" + gmsCorePackage + ") is disabled (frozen).\n\n"
                                + "On Onyx BOOX, exclude GmsCore from \"Auto Freeze\" and allow it to run "
                                + "in the background.\n\n"
                                + "To restore it with ADB: adb shell pm enable --user 0 " + gmsCorePackage));
                return;
            }

            if (activity.checkSelfPermission(Manifest.permission.GET_ACCOUNTS)
                    != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.GET_ACCOUNTS}, REQUEST_GET_ACCOUNTS);
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "GmsCore check failed", throwable);
        }
    }

    static String gmsCorePackage(Context context) {
        try {
            ApplicationInfo self = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = self.metaData;
            if (metaData != null) {
                String value = metaData.getString(META_GMSCORE_PACKAGE);
                if (value != null && !value.isEmpty()) return value;
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not read GmsCore package name", throwable);
        }
        return DEFAULT_GMSCORE_PACKAGE;
    }

    private static void showDialog(final Activity activity, final String packageToOpen, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("GmsCore")
                .setMessage(message)
                .setNegativeButton(android.R.string.ok, null);

        if (packageToOpen != null) {
            builder.setPositiveButton(t("앱 정보 열기", "Open app info"), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageToOpen, null));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);
                    } catch (Throwable throwable) {
                        Log.e(TAG, "Could not open app info", throwable);
                    }
                }
            });
        }

        builder.show();
    }
}
