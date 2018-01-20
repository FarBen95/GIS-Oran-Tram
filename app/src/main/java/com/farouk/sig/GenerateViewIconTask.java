package com.farouk.sig;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.mapbox.services.commons.geojson.Feature;
import com.mapbox.services.commons.geojson.FeatureCollection;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Created by USER on 09/01/2018.
 */

public class GenerateViewIconTask extends AsyncTask<FeatureCollection, Void, HashMap<String, Bitmap>> {

    private final WeakReference<MainActivity> activityRef;

    GenerateViewIconTask(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @SuppressWarnings("WrongThread")
    @Override
    protected HashMap<String, Bitmap> doInBackground(FeatureCollection... params) {
        MainActivity activity = activityRef.get();
        if (activity != null) {
            HashMap<String, Bitmap> imagesMap = new HashMap<>();
            LayoutInflater inflater = LayoutInflater.from(activity);
            FeatureCollection featureCollection = params[0];

            for (Feature feature : featureCollection.getFeatures()) {
                View view = inflater.inflate(R.layout.info_window_layout, null);

                String name = feature.getStringProperty(activity.PROPERTY_TITLE);
                TextView titleTv = (TextView) view.findViewById(R.id.title);
                titleTv.setText(name);

                Bitmap bitmap = activity.generate(view);
                imagesMap.put(name, bitmap);
                Log.d("nameOfBitmap", name);
                activity.mapboxMap.addImage(name, bitmap);
            }

            return imagesMap;
        } else {
            return null;
        }
    }

    @Override
    protected void onPostExecute(HashMap<String, Bitmap> bitmapHashMap) {
        super.onPostExecute(bitmapHashMap);
        MainActivity activity = activityRef.get();
        if (activity != null && bitmapHashMap != null) {

            //activity.mapboxMap.addImages(bitmapHashMap);
            Log.d("Bitmap", "done");
            /*if (refreshSource) {
                activity.refreshSource();
            }*/
        }

        //Log.d("Yassmine", activity.mapboxMap.getImage("Yassmine").toString());
        //for(String name: bitmapHashMap.keySet())
        //Log.d("infoImages", activity.mapboxMap.getImage(name).toString());
        //activity.refreshSource();
        //activity.setupInfoWindowsLayer();
    }
}
