package network.misq.trade.sharedState;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DependsOnAlternatives.class)
public @interface DependsOn {
    String[] value();
}
