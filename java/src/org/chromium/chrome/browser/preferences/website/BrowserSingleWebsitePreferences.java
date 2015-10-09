/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.chromium.chrome.browser.preferences.website;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ContentSettingsType;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.ssl.ConnectionSecurityLevel;
import org.chromium.chrome.browser.widget.TintedImageView;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

public class BrowserSingleWebsitePreferences extends SingleWebsitePreferences {

    public static final String EXTRA_SECURITY_CERT_LEVEL = "org.chromium.chrome.preferences." +
            "website_security_cert_level";
    public static final String EXTRA_FAVICON = "org.chromium.chrome.preferences.favicon";

    private int mSecurityLevel = -1;

    private int mSiteColor = -1;

    private SiteSecurityViewFactory mSecurityViews;
    private Preference mSecurityInfoPrefs;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mSecurityViews = new SiteSecurityViewFactory();
        mSecurityLevel = getArguments().getInt(EXTRA_SECURITY_CERT_LEVEL);
        updateSecurityInfo();
        super.onActivityCreated(savedInstanceState);
    }

    private void updateSecurityInfo() {
        switch (mSecurityLevel) {
            case ConnectionSecurityLevel.NONE:
                break;
            case ConnectionSecurityLevel.SECURITY_WARNING:
                mSecurityViews.appendText(SiteSecurityViewFactory.ViewType.WARNING,
                        getResources().getString(R.string.ssl_warning));
                break;
            case ConnectionSecurityLevel.SECURITY_ERROR:
                mSecurityViews.appendText(SiteSecurityViewFactory.ViewType.ERROR,
                        getResources().getString(R.string.ssl_error));
                break;
            case ConnectionSecurityLevel.SECURE:
            case ConnectionSecurityLevel.EV_SECURE:
                mSecurityViews.appendText(SiteSecurityViewFactory.ViewType.INFO,
                        getResources().getString(R.string.ssl_secure));
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentSetting permission = ContentSetting.fromString((String) newValue);
        createPermissionInfo(preference, permission);
        super.onPreferenceChange(preference, newValue);
        updateSecurityPreferenceVisibility();
        return true;
    }

    @Override
    protected void updateSecurityPreferenceVisibility() {
        //Now update any warnings we want to show to the user
        String securityWarnings = "";

        if (ContentSetting.ALLOW.equals(mSite.getGeolocationPermission())) {
            securityWarnings += (securityWarnings.isEmpty()) ? getResources().getString(
                    ContentSettingsResources.getTitle(ContentSettingsType.
                            CONTENT_SETTINGS_TYPE_GEOLOCATION))
                    : "";
        }

        if (!securityWarnings.isEmpty()) {
            securityWarnings += " ";
            securityWarnings += getResources().getString(R.string.page_info_permission_allowed);
            mSecurityViews.setText(SiteSecurityViewFactory.ViewType.WARNING, securityWarnings);
        } else {
            mSecurityViews.clearText(SiteSecurityViewFactory.ViewType.WARNING);
        }


        if (mSecurityViews.mbEmpty) {
            PreferenceScreen screen = getPreferenceScreen();

            if (mSecurityInfoPrefs == null) {
                mSecurityInfoPrefs = findPreference("site_security_info_title");
            }

            if (mSecurityInfoPrefs != null && screen != null) {
                screen.removePreference(mSecurityInfoPrefs);
            }
        } else {
            PreferenceScreen screen = getPreferenceScreen();

            Preference pref = findPreference("site_security_info_title");
            if (pref == null && mSecurityInfoPrefs != null) {
                screen.addPreference(mSecurityInfoPrefs);
            }
        }
    }

    @Override
    protected void updateSummary(Preference preference, int contentType, ContentSetting value){
        if (ContentSettingsResources.getDefaultEnabledValue(contentType).equals(ContentSetting.ASK))
        {
            // MEDIA_STREAM permissions like Mic and Camera only have an "ASK" and "BLOCK" option.
            if (preference.getKey().equals(PREF_CAMERA_CAPTURE_PERMISSION) ||
                    preference.getKey().equals(PREF_MIC_CAPTURE_PERMISSION)) {
                CharSequence[] entries = ((ListPreference)preference).getEntries();
                entries[0] = getResources().getString(R.string.website_settings_category_ask);
                ((ListPreference)preference).setEntries(entries);
            }

            if (value == ContentSetting.ASK)
                preference.setSummary(R.string.website_settings_category_ask);
        }
    }

    private static class SiteSecurityViewFactory {
        private class SiteSecurityView {
            private TextView mTextView;
            private View mContainer;
            private String mDisplayText;

            public SiteSecurityView(View parent, int resId, String text) {
                mContainer = parent.findViewById(resId);
                mTextView = (TextView) mContainer.findViewById(R.id.security_view_text);
                mTextView.setText(text);
                mDisplayText = text;
                updateVisibility();
            }

            private void updateVisibility() {
                if (TextUtils.isEmpty(mDisplayText)) {
                    mContainer.setVisibility(View.GONE);
                } else {
                    mContainer.setVisibility(View.VISIBLE);
                }
            }

            public void setText(String text) {
                mDisplayText = text;
                mTextView.setText(mDisplayText);
                updateVisibility();
            }

            public void clearText() {
                mDisplayText = null;
                updateVisibility();
            }
        }

        public enum ViewType{
            ERROR,
            WARNING,
            INFO
        };

        private Map<ViewType, SiteSecurityView> mViews =
                new EnumMap<ViewType, SiteSecurityView>(ViewType.class);
        private Map<ViewType, String> mTexts = new EnumMap<ViewType, String>(ViewType.class);

        private boolean mbEmpty = true;

        public void setText(ViewType type, String text) {
            mTexts.put(type, text);

            SiteSecurityView view = mViews.get(type);
            if (view != null) {
                view.setText(text);
            }

            mbEmpty = false;
        }

        public void appendText(ViewType type, String text) {
            String new_text = mTexts.get(type);
            if (new_text != null)
                new_text += text;
            else
                new_text = text;

            mTexts.put(type, new_text);

            SiteSecurityView view = mViews.get(type);
            if (view != null) {
                view.setText(new_text);
            }

            mbEmpty = false;
        }

        public void clearText(ViewType type) {
            mTexts.remove(type);

            SiteSecurityView view = mViews.get(type);
            if (view != null) {
                view.clearText();
            }

            boolean empty = true;
            for (Map.Entry<ViewType, String> entry: mTexts.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    empty = false;
                }
            }
            mbEmpty = empty;
        }

        public void setResource(ViewType type, View parent, int resId) {
            String text = mTexts.get(type);
            mViews.remove(type);
            mViews.put(type, new SiteSecurityView(parent, resId, text));
        }
    }

    /**
     * Get the global setting value for the given prefernece.
     * @param preference The ListPreference to be checked.
     */
    @Override
    protected ContentSetting getGlobalDefaultPermission(Preference preference) {
        String preferenceKey = preference.getKey();
        int contentType = getContentSettingsTypeFromPreferenceKey(preferenceKey);
        ContentSetting defaultValue = null;
        boolean isEnabled = false;

        if (PREF_CAMERA_CAPTURE_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isCameraEnabled();
        } else if (PREF_COOKIES_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isAcceptCookiesEnabled();
        } else if (PREF_FULLSCREEN_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isFullscreenAllowed();
        } else if (PREF_JAVASCRIPT_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().javaScriptEnabled();
        } else if (PREF_LOCATION_ACCESS.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isAllowLocationEnabled();
        } else if (PREF_MIC_CAPTURE_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isMicEnabled();
        } else if (PREF_POPUP_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().popupsEnabled();
        } else if (PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isProtectedMediaIdentifierEnabled();
        } else if (PREF_PUSH_NOTIFICATIONS_PERMISSION.equals(preferenceKey)) {
            isEnabled = PrefServiceBridge.getInstance().isPushNotificationsEnabled();
        } else {
            return null;
        }

        if (isEnabled) {
            defaultValue = ContentSettingsResources.getDefaultEnabledValue(contentType);
        } else {
            defaultValue = ContentSettingsResources.getDefaultDisabledValue(contentType);
        }

        return defaultValue;
    }

    /**
     * Create Info object for the given preference if it does not exist.
     * @param preference The ListPreference to initialize.
     * @param permission The ContentSetting to initialize it to.
     */
    private void createPermissionInfo(Preference preference, ContentSetting permission) {
        if (false == doesPermissionInfoExist(preference)) {
            String preferenceKey = preference.getKey();
            int contentType = getContentSettingsTypeFromPreferenceKey(preferenceKey);

            if (PREF_CAMERA_CAPTURE_PERMISSION.equals(preferenceKey)) {
                mSite.setCameraInfo(new CameraInfo(mSite.getAddress().getOrigin(),
                        null,
                        false));
            } else if (PREF_COOKIES_PERMISSION.equals(preferenceKey)) {
                mSite.setCookieInfo(new CookieInfo(mSite.getAddress().getOrigin(),
                        null,
                        false));
            } else if (PREF_FULLSCREEN_PERMISSION.equals(preferenceKey)) {
                mSite.setFullscreenInfo(new FullscreenInfo(mSite.getAddress().getOrigin(),
                        null,
                        false));
            } else if (PREF_JAVASCRIPT_PERMISSION.equals(preferenceKey)) {
                mSite.setJavaScriptException(new ContentSettingException(contentType,
                        mSite.getAddress().getOrigin(),
                        permission.toString(),
                        "policy"));
            } else if (PREF_LOCATION_ACCESS.equals(preferenceKey)) {
                mSite.setGeolocationInfo(new GeolocationInfo(mSite.getAddress().getOrigin(),
                        null,
                        false));
            } else if (PREF_MIC_CAPTURE_PERMISSION.equals(preferenceKey)) {
                mSite.setMicrophoneInfo(new MicrophoneInfo(mSite.getAddress().getOrigin(),
                        null,
                        false));
            } else if (PREF_POPUP_PERMISSION.equals(preferenceKey)) {
                mSite.setPopupException(new ContentSettingException(contentType,
                        mSite.getAddress().getOrigin(),
                        permission.toString(),
                        "policy"));
            } else if (PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION.equals(preferenceKey)) {
                mSite.setProtectedMediaIdentifierInfo(
                        new ProtectedMediaIdentifierInfo(mSite.getAddress().getOrigin(),
                                mSite.getAddress().getOrigin(),
                                false));
            } else if (PREF_PUSH_NOTIFICATIONS_PERMISSION.equals(preferenceKey)) {
                mSite.setPushNotificationInfo(
                        new PushNotificationInfo(mSite.getAddress().getOrigin(),
                                null,
                                false));
            }
        }
    }

    /**
     * Check whether the Info object for the given preference for this website already exists.
     * @param preference The ListPreference.
     */
    private boolean doesPermissionInfoExist(Preference preference) {
        String preferenceKey = preference.getKey();
        boolean doesExist = false;

        if (PREF_CAMERA_CAPTURE_PERMISSION.equals(preferenceKey)) {
            doesExist = mSite.getCameraInfo() != null;
        } else if (PREF_COOKIES_PERMISSION.equals(preferenceKey)) {
            doesExist = mSite.getCookieInfo() != null;
        } else if (PREF_FULLSCREEN_PERMISSION.equals(preferenceKey)) {
            doesExist = mSite.getFullscreenInfo() != null;
        } else if (PREF_JAVASCRIPT_PERMISSION.equals(preferenceKey)) {
            doesExist = mSite.getJavaScriptPermission() != null;
        } else if (PREF_LOCATION_ACCESS.equals(preferenceKey)) {
            doesExist = mSite.getGeolocationInfo() != null;
        } else if (PREF_MIC_CAPTURE_PERMISSION.equals(preferenceKey)) {
            doesExist = mSite.getMicrophoneInfo() != null;
        } else if (PREF_POPUP_PERMISSION.equals(preferenceKey)) {
            doesExist = mSite.getPopupException() != null;
        } else if (PREF_PROTECTED_MEDIA_IDENTIFIER_PERMISSION.equals(preferenceKey)) {
            doesExist = mSite.getProtectedMediaIdentifierInfo() != null;
        } else if (PREF_PUSH_NOTIFICATIONS_PERMISSION.equals(preferenceKey)) {
            doesExist = mSite.getPushNotificationInfo() != null;
        }

        return doesExist;
    }

    @Override
    public void onChildViewAddedToHierarchy(View parent, View child) {
        if (child.getId() == R.id.site_security_info) {
            mSecurityViews.setResource(SiteSecurityViewFactory.ViewType.ERROR,
                    child, R.id.site_security_error);
            mSecurityViews.setResource(SiteSecurityViewFactory.ViewType.WARNING,
                    child, R.id.site_security_warning);
            mSecurityViews.setResource(SiteSecurityViewFactory.ViewType.INFO,
                    child, R.id.site_security_verbose);
        }
        if (mSiteColor != -1) {
            if (child.getId() == R.id.browser_pref_cat
                    || child.getId() == R.id.browser_pref_cat_first) {
                TextView view = (TextView) child.findViewById(android.R.id.title);
                if (view != null) {
                    view.setTextColor(mSiteColor);
                }
            }
            Button btn = (Button) child.findViewById(R.id.button_preference);
            if (btn != null) {
                btn.setBackgroundColor(mSiteColor);
            }
            ImageView imageView = (ImageView) child.findViewById(R.id.clear_site_data);
            if (imageView != null && imageView instanceof TintedImageView) {
                ColorStateList colorList = ColorStateList.valueOf(mSiteColor);
                ((TintedImageView) imageView).setTint(colorList);
            }
        }
    }

    private void appendActionBarDisplayOptions(ActionBar bar, int extraOptions) {
        int options = bar.getDisplayOptions();
        options |= extraOptions;
        bar.setDisplayOptions(options);
    }

    private void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
            activity.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[2] *= 0.7f;
            mSiteColor = Color.HSVToColor(Color.alpha(color), hsv);
            activity.getWindow().setStatusBarColor(mSiteColor);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            ActionBar bar = activity.getSupportActionBar();
            Bundle args = getArguments();
            if (bar != null && args != null) {
                byte[] data = args.getByteArray(EXTRA_FAVICON);
                if (data != null) {
                    Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bm != null) {
                        Bitmap bitmap = Bitmap.createScaledBitmap(bm, 150, 150, true);
                        int color = FaviconHelper.getDominantColorForBitmap(bitmap);
                        appendActionBarDisplayOptions(bar,
                                ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
                        bar.setHomeButtonEnabled(true);
                        bar.setIcon(new BitmapDrawable(getResources(), bitmap));
                        bar.setBackgroundDrawable(new ColorDrawable(color));
                        setStatusBarColor(color);
                    }
                }
            }
        }
    }

    /**
     * Creates a Bundle with the correct arguments for opening this fragment for
     * the website with the given url and icon.
     *
     * @param url The URL to open the fragment with. This is a complete url including scheme,
     *            domain, port,  path, etc.
     * @param icon The favicon for the URL
     * @return The bundle to attach to the preferences intent.
     */
    public static Bundle createFragmentArgsForSite(String url, Bitmap icon, int securityLevel) {
        Bundle fragmentArgs = new Bundle();
        // TODO(mvanouwerkerk): Define a pure getOrigin method in UrlUtilities that is the
        // equivalent of the call below, because this is perfectly fine for non-display purposes.
        String origin = UrlUtilities.getOriginForDisplay(URI.create(url), true /*  showScheme */);
        fragmentArgs.putString(SingleWebsitePreferences.EXTRA_ORIGIN, origin);
        fragmentArgs.putInt(BrowserSingleWebsitePreferences.EXTRA_SECURITY_CERT_LEVEL, securityLevel);

        if (icon != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            icon.compress(Bitmap.CompressFormat.PNG, 100, baos);
            fragmentArgs.putByteArray(EXTRA_FAVICON, baos.toByteArray());
        }
        return fragmentArgs;
    }

}
