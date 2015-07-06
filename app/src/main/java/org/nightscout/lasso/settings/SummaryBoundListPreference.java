package org.nightscout.lasso.settings;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class SummaryBoundListPreference extends ListPreference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SummaryBoundListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SummaryBoundListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SummaryBoundListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SummaryBoundListPreference(Context context) {
        super(context);
    }

    @Override
    public void setValue(String text) {
        super.setValue(text);
        String summary = String.valueOf(getEntries()[Integer.valueOf(text)]);
        setSummary(summary);
    }

}
