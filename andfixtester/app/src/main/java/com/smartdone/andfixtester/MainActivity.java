package com.smartdone.andfixtester;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.alipay.euler.andfix.AndFix;

import junit.framework.*;

import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AndFix-Test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndFix.setup();
        setContentView(R.layout.activity_main);
        int ret = Test.add(1,2);
        Log.d(TAG, "add(1, 2) = " + ret);
        hook();
        ret = Test.add(1,2);
        Log.d(TAG, "add(1, 2) = " + ret);

    }

    private void hook() {
        try {
            // 用需要hook的目标方法去替换我们这里定义的保存就方法的方法
            Method method = Test.class.getDeclaredMethod("add", int.class, int.class);
            replaceMethod(getClassLoader(), MainActivity.class.getName(), "orig_add", method);
            // 用新的方法去替换目标方法
            method = MainActivity.class.getDeclaredMethod("new_add", int.class, int.class);
            replaceMethod(getClassLoader(), Test.class.getName(), "add", method);
        }catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }
    // 用来替换目标类中的方法
    private static int new_add(int a, int b) {
        return orig_add(a, b) * 2;
    }

    // 用来保存旧的方法
    private static int orig_add(int a, int b) {
        return 0;
    }

    private void replaceMethod(ClassLoader classLoader, String clz,
                               String meth, Method method) {
        try {
            Class<?> clazz = classLoader.loadClass(clz);
            if(clazz != null) {
                clazz = AndFix.initTargetClass(clazz);
                Method src = clazz.getDeclaredMethod(meth, method.getParameterTypes());
                AndFix.addReplaceMethod(src, method);
            }
        } catch (Exception e) {
            Log.e(TAG, "replaceMethod", e);
        }
    }


}
