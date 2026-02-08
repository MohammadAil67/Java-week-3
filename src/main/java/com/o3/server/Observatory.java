package com.o3.server;

import org.json.JSONObject;

public class Observatory {
    private double latitude;
    private double longitude;
    private String observatoryName;

    public Observatory(double latitude, double longitude, String observatoryName) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.observatoryName = observatoryName;
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

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("latitude", latitude);
        json.put("longitude", longitude);
        json.put("observatory_name", observatoryName);
        return json;
    }
}
