package com.example.accidentdetection.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accidentdetection.R;
import com.example.accidentdetection.adapters.ContactAdapter;
import com.example.accidentdetection.database.AppDatabase;
import com.example.accidentdetection.database.Contact;
import com.example.accidentdetection.database.ContactDao;
import com.example.accidentdetection.utils.BottomNavHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class ContactsActivity extends AppCompatActivity {

    private static final int MAX_CONTACTS = 5;

    private AppDatabase db;
    private List<Contact> current = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_contacts);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });

        db = AppDatabase.get(this);

        RecyclerView rv = findViewById(R.id.rvContacts);
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        FloatingActionButton fab = findViewById(R.id.fabAdd);
        BottomNavigationView nav = findViewById(R.id.bottomNav);

        ContactAdapter adapter = new ContactAdapter(new ContactAdapter.Listener() {
            @Override public void onEdit(Contact c) { showContactDialog(c); }
            @Override public void onDelete(Contact c) { confirmDelete(c); }
        });
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        db.contactDao().getAll().observe(this, list -> {
            current = list;
            adapter.setItems(list);
            tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });

        fab.setOnClickListener(v -> {
            if (current.size() >= MAX_CONTACTS) {
                Toast.makeText(this, "Maximum " + MAX_CONTACTS + " contacts allowed",
                        Toast.LENGTH_SHORT).show();
            } else {
                showContactDialog(null);
            }
        });

        BottomNavHelper.setup(this, nav, R.id.nav_contacts);
    }

    private void showContactDialog(Contact existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_contact, null);
        TextInputLayout tilName = view.findViewById(R.id.tilName);
        TextInputLayout tilPhone = view.findViewById(R.id.tilPhone);
        TextInputEditText etName = view.findViewById(R.id.etName);
        TextInputEditText etPhone = view.findViewById(R.id.etPhone);
        TextInputEditText etRelation = view.findViewById(R.id.etRelation);
        CheckBox cbPrimary = view.findViewById(R.id.cbPrimary);

        if (existing != null) {
            etName.setText(existing.name);
            etPhone.setText(existing.phone);
            etRelation.setText(existing.relation);
            cbPrimary.setChecked(existing.isPrimary);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add contact" : "Edit contact")
                .setView(view)
                .setPositiveButton("Save", null)   // set below so errors don't close it
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilName.setError(null);
            tilPhone.setError(null);

            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String relation = etRelation.getText().toString().trim();

            if (name.length() < 2) {
                tilName.setError("Enter a name");
                return;
            }
            if (!phone.matches("\\d{10}")) {
                tilPhone.setError("Enter a 10-digit number");
                return;
            }
            for (Contact c : current) {
                boolean same = existing != null && c.id == existing.id;
                if (!same && c.phone.equals(phone)) {
                    tilPhone.setError("This number is already saved");
                    return;
                }
            }

            // Build a fresh object so the list on screen isn't mutated before saving
            Contact c = new Contact();
            c.id = existing != null ? existing.id : 0;
            c.name = name;
            c.phone = phone;
            c.relation = relation;
            boolean makePrimary = cbPrimary.isChecked() || current.isEmpty();

            AppDatabase.executor.execute(() -> {
                ContactDao dao = db.contactDao();
                if (makePrimary) dao.clearPrimary();
                c.isPrimary = makePrimary;
                if (c.id == 0) dao.insert(c); else dao.update(c);
                ensurePrimary(dao);
            });
            dialog.dismiss();
        });
    }

    private void confirmDelete(Contact contact) {
        new AlertDialog.Builder(this)
                .setTitle("Delete contact?")
                .setMessage(contact.name + " will no longer receive emergency alerts.")
                .setPositiveButton("Delete", (d, w) ->
                        AppDatabase.executor.execute(() -> {
                            db.contactDao().delete(contact);
                            ensurePrimary(db.contactDao());
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void ensurePrimary(ContactDao dao) {
        List<Contact> all = dao.getAllNow();
        if (all.isEmpty()) return;
        for (Contact c : all) if (c.isPrimary) return;
        all.get(0).isPrimary = true;
        dao.update(all.get(0));
    }
}
