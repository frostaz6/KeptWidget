package com.frostaz6.keptwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final List<JSONObject> labels = new ArrayList<>();
    private Spinner labelSpinner;
    private RadioGroup modes;
    private TextView status;
    private WidgetAppearance.Controls appearance;
    private final int ALL = 1, PINNED = 2, LABEL = 3;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }

        LinearLayout root = ScreenUi.page(this, "Choose notes");
        root.addView(ScreenUi.hint(this, "Pick which notes this widget should show. You can add multiple widgets with different filters."),
            ScreenUi.margins(this,0,0,0,22));
        modes = new RadioGroup(this);
        modes.setOrientation(LinearLayout.VERTICAL);
        RadioButton all = option("All notes", ALL);
        RadioButton pinned = option("Pinned notes", PINNED);
        RadioButton label = option("A specific label", LABEL);
        modes.addView(all);
        modes.addView(pinned);
        modes.addView(label);
        modes.check(ALL);
        root.addView(modes, ScreenUi.margins(this,0,0,0,18));
        labelSpinner = new Spinner(this);
        ArrayAdapter<String> choices = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, new ArrayList<>(List.of("Loading labels…")));
        labelSpinner.setAdapter(choices);
        root.addView(labelSpinner, ScreenUi.margins(this,0,0,0,12));
        modes.setOnCheckedChangeListener((g, id) -> labelSpinner.setVisibility(id == LABEL ? View.VISIBLE : View.GONE));
        labelSpinner.setVisibility(View.GONE);
        appearance = WidgetAppearance.controls(this,root,widgetId,true);
        status = ScreenUi.hint(this, "");
        root.addView(status, ScreenUi.margins(this,0,0,0,18));

        String saved = SecureStore.preferences(this).getString("filter_" + widgetId, "all");
        if ("pinned".equals(saved)) modes.check(PINNED);
        else if (saved.startsWith("label:")) modes.check(LABEL);

        Button save = ScreenUi.button(this, "Add widget", true);
        root.addView(save, ScreenUi.margins(this,0,0,0,10));
        save.setOnClickListener(v -> finishConfiguration());
        if (!SecureStore.token(this).isEmpty()) loadLabels(choices);
        else status.setText("Sign in to the Kept Widget app to load labels.");
    }

    private RadioButton option(String title, int id) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(title);
        button.setTextSize(17);
        button.setPadding(0, ScreenUi.dp(this,8), 0, ScreenUi.dp(this,8));
        return button;
    }

    private void loadLabels(ArrayAdapter<String> adapter) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONArray response = KeptApi.labels(this);
                List<JSONObject> collected = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (int i = 0; i < response.length(); i++) {
                    JSONObject value = response.optJSONObject(i);
                    if (value != null && !value.optString("name").isBlank()) {
                        collected.add(value);
                        names.add(value.optString("name"));
                    }
                }
                runOnUiThread(() -> {
                    labels.clear();
                    labels.addAll(collected);
                    adapter.clear();
                    if (names.isEmpty()) adapter.add("No labels found");
                    else adapter.addAll(names);
                    adapter.notifyDataSetChanged();
                    String saved = SecureStore.preferences(this).getString("filter_" + widgetId, "");
                    for (int i = 0; i < labels.size(); i++) {
                        if (saved.equals("label:" + labels.get(i).optLong("id", -1)))
                            labelSpinner.setSelection(i);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Can't load labels: " + e.getMessage()));
            }
        });
    }

    private void finishConfiguration() {
        String filter = "all", title = "Kept";
        if (modes.getCheckedRadioButtonId() == PINNED) {
            filter = "pinned";
            title = "Pinned";
        } else if (modes.getCheckedRadioButtonId() == LABEL) {
            int index = labelSpinner.getSelectedItemPosition();
            if (index < 0 || index >= labels.size()) {
                status.setText("Choose an available label, or select All notes.");
                return;
            }
            JSONObject label = labels.get(index);
            filter = "label:" + label.optLong("id");
            title = label.optString("name", "Kept");
        }
        appearance.save();
        SecureStore.preferences(this).edit().putString("filter_" + widgetId, filter)
            .putString("title_" + widgetId, title).apply();
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        android.appwidget.AppWidgetProviderInfo info = manager.getAppWidgetInfo(widgetId);
        if (info != null && info.provider.getClassName().endsWith("GridCollectionWidgetProvider"))
            GridCollectionWidgetProvider.render(this,manager,widgetId);
        else CollectionWidgetProvider.render(this,manager,widgetId);
        Intent done = new Intent();
        done.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, done);
        finish();
    }
}
