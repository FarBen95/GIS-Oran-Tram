package com.farouk.sig;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionError;
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;
import com.mapbox.mapboxsdk.style.functions.Function;
import com.mapbox.mapboxsdk.style.functions.stops.Stop;
import com.mapbox.mapboxsdk.style.functions.stops.Stops;
import com.mapbox.mapboxsdk.style.layers.Filter;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.commons.geojson.Feature;
import com.mapbox.services.commons.geojson.FeatureCollection;
import com.mapbox.services.commons.models.Position;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, MapboxMap.OnMapClickListener, TabLayout.OnTabSelectedListener, AdapterView.OnItemClickListener {

    public static final String TAG = "SimpOfflineMapActivity";
    public static final String PROPERTY_SELECTED = "selected";
    public static final String INFO_LAYER_ID = "info_layer";
    public static final String PROPERTY_TITLE = "Name";
    public static final String CIRCLE_IMAGE = "circle_image";
    public static final String DESTINATION_IMAGE = "destination_image";
    public static final String SOURCE_IMAGE = "source_image";

    public static MapView mapView;
    public static MapboxMap mapboxMap;
    public static OfflineManager offlineManager;
    public FeatureCollection mainFeatureCollection;
    public GeoJsonSource geoJsonSource;

    public static MarkerOptions sourceMarker;
    public static MarkerOptions destinationMarker;

    private boolean sourceSelected = false;
    private boolean destinationSelected = false;
    private List<Feature> extremities = Arrays.asList(new Feature[2]);


    LinearLayout navigationWindow, distanceTime;
    TextInputLayout sourceWrapper;
    TextInputLayout destinationWrapper;
    AutoCompleteTextView sourceField;
    AutoCompleteTextView destinationField;
    String[] amounts;
    ArrayAdapter<String> adapter;
    AppBarLayout appBarLayout;
    TextView distance;
    TextView time;

    private TabLayout tabLayout;
    private int[] tabIcons = {
            R.drawable.view_icon_selector,
            R.drawable.navigation_icon_selector,
    };

    private boolean isEndNotified;
    private static ProgressBar progressBar;

    // JSON encoding/decoding
    public static final String JSON_CHARSET = "UTF-8";
    public static final String JSON_FIELD_REGION_NAME = "FIELD_REGION_NAME";
    private static final String GEOJSON_SOURCE_ID = "ORAN_TRAM";
    private static final String STATIONS_LAYER_ID = "stations-layer";
    private static final String CIRCLES_LAYER_ID = "circles-layer";

    private int mapState = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mapbox Access token
        Mapbox.getInstance(getApplicationContext(), getResources().getString(R.string.access_token));

        setContentView(R.layout.activity_main);

        Toolbar appbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(appbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        //asyncMap(this);
        mapView.getMapAsync(this);

        appBarLayout = (AppBarLayout) findViewById(R.id.appbar_layout);

        tabLayout = (TabLayout) findViewById(R.id.sliding_tabs);
        //tabLayout.setupWithViewPager(viewPager);
        setupTabIcons();
        tabLayout.addOnTabSelectedListener(this);

        navigationWindow = (LinearLayout) findViewById(R.id.navigation_window);
        sourceWrapper = (TextInputLayout) findViewById(R.id.source_field_layout);
        destinationWrapper = (TextInputLayout) findViewById(R.id.destination_field_layout);
        sourceField = (AutoCompleteTextView) findViewById(R.id.source_field_text);
        destinationField = (AutoCompleteTextView) findViewById(R.id.destination_field_text);

        distanceTime = (LinearLayout) findViewById(R.id.distance_time_layout);
        distance = (TextView) findViewById(R.id.distance_text);
        time = (TextView) findViewById(R.id.time_text);
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        if (tab.getPosition() == 0) {
            hideStations(false);
            hideRail(false);
            hideCircles(true);
            mapState = 0;
            navigationWindow.setVisibility(View.GONE);
            mapboxMap.clear();
            deselectAll();
            refreshSource();
            distanceTime.setVisibility(View.GONE);
        }
        if (tab.getPosition() == 1) {
            hideStations(true);
            hideRail(true);
            hideCircles(false);
            mapState = 1;
            navigationWindow.setVisibility(View.VISIBLE);
            if (amounts == null) {
                setupAutoFields();
            }
            mapboxMap.clear();
            sourceSelected = destinationSelected = false;
            sourceField.setText("");
            destinationField.setText("");
            sourceField.requestFocus();
            deselectAll();
            refreshSource();
            setupNavigationMarkers();


        }
    }


    private void setupAutoFields() {
        /*final GeoJsonSource tramSource = mapboxMap.getSourceAs(GEOJSON_SOURCE_ID);
        Log.d("sourceList", tramSource.toString());
        List<Feature> stationsList = tramSource.querySourceFeatures(Filter.eq("$id", "2a4f854726f0494381b7fdf9b333a0a3"));
        Log.d("source", stationsList.toString()+ "\t" + String.valueOf(stationsList.size()));*/
        amounts = new String[mainFeatureCollection.getFeatures().size() - 1];
        //Log.d("names", featureList.toString());
        int i = 0;
        for (Feature f : mainFeatureCollection.getFeatures()) {
            Log.d("SelectedLayerPoints", f.getGeometry().toString() + "\t" + f.getStringProperty("Name"));
            if (f.getGeometry().getType().equals("Point"))
                amounts[i++] = f.getStringProperty("Name");
        }
        Log.d("lastname", String.valueOf(amounts.length));
        i = 1;
        for (String s : amounts) {
            Log.d("names", s + "\t" + String.valueOf(i++));
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, amounts);
        Log.d("adapter", adapter.toString());
        //Set the number of characters the user must type before the drop down list is shown
        sourceField.setThreshold(1);
        destinationField.setThreshold(1);
        //Set the adapter
        sourceField.setAdapter(adapter);
        destinationField.setAdapter(adapter);

        adapter.notifyDataSetChanged();

        sourceField.setOnItemClickListener(this);
        destinationField.setOnItemClickListener(this);

        sourceField.setDropDownBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.iron)));
        destinationField.setDropDownBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.iron)));
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }

    private void hideRail(boolean hide) {
        LineLayer layer = (LineLayer) mapboxMap.getLayer("rail-layer");
        if (layer != null) {
            if (hide) {
                layer.setProperties(PropertyFactory.visibility(NONE));
            } else {
                layer.setProperties(PropertyFactory.visibility(VISIBLE));
            }
        }
    }

    private void hideStations(boolean hide) {
        SymbolLayer layer = (SymbolLayer) mapboxMap.getLayer(STATIONS_LAYER_ID);
        if (layer != null) {
            if (hide) {
                layer.setProperties(PropertyFactory.visibility(NONE));
            } else {
                layer.setProperties(PropertyFactory.visibility(VISIBLE));
            }
        }
    }

    public void hideCircles(boolean hide) {
        SymbolLayer layer = (SymbolLayer) mapboxMap.getLayer(CIRCLES_LAYER_ID);
        if (layer != null) {
            if (hide) {
                layer.setProperties(PropertyFactory.visibility(NONE));
            } else {
                layer.setProperties(PropertyFactory.visibility(VISIBLE));
            }
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
        outState.putInt("POSITION", tabLayout.getSelectedTabPosition());
    }

    private void setupTabIcons() {
        Log.d("icons", tabIcons[0] + "\t" + tabIcons[1] + "\n");
        tabLayout.addTab(tabLayout.newTab().setIcon(tabIcons[0]));
        tabLayout.addTab(tabLayout.newTab().setIcon(tabIcons[1]));
    }

    //public void asyncMap(final Context context) {
    //mapView.getMapAsync(new OnMapReadyCallback() {
    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        // Set up the OfflineManager
        offlineManager = OfflineManager.getInstance(this);

        // Create a bounding box for the offline region
        LatLngBounds latLngBounds = new LatLngBounds.Builder()
                .include(new LatLng(35.736263, -0.521185)) // Northeast
                .include(new LatLng(35.630547, -0.708225)) // Southwest
                .build();

        // Define the offline region
        OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                mapboxMap.getStyleUrl(),
                latLngBounds,
                10,
                20,
                this.getResources().getDisplayMetrics().density);

        // Set the metadata
        byte[] metadata;
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(JSON_FIELD_REGION_NAME, "Oran_tram");
            String json = jsonObject.toString();
            metadata = json.getBytes(JSON_CHARSET);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to encode metadata: " + exception.getMessage());
            metadata = null;
        }

        // Create the region asynchronously
        offlineManager.createOfflineRegion(
                definition,
                metadata,
                new OfflineManager.CreateOfflineRegionCallback() {
                    @Override
                    public void onCreate(OfflineRegion offlineRegion) {
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);

                        // Display the download progress bar
                        //progressBar = (ProgressBar) findViewById(R.id.progress_bar);
                        //startProgress();

                        // Monitor the download progress using setObserver
                        offlineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {
                            @Override
                            public void onStatusChanged(OfflineRegionStatus status) {

                                // Calculate the download percentage and update the progress bar
                                double percentage = status.getRequiredResourceCount() >= 0
                                        ? (100.0 * status.getCompletedResourceCount() / status.getRequiredResourceCount()) :
                                        0.0;

                                if (status.isComplete()) {
                                    // Download complete
                                    //endProgress(getString(R.string.simple_offline_end_progress_success));
                                } else if (status.isRequiredResourceCountPrecise()) {
                                    // Switch to determinate state
                                    //setPercentage((int) Math.round(percentage));
                                }
                            }

                            @Override
                            public void onError(OfflineRegionError error) {
                                // If an error occurs, print to logcat
                                Log.e(TAG, "onError reason: " + error.getReason());
                                Log.e(TAG, "onError message: " + error.getMessage());
                            }

                            @Override
                            public void mapboxTileCountLimitExceeded(long limit) {
                                // Notify if offline region exceeds maximum tile count
                                Log.e(TAG, "Mapbox tile count limit exceeded: " + limit);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error: " + error);
                    }
                });

        //new DrawGeoJson(context).execute();
        createGeoJsonSource();
        addPolylineLayer();
        addPointsLayer();
        setupCirclesLayer();
        new GenerateViewIconTask(this).execute(mainFeatureCollection);

        setupInfoWindowsLayer();
        //addSelectedPolylineLayer();
        //addSelectedPointsLayer();
        mapboxMap.setOnMapClickListener(this);

    }

    private void setupNavigationMarkers() {
        Icon icon;

        ImageView source = new ImageView(this);
        source.setImageDrawable(getResources().getDrawable(R.drawable.ic_source_marker));
        icon = IconFactory.getInstance(this).fromBitmap(generate(source));
        sourceMarker = new MarkerOptions().icon(icon);

        ImageView destination = new ImageView(this);
        destination.setImageDrawable(getResources().getDrawable(R.drawable.ic_destination_flag_marker));
        icon = IconFactory.getInstance(this).fromBitmap(generate(destination));
        destinationMarker = new MarkerOptions().icon(icon);


    }


    private void startProgress() {

        // Start and show the progress bar
        isEndNotified = false;
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void setPercentage(final int percentage) {
        progressBar.setIndeterminate(false);
        progressBar.setProgress(percentage);
    }

    private void endProgress(final String message) {
        // Don't notify more than once
        if (isEndNotified) {
            return;
        }

        // Stop and hide the progress bar
        isEndNotified = true;
        progressBar.setIndeterminate(false);
        progressBar.setVisibility(View.GONE);

        // Show a toast
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void createGeoJsonSource() {
        // Load data from GeoJSON file in the assets folder
        String json = loadJsonFromAsset("Oran_tram.geojson");
        mainFeatureCollection = FeatureCollection.fromJson(json);
        Log.i("addgeojson", mainFeatureCollection.toJson());
        geoJsonSource = new GeoJsonSource(GEOJSON_SOURCE_ID,
                mainFeatureCollection);
        Log.i("sourcegeojson", geoJsonSource.getAttribution());
        mapboxMap.addSource(geoJsonSource);
    }

    private void addPolylineLayer() {
        // Create and style a LineLayer that uses the LineString Feature's coordinates in the GeoJSON data
        LineLayer railLayer = new LineLayer("rail-layer", GEOJSON_SOURCE_ID);
        railLayer.setProperties(PropertyFactory.lineColor(getResources().getColor(R.color.purple)),
                PropertyFactory.lineWidth(4f));
        railLayer.setFilter(Filter.eq("$type", "LineString"));
        mapboxMap.addLayer(railLayer);
    }

    private void addPointsLayer() {
        // Create and style a SymbolLayer that uses the Point Features' coordinates in the GeoJSON data
        ImageView drawable = new ImageView(this);
        drawable.setImageDrawable(getResources().getDrawable(R.drawable.ic_station_marker));
        Bitmap icon = generate(drawable);
        mapboxMap.addImage("marker-image", icon);
        Float[] offset = {0f, -17f};
        SymbolLayer stationsLayer = new SymbolLayer(STATIONS_LAYER_ID, GEOJSON_SOURCE_ID).withFilter(Filter.eq("$type", "Point"))
                .withProperties(PropertyFactory.iconImage("marker-image"),
                        PropertyFactory.iconOffset(offset),
                        PropertyFactory.iconAllowOverlap(true));
        mapboxMap.addLayer(stationsLayer);
    }

    private void setupCirclesLayer() {
        Bitmap icon;
        ImageView drawable = new ImageView(this);

        drawable.setImageDrawable(getResources().getDrawable(R.drawable.circle_station));
        icon = generate(drawable);
        mapboxMap.addImage(CIRCLE_IMAGE, icon);

        SymbolLayer circleStations = new SymbolLayer(CIRCLES_LAYER_ID, GEOJSON_SOURCE_ID)
                .withProperties(PropertyFactory.iconImage(Function.property("selected", Stops.categorical(
                        Stop.stop(true, PropertyFactory.iconImage(SOURCE_IMAGE)),
                        Stop.stop(false, PropertyFactory.iconImage(CIRCLE_IMAGE))))),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.visibility(NONE))
                .withFilter(Filter.eq("$type", "Point"));
        mapboxMap.addLayer(circleStations);

    }

    private void addSelectedPolylineLayer() {
        FeatureCollection emptySource = FeatureCollection.fromFeatures(new Feature[]{});
        GeoJsonSource selectedRailSource = new GeoJsonSource("selected-rail", emptySource);
        mapboxMap.addSource(selectedRailSource);

        LineLayer selectedRail = new LineLayer("selected-rail-layer", "selected-rail");
        mapboxMap.addLayer(selectedRail);

    }

    public void setupInfoWindowsLayer() {
        mapboxMap.addLayer(new SymbolLayer(INFO_LAYER_ID, GEOJSON_SOURCE_ID)
                        .withProperties(
        /* show image with id title based on the value of the title feature property */
                                PropertyFactory.iconImage("{Name}"),

        /* set anchor of icon to bottom-left */
                                PropertyFactory.iconAnchor("bottom-left"),

        /* offset icon slightly to match bubble layout */
                                PropertyFactory.iconOffset(new Float[]{-20.0f, -10.0f})
                        )

      /* add a filter to show only when selected feature property is true */
                        .withFilter(Filter.eq(PROPERTY_SELECTED, true))
        );
    }

    @Nullable
    private String loadJsonFromAsset(String filename) {
        try {
            InputStream is = getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public void onMapClick(@NonNull LatLng point) {
        if (mapState == 0) {
            touchStationView(point);
        } else if (mapState == 1) {
            touchStationNavigation(point);
        }

    }

    private void touchStationNavigation(LatLng point) {
        Log.i("navigationTouch", "i am here");
        //final SymbolLayer marker = (SymbolLayer) mapboxMap.getLayer("selected-stations-layer");
        //final LineLayer track = (LineLayer) mapboxMap.getLayer("selected-rail-layer");
        //final GeoJsonSource selectedStationsSource = mapboxMap.getSourceAs("selected-stations");
        //List<Feature> selectedStations = selectedStationsSource.querySourceFeatures(Filter.eq("$type", "Point"));

        final PointF pixel = mapboxMap.getProjection().toScreenLocation(point);
        List<Feature> features = mapboxMap.queryRenderedFeatures(pixel, CIRCLES_LAYER_ID);
        //List<Feature> selectedFeature = mapboxMap.queryRenderedFeatures(pixel, "selected-stations-layer");
        /*boolean exist = false;
        //if(!selectedFeature.isEmpty())
        //    Log.d("selectedFeatures", selectedFeature.get(0).getGeometry().toString());
        //if (!features.isEmpty())
            Log.d("touchedFeatures", features.get(0).getGeometry().toString());
        if (!extremities.isEmpty()) {
            for (Feature ll : extremities) {
                try {
                    Log.d("selectedStations", ll.getGeometry().toString());
                } catch (Exception e) {
                    Log.e("extremities", e.getMessage());
                }

            }
        }*/

        if (features.isEmpty()) {
            /*Log.d("if", "1");
            if (!sourceSelected) {
                Log.d("if", "1.1");
                return;
            }
            if (!destinationSelected) {
                Log.d("if", "1.2");
                //Toast
                return;
            }
            //deselectMarker(marker);
            sourceSelected = false;
            destinationSelected = false;
            //FeatureCollection featureCollection = FeatureCollection.fromFeatures(
            //        new Feature[]{});
            //setSource("selected-stations", featureCollection);
            extremities.clear();
            mapboxMap.clear();*/

            Toast.makeText(this, R.string.invalide_marker,
                    Toast.LENGTH_LONG).show();

        } else {
            /*Log.d("else", "1");
            Log.d("sourceSelected", String.valueOf(sourceSelected));
            if (!sourceSelected) {
                Log.d("else-if", "1.1");
                //FeatureCollection featureCollection = FeatureCollection.fromFeatures(
                //        new Feature[]{Feature.fromGeometry(features.get(0).getGeometry())});
                //Log.d("featureCollection", featureCollection.getFeatures().get(0).getGeometry().toString());
                //setSource("selected-stations", featureCollection);
                //selectMarker(marker);
                extremities.add(0, features.get(0));
                sourceField.setText(extremities.get(0).getStringProperty("Name"));
                sourceSelected = true;
                return;
            }
            for (Feature f : extremities) {
                try {
                    if (f.getId().equals(features.get(0).getId())) {
                        exist = true;
                        break;
                    }
                } catch (Exception e) {
                    Log.e("extremities", e.getMessage());
                }
            }
            if (exist && destinationSelected) {
                Log.d("else-if", "1.2");
                return;
            }
            if (exist && !destinationSelected) {
                Log.d("else-if", "1.3");
                return;
            }
            if (destinationSelected) {
                Log.d("else-if", "1.4");
                //deselectMarker(marker);
                //FeatureCollection featureCollection = FeatureCollection.fromFeatures(
                //        new Feature[]{Feature.fromGeometry(selectedStations.get(0).getGeometry()), Feature.fromGeometry(features.get(0).getGeometry())});
                //setSource("selected-stations", featureCollection);
                //selectMarker(marker);
                extremities.set(1, features.get(0));
                drawTrack();
                return;
            }
            if (!destinationSelected) {
                Log.d("else-if", "1.5");
                //FeatureCollection featureCollection = FeatureCollection.fromFeatures(
                //        new Feature[]{Feature.fromGeometry(selectedStations.get(0).getGeometry()), Feature.fromGeometry(features.get(0).getGeometry())});
                //for (Feature ll : featureCollection.getFeatures()) {
                //    Log.d("newFeatures", ll.getGeometry().toString());
                //}
                //setSource("selected-stations", featureCollection);
                //selectMarker(marker);
                extremities.add(1, features.get(0));
                destinationSelected = true;
                drawTrack();

                return;
            }*/

            View focusView = this.getCurrentFocus();
            Log.d("focus", focusView.toString());
            if (focusView != null) {
                if (focusView.getId() == sourceField.getId()) {
                    extremities.set(0, features.get(0));
                    sourceField.setText(extremities.get(0).getStringProperty("Name") + " ");
                    sourceSelected = true;

                    if (!destinationSelected) {
                        destinationField.requestFocus();
                    }

                    Log.d("extremity0", features.get(0).getStringProperty("Name"));
                } else {
                    extremities.set(1, features.get(0));
                    destinationField.setText(extremities.get(1).getStringProperty("Name") + " ");
                    destinationSelected = true;
                    Log.d("extremity0", features.get(0).getStringProperty("Name"));
                }
                //sourceSelected = (sourceSelected ? (destinationSelected = true) : true);
                Log.d("selectBool", String.valueOf(sourceSelected) + String.valueOf(destinationSelected));
            }

            if (destinationSelected && sourceSelected) {
                Log.d("selectTwice", String.valueOf(destinationSelected));
                drawTrack();
            }
        }


    }


    private void touchStationView(LatLng point) {
        Log.i("viewTouch", "i am here");
        //final SymbolLayer marker = (SymbolLayer) mapboxMap.getLayer("selected-stations-layer");
        final PointF pixel = mapboxMap.getProjection().toScreenLocation(point);
        List<Feature> features = mapboxMap.queryRenderedFeatures(pixel, STATIONS_LAYER_ID);
        //List<Feature> selectedFeature = mapboxMap.queryRenderedFeatures(pixel, "selected-stations-layer");
        if (features == null || features.isEmpty()) {
            Log.d("empty", features.toString());
            deselectAll();
            refreshSource();
            return;
        }
        if (features.get(0).getBooleanProperty(PROPERTY_SELECTED)) {
            Log.d("empty", String.valueOf(features.get(0).getBooleanProperty(PROPERTY_SELECTED)));
            return;
        }

        deselectAll();
        String title = features.get(0).getStringProperty(PROPERTY_TITLE);
        Log.d("feature0", title + "\t" + features.get(0).getBooleanProperty(PROPERTY_SELECTED));
        for (Feature feature : mainFeatureCollection.getFeatures()) {
            Log.d("selectedField", feature.getStringProperty(PROPERTY_TITLE));
            if (feature.getStringProperty(PROPERTY_TITLE).equals(title)) {
                feature.getProperties().addProperty(PROPERTY_SELECTED, true);
                refreshSource();
            }

        }

        for (Feature f : mainFeatureCollection.getFeatures()) {
            Log.d("selectedPropriety", f.getProperties().toString());
        }

        SymbolLayer info = (SymbolLayer) mapboxMap.getLayer(INFO_LAYER_ID);
        Log.d("infoLayer", features.get(0).getId() + "\t" + info.getIconImage());

    }

    private void selectMarker(final SymbolLayer marker) {
        ValueAnimator markerAnimator = new ValueAnimator();
        markerAnimator.setObjectValues(1f, 2f);
        markerAnimator.setDuration(300);
        markerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                marker.setProperties(
                        PropertyFactory.iconSize((float) animator.getAnimatedValue())
                );
            }
        });

        markerAnimator.start();
        sourceSelected = true;

    }

    private void deselectMarker(final SymbolLayer marker) {
        ValueAnimator markerAnimator = new ValueAnimator();
        markerAnimator.setObjectValues(2f, 1f);
        markerAnimator.setDuration(300);
        markerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                marker.setProperties(
                        PropertyFactory.iconSize((float) animator.getAnimatedValue())
                );
            }
        });
        markerAnimator.start();
        sourceSelected = false;
    }

    public void refreshSource() {
        if (geoJsonSource != null && mainFeatureCollection != null) {
            geoJsonSource.setGeoJson(mainFeatureCollection);
        }
    }

    private void drawTrack() {
        /*final GeoJsonSource selectedStationsSource = mapboxMap.getSourceAs("selected-stations");
        List<Feature> selectedStations = selectedStationsSource.querySourceFeatures(Filter.eq("$type", "Point"));
        for (Feature ll : selectedStations) {
            Log.d("SelectedLayerPoints", ll.getGeometry().toString());
        }*/
        //Log.d("selectedStations", selectedStations.get(0).getGeometry().toString() +"\t"+ selectedStations.get(1).getGeometry().toString());

        mapboxMap.clear();
        getCurrentFocus().clearFocus();

        Position source = (Position) extremities.get(0).getGeometry().getCoordinates();
        Position destination = (Position) extremities.get(1).getGeometry().getCoordinates();

        Log.d("SouDes", "[" + String.valueOf(source.getLatitude()) + " ; " + String.valueOf(source.getLongitude() + "]" + "\t" + "[" + String.valueOf(destination.getLatitude()) + " ; " + String.valueOf(destination.getLongitude()) + "]"));
        new DrawTrackFromGeoJson(MainActivity.this, source, destination).execute();


    }

    public void resetAll(View view) {
        sourceSelected = destinationSelected = false;
        sourceField.setText("");
        destinationField.setText("");
        mapboxMap.clear();
    }

    static Bitmap generate(@NonNull View view) {
        int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(measureSpec, measureSpec);

        int measuredWidth = view.getMeasuredWidth();
        int measuredHeight = view.getMeasuredHeight();

        view.layout(0, 0, measuredWidth, measuredHeight);
        Bitmap bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    private void deselectAll() {
        for (Feature feature : mainFeatureCollection.getFeatures()) {
            feature.getProperties().addProperty(PROPERTY_SELECTED, false);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        for (Feature item : mainFeatureCollection.getFeatures()) {
            if (item.getStringProperty(PROPERTY_TITLE).equals(adapterView.getItemAtPosition(i))) {
                Position position = (Position) item.getGeometry().getCoordinates();
                onMapClick(new LatLng(position.getLatitude(), position.getLongitude()));
            }
        }

    }
}
