package com.server;

import org.json.JSONObject;
import org.json.JSONArray;

public class ObservationRecord {
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;
    private JSONObject orbitalElements;
    private JSONObject stateVector;
    
    // Metadata fields
    private int id;
    private String recordTimeReceived;
    private String recordOwner;

    public ObservationRecord(String targetBodyName, String centerBodyName, String epoch, 
                            JSONObject orbitalElements, JSONObject stateVector) {
        this.targetBodyName = targetBodyName;
        this.centerBodyName = centerBodyName;
        this.epoch = epoch;
        this.orbitalElements = orbitalElements;
        this.stateVector = stateVector;
        this.id = -1; // Default value
        this.recordTimeReceived = null;
        this.recordOwner = null;
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

    public void setMetadata(int id, String recordTimeReceived, String recordOwner) {
        this.id = id;
        this.recordTimeReceived = recordTimeReceived;
        this.recordOwner = recordOwner;
    }

    public int getId() {
        return id;
    }

    public String getRecordTimeReceived() {
        return recordTimeReceived;
    }

    public String getRecordOwner() {
        return recordOwner;
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
        
        // Add metadata if available
        if (recordTimeReceived != null && recordOwner != null && id != -1) {
            JSONObject metadata = new JSONObject();
            metadata.put("record_time_received", recordTimeReceived);
            metadata.put("record_owner", recordOwner);
            metadata.put("id", id);
            json.put("metadata", metadata);
        }
        
        return json;
    }
}
