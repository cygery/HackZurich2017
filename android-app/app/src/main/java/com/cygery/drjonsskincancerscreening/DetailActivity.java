package com.cygery.drjonsskincancerscreening;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {
    public static final String TAG = DetailActivity.class.getName();

    public static final String EXTRA_FILENAME = "extra_filename";

    SharedPreferences settings;
    JSONObject screenings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            screenings = new JSONObject(settings.getString("screenings_json", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        setContentView(R.layout.activity_detail);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getIntent().hasExtra(EXTRA_FILENAME)) {
            String filename = getIntent().getStringExtra(EXTRA_FILENAME);

            try {
                JSONObject screening = (JSONObject) screenings.get(filename);
                String path = screening.getString("path");
                long timeStamp = screening.getLong("ts");
                double prob = screening.getDouble("prob");

                ((TextView) findViewById(R.id.textViewTimeStampValue)).setText(
                        new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault()).format(timeStamp));
                if (prob != -1) {
                    ((TextView) findViewById(R.id.textViewProbabilityValue)).setText(Math.round(prob * 100) + "%");
                } else {
                    ((TextView) findViewById(R.id.textViewProbabilityValue)).setText("unknown");
                }

                Bitmap bitmap = BitmapFactory.decodeFile(new File(path).getAbsolutePath());
                ImageView imageView = findViewById(R.id.imageView);
                imageView.setImageBitmap(bitmap);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
