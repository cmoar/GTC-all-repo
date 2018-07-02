package com.gtc.opportunity.trader.service.command.gateway;

import com.gtc.model.gateway.response.ErrorResponse;
import com.gtc.opportunity.trader.domain.Trade;
import com.gtc.opportunity.trader.service.trade.management.TradeEsbEventHandler;
import com.newrelic.api.agent.Trace;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Created by Valentyn Berezin on 27.03.18.
 */
@Service
@RequiredArgsConstructor
@Retryable(value = JpaSystemException.class, // FIXME hackish fix for locks (this retries on any db exception)
        maxAttemptsExpression = "3",
        backoff = @Backoff(multiplier = 3)
)
public class WsGatewayErrorResponseListener {

    private final TradeEsbEventHandler esbEventHandler;

    @Trace(dispatcher = true)
    public void createOrderError(ErrorResponse error) {

        if (error.isTransient()) {
            esbEventHandler.ackTransientError(
                    new Trade.EsbKey(error.getOrderId(), error.getClientName()),
                    error.getId(),
                    error.getErrorCause()
            );
        } else {
            esbEventHandler.ackError(
                    new Trade.EsbKey(error.getOrderId(), error.getClientName()),
                    error.getId(),
                    error.getErrorCause()
            );
        }
    }

    @Trace(dispatcher = true)
    public void manageError(ErrorResponse response) {
        // NOP
    }


    @Trace(dispatcher = true)
    public void accountError(ErrorResponse response) {
        // NOP
    }
}
