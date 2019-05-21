package com.testexcelview;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.lhg.excelview.ExcelView;
import com.lhg.excelview.ExcelView.Span;

public class ExcelFragment extends Fragment {
    private static final String TAG = "ExcelFragment";
    ExcelView excelView;
    MyAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_excel, null);
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        excelView = getView().findViewById(R.id.excelView);
        excelView.setAdapter(adapter = new MyAdapter());
        excelView.setBackgroundColor(Color.YELLOW);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.add("滚").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add("大").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add("粗").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add("色").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getTitle().equals("滚")) {
            excelView.scrollTo(800, 800);
        } else if (item.getTitle().equals("大")) {
            float weight = Math.max(2, (float) (10 * Math.random()));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) excelView.getLayoutParams();
            params.weight = weight;
            excelView.setLayoutParams(params);
        } else if (item.getTitle().equals("粗")) {
            int width = (int) (20 * Math.random());
            excelView.setDividerWidth(width);
        }else if (item.getTitle().equals("色")) {
            int colors[] = {
                    Color.BLUE, Color.YELLOW, Color.GREEN, Color.RED, Color.LTGRAY,
                    Color.MAGENTA, Color.CYAN
            };
            int i = (int) (colors.length * Math.random());
            excelView.setDividerColor(colors[i]);
        }
        return super.onOptionsItemSelected(item);
    }

    static Span spans[] = new Span[]{
            new Span(2, 2, 4, 3),
            new Span(3, 8, 4, 10),
            new Span(7, 4, 9, 4),
            new Span(6, 8, 8, 8),
    };
    static int col = 15, row = 20;

    class MyAdapter extends ExcelView.ExcelAdapter {
        int newCount = 0;
        @Override
        public Span querySpan(int row, int col) {
            for (Span span : spans) {
                if (span.contains(row, col)) {
                    return span;
                }
            }
            return null;
        }

        @Override
        public int getColCount() {
            return col;
        }

        @Override
        public int getRowCount() {
            return row;
        }

        @Override
        public int getRowHeight(int row) {
            return 150;
        }

        @Override
        public int getColWidth(int col) {
            return 200;
        }

        @Override
        public int getCellViewType(int row, int col) {
            if (row == 2 && col == 2) {
                return 1;
            }
            return 0;
        }

        @Override
        public View getCellView(Context context, View convertView, int row, int col) {
            if (getCellViewType(row, col) == 1) {
                if (convertView == null) {
                    convertView = new ImageView(context);
                    convertView.setOnClickListener(v -> Toast.makeText(context, "image click", Toast.LENGTH_SHORT).show());
                    newCount++;
                    Log.i(TAG, "newCount=" + newCount);
                }
                ImageView tv = (ImageView) convertView;
                Glide.with(tv).load("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1557473353600&di=bc560eeaeddeb821d7360e8c4e344675&imgtype=0&src=http%3A%2F%2Fimg2.ph.126.net%2F2zB3_wWPXlEW0RdwQa8d6A%3D%3D%2F2268688312388037455.jpg").into(tv);
                return convertView;
            }

            if (convertView == null) {
                convertView = new TextView(context);
                convertView.setBackgroundColor(Color.WHITE);
                newCount++;
                Log.i(TAG, "newCount=" + newCount);
            }
            TextView tv = (TextView) convertView;
            tv.setGravity(Gravity.CENTER);
            tv.setText(String.format("%d, %d", row, col));
            return convertView;
        }
    }
}
