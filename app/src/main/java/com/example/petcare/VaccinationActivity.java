package com.example.petcare;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
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

public class VaccinationActivity extends BaseActivity {

    private Spinner spinnerPet;
    private TextView tvCategoryDisplay;
    private LinearLayout containerVaccines;
    private TextInputEditText etOtherVaccine, etVaccineDate, etNextDueDate;
    private MaterialButton btnSaveVaccination, btnClearVaccination;
    private ListView listVaccinations;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private String uid;

    // Pet data
    private final List<String> petIds = new ArrayList<>();
    private final List<String> petNames = new ArrayList<>();
    private final List<String> petCategories = new ArrayList<>();
    private String selectedPetId = "";
    private String selectedPetName = "";
    private String selectedPetCategory = "";

    // Vaccination display list
    private final List<String> displayList = new ArrayList<>();
    private final List<String> idList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vaccination);

        checkAuth();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Vaccinations");
        }

        db = FirebaseFirestore.getInstance();
        if (mAuth.getCurrentUser() != null) {
            uid = mAuth.getCurrentUser().getUid();
        } else return;

        spinnerPet = findViewById(R.id.spinnerPet);
        tvCategoryDisplay = findViewById(R.id.tvCategoryDisplay);
        containerVaccines = findViewById(R.id.containerVaccines);
        etOtherVaccine = findViewById(R.id.etOtherVaccine);
        etVaccineDate = findViewById(R.id.etVaccineDate);
        etNextDueDate = findViewById(R.id.etNextDueDate);
        btnSaveVaccination = findViewById(R.id.btnSaveVaccination);
        btnClearVaccination = findViewById(R.id.btnClearVaccination);
        listVaccinations = findViewById(R.id.listVaccinations);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
        listVaccinations.setAdapter(adapter);

        etVaccineDate.setOnClickListener(v -> showDatePicker(etVaccineDate));
        etNextDueDate.setOnClickListener(v -> showDatePicker(etNextDueDate));

        btnSaveVaccination.setOnClickListener(v -> saveVaccination());
        btnClearVaccination.setOnClickListener(v -> clearForm());

        listVaccinations.setOnItemClickListener((parent, view, position, id) -> showOptionsDialog(position));

        // Load user's pets into spinner
        loadPetsForSpinner();

        // Load existing vaccinations
        loadVaccinations();
    }

    // ================= PET SPINNER =================

    private void loadPetsForSpinner() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("pets").whereEqualTo("uid", uid).get()
                .addOnSuccessListener(q -> {
                    progressBar.setVisibility(View.GONE);
                    petIds.clear();
                    petNames.clear();
                    petCategories.clear();

                    List<String> displayNames = new ArrayList<>();

                    for (DocumentSnapshot doc : q.getDocuments()) {
                        String name = doc.getString("name");
                        String category = doc.getString("category");
                        petIds.add(doc.getId());
                        petNames.add(name != null ? name : "Unnamed");
                        petCategories.add(category != null ? category : "Other");
                        displayNames.add((name != null ? name : "Unnamed") +
                                " (" + (category != null ? category : "Other") + ")");
                    }

                    if (displayNames.isEmpty()) {
                        displayNames.add("No pets - add a pet first");
                    }

                    ArrayAdapter<String> petAdapter = new ArrayAdapter<>(
                            VaccinationActivity.this,
                            android.R.layout.simple_spinner_item,
                            displayNames);
                    petAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerPet.setAdapter(petAdapter);

                    spinnerPet.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (petIds.isEmpty()) return;
                            selectedPetId = petIds.get(position);
                            selectedPetName = petNames.get(position);
                            selectedPetCategory = petCategories.get(position);
                            tvCategoryDisplay.setText(selectedPetCategory);
                            buildVaccineCheckboxes(selectedPetCategory);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) { }
                    });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load pets: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ================= VACCINE CHECKBOXES =================

    private void buildVaccineCheckboxes(String category) {
        containerVaccines.removeAllViews();

        int arrayId = getVaccineArrayId(category);
        String[] vaccines = getResources().getStringArray(arrayId);

        for (String vaccine : vaccines) {
            CheckBox cb = new CheckBox(this);
            cb.setText(vaccine);
            cb.setTextSize(14f);
            cb.setPadding(8, 8, 8, 8);
            containerVaccines.addView(cb);
        }
    }

    private int getVaccineArrayId(String category) {
        switch (category) {
            case "Dog":    return R.array.vaccines_dog;
            case "Cat":    return R.array.vaccines_cat;
            case "Bird":   return R.array.vaccines_bird;
            case "Fish":   return R.array.vaccines_fish;
            case "Rabbit": return R.array.vaccines_rabbit;
            case "Hamster":return R.array.vaccines_hamster;
            case "Turtle": return R.array.vaccines_turtle;
            case "Cow":    return R.array.vaccines_cow;
            case "Goat":   return R.array.vaccines_goat;
            case "Horse":  return R.array.vaccines_horse;
            default:       return R.array.vaccines_dog;
        }
    }

    // ================= DATE PICKER =================

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

    // ================= SAVE / CLEAR =================

    private void clearForm() {
        etOtherVaccine.setText("");
        etVaccineDate.setText("");
        etNextDueDate.setText("");
        for (int i = 0; i < containerVaccines.getChildCount(); i++) {
            View v = containerVaccines.getChildAt(i);
            if (v instanceof CheckBox) {
                ((CheckBox) v).setChecked(false);
            }
        }
    }

    private void saveVaccination() {
        if (TextUtils.isEmpty(selectedPetId)) {
            Toast.makeText(this, "Select a pet first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Collect selected vaccines
        List<String> selectedVaccines = new ArrayList<>();
        for (int i = 0; i < containerVaccines.getChildCount(); i++) {
            View v = containerVaccines.getChildAt(i);
            if (v instanceof CheckBox) {
                CheckBox cb = (CheckBox) v;
                if (cb.isChecked()) {
                    selectedVaccines.add(cb.getText().toString());
                }
            }
        }

        // Add "Other" vaccine if typed
        String otherVaccine = etOtherVaccine.getText().toString().trim();
        if (!TextUtils.isEmpty(otherVaccine)) {
            selectedVaccines.add(otherVaccine);
        }

        if (selectedVaccines.isEmpty()) {
            Toast.makeText(this, "Select at least one vaccine", Toast.LENGTH_SHORT).show();
            return;
        }

        String date = etVaccineDate.getText().toString().trim();
        String nextDue = etNextDueDate.getText().toString().trim();

        if (TextUtils.isEmpty(date)) {
            etVaccineDate.setError("Required");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSaveVaccination.setEnabled(false);

        // Save each selected vaccine as a separate record
        final int[] successCount = {0};
        final int total = selectedVaccines.size();

        for (String vaccine : selectedVaccines) {
            Map<String, Object> data = new HashMap<>();
            data.put("petId", selectedPetId);
            data.put("petName", selectedPetName);
            data.put("category", selectedPetCategory);
            data.put("vaccineName", vaccine);
            data.put("date", date);
            data.put("nextDueDate", nextDue);
            data.put("uid", uid);
            data.put("createdAt", System.currentTimeMillis());

            db.collection("vaccinations").add(data)
                    .addOnSuccessListener(ref -> {
                        successCount[0]++;
                        if (successCount[0] == total) {
                            progressBar.setVisibility(View.GONE);
                            btnSaveVaccination.setEnabled(true);
                            Toast.makeText(this, total + " vaccination(s) saved", Toast.LENGTH_SHORT).show();
                            clearForm();
                            loadVaccinations();
                        }
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        btnSaveVaccination.setEnabled(true);
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    // ================= LOAD VACCINATIONS =================

    private void loadVaccinations() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("vaccinations")
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener(q -> {
                    progressBar.setVisibility(View.GONE);
                    displayList.clear();
                    idList.clear();

                    for (DocumentSnapshot doc : q.getDocuments()) {
                        String petName = doc.getString("petName");
                        String category = doc.getString("category");
                        String vaccine = doc.getString("vaccineName");
                        String date = doc.getString("date");
                        String nextDue = doc.getString("nextDueDate");

                        String display = "🐾 " + (petName != null ? petName : "") +
                                " (" + (category != null ? category : "") + ")" +
                                "\n💉 " + (vaccine != null ? vaccine : "") +
                                "\n📅 Given: " + (date != null ? date : "") +
                                "\n🔔 Next: " + (nextDue != null && !nextDue.isEmpty() ? nextDue : "-");

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
                .setTitle("Vaccination")
                .setItems(new CharSequence[]{"Delete"}, (dialog, which) -> {
                    if (which == 0) deleteVaccination(id);
                })
                .show();
    }

    private void deleteVaccination(String id) {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("vaccinations").document(id).delete()
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    loadVaccinations();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                });
    }
}