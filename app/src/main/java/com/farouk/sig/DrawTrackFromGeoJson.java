package com.farouk.sig;

import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.services.commons.models.Position;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by USER on 28/12/2017.
 */

public class DrawTrackFromGeoJson extends AsyncTask<Void, Void, List<LatLng>> {
    private static final String TAG = "DrawGeoJson";
    private final WeakReference<MainActivity> activityRef;
    double distance = 0;
    private Position source, destination;

    public DrawTrackFromGeoJson(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    public DrawTrackFromGeoJson(MainActivity activity, Position source, Position destination) {
        this.activityRef = new WeakReference<>(activity);
        this.source = source;
        this.destination = destination;
    }

    private static double truncateDecimal(double x, int numberofDecimals) {
        if (x > 0) {
            return new BigDecimal(String.valueOf(x)).setScale(numberofDecimals, BigDecimal.ROUND_FLOOR).doubleValue();
        } else {
            return new BigDecimal(String.valueOf(x)).setScale(numberofDecimals, BigDecimal.ROUND_CEILING).doubleValue();
        }
    }

    @SuppressWarnings("WrongThread")
    @Override
    protected List<LatLng> doInBackground(Void... voids) {
        MainActivity activity = activityRef.get();
        ArrayList<LatLng> track = new ArrayList<>();

        try {
            // Load GeoJSON file
            InputStream inputStream = activity.getAssets().open("Oran_tram.geojson");
            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }

            inputStream.close();

            // Parse JSON
            JSONObject json = new JSONObject(sb.toString());
            JSONArray features = json.getJSONArray("features");
            Log.d("features", features.toString());
            JSONObject geometry;
            String type = "Point";
            int i = 0;
            do {
                JSONObject feature = features.getJSONObject(i);
                geometry = feature.getJSONObject("geometry");

                if (geometry != null) {
                    type = geometry.getString("type");
                    Log.d("type", type);
                }
                i++;
            }
            while (!type.equalsIgnoreCase("LineString"));

            // Our GeoJSON only has one feature: a line string
            if (!TextUtils.isEmpty(type) && type.equalsIgnoreCase("LineString")) {
                //Log.d("features", features.toString());
                // Get the Coordinates
                JSONArray coords = geometry.getJSONArray("coordinates");
                Log.d("coordinates", coords.toString());
                boolean valid = false;
                boolean reverse = false;
                LatLng currentCoordinates = new LatLng();
                LatLng oldCoordinates = null;
                for (int lc = 0; lc < coords.length(); lc += ((reverse && valid) ? -1 : 1)) {
                    Log.d("loop", String.valueOf(lc));
                    JSONArray coord = coords.getJSONArray(lc);
                    currentCoordinates.setLatitude(coord.getDouble(1));
                    currentCoordinates.setLongitude(coord.getDouble(0));
                    //Log.d("current", String.valueOf(truncateDecimal(latitude, 4) + "\t" + truncateDecimal(longitude, 4)));
                    //Log.d("truncatSource", String.valueOf(truncateDecimal(source.getLatitude(), 4)) + "\t" + String.valueOf(truncateDecimal(source.getLongitude(), 4)));
                    if ((truncateDecimal(source.getLatitude(), 4) == truncateDecimal(currentCoordinates.getLatitude(), 4) &&
                            truncateDecimal(source.getLongitude(), 4) == truncateDecimal(currentCoordinates.getLongitude(), 4)) || valid){
                        track.add(new LatLng(currentCoordinates.getLatitude(), currentCoordinates.getLongitude()));
                        if (oldCoordinates == null){
                            oldCoordinates = new LatLng(currentCoordinates);
                        }else {
                            distance += oldCoordinates.distanceTo(currentCoordinates);
                            oldCoordinates.setLatitude(currentCoordinates.getLatitude());
                            oldCoordinates.setLongitude(currentCoordinates.getLongitude());
                        }
                        valid = true;
                    }
                    //Log.d("truncatDestination", String.valueOf(truncateDecimal(destination.getLatitude(), 4)) + "\t" + String.valueOf(truncateDecimal(destination.getLongitude(), 4)));
                    if ((truncateDecimal(destination.getLatitude(), 4) == truncateDecimal(currentCoordinates.getLatitude(), 4) &&
                            truncateDecimal(destination.getLongitude(), 4) == truncateDecimal(currentCoordinates.getLongitude(), 4))){
                        if (valid) {
                            break;
                        } else {
                            reverse = true;
                        }
                    }

                }
            }

        } catch (Exception exception) {
            Log.e(TAG, "Exception Loading GeoJSON: " + exception.toString());
        }
        Log.i("track", track.toString());
        return track;

    }

    @Override
    protected void onPostExecute(List<LatLng> track) {
        super.onPostExecute(track);
        MainActivity activity = activityRef.get();
        Log.d("size", String.valueOf(track.size()));
        if (track.size() > 0) {
            // Draw polyline on map
            for (LatLng ll : track) {
                Log.d("point", ll.toString());
            }
            activity.mapboxMap.addPolyline(new PolylineOptions()
                    .addAll(track)
                    .color(ContextCompat.getColor(activity, R.color.magenta))
                    .width(4));

            /*Icon icon = IconFactory.getInstance(activity).fromBitmap(activity.mapboxMap.getImage(activity.DESTINATION_IMAGE));
            MainActivity.mapboxMap.addMarker(new MarkerOptions().
                    position(new LatLng(destination.getLatitude(), destination.getLongitude())).
                    icon(icon));

            activity.hideCircles(true);*/

            activity.sourceMarker.setPosition(new LatLng(source.getLatitude(),
                    source.getLongitude()));
            activity.mapboxMap.addMarker(activity.sourceMarker);

            activity.destinationMarker.setPosition(new LatLng(destination.getLatitude(),
                    destination.getLongitude()));
            activity.mapboxMap.addMarker(activity.destinationMarker);

            activity.distance.setText(String.valueOf(truncateDecimal(distance, 2) +" m"));
            double roundTime = truncateDecimal(distance / (1000 / 3), 0);
            activity.time.setText(Double.valueOf(roundTime).intValue() + " min " +
                    Double.valueOf((distance / (1000 / 3) - roundTime) * 60).intValue() + " sec");
            activity.distanceTime.setVisibility(View.VISIBLE);
        }
    }
}
