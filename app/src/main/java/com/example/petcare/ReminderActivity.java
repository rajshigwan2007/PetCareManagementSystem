package com.example.petcare;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReminderActivity extends BaseActivity {

 private TextInputEditText etTitle, etDescription, etDate, etTime;
 private MaterialButton btnSaveReminder, btnClearReminder;
 private ListView listReminders;
 private TextView tvEmpty;
 private ProgressBar progressBar;

 private FirebaseFirestore db;
 private String uid;

 private final List<String> displayList = new ArrayList<>();
 private final List<String> idList = new ArrayList<>();
 private ArrayAdapter<String> adapter;

 // temp selectors
 private int pickedYear, pickedMonth, pickedDay, pickedHour, pickedMinute;

 @Override
 protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  setContentView(R.layout.activity_reminder);

  checkAuth();

  if (getSupportActionBar() != null) {
   getSupportActionBar().setTitle("Reminders");
  }

  db = FirebaseFirestore.getInstance();
  if (mAuth.getCurrentUser() != null) {
   uid = mAuth.getCurrentUser().getUid();
  } else return;

  etTitle = findViewById(R.id.etTitle);
  etDescription = findViewById(R.id.etDescription);
  etDate = findViewById(R.id.etDate);
  etTime = findViewById(R.id.etTime);
  btnSaveReminder = findViewById(R.id.btnSaveReminder);
  btnClearReminder = findViewById(R.id.btnClearReminder);
  listReminders = findViewById(R.id.listReminders);
  tvEmpty = findViewById(R.id.tvEmpty);
  progressBar = findViewById(R.id.progressBar);

  adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
  listReminders.setAdapter(adapter);

  etDate.setOnClickListener(v -> showDatePicker());
  etTime.setOnClickListener(v -> showTimePicker());

  btnSaveReminder.setOnClickListener(v -> saveReminder());
  btnClearReminder.setOnClickListener(v -> clearForm());

  listReminders.setOnItemClickListener((parent, view, position, id) -> showOptionsDialog(position));

  requestNotificationPermissionIfNeeded();

  loadReminders();
 }

 private void requestNotificationPermissionIfNeeded() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
   if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
           != android.content.pm.PackageManager.PERMISSION_GRANTED) {
    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
   }
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
   AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
   if (am != null && !am.canScheduleExactAlarms()) {
    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
    startActivity(intent);
   }
  }
 }

 private void showDatePicker() {
  Calendar c = Calendar.getInstance();
  new DatePickerDialog(this,
          (view, year, month, dayOfMonth) -> {
           pickedYear = year;
           pickedMonth = month;
           pickedDay = dayOfMonth;
           etDate.setText(String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year));
          },
          c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
          .show();
 }

 private void showTimePicker() {
  Calendar c = Calendar.getInstance();
  new TimePickerDialog(this,
          (view, hourOfDay, minute) -> {
           pickedHour = hourOfDay;
           pickedMinute = minute;
           etTime.setText(String.format("%02d:%02d", hourOfDay, minute));
          },
          c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
          .show();
 }

 private void clearForm() {
  etTitle.setText("");
  etDescription.setText("");
  etDate.setText("");
  etTime.setText("");
 }

 private void saveReminder() {
  String title = etTitle.getText().toString().trim();
  String description = etDescription.getText().toString().trim();
  String date = etDate.getText().toString().trim();
  String time = etTime.getText().toString().trim();

  if (TextUtils.isEmpty(title)) { etTitle.setError("Required"); return; }
  if (TextUtils.isEmpty(date)) { etDate.setError("Required"); return; }
  if (TextUtils.isEmpty(time)) { etTime.setError("Required"); return; }

  progressBar.setVisibility(View.VISIBLE);
  btnSaveReminder.setEnabled(false);

  Map<String, Object> data = new HashMap<>();
  data.put("title", title);
  data.put("description", description);
  data.put("date", date);
  data.put("time", time);
  data.put("uid", uid);
  data.put("createdAt", System.currentTimeMillis());

  db.collection("reminders").add(data)
          .addOnSuccessListener(ref -> {
           progressBar.setVisibility(View.GONE);
           btnSaveReminder.setEnabled(true);
           Toast.makeText(this, "Reminder saved", Toast.LENGTH_SHORT).show();

           scheduleAlarm(title, description);
           clearForm();
           loadReminders();
          })
          .addOnFailureListener(e -> {
           progressBar.setVisibility(View.GONE);
           btnSaveReminder.setEnabled(true);
           Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
          });
 }

 private void scheduleAlarm(String title, String description) {
  Calendar target = Calendar.getInstance();
  target.set(Calendar.YEAR, pickedYear);
  target.set(Calendar.MONTH, pickedMonth);
  target.set(Calendar.DAY_OF_MONTH, pickedDay);
  target.set(Calendar.HOUR_OF_DAY, pickedHour);
  target.set(Calendar.MINUTE, pickedMinute);
  target.set(Calendar.SECOND, 0);

  if (target.getTimeInMillis() < System.currentTimeMillis()) {
   Toast.makeText(this, "Selected time is in the past", Toast.LENGTH_SHORT).show();
   return;
  }

  Intent intent = new Intent(this, ReminderReceiver.class);
  intent.putExtra("title", title);
  intent.putExtra("description", description);

  int requestCode = (int) System.currentTimeMillis();
  PendingIntent pendingIntent = PendingIntent.getBroadcast(
          this,
          requestCode,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
  );

  AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
  if (am != null) {
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (am.canScheduleExactAlarms()) {
     am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pendingIntent);
    } else {
     am.set(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pendingIntent);
    }
   } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pendingIntent);
   } else {
    am.setExact(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pendingIntent);
   }
  }
 }

 private void loadReminders() {
  progressBar.setVisibility(View.VISIBLE);
  db.collection("reminders")
          .whereEqualTo("uid", uid)
          .get()
          .addOnSuccessListener(q -> {
           progressBar.setVisibility(View.GONE);
           displayList.clear();
           idList.clear();

           for (DocumentSnapshot doc : q.getDocuments()) {
            String title = doc.getString("title");
            String description = doc.getString("description");
            String date = doc.getString("date");
            String time = doc.getString("time");

            String display = "🔔 " + (title != null ? title : "") +
                    (description != null && !description.isEmpty() ? "\n" + description : "") +
                    "\n📅 " + (date != null ? date : "") + " at " + (time != null ? time : "");

            displayList.add(display);
            idList.add(doc.getId());
           }

           adapter.notifyDataSetChanged();
           tvEmpty.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
          })
          .addOnFailureListener(e -> {
           progressBar.setVisibility(View.GONE);
           Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
          });
 }

 private void showOptionsDialog(int position) {
  String id = idList.get(position);
  new AlertDialog.Builder(this)
          .setTitle("Reminder")
          .setItems(new CharSequence[]{"Delete"}, (dialog, which) -> {
           if (which == 0) deleteReminder(id);
          })
          .show();
 }

 private void deleteReminder(String id) {
  progressBar.setVisibility(View.VISIBLE);
  db.collection("reminders").document(id).delete()
          .addOnSuccessListener(aVoid -> {
           progressBar.setVisibility(View.GONE);
           Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
           loadReminders();
          })
          .addOnFailureListener(e -> {
           progressBar.setVisibility(View.GONE);
           Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
          });
 }
}