/*
 * Copyright 2014-2016 Hans-Christoph Steiner
 * Copyright 2012-2016 Nathan Freitas
 * Portions Copyright (c) 2016 CommonsWare, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.guardianproject.netcipher.proxy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.signal.core.util.logging.Log;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Utility class to simplify setting up a proxy connection
 * to Orbot.
 * <p>
 * If you are using classes in the info.guardianproject.netcipher.client
 * package, call OrbotHelper.get(this).init(); from onCreate()
 * of a custom Application subclass, or from some other guaranteed
 * entry point to your app. At that point, the
 * info.guardianproject.netcipher.client classes will be ready
 * for use.
 */
public class OrbotHelper implements ProxyHelper {

    private static final String TAG = Log.tag(OrbotHelper.class);

    private final static int REQUEST_CODE_STATUS = 100;

    public final static String ORBOT_PACKAGE_NAME = "org.torproject.android";
    public final static String ORBOT_MARKET_URI = "market://details?id=" + ORBOT_PACKAGE_NAME;

    public final static String ORBOT_INSTALL_URL = "https://www.torproject.org/docs/android.html";

    public final static String DEFAULT_PROXY_HOST = "localhost";//"127.0.0.1";
    public final static int DEFAULT_PROXY_HTTP_PORT = 8118;
    public final static int DEFAULT_PROXY_SOCKS_PORT = 9050;

    /**
     * A request to Orbot to transparently start Tor services
     */
    public final static String ACTION_START = "org.torproject.android.intent.action.START";

    /**
     * {@link Intent} send by Orbot with {@code ON/OFF/STARTING/STOPPING} status
     * included as an {@link #EXTRA_STATUS} {@code String}.  Your app should
     * always receive {@code ACTION_STATUS Intent}s since any other app could
     * start Orbot.  Also, user-triggered starts and stops will also cause
     * {@code ACTION_STATUS Intent}s to be broadcast.
     */
    public final static String ACTION_STATUS = "org.torproject.android.intent.action.STATUS";

    /**
     * {@code String} that contains a status constant: {@link #STATUS_ON},
     * {@link #STATUS_OFF}, {@link #STATUS_STARTING}, or
     * {@link #STATUS_STOPPING}
     */
    public final static String EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS";
    /**
     * A {@link String} {@code packageName} for Orbot to direct its status reply
     * to, used in {@link #ACTION_START} {@link Intent}s sent to Orbot
     */
    public final static String EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME";

    public final static String EXTRA_PROXY_PORT_HTTP = "org.torproject.android.intent.extra.HTTP_PROXY_PORT";
    public final static String EXTRA_PROXY_PORT_SOCKS = "org.torproject.android.intent.extra.SOCKS_PROXY_PORT";


    /**
     * All tor-related services and daemons are stopped
     */
    public final static String STATUS_OFF = "OFF";
    /**
     * All tor-related services and daemons have completed starting
     */
    public final static String STATUS_ON = "ON";
    public final static String STATUS_STARTING = "STARTING";
    public final static String STATUS_STOPPING = "STOPPING";
    /**
     * The user has disabled the ability for background starts triggered by
     * apps. Fallback to the old Intent that brings up Orbot.
     */
    public final static String STATUS_STARTS_DISABLED = "STARTS_DISABLED";

    public final static String ACTION_START_TOR = "org.torproject.android.START_TOR";
    public final static String ACTION_REQUEST_HS = "org.torproject.android.REQUEST_HS_PORT";
    public final static int START_TOR_RESULT = 0x9234;
    public final static int HS_REQUEST_CODE = 9999;


/*
    private OrbotHelper() {
        // only static utility methods, do not instantiate
    }
*/

    /**
     * Test whether a {@link URL} is a Tor Hidden Service host name, also known
     * as an ".onion address".
     *
     * @return whether the host name is a Tor .onion address
     */
    public static boolean isOnionAddress(URL url) {
        return url.getHost().endsWith(".onion");
    }

    /**
     * Test whether a URL {@link String} is a Tor Hidden Service host name, also known
     * as an ".onion address".
     *
     * @return whether the host name is a Tor .onion address
     */
    public static boolean isOnionAddress(String urlString) {
        try {
            return isOnionAddress(new URL(urlString));
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Test whether a {@link Uri} is a Tor Hidden Service host name, also known
     * as an ".onion address".
     *
     * @return whether the host name is a Tor .onion address
     */
    public static boolean isOnionAddress(Uri uri) {
        return uri.getHost().endsWith(".onion");
    }

    public static boolean isOrbotInstalled(Context context) {
        return isAppInstalled(context, ORBOT_PACKAGE_NAME);
    }

    private static boolean isAppInstalled(Context context, String uri) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void requestHiddenServiceOnPort(Activity activity, int port) {
        Intent intent = new Intent(ACTION_REQUEST_HS);
        intent.setPackage(ORBOT_PACKAGE_NAME);
        intent.putExtra("hs_port", port);

        activity.startActivityForResult(intent, HS_REQUEST_CODE);
    }

    /**
     * First, checks whether Orbot is installed. If Orbot is installed, then a
     * broadcast {@link Intent} is sent to request Orbot to start
     * transparently in the background. When Orbot receives this {@code
     * Intent}, it will immediately reply to the app that called this method
     * with an {@link #ACTION_STATUS} {@code Intent} that is broadcast to the
     * {@code packageName} of the provided {@link Context} (i.e.  {@link
     * Context#getPackageName()}.
     * <p>
     * That reply {@link #ACTION_STATUS} {@code Intent} could say that the user
     * has disabled background starts with the status
     * {@link #STATUS_STARTS_DISABLED}. That means that Orbot ignored this
     * request.
     *
     * @param context the app {@link Context} will receive the reply
     * @return whether the start request was sent to Orbot
     */
    public static boolean requestStartTor(Context context) {
        if (OrbotHelper.isOrbotInstalled(context)) {
            Log.i(TAG, "requestStartTor " + context.getPackageName());
            Intent intent = getOrbotStartIntent(context);
            context.sendBroadcast(intent);
            return true;
        }
        return false;
    }

    /**
     * Gets an {@link Intent} for starting Orbot.  Orbot will reply with the
     * current status to the {@code packageName} of the app in the provided
     * {@link Context} (i.e.  {@link Context#getPackageName()}.
     */
    public static Intent getOrbotStartIntent(Context context) {
        Intent intent = new Intent(ACTION_START);
        intent.setPackage(ORBOT_PACKAGE_NAME);
        intent.putExtra(EXTRA_PACKAGE_NAME, context.getPackageName());
        return intent;
    }

    /**
     * Gets a barebones {@link Intent} for starting Orbot.  This is deprecated
     * in favor of {@link #getOrbotStartIntent(Context)}.
     */
    @Deprecated
    public static Intent getOrbotStartIntent() {
        Intent intent = new Intent(ACTION_START);
        intent.setPackage(ORBOT_PACKAGE_NAME);
        return intent;
    }

    public static Intent getShowOrbotStartIntent() {
        Intent intent = new Intent(ACTION_START_TOR);
        intent.setPackage(ORBOT_PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static Intent getOrbotInstallIntent(Context context) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(ORBOT_MARKET_URI));

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resInfos = pm.queryIntentActivities(intent, 0);

        String foundPackageName = null;
        for (ResolveInfo r : resInfos) {
            Log.i(TAG, "market: " + r.activityInfo.packageName);
            if (TextUtils.equals(r.activityInfo.packageName, FDROID_PACKAGE_NAME)
                    || TextUtils.equals(r.activityInfo.packageName, PLAY_PACKAGE_NAME)) {
                foundPackageName = r.activityInfo.packageName;
                break;
            }
        }

        if (foundPackageName == null) {
            intent.setData(Uri.parse(ORBOT_INSTALL_URL));
        } else {
            intent.setPackage(foundPackageName);
        }
        return intent;
    }

    @Override
    public boolean isInstalled(Context context) {
        return isOrbotInstalled(context);
    }

    @Override
    public boolean requestStart(Context context) {
        return requestStartTor(context);
    }

    @Override
    public Intent getInstallIntent(Context context) {
        return getOrbotInstallIntent(context);
    }

    @Override
    public Intent getStartIntent(Context context) {
        return getOrbotStartIntent();
    }

    @Override
    public String getName() {
        return "Orbot";
    }

    /* MLM additions */

    private final Context context;
    private final Handler handler;
    private boolean isInstalled = false;
    @Nullable
    private Intent lastStatusIntent = null;
    private final Set<StatusCallback> statusCallbacks =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<InstallCallback> installCallbacks =
            Collections.newSetFromMap(new WeakHashMap<>());
    private long statusTimeoutMs = 30000L;
    private long installTimeoutMs = 60000L;
    private boolean validateOrbot = true;

    abstract public static class SimpleStatusCallback
            implements StatusCallback {
        @Override
        public void onEnabled(Intent statusIntent) {
            // no-op; extend and override if needed
        }

        @Override
        public void onStarting() {
            // no-op; extend and override if needed
        }

        @Override
        public void onStopping() {
            // no-op; extend and override if needed
        }

        @Override
        public void onDisabled() {
            // no-op; extend and override if needed
        }

        @Override
        public void onNotYetInstalled() {
            // no-op; extend and override if needed
        }
    }

    /**
     * Callback interface used for reporting the results of an
     * attempt to install Orbot
     */
    public interface InstallCallback {
        void onInstalled();

        void onInstallTimeout();
    }

    private static volatile OrbotHelper instance;

    /**
     * Retrieves the singleton, initializing if if needed
     *
     * @param context any Context will do, as we will hold onto
     *                the Application
     * @return the singleton
     */
    synchronized public static OrbotHelper get(Context context) {
        if (instance == null) {
            instance = new OrbotHelper(context);
        }

        return (instance);
    }

    /**
     * Standard constructor
     *
     * @param context any Context will do; OrbotInitializer will hold
     *                onto the Application context
     */
    private OrbotHelper(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Adds a StatusCallback to be called when we find out that
     * Orbot is ready. If Orbot is ready for use, your callback
     * will be called with onEnabled() immediately, before this
     * method returns.
     *
     * @param cb a callback
     * @return the singleton, for chaining
     */
    public OrbotHelper addStatusCallback(StatusCallback cb) {
        statusCallbacks.add(cb);

        if (lastStatusIntent != null) {
            String status =
                    lastStatusIntent.getStringExtra(OrbotHelper.EXTRA_STATUS);

            if (status.equals(OrbotHelper.STATUS_ON)) {
                cb.onEnabled(lastStatusIntent);
            }
        }

        return (this);
    }

    /**
     * Removes an existing registered StatusCallback.
     *
     * @param cb the callback to remove
     * @return the singleton, for chaining
     */
    public OrbotHelper removeStatusCallback(StatusCallback cb) {
        statusCallbacks.remove(cb);

        return (this);
    }


    /**
     * Adds an InstallCallback to be called when we find out that
     * Orbot is installed
     *
     * @param cb a callback
     * @return the singleton, for chaining
     */
    public OrbotHelper addInstallCallback(InstallCallback cb) {
        installCallbacks.add(cb);

        return (this);
    }

    /**
     * Removes an existing registered InstallCallback.
     *
     * @param cb the callback to remove
     * @return the singleton, for chaining
     */
    public OrbotHelper removeInstallCallback(InstallCallback cb) {
        installCallbacks.remove(cb);

        return (this);
    }

    /**
     * Sets how long of a delay, in milliseconds, after trying
     * to get a status from Orbot before we give up.
     * Defaults to 30000ms = 30 seconds = 0.000347222 days
     *
     * @param timeoutMs delay period in milliseconds
     * @return the singleton, for chaining
     */
    public OrbotHelper statusTimeout(long timeoutMs) {
        statusTimeoutMs = timeoutMs;

        return (this);
    }

    /**
     * Sets how long of a delay, in milliseconds, after trying
     * to install Orbot do we assume that it's not happening.
     * Defaults to 60000ms = 60 seconds = 1 minute = 1.90259e-6 years
     *
     * @param timeoutMs delay period in milliseconds
     * @return the singleton, for chaining
     */
    public OrbotHelper installTimeout(long timeoutMs) {
        installTimeoutMs = timeoutMs;

        return (this);
    }

    /**
     * By default, NetCipher ensures that the Orbot on the
     * device is one of the official builds. Call this method
     * to skip that validation. Mostly, this is for developers
     * who have their own custom Orbot builds (e.g., for
     * dedicated hardware).
     *
     * @return the singleton, for chaining
     */
    public OrbotHelper skipOrbotValidation() {
        validateOrbot = false;

        return (this);
    }

    /**
     * @return true if Orbot is installed (the last time we checked),
     * false otherwise
     */
    public boolean isInstalled() {
        return (isInstalled);
    }

    /**
     * Initializes the connection to Orbot, revalidating that it is installed
     * and requesting fresh status broadcasts.  This is best run in your app's
     * {@link android.app.Application} subclass, in its
     * {@link android.app.Application#onCreate()} method.
     *
     * @return true if initialization is proceeding, false if Orbot is not installed,
     * or version of Orbot with a unofficial signing key is present.
     */
    public boolean init() {
        Intent orbot = OrbotHelper.getOrbotStartIntent(context);

        if (validateOrbot) {
            ArrayList<String> hashes = new ArrayList<String>();

            // Tor Project signing key
            hashes.add("A4:54:B8:7A:18:47:A8:9E:D7:F5:E7:0F:BA:6B:BA:96:F3:EF:29:C2:6E:09:81:20:4F:E3:47:BF:23:1D:FD:5B");
            // f-droid.org signing key
            hashes.add("A7:02:07:92:4F:61:FF:09:37:1D:54:84:14:5C:4B:EE:77:2C:55:C1:9E:EE:23:2F:57:70:E1:82:71:F7:CB:AE");

            orbot =
                    SignatureUtils.validateBroadcastIntent(context, orbot,
                            hashes, false);
        }

        if (orbot != null) {
            isInstalled = true;
            handler.postDelayed(onStatusTimeout, statusTimeoutMs);
            ContextCompat.registerReceiver(context, orbotStatusReceiver,
                                           new IntentFilter(OrbotHelper.ACTION_STATUS),
                                           ContextCompat.RECEIVER_EXPORTED);
            context.sendBroadcast(orbot);
        } else {
            isInstalled = false;

            for (StatusCallback cb : statusCallbacks) {
                cb.onNotYetInstalled();
            }
        }

        return (isInstalled);
    }

    private BroadcastReceiver orbotStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(),
                    OrbotHelper.ACTION_STATUS)) {
                String status = intent.getStringExtra(OrbotHelper.EXTRA_STATUS);

                if (status.equals(OrbotHelper.STATUS_ON)) {
                    lastStatusIntent = intent;
                    handler.removeCallbacks(onStatusTimeout);

                    for (StatusCallback cb : statusCallbacks) {
                        cb.onEnabled(intent);
                    }
                } else if (status.equals(OrbotHelper.STATUS_OFF)) {
                    for (StatusCallback cb : statusCallbacks) {
                        cb.onDisabled();
                    }
                } else if (status.equals(OrbotHelper.STATUS_STARTING)) {
                    for (StatusCallback cb : statusCallbacks) {
                        cb.onStarting();
                    }
                } else if (status.equals(OrbotHelper.STATUS_STOPPING)) {
                    for (StatusCallback cb : statusCallbacks) {
                        cb.onStopping();
                    }
                }
            }
        }
    };

    private final Runnable onStatusTimeout = new Runnable() {
        @Override
        public void run() {
            try {
                context.unregisterReceiver(orbotStatusReceiver);
            } catch (IllegalArgumentException ignored) {}

            for (StatusCallback cb : statusCallbacks) {
                cb.onStatusTimeout();
            }
        }
    };

    private final BroadcastReceiver orbotInstallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(),
                    Intent.ACTION_PACKAGE_ADDED)) {
                String pkgName = intent.getData().getEncodedSchemeSpecificPart();

                if (OrbotHelper.ORBOT_PACKAGE_NAME.equals(pkgName)) {
                    isInstalled = true;
                    handler.removeCallbacks(onInstallTimeout);
                    context.unregisterReceiver(orbotInstallReceiver);

                    for (InstallCallback cb : installCallbacks) {
                        cb.onInstalled();
                    }

                    init();
                }
            }
        }
    };

    private final Runnable onInstallTimeout = new Runnable() {
        @Override
        public void run() {
            context.unregisterReceiver(orbotInstallReceiver);

            for (InstallCallback cb : installCallbacks) {
                cb.onInstallTimeout();
            }
        }
    };
}
