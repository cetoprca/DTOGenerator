package com.github.cetoprca.dtogenerator.annotation;

import java.lang.annotation.*;

/**
 * Specifies a DTO in which the field appears.
 * Fields can have several of this annotation so it appears in several custom DTOs
 */
@Repeatable(AppearsInDTOs.class)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface AppearsInDTO {
     /**
      * @return Name of the DTO the field will be a part of
      */
     String name();
}
