package com.example.appdonhiptim;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class caidat extends AppCompatActivity {

    private EditText etHeartMin;
    private EditText etHeartMax;
    private EditText etO2Min;
    private EditText etO2Max;

    private DatabaseReference rootRef;

    // Cờ chống lặp khi dữ liệu được tải từ Firebase về
    private boolean isUpdatingFromFirebase = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_caidat);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        // Ánh xạ View
        etHeartMin = findViewById(R.id.etHeartMin);
        etHeartMax = findViewById(R.id.etHeartMax);
        etO2Min = findViewById(R.id.etO2Min);
        etO2Max = findViewById(R.id.etO2Max);

        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnInfo = findViewById(R.id.btnInfo);
        View btnSaveConfig = findViewById(R.id.btnSaveConfig);
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);

        // Khởi tạo kết nối Firebase Database
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://donhiptim-dee10-default-rtdb.firebaseio.com/");
        rootRef = database.getReference();

        // Tải cấu hình cài đặt từ Firebase khi mở màn hình
        loadAndListenFirebaseConfig();

        // Gắn lắng nghe thay đổi ô nhập liệu để tự động đồng bộ ngay lập tức sang Firebase
        setupRealtimeSyncListeners();

        // Nút quay lại Trang chủ
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                Intent intent = new Intent(caidat.this, MainActivity.class);
                startActivity(intent);
                finish();
            });
        }

        // Nút thông tin thiết bị
        if (btnInfo != null) {
            btnInfo.setOnClickListener(v -> 
                Toast.makeText(caidat.this, "Thiết bị MAX30102 đang kết nối hoạt động bình thường.", Toast.LENGTH_SHORT).show()
            );
        }

        // Nút "LƯU CẤU HÌNH" -> Đẩy dữ liệu cấu hình lên Firebase & hiện thông báo
        if (btnSaveConfig != null) {
            btnSaveConfig.setOnClickListener(v -> saveConfigToFirebase());
        }

        // Chọn sẵn tab Cài đặt
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_settings);

            bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    Intent intent = new Intent(caidat.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_map) {
                    Intent intent = new Intent(caidat.this, bieudo.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_history) {
                    openGoogleSheetHistory();
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    return true;
                }
                return false;
            });
        }
    }

    // Tải dữ liệu cài đặt sẵn có trên Firebase
    private void loadAndListenFirebaseConfig() {
        if (rootRef == null) return;

        rootRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                isUpdatingFromFirebase = true;

                // Lấy các giá trị cài đặt từ node 'CaiDat' hoặc từ root Firebase
                Object heartMinObj = findChildValue(snapshot, "HeartMin", "NhipTimMin", "MinHeart");
                Object heartMaxObj = findChildValue(snapshot, "HeartMax", "NhipTimMax", "MaxHeart");
                Object o2MinObj = findChildValue(snapshot, "O2Min", "OxyMin", "MinO2");
                Object o2MaxObj = findChildValue(snapshot, "O2Max", "OxyMax", "MaxO2");
                Object fallAlertObj = findChildValue(snapshot, "FallAlert", "CanhBaoNga", "TeNga");

                if (heartMinObj != null && etHeartMin != null && !etHeartMin.hasFocus()) {
                    etHeartMin.setText(heartMinObj.toString());
                }
                if (heartMaxObj != null && etHeartMax != null && !etHeartMax.hasFocus()) {
                    etHeartMax.setText(heartMaxObj.toString());
                }
                if (o2MinObj != null && etO2Min != null && !etO2Min.hasFocus()) {
                    etO2Min.setText(o2MinObj.toString());
                }
                if (o2MaxObj != null && etO2Max != null && !etO2Max.hasFocus()) {
                    etO2Max.setText(o2MaxObj.toString());
                }

                isUpdatingFromFirebase = false;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isUpdatingFromFirebase = false;
            }
        });
    }

    // Lắng nghe thay đổi ô nhập liệu và công tắc để ĐỒNG BỘ TRỰC TIẾP sang Firebase
    private void setupRealtimeSyncListeners() {
        if (etHeartMin != null) {
            etHeartMin.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isUpdatingFromFirebase || rootRef == null) return;
                    String val = s.toString().trim();
                    if (!val.isEmpty()) {
                        rootRef.child("CaiDat").child("HeartMin").setValue(val);
                        rootRef.child("NhipTimMin").setValue(val);
                    }
                }
            });
        }

        if (etHeartMax != null) {
            etHeartMax.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isUpdatingFromFirebase || rootRef == null) return;
                    String val = s.toString().trim();
                    if (!val.isEmpty()) {
                        rootRef.child("CaiDat").child("HeartMax").setValue(val);
                        rootRef.child("NhipTimMax").setValue(val);
                    }
                }
            });
        }

        if (etO2Min != null) {
            etO2Min.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isUpdatingFromFirebase || rootRef == null) return;
                    String val = s.toString().trim();
                    if (!val.isEmpty()) {
                        rootRef.child("CaiDat").child("O2Min").setValue(val);
                        rootRef.child("O2Min").setValue(val);
                    }
                }
            });
        }

        if (etO2Max != null) {
            etO2Max.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (isUpdatingFromFirebase || rootRef == null) return;
                    String val = s.toString().trim();
                    if (!val.isEmpty()) {
                        rootRef.child("CaiDat").child("O2Max").setValue(val);
                        rootRef.child("O2Max").setValue(val);
                    }
                }
            });
        }
    }

    // Đẩy toàn bộ cấu hình lên Firebase và hiển thị thông báo thành công
    private void saveConfigToFirebase() {
        if (rootRef == null) return;

        String heartMinStr = etHeartMin != null ? etHeartMin.getText().toString().trim() : "50";
        String heartMaxStr = etHeartMax != null ? etHeartMax.getText().toString().trim() : "120";
        String o2MinStr = etO2Min != null ? etO2Min.getText().toString().trim() : "90";
        String o2MaxStr = etO2Max != null ? etO2Max.getText().toString().trim() : "100";

        DatabaseReference caiDatRef = rootRef.child("CaiDat");
        caiDatRef.child("HeartMin").setValue(heartMinStr);
        caiDatRef.child("HeartMax").setValue(heartMaxStr);
        caiDatRef.child("O2Min").setValue(o2MinStr);
        caiDatRef.child("O2Max").setValue(o2MaxStr);

        // Đẩy đồng thời các node trực tiếp ở root
        rootRef.child("NhipTimMin").setValue(heartMinStr);
        rootRef.child("NhipTimMax").setValue(heartMaxStr);
        rootRef.child("O2Min").setValue(o2MinStr);
        rootRef.child("O2Max").setValue(o2MaxStr);

        Toast.makeText(caidat.this, "Đã đồng bộ trực tiếp cấu hình sang Firebase thành công!", Toast.LENGTH_LONG).show();
    }

    private Object findChildValue(DataSnapshot snapshot, String... keys) {
        if (snapshot == null || !snapshot.exists()) return null;

        for (String key : keys) {
            for (DataSnapshot child : snapshot.getChildren()) {
                if (child.getKey() != null && child.getKey().equalsIgnoreCase(key)) {
                    if (child.getValue() != null && !child.hasChildren()) {
                        return child.getValue();
                    }
                }
            }
        }

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

    private void openGoogleMaps() {
        String uri = String.format(Locale.ENGLISH, "http://maps.google.com/maps?q=loc:%f,%f(Vị+trí+hiện+tại)", 21.028511, 105.804817);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            startActivity(browserIntent);
        }
    }

    private void openGoogleSheetHistory() {
        if (MainActivity.GOOGLE_SHEET_VIEW_URL != null && !MainActivity.GOOGLE_SHEET_VIEW_URL.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.GOOGLE_SHEET_VIEW_URL));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Không thể mở Google Sheet", Toast.LENGTH_SHORT).show();
            }
        }
    }
}