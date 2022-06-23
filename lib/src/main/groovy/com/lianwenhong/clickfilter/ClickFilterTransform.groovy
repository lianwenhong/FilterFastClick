package com.lianwenhong.clickfilter

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import javassist.ClassPool
import javassist.CtClass
import javassist.CtField
import javassist.CtMethod
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.MethodInfo
import javassist.bytecode.analysis.Type
import javassist.bytecode.annotation.Annotation
import javassist.bytecode.annotation.IntegerMemberValue
import org.gradle.api.Project

class ClickFilterTransform extends Transform {

    def project
    // 缓存字节码对象CtClass的容器
    def pool = ClassPool.default

    ClickFilterTransform(Project project) {
        this.project = project
    }

    @Override
    String getName() {
        return "click_filter_inject"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) {
        super.transform(transformInvocation)
        println "========开始处理字节码文件========"

        // 向缓存中加入android.jar，不然找不到android相关的所有类
        project.android.bootClasspath.each {
            pool.appendClassPath(it.absolutePath)
        }

        // 遍历项目中的所有输入文件
        transformInvocation.inputs.each {
            // 遍历jar文件
            it.jarInputs.each {
                // 将该路径下的所有class都加入缓存中
                pool.insertClassPath(it.file.absolutePath)
                // 获取jar文件的输出目录
                def dest = transformInvocation.outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes, Format.JAR)
                // jar文件中的class不处理，直接拷贝到下一个transform中
                FileUtils.copyFile(it.file, dest)
            }
            // 遍历项目中所有的输入目录
            it.directoryInputs.each {
                def preFileName = it.file.absolutePath
                // 将该路径下的所有class都加入缓存中
                pool.insertClassPath(preFileName)
                // 将某个包加入缓存，例如这里单独将android.os.Bundle包加入缓存（只是为了演示，因为这个包已经在android.jar中了，所以并不需要单独加入）
                // pool.importPackage("android.os.Bundle")
                findTarget(it.file, preFileName)
                // 获取文件夹的输出目录
                def dest = transformInvocation.outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes, Format.DIRECTORY)
                // 拷贝文件夹给下一个Transform任务
                FileUtils.copyDirectory(it.file, dest)
            }
        }
    }

    /**
     * 遍历并修改文件夹下的所有class文件
     * @param file
     */
    void findTarget(File file, String filePath) {
        if (file.isDirectory()) {
            file.listFiles().each {
                findTarget(it, filePath)
            }
        } else {
            modify(file, filePath)
        }
    }

    /**
     * 动态修改class文件
     * @param file
     * @param filePath
     */
    void modify(File file, String filePath) {

        def fileName = file.absolutePath
        if (!fileName.endsWith(SdkConstants.DOT_CLASS)) {
            return
        }
        if (fileName.contains('R$') || fileName.contains('R.class') || fileName.contains('BuildConfig.class')) {
            return
        }
        if (fileName.contains('com.lianwenhong.annotation.FastClick.class')) {
            return
        }
        // 获得全类名 key -> 字节码 ctClass(字节码文件在内存中的对象表现) -> 修改
        // 从/Users/lianwenhong/AndroidStudioProjects/demos/JavassistDemo/app/build/intermediates/javac/release/classes/com/lianwenhong/javassistdemo/MainActivity.class
        // 中获取
        // com.lianwenhong.javassistdemo.MainActivity.class全类名
        def clzName = fileName.replace(filePath, "").replace(File.separator, ".")
        def name = clzName.replace(SdkConstants.DOT_CLASS, "").substring(1)
        println " >>> 类名:" + name

        CtClass ctClass = pool.get(name)

        //获取类中的方法
        CtMethod[] methods = ctClass.getDeclaredMethods()
        for (CtMethod method : methods) {
            println " >>> method:" + method
            // 类被虚拟机加载过后就会被冻结，所以需要将其解冻
            if (ctClass.isFrozen()) {
                ctClass.defrost()
            }
            // 获取方法信息
            MethodInfo methodInfo = method.getMethodInfo()
            if (methodInfo != null) {
                // 获取注解属性
                AnnotationsAttribute attribute = (AnnotationsAttribute) methodInfo.getAttribute(AnnotationsAttribute.visibleTag)
                if (attribute != null) {
                    println " >>> attribute:" + attribute
                    // 获取注解
                    Annotation annotation = attribute.getAnnotation("com.lianwenhong.annotation.FastClick")
                    if (annotation != null) {
                        // 获取注解的值
                        int id = ((IntegerMemberValue) annotation.getMemberValue("value")).getValue()
                        addField(ctClass, id)
                        addStatement(id, ctClass, method, filePath)
                    }
                }
            }
        }
        ctClass.detach()
    }

    /**
     * 增加类属性
     * @param ctClass
     * @param id
     */
    void addField(CtClass ctClass, int id) {
        println " >>> 执行addField()"

        // 创建属性方式一
//        CtField ctField = new CtField(CtClass.longType, "lastClickTime" + id, ctClass);
//        ctField.setModifiers(Modifier.PRIVATE);

        // 创建属性方式二
        CtField ctField = CtField.make("private long lastClickTime" + id + " = 0L;", ctClass)

        // 添加属性
        ctClass.addField(ctField)
    }

    /**
     * 增加代码块
     * @param id
     * @param ctClass
     * @param method
     * @param filePath
     */
    void addStatement(int id, CtClass ctClass, CtMethod method, String filePath) {
        println " >>> method:" + method.name + " returnType:" + method.returnType
        if (method == null) return
        def body
        switch (method.returnType) {
            case CtClass.voidType:
                body = "long time = System.currentTimeMillis();\n" +
                        "        long timeD = time - lastClickTime" + id + ";\n" +
                        "        if (0 < timeD && timeD < 500) {\n" +
                        "            return ;\n" +
                        "        }\n" +
                        "        lastClickTime" + id + " = time;\n"
                break
            case CtClass.byteType:
            case CtClass.charType:
            case CtClass.shortType:
            case CtClass.intType:
            case CtClass.longType:
            case CtClass.floatType:
            case CtClass.doubleType:
                body = "long time = System.currentTimeMillis();\n" +
                        "        long timeD = time - lastClickTime" + id + ";\n" +
                        "        if (0 < timeD && timeD < 500) {\n" +
                        "            return 0;\n" +
                        "        }\n" +
                        "        lastClickTime" + id + " = time;\n"
                break;
            case CtClass.booleanType:
                body = "long time = System.currentTimeMillis();\n" +
                        "        long timeD = time - lastClickTime" + id + ";\n" +
                        "        if (0 < timeD && timeD < 500) {\n" +
                        "            return false;\n" +
                        "        }\n" +
                        "        lastClickTime" + id + " = time;\n"
                break
            default:
                body = "long time = System.currentTimeMillis();\n" +
                        "        long timeD = time - lastClickTime" + id + ";\n" +
                        "        if (0 < timeD && timeD < 500) {\n" +
                        "            return null;\n" +
                        "        }\n" +
                        "        lastClickTime" + id + " = time;\n"
                break
        }
        addCode(ctClass, method, body, filePath)
    }

    /**
     * 注入动态添加的代码
     * @param ctClass
     * @param body
     * @param fileName
     */
    void addCode(CtClass ctClass, CtMethod method, String body, String fileName) {
        method.insertBefore(body)
        ctClass.writeFile(fileName)
    }
}
