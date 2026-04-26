package com.tpt.validator.validation;

import java.util.List;

public interface Rule {
    String id();
    List<Finding> evaluate(ValidationContext ctx);
}
