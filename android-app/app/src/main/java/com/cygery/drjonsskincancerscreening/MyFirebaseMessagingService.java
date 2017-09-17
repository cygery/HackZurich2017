package com.cygery.drjonsskincancerscreening;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = MyFirebaseMessagingService.class.getName();

    private static final String CHANNEL_ID = "my_channel";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().size() > 0) {
            String data = remoteMessage.getData().get("data");
            try {
                JSONObject json = new JSONObject(data);

                String filename = json.getString("filename");
                double prob = json.getDouble("prob");

                // message indicating successful receiving of image
                if (prob == -1) {
                    return;
                }

                storeScreening(filename, prob);

                String adviceString;
                if (prob < 0.1) {
                    adviceString = "Skin cancer unlikely";
                } else if (prob < 0.5) {
                    adviceString = "Consider seeing a doctor";
                } else {
                    adviceString = "Doctor visit highly recommended!";
                }

                String shortText = "Melanoma probability: " + Math.round(prob * 100) + "% (expand for more details)";
                String longText = "Melanoma probability: " + Math.round(prob * 100) + "%" + "\n" + adviceString + "\n" +
                                  "(algorithmic result - no limit or warranty)";

                NotificationManager notificationManager = (NotificationManager) getSystemService(
                        Service.NOTIFICATION_SERVICE);
                Intent intent = new Intent(this, DetailActivity.class);
                intent.putExtra(DetailActivity.EXTRA_FILENAME, filename);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                                                                        PendingIntent.FLAG_UPDATE_CURRENT);

                CharSequence name = getString(R.string.channel_name);
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                notificationManager.createNotificationChannel(channel);

                Notification notification = new Notification.Builder(this, CHANNEL_ID).setContentTitle(
                        "Your Skin Cancer Screening Result")
                                                                                      .setContentText(shortText)
                                                                                      .setStyle(
                                                                                              new Notification.BigTextStyle()
                                                                                                      .bigText(
                                                                                                              longText))
                                                                                      .setContentIntent(pendingIntent)
                                                                                      .setSmallIcon(
                                                                                              R.mipmap.ic_launcher_round)
                                                                                      .setAutoCancel(true)
                                                                                      .build();

                notificationManager.notify(1, notification);

                sendBroadcast(new Intent(MainActivity.ACTION_REFRESH_DATA));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void storeScreening(String filename, double prob) throws JSONException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        JSONObject screenings = null;
        try {
            screenings = new JSONObject(settings.getString("screenings_json", "{}"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        SharedPreferences.Editor editor = settings.edit();
        JSONObject screening = screenings.getJSONObject(filename);
        screening.put("prob", prob);
        screenings.put(filename, screening);
        editor.putString("screenings_json", screenings.toString());
        editor.apply();
    }
}
