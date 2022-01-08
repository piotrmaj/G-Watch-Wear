/*
 * Copyright (C) 2021 Juraj Antal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sk.trupici.gwatch.wear.config.complications;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import sk.trupici.gwatch.wear.BuildConfig;
import sk.trupici.gwatch.wear.R;
import sk.trupici.gwatch.wear.config.ActivityResultAware;
import sk.trupici.gwatch.wear.config.AnalogWatchfaceConfig;
import sk.trupici.gwatch.wear.config.BackgroundChangeAware;
import sk.trupici.gwatch.wear.config.BorderPickerActivity;
import sk.trupici.gwatch.wear.config.BorderType;
import sk.trupici.gwatch.wear.config.ColorPickerActivity;
import sk.trupici.gwatch.wear.config.ConfigPageData;
import sk.trupici.gwatch.wear.config.PickerViewHolder;
import sk.trupici.gwatch.wear.config.item.BasicConfigItem;
import sk.trupici.gwatch.wear.config.item.ConfigItem;

import static android.app.Activity.RESULT_OK;
import static sk.trupici.gwatch.wear.util.BorderUtils.BORDER_DASH_LEN;
import static sk.trupici.gwatch.wear.util.BorderUtils.BORDER_DOT_LEN;
import static sk.trupici.gwatch.wear.util.BorderUtils.BORDER_GAP_LEN;
import static sk.trupici.gwatch.wear.util.BorderUtils.BORDER_RING_RADIUS;
import static sk.trupici.gwatch.wear.util.BorderUtils.BORDER_ROUND_RECT_RADIUS;
import static sk.trupici.gwatch.wear.util.BorderUtils.BORDER_WIDTH;
import static sk.trupici.gwatch.wear.util.CommonConstants.BORDER_TYPE_CONFIG_REQUEST_CODE;
import static sk.trupici.gwatch.wear.util.CommonConstants.COMPLICATION_CONFIG_REQUEST_CODE;
import static sk.trupici.gwatch.wear.util.CommonConstants.UPDATE_COLORS_CONFIG_REQUEST_CODE;

public class ComplicationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements BackgroundChangeAware, ActivityResultAware {

    final private static String LOG_TAG = ComplicationAdapter.class.getSimpleName();
    final private Context context;
    final private ComponentName componentName;
    final private ConfigItem[] items;
    final private AnalogWatchfaceConfig config;

    final private SharedPreferences prefs;

    // Required to retrieve complication data from watch face for preview.
    final private ProviderInfoRetriever providerInfoRetriever;

    private ComplicationId selectedComplicationId;

    final private Drawable defaultComplicationDrawable;

    // Maintains reference view holder to dynamically update watch face preview. Used instead of
    // notifyItemChanged(int position) to avoid flicker and re-inflating the view.
    private ComplicationsViewHolder complicationViewHolder;

    public ComponentName getComponentName() {
        return componentName;
    }

    public ComplicationAdapter(Context context, ComponentName componentName,
                               ConfigItem[] items, AnalogWatchfaceConfig config,
                               SharedPreferences prefs) {
        this.context = context;
        this.componentName = componentName;
        this.items = items;

        this.prefs = prefs;
        this.config = config;

        defaultComplicationDrawable = context.getDrawable(R.drawable.config_add_complication);

        // Initialization of code to retrieve active complication data for the watch face.
        this.providerInfoRetriever = new ProviderInfoRetriever(context, Executors.newCachedThreadPool());
        providerInfoRetriever.init();

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "ComplicationAdapter: pre-selecting LEFT_COMPLICATION_ID");
        }
        selectedComplicationId = ComplicationId.LEFT_COMPLICATION_ID;
    }


    public void destroy() {
        try {
            providerInfoRetriever.release();
        } catch (Exception e) {
            Log.e(LOG_TAG, "destroy: " + e.getLocalizedMessage());
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "onCreateViewHolder(): viewType: " + viewType);
        }
        RecyclerView.ViewHolder viewHolder;

//        ComplicationId defaultComplicationId = ComplicationId.LEFT_COMPLICATION_ID;
        ConfigItem.Type type = ConfigItem.Type.valueOf(viewType);
        switch (type) {
            case TYPE_COMPLICATION:
                ComplicationConfigItem complItem = (ComplicationConfigItem) getConfigItemByType(type);
                View layout = LayoutInflater.from(parent.getContext()).inflate(complItem.getLayoutId(), parent, false);
                viewHolder = new ComplicationsViewHolder(context, this, layout);
                complicationViewHolder = (ComplicationsViewHolder) viewHolder;
                break;
            case TYPE_BORDER_COLOR:
            case TYPE_DATA_COLOR:
            case TYPE_BKG_COLOR:
                viewHolder = new PickerViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.config_list_button_item, parent, false),
                        view -> {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "complication callback: " + selectedComplicationId + ", " + view + ", " + view.getParent());
                            }
                            Intent launchIntent = new Intent(context, ColorPickerActivity.class);
                            launchIntent.putExtra(ColorPickerActivity.EXTRA_ITEM_ID, selectedComplicationId.ordinal());
                            launchIntent.putExtra(ColorPickerActivity.EXTRA_ITEM_TYPE, type.ordinal());
                            launchIntent.putExtra(ColorPickerActivity.EXTRA_COLOR, getItemColor(selectedComplicationId, type));
                            ((Activity)context).startActivityForResult(launchIntent, UPDATE_COLORS_CONFIG_REQUEST_CODE);
                        }
                );
                break;
            case TYPE_PADDING:
                viewHolder = new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.config_list_empty_item, parent, false)) {
                };
                break;
            case TYPE_BORDER_TYPE:
                viewHolder = new PickerViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.config_list_button_item, parent, false),
                        view -> {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "complication callback: " + selectedComplicationId + ", " + view + ", " + view.getParent());
                            }
                            Intent launchIntent = new Intent(context, BorderPickerActivity.class);
                            launchIntent.putExtra(BorderPickerActivity.EXTRA_ITEM_ID, selectedComplicationId.ordinal());
                            launchIntent.putExtra(BorderPickerActivity.EXTRA_ITEM_TYPE, ConfigItem.Type.TYPE_BORDER_TYPE.ordinal());
                            launchIntent.putExtra(BorderPickerActivity.EXTRA_BORDER_TYPE, getBorderType(selectedComplicationId));
                            ((Activity)context).startActivityForResult(launchIntent, BORDER_TYPE_CONFIG_REQUEST_CODE);
                        });
                break;
            default:
                throw new IllegalArgumentException(""+type);
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "onBindViewHolder: " + position);
        }
        // Pulls all data required for creating the UX for the specific setting option.
        ConfigItem configItem = items[position];

        switch (ConfigItem.Type.valueOf(holder.getItemViewType())) {
            case TYPE_COMPLICATION:
                onBackgroundChanged();
                initializeComplications();
                break;
            case TYPE_BORDER_COLOR:
            case TYPE_DATA_COLOR:
            case TYPE_BKG_COLOR:
            case TYPE_BORDER_TYPE:
                PickerViewHolder pickerViewHolder = (PickerViewHolder) holder;
                BasicConfigItem basicConfigItem = (BasicConfigItem) configItem;
                pickerViewHolder.setIcon(basicConfigItem.getIconResourceId());
                pickerViewHolder.setName(basicConfigItem.getLabelResourceId());
                break;
            case TYPE_PADDING:
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void onBackgroundChanged() {
        if (complicationViewHolder == null) {
            return;
        }

        ViewGroup bkgViewGroup = complicationViewHolder.getBackgroundViewGroup();
        bkgViewGroup.removeAllViews();
        addBackgroundView(bkgViewGroup,
                config.getConfigItemData(ConfigPageData.ConfigType.BACKGROUND,
                        prefs.getInt(config.PREF_BACKGROUND_IDX, config.DEF_BACKGROUND_IDX)
                ).getResourceId());
        addBackgroundView(bkgViewGroup,
                config.getConfigItemData(ConfigPageData.ConfigType.HANDS,
                        prefs.getInt(config.PREF_HANDS_SET_IDX, config.DEF_HANDS_SET_IDX)
                ).getResourceId());
    }

    void addBackgroundView(ViewGroup bkgViewGroup, int resourceId) {
        if (resourceId == 0) {
            return;
        }

        ViewGroup view = (ViewGroup) LayoutInflater.from(bkgViewGroup.getContext()).inflate(R.layout.layout_config_item_page, bkgViewGroup, false);
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "bounds: " + view.getClipBounds());
        }
        ImageView bkgView = view.findViewById(R.id.image);
        bkgView.setImageDrawable(bkgViewGroup.getContext().getDrawable(resourceId));
        ImageView more = view.findViewById(R.id.label_image);
        more.setImageDrawable(bkgViewGroup.getContext().getDrawable(R.drawable.config_more_items));
        bkgViewGroup.addView(view);
//            bkgViewGroup.invalidate();

    }

    @Override
    public int getItemViewType(int position) {
        ConfigItem configItem = items[position];
        return configItem.getConfigType().ordinal();
    }

    @Override
    public int getItemCount() {
        return items.length;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case COMPLICATION_CONFIG_REQUEST_CODE:
                    // Retrieves information for selected Complication provider.
                    ComplicationProviderInfo complicationProviderInfo = data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO);
                    Integer id = data.getParcelableExtra(ProviderChooserIntent.EXTRA_COMPLICATION_ID);
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "onActivityResult: complicationId=" + id);
                    }
                    // Updates preview with new complication information for selected complication id.
                    // Note: complication id is saved and tracked in the adapter class.
                    return complicationViewHolder.updateComplicationView(context, complicationProviderInfo, id);
                case UPDATE_COLORS_CONFIG_REQUEST_CODE:
                    int color = data.getIntExtra(ColorPickerActivity.EXTRA_COLOR, Color.TRANSPARENT);
                    ConfigItem.Type type = ConfigItem.Type.valueOf(data.getExtras().getInt(ColorPickerActivity.EXTRA_ITEM_TYPE));
                    id = (Integer) data.getExtras().get(ColorPickerActivity.EXTRA_ITEM_ID);
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "onActivityResult: complicationId=" + id);
                    }
                    return updatePreviewColors(type, id, color);
                case BORDER_TYPE_CONFIG_REQUEST_CODE:
                    type = ConfigItem.Type.valueOf(data.getExtras().getInt(BorderPickerActivity.EXTRA_ITEM_TYPE));
                    BorderType borderType = (BorderType) data.getExtras().get(BorderPickerActivity.EXTRA_BORDER_TYPE);
                    id = (Integer) data.getExtras().get(BorderPickerActivity.EXTRA_ITEM_ID);
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "onActivityResult: complicationId=" + id);
                    }
                    return updateBorderType(type, id, borderType);
                default:
                    break;
            }
        }
        return false;
    }


    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        // Required to release retriever for active complication data on detach.
        providerInfoRetriever.release();
    }

    private boolean updatePreviewColors(ConfigItem.Type type, Integer id, Integer color) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "updatePreviewColors: " + type + ", " + color);
        }
        if (type == null) {
            Log.e(LOG_TAG, "updatePreviewColors: no item type specified");
            return false;
        }

        if (id == null) {
            return false;
        }
        ComplicationId complicationId = ComplicationId.valueOf(id);

        Drawable drawable;
        switch (type) {
            case TYPE_BORDER_COLOR:
                drawable = createBorderDrawable(getBorderType(complicationId), color, true);
                complicationViewHolder.setBorder(complicationId, drawable);
                break;
            case TYPE_DATA_COLOR:
                PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                complicationViewHolder.setColorFilter(complicationId, colorFilter);
                break;
            case TYPE_BKG_COLOR:
                BorderType borderType = getBorderType(complicationId);
                drawable = createBorderDrawable(borderType, color, false);
                if (borderType == BorderType.NONE) {
                    ((GradientDrawable)drawable).setColor(color);
                }
                complicationViewHolder.setBackground(complicationId, drawable);
                break;
            default:
                Log.e(LOG_TAG, "updatePreviewColors: " + "unsupported component type: " + type.name());
                return false;
        }
        BasicConfigItem item = (BasicConfigItem) getConfigItemByType(type);
        String prefName = config.PREF_PREFIX + ComplicationConfig.getComplicationPrefix(complicationId) + item.getPreferenceName();
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "updatePreviewColors: " + prefName + " -> " + color);
        }
        prefs.edit().putInt(prefName, color).commit();
        return true;
    }

    private BorderType getBorderType(ComplicationId complicationId) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "getBorderType: " + complicationId.name());
        }
        BasicConfigItem borderTypeItem = (BasicConfigItem) getConfigItemByType(ConfigItem.Type.TYPE_BORDER_TYPE);
        String prefName = config.PREF_PREFIX + ComplicationConfig.getComplicationPrefix(complicationId) + borderTypeItem.getPreferenceName();
        String borderTypeName = prefs.getString(prefName, null);
        if (borderTypeName == null && borderTypeItem.getDefaultValueResourceId() != -1) {
            borderTypeName = context.getResources().getString(borderTypeItem.getDefaultValueResourceId());
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "getBorderType: '" + prefName + "' " + borderTypeName);
        }
        return BorderType.getByNameOrDefault(borderTypeName);
    }

    private int getItemColor(ComplicationId complicationId, ConfigItem.Type type) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "getItemColor: " + complicationId.name() + ", " + type.name());
        }
        BasicConfigItem colorTypeItem = (BasicConfigItem) getConfigItemByType(type);
        String prefName = config.PREF_PREFIX + ComplicationConfig.getComplicationPrefix(complicationId) + colorTypeItem.getPreferenceName();
        int defaultValue = colorTypeItem.getDefaultValueResourceId() == -1 ? Color.TRANSPARENT
                : context.getResources().getColor(colorTypeItem.getDefaultValueResourceId(), null);
        int color = prefs.getInt(prefName, defaultValue);
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "getColor: '" + prefName + "' " + color);
        }
        return color;
    }

    private Drawable createBorderDrawable(BorderType borderType, int color, boolean setStroke) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "getBorderDrawable: " + borderType + ", " + color);
        }
        if (borderType == null) {
            borderType = BorderType.NONE;
        }

        GradientDrawable drawable = new GradientDrawable();
        switch (borderType) {
            case RECT:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(0);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, 0);
                break;
            case ROUNDED_RECT:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(BORDER_ROUND_RECT_RADIUS);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, 0);
                break;
            case RING:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(BORDER_RING_RADIUS);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, 0);
                break;
            case DASHED_RECT:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(0);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, BORDER_DASH_LEN);
                break;
            case DASHED_ROUNDED_RECT:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(BORDER_ROUND_RECT_RADIUS);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, BORDER_DASH_LEN);
                break;
            case DASHED_RING:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(BORDER_RING_RADIUS);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, BORDER_DASH_LEN);
                break;
            case DOTTED_RECT:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(0);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, BORDER_DOT_LEN);
                break;
            case DOTTED_ROUNDED_RECT:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(BORDER_ROUND_RECT_RADIUS);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, BORDER_DOT_LEN);
                break;
            case DOTTED_RING:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(BORDER_RING_RADIUS);
                drawableSetBorderStrokeOrColor(drawable, color, setStroke, BORDER_DOT_LEN);
                break;
            default:
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(0);
                drawable.setColor(Color.TRANSPARENT);
                break;
        }
        return drawable;
    }

    private void drawableSetBorderStrokeOrColor(GradientDrawable drawable, int color, boolean setStroke, float dashWidth) {
        if (setStroke) {
            drawable.setStroke(BORDER_WIDTH, color, dashWidth, BORDER_GAP_LEN);
        } else {
            drawable.setColor(color);
        }
    }


    private boolean updateBorderType(ConfigItem.Type type, Integer id, BorderType borderType) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "updateBorderType: " + borderType);
        }
        if (borderType == null) {
            Log.e(LOG_TAG, "updateBorderType: no border type specified");
            return false;
        }

        if (id == null) {
            return false;
        }
        ComplicationId complicationId = ComplicationId.valueOf(id);

        String prefix = config.PREF_PREFIX + ComplicationConfig.getComplicationPrefix(complicationId);
        BasicConfigItem item = (BasicConfigItem) getConfigItemByType(ConfigItem.Type.TYPE_BORDER_TYPE);
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "updateBorderType: " + prefix + item.getPreferenceName() + " -> " + borderType);
        }
        prefs.edit().putString(prefix + item.getPreferenceName(), borderType.name()).commit();

        // draw border
        BasicConfigItem colorItem = (BasicConfigItem) getConfigItemByType(ConfigItem.Type.TYPE_BORDER_COLOR);
        int defaultValue = colorItem.getDefaultValueResourceId() == -1 ? Color.TRANSPARENT
                : context.getResources().getColor(colorItem.getDefaultValueResourceId(), null);
        int color = prefs.getInt(prefix + colorItem.getPreferenceName(), defaultValue);
        Drawable drawable = createBorderDrawable(borderType, color, true);
        complicationViewHolder.setBorder(complicationId, drawable);

        // draw background
        colorItem = (BasicConfigItem) getConfigItemByType(ConfigItem.Type.TYPE_BKG_COLOR);
        defaultValue = colorItem.getDefaultValueResourceId() == -1 ? Color.TRANSPARENT
                : context.getResources().getColor(colorItem.getDefaultValueResourceId(), null);
        color = prefs.getInt(prefix + colorItem.getPreferenceName(), defaultValue);
        drawable = createBorderDrawable(borderType, color, false);
        if (borderType == BorderType.NONE) {
            ((GradientDrawable)drawable).setColor(color);
        }
        complicationViewHolder.setBackground(complicationId, drawable);

        return true;
    }

    private ConfigItem getConfigItemByType(ConfigItem.Type type) {
        for (ConfigItem item : items) {
            if (item.getConfigType() == type) {
                return item;
            }
        }
        return null;
    }

    public void initializeComplications() {

        final int[] complicationIds = Config.getComplicationIds();

        for (int id : complicationIds) {
            ComplicationId complicationId = ComplicationId.valueOf(id);

            String prefix = config.PREF_PREFIX + ComplicationConfig.getComplicationPrefix(complicationId);
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "initializeComplications: " + complicationId.name() + ", " + prefix);
            }
            BorderType borderType = getBorderType(complicationId);

            // draw background
            BasicConfigItem colorItem = (BasicConfigItem) getConfigItemByType(ConfigItem.Type.TYPE_BKG_COLOR);
            int color = prefs.getInt(prefix + colorItem.getPreferenceName(), Color.TRANSPARENT);
            Drawable drawable = createBorderDrawable(borderType, color, false);
            if (borderType == BorderType.NONE) {
                ((GradientDrawable) drawable).setColor(color);
            }
            complicationViewHolder.setBackground(complicationId, drawable);

            // set data color
            colorItem = (BasicConfigItem) getConfigItemByType(ConfigItem.Type.TYPE_DATA_COLOR);
            color = prefs.getInt(prefix + colorItem.getPreferenceName(), Color.TRANSPARENT);
            PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            complicationViewHolder.setColorFilter(complicationId, colorFilter);

            // set default drawable
            complicationViewHolder.setDrawable(complicationId, defaultComplicationDrawable);

            // draw border
            if (borderType != BorderType.NONE) {
                // draw border
                colorItem = (BasicConfigItem) getConfigItemByType(ConfigItem.Type.TYPE_BORDER_COLOR);
                color = prefs.getInt(prefix + colorItem.getPreferenceName(), Color.TRANSPARENT);
                drawable = createBorderDrawable(borderType, color, true);
                complicationViewHolder.setBorder(complicationId, drawable);
            }

            providerInfoRetriever.retrieveProviderInfo(
                    new ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                        @Override
                        public void onProviderInfoReceived(int watchFaceComplicationId, @Nullable ComplicationProviderInfo complicationProviderInfo) {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "onProviderInfoReceived: " + watchFaceComplicationId + ", " + complicationProviderInfo);
                            }
                            if (complicationProviderInfo != null) {
                                ComplicationId complicationId = ComplicationId.valueOf(watchFaceComplicationId);
                                complicationViewHolder.setIcon(complicationId, complicationProviderInfo.providerIcon);
                            }
                        }
                    },
                    componentName,
                    complicationIds);
        }
    }

    public ComplicationId getSelectedComplicationId() {
        return selectedComplicationId;
    }

    public void setSelectedComplicationId(ComplicationId selectedComplicationId) {
        this.selectedComplicationId = selectedComplicationId;
    }
}
