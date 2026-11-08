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

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Locale;

public class bieudo extends AppCompatActivity {

    private TextView textNhipTimBieudo;
    private TextView textOxyBieudo;
    private TextView txtHeartRange;
    private TextView txtO2Range;
    private TextView txtHeartMax;
    private TextView txtHeartMin;
    private TextView txtHeartAvg;
    private TextView txtO2Max;
    private TextView txtO2Min;
    private TextView txtO2Avg;

    private LineChart lineChartHeart;
    private LineChart lineChartO2;
    private BarChart heartBarChart;
    private BarChart o2BarChart;

    // Biến tính toán thống kê thực tế
    private int heartMin = 999;
    private int heartMax = 0;
    private long heartSum = 0;
    private int heartCount = 0;

    private int o2Min = 100;
    private int o2Max = 0;
    private long o2Sum = 0;
    private int o2Count = 0;

    // Data for charts
    private final ArrayList<Entry> heartLineEntries = new ArrayList<>();
    private final ArrayList<Entry> o2LineEntries = new ArrayList<>();
    private final ArrayList<BarEntry> heartBarEntries = new ArrayList<>();
    private final ArrayList<BarEntry> o2BarEntries = new ArrayList<>();
    private int timeIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_bieudo);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        // Ánh xạ View
        textNhipTimBieudo = findViewById(R.id.textNhipTimBieudo);
        textOxyBieudo = findViewById(R.id.textOxyBieudo);
        txtHeartRange = findViewById(R.id.txtHeartRange);
        txtO2Range = findViewById(R.id.txtO2Range);
        txtHeartMax = findViewById(R.id.txtHeartMax);
        txtHeartMin = findViewById(R.id.txtHeartMin);
        txtHeartAvg = findViewById(R.id.txtHeartAvg);
        txtO2Max = findViewById(R.id.txtO2Max);
        txtO2Min = findViewById(R.id.txtO2Min);
        txtO2Avg = findViewById(R.id.txtO2Avg);

        lineChartHeart = findViewById(R.id.lineChartHeart);
        lineChartO2 = findViewById(R.id.lineChartO2);
        heartBarChart = findViewById(R.id.heartBarChart);
        o2BarChart = findViewById(R.id.o2BarChart);

        setupCharts();

        // Khởi tạo màn hình ở trạng thái thời gian thực ban đầu (khi chưa đo sẽ chưa có đường biểu diễn)
        resetToRealtimeMode();

        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnSettingsHeader = findViewById(R.id.btnSettingsHeader);
        TextView tabHomNay = findViewById(R.id.tabHomNay);
        TextView tabTuan = findViewById(R.id.tabTuan);
        TextView tabThang = findViewById(R.id.tabThang);
        TextView tabLich = findViewById(R.id.tabLich);
        com.google.android.material.card.MaterialCardView cardExportExcel = findViewById(R.id.cardExportExcel);
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);

        // Bắt sự kiện bấm nút Xuất báo cáo Excel
        if (cardExportExcel != null) {
            cardExportExcel.setOnClickListener(v -> HealthHistoryManager.shareExcelReport(bieudo.this));
        }

        // Bắt sự kiện bấm "Hôm nay" để quay lại chế độ đo thời gian thực
        if (tabHomNay != null) {
            tabHomNay.setOnClickListener(v -> resetToRealtimeMode());
        }

        // Bắt sự kiện bấm Lịch để hiển thị Lịch chọn ngày tùy chỉnh
        if (tabLich != null) {
            tabLich.setOnClickListener(v -> showDatePicker(tabLich));
        }

        // Bắt sự kiện bấm chữ Tuần hoặc Tháng để chuyển sang màn hình Thống kê Tuần/Tháng (activity_thang)
        android.view.View.OnClickListener openThangListener = v -> {
            Intent intent = new Intent(bieudo.this, thang.class);
            startActivity(intent);
        };

        if (tabTuan != null) {
            tabTuan.setOnClickListener(openThangListener);
        }
        if (tabThang != null) {
            tabThang.setOnClickListener(openThangListener);
        }

        // Nút quay lại Trang chủ
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Nút mở cài đặt ở góc trên
        if (btnSettingsHeader != null) {
            btnSettingsHeader.setOnClickListener(v -> {
                Intent intent = new Intent(bieudo.this, caidat.class);
                startActivity(intent);
            });
        }

        // Lắng nghe dữ liệu sinh hiệu từ Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://donhiptim-dee10-default-rtdb.firebaseio.com/");
        DatabaseReference rootRef = database.getReference();

        rootRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                // Ưu tiên đọc từ node 'Stream'
                DataSnapshot streamSnap = snapshot.hasChild("Stream") ? snapshot.child("Stream") : snapshot;

                Object nhipTimObj = findChildValue(streamSnap, "NhipTim", "Nhiptim", "BPM", "HeartRate");
                Object oxyObj = findChildValue(streamSnap, "NongDoO2", "Oxy", "SpO2", "O2");

                // Tính toán Thống kê Nhịp tim thực tế
                if (nhipTimObj != null) {
                    try {
                        int curHeart = Integer.parseInt(nhipTimObj.toString().replaceAll("[^0-9]", ""));
                        if (curHeart > 30 && curHeart < 220) {
                            if (textNhipTimBieudo != null) textNhipTimBieudo.setText(String.valueOf(curHeart));

                            heartSum += curHeart;
                            heartCount++;
                            int avg = (int) (heartSum / heartCount);

                            if (curHeart < heartMin) heartMin = curHeart;
                            if (curHeart > heartMax) heartMax = curHeart;

                            if (txtHeartRange != null) txtHeartRange.setText(heartMin + " - " + heartMax + " bpm");
                            if (txtHeartMax != null) txtHeartMax.setText(heartMax + " bpm");
                            if (txtHeartMin != null) txtHeartMin.setText(heartMin + " bpm");
                            if (txtHeartAvg != null) txtHeartAvg.setText(avg + " bpm");
                        }
                    } catch (Exception ignored) {}
                }

                // Tính toán Thống kê SpO2 thực tế
                if (oxyObj != null) {
                    try {
                        int curO2 = Integer.parseInt(oxyObj.toString().replaceAll("[^0-9]", ""));
                        if (curO2 > 50 && curO2 <= 100) {
                            if (textOxyBieudo != null) textOxyBieudo.setText(String.valueOf(curO2));

                            o2Sum += curO2;
                            o2Count++;
                            int avg = (int) (o2Sum / o2Count);

                            if (curO2 < o2Min) o2Min = curO2;
                            if (curO2 > o2Max) o2Max = curO2;

                            if (txtO2Range != null) txtO2Range.setText(o2Min + " - " + o2Max + " %");
                            if (txtO2Max != null) txtO2Max.setText(o2Max + " %");
                            if (txtO2Min != null) txtO2Min.setText(o2Min + " %");
                            if (txtO2Avg != null) txtO2Avg.setText(avg + " %");
                        }
                    } catch (Exception ignored) {}
                }

                // Cập nhật biểu đồ theo thời gian thực & Tự động lưu lịch sử đo
                if (nhipTimObj != null && oxyObj != null) {
                    try {
                        int curHeart = Integer.parseInt(nhipTimObj.toString().replaceAll("[^0-9]", ""));
                        int curO2 = Integer.parseInt(oxyObj.toString().replaceAll("[^0-9]", ""));
                        if (curHeart > 30 && curHeart < 220 && curO2 > 50 && curO2 <= 100) {
                            HealthHistoryManager.saveReading(bieudo.this, curHeart, curO2);
                            addEntryToCharts(curHeart, curO2);
                        }
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

        // Cấu hình Bottom Navigation Bar
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_map);

            bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    Intent intent = new Intent(bieudo.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_map) {
                    return true;
                } else if (itemId == R.id.nav_history) {
                    openGoogleSheetHistory();
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    Intent intent = new Intent(bieudo.this, caidat.class);
                    startActivity(intent);
                    finish();
                    return true;
                }
                return false;
            });
        }
    }

    // Khôi phục về chế độ đo thời gian thực ban đầu (chưa đo sẽ hiện trống)
    private void resetToRealtimeMode() {
        if (textNhipTimBieudo != null) textNhipTimBieudo.setText("--");
        if (textOxyBieudo != null) textOxyBieudo.setText("--");

        if (txtHeartRange != null) txtHeartRange.setText("0 - 0 bpm");
        if (txtO2Range != null) txtO2Range.setText("0 - 0 %");

        if (txtHeartMax != null) txtHeartMax.setText("0 bpm");
        if (txtHeartMin != null) txtHeartMin.setText("0 bpm");
        if (txtHeartAvg != null) txtHeartAvg.setText("0 bpm");

        if (txtO2Max != null) txtO2Max.setText("0 %");
        if (txtO2Min != null) txtO2Min.setText("0 %");
        if (txtO2Avg != null) txtO2Avg.setText("0 %");

        heartMin = 999;
        heartMax = 0;
        heartSum = 0;
        heartCount = 0;

        o2Min = 100;
        o2Max = 0;
        o2Sum = 0;
        o2Count = 0;

        timeIndex = 0;
        heartLineEntries.clear();
        o2LineEntries.clear();
        heartBarEntries.clear();
        o2BarEntries.clear();

        if (lineChartHeart != null) { lineChartHeart.clear(); lineChartHeart.invalidate(); }
        if (lineChartO2 != null) { lineChartO2.clear(); lineChartO2.invalidate(); }
        if (heartBarChart != null) { heartBarChart.clear(); heartBarChart.invalidate(); }
        if (o2BarChart != null) { o2BarChart.clear(); o2BarChart.invalidate(); }
    }

    // Thiết lập tùy chỉnh 2 biểu đồ đường nhịp tim & SpO2 riêng biệt cùng biểu đồ cột
    private void setupCharts() {
        android.view.View.OnTouchListener chartTouchListener = (v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        };

        if (lineChartHeart != null) {
            lineChartHeart.getDescription().setEnabled(false);
            lineChartHeart.setDrawGridBackground(false);
            lineChartHeart.getAxisRight().setEnabled(false);
            lineChartHeart.setExtraOffsets(8f, 12f, 8f, 8f);

            YAxis leftAxis = lineChartHeart.getAxisLeft();
            leftAxis.setAxisMinimum(35f);
            leftAxis.setAxisMaximum(160f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.parseColor("#F1F5F9"));
            leftAxis.setTextColor(Color.parseColor("#EF4444"));

            XAxis xAxis = lineChartHeart.getXAxis();
            xAxis.setDrawGridLines(false);
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setTextColor(Color.parseColor("#64748B"));

            lineChartHeart.setTouchEnabled(true);
            lineChartHeart.setDragEnabled(true);
            lineChartHeart.setScaleEnabled(true);
            lineChartHeart.setPinchZoom(true);
            lineChartHeart.setDoubleTapToZoomEnabled(true);
            lineChartHeart.setOnTouchListener(chartTouchListener);
            lineChartHeart.animateX(800);
        }

        if (lineChartO2 != null) {
            lineChartO2.getDescription().setEnabled(false);
            lineChartO2.setDrawGridBackground(false);
            lineChartO2.getAxisRight().setEnabled(false);
            lineChartO2.setExtraOffsets(8f, 12f, 8f, 8f);

            YAxis leftAxis = lineChartO2.getAxisLeft();
            leftAxis.setAxisMinimum(85f);
            leftAxis.setAxisMaximum(102f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.parseColor("#F1F5F9"));
            leftAxis.setTextColor(Color.parseColor("#2563EB"));

            XAxis xAxis = lineChartO2.getXAxis();
            xAxis.setDrawGridLines(false);
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setTextColor(Color.parseColor("#64748B"));

            lineChartO2.setTouchEnabled(true);
            lineChartO2.setDragEnabled(true);
            lineChartO2.setScaleEnabled(true);
            lineChartO2.setPinchZoom(true);
            lineChartO2.setDoubleTapToZoomEnabled(true);
            lineChartO2.setOnTouchListener(chartTouchListener);
            lineChartO2.animateX(800);
        }

        if (heartBarChart != null) {
            heartBarChart.getDescription().setEnabled(false);
            heartBarChart.setDrawGridBackground(false);
            heartBarChart.getAxisRight().setEnabled(false);
            heartBarChart.setExtraOffsets(4f, 8f, 4f, 4f);

            YAxis leftAxis = heartBarChart.getAxisLeft();
            leftAxis.setAxisMinimum(35f);
            leftAxis.setAxisMaximum(160f);
            leftAxis.setGridColor(Color.parseColor("#F1F5F9"));
            leftAxis.setTextColor(Color.parseColor("#64748B"));

            heartBarChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
            heartBarChart.getXAxis().setDrawGridLines(false);
            heartBarChart.getXAxis().setTextColor(Color.parseColor("#64748B"));
            heartBarChart.animateY(800);
        }

        if (o2BarChart != null) {
            o2BarChart.getDescription().setEnabled(false);
            o2BarChart.setDrawGridBackground(false);
            o2BarChart.getAxisRight().setEnabled(false);
            o2BarChart.setExtraOffsets(4f, 8f, 4f, 4f);

            YAxis leftAxis = o2BarChart.getAxisLeft();
            leftAxis.setAxisMinimum(85f);
            leftAxis.setAxisMaximum(102f);
            leftAxis.setGridColor(Color.parseColor("#F1F5F9"));
            leftAxis.setTextColor(Color.parseColor("#64748B"));

            o2BarChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
            o2BarChart.getXAxis().setDrawGridLines(false);
            o2BarChart.getXAxis().setTextColor(Color.parseColor("#64748B"));
            o2BarChart.animateY(800);
        }
    }

    // Thêm điểm dữ liệu mới vào 2 biểu đồ đường riêng biệt & 2 biểu đồ cột
    private void addEntryToCharts(int heartRate, int spO2) {
        heartLineEntries.add(new Entry(timeIndex, heartRate));
        o2LineEntries.add(new Entry(timeIndex, spO2));

        // Line Chart Nhịp tim
        LineDataSet heartDataSet = new LineDataSet(heartLineEntries, "Nhịp tim (bpm)");
        heartDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        heartDataSet.setCubicIntensity(0.2f);
        heartDataSet.setDrawFilled(true);
        heartDataSet.setFillColor(Color.parseColor("#EF4444"));
        heartDataSet.setFillAlpha(35);
        heartDataSet.setColor(Color.parseColor("#EF4444"));
        heartDataSet.setCircleColor(Color.parseColor("#EF4444"));
        heartDataSet.setLineWidth(3.5f);
        heartDataSet.setCircleRadius(5.5f);
        heartDataSet.setCircleHoleRadius(2.5f);
        heartDataSet.setDrawCircleHole(true);
        heartDataSet.setCircleHoleColor(Color.WHITE);
        heartDataSet.setHighlightEnabled(true);
        heartDataSet.setHighLightColor(Color.parseColor("#94A3B8"));
        heartDataSet.setDrawValues(false);

        if (lineChartHeart != null) {
            lineChartHeart.setData(new LineData(heartDataSet));
            lineChartHeart.notifyDataSetChanged();
            lineChartHeart.invalidate();
        }

        // Line Chart SpO2
        LineDataSet o2DataSet = new LineDataSet(o2LineEntries, "SpO2 (%)");
        o2DataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        o2DataSet.setCubicIntensity(0.2f);
        o2DataSet.setDrawFilled(true);
        o2DataSet.setFillColor(Color.parseColor("#2563EB"));
        o2DataSet.setFillAlpha(35);
        o2DataSet.setColor(Color.parseColor("#2563EB"));
        o2DataSet.setCircleColor(Color.parseColor("#2563EB"));
        o2DataSet.setLineWidth(3.5f);
        o2DataSet.setCircleRadius(5.5f);
        o2DataSet.setCircleHoleRadius(2.5f);
        o2DataSet.setDrawCircleHole(true);
        o2DataSet.setCircleHoleColor(Color.WHITE);
        o2DataSet.setHighlightEnabled(true);
        o2DataSet.setHighLightColor(Color.parseColor("#94A3B8"));
        o2DataSet.setDrawValues(false);
        o2DataSet.setCircleHoleColor(Color.WHITE);
        o2DataSet.setDrawValues(false);

        if (lineChartO2 != null) {
            lineChartO2.setData(new LineData(o2DataSet));
            lineChartO2.notifyDataSetChanged();
            lineChartO2.invalidate();
        }

        // Bar Charts
        heartBarEntries.add(new BarEntry(timeIndex, heartRate));
        BarDataSet heartBarDataSet = new BarDataSet(heartBarEntries, "Nhịp tim");
        heartBarDataSet.setColor(Color.parseColor("#F87171"));
        heartBarDataSet.setDrawValues(false);
        if (heartBarChart != null) {
            heartBarChart.setData(new BarData(heartBarDataSet));
            heartBarChart.notifyDataSetChanged();
            heartBarChart.invalidate();
        }

        o2BarEntries.add(new BarEntry(timeIndex, spO2));
        BarDataSet o2BarDataSet = new BarDataSet(o2BarEntries, "SpO2");
        o2BarDataSet.setColor(Color.parseColor("#3B82F6"));
        o2BarDataSet.setDrawValues(false);
        if (o2BarChart != null) {
            o2BarChart.setData(new BarData(o2BarDataSet));
            o2BarChart.notifyDataSetChanged();
            o2BarChart.invalidate();
        }

        timeIndex++;
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
                    HealthHistoryManager.DailyRecord record = HealthHistoryManager.getDailyRecord(bieudo.this, selectedDate);
                    displayHistoricalRecord(record);
                    if (record.totalReadings > 0) {
                        Toast.makeText(bieudo.this, "Đã tải " + record.totalReadings + " lượt đo ngày " + selectedDate, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(bieudo.this, "Chưa có dữ liệu lịch sử đo ngày " + selectedDate, Toast.LENGTH_SHORT).show();
                    }
                },
                year, month, day
        );
        datePickerDialog.show();
    }

    // Hiển thị dữ liệu biểu đồ và thống kê đã lưu lịch sử cho ngày được chọn từ Lịch
    private void displayHistoricalRecord(HealthHistoryManager.DailyRecord record) {
        if (record == null) return;

        if (textNhipTimBieudo != null) textNhipTimBieudo.setText(record.totalReadings > 0 ? String.valueOf(record.avgHeart) : "0");
        if (textOxyBieudo != null) textOxyBieudo.setText(record.totalReadings > 0 ? String.format(Locale.getDefault(), "%.1f", record.avgO2) : "0");

        if (txtHeartRange != null) txtHeartRange.setText(record.minHeart + " - " + record.maxHeart + " bpm");
        if (txtO2Range != null) txtO2Range.setText(record.minO2 + " - " + record.maxO2 + " %");

        if (txtHeartMax != null) txtHeartMax.setText(record.maxHeart + " bpm");
        if (txtHeartMin != null) txtHeartMin.setText(record.minHeart + " bpm");
        if (txtHeartAvg != null) txtHeartAvg.setText(record.avgHeart + " bpm");

        if (txtO2Max != null) txtO2Max.setText(record.maxO2 + " %");
        if (txtO2Min != null) txtO2Min.setText(record.minO2 + " %");
        if (txtO2Avg != null) txtO2Avg.setText(String.format(Locale.getDefault(), "%.1f %%", record.avgO2));

        // Re-render chart data
        heartLineEntries.clear();
        o2LineEntries.clear();
        heartBarEntries.clear();
        o2BarEntries.clear();

        for (int i = 0; i < record.points.size(); i++) {
            HealthHistoryManager.HealthPoint pt = record.points.get(i);
            heartLineEntries.add(new Entry(i, pt.heartRate));
            o2LineEntries.add(new Entry(i, pt.spO2));
            heartBarEntries.add(new BarEntry(i, pt.heartRate));
            o2BarEntries.add(new BarEntry(i, pt.spO2));
        }

        if (heartLineEntries.isEmpty()) {
            if (lineChartHeart != null) { lineChartHeart.clear(); lineChartHeart.invalidate(); }
            if (lineChartO2 != null) { lineChartO2.clear(); lineChartO2.invalidate(); }
            if (heartBarChart != null) { heartBarChart.clear(); heartBarChart.invalidate(); }
            if (o2BarChart != null) { o2BarChart.clear(); o2BarChart.invalidate(); }
        } else {
            LineDataSet heartDataSet = new LineDataSet(heartLineEntries, "Nhịp tim (bpm)");
            heartDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            heartDataSet.setCubicIntensity(0.2f);
            heartDataSet.setDrawFilled(true);
            heartDataSet.setFillColor(Color.parseColor("#EF4444"));
            heartDataSet.setFillAlpha(35);
            heartDataSet.setColor(Color.parseColor("#EF4444"));
            heartDataSet.setCircleColor(Color.parseColor("#EF4444"));
            heartDataSet.setLineWidth(3.5f);
            heartDataSet.setCircleRadius(5.5f);
            heartDataSet.setCircleHoleRadius(2.5f);
            heartDataSet.setDrawCircleHole(true);
            heartDataSet.setCircleHoleColor(Color.WHITE);
            heartDataSet.setHighlightEnabled(true);
            heartDataSet.setHighLightColor(Color.parseColor("#94A3B8"));
            heartDataSet.setDrawValues(false);

            if (lineChartHeart != null) {
                lineChartHeart.setData(new LineData(heartDataSet));
                lineChartHeart.notifyDataSetChanged();
                lineChartHeart.invalidate();
            }

            LineDataSet o2DataSet = new LineDataSet(o2LineEntries, "SpO2 (%)");
            o2DataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            o2DataSet.setCubicIntensity(0.2f);
            o2DataSet.setDrawFilled(true);
            o2DataSet.setFillColor(Color.parseColor("#2563EB"));
            o2DataSet.setFillAlpha(35);
            o2DataSet.setColor(Color.parseColor("#2563EB"));
            o2DataSet.setCircleColor(Color.parseColor("#2563EB"));
            o2DataSet.setLineWidth(3.5f);
            o2DataSet.setCircleRadius(5.5f);
            o2DataSet.setCircleHoleRadius(2.5f);
            o2DataSet.setDrawCircleHole(true);
            o2DataSet.setCircleHoleColor(Color.WHITE);
            o2DataSet.setHighlightEnabled(true);
            o2DataSet.setHighLightColor(Color.parseColor("#94A3B8"));
            o2DataSet.setDrawValues(false);

            if (lineChartO2 != null) {
                lineChartO2.setData(new LineData(o2DataSet));
                lineChartO2.notifyDataSetChanged();
                lineChartO2.invalidate();
            }

            BarDataSet heartBarDataSet = new BarDataSet(heartBarEntries, "Nhịp tim");
            heartBarDataSet.setColor(Color.parseColor("#F87171"));
            heartBarDataSet.setDrawValues(false);
            if (heartBarChart != null) {
                heartBarChart.setData(new BarData(heartBarDataSet));
                heartBarChart.notifyDataSetChanged();
                heartBarChart.invalidate();
            }

            BarDataSet o2BarDataSet = new BarDataSet(o2BarEntries, "SpO2");
            o2BarDataSet.setColor(Color.parseColor("#3B82F6"));
            o2BarDataSet.setDrawValues(false);
            if (o2BarChart != null) {
                o2BarChart.setData(new BarData(o2BarDataSet));
                o2BarChart.notifyDataSetChanged();
                o2BarChart.invalidate();
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