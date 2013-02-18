/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctrlplusz.dashclock.yr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import com.ctrlplusz.dashclock.yr.configuration.AppChooserPreference;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.Locale;

public class YrExtension extends DashClockExtension {

    public static final String PREF_WEATHER_UNITS = "pref_yr_weather_units";
    public static final String PREF_WEATHER_SHORTCUT = "pref_yr_weather_shortcut";

    public static final Intent DEFAULT_WEATHER_INTENT = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=weather"));

    private static final long STALE_LOCATION_NANOS = 10l * 60000000000l; // 10 minutes

    private static XmlPullParserFactory sXmlPullParserFactory;

    private static final Criteria sLocationCriteria;

    private static Intent sWeatherIntent;
    private static String sWeatherUnits = "f";

    private boolean mOneTimeLocationListenerActive = false;

    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }

    private static final String TAG = "Yr Dashclock Extension";

    static {
        try {
            sXmlPullParserFactory = XmlPullParserFactory.newInstance();
            sXmlPullParserFactory.setNamespaceAware(true);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Could not instantiate XmlPullParserFactory", e);
        }
    }

    @Override
    protected void onUpdateData(int reason) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sWeatherUnits = sp.getString(PREF_WEATHER_UNITS, sWeatherUnits);
        sWeatherIntent = AppChooserPreference.getIntentValue(
                sp.getString(PREF_WEATHER_SHORTCUT, null), DEFAULT_WEATHER_INTENT);

        NetworkInfo ni = ((ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String provider = lm.getBestProvider(sLocationCriteria, true);
        if (TextUtils.isEmpty(provider)) {
            Log.e(TAG, "No available location providers matching criteria.");
            return;
        }

        final Location lastLocation = lm.getLastKnownLocation(provider);
        if (lastLocation == null ||
                (SystemClock.elapsedRealtimeNanos() - lastLocation.getElapsedRealtimeNanos())
                        >= STALE_LOCATION_NANOS) {
            Log.w(TAG, "Stale or missing last-known location; requesting single coarse location "
                    + "update.");
            disableOneTimeLocationListener();
            mOneTimeLocationListenerActive = true;
            lm.requestSingleUpdate(provider, mOneTimeLocationListener, null);
        } else {
            getWeatherAndTryPublishUpdate(lastLocation);
        }
    }

    private void disableOneTimeLocationListener() {
        if (mOneTimeLocationListenerActive) {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.removeUpdates(mOneTimeLocationListener);
            mOneTimeLocationListenerActive = false;
        }
    }

    private LocationListener mOneTimeLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            getWeatherAndTryPublishUpdate(location);
            disableOneTimeLocationListener();
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        @Override
        public void onProviderEnabled(String s) {
        }

        @Override
        public void onProviderDisabled(String s) {
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        disableOneTimeLocationListener();
    }

    private void getWeatherAndTryPublishUpdate(Location location) {
        try {
            WeatherData weatherData = getWeatherDataForLocation(location);
            publishUpdate(renderExtensionData(weatherData));
        } catch (IOException e) {
            Log.w(TAG, "Generic read error while retrieving weather information.", e);
        } catch (InvalidLocationException e) {
            Log.w(TAG, "Invalid location.", e);
        }
    }

    private ExtensionData renderExtensionData(WeatherData weatherData) {
        String temperature = weatherData.hasValidTemperature()
                ? getString(R.string.temperature_template, getTemperatureBasedOnSelectedWeatherUnit(weatherData.temperature))
                : getString(R.string.status_none);
        StringBuilder expandedBody = new StringBuilder();

        int conditionIconId = WeatherData.getConditionIconId(weatherData.conditionCode);
        if (WeatherData.getConditionIconId(weatherData.todayForecastConditionCode)
                == R.drawable.ic_weather_raining) {

            // Show rain if it will rain today.
            conditionIconId = R.drawable.ic_weather_raining;
            expandedBody.append(
                    getString(R.string.later_forecast_template, weatherData.forecastText));
        }

        if (expandedBody.length() > 0) {
            expandedBody.append("\n");
        }
        expandedBody.append(weatherData.location);

        return new ExtensionData()
                .visible(true)
                .status(temperature)
                .clickIntent(sWeatherIntent)
                .expandedTitle(getString(R.string.weather_expanded_title_template, temperature + sWeatherUnits.toUpperCase(Locale.US),
                        weatherData.conditionText))
                .icon(conditionIconId)
                .expandedBody(expandedBody.toString());
    }

    private String getTemperatureBasedOnSelectedWeatherUnit(String temperature) {
        if (sWeatherUnits.equals("f")){
            double fahrenheitTemperature = Double.valueOf(temperature)  *  9/5 + 32;
            return Long.toString(Math.round(fahrenheitTemperature));
        }

        return temperature;
    }

    private static WeatherData getWeatherDataForLocation(Location location) throws IOException, InvalidLocationException {
        WeatherData data = new WeatherData();
        LocationInfo li = getLocationInfo(location);
        data.location = li.town + ", " + li.country;

        HttpURLConnection connection = Utils.openUrlConnection(buildWeatherQueryUrl(location));

        try {
            XmlPullParser xpp = sXmlPullParserFactory.newPullParser();
            xpp.setInput(new InputStreamReader(connection.getInputStream()));

            boolean hasTodayForecast = false;
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {

                if (eventType == XmlPullParser.START_TAG){
                    if ("temperature".equals(xpp.getName())) {
                        for (int i = 0; i < xpp.getAttributeCount(); i++){
                            if ("value".equals(xpp.getAttributeName(i))){
                                data.temperature = xpp.getAttributeValue(i);
                            }
                        }
                    }

                    if ("symbol".equals(xpp.getName())){
                        for (int i = 0; i < xpp.getAttributeCount(); i++){
                            if ("number".equals(xpp.getAttributeName(i))){
                                data.conditionCode = Integer.parseInt(xpp.getAttributeValue(i));
                            }
                        }
                    }
                }

                if (!"NaN".equals(data.temperature) && data.conditionCode != -1){
                    break;
                }
                eventType = xpp.next();
            }

            data.conditionText = WeatherData.getConditionText(data.conditionCode);

            return data;

        } catch (XmlPullParserException e) {
            throw new IOException("Error parsing weather feed XML.", e);
        } finally {
            connection.disconnect();
        }
    }

    private static String buildWeatherQueryUrl(Location location) throws MalformedURLException {
        return "http://api.yr.no/weatherapi/locationforecast/1.8/?lat=" + location.getLatitude() + "&lon=" + location.getLongitude();
    }

    private static LocationInfo getLocationInfo(Location location)
            throws IOException, InvalidLocationException {
        LocationInfo li = new LocationInfo();
        HttpURLConnection connection = Utils.openUrlConnection(buildPlaceSearchUrl(location));
        InputStreamReader inputStreamReader = new InputStreamReader(connection.getInputStream());

        try {
            XmlPullParser xpp = sXmlPullParserFactory.newPullParser();

            xpp.setInput(inputStreamReader);

            int eventType = xpp.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG){
                    if ("locality1".equals(xpp.getName())) {
                        li.town = xpp.nextText();
                    }

                    if ("country".equals(xpp.getName())) {
                        li.country = xpp.nextText();
                    }
                }

                eventType = xpp.next();
            }

            if (!TextUtils.isEmpty(li.town)) {
                return li;
            }

            throw new InvalidLocationException();

        } catch (XmlPullParserException e) {
            throw new IOException("Error parsing location XML response.", e);
        } finally {
            connection.disconnect();
            inputStreamReader.close();
        }
    }

    private static String buildPlaceSearchUrl(Location l) throws MalformedURLException {
        // GeoPlanet API
        return "http://where.yahooapis.com/v1/places.q('"
                + l.getLatitude() + "," + l.getLongitude() + "')"
                + "?appid=" + Config.YAHOO_PLACES_API_KEY;
    }


    private static class LocationInfo {
        String country;
        String town;
    }

    public static class InvalidLocationException extends Exception {
        public InvalidLocationException() {
        }
    }
}
