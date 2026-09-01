package com.worq.travel;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 1407;
    private WebView webView;
    private LocationManager locationManager;
    private boolean locationStarted = false;
    private boolean pageReady = false;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private Location latestLocation;
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();

    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (isBetterLocation(location, latestLocation)) latestLocation = location;
            sendLocation(latestLocation != null ? latestLocation : location);
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) { startLocationUpdates(); }
        @Override public void onProviderDisabled(String provider) {}
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        // index.html file:///android_asset/ üzerinden açılıyor; Leaflet ve Türkiye il/ilçe
        // koordinatları HTTPS kaynaklarından yüklenebilsin.
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void requestLocation() {
                runOnUiThread(() -> ensureLocation(true));
            }

            @JavascriptInterface public void openLocationSettings() {
                runOnUiThread(() -> {
                    try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                    catch (Exception ignored) {}
                });
            }

            // Harita üzerindeki yeni Türkiye verilerinin hassas koordinatı Excel adresinden
            // cihazın Android Geocoder servisiyle talep üzerine bulunur. Firma adı sorguya eklenmez.
            @JavascriptInterface public void geocodeAddress(String requestId, String address) {
                final String safeRequestId = requestId == null ? "" : requestId;
                final String safeAddress = address == null ? "" : address.trim();
                if (safeAddress.isEmpty()) {
                    sendGeocodeResult(safeRequestId, 0d, 0d, false);
                    return;
                }

                geocodeExecutor.execute(() -> {
                    try {
                        if (!Geocoder.isPresent()) {
                            sendGeocodeResult(safeRequestId, 0d, 0d, false);
                            return;
                        }
                        Geocoder geocoder = new Geocoder(MainActivity.this, new Locale("tr", "TR"));
                        @SuppressWarnings("deprecation")
                        List<Address> results = geocoder.getFromLocationName(safeAddress, 1);
                        if (results != null && !results.isEmpty()) {
                            Address result = results.get(0);
                            sendGeocodeResult(safeRequestId, result.getLatitude(), result.getLongitude(), true);
                        } else {
                            sendGeocodeResult(safeRequestId, 0d, 0d, false);
                        }
                    } catch (Exception ignored) {
                        sendGeocodeResult(safeRequestId, 0d, 0d, false);
                    }
                });
            }
        }, "AndroidLocation");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageReady = true;
                if (latestLocation != null) sendLocation(latestLocation);
                ensureLocation(false);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                    startLocationUpdates();
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestLocationPermission();
                }
            }
        });

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (savedInstanceState == null) webView.loadUrl("file:///android_asset/index.html");
        else webView.restoreState(savedInstanceState);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_REQUEST);
    }

    private void ensureLocation(boolean userInitiated) {
        if (hasLocationPermission()) {
            startLocationUpdates();
            if (latestLocation != null) sendLocation(latestLocation);
        } else if (userInitiated || pageReady) {
            requestLocationPermission();
        }
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission() || locationManager == null) return;
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps != null && isBetterLocation(gps, latestLocation)) latestLocation = gps;
            if (net != null && isBetterLocation(net, latestLocation)) latestLocation = net;
            if (latestLocation != null) sendLocation(latestLocation);

            if (!locationStarted) {
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2500L, 5f, locationListener);
                }
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500L, 5f, locationListener);
                }
                locationStarted = true;
            }
        } catch (SecurityException ignored) {}
    }

    private boolean isBetterLocation(Location candidate, Location current) {
        if (candidate == null) return false;
        if (current == null) return true;
        long timeDelta = candidate.getTime() - current.getTime();
        if (timeDelta > 120000) return true;
        if (timeDelta < -120000) return false;
        float accuracyDelta = candidate.getAccuracy() - current.getAccuracy();
        return accuracyDelta < 0 || (timeDelta > 0 && accuracyDelta <= 75);
    }

    private void sendLocation(Location loc) {
        if (loc == null || webView == null || !pageReady) return;
        String js = "window.onNativeLocation && window.onNativeLocation(" +
                loc.getLatitude() + "," + loc.getLongitude() + "," + Math.max(0f, loc.getAccuracy()) + ");";
        webView.post(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    private void sendGeocodeResult(String requestId, double lat, double lon, boolean ok) {
        if (webView == null || !pageReady) return;
        String js = "window.onNativeGeocode && window.onNativeGeocode(" +
                JSONObject.quote(requestId == null ? "" : requestId) + "," +
                lat + "," + lon + "," + (ok ? "true" : "false") + ");";
        webView.post(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath();
        boolean geo = "geo".equalsIgnoreCase(scheme);
        boolean maps = host != null &&
                (host.endsWith("google.com") || host.endsWith("google.com.tr") || host.equals("maps.app.goo.gl")) &&
                (path == null || path.contains("/maps"));
        if (!geo && !maps) return false;

        try {
            Intent googleMaps = new Intent(Intent.ACTION_VIEW, uri);
            googleMaps.setPackage("com.google.android.apps.maps");
            if (googleMaps.resolveActivity(getPackageManager()) != null) {
                startActivity(googleMaps);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Google Maps veya uygun harita uygulaması bulunamadı.", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST) return;
        boolean granted = hasLocationPermission();
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
        if (granted) startLocationUpdates();
        else Toast.makeText(this, "Konumunuzu haritada göstermek için konum izni gerekli.", Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasLocationPermission()) startLocationUpdates();
    }

    @Override protected void onPause() {
        if (locationManager != null && locationStarted) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) {}
            locationStarted = false;
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (locationManager != null && locationStarted) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) {}
        }
        geocodeExecutor.shutdownNow();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
