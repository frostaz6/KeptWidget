package com.frostaz6.keptwidget;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.RemoteViews;

/** Per-widget styling shared by the list, grid, single-note and capture widgets. */
final class WidgetAppearance {
    private static final int DEFAULT_BACKGROUND = 88;
    private static final int DEFAULT_CARD = 96;
    private WidgetAppearance() {}

    static boolean dark(Context c, int widgetId) {
        String mode = SecureStore.preferences(c).getString("theme_" + widgetId, "dark");
        if ("light".equals(mode)) return false;
        if ("system".equals(mode)) {
            return (c.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
        return true;
    }

    static int opacity(Context c, int widgetId) {
        return clamp(SecureStore.preferences(c).getInt("opacity_" + widgetId, DEFAULT_BACKGROUND));
    }

    static int cardOpacity(Context c, int widgetId) {
        return clamp(SecureStore.preferences(c).getInt("card_opacity_" + widgetId, DEFAULT_CARD));
    }

    static int clamp(int n) { return Math.min(100, Math.max(0,n)); }

    static int text(boolean dark) { return dark ? 0xfff1f1ec : 0xff292d28; }
    static int muted(boolean dark) { return dark ? 0xffb5bdb3 : 0xff566250; }
    static int accent(boolean dark) { return dark ? 0xffc9ee9d : 0xff355829; }
    static int base(boolean dark) { return dark ? 0xff202225 : 0xfff6f5ef; }

    private static int alpha(int rgb, int opacity) {
        return Color.argb(Math.round(clamp(opacity) * 255f / 100f),
            Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private static int blend(int from, int to, float proportion) {
        return Color.rgb(
            Math.round(Color.red(from)*(1-proportion) + Color.red(to)*proportion),
            Math.round(Color.green(from)*(1-proportion) + Color.green(to)*proportion),
            Math.round(Color.blue(from)*(1-proportion) + Color.blue(to)*proportion));
    }

    static int noteColor(String hex, boolean dark) {
        int blank = dark ? 0xff303339 : Color.WHITE;
        if (hex == null || hex.isEmpty()) return blank;
        try {
            int source = Color.parseColor(hex);
            if (Color.red(source)>245 && Color.green(source)>245 && Color.blue(source)>245) return blank;
            return blend(blank, source, dark ? 0.37f : 0.58f);
        } catch(IllegalArgumentException ignored) { return blank; }
    }

    /** Preserve rounded XML shape and tint it; fall back to a flat translucent fill on Android 8–11. */
    static void tint(RemoteViews views, int viewId, int argb) {
        if (Build.VERSION.SDK_INT >= 31) {
            views.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(argb));
        } else {
            views.setInt(viewId, "setBackgroundColor", argb);
        }
    }

    static void applyBackground(Context context, RemoteViews views, int rootId, int widgetId) {
        boolean dark = dark(context, widgetId);
        tint(views,rootId,alpha(base(dark),opacity(context,widgetId)));
    }

    static void applyCard(Context context, RemoteViews views, int cardId, int widgetId, String noteColor) {
        boolean dark = dark(context,widgetId);
        tint(views,cardId,alpha(noteColor(noteColor,dark),cardOpacity(context,widgetId)));
    }

    static void applyCollectionHeader(Context context, RemoteViews views, int rootId, int id) {
        boolean dark = dark(context,id);
        applyBackground(context,views,rootId,id);
        views.setTextColor(R.id.widget_header,text(dark));
        views.setTextColor(R.id.widget_add_text,accent(dark));
        views.setTextColor(R.id.widget_add_list,accent(dark));
        views.setTextColor(R.id.widget_refresh,muted(dark));
        views.setTextColor(R.id.widget_empty,muted(dark));
    }

    static void clear(Context context,int widgetId) {
        SecureStore.preferences(context).edit().remove("theme_"+widgetId)
            .remove("opacity_"+widgetId).remove("card_opacity_"+widgetId).apply();
    }

    static Controls controls(Activity activity, LinearLayout root, int widgetId, boolean showCardOpacity) {
        Controls c = new Controls(activity, root, widgetId, showCardOpacity);
        return c;
    }

    static final class Controls {
        private final Activity activity;
        private final int id;
        private final Spinner mode;
        private final SeekBar background, cards;
        Controls(Activity activity,LinearLayout root,int id,boolean showCardOpacity) {
            this.activity=activity;
            this.id=id;
            TextView header=ScreenUi.label(activity,"Appearance",20,true);
            root.addView(header,ScreenUi.margins(activity,0,12,0,12));
            root.addView(ScreenUi.hint(activity,"Theme"),ScreenUi.margins(activity,0,0,0,5));
            mode=new Spinner(activity);
            String[] labels={"Dark","Light","Follow device"};
            ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item,labels);
            mode.setAdapter(adapter);
            String theme=SecureStore.preferences(activity).getString("theme_"+id,"dark");
            mode.setSelection("light".equals(theme)?1:"system".equals(theme)?2:0);
            root.addView(mode,ScreenUi.margins(activity,0,0,0,14));
            background=slider(root,"Widget background opacity",opacity(activity,id));
            cards=showCardOpacity?slider(root,"Note card opacity",cardOpacity(activity,id)):null;
            root.addView(ScreenUi.hint(activity,"Opacity 0% is transparent, 100% is solid. Text stays readable; note colors remain visible."),
                ScreenUi.margins(activity,0,5,0,18));
        }

        private SeekBar slider(LinearLayout root,String caption,int initial) {
            TextView label=ScreenUi.label(activity,caption+": "+initial+"%",15,false);
            root.addView(label,ScreenUi.margins(activity,0,5,0,4));
            SeekBar bar=new SeekBar(activity);
            bar.setMax(100);
            bar.setProgress(initial);
            root.addView(bar,ScreenUi.margins(activity,0,0,0,12));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(SeekBar b,int value,boolean fromUser) {
                    label.setText(caption+": "+value+"%");
                }
                @Override public void onStartTrackingTouch(SeekBar b){}
                @Override public void onStopTrackingTouch(SeekBar b){}
            });
            return bar;
        }

        void save() {
            String chosen=mode.getSelectedItemPosition()==1?"light":
                mode.getSelectedItemPosition()==2?"system":"dark";
            android.content.SharedPreferences.Editor e=SecureStore.preferences(activity).edit()
                .putString("theme_"+id,chosen).putInt("opacity_"+id,background.getProgress());
            if(cards!=null)e.putInt("card_opacity_"+id,cards.getProgress());
            e.apply();
        }
    }
}
