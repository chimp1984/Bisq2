package network.misq.finance.contract.sharedState;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DependsOnAlternatives {
    DependsOn[] value();
}
