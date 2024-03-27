package com.openpositioning.PositionMe.sensors;

import com.google.gson.Gson;
import java.util.List;

public class WifiFPManager {
    private SensorFusion sensorFusion;

    public WifiFPManager(SensorFusion sensorFusion) {
        this.sensorFusion = sensorFusion;
    }

    public String createWifiFingerprintJson() {
        List<Wifi> wifiList = sensorFusion.getWifiList();
        if (wifiList != null && !wifiList.isEmpty()) {
            Gson gson = new Gson();
            String jsonWifiFingerprints = gson.toJson(wifiList);
            return jsonWifiFingerprints;
        }
        return "{}"; // Return an empty JSON object if no Wi-Fi data is available
    }
}