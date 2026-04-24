package com.detector.esp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.detector.esp.ui.SkyPlotView;

public class SatelliteActivity extends Activity {

    private SkyPlotView skyPlot;
    private TextView infoText;
    private LocationManager locationManager;
    private GnssStatus.Callback gnssCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF111111);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, 40);

        TextView title = new TextView(this);
        title.setText("🛰 卫星实时监控");
        title.setTextSize(22);
        title.setTextColor(0xFF00E676);
        title.setPadding(32, 48, 32, 16);
        title.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD));
        root.addView(title);

        skyPlot = new SkyPlotView(this);
        root.addView(skyPlot);

        infoText = new TextView(this);
        infoText.setTextSize(14);
        infoText.setTextColor(0xCCFFFFFF);
        infoText.setTypeface(android.graphics.Typeface.MONOSPACE);
        infoText.setPadding(32, 24, 32, 32);
        infoText.setText("等待卫星数据...");
        root.addView(infoText);

        scroll.addView(root);
        setContentView(scroll);

        startListening();
    }

    @SuppressWarnings("MissingPermission")
    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            infoText.setText("未授予位置权限");
            return;
        }

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        gnssCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                skyPlot.updateSatellites(status);
                updateInfoText(status);
            }
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                new LocationListener() {
                    @Override public void onLocationChanged(Location loc) {}
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                });

        locationManager.registerGnssStatusCallback(gnssCallback, null);
    }

    private void updateInfoText(GnssStatus status) {
        StringBuilder sb = new StringBuilder();
        int total = status.getSatelliteCount();
        int used = 0;

        sb.append("━━━ 卫星详情 ━━━\n\n");
        sb.append(String.format("%-5s %-4s %-7s %-7s %-6s %-5s\n",
                "系统", "ID", "仰角", "方位角", "信噪比", "定位"));
        sb.append("─────────────────────────────────\n");

        for (int i = 0; i < total; i++) {
            String sys = getConstellationName(status.getConstellationType(i));
            int svid = status.getSvid(i);
            float el = status.getElevationDegrees(i);
            float az = status.getAzimuthDegrees(i);
            float cn0 = status.getCn0DbHz(i);
            boolean fix = status.usedInFix(i);
            if (fix) used++;

            sb.append(String.format("%-5s %-4d %5.1f° %6.1f° %5.1f  %s\n",
                    sys, svid, el, az, cn0, fix ? "✓" : ""));
        }

        sb.append("\n总计: ").append(total).append(" 颗可见, ")
          .append(used).append(" 颗用于定位");

        runOnUiThread(() -> infoText.setText(sb.toString()));
    }

    private String getConstellationName(int type) {
        switch (type) {
            case GnssStatus.CONSTELLATION_GPS: return "GPS";
            case GnssStatus.CONSTELLATION_GLONASS: return "GLO";
            case GnssStatus.CONSTELLATION_GALILEO: return "GAL";
            case GnssStatus.CONSTELLATION_BEIDOU: return "BDS";
            case GnssStatus.CONSTELLATION_QZSS: return "QZS";
            case GnssStatus.CONSTELLATION_SBAS: return "SBA";
            default: return "UNK";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null && gnssCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssCallback);
        }
    }
}
