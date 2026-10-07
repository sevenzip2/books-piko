package app.morphe.extension.playbooks.gms;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.util.Log;

@SuppressWarnings("unused")
public final class GmsCoreSupport {
    private static final String TAG = "BooksPiko";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
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
     * GmsCore only lists accounts to apps holding GET_ACCOUNTS, a runtime permission.
     */
    public static void onActivityResumed(Activity activity) {
        if (checkedThisProcess) return;
        checkedThisProcess = true;

        try {
            if (activity.checkSelfPermission(Manifest.permission.GET_ACCOUNTS)
                    != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.GET_ACCOUNTS}, REQUEST_GET_ACCOUNTS);
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "GET_ACCOUNTS request failed", throwable);
        }
    }
}
