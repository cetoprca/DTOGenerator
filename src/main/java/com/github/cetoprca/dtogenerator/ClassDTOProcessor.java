package com.github.cetoprca.dtogenerator;

import com.github.cetoprca.dtogenerator.annotation.*;
import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.*;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.github.cetoprca.dtogenerator.annotation.GenerateDTO")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class ClassDTOProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateDTO.class)) {

            if (element.getKind() == ElementKind.CLASS) {
                try {
                    generateClassDTOs((TypeElement) element);
                } catch (Exception e) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Error generando DTO: " + e.getMessage(),
                            element
                    );
                }
            }else {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@GenerateDTO solo puede aplicarse a clases",
                        element
                );
            }
        }

        return true;
    }

    private void generateClassDTOs(TypeElement currentClass) throws Exception {

        Map<String, List<VariableElement>> DTOsAndFields = new HashMap<>();

        TypeMirror superClass = currentClass.getSuperclass();
        TypeElement object = processingEnv.getElementUtils().getTypeElement("java.lang.Object");

        boolean classExtends = superClass.getKind() != TypeKind.NONE && !processingEnv.getTypeUtils().isSameType(superClass, object.asType());

        TypeElement originalClass = currentClass;
        while (currentClass != null) {

            // Recorremos los elementos de la clase actual
            for (Element e : currentClass.getEnclosedElements()) {
                if (e.getKind() == ElementKind.FIELD) {
                    VariableElement var = (VariableElement) e;

                    // Obtener todas las anotaciones repetibles @AppearsInDTO
                    AppearsInDTO[] anns = var.getAnnotationsByType(AppearsInDTO.class);
                    for (AppearsInDTO ann : anns) {
                        String customDTOName = ann.name();
                        DTOsAndFields.computeIfAbsent(customDTOName, _ -> new ArrayList<>()).add(var);
                    }

                    // Ignorar campos marcados como @Secret
                    if (var.getAnnotation(Secret.class) == null) {
                        if (classExtends){
                            DTOsAndFields.computeIfAbsent("Full", _ -> new ArrayList<>()).add(var);
                            if (currentClass == originalClass){
                                DTOsAndFields.computeIfAbsent("Base", _ -> new ArrayList<>()).add(var);
                            }
                        }else{
                            DTOsAndFields.computeIfAbsent("", _ -> new ArrayList<>()).add(var);
                        }
                    }
                }
            }

            // Subimos a la superclase
            superClass = currentClass.getSuperclass();
            if (superClass.getKind().isPrimitive() || superClass.toString().equals("java.lang.Object")) {
                break; // no hay más superclases
            }
            currentClass = (TypeElement) processingEnv.getTypeUtils().asElement(superClass);
        }


        currentClass = originalClass;

        for (Map.Entry<String, List<VariableElement>> entry : DTOsAndFields.entrySet()){
            String packageName = processingEnv.getElementUtils().getPackageOf(currentClass).getQualifiedName().toString();
            String className = currentClass.getSimpleName() + entry.getKey() + "DTO";

            String DTOBody = generateClassDTO(packageName, className, currentClass.getQualifiedName().toString(), entry.getValue());

            JavaFileObject file = processingEnv.getFiler()
                .createSourceFile(packageName + "." + className, currentClass);

            try (Writer writer = file.openWriter()) {
                writer.write(DTOBody);
            }
        }
    }

    private String generateClassDTO(
            String packageName,
            String dtoClassName,
            String originalClassName,
            List<VariableElement> variables
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(packageName).append(";\n\n");

        // Cabecera de record
        sb.append("public record ").append(dtoClassName).append("(");

        // Campos del record
        for (int i = 0; i < variables.size(); i++) {
            VariableElement var = variables.get(i);
            String fieldName = var.getSimpleName().toString();
            String fieldType = var.asType().toString();

            Relational rel = var.getAnnotation(Relational.class);
            if (rel != null) {
                TypeMirror idTypeMirror = null;
                if (rel.isCollection()){
                    try {
                        Class<?> clazz = rel.relationClassIdType();
                    } catch (MirroredTypeException mte) {
                        idTypeMirror = mte.getTypeMirror();
                    }
                    String relationClassIdType = idTypeMirror.toString();
                    fieldType = "java.util.List<" + relationClassIdType + ">";
                }else{
                    try {
                        Class<?> clazz = rel.relationClassIdType();
                    } catch (MirroredTypeException mte) {
                        idTypeMirror = mte.getTypeMirror();
                    }
                    fieldType = idTypeMirror.toString();
                }
            }

            sb.append(fieldType).append(" ").append(fieldName);
            if (i < variables.size() - 1) sb.append(", ");
        }
        sb.append(") {\n"); // fin cabecera record

        // Constructor desde la clase original
        sb.append("    public ").append(dtoClassName).append("(").append(originalClassName).append(" original) {\n");
        sb.append("        this(");

        for (int i = 0; i < variables.size(); i++) {
            VariableElement var = variables.get(i);
            String fieldName = var.getSimpleName().toString();

            Relational rel = var.getAnnotation(Relational.class);
            if (rel != null) {
                if (rel.isCollection()){
                    TypeMirror idTypeMirror = null;
                    try {
                        Class<?> clazz = rel.relationClass();
                    } catch (MirroredTypeException mte) {
                        idTypeMirror = mte.getTypeMirror();
                    }
                    String relationClass = idTypeMirror.toString();
                    sb.append("original.").append("get").append(capitalize(fieldName)).append("().stream().map(")
                            .append(relationClass).append("::get").append(capitalize(rel.relationIDName())).append(").toList()");
                }else{
                    sb.append("original.").append("get").append(capitalize(fieldName)).append("().get").append(capitalize(rel.relationIDName())).append("()");
                }
            } else {
                sb.append("original.").append("get").append(capitalize(fieldName)).append("()");
            }

            if (i < variables.size() - 1) sb.append(", ");
        }
        sb.append(");\n");
        sb.append("    }\n\n");

        sb.append("    public ").append(originalClassName).append(" toOriginal() {\n");
        sb.append("        ").append(originalClassName).append(" obj = new ").append(originalClassName).append("();\n");
        for (VariableElement var : variables) {
            String fieldName = var.getSimpleName().toString();
            if (var.getAnnotation(Relational.class) != null) {
                sb.append("        // obj.set").append(capitalize(fieldName))
                        .append("(null); // no se puede reconstruir relación solo con el id\n");
            } else {
                sb.append("        obj.set").append(capitalize(fieldName))
                        .append("(this.").append(fieldName).append(");\n");
            }
        }
        sb.append("        return obj;\n");
        sb.append("    }\n");

        sb.append("}\n");

        return sb.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}