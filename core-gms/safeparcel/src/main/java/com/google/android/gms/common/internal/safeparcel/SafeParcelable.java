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

package com.google.android.gms.common.internal.safeparcel;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A SafeParcelable is a special {@link Parcelable} interface that marshalls its fields in a
 * protobuf-like manner into a {@link Parcel}. The marshalling encodes a unique id for each field
 * along with the size in bytes of the field. By doing this, older versions of a SafeParcelable can
 * skip over unknown fields, which enables backwards compatibility. Because SafeParcelable extends a
 * Parcelable, it is NOT safe for persistence.
 *
 * <p>To prevent the need to manually write code to marshall fields like in a Parcelable, a
 * SafeParcelable implementing class is annotated with several annotations so that a generated
 * "creator" class has the metadata it needs to automatically generate the boiler plate marshalling
 * and unmarshalling code.
 *
 * <p>The main annotations are the following:
 *
 * <ul>
 *   <li>{@link Class} - This annotates the SafeParcelable implementing class and indicates the name
 *       of the generated "creator" class that has the boiler plate marshalling/unmarshalling code.
 *       You can also specify whether to call a method named validateContents() after a new instance
 *       of this class is constructed.
 *   <li>{@link VersionField} - This annotation may be on one field in the SafeParcelable to
 *       indicate the version number for this SafeParcelable. This is purely here for style reasons
 *       in case it is necessary for code to be dependent on the version of the SafeParcelable. This
 *       member field must be final.
 *   <li>{@link Field} - This annotates fields that will be marshalled and unmarshalled by the
 *       generated "creator" class. You must provide an integer "id" for each field. To ensure
 *       backwards compatibility, these field id's should never be changed. It is okay to omit field
 *       id's in future versions, but do not reuse old id's. Member fields annotated with {@link
 *       Field} may be any visibility (private, protected, etc.) and may also be final. You will
 *       have to specify the "getter" if member fields are not at least package visible. See details
 *       in {@link Field}.
 *   <li>{@link Constructor} - You must annotate one constructor with this annotation, which
 *       indicates the constructor that the "creator" class will use to construct a new instance of
 *       this class. Each parameter to this constructor must be annotated with the {@link Param} or
 *       {@link RemovedParam} annotation indicating the field id that the parameter corresponds to.
 *       Every {@link Param} must correspond to a {@link Field} or {@link VersionField}, and every
 *       {@link RemovedParam} must correspond to a {@link Reserved} field. Note that this
 *       constructor must have at least package visibility because the generated "creator" class
 *       must be able to use this constructor. (The "creator" class is generated in the same package
 *       as the SafeParcelable class.).
 *   <li>{@link Indicator} - This is an annotation on a field that keeps track of which fields are
 *       actually present in a Parcel that represents the marshalled version of a SafeParcelable.
 *       This is used in the GMS Core Apiary model class code generation.
 * </ul>
 *
 * <p>Because a SafeParcelable extends Parcelable, you must have a public static final member named
 * CREATOR and override writeToParcel() and describeContents(). Here's a typical example.
 *
 * <pre>
 *   &#64;Class(creator="MySafeParcelableCreator", validate=true)
 *   public class MySafeParcelable implements SafeParcelable {
 *       public static final Parcelable.Creator&#60;MySafeParcelable&#62; CREATOR =
 *               new MySafeParcelableCreator();
 *
 *       &#64;Field(id=1)
 *       public final String myString;
 *
 *       &#64;Field(id=2, getter="getInteger")
 *       private final int myInteger;
 *
 *       &#64;Constructor
 *       MySafeParcelable(
 *               &#64;Param(id=1) String string,
 *               &#64;Param(id=2) int integer) {
 *           myString = string;
 *           myInteger = integer;
 *       )
 *
 *       // Example public constructor (not used by MySafeParcelableCreator)
 *       public MySafeParcelable(String string, int integer) {
 *           myString = string;
 *           myInteger = integer;
 *       }
 *
 *       // This is only needed if validate=true in &#64;Class annotation.
 *       public void validateContents() {
 *           // Add validation here.
 *       }
 *
 *       // This getter is needed because myInteger is private, and the generated creator class
 *       // MySafeParcelableCreator can't access private member fields.
 *       int getInteger() {
 *           return myInteger;
 *       }
 *
 *       // This is necessary because SafeParcelable extends Parcelable.
 *       // {@link AbstractSafeParcelable} implements this for you.
 *       &#64;Override
 *       public int describeContents() {
 *           return MySafeParcelableCreator.CONTENT_DESCRIPTION;
 *       }
 *
 *       // This is necessary because SafeParcelable extends Parcelable.
 *       &#64;Override
 *       public void writeToParcel(Parcel out, int flags) {
 *           // This invokes the generated MySafeParcelableCreator class's marshalling to a Parcel.
 *           // In the event you need custom logic when writing to a Parcel, that logic can be
 *           // inserted here.
 *           MySafeParcelableCreator.writeToParcel(this, out, flags);
 *       }
 *   }
 * </pre>
 *
 * @hide
 */
public interface SafeParcelable extends Parcelable {
    /** @hide */
    // Note: the field name and value are accessed using reflection for backwards compatibility, and
    // must not be changed.
    String NULL = "SAFE_PARCELABLE_NULL_STRING";

    /**
     * This annotates your class and specifies the name of the generated "creator" class for
     * marshalling/unmarshalling a SafeParcelable to/from a {@link Parcel}. The "creator" class is
     * generated in the same package as the SafeParcelable class. You can also set "validate" to
     * true, which will cause the "creator" to invoke the method validateContents() on your class
     * after constructing an instance.
     */
    @SuppressWarnings("JavaLangClash")
    @interface Class {
        /**
         * Simple name of the generated "creator" class generated in the same package as the
         * SafeParceable.
         */
        String creator();

        /** Whether the generated "creator" class is final. */
        boolean creatorIsFinal() default true;

        /**
         * When set to true, invokes the validateContents() method in this SafeParcelable object
         * after constructing a new instance.
         */
        boolean validate() default false;

        /**
         * When set to true, it will not write type default values to the Parcel.
         *
         * <p>boolean: false byte/char/short/int/long: 0 float: 0.0f double: 0.0 Objects/arrays:
         * null
         *
         * <p>Cannot be used with Field(defaultValue)
         */
        boolean doNotParcelTypeDefaultValues() default false;
    }

    /** Use this annotation on members that you wish to be marshalled in the SafeParcelable. */
    @interface Field {
        /**
         * Valid values for id are between 1 and 65535. This field id is marshalled into a Parcel .
         * To maintain backwards compatibility, never reuse old id's. It is okay to no longer use
         * old id's and add new ones in subsequent versions of a SafeParcelable.
         */
        int id();

        /**
         * This specifies the name of the getter method for retrieving the value of this field. This
         * must be specified for fields that do not have at least package visibility because the
         * "creator" class will be unable to access the value when attempting to marshall this
         * field. The getter method should take no parameters and return the type of this field
         * (unless overridden by the "type" attribute below).
         */
        String getter() default NULL;

        /**
         * For advanced uses, this specifies the type for the field when marshalling and
         * unmarshalling by the "creator" class to be something different than the declared type of
         * the member variable. This is useful if you want to incorporate an object that is not
         * SafeParcelable (or a system Parcelable object). Be sure to enter the fully qualified name
         * for the class (i.e., android.os.Bundle and not Bundle). For example,
         *
         * <pre>
         *   &#64;Class(creator="MyAdvancedCreator")
         *   public class MyAdvancedSafeParcelable implements SafeParcelable {
         *       public static final Parcelable.Creator&#60;MyAdvancedSafeParcelable&#62; CREATOR =
         *               new MyAdvancedCreator();
         *
         *       &#64;Field(id=1, getter="getObjectAsBundle", type="android.os.Bundle")
         *       private final MyCustomObject myObject;
         *
         *       &#64;Constructor
         *       MyAdvancedSafeParcelable(
         *               &#64;Param(id=1) Bundle objectAsBundle) {
         *           myObject = myConvertFromBundleToObject(objectAsBundle);
         *       }
         *
         *       Bundle getObjectAsBundle() {
         *           // The code here can convert your custom object to one that can be parcelled.
         *           return myConvertFromObjectToBundle(myObject);
         *       }
         *
         *       ...
         *   }
         * </pre>
         */
        String type() default NULL;

        /**
         * This can be used to specify the default value for primitive types (e.g., boolean, int,
         * long), primitive type object wrappers (e.g., Boolean, Integer, Long) and String in the
         * case a value for a field was not explicitly set in the marshalled Parcel. This performs
         * compile-time checks for the type of the field and inserts the appropriate quotes or
         * double quotes around strings and chars or removes them completely for booleans and
         * numbers. To insert a generic string for initializing field, use {@link
         * #defaultValueUnchecked()}. You can specify at most one of {@link #defaultValue()} or
         * {@link #defaultValueUnchecked()}. For example,
         *
         * <pre>
         *   &#64;Field(id=2, defaultValue="true")
         *   boolean myBoolean;
         *
         *   &#64;Field(id=3, defaultValue="13")
         *   Integer myInteger;
         *
         *   &#64;Field(id=4, defaultValue="foo")
         *   String myString;
         * </pre>
         */
        String defaultValue() default NULL;

        /**
         * This can be used to specify the default value for any object and the string value is
         * literally added to the generated creator class code unchecked. You can specify at most
         * one of {@link #defaultValue()} or {@link #defaultValueUnchecked()}. You must fully
         * qualify any classes you reference within the string. For example,
         *
         * <pre>
         *   &#64;Field(id=2, defaultValueUnchecked="new android.os.Bundle()")
         *   Bundle myBundle;
         * </pre>
         */
        String defaultValueUnchecked() default NULL;
    }

    /**
     * There may be exactly one member annotated with VersionField, which represents the version of
     * this safe parcelable. The attributes are the same as those of {@link Field}. Note you can use
     * any type you want for your version field, although most people use int's.
     */
    @interface VersionField {
        int id();

        String getter() default NULL;

        String type() default NULL;
    }

    /**
     * Use this to indicate the member field that holds whether a field was set or not. The member
     * field type currently supported is a HashSet&#60;Integer&#62; which is the set of safe
     * parcelable field id's that have been explicitly set.
     *
     * <p>This annotation should also be used to annotate one of the parameters to the constructor
     * annotated with &#64;Constructor. Note that this annotation should either be present on
     * exactly one member field and one constructor parameter or left out completely.
     */
    @interface Indicator {
        String getter() default NULL;
    }

    /**
     * Use this to indicate the constructor that the creator should use. The constructor annotated
     * with this must be package or public visibility, so that the generated "creator" class can
     * invoke this.
     */
    @interface Constructor {}

    /**
     * Use this on each parameter passed in to the Constructor to indicate to which field id each
     * formal parameter corresponds.
     */
    @interface Param {
        int id();
    }

    /**
     * Use this on a parameter passed in to the Constructor to indicate that a removed field should
     * be read on construction. If the field is not present when read, the default value will be
     * used instead.
     */
    @interface RemovedParam {
        int id();

        String defaultValue() default NULL;

        String defaultValueUnchecked() default NULL;
    }

    /**
     * Use this to mark tombstones for removed {@link Field Fields} or {@link VersionField
     * VersionFields}.
     */
    @interface Reserved {
        int[] value();
    }
}
