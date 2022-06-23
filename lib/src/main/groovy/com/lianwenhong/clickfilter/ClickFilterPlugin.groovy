package com.lianwenhong.clickfilter

import org.gradle.api.Plugin
import org.gradle.api.Project

class ClickFilterPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println "开始注入快速点击过滤逻辑"
        project.android.registerTransform(new ClickFilterTransform(project))
    }

}