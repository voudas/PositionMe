package com.openpositioning.PositionMe.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.openpositioning.PositionMe.MapUtils;
import com.openpositioning.PositionMe.OverlayManager;
import com.openpositioning.PositionMe.PdrProcessing;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.ServerCommunications;
import com.openpositioning.PositionMe.sensors.LocationResponse;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.openpositioning.PositionMe.sensors.SensorTypes;
import com.openpositioning.PositionMe.sensors.WifiFPManager;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * A simple {@link Fragment} subclass. The recording fragment is displayed while the app is actively
 * saving data, with some UI elements indicating current PDR status.
 *
 * @see HomeFragment the previous fragment in the nav graph.
 * @see CorrectionFragment the next fragment in the nav graph.
 * @see SensorFusion the class containing sensors and recording.
 *
 * @author Mate Stodulka
 */
public class RecordingFragment extends Fragment {

    // UI components and handlers
    private Button stopButton, cancelButton, selectOverlayButton, resetToAutoButton, setMapType;
    private ImageView recIcon, compassIcon, elevatorIcon;
    private ImageButton rcntr,indoorMapAvailableIndicator;
    private ProgressBar timeRemaining;
    private TextView positionX, positionY, elevation, distanceTravelled, locationError;
    private Handler refreshDataHandler;
    private Handler locationUpdateHandler = new Handler();

    // Settings and sensor data
    private SharedPreferences settings;
    private SensorFusion sensorFusion;
    private CountDownTimer autoStop;

    private WifiFPManager wifiFPManager;
    private ServerCommunications serverCommunications;

    private boolean isManualSelection = false;

    // Data for trajectory and location
    private float distance, previousPosX, previousPosY;
    private float[] startingLocation, userLocation, gnssLocation;

    // Map and markers
    private GoogleMap mMap;
    private Polyline pdrPolyline;
    private Marker currentMarker, startingPositionMarker, gnssMarker;

    private OverlayManager overlayManager;
    private PdrProcessing pdrProcessing;


    private int currentFloor;
    double proximityThreshold = 10;

    //current floor tracking
    private String currentDisplayedFloor = "";
    double errorVal;

    private boolean isFirstUpdate = true;

    // Constants
    private static final float APPROX_METERS_PER_DEGREE_LATITUDE = 111000;

    /**
     * Public Constructor for the class.
     * Left empty as not required
     */
    public RecordingFragment() {
        // Required empty public constructor
    }

    /**
     * {@inheritDoc}
     * Gets an instance of the {@link SensorFusion} class, and initialises the context and settings.
     * Creates a handler for periodically updating the displayed data.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.sensorFusion = SensorFusion.getInstance();
        Context context = getActivity();
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.refreshDataHandler = new Handler();
        // Retrieve the starting location from SensorFusion
        this.startingLocation = sensorFusion.getGNSSLatitude(true);
        userLocation = startingLocation;
        this.wifiFPManager = WifiFPManager.getInstance();
        serverCommunications = new ServerCommunications(context);
    }

    /**
     * {@inheritDoc}
     * Set title in action bar to "Recording"
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_recording, container, false);
        // Inflate the layout for this fragment
        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
        getActivity().setTitle("Recording...");
        return rootView;
    }

    /**
     * {@inheritDoc}
     * Text Views and Icons initialised to display the current PDR to the user. A Button onClick
     * listener is enabled to detect when to go to next fragment and allow the user to correct PDR.
     * A runnable thread is called to update the UI every 0.5 seconds.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pdrProcessing = new PdrProcessing(getContext());
        locationUpdateHandler.post(locationUpdateTask);
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    mMap = googleMap;
                    overlayManager = new OverlayManager(mMap, getContext()); // Initialize OverlayManager
                    setupMapAndOverlays();
                }
            });
        }
        // Set autoStop to null for repeat recordings
        this.autoStop = null;

        //Initialise UI components
        this.positionX = getView().findViewById(R.id.currentXPos);
        this.positionY = getView().findViewById(R.id.currentYPos);
        this.elevation = getView().findViewById(R.id.currentElevation);
        this.distanceTravelled = getView().findViewById(R.id.currentDistanceTraveled);
        this.compassIcon = getView().findViewById(R.id.compass);
        this.elevatorIcon = getView().findViewById(R.id.elevatorImage);
        this.locationError = view.findViewById(R.id.locationError);

        //Set default text of TextViews to 0
        this.locationError.setText(getString(R.string.err, 0.0));
        this.positionX.setText(getString(R.string.x, "0"));
        this.positionY.setText(getString(R.string.y, "0"));
        this.positionY.setText(getString(R.string.elevation, "0"));
        this.distanceTravelled.setText(getString(R.string.meter, "0"));

        //Reset variables to 0
        this.distance = 0f;
        this.previousPosX = 0f;
        this.previousPosY = 0f;

        selectOverlayButton = view.findViewById(R.id.selectOverlayButton);
        selectOverlayButton.setVisibility(View.GONE);
        selectOverlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOverlaySelectionDialog();
            }
        });

        // Stop button to save trajectory and move to corrections
        this.stopButton = getView().findViewById(R.id.stopButton);
        this.stopButton.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * OnClick listener for button to go to next fragment.
             * When button clicked the PDR recording is stopped and the {@link CorrectionFragment} is loaded.
             */
            @Override
            public void onClick(View view) {
                if (autoStop != null) autoStop.cancel();
                sensorFusion.stopRecording();
                NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToCorrectionFragment();
                Navigation.findNavController(view).navigate(action);
            }
        });

        indoorMapAvailableIndicator = view.findViewById(R.id.indoorMapAvailableIndicator);
        indoorMapAvailableIndicator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LatLng userLatLng = new LatLng(userLocation[0], userLocation[1]);

                // Check if the user is near either of the buildings, instead of strictly within the bounds
                boolean isNearNucleus = overlayManager.isCloseToBuildingBounds(userLatLng, overlayManager.getNucleusBounds(), proximityThreshold);
                boolean isNearLibrary = overlayManager.isCloseToBuildingBounds(userLatLng, overlayManager.getLibraryBounds(), proximityThreshold);

                if (isNearNucleus || isNearLibrary) {
                    overlayManager.enableOverlays();
                    overlayManager.showOverlayForFloor("Ground Floor", userLatLng);
                    updateCurrentFloorBasedOnElevation();
                    selectOverlayButton.setVisibility(View.VISIBLE);
                    resetToAutoButton.setVisibility(View.VISIBLE);
                } else {
                    // If the user is not near any building, you might want to provide feedback or hide the overlays
                    overlayManager.disableOverlays();
                }
            }
        });

        setMapType = view.findViewById(R.id.setMapType);
        setMapType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if mMap is not null then call the map type selection dialog
                if (mMap != null) {
                    showMapTypeSelectionDialog();
                }
            }
        });

        resetToAutoButton = view.findViewById(R.id.resetToAutoButton);
        resetToAutoButton.setVisibility(View.GONE);
        resetToAutoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isManualSelection = false;
                isFirstUpdate = true;
                updateCurrentFloorBasedOnElevation(); // Refresh overlay based on current elevation
                Toast.makeText(getContext(), "Floor selection reset to automatic", Toast.LENGTH_SHORT).show();
            }
        });

        rcntr = view.findViewById(R.id.rcntr);
        rcntr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Get Current Location
                float[] latestPdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
                LatLng currentLocation = convertDisplacementToLatLng(startingLocation, latestPdrValues);

                // Center the map on the current location of the user
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 19f));
                }
            }
        });


        // Cancel button to discard trajectory and return to Home
        this.cancelButton = getView().findViewById(R.id.cancelButton);
        this.cancelButton.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * OnClick listener for button to go to home fragment.
             * When button clicked the PDR recording is stopped and the {@link HomeFragment} is loaded.
             * The trajectory is not saved.
             */
            @Override
            public void onClick(View view) {
                sensorFusion.stopRecording();
                NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToHomeFragment();
                Navigation.findNavController(view).navigate(action);
                if (autoStop != null) autoStop.cancel();
            }
        });

        // Display the progress of the recording when a max record length is set
        this.timeRemaining = getView().findViewById(R.id.timeRemainingBar);

        // Display a blinking red dot to show recording is in progress
        blinkingRecording();

        // Check if there is manually set time limit:
        if (this.settings.getBoolean("split_trajectory", false)) {
            // If that time limit has been reached:
            long limit = this.settings.getInt("split_duration", 30) * 60000L;
            // Set progress bar
            this.timeRemaining.setMax((int) (limit / 1000));
            this.timeRemaining.setScaleY(3f);

            // Create a CountDownTimer object to adhere to the time limit
            this.autoStop = new CountDownTimer(limit, 1000) {
                /**
                 * {@inheritDoc}
                 * Increment the progress bar to display progress and remaining time. Update the
                 * observed PDR values, and animate icons based on the data.
                 */
                @Override
                public void onTick(long l) {
                    // increment progress bar
                    timeRemaining.incrementProgressBy(1);
                    // Get new position
                    float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
                    positionX.setText(getString(R.string.x, String.format("%.1f", pdrValues[0])));
                    positionY.setText(getString(R.string.y, String.format("%.1f", pdrValues[1])));
                    // Calculate distance travelled
                    distance += Math.sqrt(Math.pow(pdrValues[0] - previousPosX, 2) + Math.pow(pdrValues[1] - previousPosY, 2));
                    distanceTravelled.setText(getString(R.string.meter, String.format("%.2f", distance)));
                    previousPosX = pdrValues[0];
                    previousPosY = pdrValues[1];
                    // Display elevation and elevator icon when necessary
                    float elevationVal = sensorFusion.getElevation();
                    elevation.setText(getString(R.string.elevation, String.format("%.1f", elevationVal)));
                    if (sensorFusion.getElevator()) elevatorIcon.setVisibility(View.VISIBLE);
                    else elevatorIcon.setVisibility(View.GONE);

                    //Rotate compass image to heading angle
                    compassIcon.setRotation((float) -Math.toDegrees(sensorFusion.passOrientation()));
                }

                /**
                 * {@inheritDoc}
                 * Finish recording and move to the correction fragment.
                 *
                 * @see CorrectionFragment
                 */
                @Override
                public void onFinish() {
                    // Timer done, move to next fragment automatically - will stop recording
                    sensorFusion.stopRecording();
                    NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToCorrectionFragment();
                    Navigation.findNavController(view).navigate(action);
                }
            }.start();
        } else {
            // No time limit - use a repeating task to refresh UI.
            this.refreshDataHandler.post(refreshDataTask);
        }
    }

    /**
     * Runnable task used to refresh UI elements with live data.
     * Has to be run through a Handler object to be able to alter UI elements
     */
    private final Runnable refreshDataTask = new Runnable() {
        @Override
        public void run() {
            // Get new position
            float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
            positionX.setText(getString(R.string.x, String.format("%.1f", pdrValues[0])));
            positionY.setText(getString(R.string.y, String.format("%.1f", pdrValues[1])));
            // Calculate distance travelled
            distance += Math.sqrt(Math.pow(pdrValues[0] - previousPosX, 2) + Math.pow(pdrValues[1] - previousPosY, 2));
            distanceTravelled.setText(getString(R.string.meter, String.format("%.2f", distance)));
            previousPosX = pdrValues[0];
            previousPosY = pdrValues[1];
            // Display elevation and elevator icon when necessary
            float elevationVal = sensorFusion.getElevation();
            elevation.setText(getString(R.string.elevation, String.format("%.1f", elevationVal)));
            if (sensorFusion.getElevator()) elevatorIcon.setVisibility(View.VISIBLE);
            else elevatorIcon.setVisibility(View.GONE);

            //Rotate compass image to heading angle
            compassIcon.setRotation((float) -Math.toDegrees(sensorFusion.passOrientation()));

            // Loop the task again to keep refreshing the data
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
    };
    private void setupMapAndOverlays() {
        pdrPolyline = mMap.addPolyline(new PolylineOptions().width(9).color(Color.BLUE));
        int mapType = settings.getInt("mapType", GoogleMap.MAP_TYPE_NORMAL); // Default to normal
        MapUtils.switchMapType(mMap, mapType);
        if (startingLocation != null && startingLocation.length == 2 && startingLocation[0] != 0 && startingLocation[1] != 0) {
            LatLng currentLocation = new LatLng(startingLocation[0], startingLocation[1]);
            startingPositionMarker = mMap.addMarker(new MarkerOptions()
                    .position(currentLocation)
                    .icon(BitmapDescriptorFactory
                            .fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_blue))));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 19f));
            updateFloorOverlay(pdrProcessing.getCurrentFloor());
        }
    }

    /**
     * Displays a blinking red dot to signify an ongoing recording.
     *
     * @see Animation for makin the red dot blink.
     */
    private void blinkingRecording() {
        //Initialise Image View
        this.recIcon = getView().findViewById(R.id.redDot);
        //Configure blinking animation
        Animation blinking_rec = new AlphaAnimation(1, 0);
        blinking_rec.setDuration(800);
        blinking_rec.setInterpolator(new LinearInterpolator());
        blinking_rec.setRepeatCount(Animation.INFINITE);
        blinking_rec.setRepeatMode(Animation.REVERSE);
        recIcon.startAnimation(blinking_rec);
    }

    /**
     * {@inheritDoc}
     * Stops ongoing refresh task, but not the countdown timer which stops automatically
     */
    @Override
    public void onPause() {
        refreshDataHandler.removeCallbacks(refreshDataTask);
        super.onPause();
        locationUpdateHandler.removeCallbacks(locationUpdateTask);
    }

    /**
     * {@inheritDoc}
     * Restarts UI refreshing task when no countdown task is in progress
     */
    @Override
    public void onResume() {
        if (!this.settings.getBoolean("split_trajectory", false)) {
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
        super.onResume();
        locationUpdateHandler.post(locationUpdateTask);
    }

    private void showMapTypeSelectionDialog() {
        final CharSequence[] mapTypes = {"Normal", "Satellite", "Terrain", "Hybrid"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Choose map type");
        builder.setItems(mapTypes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt("mapType", which);
                editor.apply();
                MapUtils.switchMapType(mMap, which); // Update the map type
            }
        });
        builder.show();
    }

    private Bitmap getBitmapFromVector(Context context, int vectorResId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);

        if (vectorDrawable == null) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);

        return bitmap;
    }

    private final Runnable locationUpdateTask = new Runnable() {
        @Override
        public void run() {
            // Retrieve the latest PDR coordinates and orientation
            float[] pdrDisplacement = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
            float azimuthInRadians = sensorFusion.passOrientation();
            float azimuthInDegrees = (float) Math.toDegrees(azimuthInRadians);
            gnssLocation = sensorFusion.getGNSSLatitude(false);
            LatLng gnssLocationLatlng = new LatLng(gnssLocation[0], gnssLocation[1]);


            if (pdrDisplacement != null && mMap != null) {
                // Adjust the azimuth based on the map's bearing
                float mapBearing = mMap.getCameraPosition().bearing; // Map's bearing in degrees
                float adjustedAzimuth = azimuthInDegrees - mapBearing;
                // Ensure the adjusted azimuth is within the range [0, 360)
                adjustedAzimuth = (adjustedAzimuth + 360) % 360;
                LatLng newLocation = convertDisplacementToLatLng(userLocation, pdrDisplacement);

                // Calculating the error between GNSS and PDR locations
                errorVal = calculateDistance(newLocation.latitude, newLocation.longitude, gnssLocationLatlng.latitude, gnssLocationLatlng.longitude);
                locationError.setText(getString(R.string.err, errorVal));

                // Check if the user is near either of the buildings
                boolean isNearNucleus = overlayManager.isCloseToBuildingBounds(newLocation, overlayManager.getNucleusBounds(), proximityThreshold);
                boolean isNearLibrary = overlayManager.isCloseToBuildingBounds(newLocation, overlayManager.getLibraryBounds(), proximityThreshold);

                if (isNearNucleus || isNearLibrary) {
                    indoorMapAvailableIndicator.setVisibility(View.VISIBLE); // Show the indicator if near any building
                } else {
                    indoorMapAvailableIndicator.setVisibility(View.GONE); // Hide the indicator if not near any building
                    selectOverlayButton.setVisibility(View.GONE); // Hide selectOverlayButton
                    resetToAutoButton.setVisibility(View.GONE); // Hide resetToAutoButton
                }


                if (newLocation != null) {
                    userLocation[0] = (float) newLocation.latitude;
                    userLocation[1] = (float) newLocation.longitude;
                }

                if (overlayManager != null) {
                    overlayManager.removeOverlaysOutsideBounds(newLocation);
                    if (!isManualSelection) {
                        updateCurrentFloorBasedOnElevation();
                    }
                }

                // Initialize the polyline if it's null
                if (pdrPolyline != null) {
                    List<LatLng> pdrPoints = pdrPolyline.getPoints();
                    pdrPoints.add(newLocation);
                    pdrPolyline.setPoints(pdrPoints);
                }
                //Remove starting location map
                if (startingPositionMarker != null) {
                    startingPositionMarker.remove();
                    startingPositionMarker = null;
                }


                // Update or move the marker without clearing the map
                if (currentMarker == null || gnssMarker == null) {
                    currentMarker = mMap.addMarker(new MarkerOptions()
                            .position(newLocation)
                            .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_blue)))
                            .rotation(adjustedAzimuth)
                            .anchor(0.5f, 0.5f));
                    gnssMarker = mMap.addMarker(new MarkerOptions()
                            .position(gnssLocationLatlng)
                            .icon(BitmapDescriptorFactory.fromBitmap(getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_red)))
                            .rotation(adjustedAzimuth)
                            .anchor(0.5f, 0.5f));
                } else {
                    currentMarker.setPosition(newLocation);
                    currentMarker.setRotation(adjustedAzimuth);
                    gnssMarker.setPosition(gnssLocationLatlng);
                    gnssMarker.setRotation(adjustedAzimuth);
                    fetchLocationAndAddMarker();
                }
            }

            // Schedule the next update
            locationUpdateHandler.postDelayed(this, 1000); // Update interval in milliseconds
        }
    };

    private void fetchLocationAndAddMarker() {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                String wifiFingerprintJson = wifiFPManager.createWifiFingerprintJson();
                LocationResponse locationResponse = serverCommunications.sendWifiFingerprintToServer(wifiFingerprintJson);

                getActivity().runOnUiThread(() -> {
                    if (locationResponse != null && mMap != null && !Double.isNaN(locationResponse.getLatitude()) && !Double.isNaN(locationResponse.getLongitude())) {
                        LatLng wifiLocation = new LatLng(locationResponse.getLatitude(), locationResponse.getLongitude());
                        mMap.addMarker(new MarkerOptions()
                                .position(wifiLocation)
                                .title("Wi-Fi Location") // You can also include the floor information if needed
                                .snippet("Floor: " + locationResponse.getFloor()) // Assuming floor is a string. If it's null, this will show "Floor: null"
                        );
                        // Optionally, move the camera
                        mMap.animateCamera(CameraUpdateFactory.newLatLng(wifiLocation));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                // Consider providing feedback to the user that an error occurred
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Error fetching location", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private LatLng convertDisplacementToLatLng(float[] location, float[] displacement) {
        // Calculate adjustment for longitude based on latitude
        float longitudeAdjustment = APPROX_METERS_PER_DEGREE_LATITUDE * (float) Math.cos(Math.toRadians(location[0]));
        //final float CORRECTIONFACTOR = 2;
        // Calculate the change in position based on the current and previous PDR values
        float deltaX = displacement[0] - previousPosX;
        float deltaY = displacement[1] - previousPosY;

        // Calculate new latitude and longitude by applying the displacement to the current location
        float newLatitude = location[0] + (deltaX / (/*CORRECTIONFACTOR **/APPROX_METERS_PER_DEGREE_LATITUDE));
        float newLongitude = location[1] + (deltaY / longitudeAdjustment);

        return new LatLng(newLatitude, newLongitude);
    }

    private void updateCurrentFloorBasedOnElevation() {
        int floorHeight = 4;
        // Get elevation from PdrProcessing
        float currentElevation = sensorFusion.getElevation();

        // Calculate the current floor, rounding down. Floor is zero-indexed.
        int calculatedFloor = (int) Math.floor(currentElevation / floorHeight);

        // Ensure the floor number does not fall below 0 (ground floor)
        calculatedFloor = Math.max(calculatedFloor, 0);

        // Limit the floor number to a maximum of 3
        calculatedFloor = Math.min(calculatedFloor, 3);

        // Update the floor overlay if this is the first update or if the calculated floor is different from the current floor
        if (isFirstUpdate || calculatedFloor != currentFloor) {
            currentFloor = calculatedFloor;
            updateFloorOverlay(currentFloor);
            isFirstUpdate = false; // Set flag to false after the first update
        }
    }


    private void showOverlaySelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select a Floor");

        final CharSequence[] options = {"Ground Floor", "Floor 1", "Floor 2", "Floor 3", "Disable Overlays"};
        builder.setItems(options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int floor) {
                LatLng userLatLng = new LatLng(userLocation[0], userLocation[1]);
                String floorName = "";

                switch (floor) {
                    case 0: floorName = "Ground Floor"; break;
                    case 1: floorName = "Floor 1"; break;
                    case 2: floorName = "Floor 2"; break;
                    case 3: floorName = "Floor 3"; break;
                    case 4: // Disable overlays
                        overlayManager.disableOverlays();
                        showToast("Overlays disabled");
                        return; // Exit the method to avoid showing any overlay
                }

                // Enable overlays and show the selected floor overlay
                overlayManager.enableOverlays();
                overlayManager.showOverlayForFloor(floorName, userLatLng);
                showToast("Switched to " + floorName);
                if (floor < 4) { // For specific floor selections
                    isManualSelection = true;
                } else { // For "Disable Overlays" option
                    isManualSelection = false;
                }

            }
        });
        builder.show();
        isManualSelection = true; // Set flag on manual selection
    }

    private void updateFloorOverlay(int currentFloor) {
        LatLng userLatLng = new LatLng(userLocation[0], userLocation[1]);
        if (overlayManager != null) {
            String floorName = "";
            switch (currentFloor) {
                case 0: floorName = "Ground Floor"; break;
                case 1: floorName = "Floor 1"; break;
                case 2: floorName = "Floor 2"; break;
                case 3: floorName = "Floor 3"; break;
            }

            // Check if the current floor is different from the last displayed floor
            if (!floorName.equals(currentDisplayedFloor)) {
                boolean isNearNucleus = overlayManager.isCloseToBuildingBounds(userLatLng, overlayManager.getNucleusBounds(), proximityThreshold);
                boolean isNearLibrary = overlayManager.isCloseToBuildingBounds(userLatLng, overlayManager.getLibraryBounds(), proximityThreshold);

                if (isNearNucleus || isNearLibrary) {
                    overlayManager.showOverlayForFloor(floorName, userLatLng);
                    showToast("Switched to " + floorName);
                    currentDisplayedFloor = floorName;
                } else {
                    overlayManager.hideAllOverlays();
                    currentDisplayedFloor = ""; // Reset current floor as overlays are hidden
                }
            }
        }
    }

    // Method to show a toast
    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    //Method to calculate the distance error between pdr and gnss
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in kilometers
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        return distance;
    }
}

