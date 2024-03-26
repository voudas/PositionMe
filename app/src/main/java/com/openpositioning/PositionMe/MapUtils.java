package com.openpositioning.PositionMe;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

public class MapUtils {
    public static void switchMapType(GoogleMap mMap, int which) {
        switch (which) {
            case 0:
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                break;
            case 1:
                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                break;
            case 2:
                mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                break;
            case 3:
                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                break;
        }
    }

    static LatLng getNearestPointOnBounds(LatLng location, LatLngBounds bounds) {
        double lat = Math.max(bounds.southwest.latitude, Math.min(location.latitude, bounds.northeast.latitude));
        double lng = Math.max(bounds.southwest.longitude, Math.min(location.longitude, bounds.northeast.longitude));
        return new LatLng(lat, lng);
    }

    static double distanceBetweenPoints(LatLng point1, LatLng point2) {
        double earthRadius = 6371000; // meters
        double dLat = Math.toRadians(point2.latitude - point1.latitude);
        double dLng = Math.toRadians(point2.longitude - point1.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(point1.latitude)) * Math.cos(Math.toRadians(point2.latitude)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
