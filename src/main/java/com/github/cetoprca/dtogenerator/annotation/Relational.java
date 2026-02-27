package com.github.cetoprca.dtogenerator.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use on fields that represent a relation between entities
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface Relational {
     /**
      * @return Name of the field marked as ID in the related entity
      */
     String relationIDName();           // nombre del campo ID en la clase relacionada

     /**
      * @return .class of the related entity
      */
     Class<?> relationClass();      // la clase de la relación

     /**
      * @return .class of the field marked as ID in the related entity
      */
     Class<?> relationClassIdType() default Integer.class;

     /**
      * @return Marks the field as a Collection so it generates the DTO with a List<relationClassIdType> to hold the related entities IDs
      */
     boolean isCollection() default false;
}