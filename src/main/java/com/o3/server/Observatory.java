package com.o3.server;

import org.json.JSONObject;

public class Observatory {
    private double latitude;
    private double longitude;
    private String observatoryName;
    private Double temperatureInKelvins;
    private Double cloudinessPercentage;
    private Double backgroundLightVolume;

    public Observatory(double latitude, double longitude, String observatoryName) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.observatoryName = observatoryName;
        this.temperatureInKelvins = null;
        this.cloudinessPercentage = null;
        this.backgroundLightVolume = null;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getObservatoryName() {
        return observatoryName;
    }

    public void setObservatoryName(String observatoryName) {
        this.observatoryName = observatoryName;
    }

    public Double getTemperatureInKelvins() {
        return temperatureInKelvins;
    }

    public void setTemperatureInKelvins(Double temperatureInKelvins) {
        this.temperatureInKelvins = temperatureInKelvins;
    }

    public Double getCloudinessPercentage() {
        return cloudinessPercentage;
    }

    public void setCloudinessPercentage(Double cloudinessPercentage) {
        this.cloudinessPercentage = cloudinessPercentage;
    }

    public Double getBackgroundLightVolume() {
        return backgroundLightVolume;
    }

    public void setBackgroundLightVolume(Double backgroundLightVolume) {
        this.backgroundLightVolume = backgroundLightVolume;
    }

    public void setWeatherData(double temperatureInKelvins, double cloudinessPercentage, double backgroundLightVolume) {
        this.temperatureInKelvins = temperatureInKelvins;
        this.cloudinessPercentage = cloudinessPercentage;
        this.backgroundLightVolume = backgroundLightVolume;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("latitude", latitude);
        json.put("longitude", longitude);
        json.put("observatory_name", observatoryName);
        
        // Add observatory_weather if weather data is available
        if (temperatureInKelvins != null && cloudinessPercentage != null && backgroundLightVolume != null) {
            JSONObject weather = new JSONObject();
            weather.put("temperature_in_kelvins", temperatureInKelvins);
            weather.put("cloudiness_percentage", cloudinessPercentage);
            weather.put("background_light_volume", backgroundLightVolume);
            json.put("observatory_weather", weather);
        }
        
        return json;
    }
}
