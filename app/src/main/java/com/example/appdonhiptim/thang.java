package com.example.appdonhiptim;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Locale;

public class thang extends AppCompatActivity {

    private TextView textTbNhipTimThang;
    private TextView textTbOxyThang;
    private TextView txtHeartChange;
    private TextView txtO2Change;

    private TextView textNhipTimDonut;
    private TextView textOxyDonut;

    private TextView txtHeartNormalPercent;
    private TextView txtHeartHighPercent;
    private TextView txtHeartLowPercent;

    private TextView txtO2GoodPercent;
    private TextView txtO2MediumPercent;
    private TextView txtO2LowPercent;

    private LineChart lineChartThang;

    // Biến tính toán & phân bố dữ liệu thời gian thực
    private double totalHeartSum = 0;
    private int totalHeartCount = 0;
    private int heartNormalCount = 0;
    private int heartHighCount = 0;
    private int heartLowCount = 0;
    private double prevAvgHeart = 0;

    private double totalO2Sum = 0;
    private int totalO2Count = 0;
    private int o2GoodCount = 0;
    private int o2MediumCount = 0;
    private int o2LowCount = 0;
    private double prevAvgO2 = 0;

    private final ArrayList<Entry> heartEntries = new ArrayList<>();
    private final ArrayList<Entry> o2Entries = new ArrayList<>();
    private int chartIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_thang);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        // Ánh xạ View
        textTbNhipTimThang = findViewById(R.id.textTbNhipTimThang);
        textTbOxyThang = findViewById(R.id.textTbOxyThang);
        txtHeartChange = findViewById(R.id.txtHeartChange);
        txtO2Change = findViewById(R.id.txtO2Change);

        textNhipTimDonut = findViewById(R.id.textNhipTimDonut);
        textOxyDonut = findViewById(R.id.textOxyDonut);

        txtHeartNormalPercent = findViewById(R.id.txtHeartNormalPercent);
        txtHeartHighPercent = findViewById(R.id.txtHeartHighPercent);
        txtHeartLowPercent = findViewById(R.id.txtHeartLowPercent);

        txtO2GoodPercent = findViewById(R.id.txtO2GoodPercent);
        txtO2MediumPercent = findViewById(R.id.txtO2MediumPercent);
        txtO2LowPercent = findViewById(R.id.txtO2LowPercent);

        lineChartThang = findViewById(R.id.lineChartThang);
        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tabNgayThang = findViewById(R.id.tabNgayThang);
        TextView tabLichThang = findViewById(R.id.tabLichThang);
        com.google.android.material.card.MaterialCardView cardExportExcel = findViewById(R.id.cardExportExcel);
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);

        // Bắt sự kiện bấm nút Xuất báo cáo Excel
        if (cardExportExcel != null) {
            cardExportExcel.setOnClickListener(v -> HealthHistoryManager.shareExcelReport(thang.this));
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (tabNgayThang != null) {
            tabNgayThang.setOnClickListener(v -> {
                Intent intent = new Intent(thang.this, bieudo.class);
                startActivity(intent);
                finish();
            });
        }

        if (tabLichThang != null) {
            tabLichThang.setOnClickListener(v -> showDatePicker(tabLichThang));
        }

        setupThangChart();
        resetToRealtimeMode();

        // Lắng nghe dữ liệu sinh hiệu thời gian thực từ Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://donhiptim-dee10-default-rtdb.firebaseio.com/");
        DatabaseReference rootRef = database.getReference();

        rootRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                DataSnapshot streamSnap = snapshot.hasChild("Stream") ? snapshot.child("Stream") : snapshot;

                Object nhipTimObj = findChildValue(streamSnap, "NhipTim", "Nhiptim", "BPM", "HeartRate");
                Object oxyObj = findChildValue(streamSnap, "NongDoO2", "Oxy", "SpO2", "O2");

                boolean hasData = false;
                int curHeart = -1;
                int curO2 = -1;

                // 1. Tính toán Nhịp tim & Tỷ lệ tăng giảm chính xác
                if (nhipTimObj != null) {
                    try {
                        curHeart = Integer.parseInt(nhipTimObj.toString().replaceAll("[^0-9]", ""));
                        if (curHeart > 30 && curHeart < 220) {
                            hasData = true;
                            totalHeartSum += curHeart;
                            totalHeartCount++;

                            if (curHeart >= 60 && curHeart <= 100) {
                                heartNormalCount++;
                            } else if (curHeart > 100) {
                                heartHighCount++;
                            } else {
                                heartLowCount++;
                            }

                            double newAvgHeart = totalHeartSum / totalHeartCount;
                            if (textTbNhipTimThang != null) {
                                textTbNhipTimThang.setText(Math.round(newAvgHeart) + " bpm");
                            }
                            if (textNhipTimDonut != null) {
                                textNhipTimDonut.setText(String.valueOf(curHeart));
                            }

                            // Tính % Tăng/Giảm so với trung bình trước đó
                            if (txtHeartChange != null) {
                                if (prevAvgHeart > 0) {
                                    double diff = newAvgHeart - prevAvgHeart;
                                    double pct = (diff / prevAvgHeart) * 100.0;
                                    if (pct > 0) {
                                        txtHeartChange.setText(String.format(Locale.getDefault(), "↑ %.1f%%", pct));
                                        txtHeartChange.setTextColor(Color.parseColor("#10B981"));
                                    } else if (pct < 0) {
                                        txtHeartChange.setText(String.format(Locale.getDefault(), "↓ %.1f%%", Math.abs(pct)));
                                        txtHeartChange.setTextColor(Color.parseColor("#10B981"));
                                    } else {
                                        txtHeartChange.setText("0%");
                                        txtHeartChange.setTextColor(Color.parseColor("#10B981"));
                                    }
                                } else {
                                    txtHeartChange.setText("0%");
                                }
                            }
                            prevAvgHeart = newAvgHeart;

                            // Cập nhật tỷ lệ % phân bố nhịp tim
                            if (txtHeartNormalPercent != null)
                                txtHeartNormalPercent.setText(Math.round((float) heartNormalCount * 100 / totalHeartCount) + "%");
                            if (txtHeartHighPercent != null)
                                txtHeartHighPercent.setText(Math.round((float) heartHighCount * 100 / totalHeartCount) + "%");
                            if (txtHeartLowPercent != null)
                                txtHeartLowPercent.setText(Math.round((float) heartLowCount * 100 / totalHeartCount) + "%");
                        }
                    } catch (Exception ignored) {}
                }

                // 2. Tính toán SpO2 & Tỷ lệ tăng giảm chính xác
                if (oxyObj != null) {
                    try {
                        curO2 = Integer.parseInt(oxyObj.toString().replaceAll("[^0-9]", ""));
                        if (curO2 > 50 && curO2 <= 100) {
                            hasData = true;
                            totalO2Sum += curO2;
                            totalO2Count++;

                            if (curO2 >= 95) {
                                o2GoodCount++;
                            } else if (curO2 >= 90) {
                                o2MediumCount++;
                            } else {
                                o2LowCount++;
                            }

                            double newAvgO2 = totalO2Sum / totalO2Count;
                            if (textTbOxyThang != null) {
                                textTbOxyThang.setText(String.format(Locale.getDefault(), "%.1f %%", newAvgO2));
                            }
                            if (textOxyDonut != null) {
                                textOxyDonut.setText(String.valueOf(curO2));
                            }

                            // Tính % Tăng/Giảm so với trung bình trước đó
                            if (txtO2Change != null) {
                                if (prevAvgO2 > 0) {
                                    double diff = newAvgO2 - prevAvgO2;
                                    double pct = (diff / prevAvgO2) * 100.0;
                                    if (pct > 0) {
                                        txtO2Change.setText(String.format(Locale.getDefault(), "↑ %.1f%%", pct));
                                        txtO2Change.setTextColor(Color.parseColor("#10B981"));
                                    } else if (pct < 0) {
                                        txtO2Change.setText(String.format(Locale.getDefault(), "↓ %.1f%%", Math.abs(pct)));
                                        txtO2Change.setTextColor(Color.parseColor("#10B981"));
                                    } else {
                                        txtO2Change.setText("0%");
                                        txtO2Change.setTextColor(Color.parseColor("#10B981"));
                                    }
                                } else {
                                    txtO2Change.setText("0%");
                                }
                            }
                            prevAvgO2 = newAvgO2;

                            // Cập nhật tỷ lệ % phân bố SpO2
                            if (txtO2GoodPercent != null)
                                txtO2GoodPercent.setText(Math.round((float) o2GoodCount * 100 / totalO2Count) + "%");
                            if (txtO2MediumPercent != null)
                                txtO2MediumPercent.setText(Math.round((float) o2MediumCount * 100 / totalO2Count) + "%");
                            if (txtO2LowPercent != null)
                                txtO2LowPercent.setText(Math.round((float) o2LowCount * 100 / totalO2Count) + "%");
                        }
                    } catch (Exception ignored) {}
                }

                // 3. Vẽ dữ liệu thực tế lên biểu đồ LineChart & Tự động lưu lịch sử đo
                if (hasData && curHeart > 0 && curO2 > 0) {
                    HealthHistoryManager.saveReading(thang.this, curHeart, curO2);
                    addRealtimeDataToChart(curHeart, curO2);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

        // Bottom Navigation Bar
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_map);

            bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    Intent intent = new Intent(thang.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_map) {
                    Intent intent = new Intent(thang.this, bieudo.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_history) {
                    openGoogleSheetHistory();
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    Intent intent = new Intent(thang.this, caidat.class);
                    startActivity(intent);
                    finish();
                    return true;
                }
                return false;
            });
        }
    }

    // Cấu hình Biểu đồ trục đúp (Trục trái Nhịp tim, Trục phải SpO2, Trục X Thứ T2-CN)
    private void setupThangChart() {
        if (lineChartThang == null) return;

        lineChartThang.getDescription().setEnabled(false);
        lineChartThang.setDrawGridBackground(false);
        lineChartThang.setTouchEnabled(true);
        lineChartThang.setDragEnabled(true);
        lineChartThang.setScaleEnabled(true);

        // Trục Y bên trái: Nhịp tim (bpm: 35 - 160)
        YAxis leftAxis = lineChartThang.getAxisLeft();
        leftAxis.setAxisMinimum(35f);
        leftAxis.setAxisMaximum(160f);
        leftAxis.setTextColor(Color.parseColor("#EF4444"));
        leftAxis.setGridColor(Color.parseColor("#F1F5F9"));
        leftAxis.setTextSize(10f);

        // Trục Y bên phải: Nồng độ SpO2 (%: 88 - 100)
        YAxis rightAxis = lineChartThang.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setAxisMinimum(88f);
        rightAxis.setAxisMaximum(100f);
        rightAxis.setTextColor(Color.parseColor("#2563EB"));
        rightAxis.setGridColor(Color.parseColor("#F1F5F9"));
        rightAxis.setTextSize(10f);

        // Trục X bên dưới: Thứ trong tuần (T2, T3, T4, T5, T6, T7, CN)
        XAxis xAxis = lineChartThang.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#64748B"));
        xAxis.setTextSize(11f);
        String[] days = new String[]{"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        xAxis.setValueFormatter(new IndexAxisValueFormatter(days));
        xAxis.setGranularity(1f);
    }

    // Vẽ đường sinh hiệu thời gian thực lên biểu đồ
    private void addRealtimeDataToChart(int heartRate, int spO2) {
        int xPos = chartIndex % 7; // Vòng lặp các thứ T2 - CN

        heartEntries.add(new Entry(xPos, heartRate));
        o2Entries.add(new Entry(xPos, spO2));

        LineDataSet heartDataSet = new LineDataSet(heartEntries, "Nhịp tim (bpm)");
        heartDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        heartDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        heartDataSet.setCubicIntensity(0.2f);
        heartDataSet.setColor(Color.parseColor("#EF4444"));
        heartDataSet.setCircleColor(Color.parseColor("#EF4444"));
        heartDataSet.setLineWidth(3.5f);
        heartDataSet.setCircleRadius(5.5f);
        heartDataSet.setCircleHoleRadius(2.5f);
        heartDataSet.setDrawCircleHole(true);
        heartDataSet.setCircleHoleColor(Color.WHITE);
        heartDataSet.setDrawFilled(true);
        heartDataSet.setFillColor(Color.parseColor("#EF4444"));
        heartDataSet.setFillAlpha(35);
        heartDataSet.setDrawValues(false);

        LineDataSet o2DataSet = new LineDataSet(o2Entries, "SpO2 (%)");
        o2DataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        o2DataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        o2DataSet.setCubicIntensity(0.2f);
        o2DataSet.setColor(Color.parseColor("#2563EB"));
        o2DataSet.setCircleColor(Color.parseColor("#2563EB"));
        o2DataSet.setLineWidth(3.5f);
        o2DataSet.setCircleRadius(5.5f);
        o2DataSet.setCircleHoleRadius(2.5f);
        o2DataSet.setDrawCircleHole(true);
        o2DataSet.setCircleHoleColor(Color.WHITE);
        o2DataSet.setDrawFilled(true);
        o2DataSet.setFillColor(Color.parseColor("#2563EB"));
        o2DataSet.setFillAlpha(35);
        o2DataSet.setDrawValues(false);

        LineData lineData = new LineData(heartDataSet, o2DataSet);
        if (lineChartThang != null) {
            lineChartThang.setData(lineData);
            lineChartThang.notifyDataSetChanged();
            lineChartThang.invalidate();
        }

        chartIndex++;
    }

    // Reset về trạng thái chờ/đo thời gian thực ban đầu khi chưa/đang đo
    private void resetToRealtimeMode() {
        if (textTbNhipTimThang != null) textTbNhipTimThang.setText("0 bpm");
        if (textTbOxyThang != null) textTbOxyThang.setText("0 %");
        if (txtHeartChange != null) txtHeartChange.setText("0%");
        if (txtO2Change != null) txtO2Change.setText("0%");

        if (textNhipTimDonut != null) textNhipTimDonut.setText("0");
        if (textOxyDonut != null) textOxyDonut.setText("0");

        if (txtHeartNormalPercent != null) txtHeartNormalPercent.setText("0%");
        if (txtHeartHighPercent != null) txtHeartHighPercent.setText("0%");
        if (txtHeartLowPercent != null) txtHeartLowPercent.setText("0%");

        if (txtO2GoodPercent != null) txtO2GoodPercent.setText("0%");
        if (txtO2MediumPercent != null) txtO2MediumPercent.setText("0%");
        if (txtO2LowPercent != null) txtO2LowPercent.setText("0%");

        chartIndex = 0;
        heartEntries.clear();
        o2Entries.clear();

        if (lineChartThang != null) {
            lineChartThang.clear();
            lineChartThang.invalidate();
        }
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

    // Hiển thị Lịch chọn ngày tùy chỉnh để lọc xem sức khỏe theo ngày
    private void showDatePicker(TextView tabLich) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int year = calendar.get(java.util.Calendar.YEAR);
        int month = calendar.get(java.util.Calendar.MONTH);
        int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear);
                    if (tabLich != null) {
                        tabLich.setText(selectedDate);
                    }
                    HealthHistoryManager.DailyRecord record = HealthHistoryManager.getDailyRecord(thang.this, selectedDate);
                    displayHistoricalRecord(record);
                    if (record.totalReadings > 0) {
                        Toast.makeText(thang.this, "Đã tải " + record.totalReadings + " lượt đo ngày " + selectedDate, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(thang.this, "Chưa có dữ liệu lịch sử đo ngày " + selectedDate, Toast.LENGTH_SHORT).show();
                    }
                },
                year, month, day
        );
        datePickerDialog.show();
    }

    // Hiển thị dữ liệu biểu đồ và thống kê đã lưu lịch sử cho ngày được chọn từ Lịch
    private void displayHistoricalRecord(HealthHistoryManager.DailyRecord record) {
        if (record == null) return;

        if (textTbNhipTimThang != null) textTbNhipTimThang.setText(record.totalReadings > 0 ? record.avgHeart + " bpm" : "0 bpm");
        if (textTbOxyThang != null) textTbOxyThang.setText(record.totalReadings > 0 ? String.format(Locale.getDefault(), "%.1f %%", record.avgO2) : "0 %");

        if (textNhipTimDonut != null) textNhipTimDonut.setText(record.totalReadings > 0 ? String.valueOf(record.avgHeart) : "0");
        if (textOxyDonut != null) textOxyDonut.setText(record.totalReadings > 0 ? String.format(Locale.getDefault(), "%.0f", record.avgO2) : "0");

        if (txtHeartNormalPercent != null)
            txtHeartNormalPercent.setText(record.totalReadings > 0 ? Math.round((float) record.heartNormalCount * 100 / record.totalReadings) + "%" : "0%");
        if (txtHeartHighPercent != null)
            txtHeartHighPercent.setText(record.totalReadings > 0 ? Math.round((float) record.heartHighCount * 100 / record.totalReadings) + "%" : "0%");
        if (txtHeartLowPercent != null)
            txtHeartLowPercent.setText(record.totalReadings > 0 ? Math.round((float) record.heartLowCount * 100 / record.totalReadings) + "%" : "0%");

        if (txtO2GoodPercent != null)
            txtO2GoodPercent.setText(record.totalReadings > 0 ? Math.round((float) record.o2GoodCount * 100 / record.totalReadings) + "%" : "0%");
        if (txtO2MediumPercent != null)
            txtO2MediumPercent.setText(record.totalReadings > 0 ? Math.round((float) record.o2MediumCount * 100 / record.totalReadings) + "%" : "0%");
        if (txtO2LowPercent != null)
            txtO2LowPercent.setText(record.totalReadings > 0 ? Math.round((float) record.o2LowCount * 100 / record.totalReadings) + "%" : "0%");

        // Re-render chart
        heartEntries.clear();
        o2Entries.clear();

        for (int i = 0; i < record.points.size(); i++) {
            HealthHistoryManager.HealthPoint pt = record.points.get(i);
            int xPos = i % 7;
            heartEntries.add(new Entry(xPos, pt.heartRate));
            o2Entries.add(new Entry(xPos, pt.spO2));
        }

        if (heartEntries.isEmpty()) {
            if (lineChartThang != null) { lineChartThang.clear(); lineChartThang.invalidate(); }
        } else {
            LineDataSet heartDataSet = new LineDataSet(heartEntries, "Nhịp tim (bpm)");
            heartDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
            heartDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            heartDataSet.setCubicIntensity(0.2f);
            heartDataSet.setColor(Color.parseColor("#EF4444"));
            heartDataSet.setCircleColor(Color.parseColor("#EF4444"));
            heartDataSet.setLineWidth(3.5f);
            heartDataSet.setCircleRadius(5.5f);
            heartDataSet.setCircleHoleRadius(2.5f);
            heartDataSet.setDrawCircleHole(true);
            heartDataSet.setCircleHoleColor(Color.WHITE);
            heartDataSet.setDrawValues(false);

            LineDataSet o2DataSet = new LineDataSet(o2Entries, "SpO2 (%)");
            o2DataSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
            o2DataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            o2DataSet.setCubicIntensity(0.2f);
            o2DataSet.setColor(Color.parseColor("#2563EB"));
            o2DataSet.setCircleColor(Color.parseColor("#2563EB"));
            o2DataSet.setLineWidth(3.5f);
            o2DataSet.setCircleRadius(5.5f);
            o2DataSet.setCircleHoleRadius(2.5f);
            o2DataSet.setDrawCircleHole(true);
            o2DataSet.setCircleHoleColor(Color.WHITE);
            o2DataSet.setDrawValues(false);

            LineData lineData = new LineData(heartDataSet, o2DataSet);
            if (lineChartThang != null) {
                lineChartThang.setData(lineData);
                lineChartThang.notifyDataSetChanged();
                lineChartThang.invalidate();
            }
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