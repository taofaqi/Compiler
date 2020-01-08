package com.ttp.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.ttp.annotation.Aspect;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * @author faqi.tao
 * @time 2019/12/26
 * <p>
 * https://blog.csdn.net/crazy1235/article/details/51876192     JavaPoet的基本使用
 * <p>
 * https://blog.csdn.net/IO_Field/article/details/89355941      JavaPoet使用详解
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({"com.ttp.annotation.ARouter"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions("content")
public class ApiProcessorJavaPot extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Messager messager;
    private Filer filer;

    private String ttpcHttpApiProxyName = "TtpcHttpApiProxy";
    private String httpApiManager = "HttpApiManager";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();

        String content = processingEnv.getOptions().get("content");
        messager.printMessage(Diagnostic.Kind.NOTE, content);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) return false;
        //获取所有被ARouter注解的类节点
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Aspect.class);
        if (elements.isEmpty()) return false;

        //构建方法
        MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build();
        List<ClassName> classNameList = new ArrayList<>();
        List<MethodSpec> methodSpecList = new ArrayList<>();
        List<TypeName> typeNames = new ArrayList<>();

        for (Element element : elements) {
            TypeMirror typeMirror = element.asType();

            messager.printMessage(Diagnostic.Kind.NOTE, "typeMirror.toString()：=======" + typeMirror.toString());

            typeNames.add(TypeVariableName.get(typeMirror));

            String packageName = elementUtils.getPackageOf(element).getQualifiedName().toString();  //获取包节点
            String className = element.getSimpleName().toString();
            classNameList.add(ClassName.get(packageName, className));

            messager.printMessage(Diagnostic.Kind.NOTE, "Aspect注解的类有：=======" + packageName + "." + className);

            if (element.getKind() == ElementKind.INTERFACE) {
                messager.printMessage(Diagnostic.Kind.NOTE, "注解的类----：" + className);
                List<? extends Element> methodElements = element.getEnclosedElements();//获取所有方法

                MethodSpec methodSpec;
                for (Element e : methodElements) {
                    if (e instanceof ExecutableElement) {
                        ExecutableElement methodElement = (ExecutableElement) e;
                        List<? extends VariableElement> parameters = methodElement.getParameters();  //方法参数

                        String returnType = methodElement.getReturnType().toString();  //返回值 com.ttp.http.HttpTask<java.util.List<com.ttp.apt_project.bean.ListProjectResult>>
                        messager.printMessage(Diagnostic.Kind.NOTE, "方法的返回参数----：" + returnType);

                        List<ParameterSpec> parameterList = new ArrayList<>();   //方法参数集合
                        ParameterSpec parameterSpec;

                        StringBuilder parameterName = new StringBuilder();
                        for (int i = 0; i < parameters.size(); i++) {
                            VariableElement parameter = parameters.get(i);
                            String name = parameter.getSimpleName().toString();

                            parameterSpec = ParameterSpec.builder(parameter.getKind().toString().getClass(), name).build();
                            parameterName.append(name);
                            if (i < parameters.size() - 1) { //参数拼接
                                parameterName.append(" , ");
                            }
                            parameterList.add(parameterSpec);
                        }
                        String methodName = methodElement.getSimpleName().toString();

                        methodSpec = MethodSpec.methodBuilder(methodName)
                                .addModifiers(Modifier.PUBLIC)
                                .addParameters(parameterList)
                                .returns(ClassName.get("", returnType))
                                .addStatement("return $T.getHttpService($N.class)." + methodName + "($N)",
                                        ClassName.get("com.ttp.http", "HttpManager"), className, parameterName)
                                .build();
                        methodSpecList.add(methodSpec);
                    }
                }
            }
        }

        TypeName[] typeNameArray = new TypeName[2];
        TypeName[] toArray = typeNames.toArray(typeNameArray);

        TypeVariableName typeVariableName = TypeVariableName.get("T", toArray);

        //构建类
        TypeSpec typeSpec = TypeSpec.classBuilder(ttpcHttpApiProxyName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(constructor)
                .addMethods(methodSpecList)
                .addSuperinterfaces(classNameList)
                .build();

        JavaFile javaFile = JavaFile.builder("com.ttp.httpCore", typeSpec)
                .build();

        FieldSpec sInstance = FieldSpec.builder(ClassName.get("", ttpcHttpApiProxyName), "sInstance", Modifier.STATIC, Modifier.PRIVATE).build();

        MethodSpec constructorMethod = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build();


        MethodSpec getService = MethodSpec.methodBuilder("getService")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                //添加泛型信息
                .addTypeVariable(typeVariableName)
                .returns(ClassName.get("", "T"))
                .addCode("if (sInstance == null) {\n" +
                        "    synchronized (HttpApiManager.class) {\n" +
                        "        if (sInstance == null) {\n" +
                        "            sInstance = new $T();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n" +
                        "return (T) sInstance;", ClassName.get("", "TtpcHttpApiProxy"))
                .build();
        TypeSpec typeSpec1 = TypeSpec.classBuilder(httpApiManager)
                .addField(sInstance)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(constructorMethod)
                .addMethod(getService)
                .build();

        JavaFile javaFile1 = JavaFile.builder("com.ttp.httpCore", typeSpec1)
                .build();

        try {
            javaFile.writeTo(filer);
            javaFile1.writeTo(filer);

        } catch (IOException e) {
            e.printStackTrace();

        }
        return true;
    }
}