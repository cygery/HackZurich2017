package com.cygery.drjonsskincancerscreening;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionMenu;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();

    public static final int REQUEST_CAMERA = 0;
    public static final int REQUEST_GALLERY = 1;

    public static final String ACTION_REFRESH_DATA = "action_refresh_data";

    OkHttpClient client = new OkHttpClient();
    String currentPhotoPath;
    SharedPreferences settings;
    JSONObject screenings;
    DataUpdateReceiver dataUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadItems();

        final FloatingActionMenu fab_menu = findViewById(R.id.fabmenu);

        findViewById(R.id.fab_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (photoFile != null) {
                    Uri photoUri = FileProvider.getUriForFile(MainActivity.this, getString(R.string.file_authority),
                                                              photoFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(intent, REQUEST_CAMERA);
                }
                fab_menu.close(true);
            }
        });

        findViewById(R.id.fab_gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK,
                                           android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, REQUEST_GALLERY);
                fab_menu.close(true);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (dataUpdateReceiver == null) {
            dataUpdateReceiver = new DataUpdateReceiver();
        }
        IntentFilter intentFilter = new IntentFilter(ACTION_REFRESH_DATA);
        registerReceiver(dataUpdateReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        if (dataUpdateReceiver != null) {
            unregisterReceiver(dataUpdateReceiver);
        }

        super.onPause();
    }

    private void loadItems() {
        try {
            screenings = new JSONObject(settings.getString("screenings_json", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        ArrayList<Screening> items = new ArrayList<>();
        for (Iterator<String> it = screenings.keys(); it.hasNext(); ) {
            String key = it.next();
            try {
                Screening screening = new Screening(key, screenings.getJSONObject(key));
                items.add(screening);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        final ScreeningsAdapter adapter = new ScreeningsAdapter(this, items);
        ListView listView = findViewById(R.id.listview);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Screening screening = adapter.getItem(i);

                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                intent.putExtra(DetailActivity.EXTRA_FILENAME, screening.file);
                startActivity(intent);
            }
        });

        if (items.size() > 0) {
            findViewById(R.id.background_placeholder).setVisibility(View.GONE);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);

        currentPhotoPath = image.getAbsolutePath();

        return image;
    }

    public void storeScreening(File file, String path) throws JSONException {
        SharedPreferences.Editor editor = settings.edit();
        screenings.put(file.getName(),
                       new JSONObject().put("path", path).put("prob", -1.).put("ts", System.currentTimeMillis()));
        editor.putString("screenings_json", screenings.toString());
        editor.apply();
    }

    public boolean uploadToServer(File file) throws IOException {
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();

        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                                                      .addFormDataPart("file", file.getName(),
                                                                       RequestBody.create(MediaType.parse("image/jpg"),
                                                                                          file))
                                                      .addFormDataPart("token", refreshedToken)
                                                      .build();
        Request request = new Request.Builder().url(getString(R.string.upload_endpoint)).post(body).build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            Handler handler = new Handler(getMainLooper());

            @Override
            public void onFailure(Call call, final IOException e) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Upload failed. Please try again later.", Toast.LENGTH_SHORT)
                             .show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                       "Upload successful. We will notify you once your results are available. This can take a couple of minutes.",
                                       Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);

        switch (requestCode) {
            case REQUEST_CAMERA:
                if (resultCode == RESULT_OK) {
                    File f = new File(currentPhotoPath);

                    try {
                        storeScreening(f, currentPhotoPath);
                        uploadToServer(f);
                        loadItems();
                    } catch (IOException | JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case REQUEST_GALLERY:
                if (resultCode == RESULT_OK) {
                    Uri imageUri = imageReturnedIntent.getData();

                    String[] projection = { MediaStore.Images.Media.DATA };
                    Cursor cursor = managedQuery(imageUri, projection, null, null, null);
                    startManagingCursor(cursor);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    cursor.moveToFirst();

                    File f = new File(cursor.getString(column_index));

                    try {
                        storeScreening(f, f.getPath());
                        uploadToServer(f);
                        loadItems();
                    } catch (IOException | JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    private class DataUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_REFRESH_DATA)) {
                loadItems();
            }
        }
    }
}
