package com.example.accidentdetection.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.accidentdetection.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NearbyHelpActivity extends AppCompatActivity {

    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final int RADIUS_METERS = 5000;
    private static final int COLOR_HOSPITAL = Color.parseColor("#D32F2F");
    private static final int COLOR_POLICE = Color.parseColor("#1976D2");
    private static final int COLOR_ME = Color.parseColor("#2E7D32");

    private static class Place {
        String name, type, phone;
        double lat, lon;
        float distance;
    }

    private MapView map;
    private FusedLocationProviderClient fused;
    private GeoPoint me;
    private Marker meMarker;
    private final List<Place> places = new ArrayList<>();
    private final List<Marker> placeMarkers = new ArrayList<>();
    private final OkHttpClient client = new OkHttpClient();
    private String filter = "all";

    private View cardPlace;
    private TextView tvStatus, tvPlaceName, tvPlaceInfo;
    private MaterialButton btnCall, btnNavigate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // osmdroid needs a user agent and a place for its tile cache
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_nearby);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });

        fused = LocationServices.getFusedLocationProviderClient(this);

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);

        tvStatus = findViewById(R.id.tvStatus);
        cardPlace = findViewById(R.id.cardPlace);
        tvPlaceName = findViewById(R.id.tvPlaceName);
        tvPlaceInfo = findViewById(R.id.tvPlaceInfo);
        btnCall = findViewById(R.id.btnCall);
        btnNavigate = findViewById(R.id.btnNavigate);

        ChipGroup chips = findViewById(R.id.chipGroup);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            filter = id == R.id.chipHospital ? "hospital"
                    : id == R.id.chipPolice ? "police" : "all";
            showMarkers();
        });

        findViewById(R.id.btnCall112).setOnClickListener(v -> dial("112"));
        findViewById(R.id.fabLocate).setOnClickListener(v -> locate());

        locate();
    }

    // ---------- Location ----------

    @SuppressLint("MissingPermission")
    private void locate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            tvStatus.setText("Location permission is required.");
            Toast.makeText(this, "Grant location permission first", Toast.LENGTH_LONG).show();
            return;
        }

        tvStatus.setText("Finding your location...");
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        onLocation(loc);
                    } else {
                        fused.getLastLocation().addOnSuccessListener(last -> {
                            if (last != null) onLocation(last);
                            else tvStatus.setText("Could not get location. Turn on GPS and tap the location button.");
                        });
                    }
                })
                .addOnFailureListener(e ->
                        tvStatus.setText("Could not get location. Turn on GPS and try again."));
    }

    private void onLocation(Location loc) {
        me = new GeoPoint(loc.getLatitude(), loc.getLongitude());

        if (meMarker == null) {
            meMarker = new Marker(map);
            meMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            meMarker.setTitle("You are here");
            meMarker.setIcon(tintedPin(COLOR_ME));
            map.getOverlays().add(meMarker);
        }
        meMarker.setPosition(me);
        map.getController().animateTo(me);
        map.invalidate();

        fetchPlaces();
    }

    // ---------- Hospitals and police (OpenStreetMap / Overpass) ----------

    private void fetchPlaces() {
        tvStatus.setText("Searching hospitals and police stations nearby...");

        String query = String.format(Locale.US,
                "[out:json][timeout:25];"
                        + "nwr[\"amenity\"~\"^(hospital|police)$\"](around:%d,%.6f,%.6f);"
                        + "out center 60;",
                RADIUS_METERS, me.getLatitude(), me.getLongitude());

        Request request = new Request.Builder()
                .url(OVERPASS_URL)
                .post(new FormBody.Builder().add("data", query).build())
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> tvStatus.setText(
                        "No internet connection. Use CALL 112 for emergencies."));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        runOnUiThread(() -> tvStatus.setText(
                                "Search service is busy. Try again in a moment."));
                        return;
                    }
                    List<Place> result = parse(r.body().string());
                    runOnUiThread(() -> {
                        places.clear();
                        places.addAll(result);
                        showMarkers();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> tvStatus.setText("Could not read search results."));
                }
            }
        });
    }

    private List<Place> parse(String json) {
        List<Place> list = new ArrayList<>();
        JsonArray elements = JsonParser.parseString(json)
                .getAsJsonObject().getAsJsonArray("elements");

        for (JsonElement el : elements) {
            JsonObject o = el.getAsJsonObject();
            double lat, lon;
            if (o.has("lat")) {                       // a point
                lat = o.get("lat").getAsDouble();
                lon = o.get("lon").getAsDouble();
            } else if (o.has("center")) {             // a building outline
                JsonObject c = o.getAsJsonObject("center");
                lat = c.get("lat").getAsDouble();
                lon = c.get("lon").getAsDouble();
            } else {
                continue;
            }

            JsonObject tags = o.has("tags") ? o.getAsJsonObject("tags") : new JsonObject();
            String amenity = tags.has("amenity") ? tags.get("amenity").getAsString() : "";

            Place p = new Place();
            p.type = amenity.equals("police") ? "Police station" : "Hospital";
            p.name = tags.has("name") ? tags.get("name").getAsString()
                    : "Unnamed " + p.type.toLowerCase(Locale.ROOT);
            String phone = tags.has("phone") ? tags.get("phone").getAsString()
                    : tags.has("contact:phone") ? tags.get("contact:phone").getAsString() : null;
            p.phone = phone == null ? null : phone.split(";")[0].trim();
            p.lat = lat;
            p.lon = lon;

            float[] res = new float[1];
            Location.distanceBetween(me.getLatitude(), me.getLongitude(), lat, lon, res);
            p.distance = res[0];
            list.add(p);
        }
        Collections.sort(list, (a, b) -> Float.compare(a.distance, b.distance));
        return list;
    }

    private void showMarkers() {
        for (Marker m : placeMarkers) map.getOverlays().remove(m);
        placeMarkers.clear();

        Place nearest = null;
        int count = 0;
        for (Place p : places) {
            boolean isPolice = p.type.equals("Police station");
            if (filter.equals("hospital") && isPolice) continue;
            if (filter.equals("police") && !isPolice) continue;

            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(p.lat, p.lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle(p.name);
            m.setIcon(tintedPin(isPolice ? COLOR_POLICE : COLOR_HOSPITAL));
            m.setOnMarkerClickListener((marker, mapView) -> {
                selectPlace(p);
                return true;
            });
            map.getOverlays().add(m);
            placeMarkers.add(m);

            if (nearest == null) nearest = p;     // list is sorted by distance
            count++;
        }
        map.invalidate();

        if (count == 0) {
            cardPlace.setVisibility(View.GONE);
            tvStatus.setText("Nothing found within " + (RADIUS_METERS / 1000)
                    + " km. Use CALL 112 for emergencies.");
        } else {
            tvStatus.setText(count + " places within " + (RADIUS_METERS / 1000)
                    + " km. Tap a pin for details.");
            selectPlace(nearest);
        }
    }

    // ---------- Selected place ----------

    private void selectPlace(Place p) {
        cardPlace.setVisibility(View.VISIBLE);
        tvPlaceName.setText(p.name);
        tvPlaceInfo.setText(p.type + "  •  " + formatDistance(p.distance));

        if (p.phone != null && !p.phone.isEmpty()) {
            btnCall.setVisibility(View.VISIBLE);
            btnCall.setOnClickListener(v -> dial(p.phone));
        } else {
            btnCall.setVisibility(View.GONE);   // no number in the map data
        }
        btnNavigate.setOnClickListener(v -> navigate(p));
    }

    private String formatDistance(float meters) {
        return meters < 1000
                ? String.format(Locale.US, "%d m away", (int) meters)
                : String.format(Locale.US, "%.1f km away", meters / 1000f);
    }

    private void dial(String number) {
        // ACTION_DIAL opens the dialer and needs no permission
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))));
    }

    private void navigate(Place p) {
        Uri uri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                p.lat, p.lon, p.lat, p.lon, Uri.encode(p.name)));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No maps app installed", Toast.LENGTH_SHORT).show();
        }
    }

    private Drawable tintedPin(int color) {
        Drawable d = DrawableCompat.wrap(
                ContextCompat.getDrawable(this, R.drawable.ic_pin).mutate());
        DrawableCompat.setTint(d, color);
        return d;
    }

    // ---------- Lifecycle (required by osmdroid) ----------

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        map.onDetach();
    }
}