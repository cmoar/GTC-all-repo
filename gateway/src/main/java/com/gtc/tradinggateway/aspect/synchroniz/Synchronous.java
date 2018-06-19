package com.gtc.tradinggateway.aspect.synchroniz;

import java.lang.annotation.*;

/**
 * Created by Valentyn Berezin on 22.03.18.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface Synchronous {
}
