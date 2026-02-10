package com.o3.server;

import org.json.JSONObject;

public class WeatherFetcher {
    
    /**
     * Returns mock weather data for given coordinates
     * 
     * @param latitude The latitude of the observatory
     * @param longitude The longitude of the observatory
     * @return JSONObject containing temperature_in_kelvins, cloudiness_percentage, and background_light_volume
     */
    public static JSONObject fetchWeatherData(double latitude, double longitude) {
        return getMockWeatherData();
    }
    
    /**
     * Returns mock weather data
     */
    private static JSONObject getMockWeatherData() {
        JSONObject weatherData = new JSONObject();
        weatherData.put("temperature_in_kelvins", 253.15);
        weatherData.put("cloudiness_percentage", 0);
        weatherData.put("background_light_volume", 10.5);
        return weatherData;
    }
}
