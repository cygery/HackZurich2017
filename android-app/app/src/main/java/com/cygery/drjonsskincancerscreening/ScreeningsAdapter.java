package com.cygery.drjonsskincancerscreening;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class ScreeningsAdapter extends ArrayAdapter<Screening> {
    public ScreeningsAdapter(Context context, ArrayList<Screening> items) {
        super(context, 0, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Screening screening = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_screening, parent, false);
        }
        TextView textViewTimeStampValue = (TextView) convertView.findViewById(R.id.textViewTimeStampValue);
        TextView textViewProbabilityValue = (TextView) convertView.findViewById(R.id.textViewProbabilityValue);
        ImageView imageView = convertView.findViewById(R.id.imageView);

        textViewTimeStampValue.setText(
                new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault()).format(screening.ts));
        if (screening.prob != -1) {
            textViewProbabilityValue.setText(Math.round(screening.prob * 100) + "%");
        } else {
            textViewProbabilityValue.setText("unknown");
        }

        Bitmap myBitmap = BitmapFactory.decodeFile(new File(screening.path).getAbsolutePath());
        imageView.setImageBitmap(myBitmap);

        return convertView;
    }
}
