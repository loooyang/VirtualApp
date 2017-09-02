# 用VirtualApp无root绕过ssl证书校验

这目前还只是一个想法，正在试，感觉从理论上应该可以实现的。

后面我边自己尝试，边把结果跟新上去。

## 整体实现过程

1. 修改AndFix，并且把它融合到VirtualApp中
2. 使用AndFix来进行java层的hook，然后来完成ssl证书校验

> 为啥我要用AndFix，不用VirtualApp作者的lengend？
>
> 因为懒。AndFix native层核心代码挺少的，而且一看就懂，修改方便。

## 在VirtualApp中融入AndFix

> AndFix可以完成java方法的替换我们的方法。然后我们就可以去做些操作完成SSL证书校验的绕过

### 创建工程来测试一下

[https://github.com/smartdone/VirtualApp/tree/master/andfixtester](./https://github.com/smartdone/VirtualApp/tree/master/andfixtester)

第一步：创建一个普通的android工程，创建的时候不勾选C++支持，因为android studio默认使用的cmake，AndFix用的`Android.mk`

第二步：把AndFix的jni目录拷贝到我们创建的工程的，然后设置下build.gradle，添加下面这些东西去build so。

```
android {
	...
    sourceSets.main {
        jni.srcDirs 'src/main/jni'
    }
    externalNativeBuild {
        ndkBuild {
            path 'src/main/jni/Android.mk'
        }
    }
}
```

第三步：我们把AndFix这个类拷贝到我们的工程中来，保持它的包名不变。

第四步：创建一个类用来测试

### 需要解决的问题：

> 我最终会用第2种方案

#### 1. 调用旧的方法

这里有两种解决的方法：

第一种：只在java层去操作。比如说你要hook `Test.class`里面的`add`方法，然后你可以创建两个`add`方法。一个用来保存旧的方法，一个用来替换目标类中的方法。

```java
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
```

这种方式的效果

```txt
09-02 19:05:46.505 30813-30813/com.smartdone.andfixtester D/AndFix-Test: add(1, 2) = 3
09-02 19:05:46.505 30813-30813/com.smartdone.andfixtester D/AndFix-Test: add(1, 2) = 6
```

第二种：修改AndFix的代码，在底层把两个java方法交换了

以dalvik为例，在AndFix中，修改修改方法的代码如下：

```cpp
extern void __attribute__ ((visibility ("hidden"))) dalvik_replaceMethod(
		JNIEnv* env, jobject src, jobject dest) {
	jobject clazz = env->CallObjectMethod(dest, jClassMethod);
	ClassObject* clz = (ClassObject*) dvmDecodeIndirectRef_fnPtr(
			dvmThreadSelf_fnPtr(), clazz);
	clz->status = CLASS_INITIALIZED;

	Method* meth = (Method*) env->FromReflectedMethod(src);
	Method* target = (Method*) env->FromReflectedMethod(dest);
	LOGD("dalvikMethod: %s", meth->name);

//	meth->clazz = target->clazz;
	meth->accessFlags |= ACC_PUBLIC;
	meth->methodIndex = target->methodIndex;
	meth->jniArgInfo = target->jniArgInfo;
	meth->registersSize = target->registersSize;
	meth->outsSize = target->outsSize;
	meth->insSize = target->insSize;

	meth->prototype = target->prototype;
	meth->insns = target->insns;
	meth->nativeFunc = target->nativeFunc;
}
```

我们再去创建一个method，原方法和替换后的方法给交换了,修改后的代码如下

```cpp
extern void __attribute__ ((visibility ("hidden"))) dalvik_replaceMethod(
		JNIEnv* env, jobject src, jobject dest) {
	jobject clazz = env->CallObjectMethod(dest, jClassMethod);
	ClassObject* clz = (ClassObject*) dvmDecodeIndirectRef_fnPtr(
			dvmThreadSelf_fnPtr(), clazz);
	clz->status = CLASS_INITIALIZED;

	Method* meth = (Method*) env->FromReflectedMethod(src);
	Method* target = (Method*) env->FromReflectedMethod(dest);
	LOGD("dalvikMethod: %s", meth->name);

	Method* tmp = (Method *) malloc(sizeof(Method));

//	tmp->clazz = meth->clazz;
	tmp->accessFlags = meth->accessFlags;
	tmp->methodIndex = meth->methodIndex;
	tmp->jniArgInfo = meth->jniArgInfo;
	tmp->registersSize = meth->registersSize;
	tmp->outsSize = meth->outsSize;
	tmp->insSize = meth->insSize;

	tmp->prototype = meth->prototype;
	tmp->insns = meth->insns;
	tmp->nativeFunc = meth->nativeFunc;

//	meth->clazz = target->clazz;
	meth->accessFlags |= ACC_PUBLIC;
	meth->methodIndex = target->methodIndex;
	meth->jniArgInfo = target->jniArgInfo;
	meth->registersSize = target->registersSize;
	meth->outsSize = target->outsSize;
	meth->insSize = target->insSize;

	meth->prototype = target->prototype;
	meth->insns = target->insns;
	meth->nativeFunc = target->nativeFunc;

//	target->clazz = tmp->clazz;
	target->accessFlags |= ACC_PUBLIC;
	target->methodIndex = tmp->methodIndex;
	target->jniArgInfo = tmp->jniArgInfo;
	target->registersSize = tmp->registersSize;
	target->outsSize = tmp->outsSize;
	target->insSize = tmp->insSize;

	target->prototype = tmp->prototype;
	target->insns = tmp->insns;
	target->nativeFunc = tmp->nativeFunc;


	free(tmp);
}

```

这个时候调用原方法就很神奇了，因为我们现在这个方法和要hook的方法交换了，所以调用原方法的时候就只需要写自己就行啦，看起来的写法就和递归一样。

调用原方法demo如下：

```java
private void hook() {
    try {
        Method method = MainActivity.class.getDeclaredMethod("add", int.class, int.class);
        replaceMethod(getClassLoader(), Test.class.getName(), "add", method);
    }catch (Exception e) {
        Log.d(TAG, e.getMessage());
    }
}

// 这里看起来就特别的神奇了，看起来是无限递归，实际上并不会。哈哈哈
private static int add(int a, int b) {
    // 调用原方法，并且在原方法基础上面+1
    return add(a, b) + 1;
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
```

