package com.example.petcare;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PetActivity extends BaseActivity {

    private TextInputEditText etPetName, etPetAge, etCategoryOther, etBreedOther;
    private TextInputLayout tilCategoryOther, tilBreedOther;
    private Spinner spinnerCategory, spinnerBreed, spinnerGender;
    private MaterialButton btnSavePet, btnClearPet;
    private ListView listPets;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private String uid;

    private final List<String> petNames = new ArrayList<>();
    private final List<String> petIds = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    // Category → Breed mapping
    private String currentCategory = "Dog";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pet);

        checkAuth();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Pets");
        }

        db = FirebaseFirestore.getInstance();
        if (mAuth.getCurrentUser() != null) {
            uid = mAuth.getCurrentUser().getUid();
        } else return;

        // Find views
        etPetName = findViewById(R.id.etPetName);
        etPetAge = findViewById(R.id.etPetAge);
        etCategoryOther = findViewById(R.id.etCategoryOther);
        etBreedOther = findViewById(R.id.etBreedOther);
        tilCategoryOther = findViewById(R.id.tilCategoryOther);
        tilBreedOther = findViewById(R.id.tilBreedOther);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerBreed = findViewById(R.id.spinnerBreed);
        spinnerGender = findViewById(R.id.spinnerGender);
        btnSavePet = findViewById(R.id.btnSavePet);
        btnClearPet = findViewById(R.id.btnClearPet);
        listPets = findViewById(R.id.listPets);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, petNames);
        listPets.setAdapter(adapter);

        setupCategorySpinner();
        setupGenderSpinner();

        btnSavePet.setOnClickListener(v -> savePet());
        btnClearPet.setOnClickListener(v -> clearForm());

        listPets.setOnItemClickListener((parent, view, position, id) -> showOptionsDialog(position));

        loadPets();
    }

    // ================= SPINNER SETUP =================

    private void setupCategorySpinner() {
        ArrayAdapter<CharSequence> catAdapter = ArrayAdapter.createFromResource(
                this, R.array.pet_categories, android.R.layout.simple_spinner_item);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(catAdapter);

        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                currentCategory = selected;

                // Show/hide "Other" text field for category
                if (selected.equals("Other")) {
                    tilCategoryOther.setVisibility(View.VISIBLE);
                } else {
                    tilCategoryOther.setVisibility(View.GONE);
                    etCategoryOther.setText("");
                }

                // Update breed spinner based on category
                updateBreedSpinner(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void updateBreedSpinner(String category) {
        int breedArrayId = getBreedArrayId(category);

        ArrayAdapter<CharSequence> breedAdapter = ArrayAdapter.createFromResource(
                this, breedArrayId, android.R.layout.simple_spinner_item);
        breedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBreed.setAdapter(breedAdapter);

        spinnerBreed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                if (selected.equals("Other")) {
                    tilBreedOther.setVisibility(View.VISIBLE);
                } else {
                    tilBreedOther.setVisibility(View.GONE);
                    etBreedOther.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private int getBreedArrayId(String category) {
        switch (category) {
            case "Dog":    return R.array.breeds_dog;
            case "Cat":    return R.array.breeds_cat;
            case "Bird":   return R.array.breeds_bird;
            case "Fish":   return R.array.breeds_fish;
            case "Rabbit": return R.array.breeds_rabbit;
            case "Hamster":return R.array.breeds_hamster;
            case "Turtle": return R.array.breeds_turtle;
            case "Cow":    return R.array.breeds_cow;
            case "Goat":   return R.array.breeds_goat;
            case "Horse":  return R.array.breeds_horse;
            default:       return R.array.breeds_dog; // fallback
        }
    }

    private void setupGenderSpinner() {
        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(
                this, R.array.pet_genders, android.R.layout.simple_spinner_item);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);
    }

    // ================= SAVE / CLEAR =================

    private void clearForm() {
        etPetName.setText("");
        etPetAge.setText("");
        etCategoryOther.setText("");
        etBreedOther.setText("");
        spinnerCategory.setSelection(0);
        spinnerGender.setSelection(0);
        tilCategoryOther.setVisibility(View.GONE);
        tilBreedOther.setVisibility(View.GONE);
    }

    private void savePet() {
        String name = etPetName.getText().toString().trim();
        String age = etPetAge.getText().toString().trim();

        // Category
        String category = spinnerCategory.getSelectedItem().toString();
        if (category.equals("Other")) {
            category = etCategoryOther.getText().toString().trim();
            if (TextUtils.isEmpty(category)) {
                etCategoryOther.setError("Enter category");
                return;
            }
        }

        // Breed
        String breed = spinnerBreed.getSelectedItem() != null
                ? spinnerBreed.getSelectedItem().toString() : "";
        if (breed.equals("Other")) {
            breed = etBreedOther.getText().toString().trim();
            if (TextUtils.isEmpty(breed)) {
                etBreedOther.setError("Enter breed");
                return;
            }
        }

        // Gender
        String gender = spinnerGender.getSelectedItem().toString();

        // Validate
        if (TextUtils.isEmpty(name)) { etPetName.setError("Required"); return; }
        if (TextUtils.isEmpty(age)) { etPetAge.setError("Required"); return; }

        progressBar.setVisibility(View.VISIBLE);
        btnSavePet.setEnabled(false);

        Map<String, Object> pet = new HashMap<>();
        pet.put("name", name);
        pet.put("category", category);
        pet.put("breed", breed);
        pet.put("age", age);
        pet.put("gender", gender);
        pet.put("uid", uid);
        pet.put("createdAt", System.currentTimeMillis());

        db.collection("pets").add(pet)
                .addOnSuccessListener(ref -> {
                    progressBar.setVisibility(View.GONE);
                    btnSavePet.setEnabled(true);
                    Toast.makeText(this, "Pet added", Toast.LENGTH_SHORT).show();
                    clearForm();
                    loadPets();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSavePet.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ================= LOAD / DELETE =================

    private void loadPets() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("pets")
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);
                    petNames.clear();
                    petIds.clear();

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String name = doc.getString("name");
                        String category = doc.getString("category");
                        String breed = doc.getString("breed");
                        String age = doc.getString("age");
                        String gender = doc.getString("gender");

                        String display = "🐾 " + (name != null ? name : "Unnamed") +
                                "\n📂 " + (category != null ? category : "") +
                                " • " + (breed != null ? breed : "") +
                                "\n🎂 " + (age != null ? age : "") + " yrs" +
                                " • " + (gender != null ? gender : "");

                        petNames.add(display);
                        petIds.add(doc.getId());
                    }

                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(petNames.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showOptionsDialog(int position) {
        String petId = petIds.get(position);
        String petLabel = petNames.get(position);

        new AlertDialog.Builder(this)
                .setTitle(petLabel)
                .setItems(new CharSequence[]{"Delete"}, (dialog, which) -> {
                    if (which == 0) deletePet(petId);
                })
                .show();
    }

    private void deletePet(String petId) {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("pets").document(petId).delete()
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    loadPets();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                });
    }
}