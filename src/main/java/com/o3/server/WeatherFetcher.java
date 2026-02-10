package com.o3.server;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WeatherFetcher {
    
    /**
     * Fetches weather data for given coordinates using OpenWeatherMap API
     * 
     * @param latitude The latitude of the observatory
     * @param longitude The longitude of the observatory
     * @return JSONObject containing temperature_in_kelvins, cloudiness_percentage, and background_light_volume
     */
    public static JSONObject fetchWeatherData(double latitude, double longitude) {
        JSONObject weatherData = new JSONObject();
        
        try {
            // Get API key from environment variable
            String apiKey = System.getenv("OPENWEATHER_API_KEY");
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                // If no API key, return dummy data
                return getDummyWeatherData();
            }
            
            // Build URL for OpenWeatherMap API
            String urlString = String.format(
                "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s",
                latitude, longitude, apiKey
            );
            
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject apiResponse = new JSONObject(response.toString());
                
                // Extract relevant data
                JSONObject main = apiResponse.getJSONObject("main");
                double temperatureKelvin = main.getDouble("temp");
                
                JSONObject clouds = apiResponse.getJSONObject("clouds");
                double cloudiness = clouds.getDouble("all");
                
                // Calculate background light volume based on cloudiness (inverted)
                // Less clouds = more background light
                double backgroundLight = 100.0 - cloudiness;
                
                weatherData.put("temperature_in_kelvins", temperatureKelvin);
                weatherData.put("cloudiness_percentage", cloudiness);
                weatherData.put("background_light_volume", backgroundLight);
                
                return weatherData;
            } else {
                // If API call fails, return dummy data
                return getDummyWeatherData();
            }
            
        } catch (Exception e) {
            System.err.println("Error fetching weather data: " + e.getMessage());
            // Return dummy data on error
            return getDummyWeatherData();
        }
    }
    
    /**
     * Returns dummy weather data when API is not available
     */
    private static JSONObject getDummyWeatherData() {
        JSONObject weatherData = new JSONObject();
        weatherData.put("temperature_in_kelvins", 253.15);
        weatherData.put("cloudiness_percentage", 0);
        weatherData.put("background_light_volume", 10.5);
        return weatherData;
    }
}
