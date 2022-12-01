/* proxydroid - Global / Individual Proxy App for Android
 * Copyright (C) 2011 Max Lv <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package org.proxydroid;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.preference.MultiSelectListPreference;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.proxydroid.utils.Constraints;
import org.proxydroid.utils.Utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ProxyDroid extends AppCompatActivity {

    private static final String TAG = "ProxyDroid";
    private static final int MSG_UPDATE_FINISHED = 0;
    private static final int MSG_NO_ROOT = 1;

    String profile;

    private void showAbout() {

        WebView web = new WebView(this);
        web.loadUrl("file:///android_asset/pages/about.html");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        });

        String versionName = "";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException ignored) {
        }

        new AlertDialog.Builder(this).setTitle(
                String.format(getString(R.string.about_title), versionName))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.ok_iknow), (dialog, id) -> dialog.cancel())
                .setView(web)
                .create()
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        final Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "home_screen");
        ((ProxyDroidApplication)getApplication())
                .firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener {
        private ProgressDialog pd = null;
        private final Profile mProfile = new Profile();
        private CheckBoxPreference isAutoConnectCheck;
        private CheckBoxPreference isAutoSetProxyCheck;
        private CheckBoxPreference isAuthCheck;
        private CheckBoxPreference isNTLMCheck;
        private CheckBoxPreference isPACCheck;
        private ListPreference profileList;
        private EditTextPreference hostText;
        private EditTextPreference portText;
        private EditTextPreference userText;
        private EditTextPreference passwordText;
        private EditTextPreference domainText;
        private EditTextPreference certificateText;
        private MultiSelectListPreference ssidList;
        private MultiSelectListPreference excludedSsidList;
        private ListPreference proxyTypeList;
        private Preference isRunningCheck;
        private CheckBoxPreference isBypassAppsCheck;
        private Preference proxyedApps;
        private Preference bypassAddrs;
        private Preference ringtonePref;
        private ActivityResultLauncher<Intent> mStartForResult;

        private final BroadcastReceiver ssidReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    Log.w(TAG, "onReceived() called uncorrectly");
                    return;
                }

                loadNetworkList();
            }
        };

        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE_FINISHED:
                        Toast.makeText(getActivity(), getString(R.string.update_finished), Toast.LENGTH_LONG)
                                .show();
                        break;
                    case MSG_NO_ROOT:
                        showAToast(getString(R.string.require_root_alert));
                        break;
                }
                super.handleMessage(msg);
            }
        };

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.proxydroid_preference, rootKey);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (!isAdded()) return;

            mStartForResult = registerForActivityResult(new StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            final Context context = getActivity();
                            // Handle the Intent
                            if (data != null && context != null) {
                                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                                final Editor edit = settings.edit();
                                final Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                                if (ringtone != null) {
                                    final Ringtone rt = RingtoneManager.getRingtone(getActivity(), ringtone);
                                    final String title = rt.getTitle(context);
                                    edit.putString("settings_key_notif_ringtone", ringtone.toString());
                                    ringtonePref.setSummary(title);
                                } else {
                                    // "Silent" was selected
                                    edit.remove("settings_key_notif_ringtone");
                                    ringtonePref.setSummary(R.string.notif_ringtone_summary);
                                }
                                edit.apply();
                            }
                        }
                    });

            ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(new RequestMultiplePermissions(),
                    isGranted -> continueLoad()
            );

            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

            try {
                settings.getStringSet("ssid", null);
            } catch (Exception E) {
                final Editor editor = settings.edit();
                editor.remove("ssid");
                editor.apply();
            }
            try {
                settings.getStringSet("excludedSsid", null);
            } catch (Exception E) {
                final Editor editor = settings.edit();
                editor.remove("excludedSsid");
                editor.apply();
            }

            hostText = (EditTextPreference) findPreference("host");
            portText = (EditTextPreference) findPreference("port");
            userText = (EditTextPreference) findPreference("user");
            passwordText = (EditTextPreference) findPreference("password");
            domainText = (EditTextPreference) findPreference("domain");
            certificateText = (EditTextPreference) findPreference("certificate");
            bypassAddrs = findPreference("bypassAddrs");
            ssidList = (MultiSelectListPreference) findPreference("ssid");
            excludedSsidList = (MultiSelectListPreference) findPreference("excludedSsid");
            proxyTypeList = (ListPreference) findPreference("proxyType");
            proxyedApps = findPreference("proxyedApps");
            profileList = (ListPreference) findPreference("profile");

            isRunningCheck = (Preference) findPreference("isRunning");
            isAutoSetProxyCheck = (CheckBoxPreference) findPreference("isAutoSetProxy");
            isAuthCheck = (CheckBoxPreference) findPreference("isAuth");
            isNTLMCheck = (CheckBoxPreference) findPreference("isNTLM");
            isPACCheck = (CheckBoxPreference) findPreference("isPAC");
            isAutoConnectCheck = (CheckBoxPreference) findPreference("isAutoConnect");
            isBypassAppsCheck = (CheckBoxPreference) findPreference("isBypassApps");

            ringtonePref = findPreference("settings_key_notif_ringtone");

            final Uri ringtone = Uri.parse(settings.getString("settings_key_notif_ringtone", ""));
            if (ringtone != null) {
                final Ringtone rt = RingtoneManager.getRingtone(getActivity(), ringtone);
                final String title = rt.getTitle(getActivity());
                ringtonePref.setSummary(title);
            } else {
                ringtonePref.setSummary(R.string.notif_ringtone_summary);
            }

            String profileValuesString = settings.getString("profileValues", "");

            if (profileValuesString.equals("")) {
                Editor ed = settings.edit();
                ((ProxyDroid)getActivity()).profile = "1";
                ed.putString("profileValues", "1|0");
                ed.putString("profileEntries",
                        getString(R.string.profile_default) + "|" + getString(R.string.profile_new));
                ed.putString("profile", "1");
                ed.apply();

                profileList.setDefaultValue("1");
            }


            List<String> pl = Arrays.asList(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            Iterator<String> i = pl.iterator();
            while (i.hasNext()) {
                String s = i.next(); // must be called before you can call i.remove()
                if (ActivityCompat.checkSelfPermission(getActivity(), s) != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), s)) {
                        i.remove();
                    }
                }
            }
            if (!pl.isEmpty()) {
                requestPermissionLauncher.launch(pl.toArray(new String[0]));
                return;
            }
            continueLoad();
        }

        private void continueLoad()
        {
            final Context context = getActivity();
            if (context != null)
                context.registerReceiver(ssidReceiver,
                        new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));

            loadProfileList();

            loadNetworkList();

            new Thread() {
                @Override
                public void run() {

                    try {
                        // Try not to block activity
                        Thread.sleep(2000);
                    } catch (InterruptedException ignore) {
                        // Nothing
                    }

                    if (!Utils.isRoot()) {
                        handler.sendEmptyMessage(MSG_NO_ROOT);
                    }

                    String versionName;
                    try {
                        versionName = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
                    } catch (NameNotFoundException e) {
                        versionName = "NONE";
                    }

                    final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

                    if (!settings.getBoolean(versionName, false)) {

                        String version;
                        try {
                            version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
                        } catch (NameNotFoundException e) {
                            version = "NONE";
                        }

                        reset();

                        SharedPreferences.Editor edit = settings.edit();
                        edit.putBoolean(version, true);
                        edit.apply();

                        handler.sendEmptyMessage(MSG_UPDATE_FINISHED);
                    }
                }
            }.start();
        }

        private void loadProfileList() {
            try {
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String[] profileEntries = settings.getString("profileEntries", "").split("\\|");
                String[] profileValues = settings.getString("profileValues", "").split("\\|");

                profileList.setEntries(profileEntries);
                profileList.setEntryValues(profileValues);
            } catch (Exception ignored) {

            }
        }

        private void loadNetworkList() {
            WifiManager wm = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            List<WifiConfiguration> wcs;
            String[] ssidEntries;
            String[] pureSsid;
            int n = 3;
            int wifiIndex = n;

            try {
                wcs = wm.getConfiguredNetworks();
            } catch (SecurityException E) {
                wcs = null;
            }

            if (wcs == null) {
                ssidEntries = new String[n];

                ssidEntries[0] = Constraints.WIFI_AND_3G;
                ssidEntries[1] = Constraints.ONLY_WIFI;
                ssidEntries[2] = Constraints.ONLY_3G;
            } else {
                ssidEntries = new String[wcs.size() + n];

                ssidEntries[0] = Constraints.WIFI_AND_3G;
                ssidEntries[1] = Constraints.ONLY_WIFI;
                ssidEntries[2] = Constraints.ONLY_3G;

                for (WifiConfiguration wc : wcs) {
                    if (wc != null && wc.SSID != null) {
                        ssidEntries[n++] = wc.SSID.replace("\"", "");
                    } else {
                        ssidEntries[n++] = "unknown";
                    }
                }
            }
            ssidList.setEntries(ssidEntries);
            ssidList.setEntryValues(ssidEntries);

            pureSsid = Arrays.copyOfRange(ssidEntries, wifiIndex, ssidEntries.length);
            excludedSsidList.setEntries(pureSsid);
            excludedSsidList.setEntryValues(pureSsid);
        }

        @Override
        public void onDestroy() {
            getActivity().unregisterReceiver(ssidReceiver);

            super.onDestroy();
        }

        @Override
        public void onResume() {
            super.onResume();
            final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

            if (settings.getBoolean("isAutoSetProxy", false)) {
                proxyedApps.setEnabled(false);
                isBypassAppsCheck.setEnabled(false);
            } else {
                proxyedApps.setEnabled(true);
                isBypassAppsCheck.setEnabled(true);
            }

            if (settings.getBoolean("isAutoConnect", false)) {
                ssidList.setEnabled(true);
                excludedSsidList.setEnabled(true);
            } else {
                ssidList.setEnabled(false);
                excludedSsidList.setEnabled(false);
            }

            if (settings.getBoolean("isPAC", false)) {
                portText.setEnabled(false);
                proxyTypeList.setEnabled(false);
                hostText.setTitle(R.string.host_pac);
                hostText.setSummary(R.string.host_pac_summary);
            }

            if (!settings.getBoolean("isAuth", false)) {
                userText.setEnabled(false);
                passwordText.setEnabled(false);
                isNTLMCheck.setEnabled(false);
            }

            if (!settings.getBoolean("isAuth", false) || !settings.getBoolean("isNTLM", false)) {
                domainText.setEnabled(false);
            }

            if (!"https".equals(settings.getString("proxyType", ""))) {
                certificateText.setEnabled(false);
            }

            SharedPreferences.Editor edit = settings.edit();

            if (Utils.isWorking()) {
                if (settings.getBoolean("isConnecting", false)) isRunningCheck.setEnabled(false);
                edit.putBoolean("isRunning", true);
            } else {
                if (settings.getBoolean("isRunning", false)) {
                    new Thread() {
                        @Override
                        public void run() {
                            reset();
                        }
                    }.start();
                }
                edit.putBoolean("isRunning", false);
            }

            edit.apply();

            if (settings.getBoolean("isRunning", false)) {
                ((SwitchPreference) isRunningCheck).setChecked(true);
                disableAll();
            } else {
                ((SwitchPreference) isRunningCheck).setChecked(false);
                enableAll();
            }

            // Setup the initial values
            ((ProxyDroid)getActivity()).profile = settings.getString("profile", "1");
            profileList.setValue(((ProxyDroid)getActivity()).profile);

            profileList.setSummary(getProfileName(((ProxyDroid)getActivity()).profile));

            Set<String> sSet = settings.getStringSet("ssid", null);
            if (!sSet.isEmpty()) {
                ssidList.setSummary(String.join(",", sSet));
            }
            sSet = settings.getStringSet("excludedSsid", null);
            if (!sSet.isEmpty()) {
                excludedSsidList.setSummary(String.join(",", sSet));
            }
            if (!settings.getString("user", "").equals("")) {
                userText.setSummary(settings.getString("user", getString(R.string.user_summary)));
            }
            if (!settings.getString("certificate", "").equals("")) {
                certificateText.setSummary(settings.getString("certificate", getString(R.string.certificate_summary)));
            }
            if (!settings.getString("bypassAddrs", "").equals("")) {
                bypassAddrs.setSummary(
                        settings.getString("bypassAddrs", getString(R.string.set_bypass_summary))
                                .replace("|", ", "));
            } else {
                bypassAddrs.setSummary(R.string.set_bypass_summary);
            }
            if (!settings.getString("port", "-1").equals("-1") && !settings.getString("port", "-1")
                    .equals("")) {
                portText.setSummary(settings.getString("port", getString(R.string.port_summary)));
            }
            if (!settings.getString("host", "").equals("")) {
                hostText.setSummary(settings.getString("host", getString(
                        settings.getBoolean("isPAC", false) ? R.string.host_pac_summary
                                : R.string.host_summary)));
            }
            if (!settings.getString("password", "").equals("")) passwordText.setSummary("*********");
            if (!settings.getString("proxyType", "").equals("")) {
                proxyTypeList.setSummary(settings.getString("proxyType", "").toUpperCase());
            }
            if (!settings.getString("domain", "").equals("")) {
                domainText.setSummary(settings.getString("domain", ""));
            }

            // Set up a listener whenever a key changes
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();

            // Unregister the listener whenever a key changes
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
            // Let's do something a preference value changes

            if (key.equals("profile")) {
                String profileString = settings.getString("profile", "");
                if (profileString.equals("0")) {
                    String[] profileEntries = settings.getString("profileEntries", "").split("\\|");
                    String[] profileValues = settings.getString("profileValues", "").split("\\|");
                    int newProfileValue = Integer.valueOf(profileValues[profileValues.length - 2]) + 1;

                    StringBuilder profileEntriesBuffer = new StringBuilder();
                    StringBuilder profileValuesBuffer = new StringBuilder();

                    for (int i = 0; i < profileValues.length - 1; i++) {
                        profileEntriesBuffer.append(profileEntries[i]).append("|");
                        profileValuesBuffer.append(profileValues[i]).append("|");
                    }
                    profileEntriesBuffer.append(getProfileName(Integer.toString(newProfileValue))).append("|");
                    profileValuesBuffer.append(newProfileValue).append("|");
                    profileEntriesBuffer.append(getString(R.string.profile_new));
                    profileValuesBuffer.append("0");

                    Editor ed = settings.edit();
                    ed.putString("profileEntries", profileEntriesBuffer.toString());
                    ed.putString("profileValues", profileValuesBuffer.toString());
                    ed.putString("profile", Integer.toString(newProfileValue));
                    ed.apply();

                    loadProfileList();
                } else {
                    String oldProfile = ((ProxyDroid)getActivity()).profile;
                    ((ProxyDroid)getActivity()).profile = profileString;
                    profileList.setValue(((ProxyDroid)getActivity()).profile);
                    onProfileChange(oldProfile);
                    profileList.setSummary(getProfileName(profileString));
                }
            }

            if (key.equals("isConnecting")) {
                if (settings.getBoolean("isConnecting", false)) {
                    Log.d(TAG, "Connecting start");
                    isRunningCheck.setEnabled(false);
                    pd = ProgressDialog.show(getActivity(), "", getString(R.string.connecting), true, true);
                } else {
                    Log.d(TAG, "Connecting finish");
                    if (pd != null) {
                        pd.dismiss();
                        pd = null;
                    }
                    isRunningCheck.setEnabled(true);
                }
            }

            if (key.equals("isPAC")) {
                if (settings.getBoolean("isPAC", false)) {
                    portText.setEnabled(false);
                    proxyTypeList.setEnabled(false);
                    hostText.setTitle(R.string.host_pac);
                } else {
                    portText.setEnabled(true);
                    proxyTypeList.setEnabled(true);
                    hostText.setTitle(R.string.host);
                }
                if (settings.getString("host", "").equals("")) {
                    hostText.setSummary(settings.getBoolean("isPAC", false) ? R.string.host_pac_summary
                            : R.string.host_summary);
                } else {
                    hostText.setSummary(settings.getString("host", ""));
                }
            }

            if (key.equals("isAuth")) {
                if (!settings.getBoolean("isAuth", false)) {
                    userText.setEnabled(false);
                    passwordText.setEnabled(false);
                    isNTLMCheck.setEnabled(false);
                    domainText.setEnabled(false);
                } else {
                    userText.setEnabled(true);
                    passwordText.setEnabled(true);
                    isNTLMCheck.setEnabled(true);
                    domainText.setEnabled(isNTLMCheck.isChecked());
                }
            }

            if (key.equals("isNTLM")) {
                domainText.setEnabled(settings.getBoolean("isAuth", false) && settings.getBoolean("isNTLM", false));
            }

            if (key.equals("proxyType")) {
                certificateText.setEnabled("https".equals(settings.getString("proxyType", "")));
            }

            if (key.equals("isAutoConnect")) {
                if (settings.getBoolean("isAutoConnect", false)) {
                    loadNetworkList();
                    ssidList.setEnabled(true);
                    excludedSsidList.setEnabled(true);
                } else {
                    ssidList.setEnabled(false);
                    excludedSsidList.setEnabled(false);
                }
            }

            if (key.equals("isAutoSetProxy")) {
                if (settings.getBoolean("isAutoSetProxy", false)) {
                    proxyedApps.setEnabled(false);
                    isBypassAppsCheck.setEnabled(false);
                } else {
                    proxyedApps.setEnabled(true);
                    isBypassAppsCheck.setEnabled(true);
                }
            }

            if (key.equals("isRunning")) {
                if (settings.getBoolean("isRunning", false)) {
                    disableAll();
                    ((SwitchPreference) isRunningCheck).setChecked(true);
                    if (!Utils.isConnecting()) serviceStart();
                } else {
                    enableAll();
                    ((SwitchPreference) isRunningCheck).setChecked(false);
                    if (!Utils.isConnecting()) serviceStop();
                }
            }

            switch (key) {
                case "ssid": {
                    final Set<String> sSet = settings.getStringSet("ssid", null);
                    if (sSet == null || sSet.isEmpty()) {
                        ssidList.setSummary(getString(R.string.ssid_summary));
                    } else {
                        ssidList.setSummary(String.join(",", sSet));
                    }
                    break;
                }
                case "excludedSsid": {
                    final Set<String> sSet = settings.getStringSet("excludedSsid", null);
                    if (sSet == null || sSet.isEmpty()) {
                        excludedSsidList.setSummary(getString(R.string.excluded_ssid_summary));
                    } else {
                        excludedSsidList.setSummary(String.join(",", sSet));
                    }
                    break;
                }
                case "user":
                    if (settings.getString("user", "").equals("")) {
                        userText.setSummary(getString(R.string.user_summary));
                    } else {
                        userText.setSummary(settings.getString("user", ""));
                    }
                    break;
                case "domain":
                    if (settings.getString("domain", "").equals("")) {
                        domainText.setSummary(getString(R.string.domain_summary));
                    } else {
                        domainText.setSummary(settings.getString("domain", ""));
                    }
                    break;
                case "proxyType":
                    if (settings.getString("proxyType", "").equals("")) {
                        proxyTypeList.setSummary(getString(R.string.proxy_type_summary));
                        certificateText.setSummary(getString(R.string.certificate_summary));
                    } else {
                        proxyTypeList.setSummary(settings.getString("proxyType", "").toUpperCase());
                        certificateText.setSummary(settings.getString("certificate", ""));
                    }
                    break;
                case "bypassAddrs":
                    if (settings.getString("bypassAddrs", "").equals("")) {
                        bypassAddrs.setSummary(getString(R.string.set_bypass_summary));
                    } else {
                        bypassAddrs.setSummary(settings.getString("bypassAddrs", "").replace("|", ", "));
                    }
                    break;
                case "port":
                    if (settings.getString("port", "-1").equals("-1") || settings.getString("port", "-1")
                            .equals("")) {
                        portText.setSummary(getString(R.string.port_summary));
                    } else {
                        portText.setSummary(settings.getString("port", ""));
                    }
                    break;
                case "host":
                    if (settings.getString("host", "").equals("")) {
                        hostText.setSummary(settings.getBoolean("isPAC", false) ? R.string.host_pac_summary
                                : R.string.host_summary);
                    } else {
                        hostText.setSummary(settings.getString("host", ""));
                    }
                    break;
                case "password":
                    if (!settings.getString("password", "").equals("")) {
                        passwordText.setSummary("*********");
                    } else {
                        passwordText.setSummary(getString(R.string.password_summary));
                    }
                    break;
            }
        }

        private boolean serviceStop() {

            if (!Utils.isWorking()) return false;

            try {
                getActivity().stopService(new Intent(getActivity(), ProxyDroidService.class));
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        /**
         * Called when connect button is clicked.
         */
        private boolean serviceStart() {

            if (Utils.isWorking()) return false;

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

            mProfile.getProfile(settings);

            try {

                Intent it = new Intent(getActivity(), ProxyDroidService.class);
                Bundle bundle = new Bundle();
                bundle.putString("host", mProfile.getHost());
                bundle.putString("user", mProfile.getUser());
                bundle.putString("bypassAddrs", mProfile.getBypassAddrs());
                bundle.putString("password", mProfile.getPassword());
                bundle.putString("domain", mProfile.getDomain());
                bundle.putString("certificate", mProfile.getCertificate());

                bundle.putString("proxyType", mProfile.getProxyType());
                bundle.putBoolean("isAutoSetProxy", mProfile.isAutoSetProxy());
                bundle.putBoolean("isBypassApps", mProfile.isBypassApps());
                bundle.putBoolean("isAuth", mProfile.isAuth());
                bundle.putBoolean("isNTLM", mProfile.isNTLM());
                bundle.putBoolean("isDNSProxy", mProfile.isDNSProxy());
                bundle.putBoolean("isPAC", mProfile.isPAC());

                bundle.putInt("port", mProfile.getPort());

                it.putExtras(bundle);
                getActivity().startService(it);
            } catch (Exception ignore) {
                // Nothing
                return false;
            }

            return true;
        }

        private void onProfileChange(String oldProfileName) {

            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

            mProfile.getProfile(settings);
            SharedPreferences.Editor ed = settings.edit();
            ed.putString(oldProfileName, mProfile.toString());
            ed.apply();

            String profileString = settings.getString(((ProxyDroid)getActivity()).profile, "");

            if (profileString.equals("")) {
                mProfile.init();
                mProfile.setName(getProfileName(((ProxyDroid)getActivity()).profile));
            } else {
                mProfile.decodeJson(profileString);
            }

            hostText.setText(mProfile.getHost());
            userText.setText(mProfile.getUser());
            passwordText.setText(mProfile.getPassword());
            domainText.setText(mProfile.getDomain());
            certificateText.setText(mProfile.getCertificate());
            proxyTypeList.setValue(mProfile.getProxyType());
            Set<String> sSet = mProfile.getSsid();
            if (sSet == null) sSet = new HashSet<>();
            ssidList.setValues(sSet);
            sSet = mProfile.getExcludedSsid();
            if (sSet == null) sSet = new HashSet<>();
            excludedSsidList.setValues(sSet);

            isAuthCheck.setChecked(mProfile.isAuth());
            isNTLMCheck.setChecked(mProfile.isNTLM());
            isAutoConnectCheck.setChecked(mProfile.isAutoConnect());
            isAutoSetProxyCheck.setChecked(mProfile.isAutoSetProxy());
            isBypassAppsCheck.setChecked(mProfile.isBypassApps());
            isPACCheck.setChecked(mProfile.isPAC());

            portText.setText(Integer.toString(mProfile.getPort()));

            Log.d(TAG, mProfile.toString());

            mProfile.setProfile(settings);
        }

        private void showAToast(String msg) {
            if (!getActivity().isFinishing()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(msg)
                        .setCancelable(false)
                        .setNegativeButton(getString(R.string.ok_iknow), (dialog, id) -> dialog.cancel());
                AlertDialog alert = builder.create();
                alert.show();
            }
        }

        private void disableAll() {
            hostText.setEnabled(false);
            portText.setEnabled(false);
            userText.setEnabled(false);
            passwordText.setEnabled(false);
            domainText.setEnabled(false);
            certificateText.setEnabled(false);
            ssidList.setEnabled(false);
            excludedSsidList.setEnabled(false);
            proxyTypeList.setEnabled(false);
            proxyedApps.setEnabled(false);
            profileList.setEnabled(false);
            bypassAddrs.setEnabled(false);

            isAuthCheck.setEnabled(false);
            isNTLMCheck.setEnabled(false);
            isAutoSetProxyCheck.setEnabled(false);
            isAutoConnectCheck.setEnabled(false);
            isPACCheck.setEnabled(false);
            isBypassAppsCheck.setEnabled(false);
        }

        private void enableAll() {
            hostText.setEnabled(true);

            if (!isPACCheck.isChecked()) {
                portText.setEnabled(true);
                proxyTypeList.setEnabled(true);
            }

            bypassAddrs.setEnabled(true);

            if (isAuthCheck.isChecked()) {
                userText.setEnabled(true);
                passwordText.setEnabled(true);
                isNTLMCheck.setEnabled(true);
                if (isNTLMCheck.isChecked()) domainText.setEnabled(true);
            }
            if ("https".equals(proxyTypeList.getValue())) {
                certificateText.setEnabled(true);
            }
            if (!isAutoSetProxyCheck.isChecked()) {
                proxyedApps.setEnabled(true);
                isBypassAppsCheck.setEnabled(true);
            }
            if (isAutoConnectCheck.isChecked()) {
                ssidList.setEnabled(true);
                excludedSsidList.setEnabled(true);
            }

            profileList.setEnabled(true);
            isAutoSetProxyCheck.setEnabled(true);
            isAuthCheck.setEnabled(true);
            isAutoConnectCheck.setEnabled(true);
            isPACCheck.setEnabled(true);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {

            if (preference.getKey() != null && preference.getKey().equals("bypassAddrs")) {
                Intent intent = new Intent(getActivity(), BypassListActivity.class);
                startActivity(intent);
            } else if (preference.getKey() != null && preference.getKey().equals("proxyedApps")) {
                Intent intent = new Intent(getActivity(), AppManager.class);
                startActivity(intent);
            } else if ("settings_key_notif_ringtone".equals(preference.getKey())) {
                final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

                final String existingValue = preference.getSharedPreferences().getString(preference.getKey(), null);
                if (existingValue != null) {
                    if (existingValue.length() == 0) {
                        // Select "Silent"
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                    } else {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingValue));
                    }
                } else {
                    // No ringtone has been selected, set to the default
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
                }
                mStartForResult.launch(intent);
                return true;
            }
            return super.onPreferenceTreeClick(preference);
        }

        private String getProfileName(String profile) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
            return settings.getString("profile" + profile,
                    getString(R.string.profile_base) + " " + profile);
        }

        public void rename() {
            LayoutInflater factory = LayoutInflater.from(getActivity());
            final View textEntryView = factory.inflate(R.layout.alert_dialog_text_entry, null);
            final EditText profileName = (EditText) textEntryView.findViewById(R.id.text_edit);
            profileName.setText(getProfileName(((ProxyDroid)getActivity()).profile));

            AlertDialog ad = new AlertDialog.Builder(getActivity()).setTitle(R.string.change_name)
                    .setView(textEntryView)
                    .setPositiveButton(R.string.alert_dialog_ok, (dialog, whichButton) -> {
                        EditText profileName1 = (EditText) textEntryView.findViewById(R.id.text_edit);
                        SharedPreferences settings =
                                PreferenceManager.getDefaultSharedPreferences(getActivity());
                        String name = profileName1.getText().toString();
                        name = name.replace("|", "");
                        if (name.length() <= 0) return;
                        Editor ed = settings.edit();
                        ed.putString("profile" + ((ProxyDroid)getActivity()).profile, name);
                        ed.apply();

                        profileList.setSummary(getProfileName(((ProxyDroid)getActivity()).profile));

                        String[] profileEntries = settings.getString("profileEntries", "").split("\\|");
                        String[] profileValues = settings.getString("profileValues", "").split("\\|");

                        StringBuilder profileEntriesBuffer = new StringBuilder();
                        StringBuilder profileValuesBuffer = new StringBuilder();

                        for (int i = 0; i < profileValues.length - 1; i++) {
                            if (profileValues[i].equals(((ProxyDroid)getActivity()).profile)) {
                                profileEntriesBuffer.append(getProfileName(((ProxyDroid)getActivity()).profile)).append("|");
                            } else {
                                profileEntriesBuffer.append(profileEntries[i]).append("|");
                            }
                            profileValuesBuffer.append(profileValues[i]).append("|");
                        }

                        profileEntriesBuffer.append(getString(R.string.profile_new));
                        profileValuesBuffer.append("0");

                        ed = settings.edit();
                        ed.putString("profileEntries", profileEntriesBuffer.toString());
                        ed.putString("profileValues", profileValuesBuffer.toString());

                        ed.apply();

                        loadProfileList();
                    })
                    .setNegativeButton(R.string.alert_dialog_cancel, (dialog, whichButton) -> {
                        /* User clicked cancel so do some stuff */
                    })
                    .create();
            ad.show();
        }

        public void delProfile(String profile) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String[] profileEntries = settings.getString("profileEntries", "").split("\\|");
            String[] profileValues = settings.getString("profileValues", "").split("\\|");

            Log.d(TAG, "Profile :" + profile);
            if (profileEntries.length > 2) {
                StringBuilder profileEntriesBuffer = new StringBuilder();
                StringBuilder profileValuesBuffer = new StringBuilder();

                String newProfileValue = "1";

                for (int i = 0; i < profileValues.length - 1; i++) {
                    if (!profile.equals(profileValues[i])) {
                        profileEntriesBuffer.append(profileEntries[i]).append("|");
                        profileValuesBuffer.append(profileValues[i]).append("|");
                        newProfileValue = profileValues[i];
                    }
                }
                profileEntriesBuffer.append(getString(R.string.profile_new));
                profileValuesBuffer.append("0");

                Editor ed = settings.edit();
                ed.putString("profileEntries", profileEntriesBuffer.toString());
                ed.putString("profileValues", profileValuesBuffer.toString());
                ed.putString("profile", newProfileValue);
                ed.apply();

                loadProfileList();
            }
        }

        private void copyFile(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        private void CopyAssets() {
            AssetManager assetManager = getActivity().getAssets();
            String[] files = null;
            String abi;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                abi = Build.SUPPORTED_ABIS[0];
            } else {
                abi = Build.CPU_ABI;
            }
            try {
                if (abi.matches("armeabi-v7a|arm64-v8a"))
                    files = assetManager.list("armeabi-v7a");
                else
                    files = assetManager.list("x86");
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            if (files != null) {
                for (String file : files) {
                    InputStream in;
                    OutputStream out;
                    try {
                        if (abi.matches("armeabi-v7a|arm64-v8a"))
                            in = assetManager.open("armeabi-v7a/" + file);
                        else
                            in = assetManager.open("x86/" + file);
                        out = new FileOutputStream(getActivity().getFilesDir().getAbsolutePath() + "/" + file);
                        copyFile(in, out);
                        in.close();
                        out.flush();
                        out.close();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        }

        public void reset() {
            try {
                getActivity().stopService(new Intent(getActivity(), ProxyDroidService.class));
            } catch (Exception ignored) {
            }

            CopyAssets();

            String filePath = getActivity().getFilesDir().getAbsolutePath();

            Utils.runRootCommand(Utils.getIptables()
                    + " -t nat -F OUTPUT\n"
                    + getActivity().getFilesDir().getAbsolutePath()
                    + "/proxy.sh stop\n"
                    + "kill -9 `cat " + filePath + "cntlm.pid`\n");

            Utils.runRootCommand(
                    "chmod 700 " + filePath + "/redsocks\n"
                            + "chmod 700 " + filePath + "/proxy.sh\n"
                            + "chmod 700 " + filePath + "/gost.sh\n"
                            + "chmod 700 " + filePath + "/cntlm\n"
                            + "chmod 700 " + filePath + "/gost\n");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(Menu.NONE, Menu.FIRST + 1, 4, getString(R.string.recovery))
                .setIcon(R.drawable.ic_baseline_restart_alt_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, Menu.FIRST + 2, 2, getString(R.string.profile_del))
                .setIcon(R.drawable.ic_baseline_delete_outline_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(Menu.NONE, Menu.FIRST + 3, 5, getString(R.string.about))
                .setIcon(R.drawable.ic_baseline_info_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(Menu.NONE, Menu.FIRST + 4, 1, getString(R.string.change_name))
                .setIcon(R.drawable.ic_baseline_drive_file_rename_outline_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case Menu.FIRST + 1:
                new Thread() {
                    @Override
                    public void run() {
                        ((SettingsFragment)(getSupportFragmentManager().getFragments().get(0))).reset();
                    }
                }.start();
                return true;
            case Menu.FIRST + 2:
                AlertDialog ad = new AlertDialog.Builder(this).setTitle(R.string.profile_del)
                        .setMessage(R.string.profile_del_confirm)
                        .setPositiveButton(R.string.alert_dialog_ok, (dialog, whichButton) -> {
                            /* User clicked OK so do some stuff */
                            ((SettingsFragment)(getSupportFragmentManager().getFragments().get(0))).delProfile(profile);
                        })
                        .setNegativeButton(R.string.alert_dialog_cancel, (dialog, whichButton) -> {
                            /* User clicked Cancel so do some stuff */
                            dialog.dismiss();
                        })
                        .create();

                ad.show();

                return true;
            case Menu.FIRST + 3:
                showAbout();
                return true;
            case Menu.FIRST + 4:
                ((SettingsFragment)(getSupportFragmentManager().getFragments().get(0))).rename();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            try {
                finish();
            } catch (Exception ignore) {
                // Nothing
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
