/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie.frontend.soot;

import pascal.taie.ir.proginfo.FieldRef;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.language.annotation.Annotation;
import pascal.taie.language.annotation.AnnotationElement;
import pascal.taie.language.annotation.AnnotationHolder;
import pascal.taie.language.annotation.ArrayElement;
import pascal.taie.language.annotation.BooleanElement;
import pascal.taie.language.annotation.ClassElement;
import pascal.taie.language.annotation.DoubleElement;
import pascal.taie.language.annotation.Element;
import pascal.taie.language.annotation.EnumElement;
import pascal.taie.language.annotation.FloatElement;
import pascal.taie.language.annotation.IntElement;
import pascal.taie.language.annotation.LongElement;
import pascal.taie.language.annotation.StringElement;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JClassLoader;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.StringReps;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeManager;
import pascal.taie.util.collection.Lists;
import pascal.taie.util.collection.Maps;
import soot.ArrayType;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.LongType;
import soot.PrimType;
import soot.RefType;
import soot.ShortType;
import soot.SootClass;
import soot.SootField;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.VoidType;
import soot.tagkit.AbstractHost;
import soot.tagkit.AnnotationAnnotationElem;
import soot.tagkit.AnnotationArrayElem;
import soot.tagkit.AnnotationBooleanElem;
import soot.tagkit.AnnotationClassElem;
import soot.tagkit.AnnotationDoubleElem;
import soot.tagkit.AnnotationElem;
import soot.tagkit.AnnotationEnumElem;
import soot.tagkit.AnnotationFloatElem;
import soot.tagkit.AnnotationIntElem;
import soot.tagkit.AnnotationLongElem;
import soot.tagkit.AnnotationStringElem;
import soot.tagkit.AnnotationTag;
import soot.tagkit.VisibilityAnnotationTag;
import soot.tagkit.VisibilityParameterAnnotationTag;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static pascal.taie.language.type.VoidType.VOID;
import static pascal.taie.util.collection.Maps.newConcurrentMap;

/**
 * Converts Soot classes to Tai-e's representation.
 */
class Converter {

    private final JClassLoader loader;

    private final TypeManager typeManager;

    // Following four maps may be concurrently written during IR construction,
    // thus we use concurrent map to ensure their thread-safety.
    private final ConcurrentMap<SootField, JField> fieldMap
            = newConcurrentMap(4096);

    private final ConcurrentMap<SootMethod, JMethod> methodMap
            = newConcurrentMap(4096);

    private final ConcurrentMap<SootFieldRef, FieldRef> fieldRefMap
            = newConcurrentMap(4096);

    private final ConcurrentMap<SootMethodRef, MethodRef> methodRefMap
            = newConcurrentMap(4096);

    Converter(JClassLoader loader, TypeManager typeManager) {
        this.loader = loader;
        this.typeManager = typeManager;
    }

    Type convertType(soot.Type sootType) {
        if (sootType instanceof PrimType) {
            if (sootType instanceof ByteType) {
                return PrimitiveType.BYTE;
            } else if (sootType instanceof ShortType) {
                return PrimitiveType.SHORT;
            } else if (sootType instanceof IntType) {
                return PrimitiveType.INT;
            } else if (sootType instanceof LongType) {
                return PrimitiveType.LONG;
            } else if (sootType instanceof FloatType) {
                return PrimitiveType.FLOAT;
            } else if (sootType instanceof DoubleType) {
                return PrimitiveType.DOUBLE;
            } else if (sootType instanceof CharType) {
                return PrimitiveType.CHAR;
            } else if (sootType instanceof BooleanType) {
                return PrimitiveType.BOOLEAN;
            }
        } else if (sootType instanceof RefType) {
            return typeManager.getClassType(loader, sootType.toString());
        } else if (sootType instanceof ArrayType arrayType) {
            return typeManager.getArrayType(
                    convertType(arrayType.baseType),
                    arrayType.numDimensions);
        } else if (sootType instanceof VoidType) {
            return VOID;
        }
        throw new SootFrontendException("Cannot convert soot Type: " + sootType);
    }

    JClass convertClass(SootClass sootClass) {
        return loader.loadClass(sootClass.getName());
    }

    JField convertField(SootField sootField) {
        return fieldMap.computeIfAbsent(sootField, f ->
                new JField(convertClass(sootField.getDeclaringClass()),
                        sootField.getName(),
                        Modifiers.convert(sootField.getModifiers()),
                        convertType(sootField.getType()),
                        convertAnnotations(sootField)));
    }

    JMethod convertMethod(SootMethod sootMethod) {
        return methodMap.computeIfAbsent(sootMethod, m -> {
            List<Type> paramTypes = Lists.map(
                    m.getParameterTypes(), this::convertType);
            Type returnType = convertType(m.getReturnType());
            List<ClassType> exceptions = Lists.map(
                    m.getExceptions(),
                    sc -> (ClassType) convertType(sc.getType()));
            // TODO: convert attributes
            return new JMethod(convertClass(m.getDeclaringClass()),
                    m.getName(), Modifiers.convert(m.getModifiers()),
                    paramTypes, returnType, exceptions,
                    convertAnnotations(sootMethod),
                    convertParamAnnotations(sootMethod),
                    sootMethod
            );
        });
    }

    FieldRef convertFieldRef(SootFieldRef sootFieldRef) {
        return fieldRefMap.computeIfAbsent(sootFieldRef, ref -> {
            JClass cls = convertClass(ref.declaringClass());
            Type type = convertType(ref.type());
            return FieldRef.get(cls, ref.name(), type, ref.isStatic());
        });
    }

    MethodRef convertMethodRef(SootMethodRef sootMethodRef) {
        return methodRefMap.computeIfAbsent(sootMethodRef, ref -> {
            JClass cls = convertClass(ref.getDeclaringClass());
            List<Type> paramTypes = Lists.map(
                    ref.getParameterTypes(), this::convertType);
            Type returnType = convertType(ref.getReturnType());
            return MethodRef.get(cls, ref.getName(), paramTypes, returnType,
                    ref.isStatic());
        });
    }

    static AnnotationHolder convertAnnotations(AbstractHost host) {
        var tag = (VisibilityAnnotationTag) host.getTag(VisibilityAnnotationTag.NAME);
        return convertAnnotations(tag);
    }

    private static AnnotationHolder convertAnnotations(
            @Nullable VisibilityAnnotationTag tag) {
        return tag == null ? AnnotationHolder.emptyHolder() :
                AnnotationHolder.make(Lists.map(tag.getAnnotations(),
                        Converter::convertAnnotation));
    }

    private static Annotation convertAnnotation(AnnotationTag tag) {
        String annotationType = StringReps.toTaieTypeDesc(tag.getType());
        Map<String, Element> elements = Maps.newHybridMap();
        tag.getElems().forEach(e -> {
            String name = e.getName();
            Element elem = convertAnnotationElement(e);
            elements.put(name, elem);
        });
        return new Annotation(annotationType, elements);
    }

    private static Element convertAnnotationElement(AnnotationElem elem) {
        if (elem instanceof AnnotationStringElem e) {
            return new StringElement(e.getValue());
        } else if (elem instanceof AnnotationClassElem e) {
            return new ClassElement(StringReps.toTaieTypeDesc(e.getDesc()));
        } else if (elem instanceof AnnotationAnnotationElem e) {
            return new AnnotationElement(convertAnnotation(e.getValue()));
        } else if (elem instanceof AnnotationArrayElem e) {
            return new ArrayElement(Lists.map(e.getValues(),
                    Converter::convertAnnotationElement));
        } else if (elem instanceof AnnotationEnumElem e) {
            return new EnumElement(
                    StringReps.toTaieTypeDesc(e.getTypeName()),
                    e.getConstantName());
        } else if (elem instanceof AnnotationIntElem e) {
            return new IntElement(e.getValue());
        } else if (elem instanceof AnnotationBooleanElem e) {
            return new BooleanElement(e.getValue());
        } else if (elem instanceof AnnotationFloatElem e) {
            return new FloatElement(e.getValue());
        } else if (elem instanceof AnnotationDoubleElem e) {
            return new DoubleElement(e.getValue());
        } else if (elem instanceof AnnotationLongElem e) {
            return new LongElement(e.getValue());
        } else {
            throw new SootFrontendException(
                    "Unable to handle AnnotationElem: " + elem);
        }
    }

    private static @Nullable List<AnnotationHolder> convertParamAnnotations(
            SootMethod sootMethod) {
        var tag = (VisibilityParameterAnnotationTag)
                sootMethod.getTag(VisibilityParameterAnnotationTag.NAME);
        return tag == null ? null :
                Lists.map(tag.getVisibilityAnnotations(), Converter::convertAnnotations);
    }
}
