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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.document.BrandColorUtils;
import org.chromium.chrome.browser.preferences.BrowserPreferenceFragment;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.TextMessagePreference;
import org.chromium.content.browser.WebDefender;

/**
 * Fragment to show web defender details.
 */
public class WebDefenderDetailsPreferences extends BrowserPreferenceFragment {
    public static final String EXTRA_WEBDEFENDER_PARCEL = "extra_webdefender_parcel";
    private static final int BAR_GRAPH_HEIGHT = 100;
    private boolean mIsIncognito = false;
    private int mMaxBarGraphWidth;
    private WebDefender.ProtectionStatus mStatus;
    private String mTitle;
    private int mSmartProtectColor;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        addPreferencesFromResource(R.xml.webdefender_details_preferences);
        getActivity().setTitle(R.string.website_settings_webdefender_title);
        Bundle arguments = getArguments();
        if (arguments != null) {
            mIsIncognito = arguments.getBoolean(BrowserSingleWebsitePreferences.EXTRA_INCOGNITO);

            Object extraSite = arguments.getSerializable(SingleWebsitePreferences.EXTRA_SITE);
            Object extraOrigin = arguments.getSerializable(SingleWebsitePreferences.EXTRA_ORIGIN);

            if (extraSite != null && extraOrigin == null) {
                Website site = (Website) extraSite;
                mTitle = site.getAddress().getTitle();
            } else if (extraOrigin != null && extraSite == null) {
                WebsiteAddress siteAddress = WebsiteAddress.create((String) extraOrigin);
                mTitle = siteAddress.getTitle();
            }
            WebDefenderPreferenceHandler.StatusParcel parcel =
                    arguments.getParcelable(EXTRA_WEBDEFENDER_PARCEL);
            if (parcel != null)
                mStatus = parcel.getStatus();
        }

        if (mTitle != null) {
            TextMessagePreference siteTitle = (TextMessagePreference) findPreference("site_title");
            if (siteTitle != null) {
                siteTitle.setTitle(mTitle);
                byte[] data = arguments.getByteArray(BrowserSingleWebsitePreferences.EXTRA_FAVICON);
                if (data != null) {
                    Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bm != null) {
                        Drawable drawable = new BitmapDrawable(getResources(), bm);
                        siteTitle.setIcon(drawable);
                    }
                }
            }
        }

        TextMessagePreference overview = (TextMessagePreference) findPreference("overview");

        if (mStatus != null && mStatus.mTrackerDomains.length != 0)
            overview.setTitle(getOverviewMessage(getResources(), mStatus));

        Preference meter = findPreference("webdefender_privacy_meter");
        meter.setSummary("on " + mTitle);
        mSmartProtectColor = getResources().getColor(R.color.smart_protect);
    }

    public static String getOverviewMessage(Resources resources,
                                            WebDefender.ProtectionStatus status) {
        int count = 0;
        for (int i = 0; i < status.mTrackerDomains.length; i++) {
            if (status.mTrackerDomains[i].mProtectiveAction !=
                    WebDefender.TrackerDomain.PROTECTIVE_ACTION_UNBLOCK) {
                count++;
            }
        }

        return resources.getString(R.string.website_settings_webdefender_brief_message,count);
    }

    private void appendActionBarDisplayOptions(ActionBar bar, int extraOptions) {
        int options = bar.getDisplayOptions();
        options |= extraOptions;
        bar.setDisplayOptions(options);
    }

    private void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
            if (!PrefServiceBridge.getInstance().getPowersaveModeEnabled()) {
                activity.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            }

            int statusBarColor = BrandColorUtils.computeStatusBarColor(color);
            activity.getWindow().setStatusBarColor(statusBarColor);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            ActionBar bar = activity.getSupportActionBar();

            Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
                    R.drawable.img_deco_smartprotect_webdefender);

            appendActionBarDisplayOptions(bar,
                    ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
            bar.setHomeButtonEnabled(true);
            bar.setIcon(new BitmapDrawable(getResources(), bitmap));
            bar.setBackgroundDrawable(new ColorDrawable(
                    BrandColorUtils.computeActionBarColor(mSmartProtectColor)
            ));

            setStatusBarColor(mSmartProtectColor);
        }

        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mMaxBarGraphWidth = (int) (Math.min(size.x, size.y) * 0.40);
    }

    private static Drawable generateBarDrawable(int width, int height, int color) {
        PaintDrawable drawable = new PaintDrawable(color);
        drawable.setIntrinsicWidth(width);
        drawable.setIntrinsicHeight(height);
        drawable.setBounds(0, 0, width, height);

        Bitmap thumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(thumb);
        drawable.draw(c);

        return new BitmapDrawable(thumb);
    }

    private Drawable normalizedBarGraphDrawable(int value, int maxValue) {
        if (value == 0)
            return null;

        int normalizedWidth = mMaxBarGraphWidth * value / maxValue;

        return generateBarDrawable(normalizedWidth, BAR_GRAPH_HEIGHT, mSmartProtectColor);
    }

    private String getStringForCount(int count) {
        if (count == 0)
            return getResources().getString(
                    R.string.website_settings_webdefender_tracking_not_detected);

        return Integer.toString(count);
    }

    private static void createRatingStar(ImageView imageView, int height, int color) {
        Drawable drawable = generateBarDrawable(125, height, color);
        imageView.setImageDrawable(drawable);
    }

    public static void setupPrivacyMeterDisplay(View view, boolean enabled,
                                                WebDefender.ProtectionStatus status) {
        int possibleTrackerCount = 0;
        int starRating = 4;

        ImageView imageView = (ImageView) view.findViewById(R.id.star1);
        createRatingStar(imageView, 50, (starRating >= 1) ? Color.GREEN : Color.GRAY);

        imageView = (ImageView) view.findViewById(R.id.star2);
        createRatingStar(imageView, 50, (starRating >= 2) ? Color.GREEN : Color.GRAY);

        imageView = (ImageView) view.findViewById(R.id.star3);
        createRatingStar(imageView, 50, (starRating >= 3) ? Color.GREEN : Color.GRAY);

        imageView = (ImageView) view.findViewById(R.id.star4);
        createRatingStar(imageView, 50, (starRating >= 4) ? Color.GREEN : Color.GRAY);

        imageView = (ImageView) view.findViewById(R.id.star5);
        createRatingStar(imageView, 50, (starRating >= 5) ? Color.GREEN : Color.GRAY);

        TextView textView = (TextView) view.findViewById(R.id.count);
        if (enabled) {
            textView.setVisibility(View.VISIBLE);
            textView.setText("+10!");
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onChildViewAddedToHierarchy(View parent, View child) {
        TextView title = (TextView) child.findViewById(android.R.id.title);
        if (title != null && title.getText().equals(
               getResources().getText(R.string.website_settings_webdefender_privacy_meter_title))) {
            View meter = child.findViewById(R.id.webdefender_privacy_meter);
            if (meter != null) {
                WebDefenderDetailsPreferences.setupPrivacyMeterDisplay(meter, true, mStatus);
            }
        }


        if (child.getId() == R.id.browser_pref_cat
                || child.getId() == R.id.browser_pref_cat_first
                || child.getId() == R.id.browser_pref_cat_switch) {

            if (title != null) {
                title.setTextColor(mSmartProtectColor);
            }
        }

        if (child.getId() == R.id.webdefender_vectorchart_layout) {
            int numCookieTrackers = 0;
            int numStorageTrackers = 0;
            int numFingerprintTrackers = 0;
            int numFontEnumTrackers = 0;

            if (mStatus != null) {
                for (int i = 0; i < mStatus.mTrackerDomains.length; i++) {
                    if ((mStatus.mTrackerDomains[i].mTrackingMethods &
                            WebDefender.TrackerDomain.TRACKING_METHOD_HTTP_COOKIES) != 0) {
                        numCookieTrackers++;
                    }
                    if ((mStatus.mTrackerDomains[i].mTrackingMethods &
                            WebDefender.TrackerDomain.TRACKING_METHOD_HTML5_LOCAL_STORAGE) != 0) {
                        numStorageTrackers++;
                    }
                    if ((mStatus.mTrackerDomains[i].mTrackingMethods &
                            WebDefender.TrackerDomain.TRACKING_METHOD_CANVAS_FINGERPRINT) != 0) {
                        numFingerprintTrackers++;
                    }
                }
            }

            int max = Math.max(Math.max(numCookieTrackers, numFingerprintTrackers),
                    Math.max(numStorageTrackers, numFontEnumTrackers));

            TextView view = (TextView) child.findViewById(R.id.cookie_storage);
            if (view != null) {
                view.setText(getStringForCount(numCookieTrackers));
                view.setCompoundDrawablesWithIntrinsicBounds(
                        normalizedBarGraphDrawable(numCookieTrackers, max), null, null, null);
            }
            view = (TextView) child.findViewById(R.id.fingerprinting);
            if (view != null) {
                view.setText(getStringForCount(numStorageTrackers));
                view.setCompoundDrawablesWithIntrinsicBounds(
                        normalizedBarGraphDrawable(numStorageTrackers, max), null, null, null);
            }
            view = (TextView) child.findViewById(R.id.html5_storage);
            if (view != null) {
                view.setText(getStringForCount(numFingerprintTrackers));
                view.setCompoundDrawablesWithIntrinsicBounds(
                        normalizedBarGraphDrawable(numFingerprintTrackers, max), null, null, null);
            }
            view = (TextView) child.findViewById(R.id.font_enumeration);
            if (view != null) {
                view.setText(getStringForCount(numFontEnumTrackers));
                view.setCompoundDrawablesWithIntrinsicBounds(
                        normalizedBarGraphDrawable(numFontEnumTrackers, max), null, null, null);
            }
        } else if (child.getId() == R.id.webdefender_vectorlist_layout) {
            WebDefenderVectorsRecyclerView view =
                    (WebDefenderVectorsRecyclerView) child.findViewById(R.id.webdefender_vectors);
            if (view != null && mStatus != null) {
                view.updateVectorArray(mStatus.mTrackerDomains);
            }
        }
    }
}
