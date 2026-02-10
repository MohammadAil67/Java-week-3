package com.o3.server;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import java.util.ArrayList;

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
    private String recordPayload;
    private List<Observatory> observatories;
    private String updateReason;
    private String edited;

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
        this.recordPayload = null;
        this.observatories = new ArrayList<>();
        this.updateReason = null;
        this.edited = null;
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

    public String getRecordPayload() {
        return recordPayload;
    }

    public void setRecordPayload(String recordPayload) {
        this.recordPayload = recordPayload;
    }

    public List<Observatory> getObservatories() {
        return observatories;
    }

    public void setObservatories(List<Observatory> observatories) {
        this.observatories = observatories;
    }

    public void addObservatory(Observatory observatory) {
        this.observatories.add(observatory);
    }

    public String getUpdateReason() {
        return updateReason;
    }

    public void setUpdateReason(String updateReason) {
        this.updateReason = updateReason;
    }

    public String getEdited() {
        return edited;
    }

    public void setEdited(String edited) {
        this.edited = edited;
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
            
            // Add observatory information if present
            if (observatories != null && !observatories.isEmpty()) {
                JSONArray observatoryArray = new JSONArray();
                for (Observatory obs : observatories) {
                    observatoryArray.put(obs.toJSON());
                }
                metadata.put("observatory", observatoryArray);
            }
            
            if (recordPayload != null) {
                metadata.put("record_payload", recordPayload);
            }
            
            // Add update_reason and edited fields if present
            if (updateReason != null) {
                metadata.put("update_reason", updateReason);
            }
            if (edited != null) {
                metadata.put("edited", edited);
            }
            
            json.put("metadata", metadata);
        }
        
        return json;
    }
}
