/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.device;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.zebra.deviceidentifierswrapper.DIHelper;
import com.zebra.deviceidentifierswrapper.IDIResultCallbacks;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.TimeZone;

public class Device extends CordovaPlugin {

    public static final String TAG = Device.class.getCanonicalName();

    public static String platform;                            // Device OS
    public static String uuid;                                // Device UUID

    private static final String ANDROID_PLATFORM = "Android";
    private static final String AMAZON_PLATFORM = "amazon-fireos";
    private static final String AMAZON_DEVICE = "Amazon";
    private static final String ZEBRA_DEVICE = "zebra";

    /**
     * Constructor.
     */
    public Device() {
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Device.uuid = getUuid();
    }

    /**
     * Executes the request
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("getDeviceInfo".equals(action)) {
            getDeviceInfo(callbackContext);
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Stores all collected device info (like OS, platform, model, serial etc...)
     * in a JSONObject (= pluginResult).
     *
     * In case of a zebra device running Android 10 or higher,
     * pluginResult will be modified and returned in one of the callbacks
     * of "retrieveZebraSerialNumber()"
     *
     * In any other cases or devices, pluginResult is returned immediately.
     *
     * @param callbackContext   The callback id used when calling back into JavaScript.
     */
    private void getDeviceInfo(CallbackContext callbackContext) throws JSONException {
        JSONObject pluginResult = new JSONObject();
        pluginResult.put("uuid", Device.uuid);
        pluginResult.put("version", this.getOSVersion());
        pluginResult.put("platform", this.getPlatform());
        pluginResult.put("model", this.getModel());
        pluginResult.put("manufacturer", this.getManufacturer());
        pluginResult.put("isVirtual", this.isVirtual());

        // Due to privacy restrictions ALL devices running on Android 10 or higher
        // "getSerialNumber()" will just return the string "unknown" instead of the real device serial number ...
        pluginResult.put("serial", this.getSerialNumber());

        // ... luckily, at least zebra devices offer a way to deliver their serial number on Android 10 or higher.
        // So in case the device is a zebra device running on Android >= 10
        // we are trying to (asynchronously) access the serial number via a DeviceIdentifiersWrapper
        // and replace the "unknown" serial number in the PluginResult with the real serial number.
        if (this.isZebraDevice() && android.os.Build.VERSION.SDK_INT >= 29) {
            this.retrieveZebraSerialNumber(this.cordova.getContext(), callbackContext, pluginResult);
        } else {
            // in any other case, we instantly return the collected device values back to the caller
            // (serial number will be "unknown" if device runs Android 10 or higher)
            callbackContext.success(pluginResult);
        }
    }

    /**
     * Tries to retrieve the serial number of a Zebra device using DeviceIdentifiersWrapper.
     *
     * For more information see:
     * https://github.com/ZebraDevs/DeviceIdentifiersWrapper
     *
     * @param context            The Android context
     * @param callbackContext    The callback id used when calling back into JavaScript.
     * @param pluginResult       A JSONObject which contains already gathered device info (like OS, platform, model, serial etc...)
     */
    private void retrieveZebraSerialNumber(Context context, CallbackContext callbackContext, JSONObject pluginResult)
    {
        DIHelper.getSerialNumber(context, new IDIResultCallbacks() {
            @Override
            public void onSuccess(String serialNumber)  {
                try {
                    // replace "unknown" with the real serial number in the pluginResult
                    pluginResult.put("serial", serialNumber);
                } catch (JSONException e) {
                    Log.e(TAG, "error while assigning serial number to JSONObject: " + e.getMessage());
                } finally {
                    callbackContext.success(pluginResult);
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "error while retrieving Zebra serial number: " + message);
                // in case the serial number couldn't be retrieved by the DeviceIdentifiersWrapper
                // the serial number is "unknown" in the PluginResult
                callbackContext.success(pluginResult);
            }

            @Override
            public void onDebugStatus(String message) {
                // You can use this method to get verbose information
                // about what's happening behind the curtain
                Log.d(TAG, "Zebra serial number retrieval debug status: " + message);
            }
        });
    }


    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    /**
     * Get the OS name.
     *
     * @return
     */
    public String getPlatform() {
        String platform;
        if (isAmazonDevice()) {
            platform = AMAZON_PLATFORM;
        } else {
            platform = ANDROID_PLATFORM;
        }
        return platform;
    }

    /**
     * Get the device's Universally Unique Identifier (UUID).
     *
     * @return
     */
    public String getUuid() {
        String uuid = Settings.Secure.getString(this.cordova.getActivity().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        return uuid;
    }

    public String getModel() {
        String model = android.os.Build.MODEL;
        return model;
    }

    public String getProductName() {
        String productname = android.os.Build.PRODUCT;
        return productname;
    }

    public String getManufacturer() {
        String manufacturer = android.os.Build.MANUFACTURER;
        return manufacturer;
    }

    public String getSerialNumber() {
        String serial = android.os.Build.SERIAL;
        return serial;
    }

    /**
     * Get the OS version.
     *
     * @return
     */
    public String getOSVersion() {
        String osversion = android.os.Build.VERSION.RELEASE;
        return osversion;
    }

    public String getSDKVersion() {
        @SuppressWarnings("deprecation")
        String sdkversion = android.os.Build.VERSION.SDK;
        return sdkversion;
    }

    public String getTimeZoneID() {
        TimeZone tz = TimeZone.getDefault();
        return (tz.getID());
    }

    /**
     * Function to check if the device is manufactured by Amazon
     *
     * @return
     */
    public boolean isAmazonDevice() {
        if (android.os.Build.MANUFACTURER.equals(AMAZON_DEVICE)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if this device is manufactured by Zebra.
     *
     * @return True if this device is manufactured by Zebra, false if not.
     */
    public static boolean isZebraDevice() {
        return android.os.Build.MANUFACTURER.toLowerCase().startsWith(ZEBRA_DEVICE);
    }

    public boolean isVirtual() {
        return android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.PRODUCT.contains("sdk");
    }

}
