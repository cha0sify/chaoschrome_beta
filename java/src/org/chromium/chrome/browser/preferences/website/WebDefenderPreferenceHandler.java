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

import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.WebDefender;
import org.chromium.content.browser.WebRefiner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for webdefender that deals with initializing and handling webdefender
 * related settings
 */
public class WebDefenderPreferenceHandler {
    private static boolean mWebDefenderSetupComplete = false;
    private static HashMap<String, ContentSetting> mIncognitoPermissions;

    /**
     * Sets up webdefender when the browser initializes.
     */
    static public void applyInitialPreferences() {
        if (WebDefender.isInitialized() && !mWebDefenderSetupComplete) {

            boolean allowed = PrefServiceBridge.getInstance().isWebDefenderEnabled();
            WebDefender.getInstance().setDefaultPermission(allowed);

            WebsitePermissionsFetcher fetcher = new WebsitePermissionsFetcher(
                    new WebsitePermissionsFetcher.WebsitePermissionsCallback() {
                        @Override
                        public void onWebsitePermissionsAvailable(
                                Map<String, Set<Website>> sitesByOrigin,
                                Map<String, Set<Website>> sitesByHost) {
                            ArrayList<String> allowList = new ArrayList<>();
                            ArrayList<String> blockList = new ArrayList<>();

                            for (Map.Entry<String,
                                    Set<Website>> element : sitesByOrigin.entrySet()) {
                                for (Website site : element.getValue()) {

                                    ContentSetting permission = site.getWebRefinerPermission();
                                    if (permission != null) {
                                        if (permission == ContentSetting.ALLOW) {
                                            allowList.add(site.getAddress().getOrigin());
                                        } else if (permission == ContentSetting.BLOCK) {
                                            blockList.add(site.getAddress().getOrigin());
                                        }
                                    }
                                }
                            }
                            if (!allowList.isEmpty()) {
                                WebDefender.getInstance().setPermissionForOrigins(
                                        allowList.toArray(new String[allowList.size()]), WebRefiner.PERMISSION_ENABLE, false);
                            }

                            if (!blockList.isEmpty()) {
                                WebDefender.getInstance().setPermissionForOrigins(
                                        blockList.toArray(new String[blockList.size()]), WebRefiner.PERMISSION_DISABLE, false);
                            }
                            mWebDefenderSetupComplete = true;
                        }
                    }
            );

            fetcher.fetchPreferencesForCategory(SiteSettingsCategory.fromString(SiteSettingsCategory
                    .CATEGORY_WEBDEFENDER));
        }
    }

    public static boolean isInitialized() {
        return WebDefender.isInitialized();
    }

    public static void setWebDefenderEnabled(boolean enabled) {
        if (!isInitialized()) return;
        WebDefender.getInstance().setDefaultPermission(enabled);
    }

    public static void useDefaultPermissionForOrigins(String origin) {
        if (!isInitialized()) return;
        String[] origins = new String[1];
        origins[0] = origin;
        WebDefender.getInstance().setPermissionForOrigins(origins, WebRefiner.PERMISSION_USE_DEFAULT, false);
    }

    public static void setWebDefenderSettingForOrigin(String origin, boolean enabled) {
        if (!isInitialized()) return;
        String[] origins = new String[1];
        origins[0] = origin;
        int permission = enabled ? WebRefiner.PERMISSION_ENABLE : WebRefiner.PERMISSION_DISABLE;
        WebDefender.getInstance().setPermissionForOrigins(origins, permission, false);
    }

    public static void addIncognitoOrigin(String origin, ContentSetting permission) {
        setWebDefenderSettingForOrigin(origin, permission == ContentSetting.ALLOW);
        if (mIncognitoPermissions == null) {
            mIncognitoPermissions = new HashMap<>();
        }
        mIncognitoPermissions.put(origin, permission);
    }

    public static ContentSetting getSettingForIncognitoOrigin(String origin) {

        if (mIncognitoPermissions != null && mIncognitoPermissions.containsKey(origin)) {
            return mIncognitoPermissions.get(origin);
        }
        return null;
    }

    public static void clearIncognitoOrigin(String origin) {
        if (mIncognitoPermissions != null && mIncognitoPermissions.containsKey(origin)) {
            mIncognitoPermissions.remove(origin);
        }
    }

    public static void onIncognitoSessionFinish() {
        mIncognitoPermissions = null;
    }
}
