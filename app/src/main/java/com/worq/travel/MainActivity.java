package com.worq.travel;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 1407;
    private WebView webView;
    private LocationManager locationManager;
    private boolean locationStarted = false;
    private boolean pageReady = false;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private Location latestLocation;

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

            @JavascriptInterface public void requestDrivingRoute(
                    double fromLat, double fromLon,
                    double toLat, double toLon,
                    int requestId) {
                new Thread(() -> fetchDrivingRoute(
                        fromLat, fromLon, toLat, toLon, requestId
                ), "WorqRoute-" + requestId).start();
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
            @Override public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
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
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
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
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 2500L, 5f, locationListener);
                }
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 2500L, 5f, locationListener);
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
                loc.getLatitude() + "," + loc.getLongitude() + "," +
                Math.max(0f, loc.getAccuracy()) + ");";
        webView.post(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }

    private void fetchDrivingRoute(
            double fromLat, double fromLon,
            double toLat, double toLon,
            int requestId) {
        HttpURLConnection conn = null;
        try {
            String endpoint = String.format(
                    Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.7f,%.7f;%.7f,%.7f?overview=false&steps=false&alternatives=false",
                    fromLon, fromLat, toLon, toLat
            );

            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Worq-Travel-Android/1.0");

            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? conn.getInputStream() : conn.getErrorStream();

            StringBuilder body = new StringBuilder();
            if (stream != null) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) body.append(line);
                }
            }

            if (status < 200 || status >= 300) {
                throw new Exception("Rota servisi HTTP " + status);
            }

            JSONObject json = new JSONObject(body.toString());
            if (!"Ok".equals(json.optString("code"))) {
                throw new Exception("Rota bulunamadı");
            }

            JSONArray routes = json.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                throw new Exception("Rota bulunamadı");
            }

            JSONObject route = routes.getJSONObject(0);
            double meters = route.optDouble("distance", -1);
            double seconds = route.optDouble("duration", -1);
            if (meters <= 0 || seconds <= 0) {
                throw new Exception("Geçersiz rota sonucu");
            }

            sendDrivingRouteResult(requestId, true, meters, seconds, "");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Rota servisine ulaşılamadı" : e.getMessage();
            sendDrivingRouteResult(requestId, false, 0, 0, msg);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void sendDrivingRouteResult(
            int requestId, boolean ok,
            double meters, double seconds,
            String message) {
        if (webView == null) return;
        String safeMessage = JSONObject.quote(message == null ? "" : message);
        String js = "window.onDrivingRouteResult && window.onDrivingRouteResult(" +
                requestId + "," + ok + "," + meters + "," + seconds + "," +
                safeMessage + ");";
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
                (host.endsWith("google.com") ||
                 host.endsWith("google.com.tr") ||
                 host.equals("maps.app.goo.gl")) &&
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
            Toast.makeText(
                    this,
                    "Google Maps veya uygun harita uygulaması bulunamadı.",
                    Toast.LENGTH_LONG
            ).show();
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST) return;
        boolean granted = hasLocationPermission();
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
        if (granted) startLocationUpdates();
        else Toast.makeText(
                this,
                "Konumunuzu haritada göstermek için konum izni gerekli.",
                Toast.LENGTH_LONG
        ).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasLocationPermission()) startLocationUpdates();
    }

    @Override protected void onPause() {
        if (locationManager != null && locationStarted) {
            try { locationManager.removeUpdates(locationListener); }
            catch (SecurityException ignored) {}
            locationStarted = false;
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (locationManager != null && locationStarted) {
            try { locationManager.removeUpdates(locationListener); }
            catch (SecurityException ignored) {}
        }
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
