package com.example.appdonhiptim;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;
    private static final String CHANNEL_ID = "FALL_ALERT_CHANNEL";

    // Đường dẫn Google Apps Script Web App để tự động ghi lịch sử đo vào Google Sheet
    public static String GOOGLE_SHEET_WEB_APP_URL = "https://script.google.com/macros/s/AKfycbx05H733iZbZr_zZ4S3x6rtwciwCM-guYxDeYuV3T6Up8IxwcXWkogbSKQD8bOc4aJjoA/exec";
    public static String GOOGLE_SHEET_VIEW_URL = "https://docs.google.com/spreadsheets/d/1ufJAaSZt7rFWYM5JZ3Zc3g8pnBsMIR4XHlczr-gZAC0/edit?gid=0#gid=0";

    private TextView txtnhiptim;
    private TextView oxy1;
    private TextView solandap1;
    private TextView textTeNga;
    private TextView textViTri;
    private MaterialCardView cardTeNga;
    private MaterialCardView cardHealthToast;
    private ImageView imgHealthIcon;
    private TextView txtHealthStatus;
    private MapView osmMapView;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseReference rootRef;

    private boolean isFallAlertShown = false;
    private Ringtone currentRingtone;

    // Ngưỡng cài đặt mặc định
    private int heartMinThreshold = 50;
    private int heartMaxThreshold = 120;
    private int o2MinThreshold = 90;
    private int o2MaxThreshold = 100;

    private boolean isHeartWarningActive = false;
    private boolean isO2WarningActive = false;

    private double currentLat = 21.028511; // Tọa độ mặc định
    private double currentLng = 105.804817;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cấu hình User-Agent cho osmdroid (BẮT BUỘC trước setContentView)
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("DoAnIoT_AnNguyen_UNETI");

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Xử lý khoảng cách với thanh trạng thái hệ thống (Status Bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        // Xin quyền thông báo trên Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }

        // Ánh xạ View từ layout activity_main.xml
        txtnhiptim = findViewById(R.id.textnhiptim);
        oxy1 = findViewById(R.id.textoxy);
        solandap1 = findViewById(R.id.textlandap);
        textTeNga = findViewById(R.id.textTeNga);
        textViTri = findViewById(R.id.textViTri);
        cardTeNga = findViewById(R.id.cardTeNga);
        cardHealthToast = findViewById(R.id.cardHealthToast);
        imgHealthIcon = findViewById(R.id.imgHealthIcon);
        txtHealthStatus = findViewById(R.id.txtHealthStatus);
        ImageView btnDismissToast = findViewById(R.id.btnDismissToast);
        MaterialCardView cardViTri = findViewById(R.id.cardViTri);
        ImageView btnSettingsHeader = findViewById(R.id.btnSettingsHeader);
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);

        if (btnDismissToast != null) {
            btnDismissToast.setOnClickListener(v -> {
                if (cardHealthToast != null) {
                    cardHealthToast.setVisibility(View.GONE);
                }
            });
        }

        osmMapView = findViewById(R.id.osmMapView);
        setupMapView();

        // Khởi tạo Fused Location Provider Client cho GPS
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        getUserLocation();

        // Bắt sự kiện click vào nút Bánh răng cài đặt ở header
        if (btnSettingsHeader != null) {
            btnSettingsHeader.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, caidat.class);
                startActivity(intent);
            });
        }

        // Bắt sự kiện click vào khung Vị trí hiện tại để mở màn hình Bản đồ toàn màn hình (MapActivity)
        if (cardViTri != null) {
            cardViTri.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, MapActivity.class);
                intent.putExtra("latitude", currentLat);
                intent.putExtra("longitude", currentLng);
                intent.putExtra("address", textViTri != null ? textViTri.getText().toString() : "Hà Nội, Việt Nam");
                startActivity(intent);
            });
        }

        // Khởi tạo kết nối Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://donhiptim-dee10-default-rtdb.firebaseio.com/");
        rootRef = database.getReference();

        // Lắng nghe dữ liệu sinh hiệu & té ngã toàn bộ Firebase theo thời gian thực
        rootRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(MainActivity.this, "Cảnh báo: Không tìm thấy dữ liệu trên Firebase!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 1. Tải ngưỡng Cài đặt từ Firebase nếu người dùng đã cấu hình
                DataSnapshot caiDatSnap = snapshot.child("CaiDat");
                if (caiDatSnap.exists()) {
                    if (caiDatSnap.child("HeartMin").getValue() != null) {
                        try { heartMinThreshold = Integer.parseInt(String.valueOf(caiDatSnap.child("HeartMin").getValue()).trim()); } catch (Exception ignored) {}
                    }
                    if (caiDatSnap.child("HeartMax").getValue() != null) {
                        try { heartMaxThreshold = Integer.parseInt(String.valueOf(caiDatSnap.child("HeartMax").getValue()).trim()); } catch (Exception ignored) {}
                    }
                    if (caiDatSnap.child("O2Min").getValue() != null) {
                        try { o2MinThreshold = Integer.parseInt(String.valueOf(caiDatSnap.child("O2Min").getValue()).trim()); } catch (Exception ignored) {}
                    }
                    if (caiDatSnap.child("O2Max").getValue() != null) {
                        try { o2MaxThreshold = Integer.parseInt(String.valueOf(caiDatSnap.child("O2Max").getValue()).trim()); } catch (Exception ignored) {}
                    }
                }

                // 1. Ưu tiên đọc từ node 'Stream' (nơi cảm biến IoT đẩy dữ liệu thời gian thực)
                DataSnapshot streamSnap = snapshot.hasChild("Stream") ? snapshot.child("Stream") : snapshot;

                // 2. Tìm kiếm tự động các tên node sinh hiệu liên quan
                Object nhipTimObj = findChildValue(streamSnap, "NhipTim", "Nhiptim", "BPM", "HeartRate");
                Object oxyObj = findChildValue(streamSnap, "NongDoO2", "Oxy", "SpO2", "O2");
                Object soLanDapObj = findChildValue(streamSnap, "SolanDap", "SoLanDap", "AVGBPM", "Count");
                
                // Đọc trực tiếp node 'CanhBaoNga' từ streamSnap (Tránh bị đè bởi node root 'CanhBaoNga' cũ)
                Object teNgaObj = streamSnap.hasChild("CanhBaoNga") ? streamSnap.child("CanhBaoNga").getValue() 
                        : findChildValue(streamSnap, "CanhBaoNga", "TeNga", "Te_Nga", "te_nga", "Te", "CanhBaoTeNga", "CanhBao", "Fall", "FallAlert", "Danger");
                
                // Nếu chưa tìm thấy trong Stream thì mới quét toàn bộ snapshot
                if (teNgaObj == null) {
                    teNgaObj = findChildValue(snapshot, "CanhBaoNga", "TeNga", "Te_Nga", "te_nga", "Te", "CanhBaoTeNga", "CanhBao", "Fall", "FallAlert", "Danger");
                }

                Object viTriObj = findChildValue(snapshot, "ViTri", "Location", "Address", "GPS");

                String strNhipTim = nhipTimObj != null ? nhipTimObj.toString() : "--";
                String strOxy = oxyObj != null ? oxyObj.toString() : "0";
                String strSoLanDap = soLanDapObj != null ? soLanDapObj.toString() : "0";
                String strViTri = (textViTri != null && textViTri.getText() != null) ? textViTri.getText().toString() : "Hà Nội, Việt Nam";

                txtnhiptim.setText(strNhipTim);
                oxy1.setText(strOxy);
                solandap1.setText(strSoLanDap);

                // 3. Kiểm tra Cảnh báo Ngưỡng Nhịp tim (Thấp hơn hoặc Cao hơn ngưỡng)
                try {
                    int valHeart = Integer.parseInt(strNhipTim.replaceAll("[^0-9]", ""));
                    if (valHeart > 30 && valHeart < 220) {
                        if (valHeart < heartMinThreshold) {
                            txtnhiptim.setTextColor(Color.RED);
                            checkAndTriggerThresholdAlert("heart", "⚠️ CẢNH BÁO NHỊP TIM THẤP!", "Nhịp tim hiện tại: " + valHeart + " bpm (Xuống dưới ngưỡng cho phép " + heartMinThreshold + " bpm)!");
                        } else if (valHeart > heartMaxThreshold) {
                            txtnhiptim.setTextColor(Color.RED);
                            checkAndTriggerThresholdAlert("heart", "⚠️ CẢNH BÁO NHỊP TIM CAO!", "Nhịp tim hiện tại: " + valHeart + " bpm (Vượt ngưỡng cho phép " + heartMaxThreshold + " bpm)!");
                        } else {
                            txtnhiptim.setTextColor(Color.parseColor("#0F172A"));
                            isHeartWarningActive = false;
                        }
                    }
                } catch (Exception ignored) {}

                // 4. Kiểm tra Cảnh báo Ngưỡng SpO2 Oxy (Thấp hơn hoặc Cao hơn ngưỡng)
                try {
                    int valO2 = Integer.parseInt(strOxy.replaceAll("[^0-9]", ""));
                    if (valO2 > 50 && valO2 <= 100) {
                        if (valO2 < o2MinThreshold) {
                            oxy1.setTextColor(Color.RED);
                            checkAndTriggerThresholdAlert("o2", "⚠️ CẢNH BÁO NỒNG ĐỘ OXY THẤP!", "Nồng độ Oxy SpO2 hiện tại: " + valO2 + "% (Xuống dưới ngưỡng cho phép " + o2MinThreshold + "%)!");
                        } else if (valO2 > o2MaxThreshold) {
                            oxy1.setTextColor(Color.RED);
                            checkAndTriggerThresholdAlert("o2", "⚠️ CẢNH BÁO NỒNG ĐỘ OXY CAO!", "Nồng độ Oxy SpO2 hiện tại: " + valO2 + "% (Vượt ngưỡng cho phép " + o2MaxThreshold + "%)!");
                        } else {
                            oxy1.setTextColor(Color.parseColor("#0F172A"));
                            isO2WarningActive = false;
                        }
                    }
                } catch (Exception ignored) {}

                // 5. Đồng bộ và kiểm tra trạng thái Té Ngã thực tế từ Firebase
                boolean isFall = false;
                String displayStatus = "Bình thường";

                if (teNgaObj != null) {
                    String raw = teNgaObj.toString().trim();
                    String val = raw.toLowerCase(Locale.ROOT);

                    if (val.equals("0") || val.equals("0.0") || val.equals("false")
                            || val.contains("bình thường") || val.contains("binh thuong")
                            || val.contains("normal") || val.contains("an toàn") || val.contains("an toan")
                            || val.equals("ok") || val.equals("none")) {
                        isFall = false;
                        displayStatus = "Bình thường";
                    } else {
                        // Bất kỳ giá trị nào khác 0/bình thường (như "PHÁT HIỆN TÉ NGÃ! 🚨", "1", "true", "cảnh báo") đều là CẢNH BÁO TÉ NGÃ!
                        isFall = true;
                        displayStatus = (raw.equals("1") || raw.equalsIgnoreCase("true")) ? "PHÁT HIỆN TÉ NGÃ! 🚨" : raw;
                    }
                }

                // Xử lý thông báo cảnh báo té ngã thời gian thực & hiển thị đúng chữ từ Firebase
                handleFallAlert(isFall, displayStatus);

                // Cập nhật Toast thông báo trạng thái sức khỏe thời gian thực ở đầu màn hình
                int parsedHeart = -1;
                try { parsedHeart = Integer.parseInt(strNhipTim.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                int parsedO2 = -1;
                try { parsedO2 = Integer.parseInt(strOxy.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                updateRealtimeHealthToast(parsedHeart, parsedO2, isFall);

                // Tự động lưu lịch sử đo vào bộ nhớ theo ngày để xem lại biểu đồ theo lịch
                if (parsedHeart > 30 && parsedO2 > 50) {
                    HealthHistoryManager.saveReading(MainActivity.this, parsedHeart, parsedO2);
                }

                // Nếu trên Firebase có dữ liệu ViTri thì cập nhật
                if (viTriObj != null && !viTriObj.toString().trim().isEmpty()) {
                    strViTri = viTriObj.toString();
                    if (textViTri != null) {
                        textViTri.setText(strViTri);
                    }
                }

                // Lấy thời gian thực tế hiện tại
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String timestamp = sdf.format(new Date());

                // Tự động đẩy kết quả đo mỗi lần Firebase cập nhật vào Google Sheet
                sendDataToGoogleSheet(strNhipTim, strOxy, strSoLanDap, displayStatus, strViTri, timestamp);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Lỗi Firebase: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        // Xử lý sự kiện chọn Menu Bottom Navigation
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                Toast.makeText(MainActivity.this, "Trang chủ", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.nav_map) {
                Intent intent = new Intent(MainActivity.this, bieudo.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_history) {
                openGoogleSheetHistory();
                return true;
            } else if (itemId == R.id.nav_settings) {
                Intent intent = new Intent(MainActivity.this, caidat.class);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    // Kích hoạt thông báo cảnh báo vượt/dưới ngưỡng sinh hiệu
    private void checkAndTriggerThresholdAlert(String type, String title, String message) {
        if (type.equals("heart")) {
            if (!isHeartWarningActive) {
                isHeartWarningActive = true;
                triggerVibrationAndNotification();
                showThresholdAlertDialog(title, message);
            }
        } else if (type.equals("o2")) {
            if (!isO2WarningActive) {
                isO2WarningActive = true;
                triggerVibrationAndNotification();
                showThresholdAlertDialog(title, message);
            }
        }
    }

    // Hiện hộp thoại cảnh báo vi phạm ngưỡng
    private void showThresholdAlertDialog(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message + "\n\nVui lòng kiểm tra sức khỏe của người dùng ngay lập tức!")
                .setPositiveButton("Đã kiểm tra", (dialog, which) -> {
                    if (currentRingtone != null && currentRingtone.isPlaying()) {
                        currentRingtone.stop();
                    }
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    // Cập nhật thẻ Toast thông báo trạng thái sức khỏe thời gian thực ở đầu màn hình
    private void updateRealtimeHealthToast(int heartRate, int spO2, boolean isFall) {
        if (cardHealthToast == null) return;

        String statusTitle;
        int iconRes;
        String bgColorHex;
        String strokeColorHex;
        String textColorHex;

        if (isFall) {
            statusTitle = "Cảnh báo té ngã!";
            iconRes = R.drawable.ic_fall;
            bgColorHex = "#FEF2F2";
            strokeColorHex = "#FECACA";
            textColorHex = "#DC2626";
        } else if (heartRate > 0 && spO2 > 0) {
            if (heartRate < heartMinThreshold || heartRate > heartMaxThreshold || spO2 < o2MinThreshold) {
                statusTitle = "Cảnh báo sức khỏe!";
                iconRes = R.drawable.ic_heart_red;
                bgColorHex = "#FEF2F2";
                strokeColorHex = "#FECACA";
                textColorHex = "#DC2626";
            } else if (heartRate < 60 || heartRate > 100 || spO2 < 95) {
                statusTitle = "Cần chú ý!";
                iconRes = R.drawable.ic_heart_red;
                bgColorHex = "#FFFBEB";
                strokeColorHex = "#FDE68A";
                textColorHex = "#B45309";
            } else {
                statusTitle = "Sức khỏe ổn định!";
                iconRes = R.drawable.ic_heart_red;
                bgColorHex = "#F0FDF4";
                strokeColorHex = "#BBF7D0";
                textColorHex = "#15803D";
            }
        } else {
            statusTitle = "Đang kết nối...";
            iconRes = R.drawable.ic_pulse_red;
            bgColorHex = "#F0F9FF";
            strokeColorHex = "#BAE6FD";
            textColorHex = "#0284C7";
        }

        if (txtHealthStatus != null) {
            txtHealthStatus.setText(statusTitle);
            txtHealthStatus.setTextColor(Color.parseColor(textColorHex));
        }
        if (imgHealthIcon != null) {
            imgHealthIcon.setImageResource(iconRes);
        }

        cardHealthToast.setCardBackgroundColor(Color.parseColor(bgColorHex));
        cardHealthToast.setStrokeColor(Color.parseColor(strokeColorHex));
        cardHealthToast.setVisibility(View.VISIBLE);
    }

    // Hàm tìm kiếm đệ quy linh hoạt các giá trị từ Firebase Snapshot
    private Object findChildValue(DataSnapshot snapshot, String... keys) {
        if (snapshot == null || !snapshot.exists()) return null;

        // 1. Kiểm tra trực tiếp tại level hiện tại
        for (String key : keys) {
            for (DataSnapshot child : snapshot.getChildren()) {
                if (child.getKey() != null && child.getKey().equalsIgnoreCase(key)) {
                    if (child.getValue() != null && !child.hasChildren()) {
                        return child.getValue();
                    }
                }
            }
        }

        // 2. Tìm kiếm đệ quy ở các node con sâu hơn (Stream, Test, v.v.)
        for (DataSnapshot child : snapshot.getChildren()) {
            if (child.hasChildren()) {
                Object val = findChildValue(child, keys);
                if (val != null) {
                    return val;
                }
            }
        }

        return null;
    }

    // Xử lý khi có trạng thái Té ngã tự động từ Firebase (Tự động thông báo ngay lập tức)
    private void handleFallAlert(boolean isFall, String statusText) {
        if (isFall) {
            String textToShow = (statusText == null || statusText.isEmpty() || statusText.equals("1") || statusText.equals("true")) ? "PHÁT HIỆN TÉ NGÃ! 🚨" : statusText;
            
            if (textTeNga != null) {
                textTeNga.setText(textToShow);
                textTeNga.setTextColor(Color.RED);
            }
            if (cardTeNga != null) {
                cardTeNga.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
            }

            // Tự động kích hoạt chuông, rung, đẩy thông báo & hiện Hộp thoại Popup Cảnh báo ngay lập tức khi Firebase có tín hiệu té ngã
            if (!isFallAlertShown) {
                isFallAlertShown = true;

                // 1. Tự động phát âm thanh chuông, rung & gửi Thông báo thanh trạng thái
                triggerVibrationAndNotification();

                // 2. Hiển thị ngay Popup Cảnh báo té ngã nổi trên màn hình App
                showFallAlertDialog(textToShow);

                // Toast thông báo trên màn hình
                Toast.makeText(MainActivity.this, "🚨 CẢNH BÁO: " + textToShow, Toast.LENGTH_LONG).show();
            }
        } else {
            isFallAlertShown = false;
            if (currentRingtone != null && currentRingtone.isPlaying()) {
                currentRingtone.stop();
            }
            if (textTeNga != null) {
                textTeNga.setText("Bình thường");
                textTeNga.setTextColor(Color.parseColor("#388E3C"));
            }
            if (cardTeNga != null) {
                cardTeNga.setCardBackgroundColor(Color.WHITE);
            }
        }
    }

    // Phát âm thanh chuông, rung & Thông báo hệ thống
    private void triggerVibrationAndNotification() {
        try {
            // Phát âm thanh chuông cảnh báo
            Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (notificationUri == null) {
                notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            currentRingtone = RingtoneManager.getRingtone(getApplicationContext(), notificationUri);
            if (currentRingtone != null) {
                currentRingtone.play();
            }

            // Rung điện thoại
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        sendSystemNotification();
    }

    // Gửi thông báo trên thanh trạng thái điện thoại
    private void sendSystemNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Cảnh Báo Té Ngã & Sinh Hiệu",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo khẩn cấp khi phát hiện sự cố té ngã hoặc sinh hiệu bất thường");
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_fall)
                .setContentTitle("🚨 CẢNH BÁO SỨC KHỎE KHẨN CẤP!")
                .setContentText("Phát hiện bất thường từ thiết bị! Vui lòng kiểm tra ngay.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true);

        notificationManager.notify(101, builder.build());
    }

    // Hiện hộp thoại cảnh báo khẩn cấp trên màn hình
    private void showFallAlertDialog(String messageText) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("🚨 CẢNH BÁO TÉ NGÃ KHẨN CẤP!")
                .setMessage("Tín hiệu nhận từ Firebase: " + messageText + "\n\nHệ thống phát hiện sự cố té ngã! Vui lòng kiểm tra người dùng ngay lập tức.")
                .setPositiveButton("ĐÃ KIỂM TRA / TẮT CẢNH BÁO", (dialog, which) -> {
                    if (currentRingtone != null && currentRingtone.isPlaying()) {
                        currentRingtone.stop();
                    }
                    isFallAlertShown = false;
                    if (rootRef != null) {
                        rootRef.child("Stream").child("CanhBaoNga").setValue(0);
                        rootRef.child("CanhBaoNga").setValue(0);
                    }
                    if (textTeNga != null) {
                        textTeNga.setText("Bình thường");
                        textTeNga.setTextColor(Color.parseColor("#388E3C"));
                    }
                    if (cardTeNga != null) {
                        cardTeNga.setCardBackgroundColor(Color.WHITE);
                    }
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    // Gửi thông số đo kèm thời gian thực tế sang Google Sheet qua Web App API
    private void sendDataToGoogleSheet(String nhipTim, String oxy, String soLanDap, String teNga, String viTri, String thoiGian) {
        if (GOOGLE_SHEET_WEB_APP_URL == null || GOOGLE_SHEET_WEB_APP_URL.contains("YOUR_SCRIPT_ID")) {
            return;
        }

        new Thread(() -> {
            try {
                String utf8 = StandardCharsets.UTF_8.name();
                String urlString = GOOGLE_SHEET_WEB_APP_URL +
                        "?thoiGian=" + URLEncoder.encode(thoiGian, utf8) +
                        "&nhipTim=" + URLEncoder.encode(nhipTim, utf8) +
                        "&oxy=" + URLEncoder.encode(oxy, utf8) +
                        "&soLanDap=" + URLEncoder.encode(soLanDap, utf8) +
                        "&teNga=" + URLEncoder.encode(teNga, utf8) +
                        "&viTri=" + URLEncoder.encode(viTri, utf8);

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(true);
                HttpURLConnection.setFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 303) {
                    String newUrl = conn.getHeaderField("Location");
                    if (newUrl != null) {
                        conn.disconnect();
                        conn = (HttpURLConnection) new URL(newUrl).openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(15000);
                    }
                }

                // Đọc toàn bộ luồng trả về để bắt buộc HTTP connection thực thi xong trên Google Apps Script
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                is.close();
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Mở Bảng Google Sheet để xem Lịch sử đo khi người dùng bấm vào tab Lịch sử
    private void openGoogleSheetHistory() {
        if (GOOGLE_SHEET_VIEW_URL.isEmpty()) {
            Toast.makeText(this, "Xem lịch sử dữ liệu đo", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_SHEET_VIEW_URL));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở Google Sheet", Toast.LENGTH_SHORT).show();
        }
    }

    // Mở Google Maps hiển thị tọa độ GPS thực tế
    private void openGoogleMaps() {
        String uri = String.format(Locale.ENGLISH, "http://maps.google.com/maps?q=loc:%f,%f(Vị+trí+hiện+tại)", currentLat, currentLng);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception ex) {
            // Nếu thiết bị chưa cài app Google Maps, mở bằng Trình duyệt web
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            startActivity(browserIntent);
        }
    }

    // Kiểm tra & xin quyền định vị GPS
    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
                getAddressFromLocation(currentLat, currentLng);
            } else {
                requestNewLocationData();
            }
        });
    }

    // Yêu cầu cập nhật vị trí mới nếu vị trí cũ chưa có
    private void requestNewLocationData() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdates(1)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult.getLastLocation() != null) {
                        currentLat = locationResult.getLastLocation().getLatitude();
                        currentLng = locationResult.getLastLocation().getLongitude();
                        getAddressFromLocation(currentLat, currentLng);
                    }
                }
            }, Looper.getMainLooper());
        }
    }

    // Chuyển đổi Tọa độ GPS (Vĩ độ, Kinh độ) thành Tên địa chỉ thực tế (Ví dụ: Hà Nội, Việt Nam)
    private void getAddressFromLocation(double latitude, double longitude) {
        new Thread(() -> {
            String addressText = latitude + ", " + longitude;
            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String city = address.getAdminArea();
                    String country = address.getCountryName();
                    String locality = address.getLocality() != null ? address.getLocality() : address.getSubAdminArea();

                    if (locality != null && city != null) {
                        addressText = locality + ", " + city;
                    } else if (city != null && country != null) {
                        addressText = city + ", " + country;
                    } else if (address.getAddressLine(0) != null) {
                        addressText = address.getAddressLine(0);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final String result = addressText;
            runOnUiThread(() -> {
                if (textViTri != null) {
                    textViTri.setText(result);
                }
            });
            updateLocationOnMap(latitude, longitude, "Vị trí người đeo: " + result);
        }).start();
    }

    private void setupMapView() {
        if (osmMapView == null) return;
        try {
            osmMapView.setTileSource(new XYTileSource(
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
            osmMapView.setTileSource(TileSourceFactory.MAPNIK);
        }
        osmMapView.setMultiTouchControls(true);
        osmMapView.setBuiltInZoomControls(false);
        updateLocationOnMap(currentLat, currentLng, "Vị trí người đeo");
    }

    private boolean isMapExpanded = false;

    // Phóng to/Thu nhỏ khung bản đồ OpenStreetMap trực tiếp ngay trên ứng dụng
    private void toggleMapExpand() {
        if (osmMapView == null) return;

        isMapExpanded = !isMapExpanded;
        android.view.ViewGroup.LayoutParams params = osmMapView.getLayoutParams();
        if (isMapExpanded) {
            params.height = (int) (320 * getResources().getDisplayMetrics().density); // Phóng to 320dp
        } else {
            params.height = (int) (160 * getResources().getDisplayMetrics().density); // Thu gọn 160dp
        }
        osmMapView.setLayoutParams(params);

        if (osmMapView.getController() != null) {
            GeoPoint point = new GeoPoint(currentLat, currentLng);
            osmMapView.getController().setZoom(isMapExpanded ? 18.5 : 16.0);
            osmMapView.getController().animateTo(point);
        }
    }

    public void updateLocationOnMap(double lat, double lng, String title) {
        if (osmMapView == null) return;
        runOnUiThread(() -> {
            try {
                GeoPoint point = new GeoPoint(lat, lng);
                osmMapView.getController().setZoom(isMapExpanded ? 18.5 : 16.0);
                osmMapView.getController().setCenter(point);

                osmMapView.getOverlays().clear();
                Marker marker = new Marker(osmMapView);
                marker.setPosition(point);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marker.setTitle(title);

                osmMapView.getOverlays().add(marker);
                osmMapView.invalidate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (osmMapView != null) osmMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (osmMapView != null) osmMapView.onPause();
    }

    // Kết quả phản hồi quyền từ người dùng
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation();
            } else {
                Toast.makeText(this, "Chưa cấp quyền vị trí GPS", Toast.LENGTH_SHORT).show();
            }
        }
    }
}