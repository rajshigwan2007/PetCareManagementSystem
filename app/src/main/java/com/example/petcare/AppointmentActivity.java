package com.example.petcare;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
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

public class AppointmentActivity extends BaseActivity {

 private TextInputEditText etPetName, etVetName, etAppointmentDate, etAppointmentTime, etClinicAddress;
 private MaterialButton btnSaveAppointment, btnClearAppointment;
 private ListView listAppointments;
 private TextView tvEmpty;
 private ProgressBar progressBar;

 private FirebaseFirestore db;
 private String uid;

 private final List<String> displayList = new ArrayList<>();
 private final List<String> idList = new ArrayList<>();
 private ArrayAdapter<String> adapter;

 @Override
 protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  setContentView(R.layout.activity_appointment);

  checkAuth();

  if (getSupportActionBar() != null) {
   getSupportActionBar().setTitle("Appointments");
  }

  db = FirebaseFirestore.getInstance();
  if (mAuth.getCurrentUser() != null) {
   uid = mAuth.getCurrentUser().getUid();
  } else return;

  etPetName = findViewById(R.id.etPetName);
  etVetName = findViewById(R.id.etVetName);
  etAppointmentDate = findViewById(R.id.etAppointmentDate);
  etAppointmentTime = findViewById(R.id.etAppointmentTime);
  etClinicAddress = findViewById(R.id.etClinicAddress);
  btnSaveAppointment = findViewById(R.id.btnSaveAppointment);
  btnClearAppointment = findViewById(R.id.btnClearAppointment);
  listAppointments = findViewById(R.id.listAppointments);
  tvEmpty = findViewById(R.id.tvEmpty);
  progressBar = findViewById(R.id.progressBar);

  adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
  listAppointments.setAdapter(adapter);

  etAppointmentDate.setOnClickListener(v -> showDatePicker());
  etAppointmentTime.setOnClickListener(v -> showTimePicker());

  btnSaveAppointment.setOnClickListener(v -> saveAppointment());
  btnClearAppointment.setOnClickListener(v -> clearForm());

  listAppointments.setOnItemClickListener((parent, view, position, id) -> showOptionsDialog(position));

  loadAppointments();
 }

 private void showDatePicker() {
  Calendar c = Calendar.getInstance();
  new DatePickerDialog(this,
          (view, year, month, dayOfMonth) -> {
           String date = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year);
           etAppointmentDate.setText(date);
          },
          c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
          .show();
 }

 private void showTimePicker() {
  Calendar c = Calendar.getInstance();
  new TimePickerDialog(this,
          (view, hourOfDay, minute) -> {
           String time = String.format("%02d:%02d", hourOfDay, minute);
           etAppointmentTime.setText(time);
          },
          c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
          .show();
 }

 private void clearForm() {
  etPetName.setText("");
  etVetName.setText("");
  etAppointmentDate.setText("");
  etAppointmentTime.setText("");
  etClinicAddress.setText("");
 }

 private void saveAppointment() {
  String petName = etPetName.getText().toString().trim();
  String vetName = etVetName.getText().toString().trim();
  String date = etAppointmentDate.getText().toString().trim();
  String time = etAppointmentTime.getText().toString().trim();
  String address = etClinicAddress.getText().toString().trim();

  if (TextUtils.isEmpty(petName)) { etPetName.setError("Required"); return; }
  if (TextUtils.isEmpty(vetName)) { etVetName.setError("Required"); return; }
  if (TextUtils.isEmpty(date)) { etAppointmentDate.setError("Required"); return; }
  if (TextUtils.isEmpty(time)) { etAppointmentTime.setError("Required"); return; }

  progressBar.setVisibility(View.VISIBLE);
  btnSaveAppointment.setEnabled(false);

  Map<String, Object> data = new HashMap<>();
  data.put("petName", petName);
  data.put("vetName", vetName);
  data.put("date", date);
  data.put("time", time);
  data.put("address", address);
  data.put("uid", uid);
  data.put("createdAt", System.currentTimeMillis());

  db.collection("appointments").add(data)
          .addOnSuccessListener(ref -> {
           progressBar.setVisibility(View.GONE);
           btnSaveAppointment.setEnabled(true);
           Toast.makeText(this, "Appointment added", Toast.LENGTH_SHORT).show();
           clearForm();
           loadAppointments();
          })
          .addOnFailureListener(e -> {
           progressBar.setVisibility(View.GONE);
           btnSaveAppointment.setEnabled(true);
           Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
          });
 }

 private void loadAppointments() {
  progressBar.setVisibility(View.VISIBLE);
  db.collection("appointments")
          .whereEqualTo("uid", uid)
          .get()
          .addOnSuccessListener(q -> {
           progressBar.setVisibility(View.GONE);
           displayList.clear();
           idList.clear();

           for (DocumentSnapshot doc : q.getDocuments()) {
            String petName = doc.getString("petName");
            String vetName = doc.getString("vetName");
            String date = doc.getString("date");
            String time = doc.getString("time");
            String address = doc.getString("address");

            String display = "🐾 " + (petName != null ? petName : "") +
                    "\n👨‍⚕️ Dr. " + (vetName != null ? vetName : "") +
                    "\n📅 " + (date != null ? date : "") + " at " + (time != null ? time : "") +
                    (address != null && !address.isEmpty() ? "\n📍 " + address : "");

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
          .setTitle("Appointment")
          .setItems(new CharSequence[]{"Delete"}, (dialog, which) -> {
           if (which == 0) deleteAppointment(id);
          })
          .show();
 }

 private void deleteAppointment(String id) {
  progressBar.setVisibility(View.VISIBLE);
  db.collection("appointments").document(id).delete()
          .addOnSuccessListener(aVoid -> {
           progressBar.setVisibility(View.GONE);
           Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
           loadAppointments();
          })
          .addOnFailureListener(e -> {
           progressBar.setVisibility(View.GONE);
           Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
          });
 }
}