/*
 *    Copyright (c) 2022.  lWoHvYe(Hongyan Wang)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.gradle.transform.javamodules;

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

/**
 * An artifact transform that applies additional information to Jars without module information.
 * The transformation fails the build if a Jar does not contain information and no extra information
 * was defined for it. This way we make sure that all Jars are turned into modules.
 */
public abstract class ExtraModuleInfoTransform implements TransformAction<ExtraModuleInfoTransform.Parameter> {

    public static class Parameter implements TransformParameters, Serializable {
        private Map<String, ModuleInfo> moduleInfo = Collections.emptyMap();
        private Map<String, String> automaticModules = Collections.emptyMap();

        @Input
        public Map<String, ModuleInfo> getModuleInfo() {
            return moduleInfo;
        }

        @Input
        public Map<String, String> getAutomaticModules() {
            return automaticModules;
        }

        public void setModuleInfo(Map<String, ModuleInfo> moduleInfo) {
            this.moduleInfo = moduleInfo;
        }

        public void setAutomaticModules(Map<String, String> automaticModules) {
            this.automaticModules = automaticModules;
        }
    }

    @InputArtifact
    protected abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var moduleInfo = getParameters().moduleInfo;
        var automaticModules = getParameters().automaticModules;
        var originalJar = getInputArtifact().get().getAsFile();
        var originalJarName = originalJar.getName();

        if (isModule(originalJar)) {
            // 已经有module-info.java了，原样输出
            outputs.file(originalJar);
        } else if (moduleInfo.containsKey(originalJarName)) {
            // 没有module-info.java，但在配置中，通过module()配置的，添加module-info.java文件
            // moduleInfo是所有通过module()配置的配置，是个map (key: jarName, value: 相关配置)
            addModuleDescriptor(originalJar, getModuleJar(outputs, originalJar), moduleInfo.get(originalJarName));
        } else if (isAutoModule(originalJar)) {
            // 是AutoModule也原样输出
            outputs.file(originalJar);
        } else if (automaticModules.containsKey(originalJarName)) {
            // 不是AutoModule，添加Automatic-Module-Name到MANIFEST.MF .这里只是add-field
            addAutomaticModuleName(originalJar, getModuleJar(outputs, originalJar), automaticModules.get(originalJarName));
        } else {
            // 其他的都原样输出
            outputs.file(originalJar); // ignored no-module

            // throw new RuntimeException("Not a module and no mapping defined: " + originalJarName);
        }
    }

    private boolean isModule(File jar) {
        var moduleInfoClassMrjarPath = Pattern.compile("META-INF/versions/\\d+/module-info.class");
        try (var inputStream = new JarInputStream(new FileInputStream(jar))) {
            var isMultiReleaseJar = containsMultiReleaseJarEntry(inputStream);
            var next = inputStream.getNextEntry();
            while (next != null) {
                if ("module-info.class".equals(next.getName())) {
                    return true;
                }
                if (isMultiReleaseJar && moduleInfoClassMrjarPath.matcher(next.getName()).matches()) {
                    return true;
                }
                next = inputStream.getNextEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private boolean containsMultiReleaseJarEntry(JarInputStream jarStream) {
        var manifest = jarStream.getManifest();
        return manifest != null && Boolean.parseBoolean(manifest.getMainAttributes().getValue("Multi-Release"));
    }

    private boolean isAutoModule(File jar) {
        try (var inputStream = new JarInputStream(new FileInputStream(jar))) {
            return inputStream.getManifest().getMainAttributes().getValue("Automatic-Module-Name") != null;
        } catch (IOException | NullPointerException e) {
            return false; // regard as noAutoModule when focus error
            // throw new RuntimeException(e);
        }
    }

    private File getModuleJar(TransformOutputs outputs, File originalJar) {
        return outputs.file(originalJar.getName().substring(0, originalJar.getName().lastIndexOf('.')) + "-module.jar");
    }

    private static void addAutomaticModuleName(File originalJar, File moduleJar, String moduleName) {
        try (var inputStream = new JarInputStream(new FileInputStream(originalJar))) {
            var manifest = inputStream.getManifest();
            manifest.getMainAttributes().put(new Attributes.Name("Automatic-Module-Name"), moduleName);
            try (var outputStream = new JarOutputStream(new FileOutputStream(moduleJar), inputStream.getManifest())) {
                copyEntries(inputStream, outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addModuleDescriptor(File originalJar, File moduleJar, ModuleInfo moduleInfo) {
        try (var inputStream = new JarInputStream(new FileInputStream(originalJar))) {
            try (var outputStream = new JarOutputStream(new FileOutputStream(moduleJar), inputStream.getManifest())) {
                copyEntries(inputStream, outputStream);
                outputStream.putNextEntry(new JarEntry("module-info.class"));
                outputStream.write(addModuleInfo(moduleInfo));
                outputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyEntries(JarInputStream inputStream, JarOutputStream outputStream) throws IOException {
        var jarEntry = inputStream.getNextJarEntry();
        while (jarEntry != null) {
            outputStream.putNextEntry(jarEntry);
            outputStream.write(inputStream.readAllBytes());
            outputStream.closeEntry();
            jarEntry = inputStream.getNextJarEntry();
        }
    }

    private static byte[] addModuleInfo(ModuleInfo moduleInfo) {

        var classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null);
        var moduleVisitor = classWriter.visitModule(moduleInfo.getModuleName(), Opcodes.ACC_OPEN, moduleInfo.getModuleVersion());
        for (var export : moduleInfo.getExports()) {
            moduleVisitor.visitExport(getInternalName(export.t1), 0, export.t2);
        }
        moduleVisitor.visitRequire("java.base", 0, null);
        for (var requireName : moduleInfo.getRequires()) {
            moduleVisitor.visitRequire(requireName, 0, null);
        }
        for (var requireName : moduleInfo.getRequiresTransitive()) {
            moduleVisitor.visitRequire(requireName, Opcodes.ACC_TRANSITIVE, null);
        }
        for (var open : moduleInfo.getOpens()) {
            moduleVisitor.visitOpen(getInternalName(open.t1), 0, open.t2);
        }
        for (var use : moduleInfo.getUses()) {
            moduleVisitor.visitUse(getInternalName(use));
        }
        for (var provide : moduleInfo.getProvides()) {
            var with = provide.t2;
            if (Objects.nonNull(with))
                with = Arrays.stream(with).map(ExtraModuleInfoTransform::getInternalName).toArray(String[]::new);
            moduleVisitor.visitProvide(getInternalName(provide.t1), with);
        }
        moduleVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    static String getInternalName(String originalName) {
        return originalName.replace('.', '/');
    }
}
