package com.openpositioning.PositionMe.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.openpositioning.PositionMe.MapUtils;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.sensors.SensorFusion;


public class StartLocationFragment extends Fragment {

    private Button button;
    private SensorFusion sensorFusion = SensorFusion.getInstance();
    private LatLng position;
    private float[] startPosition = new float[2];
    private float zoom = 19f;

    private GoogleMap mMap;

    public StartLocationFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_startlocation, container, false);
        ((AppCompatActivity)getActivity()).getSupportActionBar().hide();

        startPosition = sensorFusion.getGNSSLatitude(false);
        if(startPosition[0] == 0 && startPosition[1] == 0) {
            zoom = 1f;
        }

        SupportMapFragment supportMapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.startMap);
        supportMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
                configureMap();
            }
        });

        return rootView;
    }

    private void configureMap() {
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);
        mMap.getUiSettings().setScrollGesturesEnabled(true);

        position = new LatLng(startPosition[0], startPosition[1]);
        mMap.addMarker(new MarkerOptions().position(position).title("Start Position")).setDraggable(true);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {}

            @Override
            public void onMarkerDragEnd(Marker marker) {
                startPosition[0] = (float) marker.getPosition().latitude;
                startPosition[1] = (float) marker.getPosition().longitude;
            }

            @Override
            public void onMarkerDrag(Marker marker) {}
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        this.button = view.findViewById(R.id.startLocationDone);
        this.button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sensorFusion.startRecording();
                sensorFusion.setStartGNSSLatitude(startPosition);
                NavDirections action = StartLocationFragmentDirections.actionStartLocationFragmentToRecordingFragment();
                Navigation.findNavController(view).navigate(action);
            }
        });

        Button switchMapTypeButton = view.findViewById(R.id.switchMapTypeButton);
        switchMapTypeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMapTypeSelectionDialog();
            }
        });
    }

    private void showMapTypeSelectionDialog() {
        final CharSequence[] mapTypes = {"Normal", "Satellite", "Terrain", "Hybrid"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Choose map type");
        builder.setItems(mapTypes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Context context = getActivity();
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt("mapType", which);
                editor.apply();
                MapUtils.switchMapType(mMap, which); // Update the map type
            }
        });
        builder.show();
    }
}
