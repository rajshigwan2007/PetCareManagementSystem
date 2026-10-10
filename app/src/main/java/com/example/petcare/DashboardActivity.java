package com.example.petcare;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.google.firebase.firestore.FirebaseFirestore;

public class DashboardActivity extends BaseActivity {

    private CardView cardPets, cardVaccinations, cardAppointments, cardMedicines, cardReminders;
    private TextView tvWelcome, tvUserInfo;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        checkAuth();

        db = FirebaseFirestore.getInstance();

        cardPets = findViewById(R.id.cardPets);
        cardVaccinations = findViewById(R.id.cardVaccinations);
        cardAppointments = findViewById(R.id.cardAppointments);
        cardMedicines = findViewById(R.id.cardMedicines);
        cardReminders = findViewById(R.id.cardReminders);
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserInfo = findViewById(R.id.tvUserInfo);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Dashboard");
        }

        loadUserInfo();

        cardPets.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, PetActivity.class)));

        cardVaccinations.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, VaccinationActivity.class)));

        cardAppointments.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, AppointmentActivity.class)));

        cardMedicines.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, MedicineActivity.class)));

        cardReminders.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, ReminderActivity.class)));
    }

    private void loadUserInfo() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String email = doc.getString("email");
                        String phone = doc.getString("phone");
                        if (name != null) tvWelcome.setText("Welcome, " + name);
                        tvUserInfo.setText((email != null ? email : "") + (phone != null ? "\n" + phone : ""));
                    }
                });
    }
}