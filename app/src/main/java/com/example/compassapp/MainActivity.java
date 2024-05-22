package com.example.compassapp;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private Sensor pressureSensor;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation;
    private float[] gravity;
    private float[] geomagnetic;
    private ImageView compassImageView;
    private TextView directionTV;
    private TextView latitudeTV, longitudeTV;
    private TextView headingTV, trueHeadingTV;
    private TextView cityNameTV, pressureTV, altitudeTV, magneticStrengthTV;
    private float currentDegree = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        compassImageView = findViewById(R.id.compassImageView);
        directionTV = findViewById(R.id.directionTV);
        latitudeTV = findViewById(R.id.latitudeTV);
        longitudeTV = findViewById(R.id.longitudeTV);
        headingTV = findViewById(R.id.headingTV);
        trueHeadingTV = findViewById(R.id.trueHeadingTV);
        cityNameTV = findViewById(R.id.cityTV);
        pressureTV = findViewById(R.id.pressureTV);
        altitudeTV = findViewById(R.id.altitudeTV);
        magneticStrengthTV = findViewById(R.id.magneticStrength);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            getLocation();
        }
    }

    private void getLocation() {
        LocationRequest locationRequest = new LocationRequest.Builder(10000).build();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, new com.google.android.gms.location.LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    currentLocation = location;
                    latitudeTV.setText(MessageFormat.format("Lat: {0}", location.getLatitude()));
                    longitudeTV.setText(MessageFormat.format("Long: {0}", location.getLongitude()));
                    getCityName(location);
                }
            }, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this, accelerometer);
        sensorManager.unregisterListener(this, magnetometer);
        sensorManager.unregisterListener(this, pressureSensor);
    }

    private float calculateAltitude(float pressure) {
        final float SEA_LEVEL_PRESSURE = 1013.25f;
        return (float) ((1 - Math.pow(pressure / SEA_LEVEL_PRESSURE, 0.190284)) * 145366.45 * 0.3048);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values;
            float magneticStrength = calculateMagneticStrength(geomagnetic);
            magneticStrengthTV.setText(MessageFormat.format("Magnetic Strength: {0} µT", magneticStrength));
        } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            float pressure = event.values[0];
            pressureTV.setText(MessageFormat.format("Atmospheric Pressure: {0} hPa", pressure));
            float altitude = calculateAltitude(pressure);
            altitudeTV.setText(MessageFormat.format("Altitude: {0} meters/{1} feet", altitude, altitude * 3.28084));
        }

        if (gravity != null && geomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                float azimuthInRadians = orientation[0];
                float azimuthInDegrees = (float) Math.toDegrees(azimuthInRadians);
                azimuthInDegrees = (azimuthInDegrees + 360) % 360;

                int degree = Math.round(azimuthInDegrees);

                float declination = 0;
                if (currentLocation != null) {
                    declination = getGeomagneticField(currentLocation).getDeclination();
                }
                int trueNorth = Math.round(degree + declination);

                RotateAnimation ra = new RotateAnimation(currentDegree, -degree, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

                ra.setDuration(210);
                ra.setFillAfter(true);

                compassImageView.startAnimation(ra);
                currentDegree = -degree;

                headingTV.setText(MessageFormat.format("{0}°", degree));
                trueHeadingTV.setText(MessageFormat.format("True HDG: {0}", trueNorth));
                directionTV.setText(getDirection(degree));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private String getDirection(float degree) {
        if (degree >= 22.5 && degree < 67.5) {
            return "NE";
        } else if (degree >= 67.5 && degree < 112.5) {
            return "E";
        } else if (degree >= 112.5 && degree < 157.5) {
            return "ES";
        } else if (degree >= 157.5 && degree < 202.5) {
            return "S";
        } else if (degree >= 202.5 && degree < 247.5) {
            return "SW";
        } else if (degree >= 247.5 && degree < 292.5) {
            return "W";
        } else if (degree >= 292.5 && degree < 337.5) {
            return "WN";
        } else {
            return "N";
        }
    }

    private void getCityName(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String cityName = addresses.get(0).getLocality();
                cityNameTV.setText(MessageFormat.format("{0}", cityName));
            } else {
                cityNameTV.setText("Couldn't find city");
            }
        } catch (IOException e) {
            e.printStackTrace();
            cityNameTV.setText("Couldn't find city");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLocation();
        }
    }

    private GeomagneticField getGeomagneticField(Location location) {
        return new GeomagneticField(
                (float) location.getLatitude(),
                (float) location.getLongitude(),
                (float) location.getAltitude(),
                System.currentTimeMillis()
        );
    }

    private float calculateMagneticStrength(float[] geomagnetic) {
        return (float) Math.sqrt(geomagnetic[0] * geomagnetic[0] + geomagnetic[1] * geomagnetic[1] + geomagnetic[2] * geomagnetic[2]);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitudeTV.setText(MessageFormat.format("Lat: {0}", location.getLatitude()));
        longitudeTV.setText(MessageFormat.format("Long: {0}", location.getLongitude()));
        currentLocation = location;
        getCityName(location);
    }
}