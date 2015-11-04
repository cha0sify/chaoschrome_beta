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
package org.chromium.chrome.browser.toolbar;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.VisibleForTesting;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.SiteTileView;
import org.chromium.chrome.browser.TabLoadStatus;
import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.chrome.browser.document.BrandColorUtils;
import org.chromium.chrome.browser.document.DocumentActivity;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.preferences.Preferences;
import org.chromium.chrome.browser.preferences.PreferencesLauncher;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.website.BrowserSingleWebsitePreferences;
import org.chromium.chrome.browser.preferences.website.WebRefinerPreferenceHandler;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.ssl.ConnectionSecurityLevel;
import org.chromium.chrome.browser.tab.ChromeTab;
import org.chromium.chrome.browser.tab.EmptyTabObserver;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabObserver;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;
import org.chromium.content_public.browser.LoadUrlParams;

public class ToolbarFavicon implements View.OnClickListener {

    private SiteTileView mFaviconView;
    private Context mContext;
    private ToolbarLayout mParent;
    private TabObserver mTabObserver;
    private Bitmap mFavicon;
    private Tab mTab;
    private LargeIconBridge mLargeIconBridge;
    private boolean mbSiteSettingsVisible;
    private boolean mBlockedCountSet = false;
    //Variable to track when the layout has decided to hide the favicon.
    private boolean mBrowsingModeViewsHidden = false;
    private static int mDefaultThemeColor;
    private static int mDefaultThemeColorIncognito;
    private boolean mUsingBrandColor;

    //Favicon statics
    private static final int FAVICON_MIN_SIZE = 48;
    private static final int FAVICON_CORNER_RADIUS = 4;
    private static final int FAVICON_TEXT_SIZE = 20;

    private ValueAnimator mAnimator;
    private Integer mStatusBarColor;

    public ToolbarFavicon(final ToolbarLayout parent) {
        mFaviconView = (SiteTileView) parent.findViewById(R.id.swe_favicon_badge);
        if (mFaviconView != null) {
            mFaviconView.setOnClickListener(this);
            mParent = parent;
            mContext = ApplicationStatus.getApplicationContext();
            mDefaultThemeColor = mContext.getResources().getColor(R.color.default_primary_color);
            mDefaultThemeColorIncognito = mContext.getResources().
                    getColor(R.color.incognito_primary_color);


            mTabObserver = new EmptyTabObserver() {
                @Override
                public void onSSLStateUpdated(Tab tab) {
                    refreshTabSecurityState();
                }

                //onContentChanged notifies us when the nativePages are modified/swapped
                @Override
                public void onContentChanged(Tab tab) {
                    if (mFavicon == null) refreshFavicon();
                }

                @Override
                public void onPageLoadStarted(Tab tab, String url) {
                    mBlockedCountSet = false;
                    mFaviconView.setBadgeBlockedObjectsCount(0); //Clear the count
                }

                @Override
                public void onDidCommitProvisionalLoadForFrame(Tab tab,
                                                               long frameId, boolean isMainFrame,
                                                               String url, int transitionType) {
                    if (isMainFrame) {
                        refreshFavicon();
                        refreshTabSecurityState();
                    }
                }

                @Override
                public void onLoadUrl(Tab tab, LoadUrlParams params, int loadType) {
                    if (loadType == TabLoadStatus.FULL_PRERENDERED_PAGE_LOAD ||
                            loadType == TabLoadStatus.PARTIAL_PRERENDERED_PAGE_LOAD) {
                        refreshFavicon();
                        refreshTabSecurityState();
                    }
                }

                @Override
                public void onPageLoadFinished(Tab tab) {
                    refreshTabSecurityState();
                }

                @Override
                public void onLoadProgressChanged(Tab tab, int progress) {
                    refreshBlockedCount();
                }

                @Override
                public void onFaviconUpdated(Tab tab) {
                    refreshFavicon();
                }

                public void onDidChangeThemeColor(Tab tab, int color) {
                    mUsingBrandColor = isBrandColor(color);
                    if (mFavicon != null) {
                        setStatusBarColor(FaviconHelper.getDominantColorForBitmap(mFavicon));
                    }
                }

            };

            refreshTab(mParent.getToolbarDataProvider().getTab());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                final Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
                mStatusBarColor = activity.getWindow().getStatusBarColor();
            }
        }

        mbSiteSettingsVisible = false;
    }

    private boolean isBrandColor(int color) {
        return color != mDefaultThemeColor && color != mDefaultThemeColorIncognito;
    }

    /**
     * @return True if tab doesn't exist/Or a Navtive page is visible since we don't want to update
     * the favicon when there's no tab or when showing a native page.
     */
    private boolean isNativePage() {
        return (mTab == null) ? true : mTab.isNativePage();
    }

    @Override
    public void onClick(View v) {
        if (mFaviconView == v && tabHasPermissions() ) {
            showCurrentSiteSettings();
            mbSiteSettingsVisible = true;
        }
    }

    private boolean tabHasPermissions() {
        if (mTab == null) return false;
        Uri parsedUrl = Uri.parse(mTab.getUrl());
        boolean isInternalPage = UrlUtilities.isInternalScheme(parsedUrl);
        return !isNativePage() && !mTab.isShowingInterstitialPage()
                && !mTab.isShowingSadTab() && !isInternalPage;
    }

    public void refreshTab(Tab tab) {
        if (mFaviconView == null || tab == mTab) return;

        if (mTab != null) {
            ChromeTab lastChromeTab = ChromeTab.fromTab(mTab);
            lastChromeTab.removeObserver(mTabObserver);
        }
        mTab = tab;

        ChromeTab chromeTab = ChromeTab.fromTab(tab);
        if (chromeTab != null) {
            chromeTab.addObserver(mTabObserver);
        }

        mBlockedCountSet = false;
        mFaviconView.setBadgeBlockedObjectsCount(0); //Clear the count

        refreshFavicon();
        refreshTabSecurityState();
        refreshBlockedCount();
    }

    private void refreshBlockedCount() {
        if (mBlockedCountSet == true || mTab == null ||
                mTab.getContentViewCore() == null) return ;
        int count = WebRefinerPreferenceHandler.getBlockedURLCount(
                mTab.getContentViewCore());
        mFaviconView.setBadgeBlockedObjectsCount(count);
        if (count > 0)
            mBlockedCountSet = true;
    }

    public final int getMeasuredWidth() {
        mbSiteSettingsVisible = false;
        return (mFaviconView != null) ? mFaviconView.getMeasuredWidth() : 0;
    }

    public final View getView() {
        return mFaviconView;
    }

    public void refreshFavicon() {
        if (isNativePage() && mFaviconView.getVisibility() != View.GONE) {
            mFaviconView.setVisibility(View.GONE);
            mFavicon = null;
        }
        if (mTab != null) {
            String url = mTab.getUrl();
            if (mLargeIconBridge == null) mLargeIconBridge =
                    new LargeIconBridge(Profile.getLastUsedProfile());
            LargeIconForTab callback = new LargeIconForTab(mTab);
            mLargeIconBridge.getLargeIconForUrl(
                    url, FAVICON_MIN_SIZE, callback);
        }
    }

    @VisibleForTesting
    public boolean isShowingSiteSettings() {
        return mbSiteSettingsVisible;
    }

    private void refreshTabSecurityState() {
        if (mFaviconView != null && mTab != null) {
            int level = mTab.getSecurityLevel();
            switch (level) {
                case ConnectionSecurityLevel.NONE:
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_UNKNOWN);
                    mFaviconView.setBadgeHasCertIssues(false);
                    break;
                case ConnectionSecurityLevel.SECURITY_WARNING:
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_UNTRUSTED);
                    mFaviconView.setBadgeHasCertIssues(true);
                    break;
                case ConnectionSecurityLevel.SECURITY_ERROR:
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_AVOID);
                    mFaviconView.setBadgeHasCertIssues(true);
                    break;
                case ConnectionSecurityLevel.SECURE:
                case ConnectionSecurityLevel.EV_SECURE:
                    mFaviconView.setTrustLevel(SiteTileView.TRUST_TRUSTED);
                    mFaviconView.setBadgeHasCertIssues(false);
                    break;
                default:
                    break;
            }
        }
    }

    private void setStatusBarColor(int color) {
        // The flag that allows coloring is disabled in PowerSaveMode, don't color
        if (PrefServiceBridge.getInstance().getPowersaveModeEnabled()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Activity activity = ApplicationStatus.getLastTrackedFocusedActivity();
            if (activity instanceof DocumentActivity && mUsingBrandColor) {
                return;
            }

            activity.getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            synchronized (this) {
                if (mAnimator != null && mAnimator.isRunning()) {
                    mAnimator.cancel();
                    mAnimator = null;
                }

                Integer to = BrandColorUtils.computeStatusBarColor(color);
                if (mStatusBarColor.intValue() == to.intValue()) {
                    activity.getWindow().setStatusBarColor(to.intValue());
                    return;
                }

                mAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), mStatusBarColor, to);

                mAnimator.addUpdateListener(
                        new ValueAnimator.AnimatorUpdateListener() {
                            @SuppressLint("NewApi")
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                synchronized (ToolbarFavicon.this) {
                                    Integer value = (Integer) animation.getAnimatedValue();
                                    activity.getWindow().setStatusBarColor(value.intValue());
                                    mStatusBarColor = value;
                                }
                            }
                        }
                );
                mAnimator.start();
            }
        }
    }

    private void showCurrentSiteSettings() {
        String url = mTab.getUrl();
        Context context = ApplicationStatus.getApplicationContext();

        Bitmap favicon = mFavicon != null ? mFavicon : mTab.getFavicon();
        Bundle fragmentArguments = BrowserSingleWebsitePreferences.createFragmentArgsForSite(url,
                favicon,
                mTab);
        Intent preferencesIntent = PreferencesLauncher.createIntentForSettingsPage(
                context, BrowserSingleWebsitePreferences.class.getName());
        preferencesIntent.putExtra(
                Preferences.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArguments);
        context.startActivity(preferencesIntent);
    }

    public void setVisibility(int browsingModeVisibility) {
        mBrowsingModeViewsHidden = browsingModeVisibility == View.INVISIBLE;

        if (mFavicon != null) {
            mFaviconView.setVisibility(browsingModeVisibility);
        }
    }


    class LargeIconForTab implements LargeIconBridge.LargeIconCallback {
        // Tab that made the request
        private Tab mClientTab;

        public LargeIconForTab(Tab tab) {
            mClientTab = tab;
        }

        @Override
        public void onLargeIconAvailable(Bitmap icon, int fallbackColor) {
            if (mClientTab == null || mTab == null || mTab != mClientTab) return;

            if (icon == null) {
                String url;
                url = mClientTab.getUrl();
                if (mClientTab.isIncognito()) {
                    fallbackColor = FaviconHelper.
                            getDominantColorForBitmap(mClientTab.getFavicon());
                }
                RoundedIconGenerator roundedIconGenerator = new RoundedIconGenerator(
                        mContext, FAVICON_MIN_SIZE, FAVICON_MIN_SIZE,
                        FAVICON_CORNER_RADIUS, fallbackColor,
                        FAVICON_TEXT_SIZE);
                icon = roundedIconGenerator.generateIconForUrl(url);
                setStatusBarColor(fallbackColor);
            } else {
                int color = FaviconHelper.getDominantColorForBitmap(icon);
                setStatusBarColor(color);
            }

            if (mFaviconView != null && !isNativePage()) {
                setFavicon(icon);
            }
        }
    }

    private void setFavicon(Bitmap icon) {
        mFavicon = icon;
        mFaviconView.replaceFavicon(icon);
        mFaviconView.setVisibility(mBrowsingModeViewsHidden ?
                View.GONE : View.VISIBLE);
    }
}
