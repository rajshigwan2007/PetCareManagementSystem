package com.example.petcare;

import android.app.DatePickerDialog;
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

public class MedicineActivity extends BaseActivity {

 private TextInputEditText etPetName, etMedicineName, etDosage, etFrequency, etStartDate, etEndDate;
 private MaterialButton btnSaveMedicine, btnClearMedicine;
 private ListView listMedicines;
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
  setContentView(R.layout.activity_medicine);

  checkAuth();

  if (getSupportActionBar() != null) {
   getSupportActionBar().setTitle("Medicines");
  }

  db = FirebaseFirestore.getInstance();
  if (mAuth.getCurrentUser() != null) {
   uid = mAuth.getCurrentUser().getUid();
  } else return;

  etPetName = findViewById(R.id.etPetName);
  etMedicineName = findViewById(R.id.etMedicineName);
  etDosage = findViewById(R.id.etDosage);
  etFrequency = findViewById(R.id.etFrequency);
  etStartDate = findViewById(R.id.etStartDate);
  etEndDate = findViewById(R.id.etEndDate);
  btnSaveMedicine = findViewById(R.id.btnSaveMedicine);
  btnClearMedicine = findViewById(R.id.btnClearMedicine);
  listMedicines = findViewById(R.id.listMedicines);
  tvEmpty = findViewById(R.id.tvEmpty);
  progressBar = findViewById(R.id.progressBar);

  adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
  listMedicines.setAdapter(adapter);

  etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
  etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));

  btnSaveMedicine.setOnClickListener(v -> saveMedicine());
  btnClearMedicine.setOnClickListener(v -> clearForm());

  listMedicines.setOnItemClickListener((parent, view, position, id) -> showOptionsDialog(position));

  loadMedicines();
 }

 private void showDatePicker(TextInputEditText target) {
  Calendar c = Calendar.getInstance();
  new DatePickerDialog(this,
          (view, year, month, dayOfMonth) -> {
           String date = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year);
           target.setText(date);
          },
          c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
          .show();
 }

 private void clearForm() {
  etPetName.setText("");
  etMedicineName.setText("");
  etDosage.setText("");
  etFrequency.setText("");
  etStartDate.setText("");
  etEndDate.setText("");
 }

 private void saveMedicine() {
  String petName = etPetName.getText().toString().trim();
  String medicine = etMedicineName.getText().toString().trim();
  String dosage = etDosage.getText().toString().trim();
  String frequency = etFrequency.getText().toString().trim();
  String startDate = etStartDate.getText().toString().trim();
  String endDate = etEndDate.getText().toString().trim();

  if (TextUtils.isEmpty(petName)) { etPetName.setError("Required"); return; }
  if (TextUtils.isEmpty(medicine)) { etMedicineName.setError("Required"); return; }
  if (TextUtils.isEmpty(dosage)) { etDosage.setError("Required"); return; }

  progressBar.setVisibility(View.VISIBLE);
  btnSaveMedicine.setEnabled(false);

  Map<String, Object> data = new HashMap<>();
  data.put("petName", petName);
  data.put("medicineName", medicine);
  data.put("dosage", dosage);
  data.put("frequency", frequency);
  data.put("startDate", startDate);
  data.put("endDate", endDate);
  data.put("uid", uid);
  data.put("createdAt", System.currentTimeMillis());

  db.collection("medicines").add(data)
          .addOnSuccessListener(ref -> {
           progressBar.setVisibility(View.GONE);
           btnSaveMedicine.setEnabled(true);
           Toast.makeText(this, "Medicine added", Toast.LENGTH_SHORT).show();
           clearForm();
           loadMedicines();
          })
          .addOnFailureListener(e -> {
           progressBar.setVisibility(View.GONE);
           btnSaveMedicine.setEnabled(true);
           Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
          });
 }

 private void loadMedicines() {
  progressBar.setVisibility(View.VISIBLE);
  db.collection("medicines")
          .whereEqualTo("uid", uid)
          .get()
          .addOnSuccessListener(q -> {
           progressBar.setVisibility(View.GONE);
           displayList.clear();
           idList.clear();

           for (DocumentSnapshot doc : q.getDocuments()) {
            String petName = doc.getString("petName");
            String medicine = doc.getString("medicineName");
            String dosage = doc.getString("dosage");
            String frequency = doc.getString("frequency");
            String startDate = doc.getString("startDate");
            String endDate = doc.getString("endDate");

            String display = "💊 " + (medicine != null ? medicine : "") +
                    "\n🐾 " + (petName != null ? petName : "") +
                    "\nDosage: " + (dosage != null ? dosage : "") +
                    (frequency != null && !frequency.isEmpty() ? " • " + frequency : "") +
                    "\nFrom: " + (startDate != null ? startDate : "-") +
                    " To: " + (endDate != null && !endDate.isEmpty() ? endDate : "-");

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
          .setTitle("Medicine")
          .setItems(new CharSequence[]{"Delete"}, (dialog, which) -> {
           if (which == 0) deleteMedicine(id);
          })
          .show();
 }

 private void deleteMedicine(String id) {
  progressBar.setVisibility(View.VISIBLE);
  db.collection("medicines").document(id).delete()
          .addOnSuccessListener(aVoid -> {
           progressBar.setVisibility(View.GONE);
           Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
           loadMedicines();
          })
          .addOnFailureListener(e -> {
           progressBar.setVisibility(View.GONE);
           Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
          });
 }
}