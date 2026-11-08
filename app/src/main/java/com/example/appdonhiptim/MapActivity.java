package com.example.appdonhiptim;

import android.content.Context;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapActivity extends AppCompatActivity {

    private MapView osmMapViewFull;
    private TextView tvLocationFull;
    private TextView tvAddressDetail;

    private double lat = 21.028511;
    private double lng = 105.804817;
    private String addressStr = "Hà Nội, Việt Nam";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cấu hình User-Agent cho osmdroid (BẮT BUỘC trước setContentView)
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("DoAnIoT_AnNguyen_UNETI");

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_map);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        // Đọc dữ liệu Intent nhận từ MainActivity
        if (getIntent() != null) {
            lat = getIntent().getDoubleExtra("latitude", 21.028511);
            lng = getIntent().getDoubleExtra("longitude", 105.804817);
            if (getIntent().getStringExtra("address") != null) {
                addressStr = getIntent().getStringExtra("address");
            }
        }

        // Ánh xạ View
        osmMapViewFull = findViewById(R.id.osmMapViewFull);
        tvLocationFull = findViewById(R.id.tvLocationFull);
        tvAddressDetail = findViewById(R.id.tvAddressDetail);
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnRecenter = findViewById(R.id.btnRecenter);

        if (tvLocationFull != null) tvLocationFull.setText(addressStr);
        if (tvAddressDetail != null) tvAddressDetail.setText(addressStr);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        setupMapView();

        if (btnRecenter != null) {
            btnRecenter.setOnClickListener(v -> centerMap(lat, lng));
        }
    }

    private void setupMapView() {
        if (osmMapViewFull == null) return;
        try {
            osmMapViewFull.setTileSource(new XYTileSource(
                    "OSM_HOT",
                    0,
                    19,
                    256,
                    ".png",
                    new String[]{
                            "https://a.tile.openstreetmap.fr/hot/",
                            "https://b.tile.openstreetmap.fr/hot/",
                            "https://c.tile.openstreetmap.fr/hot/"
                    }
            ));
        } catch (Exception e) {
            osmMapViewFull.setTileSource(TileSourceFactory.MAPNIK);
        }
        osmMapViewFull.setMultiTouchControls(true);
        osmMapViewFull.setBuiltInZoomControls(false);

        centerMap(lat, lng);
    }

    private void centerMap(double latitude, double longitude) {
        if (osmMapViewFull == null) return;
        GeoPoint point = new GeoPoint(latitude, longitude);
        osmMapViewFull.getController().setZoom(18.0);
        osmMapViewFull.getController().animateTo(point);

        osmMapViewFull.getOverlays().clear();
        Marker marker = new Marker(osmMapViewFull);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle("Vị trí người đeo:\n" + addressStr);

        osmMapViewFull.getOverlays().add(marker);
        osmMapViewFull.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (osmMapViewFull != null) osmMapViewFull.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (osmMapViewFull != null) osmMapViewFull.onPause();
    }
}