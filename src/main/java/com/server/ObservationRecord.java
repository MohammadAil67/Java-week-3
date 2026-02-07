package com.server;

import org.json.JSONObject;
import org.json.JSONArray;

public class ObservationRecord {
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;
    private JSONObject orbitalElements;
    private JSONObject stateVector;

    public ObservationRecord(String targetBodyName, String centerBodyName, String epoch, 
                            JSONObject orbitalElements, JSONObject stateVector) {
        this.targetBodyName = targetBodyName;
        this.centerBodyName = centerBodyName;
        this.epoch = epoch;
        this.orbitalElements = orbitalElements;
        this.stateVector = stateVector;
    }

    public String getTargetBodyName() {
        return targetBodyName;
    }

    public void setTargetBodyName(String targetBodyName) {
        this.targetBodyName = targetBodyName;
    }

    public String getCenterBodyName() {
        return centerBodyName;
    }

    public void setCenterBodyName(String centerBodyName) {
        this.centerBodyName = centerBodyName;
    }

    public String getEpoch() {
        return epoch;
    }

    public void setEpoch(String epoch) {
        this.epoch = epoch;
    }

    public JSONObject getOrbitalElements() {
        return orbitalElements;
    }

    public void setOrbitalElements(JSONObject orbitalElements) {
        this.orbitalElements = orbitalElements;
    }

    public JSONObject getStateVector() {
        return stateVector;
    }

    public void setStateVector(JSONObject stateVector) {
        this.stateVector = stateVector;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("target_body_name", targetBodyName);
        json.put("center_body_name", centerBodyName);
        json.put("epoch", epoch);
        
        if (orbitalElements != null) {
            json.put("orbital_elements", orbitalElements);
        }
        
        if (stateVector != null) {
            json.put("state_vector", stateVector);
        }
        
        return json;
    }
}
