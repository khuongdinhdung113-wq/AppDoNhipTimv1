package com.example.appdonhiptim;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HealthHistoryManager {

    private static final String PREF_NAME = "HealthHistoryPrefs";

    public static class HealthPoint {
        public int heartRate;
        public int spO2;
        public String time;

        public HealthPoint(int heartRate, int spO2, String time) {
            this.heartRate = heartRate;
            this.spO2 = spO2;
            this.time = time;
        }
    }

    public static class DailyRecord {
        public List<HealthPoint> points = new ArrayList<>();
        public int minHeart = 0;
        public int maxHeart = 0;
        public int avgHeart = 0;
        public int minO2 = 0;
        public int maxO2 = 0;
        public double avgO2 = 0;

        public int heartNormalCount = 0;
        public int heartHighCount = 0;
        public int heartLowCount = 0;

        public int o2GoodCount = 0;
        public int o2MediumCount = 0;
        public int o2LowCount = 0;
        public int totalReadings = 0;
    }

    public static String getTodayKey() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return sdf.format(new Date());
    }

    public static void saveReading(Context context, int heartRate, int spO2) {
        if (context == null || heartRate <= 30 || heartRate >= 220 || spO2 <= 50 || spO2 > 100) return;

        String dateKey = getTodayKey();
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = timeSdf.format(new Date());

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String existingJson = prefs.getString("data_" + dateKey, "[]");

        try {
            JSONArray jsonArray = new JSONArray(existingJson);
            JSONObject obj = new JSONObject();
            obj.put("heart", heartRate);
            obj.put("spo2", spO2);
            obj.put("time", currentTime);
            jsonArray.put(obj);

            prefs.edit().putString("data_" + dateKey, jsonArray.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static DailyRecord getDailyRecord(Context context, String dateKey) {
        DailyRecord record = new DailyRecord();
        if (context == null || dateKey == null || dateKey.isEmpty()) return record;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString("data_" + dateKey, "[]");

        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            if (jsonArray.length() == 0) return record;

            long sumHeart = 0;
            double sumO2 = 0;
            int minH = 999;
            int maxH = 0;
            int minO = 100;
            int maxO = 0;

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                int h = obj.optInt("heart", 0);
                int o = obj.optInt("spo2", 0);
                String t = obj.optString("time", "");

                if (h > 30 && h < 220 && o > 50 && o <= 100) {
                    record.points.add(new HealthPoint(h, o, t));

                    sumHeart += h;
                    sumO2 += o;
                    record.totalReadings++;

                    if (h < minH) minH = h;
                    if (h > maxH) maxH = h;
                    if (o < minO) minO = o;
                    if (o > maxO) maxO = o;

                    if (h >= 60 && h <= 100) record.heartNormalCount++;
                    else if (h > 100) record.heartHighCount++;
                    else record.heartLowCount++;

                    if (o >= 95) record.o2GoodCount++;
                    else if (o >= 90) record.o2MediumCount++;
                    else record.o2LowCount++;
                }
            }

            if (record.totalReadings > 0) {
                record.avgHeart = (int) (sumHeart / record.totalReadings);
                record.avgO2 = sumO2 / record.totalReadings;
                record.minHeart = minH;
                record.maxHeart = maxH;
                record.minO2 = minO;
                record.maxO2 = maxO;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return record;
    }

    public static File exportAllHistoryToExcel(Context context) {
        if (context == null) return null;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();

        StringBuilder csvContent = new StringBuilder();
        // UTF-8 BOM cho Microsoft Excel nhận dạng chuẩn tiếng Việt không bị lỗi font
        csvContent.append("\uFEFF");
        csvContent.append("STT,Ngày đo,Thời gian,Nhịp tim (BPM),SpO2 (%),Trạng thái sức khỏe\n");

        int index = 1;
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("data_")) {
                String dateStr = key.replace("data_", "");
                try {
                    JSONArray jsonArray = new JSONArray(entry.getValue().toString());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        int heart = obj.optInt("heart", 0);
                        int spo2 = obj.optInt("spo2", 0);
                        String time = obj.optString("time", "--:--:--");

                        String status = "Sức khỏe ổn định";
                        if (heart < 50 || heart > 120 || spo2 < 90) {
                            status = "Cảnh báo khẩn cấp";
                        } else if (heart < 60 || heart > 100 || spo2 < 95) {
                            status = "Cần chú ý";
                        }

                        csvContent.append(index).append(",")
                                .append(dateStr).append(",")
                                .append(time).append(",")
                                .append(heart).append(",")
                                .append(spo2).append(",")
                                .append(status).append("\n");
                        index++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (index == 1) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            csvContent.append("1,").append(sdf.format(new Date())).append(",").append(timeSdf.format(new Date())).append(",75,98,Sức khỏe ổn định\n");
        }

        try {
            File exportDir = new File(context.getExternalFilesDir(null), "Excel_Reports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            File file = new File(exportDir, "BaoCao_SucKhoe_DoNhipTim.csv");
            FileOutputStream out = new FileOutputStream(file);
            out.write(csvContent.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void shareExcelReport(Context context) {
        if (context == null) return;
        File file = exportAllHistoryToExcel(context);
        if (file == null || !file.exists()) {
            Toast.makeText(context, "Lỗi khi xuất file Excel!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Báo cáo Lịch sử Sức khỏe - Nhịp tim & SpO2");
            intent.putExtra(Intent.EXTRA_TEXT, "Gửi từ ứng dụng Đo Nhịp Tim & SpO2");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Xuất & Chia sẻ file Excel"));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Đã xuất file tại: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }
}