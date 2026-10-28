package sodresoftwares.barbearia.infra.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import org.hibernate.validator.constraints.CompositionType;
import org.hibernate.validator.constraints.ConstraintComposition;
import org.hibernate.validator.constraints.br.CNPJ;
import org.hibernate.validator.constraints.br.CPF;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@ConstraintComposition(CompositionType.OR)
@CPF
@CNPJ
@ReportAsSingleViolation
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
public @interface CpfOrCnpj {
    String message() default "The provided document must be a valid CPF or CNPJ.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}