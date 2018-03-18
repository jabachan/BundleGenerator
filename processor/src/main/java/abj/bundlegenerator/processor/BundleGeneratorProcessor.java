package abj.bundlegenerator.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes({"abj.bundlegenerator.processor.BundleGenerator", "abj.bundlegenerator.processor.BundleSet"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class BundleGeneratorProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(BundleGenerator.class)) {
            if (element instanceof TypeElement) {
                createBundleGenerator((TypeElement) element);
            }
        }
        return true;
    }

    private void createBundleGenerator(TypeElement element) {

        try {
            final String classSimpleName = element.getSimpleName().toString();
            final String className = element.getQualifiedName().toString();
            final int index = className.lastIndexOf('.');
            String packageName = "";
            if (index >= 0) {
                packageName = className.substring(0, index);
            }
            List<UseBundleMethod> useMethods = new ArrayList<>();

            // method for create Bundle
            final MethodSpec methodBundle = createMethodBundle(element, packageName, classSimpleName, useMethods);

            // create wrapper class
            final TypeSpec innerBundleWrapperClass = createInnerWrapperClass(useMethods);

            // method for restore targetClass(return Bundle wrapper)
            final MethodSpec methodRestore = createMethodRestore(innerBundleWrapperClass);

            final TypeSpec typeSpec = TypeSpec.classBuilder(classSimpleName + "BundleGenerator")
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(methodBundle)
                    .addMethod(methodRestore)
                    .addType(innerBundleWrapperClass)
                    .build();

            JavaFile.builder(packageName, typeSpec)
                    .build()
                    .writeTo(this.processingEnv.getFiler());
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "createBundleGenerator error:" + e.getMessage());
        }
    }

    private static final Map<TypeKind, String> BUNDLE_METHOD_PRIMITIVE = new HashMap<>();

    static {
        BUNDLE_METHOD_PRIMITIVE.put(TypeKind.BOOLEAN, "Boolean");
        BUNDLE_METHOD_PRIMITIVE.put(TypeKind.BYTE, "Byte");
        BUNDLE_METHOD_PRIMITIVE.put(TypeKind.SHORT, "Short");
        BUNDLE_METHOD_PRIMITIVE.put(TypeKind.INT, "Int");
        BUNDLE_METHOD_PRIMITIVE.put(TypeKind.LONG, "Long");
        BUNDLE_METHOD_PRIMITIVE.put(TypeKind.FLOAT, "Float");
        BUNDLE_METHOD_PRIMITIVE.put(TypeKind.DOUBLE, "Double");
        BUNDLE_METHOD_PRIMITIVE.put(TypeKind.CHAR, "Char");
    }

    private static final Map<String, String> BUNDLE_METHOD_DECLARED = new HashMap<>();

    static {
        BUNDLE_METHOD_DECLARED.put("java.lang.String", "String");
        BUNDLE_METHOD_DECLARED.put("java.lang.String[]", "StringArray");
        BUNDLE_METHOD_DECLARED.put("java.util.ArrayList<java.lang.String>", "StringArrayList");
        BUNDLE_METHOD_DECLARED.put("java.lang.CharSequence", "CharSequence");
        BUNDLE_METHOD_DECLARED.put("java.lang.CharSequence[]", "CharSequenceArray");
        BUNDLE_METHOD_DECLARED.put("java.util.ArrayList<java.lang.CharSequence>", "CharSequenceArrayList");
        BUNDLE_METHOD_DECLARED.put("android.os.Parcelable", "Parcelable");
        BUNDLE_METHOD_DECLARED.put("android.os.Parcelable[]", "ParcelableArray");
        BUNDLE_METHOD_DECLARED.put("java.util.ArrayList<android.os.Parcelable>", "ParcelableArrayList");
        BUNDLE_METHOD_DECLARED.put("android.os.Bundle", "Bundle");
        BUNDLE_METHOD_DECLARED.put("java.io.Serializable", "Serializable");
        BUNDLE_METHOD_DECLARED.put("boolean[]", "BooleanArray");
        BUNDLE_METHOD_DECLARED.put("byte[]", "ByteArray");
        BUNDLE_METHOD_DECLARED.put("short[]", "ShortArray");
        BUNDLE_METHOD_DECLARED.put("int[]", "IntArray");
        BUNDLE_METHOD_DECLARED.put("long[]", "LongArray");
        BUNDLE_METHOD_DECLARED.put("float[]", "FloatArray");
        BUNDLE_METHOD_DECLARED.put("double[]", "DoubleArray");
        BUNDLE_METHOD_DECLARED.put("char[]", "CharArray");
        BUNDLE_METHOD_DECLARED.put("java.util.ArrayList<java.lang.Integer>", "IntegerArrayList");
    }

    private MethodSpec createMethodBundle(TypeElement classElement,
                                          String packageName,
                                          String classSimpleName,
                                          List<UseBundleMethod> useMethods) {

        final CodeBlock.Builder builder = CodeBlock.builder()
                .addStatement("final Bundle bundle = new Bundle()");

        for (Element element : classElement.getEnclosedElements()) {
            if (element.getAnnotation(BundleSet.class) == null
                    || element.getKind() != ElementKind.METHOD
                    || !(element instanceof ExecutableElement)) {
                continue;
            }

            final ExecutableElement methodElement = (ExecutableElement) element;
            final String methodName = methodElement.getSimpleName().toString();
            final TypeMirror returnType = methodElement.getReturnType();

            String bundleMethod = null;
            TypeName returnTypeName = null;
            if (returnType.getKind() == TypeKind.DECLARED || returnType.getKind() == TypeKind.ARRAY) {
                bundleMethod = BUNDLE_METHOD_DECLARED.get(returnType.toString());
                returnTypeName = TypeVariableName.get(returnType.toString());
            } else if (returnType.getKind().isPrimitive()) {
                bundleMethod = BUNDLE_METHOD_PRIMITIVE.get(returnType.getKind());
                returnTypeName = TypeName.get(returnType);
            }
            if (bundleMethod != null) {
                builder.addStatement(bundlePutStatement(bundleMethod, classSimpleName, methodName));

                // save for restore
                useMethods.add(
                        new UseBundleMethod(
                                methodName,
                                bundleMethod,
                                classSimpleName + "_" + methodName,
                                returnTypeName)
                );
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "This method can't read: " + classSimpleName + "#" + methodName);
            }

        }

        final CodeBlock codeBlock = builder.addStatement("return bundle").build();

        return MethodSpec.methodBuilder("bundle")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addAnnotation(classNameNonNull())
                .returns(classNameBundle())
                .addParameter(
                        ParameterSpec.builder(ClassName.get(packageName, classSimpleName), "target")
                                .addAnnotation(classNameNonNull())
                                .build())
                .addCode(codeBlock)
                .build();

    }

    /**
     * ex) bundle.putInt("Sample_getId", sample.getId())
     */
    private static String bundlePutStatement(String bundleMethod, String className, String methodName) {
        return "bundle.put" +
                bundleMethod +
                "(\"" +
                className +
                "_" +
                methodName +
                "\", target." +
                methodName +
                "())";
    }

    private static TypeSpec createInnerWrapperClass(List<UseBundleMethod> useMethods) {

        final TypeSpec.Builder builder = TypeSpec.classBuilder("Wrapper")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addField(
                        FieldSpec.builder(classNameBundle(), "mBundle")
                                .addModifiers(Modifier.FINAL)
                                .build())
                .addMethod(
                        MethodSpec.constructorBuilder()
                                .addParameter(
                                        ParameterSpec.builder(classNameBundle(), "bundle")
                                                .addAnnotation(classNameNonNull())
                                                .build())
                                .addCode(
                                        CodeBlock.builder()
                                                .addStatement("mBundle = bundle")
                                                .build())
                                .build());

        for (UseBundleMethod useBundleMethod : useMethods) {
            final String getMethodName = "get" + useBundleMethod.bundleMethod;
            final MethodSpec methodSpec = MethodSpec.methodBuilder(useBundleMethod.targetMethod)
                    .addModifiers(Modifier.PUBLIC)
                    .addCode(
                            CodeBlock.builder()
                                    .addStatement("return mBundle." + getMethodName + "(\"" + useBundleMethod.bundleKey + "\")")
                                    .build()
                    )
                    .returns(useBundleMethod.returnType)
                    .build();

            builder.addMethod(methodSpec);
        }

        return builder.build();
    }

    private static MethodSpec createMethodRestore(TypeSpec returns) {
        return MethodSpec.methodBuilder("restore")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(
                        ParameterSpec.builder(classNameBundle(), "bundle")
                                .addAnnotation(classNameNonNull())
                                .build())
                .addAnnotation(classNameNonNull())
                .returns(TypeVariableName.get(returns.name))
                .addCode(CodeBlock.builder()
                        .addStatement("return new " + returns.name + "(bundle)")
                        .build())
                .build();
    }

    private static ClassName classNameBundle() {
        return ClassName.get("android.os", "Bundle");
    }

    private static ClassName classNameNonNull() {
        return ClassName.get("android.support.annotation", "NonNull");
    }

    private static class UseBundleMethod {
        final String targetMethod;
        final String bundleMethod;
        final String bundleKey;
        final TypeName returnType;

        private UseBundleMethod(String targetMethod, String bundleMethod, String bundleKey, TypeName returnType) {
            this.targetMethod = targetMethod;
            this.bundleMethod = bundleMethod;
            this.bundleKey = bundleKey;
            this.returnType = returnType;
        }
    }
}
