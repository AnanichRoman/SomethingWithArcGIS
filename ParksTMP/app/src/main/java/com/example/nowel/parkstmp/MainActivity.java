package com.example.nowel.parkstmp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ServiceFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private MapView mMapView;
    private Callout mCallout;
    private ServiceFeatureTable mServiceFeatureTable;
    private LocationDisplay mLocationDisplay;
    String[] reqPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission
            .ACCESS_COARSE_LOCATION};
    private int requestCode = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMapView = (MapView) findViewById(R.id.mapView);
        // create a map with the BasemapType topographic IMAGERY_WITH_LABELS 54.799,26.960
        final ArcGISMap mMap = new ArcGISMap(Basemap.Type.IMAGERY_WITH_LABELS, 54.799, 26.960, 16);
        // set the map to be displayed in this view
        mMapView.setMap(mMap);

        mCallout = mMapView.getCallout();
        mServiceFeatureTable = new ServiceFeatureTable(getResources().getString(R.string.sample_service_url));
        mLocationDisplay = mMapView.getLocationDisplay();

        Button center = (Button) findViewById(R.id.imageButton);
            center.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
                    if (!mLocationDisplay.isStarted())
                        mLocationDisplay.startAsync();

                }
            });

        if (!isTaskRoot()) {
            // Android launched another instance of the root activity into an existing task
            //  so just quietly finish and go away, dropping the user back into the activity
            //  at the top of the stack (ie: the last state of this task)
            finish();
            return;
        }

        ServiceFeatureTable serviceFeatureTable = new ServiceFeatureTable(getResources().getString(R.string.sample_service_url));

        //explicitly set the mode to on interaction cache (which is also the default mode for service feature tables)
        serviceFeatureTable.setFeatureRequestMode(ServiceFeatureTable.FeatureRequestMode.ON_INTERACTION_CACHE);

        // create the feature layer using the service feature table
        FeatureLayer featureLayer = new FeatureLayer(serviceFeatureTable);

        // add the layer to the map
        mMap.getOperationalLayers().add(featureLayer);


        //TOUCH
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // remove any existing callouts
                if(mCallout.isShowing()){
                    mCallout.dismiss();
                }
                // get the point that was clicked and convert it to a point in map coordinates
                final Point clickPoint = mMapView.screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
                // create a selection tolerance
                int tolerance = 10;
                double mapTolerance = tolerance * mMapView.getUnitsPerDensityIndependentPixel();
                // use tolerance to create an envelope to query
                Envelope envelope = new Envelope(clickPoint.getX() - mapTolerance, clickPoint.getY() - mapTolerance, clickPoint.getX() + mapTolerance, clickPoint.getY() + mapTolerance, mMap.getSpatialReference());
                QueryParameters query = new QueryParameters();
                query.setGeometry(envelope);
                // request all available attribute fields
                final ListenableFuture<FeatureQueryResult> future = mServiceFeatureTable.queryFeaturesAsync(query, ServiceFeatureTable.QueryFeatureFields.LOAD_ALL);
                // add done loading listener to fire when the selection returns
                future.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //call get on the future to get the result
                            FeatureQueryResult result = future.get();
                            // create an Iterator
                            Iterator<Feature> iterator = result.iterator();
                            // create a TextView to display field values
                            TextView calloutContent = new TextView(getApplicationContext());
                            calloutContent.setTextColor(Color.BLACK);
                            calloutContent.setSingleLine(false);
                            calloutContent.setVerticalScrollBarEnabled(true);
                            calloutContent.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
                            calloutContent.setMovementMethod(new ScrollingMovementMethod());
                            calloutContent.setLines(5);
                            // cycle through selections
                            int counter = 0;
                            Feature feature;
                            while (iterator.hasNext()){
                                feature = iterator.next();
                                // create a Map of all available attributes as name value pairs
                                Map<String, Object> attr = feature.getAttributes();
                                Set<String> keys = attr.keySet();
                                for(String key:keys){
                                    Object value = attr.get(key);
                                    // format observed field value as date
                                    if(value instanceof GregorianCalendar){
                                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
                                        value = simpleDateFormat.format(((GregorianCalendar) value).getTime());
                                    }
                                    // append name value pairs to TextView
                                    calloutContent.append(key + " | " + value + "\n");
                                }
                                counter++;
                                // center the mapview on selected feature
                                Envelope envelope = feature.getGeometry().getExtent();
                                mMapView.setViewpointGeometryAsync(envelope, 200);
                                // show CallOut
                                mCallout.setLocation(clickPoint);
                                mCallout.setContent(calloutContent);
                                mCallout.show();
                            }
                        } catch (Exception e) {
                            Log.e(getResources().getString(R.string.app_name), "Select feature failed: " + e.getMessage());
                        }
                    }
                });
                return super.onSingleTapConfirmed(e);
            }
        });

        // Listen to changes in the status of the location data source.
        mLocationDisplay.addDataSourceStatusChangedListener(new LocationDisplay.DataSourceStatusChangedListener() {
            @Override
            public void onStatusChanged(LocationDisplay.DataSourceStatusChangedEvent dataSourceStatusChangedEvent) {

                // If LocationDisplay started OK, then continue.
                if (dataSourceStatusChangedEvent.isStarted())
                    return;

                // No error is reported, then continue.
                if (dataSourceStatusChangedEvent.getError() == null)
                    return;

                // If an error is found, handle the failure to start.
                // Check permissions to see if failure may be due to lack of permissions.
                boolean permissionCheck1 = ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[0]) ==
                        PackageManager.PERMISSION_GRANTED;
                boolean permissionCheck2 = ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[1]) ==
                        PackageManager.PERMISSION_GRANTED;

                if (!(permissionCheck1 && permissionCheck2)) {
                    // If permissions are not already granted, request permission from the user.
                    ActivityCompat.requestPermissions(MainActivity.this, reqPermissions, requestCode);
                } else {
                    // Report other unknown failure types to the user - for example, location services may not
                    // be enabled on the device.
                    String message = String.format("Error : %s", dataSourceStatusChangedEvent
                            .getSource().getLocationDataSource().getError().getMessage());
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    mLocationDisplay.stop();
                    // Update UI to reflect that the location display did not actually start
                }
            }
        });


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Location permission was granted. This would have been triggered in response to failing to start the
            // LocationDisplay, so try starting this again.
            mLocationDisplay.startAsync();
        } else {
            // If permission was denied, show toast to inform user what was chosen. If LocationDisplay is started again,
            // request permission UX will be shown again, option should be shown to allow never showing the UX again.
            // Alternative would be to disable functionality so request is not shown again.
            Toast.makeText(MainActivity.this, "ERRORRS", Toast
                    .LENGTH_SHORT).show();

            // Update UI to reflect that the location display did not actually start
           // mSpinner.setSelection(0, true);
        }
    }



    @Override
    protected void onPause(){
        super.onPause();
        mMapView.pause();
    }

    @Override
    protected void onResume(){
        super.onResume();
        mMapView.resume();
    }

}
