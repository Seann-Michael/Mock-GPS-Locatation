package com.seannmichael.mockdrive;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import java.util.List;

public class PlacesSuggestionAdapter extends ArrayAdapter<String> {
    private final List<String> values;

    public PlacesSuggestionAdapter(Context context, List<String> values) {
        super(context, android.R.layout.simple_dropdown_item_1line, values);
        this.values = values;
    }

    @Override public int getCount() { return values.size(); }
    @Override public String getItem(int position) { return values.get(position); }

    @Override public Filter getFilter() {
        return new Filter() {
            @Override protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                results.values = values;
                results.count = values.size();
                return results;
            }

            @Override protected void publishResults(CharSequence constraint, FilterResults results) {
                notifyDataSetChanged();
            }
        };
    }
}