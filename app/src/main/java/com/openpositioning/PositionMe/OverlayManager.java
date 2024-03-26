package com.openpositioning.PositionMe;

import android.content.Context;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OverlayManager {
    private GoogleMap mMap;
    private Context context;
    private List<OverlayData> overlayPoints;
    private LatLngBounds nucleusBounds, libraryBounds;
    private List<OverlayData> nucleusOverlayPoints;
    private List<OverlayData> libraryOverlayPoints;
    double proximityThreshold = 10;
    private boolean overlaysEnabled = false;
    private boolean isOverlayVisible = false;

    public OverlayManager(GoogleMap map, Context context) {
        this.mMap = map;
        this.context = context;
        initializeBuildingBounds();
        initializeOverlayPoints();
    }

    public void disableOverlays() {
        hideAllOverlays();
        overlaysEnabled = false;
    }

    public void enableOverlays() {
        overlaysEnabled = true;
    }

    private void initializeBuildingBounds() {
        nucleusBounds = new LatLngBounds(
                new LatLng(55.92278, -3.17465),
                new LatLng(55.92335, -3.173842)
        );
        libraryBounds = new LatLngBounds(
                new LatLng(55.922738, -3.17517),
                new LatLng(55.923061, -3.174764)
        );
    }

    private void initializeOverlayPoints() {

        nucleusOverlayPoints = Arrays.asList(
                new OverlayData(nucleusBounds, R.drawable.nucleusg, "Ground Floor"),
                new OverlayData(nucleusBounds, R.drawable.nucleus1, "Floor 1"),
                new OverlayData(nucleusBounds, R.drawable.nucleus2, "Floor 2"),
                new OverlayData(nucleusBounds, R.drawable.nucleus3, "Floor 3")
        );

        libraryOverlayPoints = Arrays.asList(
                new OverlayData(libraryBounds, R.drawable.libraryg, "Ground Floor"),
                new OverlayData(libraryBounds, R.drawable.library1, "Floor 1"),
                new OverlayData(libraryBounds, R.drawable.library2, "Floor 2"),
                new OverlayData(libraryBounds, R.drawable.library3, "Floor 3")
        );

        overlayPoints = new ArrayList<>();
        overlayPoints.addAll(nucleusOverlayPoints);
        overlayPoints.addAll(libraryOverlayPoints);
    }

    public void showOverlayForFloor(String floor, LatLng userLocation) {
        if (!overlaysEnabled) return;

        // First, hide any existing overlay
        hideAllOverlays();

        if (isCloseToBuildingBounds(userLocation, nucleusBounds, proximityThreshold)) {
            displayOverlayFromList(nucleusOverlayPoints, floor);
        } else if (isCloseToBuildingBounds(userLocation, libraryBounds, proximityThreshold)) {
            displayOverlayFromList(libraryOverlayPoints, floor);
        }
    }

    private void displayOverlayFromList(List<OverlayData> overlayDataList, String floor) {
        for (OverlayData overlayData : overlayDataList) {
            if (overlayData.floor.equals(floor)) {
                displayGroundOverlay(overlayData);
                isOverlayVisible = true;
                break; // Exit loop after displaying overlay
            }
        }
    }

    // Helper method to check if a location is close to the bounds of a building
    public boolean isCloseToBuildingBounds(LatLng location, LatLngBounds bounds, double threshold) {
        LatLng nearestPoint = MapUtils.getNearestPointOnBounds(location, bounds);
        double distance = MapUtils.distanceBetweenPoints(location, nearestPoint);
        return distance <= threshold;
    }

    // Public method to get nucleusBounds
    public LatLngBounds getNucleusBounds() {
        return nucleusBounds;
    }

    // Public method to get libraryBounds
    public LatLngBounds getLibraryBounds() {
        return libraryBounds;
    }

    public void hideAllOverlays() {
        for (OverlayData overlayData : overlayPoints) {
            if (overlayData.groundOverlay != null) {
                overlayData.groundOverlay.remove();
                overlayData.groundOverlay = null;
            }
        }
        isOverlayVisible = false;
    }


    public void removeOverlaysOutsideBounds(LatLng userLocation) {
        boolean isNearNucleus = isCloseToBuildingBounds(userLocation, nucleusBounds, proximityThreshold);
        boolean isNearLibrary = isCloseToBuildingBounds(userLocation, libraryBounds, proximityThreshold);

        if (!isNearNucleus && !isNearLibrary) {
            // User is not near any building, remove all overlays
            for (OverlayData overlayData : overlayPoints) {
                if (overlayData.groundOverlay != null) {
                    overlayData.groundOverlay.remove();
                    overlayData.groundOverlay = null;
                }
            }
        }
    }

    private void displayGroundOverlay(OverlayData overlayData) {
        if (mMap != null && overlayData.imageDescriptor != null) {
            GroundOverlayOptions overlayOptions = new GroundOverlayOptions()
                    .image(overlayData.imageDescriptor)
                    .positionFromBounds(overlayData.bounds)
                    .transparency(0.5f);
            overlayData.groundOverlay = mMap.addGroundOverlay(overlayOptions);
        }
    }

    private class OverlayData {
        private LatLngBounds bounds;
        private BitmapDescriptor imageDescriptor;
        private GroundOverlay groundOverlay;
        private int imageResId;
        private String floor;

        OverlayData(LatLngBounds bounds, int imageResId, String floor) {
            this.bounds = bounds;
            this.imageResId = imageResId;
            this.imageDescriptor = BitmapDescriptorFactory.fromResource(imageResId);
            this.floor = floor;
        }
    }
}