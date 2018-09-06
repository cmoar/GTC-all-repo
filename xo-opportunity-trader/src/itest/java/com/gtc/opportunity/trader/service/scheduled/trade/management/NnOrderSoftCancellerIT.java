package com.gtc.opportunity.trader.service.scheduled.trade.management;

import com.gtc.model.gateway.command.create.CreateOrderCommand;
import com.gtc.model.gateway.command.manage.CancelOrderCommand;
import com.gtc.opportunity.trader.BaseNnTradeInitialized;
import com.gtc.opportunity.trader.domain.*;
import com.gtc.opportunity.trader.repository.SoftCancelConfigRepository;
import com.gtc.opportunity.trader.repository.SoftCancelRepository;
import com.gtc.opportunity.trader.service.LatestPrices;
import com.gtc.opportunity.trader.service.command.gateway.WsGatewayCommander;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.service.StateMachineService;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by Valentyn Berezin on 04.09.18.
 */
class NnOrderSoftCancellerIT extends BaseNnTradeInitialized {

    private static final BigDecimal DONE_TO_CANCEL_RATIO = BigDecimal.TEN;
    private static final int INITIAL_DONE = 10;
    private static final BigDecimal MIN_LOSS_PCT = BigDecimal.ONE;
    private static final BigDecimal MAX_LOSS_PCT = BigDecimal.TEN;
    private static final BigDecimal BEST_SELL = new BigDecimal("0.98");
    private static final BigDecimal BEST_BUY = new BigDecimal("0.95");

    @Autowired
    private NnOrderSoftCanceller canceller;

    @Autowired
    private SoftCancelConfigRepository cancelConfigRepository;

    @Autowired
    private SoftCancelRepository cancelRepository;

    @Autowired
    private TransactionTemplate template;

    @Autowired
    protected StateMachineService<TradeStatus, TradeEvent> tradeMachines;

    @MockBean
    private LatestPrices prices;

    @MockBean
    private WsGatewayCommander commander;

    @Captor
    private ArgumentCaptor<CancelOrderCommand> cancelCmd;

    @Captor
    private ArgumentCaptor<CreateOrderCommand> createCmd;

    private SoftCancelConfig cancelConfig;
    private SoftCancel cancel;

    @BeforeTransaction
    void initializeDb() {
        // FIXME: Without this wrapping and finding client entity again we would get detached entity exception
        template.execute(call -> {
            ClientConfig cfg = configRepository.findById(createdConfig.getId()).get();
            cancelConfig = SoftCancelConfig.builder()
                    .clientCfg(cfg)
                    .doneToCancelRatio(DONE_TO_CANCEL_RATIO)
                    .minPriceLossPct(MIN_LOSS_PCT)
                    .maxPriceLossPct(MAX_LOSS_PCT)
                    .waitM(30)
                    .enabled(true)
                    .build();
            cancelConfig = cancelConfigRepository.save(cancelConfig);
            cancel = cancelRepository.save(SoftCancel.builder()
                    .clientCfg(cfg)
                    .done(INITIAL_DONE)
                    .build());
            return null;
        });

    }

    @BeforeEach
    void prepareTest() {
        when(prices.bestSell(CLIENT, FROM, TO)).thenReturn(BEST_SELL);
        when(prices.bestBuy(CLIENT, FROM, TO)).thenReturn(BEST_BUY);

        expireMaster(TradeStatus.CLOSED);
        StateMachine<TradeStatus, TradeEvent> machine = tradeMachines.acquireStateMachine(TRADE_TWO);
        machine.sendEvent(TradeEvent.DEPENDENCY_DONE);
        machine.sendEvent(TradeEvent.ACK);
        expireSlave(TradeStatus.OPENED);
    }

    @Test
    void isCreated() {
        canceller.softCancel();

        Trade second = tradeRepository.findById(TRADE_TWO).get();
        assertThat(second.getStatus()).isEqualByComparingTo(TradeStatus.CANCELLED);
        verify(commander).cancel(cancelCmd.capture());
        verify(commander).createOrder(createCmd.capture());
        assertThat(cancelCmd.getValue().getOrderId()).isEqualTo(TRADE_TWO);
        assertThat(createCmd.getValue().getPrice()).isEqualByComparingTo(BEST_SELL);
        assertThat(createCmd.getValue().getAmount()).isEqualByComparingTo(second.getAmount().abs().negate());
        assertThat(tradeRepository.findById(createCmd.getValue().getOrderId())).map(Trade::getStatus)
                .contains(TradeStatus.UNKNOWN);
    }

    @Test
    void usesMinLoss() {

    }

    @Test
    void usesMaxLoss() {

    }

    @Test
    void usesDoneToCancelRatio() {

    }

    @Test
    void validatesWalletBalance() {

    }
}
