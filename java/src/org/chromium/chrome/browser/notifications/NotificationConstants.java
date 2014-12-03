// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.notifications;

/**
 * Constants used in more than a single Notification class, e.g. intents and extra names.
 */
public class NotificationConstants {
    // These actions have to be synchronized with the receiver defined in AndroidManfiest.xml.
    public static final String ACTION_CLICK_NOTIFICATION =
            "org.chromium.chrome.browser.notifications.CLICK_NOTIFICATION";
    public static final String ACTION_CLOSE_NOTIFICATION =
            "org.chromium.chrome.browser.notifications.CLOSE_NOTIFICATION";

    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
}