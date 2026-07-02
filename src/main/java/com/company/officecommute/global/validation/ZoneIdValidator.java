package com.company.officecommute.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

public class ZoneIdValidator implements ConstraintValidator<ValidZoneId, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null/blank은 "미지정"으로 허용 — 도메인에서 기본 시간대(Asia/Seoul)로 대체된다.
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            ZoneId.of(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
