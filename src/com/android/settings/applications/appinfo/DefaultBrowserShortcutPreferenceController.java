/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.settings.applications.appinfo;

import android.content.Context;
import android.os.UserHandle;

import com.android.settings.applications.defaultapps.DefaultBrowserPreferenceController;

public class DefaultBrowserShortcutPreferenceController
        extends DefaultAppShortcutPreferenceControllerBase {

    private static final String KEY = "default_browser";

    public DefaultBrowserShortcutPreferenceController(Context context, String packageName) {
        super(context, KEY, packageName);
    }

    @Override
    protected boolean hasAppCapability() {
        return DefaultBrowserPreferenceController
                .hasBrowserPreference(mPackageName, mContext, UserHandle.myUserId());
    }

    @Override
    protected boolean isDefaultApp() {
        return new DefaultBrowserPreferenceController(mContext)
                .isBrowserDefault(mPackageName, UserHandle.myUserId());
    }

}