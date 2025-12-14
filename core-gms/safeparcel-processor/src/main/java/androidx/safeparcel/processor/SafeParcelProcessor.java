/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.safeparcel.processor;

import static java.lang.Math.max;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import androidx.annotation.RequiresApi;

import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.clearsilver.jsilver.JSilver;
import com.google.clearsilver.jsilver.JSilverOptions;
import com.google.clearsilver.jsilver.autoescape.EscapeMode;
import com.google.clearsilver.jsilver.data.Data;
import com.google.clearsilver.jsilver.resourceloader.ClassLoaderResourceLoader;
import com.google.common.base.Joiner;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Annotation Processor for {@link SafeParcelable}.
 *
 * @hide
 */
@SupportedAnnotationTypes({SafeParcelProcessor.CLASS_ANNOTATION_NAME})
public class SafeParcelProcessor extends AbstractProcessor {
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    public static final String SAFE_PARCELABLE_NAME =
            "com.google.android.gms.common.internal.safeparcel.SafeParcelable";
    public static final String REFLECTED_PARCELABLE_NAME =
            "com.google.android.gms.common.internal.safeparcel.ReflectedParcelable";
    public static final String CLASS_ANNOTATION_NAME = SAFE_PARCELABLE_NAME + ".Class";
    public static final String FIELD_ANNOTATION_NAME = SAFE_PARCELABLE_NAME + ".Field";
    public static final String LOCAL_VARIABLE_PREFIX = "_local_safe_0a1b_";

    public static final int INDICATOR_FIELD_ID = -1;

    Elements mElements;
    Types mTypes;
    Messager mMessager;

    TypeMirror mStringType;
    TypeMirror mListType;
    TypeMirror mSetType;
    TypeMirror mHashSetType;
    TypeMirror mArrayListType;
    TypeMirror mBigIntegerType;
    TypeMirror mBigDecimalType;
    TypeMirror mBooleanType;
    TypeMirror mIntegerType;
    TypeMirror mLongType;
    TypeMirror mFloatType;
    TypeMirror mDoubleType;
    TypeMirror mCharacterType;
    TypeMirror mByteType;
    TypeMirror mShortType;

    TypeMirror mParcelableType;
    TypeMirror mParcelableCreatorType;
    TypeMirror mIBinderType;
    TypeMirror mBundleType;
    TypeMirror mParcelType;
    TypeMirror mSparseArrayType;
    TypeMirror mSparseBooleanArrayType;
    TypeMirror mSparseIntArrayType;
    TypeMirror mSparseLongArrayType;

    TypeMirror mSafeParcelableType;
    TypeMirror mReflectedParcelableType;

    static class ParcelableField {
        VariableElement mVariable;
        int mId;
        String mName;
        String mReadName;
        TypeMirror mType;
        String mGetter;
        SerializationMethods mSm;
        String mDefaultValue;

        ParcelableField(VariableElement variable) {
            this.mVariable = variable;
        }
    }

    static class ParcelableConstructor {
        ArrayList<ParcelableField> mParameters = new ArrayList<>();
    }

    class ParcelableClass {
        TypeElement          mParcelableClass;
        SafeParcelable.Class mAnnotation;
        String               mQualifiedName;
        String mGeneratedClassName;
        HashMap<Integer, ParcelableField> mFields = new HashMap<>();
        ParcelableConstructor mConstructor;
        ParcelableField mIndicatorField;
        Integer mRequiresApi = null;

        ParcelableClass(TypeElement parcelableClass) {
            this.mParcelableClass = parcelableClass;
            this.mQualifiedName = parcelableClass.getQualifiedName().toString();
            this.mAnnotation = parcelableClass.getAnnotation(SafeParcelable.Class.class);
            RequiresApi requiresApiAnnotation = parcelableClass.getAnnotation(RequiresApi.class);
            if (requiresApiAnnotation != null) {
                mRequiresApi = max(requiresApiAnnotation.value(), requiresApiAnnotation.api());
            }

            PackageElement parcelablePackage = mElements.getPackageOf(parcelableClass);
            this.mGeneratedClassName =
                    parcelablePackage.getQualifiedName() + "." + this.mAnnotation.creator();
        }
    }

    static class SerializationMethods {
        String mWrite;
        boolean mWriteWithFlags;
        boolean mIsAssignment;
        String mRead;
        String mCreator;
        boolean mHasWriteNull;

        SerializationMethods(
                String write,
                boolean writeWithFlags,
                boolean isAssignment,
                String read,
                String creator,
                Boolean hasWriteNull) {
            this.mWrite = write;
            this.mWriteWithFlags = writeWithFlags;
            this.mIsAssignment = isAssignment;
            this.mRead = read;
            this.mCreator = creator;
            this.mHasWriteNull = hasWriteNull;
        }
    }

    HashMap<String, ParcelableClass> mParcelableClasses;

    public SafeParcelProcessor() {}

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mElements = processingEnv.getElementUtils();
        mTypes = processingEnv.getTypeUtils();
        mMessager = processingEnv.getMessager();

        // Built in java ones
        mStringType = mElements.getTypeElement("java.lang.String").asType();
        mListType = mElements.getTypeElement("java.util.List").asType();
        mArrayListType = mElements.getTypeElement("java.util.ArrayList").asType();
        mSetType = mElements.getTypeElement("java.util.Set").asType();
        mHashSetType = mElements.getTypeElement("java.util.HashSet").asType();
        mBigIntegerType = mElements.getTypeElement("java.math.BigInteger").asType();
        mBigDecimalType = mElements.getTypeElement("java.math.BigDecimal").asType();
        mBooleanType = mElements.getTypeElement("java.lang.Boolean").asType();
        mIntegerType = mElements.getTypeElement("java.lang.Integer").asType();
        mLongType = mElements.getTypeElement("java.lang.Long").asType();
        mFloatType = mElements.getTypeElement("java.lang.Float").asType();
        mDoubleType = mElements.getTypeElement("java.lang.Double").asType();
        mByteType = mElements.getTypeElement("java.lang.Byte").asType();
        mShortType = mElements.getTypeElement("java.lang.Short").asType();
        mCharacterType = mElements.getTypeElement("java.lang.Character").asType();

        // Android classes
        mParcelableType = loadTypeOrFail("android.os.Parcelable");
        mParcelableCreatorType = loadTypeOrFail("android.os.Parcelable.Creator");
        mIBinderType = loadTypeOrFail("android.os.IBinder");
        mBundleType = loadTypeOrFail("android.os.Bundle");
        mParcelType = loadTypeOrFail("android.os.Parcel");
        mSparseArrayType = loadTypeOrFail("android.util.SparseArray");
        mSparseBooleanArrayType = loadTypeOrFail("android.util.SparseBooleanArray");
        mSparseIntArrayType = loadTypeOrFail("android.util.SparseIntArray");
        mSparseLongArrayType = loadTypeOrFail("android.util.SparseLongArray");
        mSafeParcelableType = loadTypeOrFail(SAFE_PARCELABLE_NAME);
        mReflectedParcelableType = loadTypeOrFail(REFLECTED_PARCELABLE_NAME);
    }

    private TypeMirror loadTypeOrFail(String qualified) {
        Element e = mElements.getTypeElement(qualified);
        if (e == null) {

            // Check if the string refers to a primitive type or an array of a primitive type
            final boolean isArray;
            final String typeName;
            if (qualified.endsWith("[]")) {
                isArray = true;
                typeName = qualified.substring(0, qualified.length() - 2).toUpperCase();
            } else {
                isArray = false;
                typeName = qualified.toUpperCase();
            }

            // Find the TypeKind of the typeName
            TypeKind primitiveKind = null;
            try {
                primitiveKind = TypeKind.valueOf(typeName);
            } catch (IllegalArgumentException ignored) {
                // continue to error reporting below
            }
            if (primitiveKind != null && primitiveKind.isPrimitive()) {
                TypeMirror type = mTypes.getPrimitiveType(primitiveKind);
                if (isArray) {
                    return mTypes.getArrayType(type);
                }
                return type;
            }
            throw new IllegalArgumentException("Can't find class " + qualified + " in classpath.");
        }
        return e.asType();
    }

    @Override
    public boolean process(Set<? extends TypeElement> elements, RoundEnvironment env) {
        mParcelableClasses = new HashMap<>();

        // Gather all the parcelable classes.
        for (TypeElement e : elements) {
            if (e.getQualifiedName().contentEquals(CLASS_ANNOTATION_NAME)) {
                Set<? extends Element> annotatedElements = env.getElementsAnnotatedWith(e);
                for (Element annotatedElement : annotatedElements) {
                    // Workaround for open-jdk bug https://bugs.openjdk.java.net/browse/JDK-8030049:
                    // we need to explicitly check that the annotated type is a TypeElement and is
                    // annotated with SafeParcelable.Class.class
                    if (annotatedElement instanceof TypeElement
                            && annotatedElement.getAnnotation(SafeParcelable.Class.class) != null) {
                        processParcelableClass((TypeElement) annotatedElement);
                    } else {
                        mMessager.printMessage(
                                Diagnostic.Kind.WARNING,
                                "Undefined annotation used on "
                                        + annotatedElement.getSimpleName()
                                        + "\nA non-class type "
                                        + annotatedElement.asType().getKind()
                                        + " is returned by RoundEnvironment"
                                        + ".getElementsAnnotatedWith.");
                    }
                }
            }
        }

        // Generate the code
        for (ParcelableClass cl : mParcelableClasses.values()) {
            generateParser(cl);
        }
        return false;
    }

    private boolean processParcelableClass(TypeElement parcelableClass) {
        ParcelableClass cl = new ParcelableClass(parcelableClass);
        boolean isOkay = true;

        // Check that the class implements SafeParcelable
        if (!mTypes.isAssignable(parcelableClass.asType(), mSafeParcelableType)) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Class tagged @SafeParcelable.Class does not implement SafeParcelable",
                    parcelableClass);
            isOkay = false;
        }

        // Check that if the annotation has validate=true the class has a validateContents() method.
        if (cl.mAnnotation.validate()) {
            if (!hasValidateContents(parcelableClass)) {
                mMessager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Class tagged @SafeParcelable.Class has validate=true but no"
                                + " void validateContents() method.",
                        parcelableClass);
                isOkay = false;
            }
        }

        // Check that the class has a CREATOR method.
        if (!hasCreator(parcelableClass, cl.mGeneratedClassName)) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Class tagged @SafeParcelable.Class must have a public static final "
                            + cl.mGeneratedClassName
                            + " CREATOR field.",
                    parcelableClass);
            isOkay = false;
        }

        // If this is an inner class, it must be static.
        if (parcelableClass.getEnclosingElement().getKind() == ElementKind.CLASS) {
            Set<Modifier> modifiers = parcelableClass.getModifiers();
            if (!modifiers.contains(Modifier.STATIC)) {
                mMessager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Inner class tagged @SafeParcelable.Class must be declared static.",
                        parcelableClass);
                isOkay = false;
            }
        }

        // Check that the class is public or package private
        final Set<Modifier> modifiers = parcelableClass.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "SafeParcelable class must be public or package private.",
                    parcelableClass);
            isOkay = false;
        }

        // Enforce final on the SafeParcelable class
        // TODO: Uncomment this after finalizing this constraint with all teams.
        // if (!modifiers.contains(Modifier.FINAL)) {
        //    mMessager.printMessage(Diagnostic.Kind.ERROR, "SafeParcelable class must be final.",
        //            parcelableClass);
        // }

        // Check whether there is exactly one constructor annotated with @Constructor for the
        // SafeParcelable.  This check needs to happen before parsing all of the fields.
        final ExecutableElement constructor = findAnnotatedConstructor(parcelableClass);
        if (constructor == null) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "SafeParcelable class must have exactly"
                            + " one constructor annotated with @Constructor, which is used to "
                            + "construct"
                            + " this SafeParcelable implementing object from a Parcel.");
            isOkay = false;
        }

        // Get ids that have been marked as reserved
        Set<Integer> reservedIds = getReservedIds(parcelableClass);

        // Get all annotated fields for serialization/deserialization
        boolean versionFieldFound = false;
        boolean indicatorFieldFound = false;
        List<VariableElement> fields =
                ElementFilter.fieldsIn(mElements.getAllMembers(parcelableClass));
        // usedIds tracks which id's have been used to prevent duplicates
        Set<Integer> usedIds = new HashSet<>();
        for (VariableElement field : fields) {
            SafeParcelable.Field fieldInfo = field.getAnnotation(SafeParcelable.Field.class);
            SafeParcelable.VersionField versionFieldInfo =
                    field.getAnnotation(SafeParcelable.VersionField.class);
            SafeParcelable.Indicator indicatorInfo =
                    field.getAnnotation(SafeParcelable.Indicator.class);
            checkAtMostOneFieldAnnotation(
                    field, fieldInfo != null, versionFieldInfo != null, indicatorInfo != null);

            if (fieldInfo != null && versionFieldInfo != null) {
                // This field has both a Field and a VersionField annotation.  This is illegal.
                mMessager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "A member cannot be annotated with both Field and a VersionField.",
                        field);
                isOkay = false;
            } else if (fieldInfo != null) {
                // This is a regular field.
                if (usedIds.contains(fieldInfo.id())) {
                    // Found a duplicate field id
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Found a duplicate field id=" + fieldInfo.id() + ".",
                            field);
                    isOkay = false;
                } else if (reservedIds.contains(fieldInfo.id())) {
                    // Found a field with a reserved id
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Found a reserved field id=" + fieldInfo.id() + ".",
                            field);
                    isOkay = false;
                } else if (cl.mAnnotation.doNotParcelTypeDefaultValues()
                        && !fieldInfo.defaultValue().equals(SafeParcelable.NULL)) {
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "You cannot set defaultValue on "
                                    + fieldInfo.id()
                                    + " when doNotParcelTypeDefaultValues is true.",
                            field);
                } else {
                    usedIds.add(fieldInfo.id());
                    parseField(
                            cl,
                            field,
                            fieldInfo.id(),
                            fieldInfo.getter(),
                            fieldInfo.type(),
                            fieldInfo.defaultValue(),
                            fieldInfo.defaultValueUnchecked(),
                            false);
                }
            } else if (versionFieldInfo != null) {
                // This is the version field.
                // Ensure that a version field was not already specified.
                if (versionFieldFound) {
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "More than one field has been"
                                    + " annotated with VersionField.  One and only one member may"
                                    + " be"
                                    + " annotated with VersionField.",
                            field);
                    isOkay = false;
                }
                if (usedIds.contains(versionFieldInfo.id())) {
                    // Found a duplicate field id
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Found a duplicate field id=" + versionFieldInfo.id() + ".",
                            field);
                    isOkay = false;
                } else if (reservedIds.contains(versionFieldInfo.id())) {
                    // Found a field with a reserved id
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Found a reserved field id=" + versionFieldInfo.id() + ".",
                            field);
                    isOkay = false;
                } else {
                    usedIds.add(versionFieldInfo.id());
                    parseField(
                            cl,
                            field,
                            versionFieldInfo.id(),
                            versionFieldInfo.getter(),
                            versionFieldInfo.type(),
                            null,
                            null,
                            true);
                }
                versionFieldFound = true;
            } else if (indicatorInfo != null) {
                // Ensure that there is only one field that has been annotated with @Indicator
                if (indicatorFieldFound) {
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "More than one field has been"
                                    + " annotated with Indicator.  Only one member may be "
                                    + "annotated with"
                                    + " Indicator.",
                            field);
                    isOkay = false;
                }
                indicatorFieldFound = true;
                isOkay &= parseIndicatorField(cl, field, indicatorInfo.getter());
            }
        }

        // Process the constructor parameters.
        // Note: the following must be executed after all annotated fields have been processed.
        // If the constructor is null, then an error message has already been printed out, so no
        // need to print another message.
        if (constructor != null) {
            isOkay &= processParcelableConstructor(cl, constructor, reservedIds);
        }

        if (isOkay) {
            mParcelableClasses.put(cl.mGeneratedClassName, cl);
        } else {
            mMessager.printMessage(
                    Diagnostic.Kind.NOTE,
                    "Errors prevented the SafeParcelable "
                            + parcelableClass.getQualifiedName()
                            + " from being processed. Look above this line in the log to find the"
                            + " error.");
        }

        return isOkay;
    }

    /** A field can be annotated with one of Field, VersionField, Indicator, or nothing. */
    private void checkAtMostOneFieldAnnotation(
            VariableElement field,
            boolean fieldAnnotation,
            boolean versionFieldAnnotation,
            boolean indicatorAnnotation) {
        int numFieldAnnotations =
                (fieldAnnotation ? 1 : 0)
                        + (versionFieldAnnotation ? 1 : 0)
                        + (indicatorAnnotation ? 1 : 0);
        if (numFieldAnnotations > 1) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Field has multiple "
                            + "annotations:"
                            + (fieldAnnotation ? " @Field" : "")
                            + (versionFieldAnnotation ? " @VersionField" : "")
                            + (indicatorAnnotation ? " @Indicator" : ""),
                    field);
        }
    }

    /**
     * Loop over all constructors, looking for the annotated one. Also, check if more than one
     * constructor was annotated and print an error message if so.
     */
    private ExecutableElement findAnnotatedConstructor(TypeElement parcelableClass) {
        ExecutableElement annotatedConstructor = null;
        // Loop over all constructors, looking for the annotated one.  Also, check if more
        // than one constructor was annotated and print an error message if so.
        List<ExecutableElement> constructors =
                ElementFilter.constructorsIn(parcelableClass.getEnclosedElements());
        for (ExecutableElement constructor : constructors) {
            SafeParcelable.Constructor constructorAnnotation =
                    constructor.getAnnotation(SafeParcelable.Constructor.class);
            if (constructorAnnotation != null) {
                if (annotatedConstructor != null) {
                    // An annotated constructor was already found before.  This is an error.
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "More than one constructor was" + " annotated with @Constructor.",
                            constructor);
                } else {
                    annotatedConstructor = constructor;
                }
            }
        }
        return annotatedConstructor;
    }

    private Set<Integer> getReservedIds(TypeElement parcelableClass) {
        SafeParcelable.Reserved annotation =
                parcelableClass.getAnnotation(SafeParcelable.Reserved.class);
        if (annotation != null) {
            Set<Integer> ids = new HashSet<>();
            for (int i : annotation.value()) {
                ids.add(i);
            }
            return Collections.unmodifiableSet(ids);
        }
        return Collections.emptySet();
    }

    private boolean processParcelableConstructor(
            ParcelableClass cl, ExecutableElement constructor, Set<Integer> reservedIds) {
        boolean isOkay = true;

        // Iterate through all of the formal parameters of the constructor, and record the field
        // id of each parameter.  All formal parameters of this constructor must be annotated with
        // Param.
        ParcelableConstructor parcelableConstructor = new ParcelableConstructor();

        // Check that the constructor is either public or package private
        Set<Modifier> modifiers = constructor.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "A SafeParcelable class constructor"
                            + " annotated with @Constructor must be either public or package "
                            + "private.",
                    constructor);
            isOkay = false;
        }

        boolean foundIndicator = false;
        List<? extends VariableElement> parameters = constructor.getParameters();
        Set<Integer> usedParamIds = new HashSet<>();
        for (VariableElement parameter : parameters) {
            SafeParcelable.Param paramInfo = parameter.getAnnotation(SafeParcelable.Param.class);
            if (paramInfo == null) {
                if (cl.mIndicatorField != null) {
                    // Check if the parameter has an Indicator annotation.
                    SafeParcelable.Indicator indicatorInfo =
                            parameter.getAnnotation(SafeParcelable.Indicator.class);
                    if (indicatorInfo != null) {
                        // Found an indicator
                        if (foundIndicator) {
                            // Already found an indicator, so this is an error
                            mMessager.printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "Found more than two"
                                            + " parameters in the constructor that have been "
                                            + "annoatated"
                                            + " with @Indicator",
                                    parameter);
                            isOkay = false;
                        }
                        foundIndicator = true;
                        parcelableConstructor.mParameters.add(cl.mIndicatorField);
                    } else {
                        // Parameter has no annotation
                        mMessager.printMessage(
                                Diagnostic.Kind.ERROR,
                                "All parameters of the constructor"
                                        + " annotated with @Constructor must be annotated with "
                                        + "@Param,"
                                        + " @RemovedParam, or @Indicator.",
                                parameter);
                        isOkay = false;
                    }
                } else {
                    SafeParcelable.RemovedParam removedParamInfo =
                            parameter.getAnnotation(SafeParcelable.RemovedParam.class);
                    if (removedParamInfo != null) {
                        int paramId = removedParamInfo.id();
                        // Check that this parameter id has not been used before.
                        if (!usedParamIds.add(paramId)) {
                            mMessager.printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "@RemovedParam(id="
                                            + paramId
                                            + ") has"
                                            + " already been used.  Each parameter must map to a "
                                            + "distinct field"
                                            + " id.",
                                    parameter);
                            isOkay = false;
                        } else {
                            if (!reservedIds.contains(paramId)) {
                                mMessager.printMessage(
                                        Diagnostic.Kind.ERROR,
                                        "@RemovedParam(id="
                                                + paramId
                                                + ") not found as a reserved id. Every "
                                                + "@RemovedParam must be listed as a "
                                                + "@Reserved id as well.",
                                        parameter);
                                isOkay = false;
                            } else {
                                parseField(
                                        cl,
                                        parameter,
                                        paramId,
                                        null,
                                        null,
                                        removedParamInfo.defaultValue(),
                                        removedParamInfo.defaultValueUnchecked(),
                                        false);
                                ParcelableField field =
                                        Objects.requireNonNull(cl.mFields.get(paramId));
                                // ensure field will not participate in writeToParcel()
                                field.mSm.mWrite = null;
                                parcelableConstructor.mParameters.add(field);
                                cl.mFields.put(paramId, field);
                            }
                        }
                    } else {
                        // A parameter in this constructor does not have a Param annotation. This
                        // is an error.
                        mMessager.printMessage(
                                Diagnostic.Kind.ERROR,
                                "All parameters of the"
                                        + " constructor annotated with @Constructor must be "
                                        + "annotated with"
                                        + " @Param or @RemovedParam.",
                                parameter);
                        isOkay = false;
                    }
                }
            } else {
                int paramId = paramInfo.id();
                // Check that this parameter id has not been used before.
                if (!usedParamIds.add(paramId)) {
                    // This id has been used before.
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "@Param(id="
                                    + paramId
                                    + ") has"
                                    + " already been used.  Each parameter must map to a distinct"
                                    + " field"
                                    + " id.",
                            parameter);
                    isOkay = false;
                } else {
                    ParcelableField field = cl.mFields.get(paramId);
                    if (field == null) {
                        // There is no field corresponding to the given parameter id.
                        mMessager.printMessage(
                                Diagnostic.Kind.ERROR,
                                "There is no field annotated" + " with @Field(id=" + paramId + ").",
                                parameter);
                        isOkay = false;
                    } else {
                        parcelableConstructor.mParameters.add(field);
                    }
                }
            }
        }

        // Check that all fields are in the constructor list
        if ((cl.mIndicatorField == null
                        && parcelableConstructor.mParameters.size() != cl.mFields.size())
                || (cl.mIndicatorField != null
                        && parcelableConstructor.mParameters.size() != (cl.mFields.size() + 1))) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Not all fields have been included in"
                            + " the formal parameter list for this SafeParcelable constructor.",
                    constructor);
            isOkay = false;
        } else {
            cl.mConstructor = parcelableConstructor;
        }

        return isOkay;
    }

    private boolean parseIndicatorField(ParcelableClass cl, VariableElement field, String getter) {
        boolean isOkay = true;

        // Convert getter to null if it is set to SafeParcelable.NULL since we can't have
        // actual null values as a default value in an annotation.
        if (getter.equals(SafeParcelable.NULL)) {
            getter = null;
        }

        // Check that the field type is assignable to a Set<Integer>
        TypeMirror fieldType = field.asType();
        if (!isSetOfInteger(fieldType)) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Field annotated with Indicator is the"
                            + " wrong type.  Should be a type assignable to Set<Integer>.",
                    field);
        }

        // Check visibility
        final Set<Modifier> fieldModifiers = field.getModifiers();
        if (fieldModifiers.contains(Modifier.PROTECTED)
                || fieldModifiers.contains(Modifier.PRIVATE)) {
            // field must have a getter
            if (getter == null) {
                mMessager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "A private or protected indicator" + " field must have a getter.",
                        field);
                isOkay = false;
            }
        }

        ParcelableField indicatorField = new ParcelableField(field);
        indicatorField.mId = INDICATOR_FIELD_ID;
        indicatorField.mName = field.getSimpleName().toString();
        indicatorField.mReadName = LOCAL_VARIABLE_PREFIX + indicatorField.mName;
        indicatorField.mType = fieldType;
        indicatorField.mGetter = getter;
        indicatorField.mSm = null;
        cl.mIndicatorField = indicatorField;

        return isOkay;
    }

    private boolean isSetOfInteger(TypeMirror type) {
        if (mTypes.isSameType(mTypes.erasure(type), mTypes.erasure(mSetType))
                || mTypes.isSameType(mTypes.erasure(type), mTypes.erasure(mHashSetType))) {
            final DeclaredType dt = (DeclaredType) type;
            List<? extends TypeMirror> typeArgs = dt.getTypeArguments();
            if (typeArgs.size() == 1) {
                TypeMirror typeArg = typeArgs.get(0);
                if (mTypes.isSameType(typeArg, mIntegerType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Parses each field annotated with @Field or @VersionField in a SafeParcelable class.
     *
     * @param id from the @Field or @VersionField annotation
     * @param getter from the @Field or the @VersionField annotation
     * @param type from the @Field or the @VersionField annotation
     * @param defaultValue from the @Field or null for the @VersionField annotation
     * @param isVersionField indicates that this field was annotated with @VersionField
     */
    private void parseField(
            ParcelableClass cl,
            VariableElement field,
            int id,
            String getter,
            String type,
            String defaultValue,
            String defaultValueUnchecked,
            boolean isVersionField) {
        // Validate the id value, which can be between 1 and 0xffff inclusive
        if (id < 0x00000001 || id > 0x0000ffff) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "id in Field annotation must be a value between 1 and 65535 inclusive.",
                    field);
        }

        // Convert getter to null if it is set to SafeParcelable.NULL since we can't have
        // actual null values as a default value in an annotation.
        if (SafeParcelable.NULL.equals(getter)) {
            getter = null;
        }
        if (SafeParcelable.NULL.equals(type)) {
            type = null;
        }
        if (SafeParcelable.NULL.equals(defaultValue)) {
            defaultValue = null;
        }
        if (SafeParcelable.NULL.equals(defaultValueUnchecked)) {
            defaultValueUnchecked = null;
        }

        // Check visibility
        final Set<Modifier> fieldModifiers = field.getModifiers();
        if (isVersionField) {
            // Version field must be final
            if (!fieldModifiers.contains(Modifier.FINAL)) {
                mMessager.printMessage(
                        Diagnostic.Kind.ERROR, "Version field must be final.", field);
            }
        }
        if (fieldModifiers.contains(Modifier.PROTECTED)
                || fieldModifiers.contains(Modifier.PRIVATE)) {
            // field must have a getter
            if (getter == null) {
                mMessager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "A private or protected field" + " must have a getter.",
                        field);
            }
        }

        TypeMirror fieldType = type != null ? loadTypeOrFail(type) : field.asType();
        final String resolvedDefaultValue;
        if (defaultValue != null) {
            if (defaultValueUnchecked != null) {
                // Both defaultValue and defaultValueUnchecked have been specified.  Only one is
                // allowed to be specified.
                mMessager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Both defaultValue and"
                                + " defaultValueUnchecked have been specified in the Field "
                                + "annotation.  You"
                                + " can specify at most only one of these.");
                resolvedDefaultValue = null;
            } else {
                // Only defaultValue has been specified, so do checks and set things
                if (!allowsDefaultValue(fieldType)) {
                    mMessager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "defaultValue in Field annotation"
                                    + " is allowed only for primitive types, primitive type "
                                    + "object wrappers"
                                    + " and String.",
                            field);
                    resolvedDefaultValue = null;
                } else {
                    resolvedDefaultValue = convertForType(defaultValue, fieldType);
                    if (resolvedDefaultValue == null) {
                        mMessager.printMessage(
                                Diagnostic.Kind.ERROR,
                                "defaultValue in Field"
                                        + " annotation must be a proper value of annotated "
                                        + "field's type.",
                                field);
                    }
                }
            }
        } else if (defaultValueUnchecked != null) {
            // Only defaultValueUnchecked has been specified.
            resolvedDefaultValue = defaultValueUnchecked;
        } else {
            // neither defaultValue nor defaultValueUnchecked has been specified.
            resolvedDefaultValue = null;
        }
        SerializationMethods sm = getSerializationMethod(field, fieldType);
        if (sm != null) {
            ParcelableField f = new ParcelableField(field);
            f.mId = id;
            f.mName = field.getSimpleName().toString();
            f.mReadName = LOCAL_VARIABLE_PREFIX + f.mName;
            f.mType = fieldType;
            f.mGetter = getter;
            f.mSm = sm;
            f.mDefaultValue = resolvedDefaultValue;

            // Special case of generic list
            if (sm.mWrite.equals("writeList")) {
                // When instantiating a List, this will be an ArrayList
                f.mType = mTypes.erasure(f.mType);
            }

            cl.mFields.put(f.mId, f);
        } else {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    fieldType
                            + " field tagged @SafeParcelable.Field is not supported in a"
                            + " SafeParcelable.  The field must be a primitive type, a concrete"
                            + " class implementing SafeParcelable, or a Parcelable class in the "
                            + "Android"
                            + " framework.",
                    field);
        }
    }

    /**
     * Returns true if @Field's defaultValue parameter is allowed for specified field type.
     * defaultValue parameter is allowed only in @Field annotating field with one of following
     * types: primitive type, primitive type object wrapper or String.
     */
    private boolean allowsDefaultValue(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return true;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        return mTypes.isSameType(type, mStringType)
                || mTypes.isSameType(type, mIntegerType)
                || mTypes.isSameType(type, mLongType)
                || mTypes.isSameType(type, mFloatType)
                || mTypes.isSameType(type, mDoubleType)
                || mTypes.isSameType(type, mByteType)
                || mTypes.isSameType(type, mShortType)
                || mTypes.isSameType(type, mCharacterType)
                || mTypes.isSameType(type, mBooleanType);
    }

    /**
     * Converts @Field's defaultValue parameter value to value of specific type. Returns null if
     * trying to convert invalid value.
     */
    private String convertForType(String value, TypeMirror type) {
        TypeKind kind = type.getKind();
        if (kind == TypeKind.DECLARED && mTypes.isSameType(type, mStringType)) {
            return "\"" + value + "\"";
        } else if (kind == TypeKind.CHAR
                || (kind == TypeKind.DECLARED && mTypes.isSameType(type, mCharacterType))) {
            if (" ".equals(value)) {
                return "' '";
            }
            value = value.trim();
            return value.length() == 1 ? "'" + value + "'" : null;
        } else {
            // Ignoring leading and trailing whitespace for non-string types
            value = value.trim();
            if (kind == TypeKind.BOOLEAN
                    || (kind == TypeKind.DECLARED && mTypes.isSameType(type, mBooleanType))) {
                return "true".equals(value) || "false".equals(value) ? value : null;
            } else {
                try {
                    if (kind == TypeKind.DOUBLE
                            || (kind == TypeKind.DECLARED
                                    && mTypes.isSameType(type, mDoubleType))) {
                        Double.parseDouble(value);
                    } else if (kind == TypeKind.FLOAT
                            || (kind == TypeKind.DECLARED && mTypes.isSameType(type, mFloatType))) {
                        Float.parseFloat(value);
                    } else if (kind == TypeKind.BYTE
                            || (kind == TypeKind.DECLARED && mTypes.isSameType(type, mByteType))) {
                        Byte.parseByte(value);
                    } else if (kind == TypeKind.INT
                            || (kind == TypeKind.DECLARED
                                    && mTypes.isSameType(type, mIntegerType))) {
                        Integer.parseInt(value);
                    } else if (kind == TypeKind.LONG
                            || (kind == TypeKind.DECLARED && mTypes.isSameType(type, mLongType))) {
                        Long.parseLong(value);
                    } else if (kind == TypeKind.SHORT
                            || (kind == TypeKind.DECLARED && mTypes.isSameType(type, mShortType))) {
                        Short.parseShort(value);
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
                return value;
            }
        }
    }

    private SerializationMethods getSerializationMethod(VariableElement field, TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return new SerializationMethods(
                        "writeBoolean", false, true, "readBoolean", null, false);
            case BYTE:
                return new SerializationMethods("writeByte", false, true, "readByte", null, false);
            case CHAR:
                return new SerializationMethods("writeChar", false, true, "readChar", null, false);
            case DOUBLE:
                return new SerializationMethods(
                        "writeDouble", false, true, "readDouble", null, false);
            case FLOAT:
                return new SerializationMethods(
                        "writeFloat", false, true, "readFloat", null, false);
            case INT:
                return new SerializationMethods("writeInt", false, true, "readInt", null, false);
            case LONG:
                return new SerializationMethods("writeLong", false, true, "readLong", null, false);
            case SHORT:
                return new SerializationMethods(
                        "writeShort", false, true, "readShort", null, false);
            case DECLARED:
                {
                    final DeclaredType dt = (DeclaredType) type;
                    if (mTypes.isSameType(type, mStringType)) {
                        return new SerializationMethods(
                                "writeString", false, true, "createString", null, true);
                    } else if (mTypes.isSameType(type, mBigIntegerType)) {
                        return new SerializationMethods(
                                "writeBigInteger", false, true, "createBigInteger", null, true);
                    } else if (mTypes.isSameType(type, mBigDecimalType)) {
                        return new SerializationMethods(
                                "writeBigDecimal", false, true, "createBigDecimal", null, true);
                    } else if (mTypes.isSameType(type, mBooleanType)) {
                        return new SerializationMethods(
                                "writeBooleanObject", false, true, "readBooleanObject", null, true);
                    } else if (mTypes.isSameType(type, mIntegerType)) {
                        return new SerializationMethods(
                                "writeIntegerObject", false, true, "readIntegerObject", null, true);
                    } else if (mTypes.isSameType(type, mLongType)) {
                        return new SerializationMethods(
                                "writeLongObject", false, true, "readLongObject", null, true);
                    } else if (mTypes.isSameType(type, mFloatType)) {
                        return new SerializationMethods(
                                "writeFloatObject", false, true, "readFloatObject", null, true);
                    } else if (mTypes.isSameType(type, mDoubleType)) {
                        return new SerializationMethods(
                                "writeDoubleObject", false, true, "readDoubleObject", null, true);
                    } else if (mTypes.isAssignable(type, mIBinderType)) {
                        return new SerializationMethods(
                                "writeIBinder", false, true, "readIBinder", null, true);
                    } else if (mTypes.isSameType(type, mBundleType)) {
                        return new SerializationMethods(
                                "writeBundle", false, true, "createBundle", null, true);
                    } else if (mTypes.isSameType(type, mParcelType)) {
                        return new SerializationMethods(
                                "writeParcel", false, true, "createParcel", null, true);
                    } else if (mTypes.isAssignable(
                            mTypes.erasure(type), mTypes.erasure(mListType))) {
                        List<? extends TypeMirror> typeArgs = dt.getTypeArguments();
                        if (typeArgs.isEmpty() || mTypes.isSameType(mListType, type)) {
                            if (isListSafeForReflection((DeclaredType) field.asType())) {
                                return new SerializationMethods(
                                        "writeList",
                                        false,
                                        false,
                                        "readList",
                                        "getClass().getClassLoader()",
                                        true);
                            } else {
                                mMessager.printMessage(
                                        Diagnostic.Kind.ERROR,
                                        "Use a type parameter for java.util.List fields.",
                                        field);

                                return null;
                            }
                        } else if (typeArgs.size() == 1) {
                            TypeMirror typeArg = typeArgs.get(0);
                            if (mTypes.isAssignable(typeArg, mBooleanType)) {
                                return new SerializationMethods(
                                        "writeBooleanList",
                                        false,
                                        true,
                                        "createBooleanList",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mIntegerType)) {
                                return new SerializationMethods(
                                        "writeIntegerList",
                                        false,
                                        true,
                                        "createIntegerList",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mLongType)) {
                                return new SerializationMethods(
                                        "writeLongList", false, true, "createLongList", null, true);
                            } else if (mTypes.isAssignable(typeArg, mFloatType)) {
                                return new SerializationMethods(
                                        "writeFloatList",
                                        false,
                                        true,
                                        "createFloatList",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mDoubleType)) {
                                return new SerializationMethods(
                                        "writeDoubleList",
                                        false,
                                        true,
                                        "createDoubleList",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mStringType)) {
                                return new SerializationMethods(
                                        "writeStringList",
                                        false,
                                        true,
                                        "createStringList",
                                        null,
                                        true);
                            } else if (mTypes.isSameType(typeArg, mParcelType)) {
                                return new SerializationMethods(
                                        "writeParcelList",
                                        false,
                                        true,
                                        "createParcelList",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mIBinderType)) {
                                return new SerializationMethods(
                                        "writeIBinderList",
                                        false,
                                        true,
                                        "createIBinderList",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mParcelableType)) {
                                return new SerializationMethods(
                                        "writeTypedList",
                                        false,
                                        true,
                                        "createTypedList",
                                        mTypes.erasure(typeArg) + ".CREATOR",
                                        true);
                            }
                        }
                    } else if (mTypes.isAssignable(type, mParcelableType)) {
                        if (isSafeForSafeParcelable(dt)) {
                            return new SerializationMethods(
                                    "writeParcelable",
                                    true,
                                    true,
                                    "createParcelable",
                                    mTypes.erasure(type) + ".CREATOR",
                                    true);
                        }
                    } else if (mTypes.isAssignable(type, mSparseBooleanArrayType)) {
                        return new SerializationMethods(
                                "writeSparseBooleanArray",
                                false,
                                true,
                                "createSparseBooleanArray",
                                null,
                                true);
                    } else if (mTypes.isAssignable(type, mSparseIntArrayType)) {
                        return new SerializationMethods(
                                "writeSparseIntArray",
                                false,
                                true,
                                "createSparseIntArray",
                                null,
                                true);
                    } else if (mTypes.isAssignable(type, mSparseLongArrayType)) {
                        return new SerializationMethods(
                                "writeSparseLongArray",
                                false,
                                true,
                                "createSparseLongArray",
                                null,
                                true);
                    } else if (mTypes.isAssignable(
                            mTypes.erasure(type), mTypes.erasure(mSparseArrayType))) {
                        List<? extends TypeMirror> typeArgs = dt.getTypeArguments();
                        if (typeArgs.isEmpty() || mTypes.isSameType(mSparseArrayType, type)) {
                            return null; // Do not support generic SparseArray.
                        } else if (typeArgs.size() == 1) {
                            TypeMirror typeArg = typeArgs.get(0);
                            if (mTypes.isAssignable(typeArg, mBooleanType)) {
                                mMessager.printMessage(
                                        Diagnostic.Kind.ERROR,
                                        "please use " + "android.util.SparseBooleanArray instead.",
                                        field);
                                return null;
                            } else if (mTypes.isAssignable(typeArg, mIntegerType)) {
                                mMessager.printMessage(
                                        Diagnostic.Kind.ERROR,
                                        "please use " + "android.util.SparseIntArray instead.",
                                        field);
                                return null;
                            } else if (mTypes.isAssignable(typeArg, mLongType)) {
                                mMessager.printMessage(
                                        Diagnostic.Kind.ERROR,
                                        "please use " + "android.util.SparseLongArray instead.",
                                        field);
                                return null;
                            } else if (mTypes.isAssignable(typeArg, mFloatType)) {
                                return new SerializationMethods(
                                        "writeFloatSparseArray",
                                        false,
                                        true,
                                        "createFloatSparseArray",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mDoubleType)) {
                                return new SerializationMethods(
                                        "writeDoubleSparseArray",
                                        false,
                                        true,
                                        "createDoubleSparseArray",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mStringType)) {
                                return new SerializationMethods(
                                        "writeStringSparseArray",
                                        false,
                                        true,
                                        "createStringSparseArray",
                                        null,
                                        true);
                            } else if (mTypes.isSameType(typeArg, mParcelType)) {
                                return new SerializationMethods(
                                        "writeParcelSparseArray",
                                        false,
                                        true,
                                        "createParcelSparseArray",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mIBinderType)) {
                                return new SerializationMethods(
                                        "writeIBinderSparseArray",
                                        false,
                                        true,
                                        "createIBinderSparseArray",
                                        null,
                                        true);
                            } else if (mTypes.isAssignable(typeArg, mParcelableType)) {
                                DeclaredType dtTypeArg = (DeclaredType) typeArg;
                                if (isSafeForSafeParcelable(dtTypeArg)) {
                                    return new SerializationMethods(
                                            "writeTypedSparseArray",
                                            false,
                                            true,
                                            "createTypedSparseArray",
                                            mTypes.erasure(typeArg) + ".CREATOR",
                                            true);
                                }
                            } else if (typeArg.getKind() == TypeKind.ARRAY) {
                                final TypeMirror internalComponentType =
                                        ((ArrayType) typeArg).getComponentType();
                                switch (internalComponentType.getKind()) {
                                    case BYTE:
                                        return new SerializationMethods(
                                                "writeByteArraySparseArray",
                                                false,
                                                true,
                                                "createByteArraySparseArray",
                                                null,
                                                true);
                                    default:
                                        return null;
                                }
                            }
                        }
                    }
                    return null;
                }
            case ARRAY:
                {
                    // The list of array types supported by Parcel is much shorter.
                    // We could certainly implement more here, but if Parcel has gotten
                    // this far without them, it's low priority.
                    final TypeMirror componentType = ((ArrayType) type).getComponentType();
                    switch (componentType.getKind()) {
                        case BOOLEAN:
                            return new SerializationMethods(
                                    "writeBooleanArray",
                                    false,
                                    true,
                                    "createBooleanArray",
                                    null,
                                    true);
                        case BYTE:
                            return new SerializationMethods(
                                    "writeByteArray", false, true, "createByteArray", null, true);
                        case CHAR:
                            return new SerializationMethods(
                                    "writeCharArray", false, true, "createCharArray", null, true);
                        case INT:
                            return new SerializationMethods(
                                    "writeIntArray", false, true, "createIntArray", null, true);
                        case LONG:
                            return new SerializationMethods(
                                    "writeLongArray", false, true, "createLongArray", null, true);
                        case FLOAT:
                            return new SerializationMethods(
                                    "writeFloatArray", false, true, "createFloatArray", null, true);
                        case DOUBLE:
                            return new SerializationMethods(
                                    "writeDoubleArray",
                                    false,
                                    true,
                                    "createDoubleArray",
                                    null,
                                    true);
                        case DECLARED:
                            {
                                final DeclaredType dt = (DeclaredType) componentType;
                                if (mTypes.isSameType(componentType, mStringType)) {
                                    return new SerializationMethods(
                                            "writeStringArray",
                                            false,
                                            true,
                                            "createStringArray",
                                            null,
                                            true);
                                } else if (mTypes.isSameType(componentType, mParcelType)) {
                                    return new SerializationMethods(
                                            "writeParcelArray",
                                            false,
                                            true,
                                            "createParcelArray",
                                            null,
                                            true);
                                } else if (mTypes.isSameType(componentType, mBigIntegerType)) {
                                    return new SerializationMethods(
                                            "writeBigIntegerArray",
                                            false,
                                            true,
                                            "createBigIntegerArray",
                                            null,
                                            true);
                                } else if (mTypes.isSameType(componentType, mBigDecimalType)) {
                                    return new SerializationMethods(
                                            "writeBigDecimalArray",
                                            false,
                                            true,
                                            "createBigDecimalArray",
                                            null,
                                            true);
                                } else if (mTypes.isAssignable(componentType, mIBinderType)) {
                                    return new SerializationMethods(
                                            "writeIBinderArray",
                                            false,
                                            true,
                                            "createIBinderArray",
                                            null,
                                            true);
                                } else if (mTypes.isAssignable(componentType, mParcelableType)) {
                                    if (isSafeForSafeParcelable(dt)) {
                                        return new SerializationMethods(
                                                "writeTypedArray",
                                                true,
                                                true,
                                                "createTypedArray",
                                                mTypes.erasure(componentType) + ".CREATOR",
                                                true);
                                    }
                                }
                                return null;
                            }
                        case ARRAY:
                            {
                                final TypeMirror internalComponentType =
                                        ((ArrayType) componentType).getComponentType();
                                switch (internalComponentType.getKind()) {
                                    case BYTE:
                                        return new SerializationMethods(
                                                "writeByteArrayArray",
                                                false,
                                                true,
                                                "createByteArrayArray",
                                                null,
                                                true);
                                    default:
                                        return null;
                                }
                            }
                        default:
                            return null;
                    }
                }
            default:
                return null;
        }
    }

    /** Check that the Parcelable type can be included as a field for a SafeParcelable. */
    private boolean isSafeForSafeParcelable(DeclaredType type) {
        // Include any types that have been annotated with SafeParcelable
        if (type.asElement().getAnnotation(SafeParcelable.Class.class) != null) {
            return true;
        }

        // Include any types under android.*
        PackageElement typePackage = mElements.getPackageOf(type.asElement());
        if (typePackage.getQualifiedName().toString().startsWith("android.")) {
            return true;
        }
        return false;
    }

    private boolean isListSafeForReflection(DeclaredType fieldType) {
        if (fieldType.getTypeArguments().size() == 1) {
            // List<?>
            DeclaredType typeArg = (DeclaredType) fieldType.getTypeArguments().get(0);
            if (mTypes.isAssignable(mTypes.erasure(typeArg), mTypes.erasure(mListType))) {
                // List<List>
                if (typeArg.getTypeArguments().size() == 1) {
                    TypeMirror innerArg = typeArg.getTypeArguments().get(0);
                    if (mTypes.isAssignable(innerArg, mReflectedParcelableType)) {
                        // List<List<? exends ReflectedParcelable>>
                        return true;
                    }
                }
            } else if (mTypes.isAssignable(typeArg, mReflectedParcelableType)) {
                // List<? extends ReflectedParcelable>
                return true;
            } else if (mTypes.isAssignable(typeArg, mIntegerType)) {
                // List<Integer> for legacy reasons
                return true;
            }
        }

        return false;
    }

    private boolean hasValidateContents(TypeElement clazz) {
        while (clazz != null) {
            List<ExecutableElement> methods = ElementFilter.methodsIn(clazz.getEnclosedElements());
            for (ExecutableElement method : methods) {
                if ("validateContents".equals(method.getSimpleName().toString())
                        && method.getReturnType().getKind() == TypeKind.VOID
                        && method.getParameters().isEmpty()) {
                    // Check that the method is either public or package private.
                    Set<Modifier> modifiers = method.getModifiers();
                    if (modifiers.contains(Modifier.PRIVATE)
                            || modifiers.contains(Modifier.PROTECTED)) {
                        mMessager.printMessage(
                                Diagnostic.Kind.ERROR,
                                "validateContents must be public or package private",
                                method);
                    }
                    return true;
                }
            }
            clazz = (TypeElement) mTypes.asElement(clazz.getSuperclass());
        }
        return false;
    }

    private boolean hasCreator(TypeElement parcelableClass, String generatedClassName) {
        List<VariableElement> fields =
                ElementFilter.fieldsIn(parcelableClass.getEnclosedElements());
        for (VariableElement field : fields) {
            if ("CREATOR".equals(field.getSimpleName().toString())) {
                // If the type of the CREATOR field is the custom creator class, then this would
                // be the type name.  Note that since the creator class is generated, the creator
                // class is not a type that is known by the annotation processor.  Hence, we must
                // manually qualify the package.  The expected name of the CREATOR class is the
                // value of generatedClassName.
                String detectedCreatorTypeName =
                        mElements.getPackageOf(parcelableClass).getQualifiedName()
                                + "."
                                + field.asType();
                // If the type of the CREATOR field is the generic form, Parcelable.Creator<T>
                // then we don't need to manually qualify the type.
                String detectedAlternativeCreatorTypeName = field.asType().toString();

                // This represents the expected type of the CREATOR object in the alternative form.
                // The expected name is in this form is the value of
                // expectedAlternativeCreatorTypeName.
                String expectedAlternativeCreatorTypeName =
                        mTypes.getDeclaredType(
                                        (TypeElement) mTypes.asElement(mParcelableCreatorType),
                                        parcelableClass.asType())
                                .toString();
                TypeMirror parcelableType = parcelableClass.asType();
                if (parcelableType instanceof DeclaredType) {
                    DeclaredType declaredType = (DeclaredType) parcelableType; // Parcel<T>
                    if (!declaredType.getTypeArguments().isEmpty()) {
                        // If the ParcelableType is generic (ex: Parcelable.Creator<Parcel<T>>),
                        // then expectedAlternativeCreatorTypeName needs to trim <T> part as
                        // detectedAlternativeCreatorTypeName would only return Parcel resulting
                        // in an incorrect ParcelCreatorType failure.
                        StringBuilder type = new StringBuilder();
                        Joiner.on(',').appendTo(type, declaredType.getTypeArguments());
                        expectedAlternativeCreatorTypeName =
                                expectedAlternativeCreatorTypeName.replace("<" + type + ">", "");
                    }
                }
                if (generatedClassName.equals(detectedCreatorTypeName)
                        || expectedAlternativeCreatorTypeName.equals(
                                detectedAlternativeCreatorTypeName)) {
                    Set<Modifier> modifiers = field.getModifiers();
                    if (modifiers.contains(Modifier.PUBLIC)
                            && modifiers.contains(Modifier.STATIC)
                            && modifiers.contains(Modifier.FINAL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void generateParser(ParcelableClass cl) {
        PrintWriter writer = null;
        try {
            JavaFileObject file =
                    this.processingEnv
                            .getFiler()
                            .createSourceFile(cl.mGeneratedClassName, cl.mParcelableClass);
            writer = new PrintWriter(file.openOutputStream());

            JSilver jSilver =
                    new JSilver(
                            new ClassLoaderResourceLoader(getClass().getClassLoader(), "templates"),
                            new JSilverOptions().setEscapeMode(EscapeMode.ESCAPE_NONE));

            Data data = jSilver.createData();

            Data annotations = data.createChild("annotations");
            if (cl.mRequiresApi != null) {
                annotations.setValue(
                        "0",
                        String.format(
                                Locale.ROOT,
                                "@androidx.annotation.RequiresApi(%d)",
                                cl.mRequiresApi));
            }

            // Creator class name
            int index = cl.mGeneratedClassName.lastIndexOf('.');
            if (index > 0) {
                data.setValue("creator_package", cl.mGeneratedClassName.substring(0, index));
                data.setValue("creator_name", cl.mGeneratedClassName.substring(index + 1));
            } else {
                data.setValue("creator_name", cl.mGeneratedClassName);
            }

            if (cl.mAnnotation.creatorIsFinal()) {
                data.setValue("creatorIsFinal", "true");
            }

            // Data class name
            data.setValue("class", cl.mQualifiedName);

            // Set the constructor parameters
            data.setValue("params", generateFormalParameters(cl.mConstructor));

            // Call validate on the object
            if (cl.mAnnotation.validate()) {
                data.setValue("call_validateContents", "true");
            }

            if (cl.mAnnotation.doNotParcelTypeDefaultValues()) {
                data.setValue("doNotParcelTypeDefaultValues", "true");
            }

            if (cl.mIndicatorField != null) {
                data.setValue("indicator.read_name", cl.mIndicatorField.mReadName);
                if (cl.mIndicatorField.mGetter == null) {
                    data.setValue("indicator.write_name", cl.mIndicatorField.mName);
                } else {
                    data.setValue("indicator.write_name", cl.mIndicatorField.mGetter + "()");
                }
            }

            // temporary variable declarations
            Data declarations = data.createChild("declarations");
            int i = 0;
            for (ParcelableField f : cl.mConstructor.mParameters) {
                Data declaration = declarations.createChild(Integer.toString(i++));
                declaration.setValue("type", typeOrSuper(f.mType));
                declaration.setValue("var_name", f.mReadName);
                declaration.setValue("initial_value", getDefaultValueForField(f));
            }

            // reading the fields
            Data fields = data.createChild("fields");
            i = 0;
            for (ParcelableField f :
                    cl.mFields.values().stream().sorted(comparing(f -> f.mId)).collect(toList())) {
                SerializationMethods sm = f.mSm;
                Data field = fields.createChild(Integer.toString(i++));
                field.setValue("id", Integer.toString(f.mId));
                if (sm.mIsAssignment) {
                    field.setValue("is_assignment", "true");
                }
                field.setValue("read_name", f.mReadName);
                if (f.mGetter == null) {
                    field.setValue("write_name", f.mName);
                } else {
                    field.setValue("write_name", f.mGetter + "()");
                }
                field.setValue("write", sm.mWrite);
                if (sm.mWriteWithFlags) {
                    field.setValue("writeWithFlags", "1");
                }
                field.setValue("create", sm.mRead);
                if (sm.mCreator != null) {
                    field.setValue("creator", sm.mCreator);
                }
                if (sm.mHasWriteNull) {
                    field.setValue("hasWriteNull", "1");
                }
            }

            // Write to the file.
            if (cl.mIndicatorField == null) {
                jSilver.render("template.cs", data, writer);
            } else {
                jSilver.render("templateWithIndicator.cs", data, writer);
            }
        } catch (IOException ex) {
            mMessager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Error writing class file for "
                            + cl.mGeneratedClassName
                            + ": "
                            + ex.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private String typeOrSuper(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            DeclaredType dt = (DeclaredType) type;
            if (mTypes.isAssignable(mTypes.erasure(type), mTypes.erasure(mListType))
                    && !mTypes.isAssignable(mTypes.erasure(type), mTypes.erasure(mArrayListType))) {
                List<? extends TypeMirror> typeArgs = dt.getTypeArguments();
                if (typeArgs.isEmpty()) {
                    return mTypes.erasure(mListType).toString();
                }
                return mTypes.erasure(mListType) + "<" + typeArgs.get(0) + ">";
            }
        }
        return type.toString();
    }

    private String getDefaultValueForField(ParcelableField field) {
        if (field.mDefaultValue != null) {
            return field.mDefaultValue;
        }
        TypeMirror type = field.mType;
        switch (type.getKind()) {
            case BOOLEAN:
                return "false";
            case BYTE:
                // fallthrough
            case CHAR:
                // fallthrough
            case SHORT:
                // fallthrough
            case INT:
                return "0";
            case DOUBLE:
                return "0.0";
            case FLOAT:
                return "0.0f";
            case LONG:
                return "0L";
            case DECLARED:
                if (mTypes.isAssignable(mTypes.erasure(type), mTypes.erasure(mListType))) {
                    final DeclaredType dt = (DeclaredType) type;
                    if (dt.getTypeArguments().isEmpty()) {
                        // This is a generic list, so create this.
                        return "new java.util.ArrayList()";
                    }
                }
                return "null";
            default:
                return "null";
        }
    }

    private static String generateFormalParameters(ParcelableConstructor constructor) {
        StringBuffer sb = new StringBuffer();
        ArrayList<ParcelableField> parameters = constructor.mParameters;
        for (int i = 0; i < parameters.size(); i++) {
            sb.append(parameters.get(i).mReadName);
            if (i < parameters.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
