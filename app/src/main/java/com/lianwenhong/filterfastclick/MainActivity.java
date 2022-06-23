package com.lianwenhong.filterfastclick;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.lianwenhong.annotation.FastClick;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button btnFilter, btnNoFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnFilter = findViewById(R.id.id_tv_btn1);
        btnNoFilter = findViewById(R.id.id_tv_btn2);
        btnFilter.setOnClickListener(this);
        btnNoFilter.setOnClickListener(this);
//        btnFilter.setOnClickListener(new FilterFastClickListener() {
//            @Override
//            public void onNoDoubleClick(View v) {
//
//            }
//        });
//
//        View.OnClickListener onClickListener = new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                doClickNoFilter();
//            }
//        };
//
//        btnNoFilter.setOnClickListener((View.OnClickListener) Proxy.newProxyInstance(onClickListener.getClass().getClassLoader(), onClickListener.getClass().getInterfaces(), new InvocationHandler() {
//
//            private long lastClickTime;
//
//            private boolean isFastDoubleClick() {
//                long time = System.currentTimeMillis();
//                long timeD = time - lastClickTime;
//                if (0 < timeD && timeD < 500) {
//                    return true;
//                }
//                lastClickTime = time;
//                return false;
//            }
//
//            @Override
//            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//                if (isFastDoubleClick()) return null;
//                return method.invoke(onClickListener, args);
//            }
//        }));
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.id_tv_btn1:
                doClickFilter();
                break;
            case R.id.id_tv_btn2:
                doClickNoFilter();
                break;
        }
    }

    @FastClick(value = 1)
    public void doClickFilter() {
        Log.e("lianwenhong", " >>> 我点击了,时间戳:" + System.currentTimeMillis());
    }

    public void doClickNoFilter() {
        Log.e("lianwenhong", " >>> 我点击了,时间戳:" + System.currentTimeMillis());
    }
}