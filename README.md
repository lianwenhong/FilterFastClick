# FilterFastClick
**首先声明：本库的编写是被QA逼出来的。**

在开发过程中经常被QA提一类恶心的bug（快速点击控件出现多个页面或多个弹窗等）

我司QA拿到包之后第一件事就爱测快速点击的场景，他们认为同一个按钮快速点击可能是误操作不应该得到响应。比如某个按钮点击之后应该弹出一个dialog，QA双击控件就出现2个dialog。这原本应该是个正常的场景，但是奈何抵不过他们一直提，所以就做了这个小功能加以限制。

其实这就是个典型的AOP编程了。

屏蔽快速点击最常见的操作是做一个工具类:
```
public class Utils{
    // 检测以避免重复多次点击
    private static long lastClickTime;

    public static boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        long timeD = time - lastClickTime;
        if (0 < timeD && timeD < 500) {
            return true;
        }
        lastClickTime = time;
        return false;
    }
}
```

这样就能解决90%问题。

奈何我司QA测得又比较细，某天提了一个更让人心烦的问题：
某个页面上有多个按钮，点击button1的时候紧接着在500毫秒以内又点击了button2，这时候预期应该是button1和button2都应该得到响应。但是因为button1和button2的点击事件都加上了

```
if(Utils.isFastDoubleClick()){
    return;
}
```
的限制，由于全局都使用的同一个静态lastClickTime变量，就导致这两个控件之间的点击相互屏蔽了。如果是点击button1之后的500毫秒以内点击了button2将会导致button2点击事件中的`Utils.isFastDoubleClick()`这句代码为true而走了return。

那么解决的途径只能是不使用全局的lastClickTime这个变量来做上一次点击时间戳的判断，而是对每个控件都单独使用一个变量来记录。

那么最简单的做法就是实现一个自己的OnClickListener类，然后在每个控件设置点击事件的时候都使用这个类来屏蔽快速点击：

```
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

//使用时：
button.setOnClickListener(new FilterFastClickListener() {
    @Override
    public void onNoDoubleClick(View v) {

    }
});
```
这样基本就能解决同一页面之间多个控件快速点击的相互干扰问题。但是我又不喜欢这么做，因为这将导致每个控件都得单独设置一个新的`FilterFastClickListener`对象。这样即占用内存代码又不太优雅，我不爱。

所以就接着想，想过使用动态代理模式来实现AOP编程，但是明显这种方式写出来的代码看着更蠢，不仅要改变原有的代码写法而且上述的问题一个它也逃不脱。不过还是写出来玩玩：

```
View.OnClickListener onClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        doClickNoFilter();
    }
};

btnNoFilter.setOnClickListener((View.OnClickListener) Proxy.newProxyInstance(onClickListener.getClass().getClassLoader(), onClickListener.getClass().getInterfaces(), new InvocationHandler() {

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
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (isFastDoubleClick()) return null;
        return method.invoke(onClickListener, args);
    }
}));
```

最终其实还有很多种方案来实现，比如将系统源码中的`mOnClickListener`进行接管，但是又得考虑系统版本升级万一要是哪一天系统源码发生变化没有及时适配这可能会是个大雷就放弃了。

因为之前有了解过一些例如AspectJ，ASM，APT，javassist等相关知识，我觉得正好借着这个契机使用一下。

一开始考虑使用类似ButterKnife的实现方案，写个注解然后解析注解再进行动态注入代码，也可以搭配JavaPoet一起，但是由于我是要在原有函数内部实现代码注入而APT和javaPoet搭配一般都是用于生成新的类所以这种方式不合适。用AspectJ这种较为成熟的方案比较麻烦，所以最终我选择用Javassist来对class文件进行动态代码注入来实现功能。

我的思路时：

既然每个点击要互不干扰又能实现单个控件屏蔽快速点击，那就必须每个控件都有一个记录上次点击时间的变量，我选择把这个区分标准转移给方法。这样就能实现每个控件的点击调用一个单独方法，针对每个单独方法我生成一个唯一的类变量，各个控件调用对应方法也就互不影响了。但是生成的类变量要保证唯一，所以我创建了一个注解，给注解指定值来保证每个生成的类变量都可控。

```
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * value属性是标记方法的唯一标识，同一类中的value属性不可相同
 * 否则可能出现2个方法直接互相过滤点击
 * <p>
 * 注解保留时机必须持续到RUNTIME，因为javassist处理的就是class文件
 * 如果是SOURCE或CLASS时本注解已被去除所以会导致在解析类时找不到该注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FastClick {
    int value();
}
```
这样在javassist工作的时候我只需要扫描出所有类中的所有方法，如果方法中带有@FastClick这个注解我就拿注解的value值拼接生成对应该方法的类变量，并对该方法注入屏蔽快速点击的业务代码。

这样在要屏蔽快速点击时，只需给每个控件对应的点击方法加一个注解，在注解中指定一个在本类中唯一的标识值即可。这样在使用的时候十分方便。

使用示例：

```
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
```

代码奉上：

### 如果有朋友想接入，那么可以提供2种方式可接入：

假设您的主module名为app。

#### 使用方法1（黑盒方式）:
1. 拷贝annotations库至自己工程中，放在与app模块同级目录下。
2. 修改settings.gradle文件将这两个工程加入编译`include ':annotations'`
3. 拷贝javassist-jar文件夹至自己工程中，放在与annotations同级目录下。
4. 在工程->build.gradle文件中repositories{}中增加本地的javassist-jar路径为maven仓库路径（）`maven { url("./javassist-jar") }`
5. 在工程->build.gradle文件中dependencies{}增加`classpath "com.lianwenhong.clickfilter:modify:1.0.0"`依赖
6. 在app->build.gradle文件增加plugin: 'com.lianwenhong.clickfilter'代码
7. 然后Sync工程即可。此时可以直接在想要屏蔽的方法上增加@FastClick就可实现快速点击屏蔽

#### 使用方式2（白盒方式）:
1. 拷贝annotations库至自己工程中，放在与app模块同级目录下。
2. 拷贝lib库至自己工程中，放在与app模块同级目录下。
3. 修改settings.gradle文件将这两个工程加入编译`include ':lib' include ':annotations'`
4. 先执行lib工程的publishing这个task让它生成javassist-jar下的jar包
5. 然后执行方法1中的4.5.6.7步骤即可。
