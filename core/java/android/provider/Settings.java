/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.provider;

import static android.provider.SettingsValidators.ANY_INTEGER_VALIDATOR;
import static android.provider.SettingsValidators.ANY_STRING_VALIDATOR;
import static android.provider.SettingsValidators.BOOLEAN_VALIDATOR;
import static android.provider.SettingsValidators.COMPONENT_NAME_VALIDATOR;
import static android.provider.SettingsValidators.LENIENT_IP_ADDRESS_VALIDATOR;
import static android.provider.SettingsValidators.LOCALE_VALIDATOR;
import static android.provider.SettingsValidators.NON_NEGATIVE_INTEGER_VALIDATOR;
import static android.provider.SettingsValidators.NULLABLE_COMPONENT_NAME_VALIDATOR;
import static android.provider.SettingsValidators.PACKAGE_NAME_VALIDATOR;
import static android.provider.SettingsValidators.URI_VALIDATOR;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.annotation.UserIdInt;
import android.app.ActivityThread;
import android.app.AppOpsManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.net.ConnectivityManager;
import android.net.NetworkScoreManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.DropBoxManager;
import android.os.IBinder;
import android.os.LocaleList;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.SettingsValidators.Validator;
import android.speech.tts.TextToSpeech;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.AndroidException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.MemoryIntArray;
import android.view.textservice.TextServicesManager;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.ColorDisplayController;
import com.android.internal.widget.ILockSettings;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Settings provider contains global system-level device preferences.
 */
public final class Settings {

    // Intent actions for Settings

    /**
     * Activity Action: Show system settings.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SETTINGS = "android.settings.SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of APNs.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * <p class="note">
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APN_SETTINGS = "android.settings.APN_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of current location
     * sources.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_LOCATION_SOURCE_SETTINGS =
            "android.settings.LOCATION_SOURCE_SETTINGS";

    /**
     * Activity Action: Show scanning settings to allow configuration of Wi-Fi
     * and Bluetooth scanning settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_LOCATION_SCANNING_SETTINGS =
            "android.settings.LOCATION_SCANNING_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of users.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_USER_SETTINGS =
            "android.settings.USER_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of wireless controls
     * such as Wi-Fi, Bluetooth and Mobile networks.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIRELESS_SETTINGS =
            "android.settings.WIRELESS_SETTINGS";

    /**
     * Activity Action: Show tether provisioning activity.
     *
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: {@link ConnectivityManager#EXTRA_TETHER_TYPE} should be included to specify which type
     * of tethering should be checked. {@link ConnectivityManager#EXTRA_PROVISION_CALLBACK} should
     * contain a {@link ResultReceiver} which will be called back with a tether result code.
     * <p>
     * Output: The result of the provisioning check.
     * {@link ConnectivityManager#TETHER_ERROR_NO_ERROR} if successful,
     * {@link ConnectivityManager#TETHER_ERROR_PROVISION_FAILED} for failure.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_TETHER_PROVISIONING =
            "android.settings.TETHER_PROVISIONING_UI";

    /**
     * Activity Action: Show settings to allow entering/exiting airplane mode.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_AIRPLANE_MODE_SETTINGS =
            "android.settings.AIRPLANE_MODE_SETTINGS";

    /**
     * Activity Action: Show mobile data usage list.
     * <p>
     * Input: {@link EXTRA_NETWORK_TEMPLATE} and {@link EXTRA_SUB_ID} should be included to specify
     * how and what mobile data statistics should be collected.
     * <p>
     * Output: Nothing
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MOBILE_DATA_USAGE =
            "android.settings.MOBILE_DATA_USAGE";

    /** @hide */
    public static final String EXTRA_NETWORK_TEMPLATE = "network_template";

    /**
     * An int extra specifying a subscription ID.
     *
     * @see android.telephony.SubscriptionInfo#getSubscriptionId
     */
    public static final String EXTRA_SUB_ID = "android.provider.extra.SUB_ID";

    /**
     * Activity Action: Modify Airplane mode settings using a voice command.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
     * <p>
     * This intent MUST be started using
     * {@link android.service.voice.VoiceInteractionSession#startVoiceActivity
     * startVoiceActivity}.
     * <p>
     * Note: The activity implementing this intent MUST verify that
     * {@link android.app.Activity#isVoiceInteraction isVoiceInteraction} returns true before
     * modifying the setting.
     * <p>
     * Input: To tell which state airplane mode should be set to, add the
     * {@link #EXTRA_AIRPLANE_MODE_ENABLED} extra to this Intent with the state specified.
     * If the extra is not included, no changes will be made.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_VOICE_CONTROL_AIRPLANE_MODE =
            "android.settings.VOICE_CONTROL_AIRPLANE_MODE";

    /**
     * Activity Action: Show settings for accessibility modules.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ACCESSIBILITY_SETTINGS =
            "android.settings.ACCESSIBILITY_SETTINGS";

    /**
     * Activity Action: Show settings to control access to usage information.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_USAGE_ACCESS_SETTINGS =
            "android.settings.USAGE_ACCESS_SETTINGS";

    /**
     * Activity Category: Show application settings related to usage access.
     * <p>
     * An activity that provides a user interface for adjusting usage access related
     * preferences for its containing application. Optional but recommended for apps that
     * use {@link android.Manifest.permission#PACKAGE_USAGE_STATS}.
     * <p>
     * The activity may define meta-data to describe what usage access is
     * used for within their app with {@link #METADATA_USAGE_ACCESS_REASON}, which
     * will be displayed in Settings.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.INTENT_CATEGORY)
    public static final String INTENT_CATEGORY_USAGE_ACCESS_CONFIG =
            "android.intent.category.USAGE_ACCESS_CONFIG";

    /**
     * Metadata key: Reason for needing usage access.
     * <p>
     * A key for metadata attached to an activity that receives action
     * {@link #INTENT_CATEGORY_USAGE_ACCESS_CONFIG}, shown to the
     * user as description of how the app uses usage access.
     * <p>
     */
    public static final String METADATA_USAGE_ACCESS_REASON =
            "android.settings.metadata.USAGE_ACCESS_REASON";

    /**
     * Activity Action: Show settings to allow configuration of security and
     * location privacy.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SECURITY_SETTINGS =
            "android.settings.SECURITY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of trusted external sources
     *
     * Input: Optionally, the Intent's data URI can specify the application package name to
     * directly invoke the management GUI specific to the package name. For example
     * "package:com.my.app".
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_UNKNOWN_APP_SOURCES =
            "android.settings.MANAGE_UNKNOWN_APP_SOURCES";

    /**
     * Activity Action: Show trusted credentials settings, opening to the user tab,
     * to allow management of installed credentials.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_TRUSTED_CREDENTIALS_USER =
            "com.android.settings.TRUSTED_CREDENTIALS_USER";

    /**
     * Activity Action: Show dialog explaining that an installed CA cert may enable
     * monitoring of encrypted network traffic.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this. Add {@link #EXTRA_NUMBER_OF_CERTIFICATES} extra to indicate the
     * number of certificates.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MONITORING_CERT_INFO =
            "com.android.settings.MONITORING_CERT_INFO";

    /**
     * Activity Action: Show settings to allow configuration of privacy options.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_PRIVACY_SETTINGS =
            "android.settings.PRIVACY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of VPN.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_VPN_SETTINGS =
            "android.settings.VPN_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Wi-Fi.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIFI_SETTINGS =
            "android.settings.WIFI_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of a static IP
     * address for Wi-Fi.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard
     * against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIFI_IP_SETTINGS =
            "android.settings.WIFI_IP_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of data and view data usage.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DATA_USAGE_SETTINGS =
            "android.settings.DATA_USAGE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Bluetooth.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_BLUETOOTH_SETTINGS =
            "android.settings.BLUETOOTH_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Assist Gesture.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ASSIST_GESTURE_SETTINGS =
            "android.settings.ASSIST_GESTURE_SETTINGS";

    /**
     * Activity Action: Show settings to enroll fingerprints, and setup PIN/Pattern/Pass if
     * necessary.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_FINGERPRINT_ENROLL =
            "android.settings.FINGERPRINT_ENROLL";

    /**
     * Activity Action: Show settings to allow configuration of cast endpoints.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_CAST_SETTINGS =
            "android.settings.CAST_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of date and time.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DATE_SETTINGS =
            "android.settings.DATE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of sound and volume.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SOUND_SETTINGS =
            "android.settings.SOUND_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of display.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DISPLAY_SETTINGS =
            "android.settings.DISPLAY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Night display.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NIGHT_DISPLAY_SETTINGS =
            "android.settings.NIGHT_DISPLAY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of locale.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_LOCALE_SETTINGS =
            "android.settings.LOCALE_SETTINGS";

    /**
     * Activity Action: Show settings to configure input methods, in particular
     * allowing the user to enable input methods.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_VOICE_INPUT_SETTINGS =
            "android.settings.VOICE_INPUT_SETTINGS";

    /**
     * Activity Action: Show settings to configure input methods, in particular
     * allowing the user to enable input methods.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_INPUT_METHOD_SETTINGS =
            "android.settings.INPUT_METHOD_SETTINGS";

    /**
     * Activity Action: Show settings to enable/disable input method subtypes.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * To tell which input method's subtypes are displayed in the settings, add
     * {@link #EXTRA_INPUT_METHOD_ID} extra to this Intent with the input method id.
     * If there is no extra in this Intent, subtypes from all installed input methods
     * will be displayed in the settings.
     *
     * @see android.view.inputmethod.InputMethodInfo#getId
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_INPUT_METHOD_SUBTYPE_SETTINGS =
            "android.settings.INPUT_METHOD_SUBTYPE_SETTINGS";

    /**
     * Activity Action: Show settings to manage the user input dictionary.
     * <p>
     * Starting with {@link android.os.Build.VERSION_CODES#KITKAT},
     * it is guaranteed there will always be an appropriate implementation for this Intent action.
     * In prior releases of the platform this was optional, so ensure you safeguard against it.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_USER_DICTIONARY_SETTINGS =
            "android.settings.USER_DICTIONARY_SETTINGS";

    /**
     * Activity Action: Show settings to configure the hardware keyboard.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_HARD_KEYBOARD_SETTINGS =
            "android.settings.HARD_KEYBOARD_SETTINGS";

    /**
     * Activity Action: Adds a word to the user dictionary.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: An extra with key <code>word</code> that contains the word
     * that should be added to the dictionary.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_USER_DICTIONARY_INSERT =
            "com.android.settings.USER_DICTIONARY_INSERT";

    /**
     * Activity Action: Show settings to allow configuration of application-related settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APPLICATION_SETTINGS =
            "android.settings.APPLICATION_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of application
     * development-related settings.  As of
     * {@link android.os.Build.VERSION_CODES#JELLY_BEAN_MR1} this action is
     * a required part of the platform.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APPLICATION_DEVELOPMENT_SETTINGS =
            "android.settings.APPLICATION_DEVELOPMENT_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of quick launch shortcuts.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_QUICK_LAUNCH_SETTINGS =
            "android.settings.QUICK_LAUNCH_SETTINGS";

    /**
     * Activity Action: Show settings to manage installed applications.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_APPLICATIONS_SETTINGS =
            "android.settings.MANAGE_APPLICATIONS_SETTINGS";

    /**
     * Activity Action: Show settings to manage all applications.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS =
            "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS";

    /**
     * Activity Action: Show screen for controlling which apps can draw on top of other apps.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Optionally, the Intent's data URI can specify the application package name to
     * directly invoke the management GUI specific to the package name. For example
     * "package:com.my.app".
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_OVERLAY_PERMISSION =
            "android.settings.action.MANAGE_OVERLAY_PERMISSION";

    /**
     * Activity Action: Show screen for controlling which apps are allowed to write/modify
     * system settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Optionally, the Intent's data URI can specify the application package name to
     * directly invoke the management GUI specific to the package name. For example
     * "package:com.my.app".
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_WRITE_SETTINGS =
            "android.settings.action.MANAGE_WRITE_SETTINGS";

    /**
     * Activity Action: Show screen of details about a particular application.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: The Intent's data URI specifies the application package name
     * to be shown, with the "package" scheme.  That is "package:com.my.app".
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APPLICATION_DETAILS_SETTINGS =
            "android.settings.APPLICATION_DETAILS_SETTINGS";

    /**
     * Activity Action: Show the "Open by Default" page in a particular application's details page.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
     * <p>
     * Input: The Intent's data URI specifies the application package name
     * to be shown, with the "package" scheme. That is "package:com.my.app".
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APPLICATION_DETAILS_SETTINGS_OPEN_BY_DEFAULT_PAGE =
            "android.settings.APPLICATION_DETAILS_SETTINGS_OPEN_BY_DEFAULT_PAGE";

    /**
     * Activity Action: Show list of applications that have been running
     * foreground services (to the user "running in the background").
     * <p>
     * Input: Extras "packages" is a string array of package names.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_FOREGROUND_SERVICES_SETTINGS =
            "android.settings.FOREGROUND_SERVICES_SETTINGS";

    /**
     * Activity Action: Show screen for controlling which apps can ignore battery optimizations.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * <p>
     * You can use {@link android.os.PowerManager#isIgnoringBatteryOptimizations
     * PowerManager.isIgnoringBatteryOptimizations()} to determine if an application is
     * already ignoring optimizations.  You can use
     * {@link #ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS} to ask the user to put you
     * on this list.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS =
            "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS";

    /**
     * Activity Action: Ask the user to allow an app to ignore battery optimizations (that is,
     * put them on the whitelist of apps shown by
     * {@link #ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS}).  For an app to use this, it also
     * must hold the {@link android.Manifest.permission#REQUEST_IGNORE_BATTERY_OPTIMIZATIONS}
     * permission.
     * <p><b>Note:</b> most applications should <em>not</em> use this; there are many facilities
     * provided by the platform for applications to operate correctly in the various power
     * saving modes.  This is only for unusual applications that need to deeply control their own
     * execution, at the potential expense of the user's battery life.  Note that these applications
     * greatly run the risk of showing to the user as high power consumers on their device.</p>
     * <p>
     * Input: The Intent's data URI must specify the application package name
     * to be shown, with the "package" scheme.  That is "package:com.my.app".
     * <p>
     * Output: Nothing.
     * <p>
     * You can use {@link android.os.PowerManager#isIgnoringBatteryOptimizations
     * PowerManager.isIgnoringBatteryOptimizations()} to determine if an application is
     * already ignoring optimizations.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS =
            "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";

    /**
     * Activity Action: Show screen for controlling background data
     * restrictions for a particular application.
     * <p>
     * Input: Intent's data URI set with an application name, using the
     * "package" schema (like "package:com.my.app").
     *
     * <p>
     * Output: Nothing.
     * <p>
     * Applications can also use {@link android.net.ConnectivityManager#getRestrictBackgroundStatus
     * ConnectivityManager#getRestrictBackgroundStatus()} to determine the
     * status of the background data restrictions for them.
     *
     * <p class="note">
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS =
            "android.settings.IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS";

    /**
     * @hide
     * Activity Action: Show the "app ops" settings screen.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APP_OPS_SETTINGS =
            "android.settings.APP_OPS_SETTINGS";

    /**
     * Activity Action: Show settings for system update functionality.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SYSTEM_UPDATE_SETTINGS =
            "android.settings.SYSTEM_UPDATE_SETTINGS";

    /**
     * Activity Action: Show settings for managed profile settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGED_PROFILE_SETTINGS =
            "android.settings.MANAGED_PROFILE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of sync settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * The account types available to add via the add account button may be restricted by adding an
     * {@link #EXTRA_AUTHORITIES} extra to this Intent with one or more syncable content provider's
     * authorities. Only account types which can sync with that content provider will be offered to
     * the user.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SYNC_SETTINGS =
            "android.settings.SYNC_SETTINGS";

    /**
     * Activity Action: Show add account screen for creating a new account.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * The account types available to add may be restricted by adding an {@link #EXTRA_AUTHORITIES}
     * extra to the Intent with one or more syncable content provider's authorities.  Only account
     * types which can sync with that content provider will be offered to the user.
     * <p>
     * Account types can also be filtered by adding an {@link #EXTRA_ACCOUNT_TYPES} extra to the
     * Intent with one or more account types.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ADD_ACCOUNT =
            "android.settings.ADD_ACCOUNT_SETTINGS";

    /**
     * Activity Action: Show settings for selecting the network operator.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * The subscription ID of the subscription for which available network operators should be
     * displayed may be optionally specified with {@link #EXTRA_SUB_ID}.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NETWORK_OPERATOR_SETTINGS =
            "android.settings.NETWORK_OPERATOR_SETTINGS";

    /**
     * Activity Action: Show settings for selection of 2G/3G.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DATA_ROAMING_SETTINGS =
            "android.settings.DATA_ROAMING_SETTINGS";

    /**
     * Activity Action: Show settings for internal storage.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_INTERNAL_STORAGE_SETTINGS =
            "android.settings.INTERNAL_STORAGE_SETTINGS";
    /**
     * Activity Action: Show settings for memory card storage.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MEMORY_CARD_SETTINGS =
            "android.settings.MEMORY_CARD_SETTINGS";

    /**
     * Activity Action: Show settings for global search.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SEARCH_SETTINGS =
        "android.search.action.SEARCH_SETTINGS";

    /**
     * Activity Action: Show general device information settings (serial
     * number, software version, phone number, etc.).
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DEVICE_INFO_SETTINGS =
        "android.settings.DEVICE_INFO_SETTINGS";

    /**
     * Activity Action: Show NFC settings.
     * <p>
     * This shows UI that allows NFC to be turned on or off.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     * @see android.nfc.NfcAdapter#isEnabled()
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NFC_SETTINGS = "android.settings.NFC_SETTINGS";

    /**
     * Activity Action: Show NFC Sharing settings.
     * <p>
     * This shows UI that allows NDEF Push (Android Beam) to be turned on or
     * off.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     * @see android.nfc.NfcAdapter#isNdefPushEnabled()
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NFCSHARING_SETTINGS =
        "android.settings.NFCSHARING_SETTINGS";

    /**
     * Activity Action: Show NFC Tap & Pay settings
     * <p>
     * This shows UI that allows the user to configure Tap&Pay
     * settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NFC_PAYMENT_SETTINGS =
        "android.settings.NFC_PAYMENT_SETTINGS";

    /**
     * Activity Action: Show Daydream settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @see android.service.dreams.DreamService
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DREAM_SETTINGS = "android.settings.DREAM_SETTINGS";

    /**
     * Activity Action: Show Notification listener settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @see android.service.notification.NotificationListenerService
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NOTIFICATION_LISTENER_SETTINGS
            = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    /**
     * Activity Action: Show Do Not Disturb access settings.
     * <p>
     * Users can grant and deny access to Do Not Disturb configuration from here.
     * See {@link android.app.NotificationManager#isNotificationPolicyAccessGranted()} for more
     * details.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * <p class="note">
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            = "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS";

    /**
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_CONDITION_PROVIDER_SETTINGS
            = "android.settings.ACTION_CONDITION_PROVIDER_SETTINGS";

    /**
     * Activity Action: Show settings for video captioning.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard
     * against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_CAPTIONING_SETTINGS = "android.settings.CAPTIONING_SETTINGS";

    /**
     * Activity Action: Show the top level print settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_PRINT_SETTINGS =
            "android.settings.ACTION_PRINT_SETTINGS";

    /**
     * Activity Action: Show Zen Mode configuration settings.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS";

    /**
     * Activity Action: Show Zen Mode visual effects configuration settings.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ZEN_MODE_BLOCKED_EFFECTS_SETTINGS =
            "android.settings.ZEN_MODE_BLOCKED_EFFECTS_SETTINGS";

    /**
     * Activity Action: Show Zen Mode onboarding activity.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ZEN_MODE_ONBOARDING = "android.settings.ZEN_MODE_ONBOARDING";

    /**
     * Activity Action: Show Zen Mode (aka Do Not Disturb) priority configuration settings.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ZEN_MODE_PRIORITY_SETTINGS
            = "android.settings.ZEN_MODE_PRIORITY_SETTINGS";

    /**
     * Activity Action: Show Zen Mode automation configuration settings.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ZEN_MODE_AUTOMATION_SETTINGS
            = "android.settings.ZEN_MODE_AUTOMATION_SETTINGS";

    /**
     * Activity Action: Modify do not disturb mode settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
     * <p>
     * This intent MUST be started using
     * {@link android.service.voice.VoiceInteractionSession#startVoiceActivity
     * startVoiceActivity}.
     * <p>
     * Note: The Activity implementing this intent MUST verify that
     * {@link android.app.Activity#isVoiceInteraction isVoiceInteraction}.
     * returns true before modifying the setting.
     * <p>
     * Input: The optional {@link #EXTRA_DO_NOT_DISTURB_MODE_MINUTES} extra can be used to indicate
     * how long the user wishes to avoid interruptions for. The optional
     * {@link #EXTRA_DO_NOT_DISTURB_MODE_ENABLED} extra can be to indicate if the user is
     * enabling or disabling do not disturb mode. If either extra is not included, the
     * user maybe asked to provide the value.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_VOICE_CONTROL_DO_NOT_DISTURB_MODE =
            "android.settings.VOICE_CONTROL_DO_NOT_DISTURB_MODE";

    /**
     * Activity Action: Show Zen Mode schedule rule configuration settings.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ZEN_MODE_SCHEDULE_RULE_SETTINGS
            = "android.settings.ZEN_MODE_SCHEDULE_RULE_SETTINGS";

    /**
     * Activity Action: Show Zen Mode event rule configuration settings.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ZEN_MODE_EVENT_RULE_SETTINGS
            = "android.settings.ZEN_MODE_EVENT_RULE_SETTINGS";

    /**
     * Activity Action: Show Zen Mode external rule configuration settings.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ZEN_MODE_EXTERNAL_RULE_SETTINGS
            = "android.settings.ZEN_MODE_EXTERNAL_RULE_SETTINGS";

    /**
     * Activity Action: Show the regulatory information screen for the device.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard
     * against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String
            ACTION_SHOW_REGULATORY_INFO = "android.settings.SHOW_REGULATORY_INFO";

    /**
     * Activity Action: Show Device Name Settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard
     * against this.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String DEVICE_NAME_SETTINGS = "android.settings.DEVICE_NAME";

    /**
     * Activity Action: Show pairing settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard
     * against this.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_PAIRING_SETTINGS = "android.settings.PAIRING_SETTINGS";

    /**
     * Activity Action: Show battery saver settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard
     * against this.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_BATTERY_SAVER_SETTINGS
            = "android.settings.BATTERY_SAVER_SETTINGS";

    /**
     * Activity Action: Modify Battery Saver mode setting using a voice command.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you safeguard against this.
     * <p>
     * This intent MUST be started using
     * {@link android.service.voice.VoiceInteractionSession#startVoiceActivity
     * startVoiceActivity}.
     * <p>
     * Note: The activity implementing this intent MUST verify that
     * {@link android.app.Activity#isVoiceInteraction isVoiceInteraction} returns true before
     * modifying the setting.
     * <p>
     * Input: To tell which state batter saver mode should be set to, add the
     * {@link #EXTRA_BATTERY_SAVER_MODE_ENABLED} extra to this Intent with the state specified.
     * If the extra is not included, no changes will be made.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE =
            "android.settings.VOICE_CONTROL_BATTERY_SAVER_MODE";

    /**
     * Activity Action: Show Home selection settings. If there are multiple activities
     * that can satisfy the {@link Intent#CATEGORY_HOME} intent, this screen allows you
     * to pick your preferred activity.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_HOME_SETTINGS
            = "android.settings.HOME_SETTINGS";

    /**
     * Activity Action: Show Default apps settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_MANAGE_DEFAULT_APPS_SETTINGS
            = "android.settings.MANAGE_DEFAULT_APPS_SETTINGS";

    /**
     * Activity Action: Show notification settings.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_NOTIFICATION_SETTINGS
            = "android.settings.NOTIFICATION_SETTINGS";

    /**
     * Activity Action: Show app listing settings, filtered by those that send notifications.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ALL_APPS_NOTIFICATION_SETTINGS =
            "android.settings.ALL_APPS_NOTIFICATION_SETTINGS";

    /**
     * Activity Action: Show notification settings for a single app.
     * <p>
     *     Input: {@link #EXTRA_APP_PACKAGE}, the package to display.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APP_NOTIFICATION_SETTINGS
            = "android.settings.APP_NOTIFICATION_SETTINGS";

    /**
     * Activity Action: Show notification settings for a single {@link NotificationChannel}.
     * <p>
     *     Input: {@link #EXTRA_APP_PACKAGE}, the package containing the channel to display.
     *     Input: {@link #EXTRA_CHANNEL_ID}, the id of the channel to display.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_CHANNEL_NOTIFICATION_SETTINGS
            = "android.settings.CHANNEL_NOTIFICATION_SETTINGS";

    /**
     * Activity Extra: The package owner of the notification channel settings to display.
     * <p>
     * This must be passed as an extra field to the {@link #ACTION_CHANNEL_NOTIFICATION_SETTINGS}.
     */
    public static final String EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";

    /**
     * Activity Extra: The {@link NotificationChannel#getId()} of the notification channel settings
     * to display.
     * <p>
     * This must be passed as an extra field to the {@link #ACTION_CHANNEL_NOTIFICATION_SETTINGS}.
     */
    public static final String EXTRA_CHANNEL_ID = "android.provider.extra.CHANNEL_ID";

    /**
     * Activity Action: Show notification redaction settings.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APP_NOTIFICATION_REDACTION
            = "android.settings.ACTION_APP_NOTIFICATION_REDACTION";

    /** @hide */ public static final String EXTRA_APP_UID = "app_uid";

    /**
     * Activity Action: Show a dialog with disabled by policy message.
     * <p> If an user action is disabled by policy, this dialog can be triggered to let
     * the user know about this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SHOW_ADMIN_SUPPORT_DETAILS
            = "android.settings.SHOW_ADMIN_SUPPORT_DETAILS";

    /**
     * Activity Action: Show a dialog for remote bugreport flow.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SHOW_REMOTE_BUGREPORT_DIALOG
            = "android.settings.SHOW_REMOTE_BUGREPORT_DIALOG";

    /**
     * Activity Action: Show VR listener settings.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * @see android.service.vr.VrListenerService
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_VR_LISTENER_SETTINGS
            = "android.settings.VR_LISTENER_SETTINGS";

    /**
     * Activity Action: Show Picture-in-picture settings.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_PICTURE_IN_PICTURE_SETTINGS
            = "android.settings.PICTURE_IN_PICTURE_SETTINGS";

    /**
     * Activity Action: Show Storage Manager settings.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_STORAGE_MANAGER_SETTINGS
            = "android.settings.STORAGE_MANAGER_SETTINGS";

    /**
     * Activity Action: Allows user to select current webview implementation.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WEBVIEW_SETTINGS = "android.settings.WEBVIEW_SETTINGS";

    /**
     * Activity Action: Show enterprise privacy section.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * @hide
     */
    @SystemApi
    @TestApi
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_ENTERPRISE_PRIVACY_SETTINGS
            = "android.settings.ENTERPRISE_PRIVACY_SETTINGS";

    /**
     * Activity Action: Show screen that let user select its Autofill Service.
     * <p>
     * Input: Intent's data URI set with an application name, using the
     * "package" schema (like "package:com.my.app").
     *
     * <p>
     * Output: {@link android.app.Activity#RESULT_OK} if user selected an Autofill Service belonging
     * to the caller package.
     *
     * <p>
     * <b>NOTE: </b> Applications should call
     * {@link android.view.autofill.AutofillManager#hasEnabledAutofillServices()} and
     * {@link android.view.autofill.AutofillManager#isAutofillSupported()}, and only use this action
     * to start an activity if they return {@code false} and {@code true} respectively.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_REQUEST_SET_AUTOFILL_SERVICE =
            "android.settings.REQUEST_SET_AUTOFILL_SERVICE";

    /**
     * Activity Action: Show screen for controlling which apps have access on volume directories.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * <p>
     * Applications typically use this action to ask the user to revert the "Do not ask again"
     * status of directory access requests made by
     * {@link android.os.storage.StorageVolume#createAccessIntent(String)}.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_STORAGE_VOLUME_ACCESS_SETTINGS =
            "android.settings.STORAGE_VOLUME_ACCESS_SETTINGS";

    // End of Intent actions for Settings

    /**
     * @hide - Private call() method on SettingsProvider to read from 'system' table.
     */
    public static final String CALL_METHOD_GET_SYSTEM = "GET_system";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'secure' table.
     */
    public static final String CALL_METHOD_GET_SECURE = "GET_secure";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'global' table.
     */
    public static final String CALL_METHOD_GET_GLOBAL = "GET_global";

    /**
     * @hide - Specifies that the caller of the fast-path call()-based flow tracks
     * the settings generation in order to cache values locally. If this key is
     * mapped to a <code>null</code> string extra in the request bundle, the response
     * bundle will contain the same key mapped to a parcelable extra which would be
     * an {@link android.util.MemoryIntArray}. The response will also contain an
     * integer mapped to the {@link #CALL_METHOD_GENERATION_INDEX_KEY} which is the
     * index in the array clients should use to lookup the generation. For efficiency
     * the caller should request the generation tracking memory array only if it
     * doesn't already have it.
     *
     * @see #CALL_METHOD_GENERATION_INDEX_KEY
     */
    public static final String CALL_METHOD_TRACK_GENERATION_KEY = "_track_generation";

    /**
     * @hide Key with the location in the {@link android.util.MemoryIntArray} where
     * to look up the generation id of the backing table. The value is an integer.
     *
     * @see #CALL_METHOD_TRACK_GENERATION_KEY
     */
    public static final String CALL_METHOD_GENERATION_INDEX_KEY = "_generation_index";

    /**
     * @hide Key with the settings table generation. The value is an integer.
     *
     * @see #CALL_METHOD_TRACK_GENERATION_KEY
     */
    public static final String CALL_METHOD_GENERATION_KEY = "_generation";

    /**
     * @hide - User handle argument extra to the fast-path call()-based requests
     */
    public static final String CALL_METHOD_USER_KEY = "_user";

    /**
     * @hide - Boolean argument extra to the fast-path call()-based requests
     */
    public static final String CALL_METHOD_MAKE_DEFAULT_KEY = "_make_default";

    /**
     * @hide - User handle argument extra to the fast-path call()-based requests
     */
    public static final String CALL_METHOD_RESET_MODE_KEY = "_reset_mode";

    /**
     * @hide - String argument extra to the fast-path call()-based requests
     */
    public static final String CALL_METHOD_TAG_KEY = "_tag";

    /** @hide - Private call() method to write to 'system' table */
    public static final String CALL_METHOD_PUT_SYSTEM = "PUT_system";

    /** @hide - Private call() method to write to 'secure' table */
    public static final String CALL_METHOD_PUT_SECURE = "PUT_secure";

    /** @hide - Private call() method to write to 'global' table */
    public static final String CALL_METHOD_PUT_GLOBAL= "PUT_global";

    /** @hide - Private call() method to reset to defaults the 'global' table */
    public static final String CALL_METHOD_RESET_GLOBAL = "RESET_global";

    /** @hide - Private call() method to reset to defaults the 'secure' table */
    public static final String CALL_METHOD_RESET_SECURE = "RESET_secure";

    /**
     * Activity Extra: Limit available options in launched activity based on the given authority.
     * <p>
     * This can be passed as an extra field in an Activity Intent with one or more syncable content
     * provider's authorities as a String[]. This field is used by some intents to alter the
     * behavior of the called activity.
     * <p>
     * Example: The {@link #ACTION_ADD_ACCOUNT} intent restricts the account types available based
     * on the authority given.
     */
    public static final String EXTRA_AUTHORITIES = "authorities";

    /**
     * Activity Extra: Limit available options in launched activity based on the given account
     * types.
     * <p>
     * This can be passed as an extra field in an Activity Intent with one or more account types
     * as a String[]. This field is used by some intents to alter the behavior of the called
     * activity.
     * <p>
     * Example: The {@link #ACTION_ADD_ACCOUNT} intent restricts the account types to the specified
     * list.
     */
    public static final String EXTRA_ACCOUNT_TYPES = "account_types";

    public static final String EXTRA_INPUT_METHOD_ID = "input_method_id";

    /**
     * Activity Extra: The device identifier to act upon.
     * <p>
     * This can be passed as an extra field in an Activity Intent with a single
     * InputDeviceIdentifier. This field is used by some activities to jump straight into the
     * settings for the given device.
     * <p>
     * Example: The {@link #ACTION_INPUT_METHOD_SETTINGS} intent opens the keyboard layout
     * dialog for the given device.
     * @hide
     */
    public static final String EXTRA_INPUT_DEVICE_IDENTIFIER = "input_device_identifier";

    /**
     * Activity Extra: Enable or disable Airplane Mode.
     * <p>
     * This can be passed as an extra field to the {@link #ACTION_VOICE_CONTROL_AIRPLANE_MODE}
     * intent as a boolean to indicate if it should be enabled.
     */
    public static final String EXTRA_AIRPLANE_MODE_ENABLED = "airplane_mode_enabled";

    /**
     * Activity Extra: Enable or disable Battery saver mode.
     * <p>
     * This can be passed as an extra field to the {@link #ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE}
     * intent as a boolean to indicate if it should be enabled.
     */
    public static final String EXTRA_BATTERY_SAVER_MODE_ENABLED =
            "android.settings.extra.battery_saver_mode_enabled";

    /**
     * Activity Extra: Enable or disable Do Not Disturb mode.
     * <p>
     * This can be passed as an extra field to the {@link #ACTION_VOICE_CONTROL_DO_NOT_DISTURB_MODE}
     * intent as a boolean to indicate if it should be enabled.
     */
    public static final String EXTRA_DO_NOT_DISTURB_MODE_ENABLED =
            "android.settings.extra.do_not_disturb_mode_enabled";

    /**
     * Activity Extra: How many minutes to enable do not disturb mode for.
     * <p>
     * This can be passed as an extra field to the {@link #ACTION_VOICE_CONTROL_DO_NOT_DISTURB_MODE}
     * intent to indicate how long do not disturb mode should be enabled for.
     */
    public static final String EXTRA_DO_NOT_DISTURB_MODE_MINUTES =
            "android.settings.extra.do_not_disturb_mode_minutes";

    /**
     * Reset mode: reset to defaults only settings changed by the
     * calling package. If there is a default set the setting
     * will be set to it, otherwise the setting will be deleted.
     * This is the only type of reset available to non-system clients.
     * @hide
     */
    public static final int RESET_MODE_PACKAGE_DEFAULTS = 1;

    /**
     * Reset mode: reset all settings set by untrusted packages, which is
     * packages that aren't a part of the system, to the current defaults.
     * If there is a default set the setting will be set to it, otherwise
     * the setting will be deleted. This mode is only available to the system.
     * @hide
     */
    public static final int RESET_MODE_UNTRUSTED_DEFAULTS = 2;

    /**
     * Reset mode: delete all settings set by untrusted packages, which is
     * packages that aren't a part of the system. If a setting is set by an
     * untrusted package it will be deleted if its default is not provided
     * by the system, otherwise the setting will be set to its default.
     * This mode is only available to the system.
     * @hide
     */
    public static final int RESET_MODE_UNTRUSTED_CHANGES = 3;

    /**
     * Reset mode: reset all settings to defaults specified by trusted
     * packages, which is packages that are a part of the system, and
     * delete all settings set by untrusted packages. If a setting has
     * a default set by a system package it will be set to the default,
     * otherwise the setting will be deleted. This mode is only available
     * to the system.
     * @hide
     */
    public static final int RESET_MODE_TRUSTED_DEFAULTS = 4;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "RESET_MODE_" }, value = {
            RESET_MODE_PACKAGE_DEFAULTS,
            RESET_MODE_UNTRUSTED_DEFAULTS,
            RESET_MODE_UNTRUSTED_CHANGES,
            RESET_MODE_TRUSTED_DEFAULTS
    })
    public @interface ResetMode{}


    /**
     * User has not started setup personalization.
     * @hide
     */
    public static final int USER_SETUP_PERSONALIZATION_NOT_STARTED = 0;

    /**
     * User has not yet completed setup personalization.
     * @hide
     */
    public static final int USER_SETUP_PERSONALIZATION_STARTED = 1;

    /**
     * User has snoozed personalization and will complete it later.
     * @hide
     */
    public static final int USER_SETUP_PERSONALIZATION_PAUSED = 2;

    /**
     * User has completed setup personalization.
     * @hide
     */
    public static final int USER_SETUP_PERSONALIZATION_COMPLETE = 10;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            USER_SETUP_PERSONALIZATION_NOT_STARTED,
            USER_SETUP_PERSONALIZATION_STARTED,
            USER_SETUP_PERSONALIZATION_PAUSED,
            USER_SETUP_PERSONALIZATION_COMPLETE
    })
    public @interface UserSetupPersonalization {}

    /**
     * Activity Extra: Number of certificates
     * <p>
     * This can be passed as an extra field to the {@link #ACTION_MONITORING_CERT_INFO}
     * intent to indicate the number of certificates
     * @hide
     */
    public static final String EXTRA_NUMBER_OF_CERTIFICATES =
            "android.settings.extra.number_of_certificates";

    private static final String JID_RESOURCE_PREFIX = "android";

    public static final String AUTHORITY = "settings";

    private static final String TAG = "Settings";
    private static final boolean LOCAL_LOGV = false;

    // Lock ensures that when enabling/disabling the master location switch, we don't end up
    // with a partial enable/disable state in multi-threaded situations.
    private static final Object mLocationSettingsLock = new Object();

    // Used in system server calling uid workaround in call()
    private static boolean sInSystemServer = false;
    private static final Object sInSystemServerLock = new Object();

    /** @hide */
    public static void setInSystemServer() {
        synchronized (sInSystemServerLock) {
            sInSystemServer = true;
        }
    }

    /** @hide */
    public static boolean isInSystemServer() {
        synchronized (sInSystemServerLock) {
            return sInSystemServer;
        }
    }

    public static class SettingNotFoundException extends AndroidException {
        public SettingNotFoundException(String msg) {
            super(msg);
        }
    }

    /**
     * Common base for tables of name/value settings.
     */
    public static class NameValueTable implements BaseColumns {
        public static final String NAME = "name";
        public static final String VALUE = "value";

        protected static boolean putString(ContentResolver resolver, Uri uri,
                String name, String value) {
            // The database will take care of replacing duplicates.
            try {
                ContentValues values = new ContentValues();
                values.put(NAME, name);
                values.put(VALUE, value);
                resolver.insert(uri, values);
                return true;
            } catch (SQLException e) {
                Log.w(TAG, "Can't set key " + name + " in " + uri, e);
                return false;
            }
        }

        public static Uri getUriFor(Uri uri, String name) {
            return Uri.withAppendedPath(uri, name);
        }
    }

    private static final class GenerationTracker {
        private final MemoryIntArray mArray;
        private final Runnable mErrorHandler;
        private final int mIndex;
        private int mCurrentGeneration;

        public GenerationTracker(@NonNull MemoryIntArray array, int index,
                int generation, Runnable errorHandler) {
            mArray = array;
            mIndex = index;
            mErrorHandler = errorHandler;
            mCurrentGeneration = generation;
        }

        public boolean isGenerationChanged() {
            final int currentGeneration = readCurrentGeneration();
            if (currentGeneration >= 0) {
                if (currentGeneration == mCurrentGeneration) {
                    return false;
                }
                mCurrentGeneration = currentGeneration;
            }
            return true;
        }

        public int getCurrentGeneration() {
            return mCurrentGeneration;
        }

        private int readCurrentGeneration() {
            try {
                return mArray.get(mIndex);
            } catch (IOException e) {
                Log.e(TAG, "Error getting current generation", e);
                if (mErrorHandler != null) {
                    mErrorHandler.run();
                }
            }
            return -1;
        }

        public void destroy() {
            try {
                mArray.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing backing array", e);
                if (mErrorHandler != null) {
                    mErrorHandler.run();
                }
            }
        }
    }

    private static final class ContentProviderHolder {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private final Uri mUri;
        @GuardedBy("mLock")
        private IContentProvider mContentProvider;

        public ContentProviderHolder(Uri uri) {
            mUri = uri;
        }

        public IContentProvider getProvider(ContentResolver contentResolver) {
            synchronized (mLock) {
                if (mContentProvider == null) {
                    mContentProvider = contentResolver
                            .acquireProvider(mUri.getAuthority());
                }
                return mContentProvider;
            }
        }

        public void clearProviderForTest() {
            synchronized (mLock) {
                mContentProvider = null;
            }
        }
    }

    // Thread-safe.
    private static class NameValueCache {
        private static final boolean DEBUG = false;

        private static final String[] SELECT_VALUE_PROJECTION = new String[] {
                Settings.NameValueTable.VALUE
        };

        private static final String NAME_EQ_PLACEHOLDER = "name=?";

        // Must synchronize on 'this' to access mValues and mValuesVersion.
        private final HashMap<String, String> mValues = new HashMap<>();

        private final Uri mUri;
        private final ContentProviderHolder mProviderHolder;

        // The method we'll call (or null, to not use) on the provider
        // for the fast path of retrieving settings.
        private final String mCallGetCommand;
        private final String mCallSetCommand;

        @GuardedBy("this")
        private GenerationTracker mGenerationTracker;

        public NameValueCache(Uri uri, String getCommand, String setCommand,
                ContentProviderHolder providerHolder) {
            mUri = uri;
            mCallGetCommand = getCommand;
            mCallSetCommand = setCommand;
            mProviderHolder = providerHolder;
        }

        public boolean putStringForUser(ContentResolver cr, String name, String value,
                String tag, boolean makeDefault, final int userHandle) {
            try {
                Bundle arg = new Bundle();
                arg.putString(Settings.NameValueTable.VALUE, value);
                arg.putInt(CALL_METHOD_USER_KEY, userHandle);
                if (tag != null) {
                    arg.putString(CALL_METHOD_TAG_KEY, tag);
                }
                if (makeDefault) {
                    arg.putBoolean(CALL_METHOD_MAKE_DEFAULT_KEY, true);
                }
                IContentProvider cp = mProviderHolder.getProvider(cr);
                cp.call(cr.getPackageName(), mCallSetCommand, name, arg);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't set key " + name + " in " + mUri, e);
                return false;
            }
            return true;
        }

        public String getStringForUser(ContentResolver cr, String name, final int userHandle) {
            final boolean isSelf = (userHandle == UserHandle.myUserId());
            int currentGeneration = -1;
            if (isSelf) {
                synchronized (NameValueCache.this) {
                    if (mGenerationTracker != null) {
                        if (mGenerationTracker.isGenerationChanged()) {
                            if (DEBUG) {
                                Log.i(TAG, "Generation changed for type:"
                                        + mUri.getPath() + " in package:"
                                        + cr.getPackageName() +" and user:" + userHandle);
                            }
                            mValues.clear();
                        } else if (mValues.containsKey(name)) {
                            return mValues.get(name);
                        }
                        if (mGenerationTracker != null) {
                            currentGeneration = mGenerationTracker.getCurrentGeneration();
                        }
                    }
                }
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "get setting for user " + userHandle
                        + " by user " + UserHandle.myUserId() + " so skipping cache");
            }

            IContentProvider cp = mProviderHolder.getProvider(cr);

            // Try the fast path first, not using query().  If this
            // fails (alternate Settings provider that doesn't support
            // this interface?) then we fall back to the query/table
            // interface.
            if (mCallGetCommand != null) {
                try {
                    Bundle args = null;
                    if (!isSelf) {
                        args = new Bundle();
                        args.putInt(CALL_METHOD_USER_KEY, userHandle);
                    }
                    boolean needsGenerationTracker = false;
                    synchronized (NameValueCache.this) {
                        if (isSelf && mGenerationTracker == null) {
                            needsGenerationTracker = true;
                            if (args == null) {
                                args = new Bundle();
                            }
                            args.putString(CALL_METHOD_TRACK_GENERATION_KEY, null);
                            if (DEBUG) {
                                Log.i(TAG, "Requested generation tracker for type: "+ mUri.getPath()
                                        + " in package:" + cr.getPackageName() +" and user:"
                                        + userHandle);
                            }
                        }
                    }
                    Bundle b;
                    // If we're in system server and in a binder transaction we need to clear the
                    // calling uid. This works around code in system server that did not call
                    // clearCallingIdentity, previously this wasn't needed because reading settings
                    // did not do permission checking but thats no longer the case.
                    // Long term this should be removed and callers should properly call
                    // clearCallingIdentity or use a ContentResolver from the caller as needed.
                    if (Settings.isInSystemServer() && Binder.getCallingUid() != Process.myUid()) {
                        final long token = Binder.clearCallingIdentity();
                        try {
                            b = cp.call(cr.getPackageName(), mCallGetCommand, name, args);
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }
                    } else {
                        b = cp.call(cr.getPackageName(), mCallGetCommand, name, args);
                    }
                    if (b != null) {
                        String value = b.getString(Settings.NameValueTable.VALUE);
                        // Don't update our cache for reads of other users' data
                        if (isSelf) {
                            synchronized (NameValueCache.this) {
                                if (needsGenerationTracker) {
                                    MemoryIntArray array = b.getParcelable(
                                            CALL_METHOD_TRACK_GENERATION_KEY);
                                    final int index = b.getInt(
                                            CALL_METHOD_GENERATION_INDEX_KEY, -1);
                                    if (array != null && index >= 0) {
                                        final int generation = b.getInt(
                                                CALL_METHOD_GENERATION_KEY, 0);
                                        if (DEBUG) {
                                            Log.i(TAG, "Received generation tracker for type:"
                                                    + mUri.getPath() + " in package:"
                                                    + cr.getPackageName() + " and user:"
                                                    + userHandle + " with index:" + index);
                                        }
                                        if (mGenerationTracker != null) {
                                            mGenerationTracker.destroy();
                                        }
                                        mGenerationTracker = new GenerationTracker(array, index,
                                                generation, () -> {
                                            synchronized (NameValueCache.this) {
                                                Log.e(TAG, "Error accessing generation"
                                                        + " tracker - removing");
                                                if (mGenerationTracker != null) {
                                                    GenerationTracker generationTracker =
                                                            mGenerationTracker;
                                                    mGenerationTracker = null;
                                                    generationTracker.destroy();
                                                    mValues.clear();
                                                }
                                            }
                                        });
                                    }
                                }
                                if (mGenerationTracker != null && currentGeneration ==
                                        mGenerationTracker.getCurrentGeneration()) {
                                    mValues.put(name, value);
                                }
                            }
                        } else {
                            if (LOCAL_LOGV) Log.i(TAG, "call-query of user " + userHandle
                                    + " by " + UserHandle.myUserId()
                                    + " so not updating cache");
                        }
                        return value;
                    }
                    // If the response Bundle is null, we fall through
                    // to the query interface below.
                } catch (RemoteException e) {
                    // Not supported by the remote side?  Fall through
                    // to query().
                }
            }

            Cursor c = null;
            try {
                Bundle queryArgs = ContentResolver.createSqlQueryBundle(
                        NAME_EQ_PLACEHOLDER, new String[]{name}, null);
                // Same workaround as above.
                if (Settings.isInSystemServer() && Binder.getCallingUid() != Process.myUid()) {
                    final long token = Binder.clearCallingIdentity();
                    try {
                        c = cp.query(cr.getPackageName(), mUri, SELECT_VALUE_PROJECTION, queryArgs,
                                null);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                } else {
                    c = cp.query(cr.getPackageName(), mUri, SELECT_VALUE_PROJECTION, queryArgs,
                            null);
                }
                if (c == null) {
                    Log.w(TAG, "Can't get key " + name + " from " + mUri);
                    return null;
                }

                String value = c.moveToNext() ? c.getString(0) : null;
                synchronized (NameValueCache.this) {
                    if(mGenerationTracker != null &&
                            currentGeneration == mGenerationTracker.getCurrentGeneration()) {
                        mValues.put(name, value);
                    }
                }
                if (LOCAL_LOGV) {
                    Log.v(TAG, "cache miss [" + mUri.getLastPathSegment() + "]: " +
                            name + " = " + (value == null ? "(null)" : value));
                }
                return value;
            } catch (RemoteException e) {
                Log.w(TAG, "Can't get key " + name + " from " + mUri, e);
                return null;  // Return null, but don't cache it.
            } finally {
                if (c != null) c.close();
            }
        }

        public void clearGenerationTrackerForTest() {
            synchronized (NameValueCache.this) {
                if (mGenerationTracker != null) {
                    mGenerationTracker.destroy();
                }
                mValues.clear();
                mGenerationTracker = null;
            }
        }
    }

    /**
     * Checks if the specified context can draw on top of other apps. As of API
     * level 23, an app cannot draw on top of other apps unless it declares the
     * {@link android.Manifest.permission#SYSTEM_ALERT_WINDOW} permission in its
     * manifest, <em>and</em> the user specifically grants the app this
     * capability. To prompt the user to grant this approval, the app must send an
     * intent with the action
     * {@link android.provider.Settings#ACTION_MANAGE_OVERLAY_PERMISSION}, which
     * causes the system to display a permission management screen.
     *
     * @param context App context.
     * @return true if the specified context can draw on top of other apps, false otherwise
     */
    public static boolean canDrawOverlays(Context context) {
        return Settings.isCallingPackageAllowedToDrawOverlays(context, Process.myUid(),
                context.getOpPackageName(), false);
    }

    /**
     * System settings, containing miscellaneous system preferences.  This
     * table holds simple name/value pairs.  There are convenience
     * functions for accessing individual settings entries.
     */
    public static final class System extends NameValueTable {
        // NOTE: If you add new settings here, be sure to add them to
        // com.android.providers.settings.SettingsProtoDumpUtil#dumpProtoSystemSettingsLocked.

        private static final float DEFAULT_FONT_SCALE = 1.0f;

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/system");

        private static final ContentProviderHolder sProviderHolder =
                new ContentProviderHolder(CONTENT_URI);

        private static final NameValueCache sNameValueCache = new NameValueCache(
                CONTENT_URI,
                CALL_METHOD_GET_SYSTEM,
                CALL_METHOD_PUT_SYSTEM,
                sProviderHolder);

        private static final HashSet<String> MOVED_TO_SECURE;
        static {
            MOVED_TO_SECURE = new HashSet<>(30);
            MOVED_TO_SECURE.add(Secure.ANDROID_ID);
            MOVED_TO_SECURE.add(Secure.HTTP_PROXY);
            MOVED_TO_SECURE.add(Secure.LOCATION_PROVIDERS_ALLOWED);
            MOVED_TO_SECURE.add(Secure.LOCK_BIOMETRIC_WEAK_FLAGS);
            MOVED_TO_SECURE.add(Secure.LOCK_PATTERN_ENABLED);
            MOVED_TO_SECURE.add(Secure.LOCK_PATTERN_VISIBLE);
            MOVED_TO_SECURE.add(Secure.LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED);
            MOVED_TO_SECURE.add(Secure.LOGGING_ID);
            MOVED_TO_SECURE.add(Secure.PARENTAL_CONTROL_ENABLED);
            MOVED_TO_SECURE.add(Secure.PARENTAL_CONTROL_LAST_UPDATE);
            MOVED_TO_SECURE.add(Secure.PARENTAL_CONTROL_REDIRECT_URL);
            MOVED_TO_SECURE.add(Secure.SETTINGS_CLASSNAME);
            MOVED_TO_SECURE.add(Secure.USE_GOOGLE_MAIL);
            MOVED_TO_SECURE.add(Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON);
            MOVED_TO_SECURE.add(Secure.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY);
            MOVED_TO_SECURE.add(Secure.WIFI_NUM_OPEN_NETWORKS_KEPT);
            MOVED_TO_SECURE.add(Secure.WIFI_ON);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_AP_COUNT);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_MAX_AP_CHECKS);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_ON);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_PING_COUNT);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_PING_DELAY_MS);
            MOVED_TO_SECURE.add(Secure.WIFI_WATCHDOG_PING_TIMEOUT_MS);

            // At one time in System, then Global, but now back in Secure
            MOVED_TO_SECURE.add(Secure.INSTALL_NON_MARKET_APPS);
        }

        private static final HashSet<String> MOVED_TO_GLOBAL;
        private static final HashSet<String> MOVED_TO_SECURE_THEN_GLOBAL;
        static {
            MOVED_TO_GLOBAL = new HashSet<>();
            MOVED_TO_SECURE_THEN_GLOBAL = new HashSet<>();

            // these were originally in system but migrated to secure in the past,
            // so are duplicated in the Secure.* namespace
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.ADB_ENABLED);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.BLUETOOTH_ON);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.DATA_ROAMING);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.DEVICE_PROVISIONED);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.USB_MASS_STORAGE_ENABLED);
            MOVED_TO_SECURE_THEN_GLOBAL.add(Global.HTTP_PROXY);

            // these are moving directly from system to global
            MOVED_TO_GLOBAL.add(Settings.Global.AIRPLANE_MODE_ON);
            MOVED_TO_GLOBAL.add(Settings.Global.AIRPLANE_MODE_RADIOS);
            MOVED_TO_GLOBAL.add(Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
            MOVED_TO_GLOBAL.add(Settings.Global.AUTO_TIME);
            MOVED_TO_GLOBAL.add(Settings.Global.AUTO_TIME_ZONE);
            MOVED_TO_GLOBAL.add(Settings.Global.CAR_DOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.CAR_UNDOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.DESK_DOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.DESK_UNDOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.DOCK_SOUNDS_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.LOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.UNLOCK_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.LOW_BATTERY_SOUND);
            MOVED_TO_GLOBAL.add(Settings.Global.POWER_SOUNDS_ENABLED);
            MOVED_TO_GLOBAL.add(Settings.Global.STAY_ON_WHILE_PLUGGED_IN);
            MOVED_TO_GLOBAL.add(Settings.Global.WIFI_SLEEP_POLICY);
            MOVED_TO_GLOBAL.add(Settings.Global.MODE_RINGER);
            MOVED_TO_GLOBAL.add(Settings.Global.WINDOW_ANIMATION_SCALE);
            MOVED_TO_GLOBAL.add(Settings.Global.TRANSITION_ANIMATION_SCALE);
            MOVED_TO_GLOBAL.add(Settings.Global.ANIMATOR_DURATION_SCALE);
            MOVED_TO_GLOBAL.add(Settings.Global.FANCY_IME_ANIMATIONS);
            MOVED_TO_GLOBAL.add(Settings.Global.COMPATIBILITY_MODE);
            MOVED_TO_GLOBAL.add(Settings.Global.EMERGENCY_TONE);
            MOVED_TO_GLOBAL.add(Settings.Global.CALL_AUTO_RETRY);
            MOVED_TO_GLOBAL.add(Settings.Global.DEBUG_APP);
            MOVED_TO_GLOBAL.add(Settings.Global.WAIT_FOR_DEBUGGER);
            MOVED_TO_GLOBAL.add(Settings.Global.ALWAYS_FINISH_ACTIVITIES);
            MOVED_TO_GLOBAL.add(Settings.Global.TZINFO_UPDATE_CONTENT_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.TZINFO_UPDATE_METADATA_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SELINUX_UPDATE_CONTENT_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SELINUX_UPDATE_METADATA_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SMS_SHORT_CODES_UPDATE_CONTENT_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.SMS_SHORT_CODES_UPDATE_METADATA_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.CERT_PIN_UPDATE_CONTENT_URL);
            MOVED_TO_GLOBAL.add(Settings.Global.CERT_PIN_UPDATE_METADATA_URL);
        }

        /** @hide */
        public static void getMovedToGlobalSettings(Set<String> outKeySet) {
            outKeySet.addAll(MOVED_TO_GLOBAL);
            outKeySet.addAll(MOVED_TO_SECURE_THEN_GLOBAL);
        }

        /** @hide */
        public static void getMovedToSecureSettings(Set<String> outKeySet) {
            outKeySet.addAll(MOVED_TO_SECURE);
        }

        /** @hide */
        public static void getNonLegacyMovedKeys(HashSet<String> outKeySet) {
            outKeySet.addAll(MOVED_TO_GLOBAL);
        }

        /** @hide */
        public static void clearProviderForTest() {
            sProviderHolder.clearProviderForTest();
            sNameValueCache.clearGenerationTrackerForTest();
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, resolver.getUserId());
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userHandle) {
            android.util.SeempLog.record(android.util.SeempLog.getSeempGetApiIdFromValue(name));
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Secure, returning read-only value.");
                return Secure.getStringForUser(resolver, name, userHandle);
            }
            if (MOVED_TO_GLOBAL.contains(name) || MOVED_TO_SECURE_THEN_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Global, returning read-only value.");
                return Global.getStringForUser(resolver, name, userHandle);
            }
            return sNameValueCache.getStringForUser(resolver, name, userHandle);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, resolver.getUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
                int userHandle) {
            android.util.SeempLog.record(android.util.SeempLog.getSeempPutApiIdFromValue(name));
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Secure, value is unchanged.");
                return false;
            }
            if (MOVED_TO_GLOBAL.contains(name) || MOVED_TO_SECURE_THEN_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Global, value is unchanged.");
                return false;
            }
            return sNameValueCache.putStringForUser(resolver, name, value, null, false, userHandle);
        }

        /**
         * Construct the content URI for a particular name/value pair,
         * useful for monitoring changes with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI, or null if not present
         */
        public static Uri getUriFor(String name) {
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                    + " to android.provider.Settings.Secure, returning Secure URI.");
                return Secure.getUriFor(Secure.CONTENT_URI, name);
            }
            if (MOVED_TO_GLOBAL.contains(name) || MOVED_TO_SECURE_THEN_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from android.provider.Settings.System"
                        + " to android.provider.Settings.Global, returning read-only global URI.");
                return Global.getUriFor(Global.CONTENT_URI, name);
            }
            return getUriFor(CONTENT_URI, name);
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, cr.getUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userHandle) {
            String v = getStringForUser(cr, name, userHandle);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getIntForUser(cr, name, cr.getUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            String v = getStringForUser(cr, name, userHandle);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * @hide
         * Convenience function for retrieving a single system settings value
         * as a boolean.  Note that internally setting values are always
         * stored as strings; this function converts the string to a boolean
         * for you. It will only return true if the stored value is "1"
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static boolean getBoolean(ContentResolver cr, String name, boolean def) {
            String v = getString(cr, name);
            try {
                if(v != null)
                    return "1".equals(v);
                else
                    return def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, cr.getUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userHandle) {
            return putStringForUser(cr, name, Integer.toString(value), userHandle);
        }

        /**
         * @hide
         * Convenience function for updating a single settings value as a
         * boolean. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string (1 or 0) before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putBoolean(ContentResolver cr, String name, boolean value) {
            return putString(cr, name, value ? "1" : "0");
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, cr.getUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userHandle) {
            String valString = getStringForUser(cr, name, userHandle);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getLongForUser(cr, name, cr.getUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            String valString = getStringForUser(cr, name, userHandle);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, cr.getUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userHandle) {
            return putStringForUser(cr, name, Long.toString(value), userHandle);
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, cr.getUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userHandle) {
            String v = getStringForUser(cr, name, userHandle);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws SettingNotFoundException {
            return getFloatForUser(cr, name, cr.getUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userHandle)
                throws SettingNotFoundException {
            String v = getStringForUser(cr, name, userHandle);
            if (v == null) {
                throw new SettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, cr.getUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userHandle) {
            return putStringForUser(cr, name, Float.toString(value), userHandle);
        }

        /**
         * Convenience function to read all of the current
         * configuration-related settings into a
         * {@link Configuration} object.
         *
         * @param cr The ContentResolver to access.
         * @param outConfig Where to place the configuration settings.
         */
        public static void getConfiguration(ContentResolver cr, Configuration outConfig) {
            adjustConfigurationForUser(cr, outConfig, cr.getUserId(),
                    false /* updateSettingsIfEmpty */);
        }

        /** @hide */
        public static void adjustConfigurationForUser(ContentResolver cr, Configuration outConfig,
                int userHandle, boolean updateSettingsIfEmpty) {
            outConfig.fontScale = Settings.System.getFloatForUser(
                    cr, FONT_SCALE, DEFAULT_FONT_SCALE, userHandle);
            if (outConfig.fontScale < 0) {
                outConfig.fontScale = DEFAULT_FONT_SCALE;
            }

            final String localeValue =
                    Settings.System.getStringForUser(cr, SYSTEM_LOCALES, userHandle);
            if (localeValue != null) {
                outConfig.setLocales(LocaleList.forLanguageTags(localeValue));
            } else {
                // Do not update configuration with emtpy settings since we need to take over the
                // locale list of previous user if the settings value is empty. This happens when a
                // new user is created.

                if (updateSettingsIfEmpty) {
                    // Make current configuration persistent. This is necessary the first time a
                    // user log in. At the first login, the configuration settings are empty, so we
                    // need to store the adjusted configuration as the initial settings.
                    Settings.System.putStringForUser(
                            cr, SYSTEM_LOCALES, outConfig.getLocales().toLanguageTags(),
                            userHandle);
                }
            }
        }

        /**
         * @hide Erase the fields in the Configuration that should be applied
         * by the settings.
         */
        public static void clearConfiguration(Configuration inoutConfig) {
            inoutConfig.fontScale = 0;
            if (!inoutConfig.userSetLocale && !inoutConfig.getLocales().isEmpty()) {
                inoutConfig.clearLocales();
            }
        }

        /**
         * Convenience function to write a batch of configuration-related
         * settings from a {@link Configuration} object.
         *
         * @param cr The ContentResolver to access.
         * @param config The settings to write.
         * @return true if the values were set, false on database errors
         */
        public static boolean putConfiguration(ContentResolver cr, Configuration config) {
            return putConfigurationForUser(cr, config, cr.getUserId());
        }

        /** @hide */
        public static boolean putConfigurationForUser(ContentResolver cr, Configuration config,
                int userHandle) {
            return Settings.System.putFloatForUser(cr, FONT_SCALE, config.fontScale, userHandle) &&
                    Settings.System.putStringForUser(
                            cr, SYSTEM_LOCALES, config.getLocales().toLanguageTags(), userHandle);
        }

        /** @hide */
        public static boolean hasInterestingConfigurationChanges(int changes) {
            return (changes & ActivityInfo.CONFIG_FONT_SCALE) != 0 ||
                    (changes & ActivityInfo.CONFIG_LOCALE) != 0;
        }

        /** @deprecated - Do not use */
        @Deprecated
        public static boolean getShowGTalkServiceStatus(ContentResolver cr) {
            return getShowGTalkServiceStatusForUser(cr, cr.getUserId());
        }

        /**
         * @hide
         * @deprecated - Do not use
         */
        @Deprecated
        public static boolean getShowGTalkServiceStatusForUser(ContentResolver cr,
                int userHandle) {
            return getIntForUser(cr, SHOW_GTALK_SERVICE_STATUS, 0, userHandle) != 0;
        }

        /** @deprecated - Do not use */
        @Deprecated
        public static void setShowGTalkServiceStatus(ContentResolver cr, boolean flag) {
            setShowGTalkServiceStatusForUser(cr, flag, cr.getUserId());
        }

        /**
         * @hide
         * @deprecated - Do not use
         */
        @Deprecated
        public static void setShowGTalkServiceStatusForUser(ContentResolver cr, boolean flag,
                int userHandle) {
            putIntForUser(cr, SHOW_GTALK_SERVICE_STATUS, flag ? 1 : 0, userHandle);
        }

        /**
         * @deprecated Use {@link android.provider.Settings.Global#STAY_ON_WHILE_PLUGGED_IN} instead
         */
        @Deprecated
        public static final String STAY_ON_WHILE_PLUGGED_IN = Global.STAY_ON_WHILE_PLUGGED_IN;

        private static final Validator STAY_ON_WHILE_PLUGGED_IN_VALIDATOR = new Validator() {
            @Override
            public boolean validate(String value) {
                try {
                    int val = Integer.parseInt(value);
                    return (val == 0)
                            || (val == BatteryManager.BATTERY_PLUGGED_AC)
                            || (val == BatteryManager.BATTERY_PLUGGED_USB)
                            || (val == BatteryManager.BATTERY_PLUGGED_WIRELESS)
                            || (val == (BatteryManager.BATTERY_PLUGGED_AC
                                    | BatteryManager.BATTERY_PLUGGED_USB))
                            || (val == (BatteryManager.BATTERY_PLUGGED_AC
                                    | BatteryManager.BATTERY_PLUGGED_WIRELESS))
                            || (val == (BatteryManager.BATTERY_PLUGGED_USB
                                    | BatteryManager.BATTERY_PLUGGED_WIRELESS))
                            || (val == (BatteryManager.BATTERY_PLUGGED_AC
                                    | BatteryManager.BATTERY_PLUGGED_USB
                                    | BatteryManager.BATTERY_PLUGGED_WIRELESS));
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        };

        /**
         * What happens when the user presses the end call button if they're not
         * on a call.<br/>
         * <b>Values:</b><br/>
         * 0 - The end button does nothing.<br/>
         * 1 - The end button goes to the home screen.<br/>
         * 2 - The end button puts the device to sleep and locks the keyguard.<br/>
         * 3 - The end button goes to the home screen.  If the user is already on the
         * home screen, it puts the device to sleep.
         */
        public static final String END_BUTTON_BEHAVIOR = "end_button_behavior";

        private static final Validator END_BUTTON_BEHAVIOR_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(0, 3);

        /**
         * END_BUTTON_BEHAVIOR value for "go home".
         * @hide
         */
        public static final int END_BUTTON_BEHAVIOR_HOME = 0x1;

        /**
         * END_BUTTON_BEHAVIOR value for "go to sleep".
         * @hide
         */
        public static final int END_BUTTON_BEHAVIOR_SLEEP = 0x2;

        /**
         * END_BUTTON_BEHAVIOR default value.
         * @hide
         */
        public static final int END_BUTTON_BEHAVIOR_DEFAULT = END_BUTTON_BEHAVIOR_SLEEP;

        /**
         * Is advanced settings mode turned on. 0 == no, 1 == yes
         * @hide
         */
        public static final String ADVANCED_SETTINGS = "advanced_settings";

        private static final Validator ADVANCED_SETTINGS_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * ADVANCED_SETTINGS default value.
         * @hide
         */
        public static final int ADVANCED_SETTINGS_DEFAULT = 0;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AIRPLANE_MODE_ON} instead
         */
        @Deprecated
        public static final String AIRPLANE_MODE_ON = Global.AIRPLANE_MODE_ON;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_BLUETOOTH} instead
         */
        @Deprecated
        public static final String RADIO_BLUETOOTH = Global.RADIO_BLUETOOTH;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_WIFI} instead
         */
        @Deprecated
        public static final String RADIO_WIFI = Global.RADIO_WIFI;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_WIMAX} instead
         * {@hide}
         */
        @Deprecated
        public static final String RADIO_WIMAX = Global.RADIO_WIMAX;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_CELL} instead
         */
        @Deprecated
        public static final String RADIO_CELL = Global.RADIO_CELL;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#RADIO_NFC} instead
         */
        @Deprecated
        public static final String RADIO_NFC = Global.RADIO_NFC;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AIRPLANE_MODE_RADIOS} instead
         */
        @Deprecated
        public static final String AIRPLANE_MODE_RADIOS = Global.AIRPLANE_MODE_RADIOS;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AIRPLANE_MODE_TOGGLEABLE_RADIOS} instead
         *
         * {@hide}
         */
        @Deprecated
        public static final String AIRPLANE_MODE_TOGGLEABLE_RADIOS =
                Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_SLEEP_POLICY} instead
         */
        @Deprecated
        public static final String WIFI_SLEEP_POLICY = Global.WIFI_SLEEP_POLICY;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_SLEEP_POLICY_DEFAULT} instead
         */
        @Deprecated
        public static final int WIFI_SLEEP_POLICY_DEFAULT = Global.WIFI_SLEEP_POLICY_DEFAULT;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED} instead
         */
        @Deprecated
        public static final int WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED =
                Global.WIFI_SLEEP_POLICY_NEVER_WHILE_PLUGGED;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#WIFI_SLEEP_POLICY_NEVER} instead
         */
        @Deprecated
        public static final int WIFI_SLEEP_POLICY_NEVER = Global.WIFI_SLEEP_POLICY_NEVER;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#MODE_RINGER} instead
         */
        @Deprecated
        public static final String MODE_RINGER = Global.MODE_RINGER;

        /**
         * Whether to use static IP and other static network attributes.
         * <p>
         * Set to 1 for true and 0 for false.
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_USE_STATIC_IP = "wifi_use_static_ip";

        private static final Validator WIFI_USE_STATIC_IP_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * The static IP address.
         * <p>
         * Example: "192.168.1.51"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_IP = "wifi_static_ip";

        private static final Validator WIFI_STATIC_IP_VALIDATOR = LENIENT_IP_ADDRESS_VALIDATOR;

        /**
         * If using static IP, the gateway's IP address.
         * <p>
         * Example: "192.168.1.1"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_GATEWAY = "wifi_static_gateway";

        private static final Validator WIFI_STATIC_GATEWAY_VALIDATOR = LENIENT_IP_ADDRESS_VALIDATOR;

        /**
         * If using static IP, the net mask.
         * <p>
         * Example: "255.255.255.0"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_NETMASK = "wifi_static_netmask";

        private static final Validator WIFI_STATIC_NETMASK_VALIDATOR = LENIENT_IP_ADDRESS_VALIDATOR;

        /**
         * If using static IP, the primary DNS's IP address.
         * <p>
         * Example: "192.168.1.1"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_DNS1 = "wifi_static_dns1";

        private static final Validator WIFI_STATIC_DNS1_VALIDATOR = LENIENT_IP_ADDRESS_VALIDATOR;

        /**
         * If using static IP, the secondary DNS's IP address.
         * <p>
         * Example: "192.168.1.2"
         *
         * @deprecated Use {@link WifiManager} instead
         */
        @Deprecated
        public static final String WIFI_STATIC_DNS2 = "wifi_static_dns2";

        private static final Validator WIFI_STATIC_DNS2_VALIDATOR = LENIENT_IP_ADDRESS_VALIDATOR;

        /**
         * Determines whether remote devices may discover and/or connect to
         * this device.
         * <P>Type: INT</P>
         * 2 -- discoverable and connectable
         * 1 -- connectable but not discoverable
         * 0 -- neither connectable nor discoverable
         */
        public static final String BLUETOOTH_DISCOVERABILITY =
            "bluetooth_discoverability";

        private static final Validator BLUETOOTH_DISCOVERABILITY_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(0, 2);

        /**
         * Bluetooth discoverability timeout.  If this value is nonzero, then
         * Bluetooth becomes discoverable for a certain number of seconds,
         * after which is becomes simply connectable.  The value is in seconds.
         */
        public static final String BLUETOOTH_DISCOVERABILITY_TIMEOUT =
            "bluetooth_discoverability_timeout";

        private static final Validator BLUETOOTH_DISCOVERABILITY_TIMEOUT_VALIDATOR =
                NON_NEGATIVE_INTEGER_VALIDATOR;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#LOCK_PATTERN_ENABLED}
         * instead
         */
        @Deprecated
        public static final String LOCK_PATTERN_ENABLED = Secure.LOCK_PATTERN_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#LOCK_PATTERN_VISIBLE}
         * instead
         */
        @Deprecated
        public static final String LOCK_PATTERN_VISIBLE = "lock_pattern_visible_pattern";

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Secure#LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED}
         * instead
         */
        @Deprecated
        public static final String LOCK_PATTERN_TACTILE_FEEDBACK_ENABLED =
            "lock_pattern_tactile_feedback_enabled";

        /**
         * Whether to scramble a pin unlock layout
         * @hide
         */
        public static final String LOCKSCREEN_PIN_SCRAMBLE_LAYOUT =
                "lockscreen_scramble_pin_layout";

        /**
         * Whether to use the custom quick unlock screen control
         * @hide
         */
        public static final String LOCKSCREEN_QUICK_UNLOCK_CONTROL =
                "lockscreen_quick_unlock_control";

        /**
         * A formatted string of the next alarm that is set, or the empty string
         * if there is no alarm set.
         *
         * @deprecated Use {@link android.app.AlarmManager#getNextAlarmClock()}.
         */
        @Deprecated
        public static final String NEXT_ALARM_FORMATTED = "next_alarm_formatted";

        private static final Validator NEXT_ALARM_FORMATTED_VALIDATOR = new Validator() {
            private static final int MAX_LENGTH = 1000;

            @Override
            public boolean validate(String value) {
                // TODO: No idea what the correct format is.
                return value == null || value.length() < MAX_LENGTH;
            }
        };

        /**
         * Scaling factor for fonts, float.
         */
        public static final String FONT_SCALE = "font_scale";

        private static final Validator FONT_SCALE_VALIDATOR = new Validator() {
            @Override
            public boolean validate(@Nullable String value) {
                try {
                    return Float.parseFloat(value) >= 0;
                } catch (NumberFormatException | NullPointerException e) {
                    return false;
                }
            }
        };

        /**
         * The serialized system locale value.
         *
         * Do not use this value directory.
         * To get system locale, use {@link LocaleList#getDefault} instead.
         * To update system locale, use {@link com.android.internal.app.LocalePicker#updateLocales}
         * instead.
         * @hide
         */
        public static final String SYSTEM_LOCALES = "system_locales";


        /**
         * Name of an application package to be debugged.
         *
         * @deprecated Use {@link Global#DEBUG_APP} instead
         */
        @Deprecated
        public static final String DEBUG_APP = Global.DEBUG_APP;

        /**
         * If 1, when launching DEBUG_APP it will wait for the debugger before
         * starting user code.  If 0, it will run normally.
         *
         * @deprecated Use {@link Global#WAIT_FOR_DEBUGGER} instead
         */
        @Deprecated
        public static final String WAIT_FOR_DEBUGGER = Global.WAIT_FOR_DEBUGGER;

        /**
         * Whether or not to dim the screen. 0=no  1=yes
         * @deprecated This setting is no longer used.
         */
        @Deprecated
        public static final String DIM_SCREEN = "dim_screen";

        private static final Validator DIM_SCREEN_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * The display color mode.
         * @hide
         */
        public static final String DISPLAY_COLOR_MODE = "display_color_mode";

        private static final Validator DISPLAY_COLOR_MODE_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(
                        ColorDisplayController.COLOR_MODE_NATURAL,
                        ColorDisplayController.COLOR_MODE_AUTOMATIC);

        /**
         * The amount of time in milliseconds before the device goes to sleep or begins
         * to dream after a period of inactivity.  This value is also known as the
         * user activity timeout period since the screen isn't necessarily turned off
         * when it expires.
         *
         * <p>
         * This value is bounded by maximum timeout set by
         * {@link android.app.admin.DevicePolicyManager#setMaximumTimeToLock(ComponentName, long)}.
         */
        public static final String SCREEN_OFF_TIMEOUT = "screen_off_timeout";

        private static final Validator SCREEN_OFF_TIMEOUT_VALIDATOR =
                NON_NEGATIVE_INTEGER_VALIDATOR;

        /**
         * The screen backlight brightness between 0 and 255.
         */
        public static final String SCREEN_BRIGHTNESS = "screen_brightness";

        /**
         * The screen backlight brightness between 0 and 255.
         * @hide
         */
        public static final String SCREEN_BRIGHTNESS_FOR_VR = "screen_brightness_for_vr";

        private static final Validator SCREEN_BRIGHTNESS_FOR_VR_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(0, 255);

        /**
         * Control whether to enable automatic brightness mode.
         */
        public static final String SCREEN_BRIGHTNESS_MODE = "screen_brightness_mode";

        private static final Validator SCREEN_BRIGHTNESS_MODE_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Adjustment to auto-brightness to make it generally more (>0.0 <1.0)
         * or less (<0.0 >-1.0) bright.
         * @hide
         */
        public static final String SCREEN_AUTO_BRIGHTNESS_ADJ = "screen_auto_brightness_adj";

        private static final Validator SCREEN_AUTO_BRIGHTNESS_ADJ_VALIDATOR =
                new SettingsValidators.InclusiveFloatRangeValidator(-1, 1);

        /**
         * SCREEN_BRIGHTNESS_MODE value for manual mode.
         */
        public static final int SCREEN_BRIGHTNESS_MODE_MANUAL = 0;

        /**
         * SCREEN_BRIGHTNESS_MODE value for automatic mode.
         */
        public static final int SCREEN_BRIGHTNESS_MODE_AUTOMATIC = 1;

        /**
         * Control whether the process CPU usage meter should be shown.
         *
         * @deprecated This functionality is no longer available as of
         * {@link android.os.Build.VERSION_CODES#N_MR1}.
         */
        @Deprecated
        public static final String SHOW_PROCESSES = Global.SHOW_PROCESSES;

        /**
         * If 1, the activity manager will aggressively finish activities and
         * processes as soon as they are no longer needed.  If 0, the normal
         * extended lifetime is used.
         *
         * @deprecated Use {@link Global#ALWAYS_FINISH_ACTIVITIES} instead
         */
        @Deprecated
        public static final String ALWAYS_FINISH_ACTIVITIES = Global.ALWAYS_FINISH_ACTIVITIES;

        /**
         * Determines which streams are affected by ringer and zen mode changes. The
         * stream type's bit should be set to 1 if it should be muted when going
         * into an inaudible ringer mode.
         */
        public static final String MODE_RINGER_STREAMS_AFFECTED = "mode_ringer_streams_affected";

        private static final Validator MODE_RINGER_STREAMS_AFFECTED_VALIDATOR =
                NON_NEGATIVE_INTEGER_VALIDATOR;

        /**
          * Determines which streams are affected by mute. The
          * stream type's bit should be set to 1 if it should be muted when a mute request
          * is received.
          */
        public static final String MUTE_STREAMS_AFFECTED = "mute_streams_affected";

        private static final Validator MUTE_STREAMS_AFFECTED_VALIDATOR =
                NON_NEGATIVE_INTEGER_VALIDATOR;

        /**
         * Whether vibrate is on for different events. This is used internally,
         * changing this value will not change the vibrate. See AudioManager.
         */
        public static final String VIBRATE_ON = "vibrate_on";

        private static final Validator VIBRATE_ON_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * If 1, redirects the system vibrator to all currently attached input devices
         * that support vibration.  If there are no such input devices, then the system
         * vibrator is used instead.
         * If 0, does not register the system vibrator.
         *
         * This setting is mainly intended to provide a compatibility mechanism for
         * applications that only know about the system vibrator and do not use the
         * input device vibrator API.
         *
         * @hide
         */
        public static final String VIBRATE_INPUT_DEVICES = "vibrate_input_devices";

        private static final Validator VIBRATE_INPUT_DEVICES_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * The intensity of notification vibrations, if configurable.
         *
         * Not all devices are capable of changing their vibration intensity; on these devices
         * there will likely be no difference between the various vibration intensities except for
         * intensity 0 (off) and the rest.
         *
         * <b>Values:</b><br/>
         * 0 - Vibration is disabled<br/>
         * 1 - Weak vibrations<br/>
         * 2 - Medium vibrations<br/>
         * 3 - Strong vibrations
         * @hide
         */
        public static final String NOTIFICATION_VIBRATION_INTENSITY =
                "notification_vibration_intensity";

        /**
         * The intensity of haptic feedback vibrations, if configurable.
         *
         * Not all devices are capable of changing their feedback intensity; on these devices
         * there will likely be no difference between the various vibration intensities except for
         * intensity 0 (off) and the rest.
         *
         * <b>Values:</b><br/>
         * 0 - Vibration is disabled<br/>
         * 1 - Weak vibrations<br/>
         * 2 - Medium vibrations<br/>
         * 3 - Strong vibrations
         * @hide
         */
        public static final String HAPTIC_FEEDBACK_INTENSITY =
                "haptic_feedback_intensity";

        private static final Validator VIBRATION_INTENSITY_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(0, 3);

        /**
         * Ringer volume. This is used internally, changing this value will not
         * change the volume. See AudioManager.
         *
         * @removed Not used by anything since API 2.
         */
        public static final String VOLUME_RING = "volume_ring";

        /**
         * System/notifications volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         *
         * @removed Not used by anything since API 2.
         */
        public static final String VOLUME_SYSTEM = "volume_system";

        /**
         * Voice call volume. This is used internally, changing this value will
         * not change the volume. See AudioManager.
         *
         * @removed Not used by anything since API 2.
         */
        public static final String VOLUME_VOICE = "volume_voice";

        /**
         * Music/media/gaming volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         *
         * @removed Not used by anything since API 2.
         */
        public static final String VOLUME_MUSIC = "volume_music";

        /**
         * Alarm volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         *
         * @removed Not used by anything since API 2.
         */
        public static final String VOLUME_ALARM = "volume_alarm";

        /**
         * Notification volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         *
         * @removed Not used by anything since API 2.
         */
        public static final String VOLUME_NOTIFICATION = "volume_notification";

        /**
         * Bluetooth Headset volume. This is used internally, changing this value will
         * not change the volume. See AudioManager.
         *
         * @removed Not used by anything since API 2.
         */
        public static final String VOLUME_BLUETOOTH_SCO = "volume_bluetooth_sco";

        /**
         * @hide
         * Acessibility volume. This is used internally, changing this
         * value will not change the volume.
         */
        public static final String VOLUME_ACCESSIBILITY = "volume_a11y";

        /**
         * Master volume (float in the range 0.0f to 1.0f).
         *
         * @hide
         */
        public static final String VOLUME_MASTER = "volume_master";

        /**
         * Master mono (int 1 = mono, 0 = normal).
         *
         * @hide
         */
        public static final String MASTER_MONO = "master_mono";

        private static final Validator MASTER_MONO_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Whether the notifications should use the ring volume (value of 1) or
         * a separate notification volume (value of 0). In most cases, users
         * will have this enabled so the notification and ringer volumes will be
         * the same. However, power users can disable this and use the separate
         * notification volume control.
         * <p>
         * Note: This is a one-off setting that will be removed in the future
         * when there is profile support. For this reason, it is kept hidden
         * from the public APIs.
         *
         * @hide
         * @deprecated
         */
        @Deprecated
        public static final String NOTIFICATIONS_USE_RING_VOLUME =
            "notifications_use_ring_volume";

        private static final Validator NOTIFICATIONS_USE_RING_VOLUME_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Whether silent mode should allow vibration feedback. This is used
         * internally in AudioService and the Sound settings activity to
         * coordinate decoupling of vibrate and silent modes. This setting
         * will likely be removed in a future release with support for
         * audio/vibe feedback profiles.
         *
         * Not used anymore. On devices with vibrator, the user explicitly selects
         * silent or vibrate mode.
         * Kept for use by legacy database upgrade code in DatabaseHelper.
         * @hide
         */
        public static final String VIBRATE_IN_SILENT = "vibrate_in_silent";

        private static final Validator VIBRATE_IN_SILENT_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * The mapping of stream type (integer) to its setting.
         *
         * @removed  Not used by anything since API 2.
         */
        public static final String[] VOLUME_SETTINGS = {
            VOLUME_VOICE, VOLUME_SYSTEM, VOLUME_RING, VOLUME_MUSIC,
            VOLUME_ALARM, VOLUME_NOTIFICATION, VOLUME_BLUETOOTH_SCO
        };

        /**
         * @hide
         * The mapping of stream type (integer) to its setting.
         * Unlike the VOLUME_SETTINGS array, this one contains as many entries as
         * AudioSystem.NUM_STREAM_TYPES, and has empty strings for stream types whose volumes
         * are never persisted.
         */
        public static final String[] VOLUME_SETTINGS_INT = {
                VOLUME_VOICE, VOLUME_SYSTEM, VOLUME_RING, VOLUME_MUSIC,
                VOLUME_ALARM, VOLUME_NOTIFICATION, VOLUME_BLUETOOTH_SCO,
                "" /*STREAM_SYSTEM_ENFORCED, no setting for this stream*/,
                "" /*STREAM_DTMF, no setting for this stream*/,
                "" /*STREAM_TTS, no setting for this stream*/,
                VOLUME_ACCESSIBILITY
            };

        /**
         * Appended to various volume related settings to record the previous
         * values before they the settings were affected by a silent/vibrate
         * ringer mode change.
         *
         * @removed  Not used by anything since API 2.
         */
        public static final String APPEND_FOR_LAST_AUDIBLE = "_last_audible";

        /**
         * Persistent store for the system-wide default ringtone URI.
         * <p>
         * If you need to play the default ringtone at any given time, it is recommended
         * you give {@link #DEFAULT_RINGTONE_URI} to the media player.  It will resolve
         * to the set default ringtone at the time of playing.
         *
         * @see #DEFAULT_RINGTONE_URI
         */
        public static final String RINGTONE = "ringtone";

        /**
         * Persistent store for the system-wide default ringtone for Slot2 URI.
         *
         * @see #RINGTONE
         * @see #DEFAULT_RINGTONE2_URI
         */
        public static final String RINGTONE2 = "ringtone2";

        private static final Validator RINGTONE_VALIDATOR = URI_VALIDATOR;

        /**
         * A {@link Uri} that will point to the current default ringtone at any
         * given time.
         * <p>
         * If the current default ringtone is in the DRM provider and the caller
         * does not have permission, the exception will be a
         * FileNotFoundException.
         */
        public static final Uri DEFAULT_RINGTONE_URI = getUriFor(RINGTONE);

        /**
         * A {@link Uri} that will point to the current default ringtone for Slot2
         * at any given time.
         *
         * @see #DEFAULT_RINGTONE_URI
         */
        public static final Uri DEFAULT_RINGTONE2_URI = getUriFor(RINGTONE2);

        /** {@hide} */
        public static final String RINGTONE_CACHE = "ringtone_cache";
        /** {@hide} */
        public static final Uri RINGTONE_CACHE_URI = getUriFor(RINGTONE_CACHE);

        /** {@hide} */
        public static final String RINGTONE2_CACHE = "ringtone2_cache";
        /** {@hide} */
        public static final Uri RINGTONE2_CACHE_URI = getUriFor(RINGTONE2_CACHE);

        /**
         * Persistent store for the system-wide default notification sound.
         *
         * @see #RINGTONE
         * @see #DEFAULT_NOTIFICATION_URI
         */
        public static final String NOTIFICATION_SOUND = "notification_sound";

        private static final Validator NOTIFICATION_SOUND_VALIDATOR = URI_VALIDATOR;

        /**
         * A {@link Uri} that will point to the current default notification
         * sound at any given time.
         *
         * @see #DEFAULT_RINGTONE_URI
         */
        public static final Uri DEFAULT_NOTIFICATION_URI = getUriFor(NOTIFICATION_SOUND);

        /** {@hide} */
        public static final String NOTIFICATION_SOUND_CACHE = "notification_sound_cache";
        /** {@hide} */
        public static final Uri NOTIFICATION_SOUND_CACHE_URI = getUriFor(NOTIFICATION_SOUND_CACHE);

        /**
         * Persistent store for the system-wide default alarm alert.
         *
         * @see #RINGTONE
         * @see #DEFAULT_ALARM_ALERT_URI
         */
        public static final String ALARM_ALERT = "alarm_alert";

        private static final Validator ALARM_ALERT_VALIDATOR = URI_VALIDATOR;

        /**
         * A {@link Uri} that will point to the current default alarm alert at
         * any given time.
         *
         * @see #DEFAULT_ALARM_ALERT_URI
         */
        public static final Uri DEFAULT_ALARM_ALERT_URI = getUriFor(ALARM_ALERT);

        /** {@hide} */
        public static final String ALARM_ALERT_CACHE = "alarm_alert_cache";
        /** {@hide} */
        public static final Uri ALARM_ALERT_CACHE_URI = getUriFor(ALARM_ALERT_CACHE);

        /**
         * Persistent store for the system default media button event receiver.
         *
         * @hide
         */
        public static final String MEDIA_BUTTON_RECEIVER = "media_button_receiver";

        private static final Validator MEDIA_BUTTON_RECEIVER_VALIDATOR = COMPONENT_NAME_VALIDATOR;

        /**
         * Setting to enable Auto Replace (AutoText) in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_AUTO_REPLACE = "auto_replace";

        private static final Validator TEXT_AUTO_REPLACE_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Setting to enable Auto Caps in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_AUTO_CAPS = "auto_caps";

        private static final Validator TEXT_AUTO_CAPS_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Setting to enable Auto Punctuate in text editors. 1 = On, 0 = Off. This
         * feature converts two spaces to a "." and space.
         */
        public static final String TEXT_AUTO_PUNCTUATE = "auto_punctuate";

        private static final Validator TEXT_AUTO_PUNCTUATE_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Setting to showing password characters in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_SHOW_PASSWORD = "show_password";

        private static final Validator TEXT_SHOW_PASSWORD_VALIDATOR = BOOLEAN_VALIDATOR;

        public static final String SHOW_GTALK_SERVICE_STATUS =
                "SHOW_GTALK_SERVICE_STATUS";

        private static final Validator SHOW_GTALK_SERVICE_STATUS_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Name of activity to use for wallpaper on the home screen.
         *
         * @deprecated Use {@link WallpaperManager} instead.
         */
        @Deprecated
        public static final String WALLPAPER_ACTIVITY = "wallpaper_activity";

        private static final Validator WALLPAPER_ACTIVITY_VALIDATOR = new Validator() {
            private static final int MAX_LENGTH = 1000;

            @Override
            public boolean validate(String value) {
                if (value != null && value.length() > MAX_LENGTH) {
                    return false;
                }
                return ComponentName.unflattenFromString(value) != null;
            }
        };

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AUTO_TIME}
         * instead
         */
        @Deprecated
        public static final String AUTO_TIME = Global.AUTO_TIME;

        private static final Validator AUTO_TIME_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#AUTO_TIME_ZONE}
         * instead
         */
        @Deprecated
        public static final String AUTO_TIME_ZONE = Global.AUTO_TIME_ZONE;

        private static final Validator AUTO_TIME_ZONE_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Display times as 12 or 24 hours
         *   12
         *   24
         */
        public static final String TIME_12_24 = "time_12_24";

        /** @hide */
        public static final Validator TIME_12_24_VALIDATOR =
                new SettingsValidators.DiscreteValueValidator(new String[] {"12", "24", null});

        /**
         * Date format string
         *   mm/dd/yyyy
         *   dd/mm/yyyy
         *   yyyy/mm/dd
         */
        public static final String DATE_FORMAT = "date_format";

        /** @hide */
        public static final Validator DATE_FORMAT_VALIDATOR = new Validator() {
            @Override
            public boolean validate(@Nullable String value) {
                try {
                    new SimpleDateFormat(value);
                    return true;
                } catch (IllegalArgumentException | NullPointerException e) {
                    return false;
                }
            }
        };

        /**
         * Defines the screen-off animation to display
         * @hide
         */
        public static final String SCREEN_OFF_ANIMATION = "screen_off_animation";

        /**
         * Whether the setup wizard has been run before (on first boot), or if
         * it still needs to be run.
         *
         * nonzero = it has been run in the past
         * 0 = it has not been run in the past
         */
        public static final String SETUP_WIZARD_HAS_RUN = "setup_wizard_has_run";

        /** @hide */
        public static final Validator SETUP_WIZARD_HAS_RUN_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Scaling factor for normal window animations. Setting to 0 will disable window
         * animations.
         *
         * @deprecated Use {@link Global#WINDOW_ANIMATION_SCALE} instead
         */
        @Deprecated
        public static final String WINDOW_ANIMATION_SCALE = Global.WINDOW_ANIMATION_SCALE;

        /**
         * Scaling factor for activity transition animations. Setting to 0 will disable window
         * animations.
         *
         * @deprecated Use {@link Global#TRANSITION_ANIMATION_SCALE} instead
         */
        @Deprecated
        public static final String TRANSITION_ANIMATION_SCALE = Global.TRANSITION_ANIMATION_SCALE;

        /**
         * Scaling factor for Animator-based animations. This affects both the start delay and
         * duration of all such animations. Setting to 0 will cause animations to end immediately.
         * The default value is 1.
         *
         * @deprecated Use {@link Global#ANIMATOR_DURATION_SCALE} instead
         */
        @Deprecated
        public static final String ANIMATOR_DURATION_SCALE = Global.ANIMATOR_DURATION_SCALE;

        /**
         * Control whether the accelerometer will be used to change screen
         * orientation.  If 0, it will not be used unless explicitly requested
         * by the application; if 1, it will be used by default unless explicitly
         * disabled by the application.
         */
        public static final String ACCELEROMETER_ROTATION = "accelerometer_rotation";

        /** @hide */
        public static final Validator ACCELEROMETER_ROTATION_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Default screen rotation when no other policy applies.
         * When {@link #ACCELEROMETER_ROTATION} is zero and no on-screen Activity expresses a
         * preference, this rotation value will be used. Must be one of the
         * {@link android.view.Surface#ROTATION_0 Surface rotation constants}.
         *
         * @see android.view.Display#getRotation
         */
        public static final String USER_ROTATION = "user_rotation";

        /** @hide */
        public static final Validator USER_ROTATION_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(0, 3);

        /**
         * Control whether the rotation lock toggle in the System UI should be hidden.
         * Typically this is done for accessibility purposes to make it harder for
         * the user to accidentally toggle the rotation lock while the display rotation
         * has been locked for accessibility.
         *
         * If 0, then rotation lock toggle is not hidden for accessibility (although it may be
         * unavailable for other reasons).  If 1, then the rotation lock toggle is hidden.
         *
         * @hide
         */
        public static final String HIDE_ROTATION_LOCK_TOGGLE_FOR_ACCESSIBILITY =
                "hide_rotation_lock_toggle_for_accessibility";

        /** @hide */
        public static final Validator HIDE_ROTATION_LOCK_TOGGLE_FOR_ACCESSIBILITY_VALIDATOR =
                BOOLEAN_VALIDATOR;

        /**
         * Whether the phone vibrates when it is ringing due to an incoming call. This will
         * be used by Phone and Setting apps; it shouldn't affect other apps.
         * The value is boolean (1 or 0).
         *
         * Note: this is not same as "vibrate on ring", which had been available until ICS.
         * It was about AudioManager's setting and thus affected all the applications which
         * relied on the setting, while this is purely about the vibration setting for incoming
         * calls.
         */
        public static final String VIBRATE_WHEN_RINGING = "vibrate_when_ringing";

        /** @hide */
        public static final Validator VIBRATE_WHEN_RINGING_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Whether the audible DTMF tones are played by the dialer when dialing. The value is
         * boolean (1 or 0).
         */
        public static final String DTMF_TONE_WHEN_DIALING = "dtmf_tone";

        /** @hide */
        public static final Validator DTMF_TONE_WHEN_DIALING_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * CDMA only settings
         * DTMF tone type played by the dialer when dialing.
         *                 0 = Normal
         *                 1 = Long
         */
        public static final String DTMF_TONE_TYPE_WHEN_DIALING = "dtmf_tone_type";

        /** @hide */
        public static final Validator DTMF_TONE_TYPE_WHEN_DIALING_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Whether the hearing aid is enabled. The value is
         * boolean (1 or 0).
         * @hide
         */
        public static final String HEARING_AID = "hearing_aid";

        /** @hide */
        public static final Validator HEARING_AID_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * CDMA only settings
         * TTY Mode
         * 0 = OFF
         * 1 = FULL
         * 2 = VCO
         * 3 = HCO
         * @hide
         */
        public static final String TTY_MODE = "tty_mode";

        /** @hide */
        public static final Validator TTY_MODE_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(0, 3);

        /**
         * Whether the sounds effects (key clicks, lid open ...) are enabled. The value is
         * boolean (1 or 0).
         */
        public static final String SOUND_EFFECTS_ENABLED = "sound_effects_enabled";

        /** @hide */
        public static final Validator SOUND_EFFECTS_ENABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Whether haptic feedback (Vibrate on tap) is enabled. The value is
         * boolean (1 or 0).
         */
        public static final String HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled";

        /** @hide */
        public static final Validator HAPTIC_FEEDBACK_ENABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * @deprecated Each application that shows web suggestions should have its own
         * setting for this.
         */
        @Deprecated
        public static final String SHOW_WEB_SUGGESTIONS = "show_web_suggestions";

        /** @hide */
        public static final Validator SHOW_WEB_SUGGESTIONS_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Whether to enable Smart Pixels
         * @hide
         */
        public static final String SMART_PIXELS_ENABLE = "smart_pixels_enable";

        /**
         * Smart Pixels pattern
         * @hide
         */
        public static final String SMART_PIXELS_PATTERN = "smart_pixels_pattern";

        /**
         * Smart Pixels Shift Timeout
         * @hide
         */
        public static final String SMART_PIXELS_SHIFT_TIMEOUT = "smart_pixels_shift_timeout";

        /**
         * Whether Smart Pixels should enable on power saver mode
         * @hide
         */
        public static final String SMART_PIXELS_ON_POWER_SAVE = "smart_pixels_on_power_save";

        /**
         * Whether the notification LED should repeatedly flash when a notification is
         * pending. The value is boolean (1 or 0).
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_PULSE = "notification_light_pulse";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Show pointer location on screen?
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String POINTER_LOCATION = "pointer_location";

        /** @hide */
        public static final Validator POINTER_LOCATION_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Show touch positions on screen?
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String SHOW_TOUCHES = "show_touches";

        /** @hide */
        public static final Validator SHOW_TOUCHES_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Log raw orientation data from
         * {@link com.android.server.policy.WindowOrientationListener} for use with the
         * orientationplot.py tool.
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String WINDOW_ORIENTATION_LISTENER_LOG =
                "window_orientation_listener_log";

        /** @hide */
        public static final Validator WINDOW_ORIENTATION_LISTENER_LOG_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#POWER_SOUNDS_ENABLED}
         * instead
         * @hide
         */
        @Deprecated
        public static final String POWER_SOUNDS_ENABLED = Global.POWER_SOUNDS_ENABLED;

        private static final Validator POWER_SOUNDS_ENABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DOCK_SOUNDS_ENABLED}
         * instead
         * @hide
         */
        @Deprecated
        public static final String DOCK_SOUNDS_ENABLED = Global.DOCK_SOUNDS_ENABLED;

        private static final Validator DOCK_SOUNDS_ENABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Check the proximity sensor during wakeup
         * @hide
         */
        public static final String PROXIMITY_ON_WAKE = "proximity_on_wake";

        /**
         * Whether to play sounds when the keyguard is shown and dismissed.
         * @hide
         */
        public static final String LOCKSCREEN_SOUNDS_ENABLED = "lockscreen_sounds_enabled";

        /** @hide */
        public static final Validator LOCKSCREEN_SOUNDS_ENABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Whether the lockscreen should be completely disabled.
         * @hide
         */
        public static final String LOCKSCREEN_DISABLED = "lockscreen.disabled";

        /** @hide */
        public static final Validator LOCKSCREEN_DISABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#LOW_BATTERY_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String LOW_BATTERY_SOUND = Global.LOW_BATTERY_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DESK_DOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String DESK_DOCK_SOUND = Global.DESK_DOCK_SOUND;

        /**
        * Whether to show Qs panel footer warnings like for active VPN
        * @hide
        */
       public static final String QS_FOOTER_WARNINGS = "qs_footer_warnings";

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DESK_UNDOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String DESK_UNDOCK_SOUND = Global.DESK_UNDOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#CAR_DOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String CAR_DOCK_SOUND = Global.CAR_DOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#CAR_UNDOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String CAR_UNDOCK_SOUND = Global.CAR_UNDOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#LOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String LOCK_SOUND = Global.LOCK_SOUND;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#UNLOCK_SOUND}
         * instead
         * @hide
         */
        @Deprecated
        public static final String UNLOCK_SOUND = Global.UNLOCK_SOUND;

        /**
         * Receive incoming SIP calls?
         * 0 = no
         * 1 = yes
         * @hide
         */
        public static final String SIP_RECEIVE_CALLS = "sip_receive_calls";

        /** @hide */
        public static final Validator SIP_RECEIVE_CALLS_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Call Preference String.
         * "SIP_ALWAYS" : Always use SIP with network access
         * "SIP_ADDRESS_ONLY" : Only if destination is a SIP address
         * @hide
         */
        public static final String SIP_CALL_OPTIONS = "sip_call_options";

        /** @hide */
        public static final Validator SIP_CALL_OPTIONS_VALIDATOR =
                new SettingsValidators.DiscreteValueValidator(
                        new String[] {"SIP_ALWAYS", "SIP_ADDRESS_ONLY"});

        /**
         * One of the sip call options: Always use SIP with network access.
         * @hide
         */
        public static final String SIP_ALWAYS = "SIP_ALWAYS";

        /** @hide */
        public static final Validator SIP_ALWAYS_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * One of the sip call options: Only if destination is a SIP address.
         * @hide
         */
        public static final String SIP_ADDRESS_ONLY = "SIP_ADDRESS_ONLY";

        /** @hide */
        public static final Validator SIP_ADDRESS_ONLY_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * @deprecated Use SIP_ALWAYS or SIP_ADDRESS_ONLY instead.  Formerly used to indicate that
         * the user should be prompted each time a call is made whether it should be placed using
         * SIP.  The {@link com.android.providers.settings.DatabaseHelper} replaces this with
         * SIP_ADDRESS_ONLY.
         * @hide
         */
        @Deprecated
        public static final String SIP_ASK_ME_EACH_TIME = "SIP_ASK_ME_EACH_TIME";

        /** @hide */
        public static final Validator SIP_ASK_ME_EACH_TIME_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Pointer speed setting.
         * This is an integer value in a range between -7 and +7, so there are 15 possible values.
         *   -7 = slowest
         *    0 = default speed
         *   +7 = fastest
         * @hide
         */
        public static final String POINTER_SPEED = "pointer_speed";

        /** @hide */
        public static final Validator POINTER_SPEED_VALIDATOR =
                new SettingsValidators.InclusiveFloatRangeValidator(-7, 7);

        /**
         * Whether lock-to-app will be triggered by long-press on recents.
         * @hide
         */
        public static final String LOCK_TO_APP_ENABLED = "lock_to_app_enabled";

        /** @hide */
        public static final Validator LOCK_TO_APP_ENABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * Enable/Disable screenshot sound
         * @hide
         */
        public static final String SCREENSHOT_SOUND = "screenshot_sound";

        /**
         * I am the lolrus.
         * <p>
         * Nonzero values indicate that the user has a bukkit.
         * Backward-compatible with <code>PrefGetPreference(prefAllowEasterEggs)</code>.
         * @hide
         */
        public static final String EGG_MODE = "egg_mode";

        /** @hide */
        public static final Validator EGG_MODE_VALIDATOR = new Validator() {
            @Override
            public boolean validate(@Nullable String value) {
                try {
                    return Long.parseLong(value) >= 0;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        };

        /**
         * Call log delete limit
         */
        public static final String CALL_LOG_DELETE_LIMIT = "call_log_delete_limit";

        /**
         * Setting to determine whether or not to show the battery percentage in the status bar.
         *    0 - Don't show percentage
         *    1 - Show percentage outside
         *    2 - Show percentage inside
         * @hide
         */
        public static final String SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";

        /** @hide */
        public static final Validator SHOW_BATTERY_PERCENT_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(0, 2);

        /**
         * show the membar in recents
         *  @hide
         */
        public static final String SYSTEMUI_RECENTS_MEM_DISPLAY = "systemui_recents_mem_display";

        /**
        * Whether to allow battery light
        * @hide
        */
        public static final String BATTERY_LIGHT_ENABLED = "battery_light_enabled";

        /**
        * Whether to show battery light when DND mode is active
        * @hide
        */
       public static final String BATTERY_LIGHT_ALLOW_ON_DND = "battery_light_allow_on_dnd";

        /**
        * Whether to show blinking light when battery is low
        * @hide
        */
       public static final String BATTERY_LIGHT_LOW_BLINKING = "battery_light_low_blinking";

        /**
        * Low battery charging color
        * @hide
        */
       public static final String BATTERY_LIGHT_LOW_COLOR = "battery_light_low_color";

        /**
        * Medium battery charging color
        * @hide
        */
       public static final String BATTERY_LIGHT_MEDIUM_COLOR = "battery_light_medium_color";

        /**
        * Full battery charging color
        * @hide
        */
       public static final String BATTERY_LIGHT_FULL_COLOR = "battery_light_full_color";

	   /**
        * @hide
         */
        public static final String OMNI_CUSTOM_FP_ICON = "custom_fingerprint_icon";

        /** @hide */
        private static final Validator OMNI_CUSTOM_FP_ICON_VALIDATOR =
                ANY_STRING_VALIDATOR;

        /**
        * Really full 100 battery charging color
        * @hide
        */
       public static final String BATTERY_LIGHT_REALLYFULL_COLOR = "battery_light_reallyfull_color";

        /**
         * Custom button brightness value for manual mode
         *
         * @hide
         */
        public static final String CUSTOM_BUTTON_BRIGHTNESS = "custom_button_brightness";

        /**
         * show clear all recents button
         * @hide
         */
        public static final String SHOW_CLEAR_ALL_RECENTS = "show_clear_all_recents";

        /**
         * location of the clear all rectents button
         * @hide
         */
        public static final String  RECENTS_CLEAR_ALL_LOCATION = "recents_clear_all_location";

        /**
         * use same value for buttons as for screen (manual and auto mode)
         *
         * @hide
         */
        public static final String CUSTOM_BUTTON_USE_SCREEN_BRIGHTNESS = "custom_button_use_screen_brightness";

        /**
         * disable all button brightness (manual and auto mode)
         *
         * @hide
         */
        public static final String BUTTON_BACKLIGHT_ENABLE = "button_backlight_enable";

        /**
         * Timeout value for button lights. 0 = disabled
         * @hide
         */
        public static final String BUTTON_BACKLIGHT_TIMEOUT = "button_backlight_timeout";

        /**
         * @hide
         */
        public static final String BUTTON_BACKLIGHT_ON_TOUCH_ONLY = "button_backlight_on_touch_only";

        /**
         * IMPORTANT: If you add a new public settings you also have to add it to
         * PUBLIC_SETTINGS below. If the new setting is hidden you have to add
         * it to PRIVATE_SETTINGS below. Also add a validator that can validate
         * the setting value. See an example above.
         */

        /**
         * Whether to display sound panel in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_SOUNDPANEL = "powermenu_soundpanel";

        /**
         * Whether to display screenshot in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_SCREENSHOT = "powermenu_screenshot";

        /**
         * Whether to hide the lockscreen clock
         * @hide
         */
        public static final String LOCKSCREEN_CLOCK = "lockscreen_clock";

        /**
         * Select which lockscreen clock style to display
         * @hide
         */
        public static final String LOCKSCREEN_CLOCK_SELECTION = "lockscreen_clock_selection";

        /**
         * Whether to hide the items underneath the lockscreen clock
         * @hide
         */
        public static final String LOCKSCREEN_INFO = "lockscreen_info";

	/**
         * Three Finger Gesture from Oppo
	 *
         * @hide
         */
        public static final String THREE_FINGER_GESTURE = "three_finger_gesture";

        /**
         * Whether to display settings in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_SETTINGS = "powermenu_settings";

        /**
         * Whether to display lock in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_LOCKDOWN = "powermenu_lockdown";

        /**
         * Whether to display airplane in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_AIRPLANE = "powermenu_airplane";

        /**
         * Whether to display reboot in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_RESTART = "powermenu_restart";

        /**
         * Whether to display advanced reboot in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_RESTART_RECOVERY = "powermenu_restart_recovery";

        /**
         * Whether to display power in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_POWER = "powermenu_power";

        /**
         * Whether to display the users option in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_USERS = "powermenu_users";

        /**
         * Whether to display the users option in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_LOGOUT = "powermenu_logout";

        /**
         * Whether to display the power menu dialog while on the lockscreen
         * @hide
         */
        public static final String POWERMENU_LOCKSCREEN = "powermenu_lockscreen";

        /**
         * Double tap on lockscreen to sleep
         * @hide
         */
        public static final String DOUBLE_TAP_SLEEP_LOCKSCREEN =
                "double_tap_sleep_lockscreen";

        /**
         *  Enable statusbar double tap gesture on to put device to sleep
         * @hide
         */
        public static final String DOUBLE_TAP_SLEEP_GESTURE = "double_tap_sleep_gesture";

        /**
         * Disable dashboard conditions in settings
         * @hide
         */
        public static final String ENABLE_CONDITIONS = "enable_conditions";

        /**
         * Disable dashboard suggestions in settings
         * @hide
         */
        public static final String ENABLE_SUGGESTIONS = "enable_suggestions";

        /**
         * Applications list where heasdup should't show
         *
         * @hide
         */
        public static final String HEADS_UP_STOPLIST_VALUES = "heads_up_stoplist_values";
         /**
         * Which applications to disable heads up notifications for
         *
         * @hide
         */
        public static final String HEADS_UP_BLACKLIST_VALUES = "heads_up_blacklist_values";

        /**
         * Whether to enable status and navigation bar color in battery saver mode.
         * Heads up timeout configuration
         * @hide
         */
        public static final String HEADS_UP_TIMEOUT = "heads_up_timeout";

        /**
         * Defines the global heads up notification snooze
         * @hide
         */
        public static final String HEADS_UP_NOTIFICATION_SNOOZE = "heads_up_notification_snooze";

        /**
         * Whether the phone vibrates on call connect
         * @hide
         */
        public static final String VIBRATE_ON_CONNECT = "vibrate_on_connect";

         /** @hide */
        private static final Validator VIBRATE_ON_CONNECT_VALIDATOR = BOOLEAN_VALIDATOR;

         /**
         * Whether the phone vibrates on disconnect
         * @hide
         */
        public static final String VIBRATE_ON_DISCONNECT = "vibrate_on_disconnect";

         /** @hide */
        private static final Validator VIBRATE_ON_DISCONNECT_VALIDATOR = BOOLEAN_VALIDATOR;

         /**
         * Whether the phone vibrates on call waiting
         * @hide
         */
        public static final String VIBRATE_ON_CALLWAITING = "vibrate_on_callwaiting";

         /** @hide */
        private static final Validator VIBRATE_ON_CALLWAITING_VALIDATOR = BOOLEAN_VALIDATOR;

         /**
         * @hide
         */
        public static final String NOTIFICATION_GUTS_KILL_APP_BUTTON = "notification_guts_kill_app_button";

        /**
         * Wheter to play notification sound and vibration if screen is on
         * @hide
         */
        public static final String NOTIFICATION_SOUND_VIB_SCREEN_ON = "notification_sound_vib_screen_on";

         /** @hide */
        private static final Validator NOTIFICATION_SOUND_VIB_SCREEN_ON_VALIDATOR = BOOLEAN_VALIDATOR;

         /**
         * @hide
         */
        public static final String ROAMING_INDICATOR_ICON = "roaming_indicator_icon";

        /**
         * Whether to mute annoying notifications
         * @hide
         */
        public static final String MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD = "mute_annoying_notifications_threshold";

         /** @hide */
        private static final Validator MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD_VALIDATOR = ANY_INTEGER_VALIDATOR;

        /**
         * Whether to display 4G icon instead LTE
         * @hide
         */
        public static final String SHOW_FOURG_ICON = "show_fourg_icon";

        /**
         * Whether to show VoLTE icon or not
         * @hide
         */
        public static final String SHOW_VOLTE_ICON = "volte_icon";

        /**
         * Volume rocker wake
         * @hide
         */
        public static final String VOLUME_ROCKER_WAKE = "volume_rocker_wake";

        /**
         * Home wake button
         * @hide
         */
        public static final String HOME_WAKE_BUTTON = "home_wake_button";

        /**
         * @hide
         */
        public static final String VOLUME_BUTTON_MUSIC_CONTROL = "volume_button_music_control";

        /**
          * Volume keys control cursor in text fields (default is 0)
          * 0 - Disabled
          * 1 - Volume up/down moves cursor left/right
          * 2 - Volume up/down moves cursor right/left
          * @hide
          */
        public static final String VOLUME_KEY_CURSOR_CONTROL = "volume_key_cursor_control";

        /**
         * @hide
         */
        public static final String TORCH_LONG_PRESS_POWER_GESTURE = "torch_long_press_power_gesture";

        /**
         * @hide
         */
        public static final String TORCH_LONG_PRESS_POWER_TIMEOUT = "torch_long_press_power_timeout";

        /**
         * @hide
         */
        public static final String OMNI_QS_QUICKBAR_COLUMNS = "qs_quickbar_columns";

        /** @hide */
        private static final Validator OMNI_QS_QUICKBAR_COLUMNS_VALIDATOR =
                ANY_INTEGER_VALIDATOR;

        /**
         * volume answer
         * @hide
         */
        public static final String ANSWER_VOLUME_BUTTON_BEHAVIOR_ANSWER = "call_volume_answer";

	/** Unlock keystore with fingerprint after reboot
         * @hide
         */
        public static final String FP_UNLOCK_KEYSTORE = "fp_unlock_keystore";

        /**
         * whether to enable or disable vibration on succesful fingerprint auth
         *
         * @hide
         */
        public static final String FINGERPRINT_SUCCESS_VIB = "fingerprint_success_vib";

        /**
         * Whether to show network traffic indicator in statusbar
         * @hide
         */
        public static final String NETWORK_TRAFFIC_STATE = "network_traffic_state";

        /**
         * What to show in network traffic indicator in statusbar
         * @hide
         */
        public static final String NETWORK_TRAFFIC_TYPE = "network_traffic_type";

        /**
         * Network traffic inactivity threshold (default is 1 kBs)
         * @hide
         */
        public static final String NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD = "network_traffic_autohide_threshold";

        /**
         * Whether to show the battery info on the lockscreen while charging
         * @hide
         */
        public static final String LOCKSCREEN_BATTERY_INFO = "lockscreen_battery_info";

        /**
         * 0 - wallpaper based
         * 1 - force light
         * 2 - force dark
         * 3 - force black
         * 4 - force extendedUI
         * 5 - force chocolateUI
         * @hide
         */
        public static final String SYSTEM_THEME_STYLE = "system_theme_style";

        /**
         * SYSTEM_THEME_STYLE value for wallpaper mode.
         * @hide
         */
        public static final int SYSTEM_THEME_STYLE_WALLPAPER = 0;

        /**
         * SYSTEM_THEME_STYLE value for light theme mode.
         * @hide
         */
        public static final int SYSTEM_THEME_STYLE_LIGHT = 1;

        /**
         * SYSTEM_THEME_STYLE value for dark theme mode.
         * @hide
         */
        public static final int SYSTEM_THEME_STYLE_DARK = 2;

        /**
         * SYSTEM_THEME_STYLE value for black theme mode.
         * @hide
         */
        public static final int SYSTEM_THEME_STYLE_BLACK = 3;

        /**
         * SYSTEM_THEME_STYLE value for extended theme mode.
         * @hide
         */
        public static final int SYSTEM_THEME_STYLE_EXTENDED = 4;

        /**
         * SYSTEM_THEME_STYLE value for chocolate theme mode.
         * @hide
         */
        public static final int SYSTEM_THEME_STYLE_CHOCOLATE = 5;

        /**
         * Whether to use icon pack for Recents
         * @hide
         */
        public static final String RECENTS_ICON_PACK = "recents_icon_pack";

        /**
         * Select various accents to go along with system themes
         *
         * @hide
         */
        public static final String ACCENT_PICKER = "accent_picker";

        /**
         * Whether to allow use of system themes alongside substratum
         *
         * @hide
         */
        public static final String FORCE_ALLOW_SYSTEM_THEMES = "force_allow_system_themes";

        /** 
         * Allow all rotations.
         * @hide
         */
        public static final String ACCELEROMETER_ROTATION_ANGLES = "accelerometer_rotation_angles";
        /** @hide */
        private static final Validator ACCELEROMETER_ROTATION_ANGLES_VALIDATOR =
                ANY_STRING_VALIDATOR;
        /**
         * Which component to use for Recents UI
         * 0 - Pie Recents (Quickstep)
         * 1 - Oreo Recents (SystemUI)
         * @hide
         */
        public static final String RECENTS_COMPONENT = "recents_component";

        /** @hide */
        public static final Validator RECENTS_COMPONENT_VALIDATOR =
                new SettingsValidators.InclusiveIntegerRangeValidator(0, 1);

        /**
         * @hide
         */
        public static final String OMNI_BOTTOM_GESTURE_TRIGGER_TIMEOUT =
                "bottom_gesture_navigation_trigger_timeout";

        private static final Validator OMNI_BOTTOM_GESTURE_TRIGGER_TIMEOUT_VALIDATOR =
                ANY_INTEGER_VALIDATOR;

        /**
         * @hide
         */
        public static final String OMNI_BOTTOM_GESTURE_SWIPE_LIMIT =
                "bottom_gesture_navigation_swipe_limit";

        private static final Validator OMNI_BOTTOM_GESTURE_SWIPE_LIMIT_VALIDATOR =
                ANY_INTEGER_VALIDATOR;

        /**
         * Number of qs columns on landscape orientation
         * @hide
         */
        public static final String OMNI_QS_LAYOUT_COLUMNS_LANDSCAPE = "qs_layout_columns_landscape";
         /** @hide */
        private static final Validator OMNI_QS_LAYOUT_COLUMNS_LANDSCAPE_VALIDATOR =
                ANY_INTEGER_VALIDATOR;
         /**
         * @hide
         */
        public static final String OMNI_QS_LAYOUT_COLUMNS = "qs_layout_columns";
         /** @hide */
        private static final Validator OMNI_QS_LAYOUT_COLUMNS_VALIDATOR =
                ANY_INTEGER_VALIDATOR;

        /**
         * Whether to display qs tile titles in the qs panel
         * @hide
         */
        public static final String OMNI_QS_TILE_TITLE_VISIBILITY = "qs_tile_title_visibility";

        /** @hide */
        private static final Validator OMNI_QS_TILE_TITLE_VISIBILITY_VALIDATOR =
                BOOLEAN_VALIDATOR;

        /**
         * @hide
         */
        public static final String OMNI_QS_LAYOUT_ROWS = "qs_layout_rows";

        /** @hide */
        private static final Validator OMNI_QS_LAYOUT_ROWS_VALIDATOR =
                ANY_INTEGER_VALIDATOR;

        /**
         * @hide
         */
        public static final String OMNI_QS_LAYOUT_ROWS_LANDSCAPE = "qs_layout_rows_landscape";

        /** @hide */
        private static final Validator OMNI_QS_LAYOUT_ROWS_LANDSCAPE_VALIDATOR =
                ANY_INTEGER_VALIDATOR;

        /**
         * Toast icon
         * @hide
         */
        public static final String TOAST_ICON = "toast_icon";

        /**
         * some devices have a extra hw button e.g. n3 on the back on the
         * fingerprint sensor. allow mapping button to key
         *
         * @hide
         */
        public static final String CUSTOM_BUTTON_EXTRA_KEY_MAPPING = "button_extra_mapping";

        /** @hide */
        private static final Validator CUSTOM_BUTTON_EXTRA_KEY_MAPPING_VALIDATOR =
                ANY_STRING_VALIDATOR;

        /**
         * Enable proxi check for wake keys - must be implemented in a device
         * KeyHandler
         * @hide
         */
        public static final String CUSTOM_DEVICE_PROXI_CHECK_ENABLED = "device_proxi_check_enabled";

        /** @hide */
        private static final Validator CUSTOM_DEVICE_PROXI_CHECK_ENABLED_VALIDATOR =
                BOOLEAN_VALIDATOR;

        /**
         * Enable Gesture Haptic feedback
         * KeyHandler
         * @hide
         */
        public static final String CUSTOM_DEVICE_GESTURE_FEEDBACK_ENABLED =
                "device_gesture_feedback_enabled";

        /** @hide */
        private static final Validator CUSTOM_DEVICE_GESTURE_FEEDBACK_ENABLED_VALIDATOR =
                BOOLEAN_VALIDATOR;

        /**
         * @hide
         */
        public static final String CUSTOM_DEVICE_FEATURE_SETTINGS = "device_feature_settings";

        /** @hide */
        private static final Validator CUSTOM_DEVICE_FEATURE_SETTINGS_VALIDATOR =
                ANY_STRING_VALIDATOR;

        /**
         * Weather lockscreen temperature scale
         * @hide
         */
        public static final String WEATHER_LOCKSCREEN_UNIT = "weather_lockscreen_unit";

        /** @hide */
        private static final Validator WEATHER_LOCKSCREEN_UNIT_VALIDATOR =
               BOOLEAN_VALIDATOR;

        /**
         * Apps to skip for Pulse
         * @hide
         */
        public static final String PULSE_APPS_BLACKLIST = "pulse_apps_blacklist";

        /**
         * 0 - fullscreen
         * 1 - partial
         * @hide
         */
        public static final String SCREENSHOT_DEFAULT_MODE = "screenshot_default_mode";

        /**
         * Whether to allow one finger quick settings expansion on the right side of the statusbar.
         * @hide
         */
        public static final String STATUS_BAR_QUICK_QS_PULLDOWN = "status_bar_quick_qs_pulldown";

        /**
         * Quick Settings Smart Pulldown
         * @hide
         */
        public static final String QS_SMART_PULLDOWN = "qs_smart_pulldown";

        /**
        * @hide
         */
        public static final String START_SCREEN_STATE_SERVICE = "start_screen_state_service";


        /**
         * @hide
         */
        public static final String SCREEN_STATE_TWOG = "screen_state_twog";

        /**
         * @hide
         */
        public static final String SCREEN_STATE_GPS = "screen_state_gps";

        /**
         * @hide
         */
        public static final String SCREEN_STATE_MOBILE_DATA = "screen_state_mobile_data";

        /**
         * @hide
         */
        public static final String SCREEN_STATE_OFF_DELAY = "screen_state_off_delay";

        /**
         * @hide
         */
        public static final String SCREEN_STATE_ON_DELAY = "screen_state_on_delay";

        /**
         * Volume key controls ringtone or media sound stream
         * @hide
         */
        public static final String VOLUME_KEYS_CONTROL_RING_TONE =
                "volume_keys_control_ring_tone";

        /** @hide */
        private static final Validator VOLUME_KEYS_CONTROL_RING_TONE_VALIDATOR =
               BOOLEAN_VALIDATOR;

        /**
         * @hide
         * Whether to set a lower brightness level when enabling night mode
         * 0: Disabled
         * 1: Set the brightness to a very low value
         * 2: Set the brightness to a low value
         * 3: Set the brightness to a medium value
         * @hide
         */
        public static final String NIGHT_BRIGHTNESS_VALUE = "night_brightness_value";

         /**
         * Status bar carrier label
         * 0: Hide
         * 1: Display on keyguard status bar
         * 2: Display on Normal status bar
         * 3: Enabled for both
         * @hide
         */
        public static final String STATUS_BAR_SHOW_CARRIER = "status_bar_show_carrier";

        /**
         * Status bar brightness control.
         * @hide
         */
        public static final String STATUS_BAR_BRIGHTNESS_CONTROL = "status_bar_brightness_control";

        /** @hide */
        public static final Validator STATUS_BAR_BRIGHTNESS_CONTROL_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * custom carrier label. The value is
         * String.
         * @hide
         */
        public static final String CUSTOM_CARRIER_LABEL = "custom_carrier_label";

        /**
         * Select from various styles to use on the QS tiles
         *
         * @hide
         */
        public static final String QS_TILE_STYLE = "qs_tile_style";

        /**
         * Whether to display cross sign for a data disabled connection
         * @hide
         */
        public static final String DATA_DISABLED_ICON = "data_disabled_icon";

        /**
         * Force an Ambient notification when a new media track is being played
         * 0 - disabled
        * 1 - show track info within normal Ambient Display and force a new Ambient clean layout when skipping tracks
         * @hide
         */
        public static final String FORCE_AMBIENT_FOR_MEDIA = "force_ambient_for_media";

       /**
         * @hide
         */
        public static final String FULL_GESTURE_NAVBAR = "full_gesture_navbar";

        /**
         * @hide
         */
        public static final String FULL_GESTURE_NAVBAR_DT2S = "full_gesture_navbar_dt2s";


        /**
         * Whether the phone ringtone should be played in an increasing manner
         * @hide
         */
        public static final String INCREASING_RING = "increasing_ring";
         /** @hide */
        public static final Validator INCREASING_RING_VALIDATOR =
                BOOLEAN_VALIDATOR;

        /**
         * Start volume fraction for increasing ring volume
         * @hide
         */
        public static final String INCREASING_RING_START_VOLUME = "increasing_ring_start_vol";
         /** @hide */
        public static final Validator INCREASING_RING_START_VOLUME_VALIDATOR =
                ANY_STRING_VALIDATOR;

        /**
         * Ramp up time (seconds) for increasing ring
         * @hide
         */
        public static final String INCREASING_RING_RAMP_UP_TIME = "increasing_ring_ramp_up_time";
         /** @hide */
        public static final Validator INCREASING_RING_RAMP_UP_TIME_VALIDATOR =
                ANY_STRING_VALIDATOR;

        /**
         * Hold current font name and name of the originating package Uses "|" as a delimiter
         *
         * @hide
         */
        public static final String FONT_INFO = "font_info";

        /**
         * Hold list of installed font provider packages. Uses "|" as a delimiter
         *
         * @hide
         */
        public static final String FONT_PACKAGES = "font_packages";

        /**
         * Whether to show the battery bar
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR = "statusbar_battery_bar_no_navbar_list";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_COLOR = "statusbar_battery_bar_color";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_THICKNESS =
                "statusbar_battery_bar_thickness";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_STYLE = "statusbar_battery_bar_style";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_ANIMATE = "statusbar_battery_bar_animate";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_CHARGING_COLOR =
                "statusbar_battery_bar_charging_color";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_BATTERY_LOW_COLOR =
                "statusbar_battery_bar_battery_low_color";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_ENABLE_CHARGING_COLOR =
                "statusbar_battery_bar_enable_charging_color";

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_BLEND_COLORS = "statusbar_battery_bar_blend_color";

        /**
         * Disable expanding quick settings on secure lock screens
         *
         * @hide
         */
        public static final String LOCK_QS_DISABLED = "lockscreen_qs_disabled";

        private static final Validator LOCK_QS_DISABLED_VALIDATOR =
                BOOLEAN_VALIDATOR;

        /**
         * @hide
         */
        public static final String STATUSBAR_BATTERY_BAR_BLEND_COLORS_REVERSE =
                "statusbar_battery_bar_blend_color_reverse";

        /**
         * Toast animations
         *
         * @hide
         */
        public static final String TOAST_ANIMATION = "toast_animation";

        /**  
         * AOKP Custom System Animations
         * @hide
         */  
        public static final String[] ACTIVITY_ANIMATION_CONTROLS = new String[] {
                "activity_open",
                "activity_close",
                "task_open",
                "task_close",
                "task_to_front",
                "task_to_back",
                "wallpaper_open",
                "wallpaper_close",
                "wallpaper_intra_open",
                "wallpaper_intra_close",
                "task_open_behind",
        };  
        public static final String ANIMATION_CONTROLS_DURATION = "animation_controls_duration";

        /**
         * ListView Animations
         * 0 == None
         * 1 == Wave (Left)
         * 2 == Wave (Right)
         * 3 == Scale
         * 4 == Alpha
         * 5 == Stack (Top)
         * 6 == Stack (Bottom)
         * 7 == Translate (Left)
         * 8 == Translate (Right)
         * @hide
         */
        public static final String LISTVIEW_ANIMATION = "listview_animation";

        /**
         * ListView Interpolators
         * 0 == None
         * 1 == accelerate_interpolator
         * 2 == decelerate_interpolator
         * 3 == accelerate_decelerate_interpolator
         * 4 == anticipate_interpolator
         * 5 == overshoot_interpolator
         * 6 == anticipate_overshoot_interpolator
         * 7 == bounce_interpolator
         * @hide
         */
        public static final String LISTVIEW_INTERPOLATOR = "listview_interpolator";

        /**
         * Whether to enable gaming mode or not
         *
         * @hide
         */
        public static final String ENABLE_GAMING_MODE = "enable_gaming_mode";

        /**
         * Whether to show media art on lockscreen
         * @hide
         */
        public static final String LOCKSCREEN_MEDIA_METADATA = "lockscreen_media_metadata";

        /**
         * show recents in grid style or android go style
         * @hide
         */
        public static final String RECENTS_LAYOUT_STYLE = "recents_layout_style";

        /**
         * Whether to show or hide the running services icon
         * @hide
         */
        public static final String QS_RUNNING_SERVICES_TOGGLE = "qs_running_services_toggle";

        /**
         * @hide
         */
        public static final String OMNI_USE_BOTTOM_GESTURE_NAVIGATION =
                "use_bottom_gesture_navigation";

        /** @hide */
        private static final Validator OMNI_USE_BOTTOM_GESTURE_NAVIGATION_VALIDATOR =
                BOOLEAN_VALIDATOR;

        /**
         * Whether to show or hide the edit icon
         * @hide
         */
        public static final String QS_EDIT_TOGGLE = "qs_edit_toggle";

         /**
         * Whether to enable DOZE only when charging
         * @hide
         */
        public static final String DOZE_ON_CHARGE = "doze_on_charge";

        private static final Validator DOZE_ON_CHARGE_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * hidden stting of the current state of DOZE only when charging
         * @hide
         */
        public static final String DOZE_ON_CHARGE_NOW = "doze_on_charge_now";

        /**
         * enable or disable single handed mode
         * @hide
         */
        public static final String ONE_HAND_MODE_ENABLED = "one_hand_mode_enabled";

        /** @hide */
        private static final Validator ONE_HAND_MODE_ENABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /** @hide */
        public static final String AUDIO_PANEL_VIEW_MEDIA = "audio_panel_view_media";

        /** @hide */
        public static final String AUDIO_PANEL_VIEW_NOTIFICATION = "audio_panel_view_notification";

        /** @hide */
        public static final String AUDIO_PANEL_VIEW_ALARM = "audio_panel_view_alarm";

        /** @hide */
        public static final String AUDIO_PANEL_VIEW_VOICE = "audio_panel_view_voice";

        /** @hide */
        public static final String AUDIO_PANEL_VIEW_BT_SCO = "audio_panel_view_bt_sco";

        /** @hide */
        public static final String AUDIO_PANEL_VIEW_POSITION = "audio_panel_view_position";

        /** @hide */
        public static final String AUDIO_PANEL_VIEW_TIMEOUT = "audio_panel_view_timeout";

        /**
         * Control how to handle the display cutout
         * @hide
         */
        public static final String DISPLAY_CUTOUT_MODE = "display_cutout_mode";

        /** @hide */
        private static final Validator DISPLAY_CUTOUT_MODE_VALIDATOR = new
                SettingsValidators.InclusiveIntegerRangeValidator(0, 2);

        /**
         * Control how to handle the display cutout
         * @hide
         */
        public static final String STOCK_STATUSBAR_IN_HIDE = "stock_statusbar_in_hide";

        /** @hide */
        private static final Validator STOCK_STATUSBAR_IN_HIDE_VALIDATOR =
                BOOLEAN_VALIDATOR;

        /**
         * Settings to backup. This is here so that it's in the same place as the settings
         * keys and easy to update.
         *
         * NOTE: Settings are backed up and restored in the order they appear
         *       in this array. If you have one setting depending on another,
         *       make sure that they are ordered appropriately.
         *
         * @hide
         */
        public static final String[] SETTINGS_TO_BACKUP = {
            STAY_ON_WHILE_PLUGGED_IN,   // moved to global
            WIFI_USE_STATIC_IP,
            WIFI_STATIC_IP,
            WIFI_STATIC_GATEWAY,
            WIFI_STATIC_NETMASK,
            WIFI_STATIC_DNS1,
            WIFI_STATIC_DNS2,
            BLUETOOTH_DISCOVERABILITY,
            BLUETOOTH_DISCOVERABILITY_TIMEOUT,
            FONT_SCALE,
            DIM_SCREEN,
            SCREEN_OFF_TIMEOUT,
            SCREEN_BRIGHTNESS_MODE,
            SCREEN_AUTO_BRIGHTNESS_ADJ,
            SCREEN_BRIGHTNESS_FOR_VR,
            VIBRATE_INPUT_DEVICES,
            MODE_RINGER_STREAMS_AFFECTED,
            TEXT_AUTO_REPLACE,
            TEXT_AUTO_CAPS,
            TEXT_AUTO_PUNCTUATE,
            TEXT_SHOW_PASSWORD,
            AUTO_TIME,                  // moved to global
            AUTO_TIME_ZONE,             // moved to global
            TIME_12_24,
            DATE_FORMAT,
            DTMF_TONE_WHEN_DIALING,
            DTMF_TONE_TYPE_WHEN_DIALING,
            HEARING_AID,
            TTY_MODE,
            MASTER_MONO,
            SOUND_EFFECTS_ENABLED,
            HAPTIC_FEEDBACK_ENABLED,
            POWER_SOUNDS_ENABLED,       // moved to global
            DOCK_SOUNDS_ENABLED,        // moved to global
            LOCKSCREEN_SOUNDS_ENABLED,
            SHOW_WEB_SUGGESTIONS,
            SIP_CALL_OPTIONS,
            SIP_RECEIVE_CALLS,
            POINTER_SPEED,
            VIBRATE_WHEN_RINGING,
            RINGTONE,
            RINGTONE2,
            LOCK_TO_APP_ENABLED,
            NOTIFICATION_SOUND,
            ACCELEROMETER_ROTATION,
            SHOW_BATTERY_PERCENT,
            NOTIFICATION_VIBRATION_INTENSITY,
            HAPTIC_FEEDBACK_INTENSITY,
            DISPLAY_COLOR_MODE,
            RECENTS_COMPONENT,
            OMNI_CUSTOM_FP_ICON,
            OMNI_QS_LAYOUT_COLUMNS_LANDSCAPE,
            OMNI_QS_LAYOUT_COLUMNS,
            OMNI_QS_TILE_TITLE_VISIBILITY,
            OMNI_QS_LAYOUT_ROWS,
            OMNI_QS_LAYOUT_ROWS_LANDSCAPE,
            OMNI_QS_QUICKBAR_COLUMNS,
            ACCELEROMETER_ROTATION_ANGLES,
            VOLUME_KEYS_CONTROL_RING_TONE,
            INCREASING_RING,
            INCREASING_RING_START_VOLUME,
            INCREASING_RING_RAMP_UP_TIME,
            STATUS_BAR_BRIGHTNESS_CONTROL,
            LOCK_QS_DISABLED,
            OMNI_USE_BOTTOM_GESTURE_NAVIGATION,
            OMNI_BOTTOM_GESTURE_TRIGGER_TIMEOUT,
            OMNI_BOTTOM_GESTURE_SWIPE_LIMIT,
            NOTIFICATION_LIGHT_PULSE,
            VIBRATE_ON_CONNECT,
            VIBRATE_ON_DISCONNECT,
            VIBRATE_ON_CALLWAITING,
            NOTIFICATION_SOUND_VIB_SCREEN_ON,
            MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD,
            ONE_HAND_MODE_ENABLED,
            DOZE_ON_CHARGE,
            DISPLAY_CUTOUT_MODE,
            STOCK_STATUSBAR_IN_HIDE,
        };

        /**
         * Keys we no longer back up under the current schema, but want to continue to
         * process when restoring historical backup datasets.
         *
         * All settings in {@link LEGACY_RESTORE_SETTINGS} array *must* have a non-null validator,
         * otherwise they won't be restored.
         *
         * @hide
         */
        public static final String[] LEGACY_RESTORE_SETTINGS = {
        };

        /**
         * These are all public system settings
         *
         * @hide
         */
        public static final Set<String> PUBLIC_SETTINGS = new ArraySet<>();
        static {
            PUBLIC_SETTINGS.add(END_BUTTON_BEHAVIOR);
            PUBLIC_SETTINGS.add(WIFI_USE_STATIC_IP);
            PUBLIC_SETTINGS.add(WIFI_STATIC_IP);
            PUBLIC_SETTINGS.add(WIFI_STATIC_GATEWAY);
            PUBLIC_SETTINGS.add(WIFI_STATIC_NETMASK);
            PUBLIC_SETTINGS.add(WIFI_STATIC_DNS1);
            PUBLIC_SETTINGS.add(WIFI_STATIC_DNS2);
            PUBLIC_SETTINGS.add(BLUETOOTH_DISCOVERABILITY);
            PUBLIC_SETTINGS.add(BLUETOOTH_DISCOVERABILITY_TIMEOUT);
            PUBLIC_SETTINGS.add(NEXT_ALARM_FORMATTED);
            PUBLIC_SETTINGS.add(FONT_SCALE);
            PUBLIC_SETTINGS.add(DIM_SCREEN);
            PUBLIC_SETTINGS.add(SCREEN_OFF_TIMEOUT);
            PUBLIC_SETTINGS.add(SCREEN_BRIGHTNESS);
            PUBLIC_SETTINGS.add(SCREEN_BRIGHTNESS_FOR_VR);
            PUBLIC_SETTINGS.add(SCREEN_BRIGHTNESS_MODE);
            PUBLIC_SETTINGS.add(MODE_RINGER_STREAMS_AFFECTED);
            PUBLIC_SETTINGS.add(MUTE_STREAMS_AFFECTED);
            PUBLIC_SETTINGS.add(VIBRATE_ON);
            PUBLIC_SETTINGS.add(VOLUME_RING);
            PUBLIC_SETTINGS.add(VOLUME_SYSTEM);
            PUBLIC_SETTINGS.add(VOLUME_VOICE);
            PUBLIC_SETTINGS.add(VOLUME_MUSIC);
            PUBLIC_SETTINGS.add(VOLUME_ALARM);
            PUBLIC_SETTINGS.add(VOLUME_NOTIFICATION);
            PUBLIC_SETTINGS.add(VOLUME_BLUETOOTH_SCO);
            PUBLIC_SETTINGS.add(RINGTONE);
            PUBLIC_SETTINGS.add(RINGTONE2);
            PUBLIC_SETTINGS.add(NOTIFICATION_SOUND);
            PUBLIC_SETTINGS.add(ALARM_ALERT);
            PUBLIC_SETTINGS.add(TEXT_AUTO_REPLACE);
            PUBLIC_SETTINGS.add(TEXT_AUTO_CAPS);
            PUBLIC_SETTINGS.add(TEXT_AUTO_PUNCTUATE);
            PUBLIC_SETTINGS.add(TEXT_SHOW_PASSWORD);
            PUBLIC_SETTINGS.add(SHOW_GTALK_SERVICE_STATUS);
            PUBLIC_SETTINGS.add(WALLPAPER_ACTIVITY);
            PUBLIC_SETTINGS.add(TIME_12_24);
            PUBLIC_SETTINGS.add(DATE_FORMAT);
            PUBLIC_SETTINGS.add(SETUP_WIZARD_HAS_RUN);
            PUBLIC_SETTINGS.add(ACCELEROMETER_ROTATION);
            PUBLIC_SETTINGS.add(USER_ROTATION);
            PUBLIC_SETTINGS.add(DTMF_TONE_WHEN_DIALING);
            PUBLIC_SETTINGS.add(SOUND_EFFECTS_ENABLED);
            PUBLIC_SETTINGS.add(HAPTIC_FEEDBACK_ENABLED);
            PUBLIC_SETTINGS.add(SHOW_WEB_SUGGESTIONS);
            PUBLIC_SETTINGS.add(VIBRATE_WHEN_RINGING);
        }

        /**
         * These are all hidden system settings.
         *
         * @hide
         */
        public static final Set<String> PRIVATE_SETTINGS = new ArraySet<>();
        static {
            PRIVATE_SETTINGS.add(WIFI_USE_STATIC_IP);
            PRIVATE_SETTINGS.add(END_BUTTON_BEHAVIOR);
            PRIVATE_SETTINGS.add(ADVANCED_SETTINGS);
            PRIVATE_SETTINGS.add(SCREEN_AUTO_BRIGHTNESS_ADJ);
            PRIVATE_SETTINGS.add(VIBRATE_INPUT_DEVICES);
            PRIVATE_SETTINGS.add(VOLUME_MASTER);
            PRIVATE_SETTINGS.add(MASTER_MONO);
            PRIVATE_SETTINGS.add(NOTIFICATIONS_USE_RING_VOLUME);
            PRIVATE_SETTINGS.add(VIBRATE_IN_SILENT);
            PRIVATE_SETTINGS.add(MEDIA_BUTTON_RECEIVER);
            PRIVATE_SETTINGS.add(HIDE_ROTATION_LOCK_TOGGLE_FOR_ACCESSIBILITY);
            PRIVATE_SETTINGS.add(DTMF_TONE_TYPE_WHEN_DIALING);
            PRIVATE_SETTINGS.add(HEARING_AID);
            PRIVATE_SETTINGS.add(TTY_MODE);
            PRIVATE_SETTINGS.add(NOTIFICATION_LIGHT_PULSE);
            PRIVATE_SETTINGS.add(POINTER_LOCATION);
            PRIVATE_SETTINGS.add(SHOW_TOUCHES);
            PRIVATE_SETTINGS.add(WINDOW_ORIENTATION_LISTENER_LOG);
            PRIVATE_SETTINGS.add(POWER_SOUNDS_ENABLED);
            PRIVATE_SETTINGS.add(DOCK_SOUNDS_ENABLED);
            PRIVATE_SETTINGS.add(LOCKSCREEN_SOUNDS_ENABLED);
            PRIVATE_SETTINGS.add(LOCKSCREEN_DISABLED);
            PRIVATE_SETTINGS.add(LOW_BATTERY_SOUND);
            PRIVATE_SETTINGS.add(DESK_DOCK_SOUND);
            PRIVATE_SETTINGS.add(DESK_UNDOCK_SOUND);
            PRIVATE_SETTINGS.add(CAR_DOCK_SOUND);
            PRIVATE_SETTINGS.add(CAR_UNDOCK_SOUND);
            PRIVATE_SETTINGS.add(LOCK_SOUND);
            PRIVATE_SETTINGS.add(UNLOCK_SOUND);
            PRIVATE_SETTINGS.add(SIP_RECEIVE_CALLS);
            PRIVATE_SETTINGS.add(SIP_CALL_OPTIONS);
            PRIVATE_SETTINGS.add(SIP_ALWAYS);
            PRIVATE_SETTINGS.add(SIP_ADDRESS_ONLY);
            PRIVATE_SETTINGS.add(SIP_ASK_ME_EACH_TIME);
            PRIVATE_SETTINGS.add(POINTER_SPEED);
            PRIVATE_SETTINGS.add(LOCK_TO_APP_ENABLED);
            PRIVATE_SETTINGS.add(EGG_MODE);
            PRIVATE_SETTINGS.add(SHOW_BATTERY_PERCENT);
            PRIVATE_SETTINGS.add(DISPLAY_COLOR_MODE);
            PRIVATE_SETTINGS.add(DOUBLE_TAP_SLEEP_LOCKSCREEN);
            PRIVATE_SETTINGS.add(DOUBLE_TAP_SLEEP_GESTURE);
            PRIVATE_SETTINGS.add(NETWORK_TRAFFIC_STATE);
            PRIVATE_SETTINGS.add(NETWORK_TRAFFIC_TYPE);
            PRIVATE_SETTINGS.add(NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD);
            PRIVATE_SETTINGS.add(BATTERY_LIGHT_ENABLED);
            PRIVATE_SETTINGS.add(BATTERY_LIGHT_ALLOW_ON_DND);
            PRIVATE_SETTINGS.add(BATTERY_LIGHT_LOW_COLOR);
            PRIVATE_SETTINGS.add(BATTERY_LIGHT_MEDIUM_COLOR);
            PRIVATE_SETTINGS.add(BATTERY_LIGHT_FULL_COLOR);
            PRIVATE_SETTINGS.add(BATTERY_LIGHT_REALLYFULL_COLOR);
            PRIVATE_SETTINGS.add(BATTERY_LIGHT_LOW_BLINKING);
            PRIVATE_SETTINGS.add(SYSTEM_THEME_STYLE);
            PRIVATE_SETTINGS.add(RECENTS_COMPONENT);
            PRIVATE_SETTINGS.add(OMNI_CUSTOM_FP_ICON);
            PRIVATE_SETTINGS.add(OMNI_QS_LAYOUT_COLUMNS_LANDSCAPE);
            PRIVATE_SETTINGS.add(OMNI_QS_LAYOUT_COLUMNS);
            PRIVATE_SETTINGS.add(OMNI_QS_TILE_TITLE_VISIBILITY);
            PRIVATE_SETTINGS.add(OMNI_QS_LAYOUT_ROWS);
            PRIVATE_SETTINGS.add(OMNI_QS_LAYOUT_ROWS_LANDSCAPE);
            PRIVATE_SETTINGS.add(OMNI_QS_QUICKBAR_COLUMNS);
            PRIVATE_SETTINGS.add(TOAST_ICON);
            PRIVATE_SETTINGS.add(SCREENSHOT_DEFAULT_MODE);
            PRIVATE_SETTINGS.add(START_SCREEN_STATE_SERVICE);
            PRIVATE_SETTINGS.add(SCREEN_STATE_TWOG);
            PRIVATE_SETTINGS.add(SCREEN_STATE_GPS);
            PRIVATE_SETTINGS.add(SCREEN_STATE_MOBILE_DATA);
            PRIVATE_SETTINGS.add(SCREEN_STATE_OFF_DELAY);
            PRIVATE_SETTINGS.add(SCREEN_STATE_ON_DELAY);
            PRIVATE_SETTINGS.add(WEATHER_LOCKSCREEN_UNIT);
            PRIVATE_SETTINGS.add(CUSTOM_BUTTON_EXTRA_KEY_MAPPING);
            PRIVATE_SETTINGS.add(CUSTOM_DEVICE_PROXI_CHECK_ENABLED);
            PRIVATE_SETTINGS.add(CUSTOM_DEVICE_GESTURE_FEEDBACK_ENABLED);
            PRIVATE_SETTINGS.add(CUSTOM_DEVICE_FEATURE_SETTINGS);
            PRIVATE_SETTINGS.add(ACCELEROMETER_ROTATION_ANGLES);
            PRIVATE_SETTINGS.add(VOLUME_KEYS_CONTROL_RING_TONE);
            PRIVATE_SETTINGS.add(CUSTOM_BUTTON_BRIGHTNESS);
            PRIVATE_SETTINGS.add(CUSTOM_BUTTON_USE_SCREEN_BRIGHTNESS);
            PRIVATE_SETTINGS.add(BUTTON_BACKLIGHT_ENABLE);
            PRIVATE_SETTINGS.add(BUTTON_BACKLIGHT_TIMEOUT);
            PRIVATE_SETTINGS.add(BUTTON_BACKLIGHT_ON_TOUCH_ONLY);
            PRIVATE_SETTINGS.add(INCREASING_RING);
            PRIVATE_SETTINGS.add(INCREASING_RING_START_VOLUME);
            PRIVATE_SETTINGS.add(INCREASING_RING_RAMP_UP_TIME);
            PRIVATE_SETTINGS.add(STATUS_BAR_BRIGHTNESS_CONTROL);
            PRIVATE_SETTINGS.add(LOCK_QS_DISABLED);
            PRIVATE_SETTINGS.add(OMNI_USE_BOTTOM_GESTURE_NAVIGATION);
            PRIVATE_SETTINGS.add(OMNI_BOTTOM_GESTURE_TRIGGER_TIMEOUT);
            PRIVATE_SETTINGS.add(OMNI_BOTTOM_GESTURE_SWIPE_LIMIT);
            PRIVATE_SETTINGS.add(VIBRATE_ON_CONNECT);
            PRIVATE_SETTINGS.add(VIBRATE_ON_DISCONNECT);
            PRIVATE_SETTINGS.add(VIBRATE_ON_CALLWAITING);
            PRIVATE_SETTINGS.add(NOTIFICATION_SOUND_VIB_SCREEN_ON);
            PRIVATE_SETTINGS.add(MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD);
            PRIVATE_SETTINGS.add(ONE_HAND_MODE_ENABLED);
            PRIVATE_SETTINGS.add(DOZE_ON_CHARGE);
            PRIVATE_SETTINGS.add(DISPLAY_CUTOUT_MODE);
            PRIVATE_SETTINGS.add(STOCK_STATUSBAR_IN_HIDE);
        }


        /**
         * Whether to display the torch option in the power menu
         *
         * @hide
         */
        public static final String POWERMENU_TORCH = "powermenu_torch";

        /**
         * These are all public system settings
         *
         * All settings in {@link SETTINGS_TO_BACKUP} array *must* have a non-null validator,
         * otherwise they won't be restored.
         *
         * @hide
         */
        public static final Map<String, Validator> VALIDATORS = new ArrayMap<>();
        static {
            VALIDATORS.put(STAY_ON_WHILE_PLUGGED_IN, STAY_ON_WHILE_PLUGGED_IN_VALIDATOR);
            VALIDATORS.put(END_BUTTON_BEHAVIOR, END_BUTTON_BEHAVIOR_VALIDATOR);
            VALIDATORS.put(WIFI_USE_STATIC_IP, WIFI_USE_STATIC_IP_VALIDATOR);
            VALIDATORS.put(BLUETOOTH_DISCOVERABILITY, BLUETOOTH_DISCOVERABILITY_VALIDATOR);
            VALIDATORS.put(BLUETOOTH_DISCOVERABILITY_TIMEOUT,
                    BLUETOOTH_DISCOVERABILITY_TIMEOUT_VALIDATOR);
            VALIDATORS.put(NEXT_ALARM_FORMATTED, NEXT_ALARM_FORMATTED_VALIDATOR);
            VALIDATORS.put(FONT_SCALE, FONT_SCALE_VALIDATOR);
            VALIDATORS.put(DIM_SCREEN, DIM_SCREEN_VALIDATOR);
            VALIDATORS.put(DISPLAY_COLOR_MODE, DISPLAY_COLOR_MODE_VALIDATOR);
            VALIDATORS.put(SCREEN_OFF_TIMEOUT, SCREEN_OFF_TIMEOUT_VALIDATOR);
            VALIDATORS.put(SCREEN_BRIGHTNESS_FOR_VR, SCREEN_BRIGHTNESS_FOR_VR_VALIDATOR);
            VALIDATORS.put(SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_VALIDATOR);
            VALIDATORS.put(MODE_RINGER_STREAMS_AFFECTED, MODE_RINGER_STREAMS_AFFECTED_VALIDATOR);
            VALIDATORS.put(MUTE_STREAMS_AFFECTED, MUTE_STREAMS_AFFECTED_VALIDATOR);
            VALIDATORS.put(VIBRATE_ON, VIBRATE_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_VALIDATOR);
            VALIDATORS.put(HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_VALIDATOR);
            VALIDATORS.put(RINGTONE, RINGTONE_VALIDATOR);
            VALIDATORS.put(RINGTONE2, RINGTONE_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_SOUND, NOTIFICATION_SOUND_VALIDATOR);
            VALIDATORS.put(ALARM_ALERT, ALARM_ALERT_VALIDATOR);
            VALIDATORS.put(TEXT_AUTO_REPLACE, TEXT_AUTO_REPLACE_VALIDATOR);
            VALIDATORS.put(TEXT_AUTO_CAPS, TEXT_AUTO_CAPS_VALIDATOR);
            VALIDATORS.put(TEXT_AUTO_PUNCTUATE, TEXT_AUTO_PUNCTUATE_VALIDATOR);
            VALIDATORS.put(TEXT_SHOW_PASSWORD, TEXT_SHOW_PASSWORD_VALIDATOR);
            VALIDATORS.put(AUTO_TIME, AUTO_TIME_VALIDATOR);
            VALIDATORS.put(AUTO_TIME_ZONE, AUTO_TIME_ZONE_VALIDATOR);
            VALIDATORS.put(SHOW_GTALK_SERVICE_STATUS, SHOW_GTALK_SERVICE_STATUS_VALIDATOR);
            VALIDATORS.put(WALLPAPER_ACTIVITY, WALLPAPER_ACTIVITY_VALIDATOR);
            VALIDATORS.put(TIME_12_24, TIME_12_24_VALIDATOR);
            VALIDATORS.put(DATE_FORMAT, DATE_FORMAT_VALIDATOR);
            VALIDATORS.put(SETUP_WIZARD_HAS_RUN, SETUP_WIZARD_HAS_RUN_VALIDATOR);
            VALIDATORS.put(ACCELEROMETER_ROTATION, ACCELEROMETER_ROTATION_VALIDATOR);
            VALIDATORS.put(USER_ROTATION, USER_ROTATION_VALIDATOR);
            VALIDATORS.put(DTMF_TONE_WHEN_DIALING, DTMF_TONE_WHEN_DIALING_VALIDATOR);
            VALIDATORS.put(SOUND_EFFECTS_ENABLED, SOUND_EFFECTS_ENABLED_VALIDATOR);
            VALIDATORS.put(HAPTIC_FEEDBACK_ENABLED, HAPTIC_FEEDBACK_ENABLED_VALIDATOR);
            VALIDATORS.put(POWER_SOUNDS_ENABLED, POWER_SOUNDS_ENABLED_VALIDATOR);
            VALIDATORS.put(DOCK_SOUNDS_ENABLED, DOCK_SOUNDS_ENABLED_VALIDATOR);
            VALIDATORS.put(SHOW_WEB_SUGGESTIONS, SHOW_WEB_SUGGESTIONS_VALIDATOR);
            VALIDATORS.put(WIFI_USE_STATIC_IP, WIFI_USE_STATIC_IP_VALIDATOR);
            VALIDATORS.put(END_BUTTON_BEHAVIOR, END_BUTTON_BEHAVIOR_VALIDATOR);
            VALIDATORS.put(ADVANCED_SETTINGS, ADVANCED_SETTINGS_VALIDATOR);
            VALIDATORS.put(SCREEN_AUTO_BRIGHTNESS_ADJ, SCREEN_AUTO_BRIGHTNESS_ADJ_VALIDATOR);
            VALIDATORS.put(VIBRATE_INPUT_DEVICES, VIBRATE_INPUT_DEVICES_VALIDATOR);
            VALIDATORS.put(MASTER_MONO, MASTER_MONO_VALIDATOR);
            VALIDATORS.put(NOTIFICATIONS_USE_RING_VOLUME, NOTIFICATIONS_USE_RING_VOLUME_VALIDATOR);
            VALIDATORS.put(VIBRATE_IN_SILENT, VIBRATE_IN_SILENT_VALIDATOR);
            VALIDATORS.put(MEDIA_BUTTON_RECEIVER, MEDIA_BUTTON_RECEIVER_VALIDATOR);
            VALIDATORS.put(HIDE_ROTATION_LOCK_TOGGLE_FOR_ACCESSIBILITY,
                    HIDE_ROTATION_LOCK_TOGGLE_FOR_ACCESSIBILITY_VALIDATOR);
            VALIDATORS.put(VIBRATE_WHEN_RINGING, VIBRATE_WHEN_RINGING_VALIDATOR);
            VALIDATORS.put(DTMF_TONE_TYPE_WHEN_DIALING, DTMF_TONE_TYPE_WHEN_DIALING_VALIDATOR);
            VALIDATORS.put(HEARING_AID, HEARING_AID_VALIDATOR);
            VALIDATORS.put(TTY_MODE, TTY_MODE_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE, NOTIFICATION_LIGHT_PULSE_VALIDATOR);
            VALIDATORS.put(POINTER_LOCATION, POINTER_LOCATION_VALIDATOR);
            VALIDATORS.put(SHOW_TOUCHES, SHOW_TOUCHES_VALIDATOR);
            VALIDATORS.put(WINDOW_ORIENTATION_LISTENER_LOG,
                    WINDOW_ORIENTATION_LISTENER_LOG_VALIDATOR);
            VALIDATORS.put(LOCKSCREEN_SOUNDS_ENABLED, LOCKSCREEN_SOUNDS_ENABLED_VALIDATOR);
            VALIDATORS.put(LOCKSCREEN_DISABLED, LOCKSCREEN_DISABLED_VALIDATOR);
            VALIDATORS.put(SIP_RECEIVE_CALLS, SIP_RECEIVE_CALLS_VALIDATOR);
            VALIDATORS.put(SIP_CALL_OPTIONS, SIP_CALL_OPTIONS_VALIDATOR);
            VALIDATORS.put(SIP_ALWAYS, SIP_ALWAYS_VALIDATOR);
            VALIDATORS.put(SIP_ADDRESS_ONLY, SIP_ADDRESS_ONLY_VALIDATOR);
            VALIDATORS.put(SIP_ASK_ME_EACH_TIME, SIP_ASK_ME_EACH_TIME_VALIDATOR);
            VALIDATORS.put(POINTER_SPEED, POINTER_SPEED_VALIDATOR);
            VALIDATORS.put(LOCK_TO_APP_ENABLED, LOCK_TO_APP_ENABLED_VALIDATOR);
            VALIDATORS.put(EGG_MODE, EGG_MODE_VALIDATOR);
            VALIDATORS.put(WIFI_STATIC_IP, WIFI_STATIC_IP_VALIDATOR);
            VALIDATORS.put(WIFI_STATIC_GATEWAY, WIFI_STATIC_GATEWAY_VALIDATOR);
            VALIDATORS.put(WIFI_STATIC_NETMASK, WIFI_STATIC_NETMASK_VALIDATOR);
            VALIDATORS.put(WIFI_STATIC_DNS1, WIFI_STATIC_DNS1_VALIDATOR);
            VALIDATORS.put(WIFI_STATIC_DNS2, WIFI_STATIC_DNS2_VALIDATOR);
            VALIDATORS.put(SHOW_BATTERY_PERCENT, SHOW_BATTERY_PERCENT_VALIDATOR);
            VALIDATORS.put(RECENTS_COMPONENT,RECENTS_COMPONENT_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE, BOOLEAN_VALIDATOR);
            VALIDATORS.put(OMNI_CUSTOM_FP_ICON,
                    OMNI_CUSTOM_FP_ICON_VALIDATOR);
            VALIDATORS.put(OMNI_QS_LAYOUT_COLUMNS_LANDSCAPE,
                    OMNI_QS_LAYOUT_COLUMNS_LANDSCAPE_VALIDATOR);
            VALIDATORS.put(OMNI_QS_LAYOUT_COLUMNS, OMNI_QS_LAYOUT_COLUMNS_VALIDATOR);
            VALIDATORS.put(OMNI_QS_TILE_TITLE_VISIBILITY, OMNI_QS_TILE_TITLE_VISIBILITY_VALIDATOR);
            VALIDATORS.put(OMNI_QS_LAYOUT_ROWS, OMNI_QS_LAYOUT_ROWS_VALIDATOR);
            VALIDATORS.put(OMNI_QS_LAYOUT_ROWS_LANDSCAPE, OMNI_QS_LAYOUT_ROWS_LANDSCAPE_VALIDATOR);
            VALIDATORS.put(OMNI_QS_QUICKBAR_COLUMNS, OMNI_QS_QUICKBAR_COLUMNS_VALIDATOR);
            VALIDATORS.put(CUSTOM_BUTTON_EXTRA_KEY_MAPPING, CUSTOM_BUTTON_EXTRA_KEY_MAPPING_VALIDATOR);
            VALIDATORS.put(CUSTOM_DEVICE_PROXI_CHECK_ENABLED, CUSTOM_DEVICE_PROXI_CHECK_ENABLED_VALIDATOR);
            VALIDATORS.put(CUSTOM_DEVICE_GESTURE_FEEDBACK_ENABLED, CUSTOM_DEVICE_GESTURE_FEEDBACK_ENABLED_VALIDATOR);
            VALIDATORS.put(CUSTOM_DEVICE_FEATURE_SETTINGS, CUSTOM_DEVICE_FEATURE_SETTINGS_VALIDATOR);
            VALIDATORS.put(WEATHER_LOCKSCREEN_UNIT, WEATHER_LOCKSCREEN_UNIT_VALIDATOR);
            VALIDATORS.put(ACCELEROMETER_ROTATION_ANGLES, ACCELEROMETER_ROTATION_ANGLES_VALIDATOR);
            VALIDATORS.put(VOLUME_KEYS_CONTROL_RING_TONE,VOLUME_KEYS_CONTROL_RING_TONE_VALIDATOR);
            VALIDATORS.put(INCREASING_RING, INCREASING_RING_VALIDATOR);
            VALIDATORS.put(INCREASING_RING_START_VOLUME, INCREASING_RING_START_VOLUME_VALIDATOR);
            VALIDATORS.put(INCREASING_RING_RAMP_UP_TIME, INCREASING_RING_RAMP_UP_TIME_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_BRIGHTNESS_CONTROL, STATUS_BAR_BRIGHTNESS_CONTROL_VALIDATOR);
            VALIDATORS.put(LOCK_QS_DISABLED, LOCK_QS_DISABLED_VALIDATOR);
            VALIDATORS.put(OMNI_USE_BOTTOM_GESTURE_NAVIGATION,
                    OMNI_USE_BOTTOM_GESTURE_NAVIGATION_VALIDATOR);
            VALIDATORS.put(OMNI_BOTTOM_GESTURE_TRIGGER_TIMEOUT,
                    OMNI_BOTTOM_GESTURE_TRIGGER_TIMEOUT_VALIDATOR);
            VALIDATORS.put(OMNI_BOTTOM_GESTURE_SWIPE_LIMIT,
                    OMNI_BOTTOM_GESTURE_SWIPE_LIMIT_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE, BOOLEAN_VALIDATOR);
            VALIDATORS.put(VIBRATE_ON_CONNECT, VIBRATE_ON_CONNECT_VALIDATOR);
            VALIDATORS.put(VIBRATE_ON_DISCONNECT, VIBRATE_ON_DISCONNECT_VALIDATOR);
            VALIDATORS.put(VIBRATE_ON_CALLWAITING, VIBRATE_ON_CALLWAITING_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_SOUND_VIB_SCREEN_ON, NOTIFICATION_SOUND_VIB_SCREEN_ON_VALIDATOR);
            VALIDATORS.put(MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD, MUTE_ANNOYING_NOTIFICATIONS_THRESHOLD_VALIDATOR);
            VALIDATORS.put(ONE_HAND_MODE_ENABLED, ONE_HAND_MODE_ENABLED_VALIDATOR);
            VALIDATORS.put(DOZE_ON_CHARGE, DOZE_ON_CHARGE_VALIDATOR);
            VALIDATORS.put(DISPLAY_CUTOUT_MODE, DISPLAY_CUTOUT_MODE_VALIDATOR);
            VALIDATORS.put(STOCK_STATUSBAR_IN_HIDE, STOCK_STATUSBAR_IN_HIDE_VALIDATOR);
        }

        /**
         * These entries are considered common between the personal and the managed profile,
         * since the managed profile doesn't get to change them.
         */
        private static final Set<String> CLONE_TO_MANAGED_PROFILE = new ArraySet<>();
        static {
            CLONE_TO_MANAGED_PROFILE.add(DATE_FORMAT);
            CLONE_TO_MANAGED_PROFILE.add(HAPTIC_FEEDBACK_ENABLED);
            CLONE_TO_MANAGED_PROFILE.add(SOUND_EFFECTS_ENABLED);
            CLONE_TO_MANAGED_PROFILE.add(TEXT_SHOW_PASSWORD);
            CLONE_TO_MANAGED_PROFILE.add(TIME_12_24);
        }

        /** @hide */
        public static void getCloneToManagedProfileSettings(Set<String> outKeySet) {
            outKeySet.addAll(CLONE_TO_MANAGED_PROFILE);
        }

        /**
         * These entries should be cloned from this profile's parent only if the dependency's
         * value is true ("1")
         *
         * Note: the dependencies must be Secure settings
         *
         * @hide
         */
        public static final Map<String, String> CLONE_FROM_PARENT_ON_VALUE = new ArrayMap<>();
        static {
            CLONE_FROM_PARENT_ON_VALUE.put(RINGTONE, Secure.SYNC_PARENT_SOUNDS);
            CLONE_FROM_PARENT_ON_VALUE.put(RINGTONE2, Secure.SYNC_PARENT_SOUNDS);
            CLONE_FROM_PARENT_ON_VALUE.put(NOTIFICATION_SOUND, Secure.SYNC_PARENT_SOUNDS);
            CLONE_FROM_PARENT_ON_VALUE.put(ALARM_ALERT, Secure.SYNC_PARENT_SOUNDS);
        }

        /** @hide */
        public static void getCloneFromParentOnValueSettings(Map<String, String> outMap) {
            outMap.putAll(CLONE_FROM_PARENT_ON_VALUE);
        }

        /**
         * System settings which can be accessed by instant apps.
         * @hide
         */
        public static final Set<String> INSTANT_APP_SETTINGS = new ArraySet<>();
        static {
            INSTANT_APP_SETTINGS.add(TEXT_AUTO_REPLACE);
            INSTANT_APP_SETTINGS.add(TEXT_AUTO_CAPS);
            INSTANT_APP_SETTINGS.add(TEXT_AUTO_PUNCTUATE);
            INSTANT_APP_SETTINGS.add(TEXT_SHOW_PASSWORD);
            INSTANT_APP_SETTINGS.add(DATE_FORMAT);
            INSTANT_APP_SETTINGS.add(FONT_SCALE);
            INSTANT_APP_SETTINGS.add(HAPTIC_FEEDBACK_ENABLED);
            INSTANT_APP_SETTINGS.add(TIME_12_24);
            INSTANT_APP_SETTINGS.add(SOUND_EFFECTS_ENABLED);
            INSTANT_APP_SETTINGS.add(ACCELEROMETER_ROTATION);
        }

        /**
         * When to use Wi-Fi calling
         *
         * @see android.telephony.TelephonyManager.WifiCallingChoices
         * @hide
         */
        public static final String WHEN_TO_MAKE_WIFI_CALLS = "when_to_make_wifi_calls";

        // Settings moved to Settings.Secure

        /**
         * @deprecated Use {@link android.provider.Settings.Global#ADB_ENABLED}
         * instead
         */
        @Deprecated
        public static final String ADB_ENABLED = Global.ADB_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#ANDROID_ID} instead
         */
        @Deprecated
        public static final String ANDROID_ID = Secure.ANDROID_ID;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#BLUETOOTH_ON} instead
         */
        @Deprecated
        public static final String BLUETOOTH_ON = Global.BLUETOOTH_ON;

        private static final Validator BLUETOOTH_ON_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DATA_ROAMING} instead
         */
        @Deprecated
        public static final String DATA_ROAMING = Global.DATA_ROAMING;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#DEVICE_PROVISIONED} instead
         */
        @Deprecated
        public static final String DEVICE_PROVISIONED = Global.DEVICE_PROVISIONED;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#HTTP_PROXY} instead
         */
        @Deprecated
        public static final String HTTP_PROXY = Global.HTTP_PROXY;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#INSTALL_NON_MARKET_APPS} instead
         */
        @Deprecated
        public static final String INSTALL_NON_MARKET_APPS = Secure.INSTALL_NON_MARKET_APPS;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#LOCATION_PROVIDERS_ALLOWED}
         * instead
         */
        @Deprecated
        public static final String LOCATION_PROVIDERS_ALLOWED = Secure.LOCATION_PROVIDERS_ALLOWED;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#LOGGING_ID} instead
         */
        @Deprecated
        public static final String LOGGING_ID = Secure.LOGGING_ID;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#NETWORK_PREFERENCE} instead
         */
        @Deprecated
        public static final String NETWORK_PREFERENCE = Global.NETWORK_PREFERENCE;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#PARENTAL_CONTROL_ENABLED}
         * instead
         */
        @Deprecated
        public static final String PARENTAL_CONTROL_ENABLED = Secure.PARENTAL_CONTROL_ENABLED;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#PARENTAL_CONTROL_LAST_UPDATE}
         * instead
         */
        @Deprecated
        public static final String PARENTAL_CONTROL_LAST_UPDATE = Secure.PARENTAL_CONTROL_LAST_UPDATE;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#PARENTAL_CONTROL_REDIRECT_URL}
         * instead
         */
        @Deprecated
        public static final String PARENTAL_CONTROL_REDIRECT_URL =
            Secure.PARENTAL_CONTROL_REDIRECT_URL;

        /**
         * @deprecated Use {@link android.provider.Settings.Secure#SETTINGS_CLASSNAME} instead
         */
        @Deprecated
        public static final String SETTINGS_CLASSNAME = Secure.SETTINGS_CLASSNAME;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#USB_MASS_STORAGE_ENABLED} instead
         */
        @Deprecated
        public static final String USB_MASS_STORAGE_ENABLED = Global.USB_MASS_STORAGE_ENABLED;

        private static final Validator USB_MASS_STORAGE_ENABLED_VALIDATOR = BOOLEAN_VALIDATOR;

        /**
         * @deprecated Use {@link android.provider.Settings.Global#USE_GOOGLE_MAIL} instead
         */
        @Deprecated
        public static final String USE_GOOGLE_MAIL = Global.USE_GOOGLE_MAIL;

       /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_MAX_DHCP_RETRY_COUNT} instead
         */
        @Deprecated
        public static final String WIFI_MAX_DHCP_RETRY_COUNT = Global.WIFI_MAX_DHCP_RETRY_COUNT;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS} instead
         */
        @Deprecated
        public static final String WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS =
                Global.WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS;

        /**
         * @deprecated Use
         * {@link android.provider.Settings.Global#WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON} instead
         */
        @Deprecated
        public static final String WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON =
                Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON;

        private static f