package com.lianwenhong.filterfastclick;

import android.view.View;

public abstract class FilterFastClickListener implements View.OnClickListener {

    private long lastClickTime;

    private boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        long timeD = time - lastClickTime;
        if (0 < timeD && timeD < 500) {
            return true;
        }
        lastClickTime = time;
        return false;
    }

    @Override
    public void onClick(View v) {
        if (isFastDoubleClick()) {
            return;
        }
        onNoDoubleClick(v);
    }

    public abstract void onNoDoubleClick(final View v);
}
