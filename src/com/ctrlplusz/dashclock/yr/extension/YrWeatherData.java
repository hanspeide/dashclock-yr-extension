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

package com.ctrlplusz.dashclock.yr.extension;

import com.ctrlplusz.dashclock.yr.R;

/**
 * A helper class representing weather data, for use with {@link com.ctrlplusz.dashclock.yr.extension.YrExtension}.
 */
public class YrWeatherData {
    public static final String INVALID_TEMPERATURE = "NaN";
    public static final int INVALID_CONDITION = -1;

    public String temperature = INVALID_TEMPERATURE;
    public int conditionCode = INVALID_CONDITION;
    public int todayForecastConditionCode = INVALID_CONDITION;
    public String conditionText;
    public String forecastText;
    public String location;

    public YrWeatherData() {
    }

    public boolean hasValidTemperature() {
        return !"NaN".equals(temperature);
    }

    public static int getConditionIconId(int conditionCode) {
        // http://api.yr.no/weatherapi/weathericon/1.0/documentation
        switch (conditionCode) {
            case 15:
                return R.drawable.ic_weather_foggy;
            case 2: // light cloud
            case 4: // cloud
            case 17: // light cloud (winter darkness)
                return R.drawable.ic_weather_cloudy;
            case 3: // partly cloudy
                return R.drawable.ic_weather_partly_cloudy;
            case 1: // sun
            case 16: // sun (winter darkness)
                return R.drawable.ic_weather_sunny;
            case 5: // light rain sun
            case 6: // light rain thunder sun
            case 9: // light rain
            case 10: // rain
            case 11: // rain and thunder
            case 18: // light rain sun (winter darkness);
            case 22: // light rain thunder

                return R.drawable.ic_weather_raining;
            case 7:  // sleet sun
            case 8:  // snow sun
            case 12: // sleet
            case 13: // snow
            case 14: // snow thunder
            case 19: // snow sun (winter darkness)
            case 20: // sleet sun thunder
            case 21: // snow sun thunder
            case 23: // sleet thunder
                return R.drawable.ic_weather_snow;
        }

        return R.drawable.ic_weather_clear;
    }

    public static String getConditionText(int conditionCode) {
        // http://api.yr.no/weatherapi/weathericon/1.0/documentation
        switch (conditionCode) {
            case 1: return "Sun";
            case 2: return "Light Cloud";
            case 3: return "Partly Cloudy";
            case 4: return "Cloudy";
            case 5: return "Light Rain/Sun";
            case 6: return "Light Rain/Thunder/Sun";
            case 7: return "Sleet and Sun";
            case 8: return "Snow and Sun";
            case 9: return "Light Rain";
            case 10: return "Rain";
            case 11: return "Rain and Thunder";
            case 12: return "Sleet";
            case 13: return "Snow";
            case 14: return "Snow and Thunder";
            case 15: return "Foggy";
            case 16: return "Sun";
            case 17: return "Light Cloud";
            case 18: return "Light Rain and Sun";
            case 19: return "Snow and Sun";
            case 20: return "Sleet/Sun/Thunder";
            case 21: return "Snow/Sun/Thunder";
            case 22: return "Light Rain and Thunder";
            case 23: return "Sleet and Thunder";
        }

        return "NaN";
    }
}
